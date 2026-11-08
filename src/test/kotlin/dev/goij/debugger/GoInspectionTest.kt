package dev.goij.debugger

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.xdebugger.frame.*
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.debug.*
import org.eclipse.lsp4j.debug.launch.DSPLauncher
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer

class GoInspectionTest : BasePlatformTestCase() {
    fun testGoroutinesFramesAndLazyVariablesThroughDap() {
        var variableCalls = 0
        val server =
            object : IDebugProtocolServer {
                override fun threads() =
                    CompletableFuture.completedFuture(
                        ThreadsResponse().apply {
                            threads =
                                arrayOf(
                                    org.eclipse.lsp4j.debug.Thread().apply {
                                        id = 7
                                        name = "worker"
                                    }
                                )
                        }
                    )

                override fun stackTrace(args: StackTraceArguments) =
                    CompletableFuture.completedFuture(
                        StackTraceResponse().apply {
                            stackFrames =
                                arrayOf(
                                    StackFrame().apply {
                                        id = 70
                                        name = "main.worker"
                                        line = 4
                                        column = 1
                                    }
                                )
                        }
                    )

                override fun scopes(args: ScopesArguments) =
                    CompletableFuture.completedFuture(
                        ScopesResponse().apply {
                            scopes =
                                arrayOf(
                                    Scope().apply {
                                        name = "Locals"
                                        variablesReference = 10
                                    }
                                )
                        }
                    )

                override fun variables(
                    args: VariablesArguments
                ): CompletableFuture<VariablesResponse> {
                    variableCalls++
                    if (variableCalls == 2)
                        return CompletableFuture.failedFuture(dapFailure("value unavailable"))
                    return CompletableFuture.completedFuture(
                        VariablesResponse().apply {
                            variables =
                                arrayOf(
                                    Variable().apply {
                                        name =
                                            if (args.variablesReference == 10) "record" else "Count"
                                        value =
                                            if (args.variablesReference == 10) "main.Record"
                                            else "42"
                                        variablesReference =
                                            if (args.variablesReference == 10) 11 else 0
                                    }
                                )
                        }
                    )
                }
            }
        withServer(server) { connection ->
            val context = GoSuspension(connection.server, 1) { true }
            val stacks = StackList()
            context.computeExecutionStacks(stacks)
            await { stacks.done }
            assertEquals("worker", stacks.items.single().displayName)
            val frames = FrameList()
            stacks.items.single().computeStackFrames(0, frames)
            await { frames.done }
            val locals = Children()
            frames.items.single().computeChildren(locals)
            await { locals.done }
            assertEquals(0, variableCalls)
            val values = Children()
            locals.items!!.getValue(0).computeChildren(values)
            await { values.done }
            assertEquals("record", values.items!!.getName(0))
            assertEquals(1, variableCalls)
            val rejected = Children()
            values.items!!.getValue(0).computeChildren(rejected)
            await { rejected.done }
            assertTrue(rejected.error.orEmpty().contains("value unavailable"))
            val fields = Children()
            values.items!!.getValue(0).computeChildren(fields)
            await { fields.done }
            assertEquals("Count", fields.items!!.getName(0))
            assertEquals(3, variableCalls)
        }
    }

    fun testSelectionAndResumeDiscardLateResponsesAndFailuresAllowRetry() {
        val pending = java.util.concurrent.LinkedBlockingQueue<CompletableFuture<ScopesResponse>>()
        val server =
            object : IDebugProtocolServer {
                override fun stackTrace(args: StackTraceArguments) =
                    CompletableFuture.completedFuture(
                        StackTraceResponse().apply {
                            stackFrames =
                                arrayOf(
                                    StackFrame().apply {
                                        id = 70
                                        name = "main.worker"
                                        line = 4
                                        column = 1
                                    }
                                )
                        }
                    )

                override fun scopes(args: ScopesArguments) =
                    CompletableFuture<ScopesResponse>().also { pending.add(it) }
            }
        withServer(server) { connection ->
            var active = true
            val context = GoSuspension(connection.server, 7) { active }
            val frames = FrameList()
            context.activeExecutionStack!!.computeStackFrames(0, frames)
            await { frames.done }
            val first = Children()
            frames.items.single().computeChildren(first)
            await { pending.isNotEmpty() }
            context.selectionChanged()
            pending.remove().complete(ScopesResponse().apply { scopes = emptyArray() })
            val second = Children()
            frames.items.single().computeChildren(second)
            await { pending.isNotEmpty() }
            pending.remove().completeExceptionally(dapFailure("scope unavailable"))
            await { second.done }
            assertNotNull(second.error)
            assertFalse(first.done)
            val retry = Children()
            frames.items.single().computeChildren(retry)
            await { pending.isNotEmpty() }
            pending.remove().complete(ScopesResponse().apply { scopes = emptyArray() })
            await { retry.done }
            assertNull(retry.error)
            val late = Children()
            frames.items.single().computeChildren(late)
            await { pending.isNotEmpty() }
            active = false
            pending.remove().completeExceptionally(dapFailure("late failure"))
            // A subsequent request/reply fences delivery of the earlier socket response.
            val fence = connection.server.stackTrace(StackTraceArguments().apply { threadId = 7 })
            await { fence.isDone }
            com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
            assertFalse(late.done)
        }
    }

    private fun withServer(server: IDebugProtocolServer, action: (DapConnection) -> Unit) {
        ServerSocket(0).use { listener ->
            val socket = Socket("127.0.0.1", listener.localPort)
            listener.accept().use { accepted ->
                val listening =
                    DSPLauncher.createServerLauncher(
                            server,
                            accepted.getInputStream(),
                            accepted.getOutputStream(),
                        )
                        .startListening()
                DapConnection(socket, object : IDebugEvents {}).use(action)
                listening.cancel(true)
            }
        }
    }

    private fun await(condition: () -> Boolean) =
        PlatformTestUtil.waitWithEventsDispatching("DAP model response", condition, 10)
}

internal class StackList : XSuspendContext.XExecutionStackContainer {
    val items = mutableListOf<XExecutionStack>()
    var done = false
    var error: String? = null

    override fun addExecutionStack(stacks: List<XExecutionStack>, last: Boolean) {
        items.addAll(stacks)
        done = last
    }

    override fun errorOccurred(message: String) {
        error = message
        done = true
    }

    override fun isObsolete() = false
}

internal class FrameList : XExecutionStack.XStackFrameContainer {
    val items = mutableListOf<XStackFrame>()
    var done = false
    var error: String? = null

    override fun addStackFrames(frames: List<XStackFrame>, last: Boolean) {
        items.addAll(frames)
        done = last
    }

    override fun errorOccurred(message: String) {
        error = message
        done = true
    }

    override fun isObsolete() = false
}

internal class Children : XCompositeNode {
    var items: XValueChildrenList? = null
    var done = false
    var error: String? = null

    override fun addChildren(children: XValueChildrenList, last: Boolean) {
        items = children
        done = last
    }

    override fun setErrorMessage(message: String) {
        error = message
        done = true
    }

    override fun setErrorMessage(message: String, link: XDebuggerTreeNodeHyperlink?) =
        setErrorMessage(message)

    override fun setAlreadySorted(sorted: Boolean) {}

    override fun tooManyChildren(remaining: Int) {}

    override fun setMessage(
        message: String,
        icon: javax.swing.Icon?,
        attributes: com.intellij.ui.SimpleTextAttributes,
        link: XDebuggerTreeNodeHyperlink?,
    ) {}

    override fun isObsolete() = false
}

internal fun dapFailure(message: String) =
    org.eclipse.lsp4j.jsonrpc.ResponseErrorException(
        org.eclipse.lsp4j.jsonrpc.messages.ResponseError(
            org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode.InvalidParams,
            message,
            null,
        )
    )

internal class ValuePresentation : XValueNode {
    var value: String? = null

    override fun setPresentation(
        icon: javax.swing.Icon?,
        type: String?,
        value: String,
        hasChildren: Boolean,
    ) {
        this.value = value
    }

    override fun setPresentation(
        icon: javax.swing.Icon?,
        presentation: com.intellij.xdebugger.frame.presentation.XValuePresentation,
        hasChildren: Boolean,
    ) {}

    override fun setFullValueEvaluator(evaluator: XFullValueEvaluator) {}

    override fun isObsolete() = false
}
