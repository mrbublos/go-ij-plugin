package dev.goij.debugger

import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.xdebugger.*
import dev.goij.configuration.GoConfigurationType
import dev.goij.configuration.GoRunConfiguration
import dev.goij.toolchain.GoToolchainSettings
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import org.eclipse.lsp4j.debug.*
import org.eclipse.lsp4j.debug.launch.DSPLauncher
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer

class GoNativeControlTest : BasePlatformTestCase() {
    fun testControlsUseSelectedGoroutineAndLateContinueCannotEraseNewStop() {
        val calls = CopyOnWriteArrayList<String>()
        var rejectNext = true
        val delayedContinue = CompletableFuture<ContinueResponse>()
        val client = CompletableFuture<IDebugProtocolClient>()
        val server =
            object : IDebugProtocolServer {
                override fun initialize(args: InitializeRequestArguments) =
                    CompletableFuture.completedFuture(Capabilities())

                override fun launch(args: Map<String, Any>): CompletableFuture<Void> {
                    client.join().initialized()
                    return CompletableFuture.completedFuture(null)
                }

                override fun configurationDone(
                    args: ConfigurationDoneArguments
                ): CompletableFuture<Void> {
                    client
                        .join()
                        .stopped(
                            StoppedEventArguments().apply {
                                reason = "breakpoint"
                                threadId = 7
                            }
                        )
                    return CompletableFuture.completedFuture(null)
                }

                override fun threads() =
                    CompletableFuture.completedFuture(
                        ThreadsResponse().apply {
                            threads =
                                arrayOf(
                                    org.eclipse.lsp4j.debug.Thread().apply {
                                        id = 9
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
                                        id = args.threadId * 10
                                        name = "worker"
                                        line = 1
                                        column = 1
                                    }
                                )
                        }
                    )

                override fun stepIn(args: StepInArguments) = completed("into:${args.threadId}")

                override fun next(args: NextArguments): CompletableFuture<Void> {
                    if (rejectNext) {
                        rejectNext = false
                        return CompletableFuture.failedFuture(dapFailure("cannot step here"))
                    }
                    return completed("over:${args.threadId}")
                }

                override fun stepOut(args: StepOutArguments) = completed("out:${args.threadId}")

                override fun pause(args: PauseArguments) = completed("pause")

                override fun continue_(args: ContinueArguments) =
                    delayedContinue.also { calls.add("continue:${args.threadId}") }

                override fun disconnect(args: DisconnectArguments) =
                    CompletableFuture.completedFuture<Void>(null)

                private fun completed(call: String) =
                    CompletableFuture.completedFuture<Void>(null).also { calls.add(call) }
            }
        ServerSocket(0).use { listener ->
            val executable = Files.createTempFile("fake-delve", ".sh")
            Files.writeString(
                executable,
                "#!/bin/sh\necho 'DAP server listening at: 127.0.0.1:${listener.localPort}'\nexec /bin/sleep 600\n",
            )
            executable.toFile().setExecutable(true)
            val accepted = CompletableFuture<java.net.Socket>()
            java.lang.Thread.ofVirtual().start {
                val socket = listener.accept()
                accepted.complete(socket)
                val launcher =
                    DSPLauncher.createServerLauncher(
                        server,
                        socket.getInputStream(),
                        socket.getOutputStream(),
                    )
                client.complete(launcher.remoteProxy)
                launcher.startListening()
            }
            val tools = GoToolchainSettings.getInstance().state
            val oldDelve = tools.delveExecutable
            var session: XDebugSession? = null
            try {
                tools.delveExecutable = executable.toString()
                project.messageBus
                    .connect(testRootDisposable)
                    .subscribe(
                        XDebuggerManager.TOPIC,
                        object : XDebuggerManagerListener {
                            override fun processStarted(process: XDebugProcess) {
                                session = process.session
                            }
                        },
                    )
                val settings =
                    RunManager.getInstance(project)
                        .createConfiguration("fake debug", GoConfigurationType::class.java)
                (settings.configuration as GoRunConfiguration).packageDirectory =
                    Path.of("src/test/testData/hello").toAbsolutePath().toString()
                val environment =
                    ExecutionEnvironmentBuilder.create(
                            DefaultDebugExecutor.getDebugExecutorInstance(),
                            settings,
                        )
                        .build()
                environment.setHeadless()
                ProgramRunner.getRunner(DefaultDebugExecutor.EXECUTOR_ID, settings.configuration)!!
                    .execute(environment)
                await { session?.isSuspended == true }
                val active = session!!
                val stacks = StackList()
                active.suspendContext!!.computeExecutionStacks(stacks)
                await { stacks.done }
                val worker = stacks.items.single()
                val frames = FrameList()
                worker.computeStackFrames(0, frames)
                await { frames.done }
                active.setCurrentStackFrame(worker, frames.items.first(), true)
                val beforeRejection = active.suspendContext
                active.stepOver(false)
                await { active.isSuspended && active.suspendContext !== beforeRejection }
                assertFalse(active.debugProcess.processHandler.isProcessTerminated)
                val actions =
                    listOf<Pair<String, () -> Unit>>(
                        "into:9" to { active.stepInto() },
                        "over:9" to { active.stepOver(false) },
                        "out:9" to { active.stepOut() },
                    )
                actions.forEach { (call, action) ->
                    val old = active.suspendContext
                    action()
                    await { calls.contains(call) }
                    client
                        .join()
                        .stopped(
                            StoppedEventArguments().apply {
                                reason = "step"
                                threadId = 9
                            }
                        )
                    await { active.isSuspended && active.suspendContext !== old }
                }
                active.resume()
                await { calls.contains("continue:9") }
                client
                    .join()
                    .stopped(
                        StoppedEventArguments().apply {
                            reason = "breakpoint"
                            threadId = 7
                        }
                    )
                await { active.isSuspended }
                val latest = active.suspendContext
                val delivered = CompletableFuture<Unit>()
                active.debugProcess.processHandler.addProcessListener(
                    object : com.intellij.execution.process.ProcessAdapter() {
                        override fun onTextAvailable(
                            event: com.intellij.execution.process.ProcessEvent,
                            outputType: com.intellij.openapi.util.Key<*>,
                        ) {
                            if (event.text.contains("continued response delivered"))
                                delivered.complete(Unit)
                        }
                    }
                )
                delayedContinue.complete(ContinueResponse())
                val fence = client.join()
                fence.output(
                    OutputEventArguments().apply { output = "continued response delivered\n" }
                )
                // Pump the event queue after the completed response reaches the client.
                PlatformTestUtil.waitWithEventsDispatching(
                    "response completed",
                    { delivered.isDone },
                    5,
                )
                com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
                assertSame(latest, active.suspendContext)
                active.debugProcess.startPausing()
                await { calls.contains("pause") }
                assertFalse(active.debugProcess.processHandler.isProcessTerminated)
            } finally {
                tools.delveExecutable = oldDelve
                session?.let {
                    it.stop()
                    await { it.debugProcess.processHandler.isProcessTerminated }
                    com.intellij.openapi.util.Disposer.dispose(it.consoleView)
                    com.intellij.openapi.util.Disposer.dispose(it.runContentDescriptor)
                }
                accepted.getNow(null)?.close()
                Files.deleteIfExists(executable)
            }
        }
    }

    private fun await(condition: () -> Boolean) =
        PlatformTestUtil.waitWithEventsDispatching("Native DAP controls", condition, 15)
}
