package dev.goij.debugger

import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.goij.configuration.GoConfigurationType
import dev.goij.configuration.GoRunConfiguration
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class GoDebugDisposalTest : BasePlatformTestCase() {
    fun testProjectDisposalStopsOwnedDebugProcessAndRemovesExecutable() {
        val directory = Files.createTempDirectory("go debug closing ")
        val launchedProject =
            ProjectManagerEx.getInstanceEx()
                .newProject(
                    directory,
                    OpenProjectTask(isNewProject = true, useDefaultProjectAsTemplate = false),
                )!!
        try {
            Files.writeString(
                directory.resolve("go.mod"),
                "module example.com/closing\n\ngo 1.20\n",
            )
            Files.writeString(
                directory.resolve("main.go"),
                "package main\nimport (\"fmt\"; \"os\"; \"time\")\nfunc main() { executable, _ := os.Executable(); fmt.Println(executable); time.Sleep(time.Minute) }",
            )
            val configuration =
                ConfigurationTypeUtil.findConfigurationType(GoConfigurationType::class.java)
                    .factory
                    .createTemplateConfiguration(launchedProject) as GoRunConfiguration
            configuration.packageDirectory = directory.toString()
            val executable = CompletableFuture<String>()
            val process =
                configuration.launchDebug(
                    object : IDebugEvents {
                        override fun output(text: String) {
                            if (
                                text.contains("go-ij-debug-") && text.trim().endsWith("application")
                            )
                                executable.complete(text.trim())
                        }

                        override fun failed(error: Throwable) {
                            executable.completeExceptionally(error)
                        }
                    },
                    emptyMap(),
                )
            process.startNotify()
            val path = executable.get(60, TimeUnit.SECONDS)
            try {
                assertTrue(Files.exists(java.nio.file.Path.of(path)))
            } finally {
                ApplicationManager.getApplication().runWriteAction {
                    Disposer.dispose(launchedProject)
                }
            }
            assertTrue(process.waitFor(10000))
            assertEquals(130, process.exitCode)
            assertFalse(
                "Session executable survived project disposal",
                Files.exists(java.nio.file.Path.of(path)),
            )
        } finally {
            if (!launchedProject.isDisposed)
                ApplicationManager.getApplication().runWriteAction {
                    Disposer.dispose(launchedProject)
                }
            directory.toFile().deleteRecursively()
        }
    }
}
