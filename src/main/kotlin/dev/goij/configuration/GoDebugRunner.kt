package dev.goij.configuration

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.xdebugger.*
import dev.goij.debugger.GoDebugProcess

class GoDebugRunner : GenericProgramRunner<RunnerSettings>() {
    override fun getRunnerId() = "StandaloneGoDebug"

    override fun canRun(executorId: String, profile: RunProfile) =
        executorId == DefaultDebugExecutor.EXECUTOR_ID && profile is GoRunConfiguration

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment) =
        XDebuggerManager.getInstance(environment.project)
            .newSessionBuilder(
                object : XDebugProcessStarter() {
                    override fun start(session: XDebugSession) =
                        GoDebugProcess(
                            session,
                            (environment.runProfile as GoRunConfiguration)::launchDebug,
                        )
                }
            )
            .environment(environment)
            .sessionName(environment.runProfile.name)
            .showTab(!environment.isHeadless)
            .startSession()
            .runContentDescriptor
}
