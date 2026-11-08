package dev.goij.debugger

import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.xdebugger.*
import com.intellij.xdebugger.breakpoints.*
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.*
import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.debug.*

/** Bridges DAP events into the native debugger. UI state is confined to the EDT. */
class GoDebugProcess(
    session: XDebugSession,
    launch: (IDebugEvents, Map<String, List<Int>>) -> GoDebugProcessHandler,
) : XDebugProcess(session) {
    private val breakpoints = linkedSetOf<XLineBreakpoint<GoBreakpointProperties>>()
    private var suspension: GoSuspension? = null
    private var generation = 0
    private var commandTail = CompletableFuture.completedFuture(Unit)
    private val handler: GoDebugProcessHandler

    init {
        val type =
            XBreakpointType.EXTENSION_POINT_NAME.extensionList
                .filterIsInstance<GoLineBreakpointType>()
                .single()
        breakpoints.addAll(
            XDebuggerManager.getInstance(session.project)
                .breakpointManager
                .getBreakpoints(type)
                .filter { it.isEnabled }
        )
        handler =
            launch(
                object : IDebugEvents {
                    override fun stopped(event: StoppedEventArguments) {
                        onStopped(event)
                    }

                    override fun continued() {
                        ui {
                            generation++
                            suspension = null
                            session.sessionResumed()
                        }
                    }

                    override fun exited(code: Int) {
                        ui {
                            generation++
                            suspension = null
                        }
                    }

                    override fun failed(error: Throwable) {
                        ui { session.reportError(error.message ?: "Delve request failed.") }
                    }

                    override fun breakpoints(
                        path: String,
                        lines: List<Int>,
                        results: Array<Breakpoint>,
                    ) {
                        ui {
                            lines.forEachIndexed { index, line ->
                                breakpoints
                                    .filter {
                                        it.fileUrl ==
                                            com.intellij.openapi.vfs.VfsUtilCore.pathToUrl(path) &&
                                            it.line + 1 == line
                                    }
                                    .forEach { bp ->
                                        val result = results.getOrNull(index)
                                        if (result?.isVerified == true)
                                            session.setBreakpointVerified(bp)
                                        else
                                            session.setBreakpointInvalid(
                                                bp,
                                                result?.message
                                                    ?: "Delve could not verify this breakpoint.",
                                            )
                                    }
                            }
                        }
                    }
                },
                breakpoints.groupBy({ path(it) }, { it.line + 1 }),
            )
    }

    override fun getEditorsProvider() =
        object : XDebuggerEditorsProvider() {
            override fun getFileType() = PlainTextFileType.INSTANCE

            override fun isEvaluateExpressionFieldEnabled() = false
        }

    override fun doGetProcessHandler(): ProcessHandler = handler

    override fun getBreakpointHandlers(): Array<XBreakpointHandler<*>> =
        arrayOf(
            object :
                XBreakpointHandler<XLineBreakpoint<GoBreakpointProperties>>(
                    GoLineBreakpointType::class.java
                ) {
                override fun registerBreakpoint(
                    breakpoint: XLineBreakpoint<GoBreakpointProperties>
                ) {
                    breakpoints.add(breakpoint)
                    synchronize(path(breakpoint))
                }

                override fun unregisterBreakpoint(
                    breakpoint: XLineBreakpoint<GoBreakpointProperties>,
                    temporary: Boolean,
                ) {
                    breakpoints.remove(breakpoint)
                    synchronize(path(breakpoint))
                }
            }
        )

    private fun path(breakpoint: XLineBreakpoint<GoBreakpointProperties>) =
        com.intellij.openapi.vfs.VfsUtilCore.urlToPath(breakpoint.fileUrl)

    private fun synchronize(path: String) {
        val lines = breakpoints.filter { path(it) == path }.map { it.line + 1 }
        handler.updateBreakpoints(path, lines)
    }

    override fun sessionInitialized() {
        session.setPauseActionSupported(true)
        session.addSessionListener(
            object : XDebugSessionListener {
                override fun stackFrameChanged() {
                    suspension?.selectionChanged(session.currentStackFrame)
                }

                override fun sessionStopped() {
                    generation++
                    suspension = null
                }
            }
        )
    }

    private fun onStopped(event: StoppedEventArguments) {
        ui {
            val dap = handler.connection ?: return@ui
            val current = ++generation
            val context =
                GoSuspension(dap.server, event.threadId ?: 1) {
                    current == generation &&
                        !handler.isProcessTerminating &&
                        !handler.isProcessTerminated &&
                        !session.project.isDisposed
                }
            suspension = context
            context.loadTopFrame(
                { session.positionReached(context) },
                {
                    session.reportError(it)
                    session.positionReached(context)
                },
            )
        }
    }

    override fun resume(context: XSuspendContext?) =
        control(context) { server, id ->
            server.continue_(ContinueArguments().apply { threadId = id })
        }

    override fun startStepInto(context: XSuspendContext?) =
        control(context) { server, id -> server.stepIn(StepInArguments().apply { threadId = id }) }

    override fun startStepOver(context: XSuspendContext?) =
        control(context) { server, id -> server.next(NextArguments().apply { threadId = id }) }

    override fun startStepOut(context: XSuspendContext?) =
        control(context) { server, id ->
            server.stepOut(StepOutArguments().apply { threadId = id })
        }

    override fun startPausing() {
        val dap = handler.connection ?: return
        val current = generation
        request({ dap.server.pause(PauseArguments().apply { threadId = 0 }) }, current) {}
    }

    private fun control(
        context: XSuspendContext?,
        operation:
            (org.eclipse.lsp4j.debug.services.IDebugProtocolServer, Int) -> CompletableFuture<*>,
    ) {
        val dap = handler.connection ?: return
        val stopped = context as? GoSuspension ?: suspension ?: return
        val id = stopped.selectedThread
        val current = ++generation
        suspension = null
        request(
            { operation(dap.server, id) },
            current,
            { onStopped(StoppedEventArguments().apply { threadId = id }) },
        ) {
            session.sessionResumed()
        }
    }

    override fun stop() {
        generation++
        suspension = null
        handler.destroyProcess()
    }

    private fun <T> request(
        operation: () -> CompletableFuture<T>,
        current: Int,
        failed: () -> Unit = {},
        action: (T) -> Unit,
    ) {
        // Preserve command submission order without waiting for a reply (pause may interrupt
        // continue).
        val submitted = commandTail.handleAsync { _, _ -> operation() }
        commandTail = submitted.handle { _, _ -> Unit }
        submitted
            .thenCompose { it }
            .whenComplete { value, error ->
                ui {
                    if (current == generation) {
                        if (error == null) action(value)
                        else {
                            session.reportError(
                                generateSequence(error) { it.cause }.last().message
                                    ?: "Delve request failed."
                            )
                            failed()
                        }
                    }
                }
            }
    }

    private fun ui(action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater {
            if (!session.project.isDisposed && !handler.isProcessTerminated) action()
        }
    }
}
