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

class GoNativeInspectionTest : BasePlatformTestCase() {
    fun testInspectSelectStepPauseAndConsecutiveSessions() {
        com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(
            testRootDisposable,
            ProcessBuilder(dev.goij.toolchain.GoTools.findGo(""), "env", "GOROOT")
                .start()
                .inputStream
                .bufferedReader()
                .readText()
                .trim(),
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
                    Path.of("src/test/testData/inspect/main.go").toAbsolutePath().toString()
                )!!
        assertTrue(type.canPutAt(file, 24, project))
        val breakpoint =
            WriteAction.compute<
                com.intellij.xdebugger.breakpoints.XLineBreakpoint<GoBreakpointProperties>,
                RuntimeException,
            > {
                manager.breakpointManager.addLineBreakpoint(
                    type,
                    file.url,
                    24,
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
                assertEquals(24, active.currentPosition!!.line)
                inspectAndStep(active)
                active.stop()
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

    private fun inspectAndStep(session: XDebugSession) {
        val context = session.suspendContext!!
        val stacks = StackList()
        context.computeExecutionStacks(stacks)
        await { stacks.done }
        assertTrue(stacks.items.size > 1)
        val mainStack = context.activeExecutionStack!!
        val mainFrames = FrameList()
        mainStack.computeStackFrames(0, mainFrames)
        await { mainFrames.done }
        val frame = mainFrames.items.first()
        assertTrue(mainFrames.items.size > 1)
        val caller = mainFrames.items[1]
        session.setCurrentStackFrame(mainStack, caller, true)
        assertSame(caller, session.currentStackFrame)
        assertEquals(caller.sourcePosition!!.file, session.currentPosition!!.file)
        assertTrue(session.currentPosition!!.file.path.endsWith("runtime/proc.go"))
        session.setCurrentStackFrame(mainStack, frame, true)
        val scopes = Children()
        frame.computeChildren(scopes)
        await { scopes.done }
        val locals = Children()
        scopes.items!!.getValue(0).computeChildren(locals)
        await { locals.done }
        val names = (0 until locals.items!!.size()).map { locals.items!!.getName(it) }
        assertTrue(names.toString(), names.contains("record"))
        val fields = Children()
        locals.items!!.getValue(names.indexOf("record")).computeChildren(fields)
        await { fields.done }
        assertTrue((0 until fields.items!!.size()).any { fields.items!!.getName(it) == "Count" })
        val countIndex =
            (0 until fields.items!!.size()).first { fields.items!!.getName(it) == "Count" }
        val presentation = ValuePresentation()
        fields.items!!
            .getValue(countIndex)
            .computePresentation(presentation, com.intellij.xdebugger.frame.XValuePlace.TREE)
        assertEquals("41", presentation.value)
        val other = stacks.items.first { it !== mainStack }
        val otherFrames = FrameList()
        other.computeStackFrames(0, otherFrames)
        await { otherFrames.done }
        assertTrue(otherFrames.items.isNotEmpty())
        session.setCurrentStackFrame(other, otherFrames.items.first(), true)
        assertSame(otherFrames.items.first(), session.currentStackFrame)
        session.setCurrentStackFrame(mainStack, frame, true)
        step(session) { session.stepInto() }
        assertTrue(
            "Step into should enter increment: ${session.currentPosition?.line}",
            session.currentPosition!!.line in 14..16,
        )
        step(session) { session.stepOver(false) }
        assertEquals(15, session.currentPosition!!.line)
        step(session) { session.stepOut() }
        assertTrue(session.currentPosition!!.line in 24..25)
        session.resume()
        await { !session.isSuspended }
        session.pause()
        await { session.isSuspended }
        assertFalse(session.debugProcess.processHandler.isProcessTerminated)
    }

    private fun step(session: XDebugSession, action: () -> Unit) {
        val previous = session.suspendContext
        action()
        await { session.isSuspended && session.suspendContext !== previous }
    }

    private fun await(condition: () -> Boolean) =
        PlatformTestUtil.waitWithEventsDispatching("Native debugger inspection", condition, 30)
}
