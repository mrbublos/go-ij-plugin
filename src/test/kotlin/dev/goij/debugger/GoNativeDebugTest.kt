package dev.goij.debugger

import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.xdebugger.*
import com.intellij.xdebugger.breakpoints.XBreakpointType
import dev.goij.configuration.GoConfigurationType
import dev.goij.configuration.GoRunConfiguration
import java.nio.file.Path

class GoNativeDebugTest : BasePlatformTestCase() {
    fun testRegisteredRunnerStopsAtNativeGoBreakpointAndReruns() {
        com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(
            testRootDisposable,
            Path.of("src/test/testData").toAbsolutePath().toString(),
        )
        val manager = XDebuggerManager.getInstance(project)
        val type =
            XBreakpointType.EXTENSION_POINT_NAME.extensionList
                .filterIsInstance<GoLineBreakpointType>()
                .single()
        val file =
            LocalFileSystem.getInstance()
                .refreshAndFindFileByPath(
                    Path.of("src/test/testData/hello/main.go").toAbsolutePath().toString()
                )!!
        assertTrue(type.canPutAt(file, 4, project))
        val breakpoint =
            WriteAction.compute<
                com.intellij.xdebugger.breakpoints.XLineBreakpoint<GoBreakpointProperties>,
                RuntimeException,
            > {
                manager.breakpointManager.addLineBreakpoint(
                    type,
                    file.url,
                    4,
                    GoBreakpointProperties(),
                )
            }
        val settings =
            RunManager.getInstance(project)
                .createConfiguration("Debug sample", GoConfigurationType::class.java)
        (settings.configuration as GoRunConfiguration).packageDirectory = file.parent.path
        val runner =
            ProgramRunner.getRunner(DefaultDebugExecutor.EXECUTOR_ID, settings.configuration)
        assertNotNull(runner)
        var session: XDebugSession? = null
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
        try {
            repeat(2) { iteration ->
                session = null
                val env =
                    ExecutionEnvironmentBuilder.create(
                            DefaultDebugExecutor.getDebugExecutorInstance(),
                            settings,
                        )
                        .build()
                env.setHeadless()
                runner!!.execute(env)
                PlatformTestUtil.waitWithEventsDispatching(
                    "Native breakpoint was not reached",
                    { session?.isSuspended == true },
                    60,
                )
                val active = session!!
                assertEquals(file.path, active.currentPosition!!.file.path)
                assertEquals(4, active.currentPosition!!.line)
                if (iteration == 0) active.resume() else active.stop()
                PlatformTestUtil.waitWithEventsDispatching(
                    "Debugger did not terminate",
                    { active.debugProcess.processHandler.isProcessTerminated },
                    15,
                )
                com.intellij.openapi.util.Disposer.dispose(active.consoleView)
                com.intellij.openapi.util.Disposer.dispose(active.runContentDescriptor)
                com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).let { editors
                    ->
                    editors.openFiles.forEach { editors.closeFile(it) }
                }
            }
        } finally {
            session?.stop()
            WriteAction.run<RuntimeException> {
                manager.breakpointManager.removeBreakpoint(breakpoint)
            }
        }
    }
}
