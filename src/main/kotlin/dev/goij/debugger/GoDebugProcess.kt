package dev.goij.debugger

import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.vfs.LocalFileSystem
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
    private var threadId = 0
    private var generation = 0
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

    private fun onStopped(event: StoppedEventArguments) {
        ui {
            threadId = event.threadId ?: 1
            val current = ++generation
            val stoppedThread = threadId
            val dap = handler.connection ?: return@ui
            request({
                dap.server.stackTrace(
                    StackTraceArguments().apply {
                        threadId = stoppedThread
                        startFrame = 0
                        levels = 1
                    }
                )
            }) { response ->
                if (current != generation) return@request
                val frame = response.stackFrames.firstOrNull()
                val source =
                    frame?.source?.path?.let { LocalFileSystem.getInstance().findFileByPath(it) }
                val position =
                    source?.let {
                        XDebuggerUtil.getInstance()
                            .createPosition(it, (frame.line - 1).coerceAtLeast(0))
                    }
                val stackFrame =
                    object : XStackFrame() {
                        override fun getSourcePosition() = position
                    }
                val stack =
                    object : XExecutionStack("Goroutine $threadId") {
                        override fun getTopFrame() = stackFrame

                        override fun computeStackFrames(
                            firstFrameIndex: Int,
                            container: XStackFrameContainer,
                        ) {
                            container.addStackFrames(
                                if (firstFrameIndex == 0) listOf(stackFrame) else emptyList(),
                                true,
                            )
                        }
                    }
                session.positionReached(
                    object : XSuspendContext() {
                        override fun getActiveExecutionStack() = stack
                    }
                )
            }
        }
    }

    override fun resume(context: XSuspendContext?) {
        val dap = handler.connection ?: return
        val current = ++generation
        val resumedThread = threadId
        request({ dap.server.continue_(ContinueArguments().apply { threadId = resumedThread }) }) {
            if (current == generation) session.sessionResumed()
        }
    }

    override fun stop() {
        generation++
        handler.destroyProcess()
    }

    private fun <T> request(operation: () -> CompletableFuture<T>, action: (T) -> Unit) {
        CompletableFuture.supplyAsync(operation)
            .thenCompose { it }
            .whenComplete { value, error ->
                if (error != null) handler.fail(error) else ui { action(value) }
            }
    }

    private fun ui(action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater {
            if (!session.project.isDisposed && !handler.isProcessTerminated) action()
        }
    }
}
