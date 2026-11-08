package dev.goij.configuration

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.DefaultProgramRunner

class GoRunRunner : DefaultProgramRunner() {
    override fun getRunnerId() = "StandaloneGoRun"

    override fun canRun(executorId: String, profile: RunProfile) =
        executorId == DefaultRunExecutor.EXECUTOR_ID && profile is GoRunConfiguration
}
