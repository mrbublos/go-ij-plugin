package dev.goij.debugger

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.ColoredTextContainer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.frame.*
import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.debug.*
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer

/**
 * One stop owns its frames and DAP references. Selection changes invalidate pending variable loads.
 */
internal class GoSuspension(
    private val server: IDebugProtocolServer,
    val threadId: Int,
    private val active: () -> Boolean,
) : XSuspendContext() {
    private val stacks = mutableMapOf<Int, GoStack>()
    private var selection = 0
    var selectedThread = threadId
        private set

    private val stoppedStack = stack(threadId, "Goroutine $threadId")

    fun selectionChanged(frame: XStackFrame? = null) {
        (frame as? GoFrame)?.let { selectedThread = it.thread }
        selection++
    }

    override fun getActiveExecutionStack(): XExecutionStack = stoppedStack

    fun loadTopFrame(ready: () -> Unit, failed: (String) -> Unit) {
        loadFrames(threadId, { true }, failed) { frames ->
            stoppedStack.frames = frames
            ready()
        }
    }

    override fun computeExecutionStacks(container: XExecutionStackContainer) {
        request({ !container.isObsolete }, { server.threads() }, container::errorOccurred) {
            response ->
            container.addExecutionStack(response.threads.map { stack(it.id, it.name) }, true)
        }
    }

    private fun stack(id: Int, name: String) = stacks.getOrPut(id) { GoStack(id, name) }

    private fun loadFrames(
        id: Int,
        valid: () -> Boolean,
        failed: (String) -> Unit,
        ready: (List<XStackFrame>) -> Unit,
    ) {
        request(
            valid,
            { server.stackTrace(StackTraceArguments().apply { threadId = id }) },
            failed,
        ) { response ->
            ready(response.stackFrames.map { GoFrame(it, id) })
        }
    }

    private inner class GoStack(val id: Int, name: String) : XExecutionStack(name) {
        var frames: List<XStackFrame>? = null

        override fun getTopFrame() = frames?.firstOrNull()

        override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer) {
            if (!active() || container.isObsolete) return
            val cached = frames
            if (cached != null) container.addStackFrames(cached.drop(firstFrameIndex), true)
            else
                loadFrames(id, { !container.isObsolete }, container::errorOccurred) {
                    frames = it
                    container.addStackFrames(it.drop(firstFrameIndex), true)
                }
        }
    }

    private inner class GoFrame(private val frame: StackFrame, val thread: Int) : XStackFrame() {
        override fun getEqualityObject(): Any = frame.id

        override fun getSourcePosition() =
            frame.source
                ?.path
                ?.let { LocalFileSystem.getInstance().refreshAndFindFileByPath(it) }
                ?.let {
                    XDebuggerUtil.getInstance()
                        .createPosition(it, (frame.line - 1).coerceAtLeast(0))
                }

        override fun customizePresentation(component: ColoredTextContainer) {
            component.append(frame.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            frame.source?.name?.let {
                component.append(" ($it:${frame.line})", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
        }

        override fun computeChildren(node: XCompositeNode) {
            val selected = selection
            request(
                { !node.isObsolete && selection == selected },
                { server.scopes(ScopesArguments().apply { frameId = frame.id }) },
                node::setErrorMessage,
            ) { response ->
                val children = XValueChildrenList()
                response.scopes.forEach { scope ->
                    children.add(scope.name, GoValue(scope.variablesReference, "", null, selected))
                }
                node.addChildren(children, true)
            }
        }
    }

    private inner class GoValue(
        private val reference: Int,
        private val value: String,
        private val type: String?,
        private val selected: Int,
    ) : XValue() {
        override fun computePresentation(node: XValueNode, place: XValuePlace) {
            if (active() && selection == selected && !node.isObsolete)
                node.setPresentation(null, type, value, reference > 0)
        }

        override fun computeChildren(node: XCompositeNode) {
            request(
                { !node.isObsolete && selection == selected },
                { server.variables(VariablesArguments().apply { variablesReference = reference }) },
                node::setErrorMessage,
            ) { response ->
                val children = XValueChildrenList()
                response.variables.forEach {
                    children.add(
                        it.name,
                        GoValue(it.variablesReference, it.value, it.type, selected),
                    )
                }
                node.addChildren(children, true)
            }
        }
    }

    private fun <T> request(
        valid: () -> Boolean,
        operation: () -> CompletableFuture<T>,
        failed: (String) -> Unit,
        ready: (T) -> Unit,
    ) {
        if (!active() || !valid()) return
        CompletableFuture.supplyAsync(operation)
            .thenCompose { it }
            .whenComplete { response, error ->
                ApplicationManager.getApplication().invokeLater {
                    if (active() && valid()) {
                        if (error == null) ready(response)
                        else
                            failed(
                                generateSequence(error) { it.cause }.last().message
                                    ?: "Delve request failed."
                            )
                    }
                }
            }
    }
}
