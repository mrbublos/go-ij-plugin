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
    fun testBreakpointUpdatesReturnWhileAdapterDisconnectIsPending() {
        val directory = Files.createTempDirectory("go delayed disconnect ")
        val settings = dev.goij.toolchain.GoToolchainSettings.getInstance().state
        val previousDelve = settings.delveExecutable
        java.net.ServerSocket(0).use { listener ->
            val adapter = directory.resolve("delayed-dlv")
            Files.writeString(
                adapter,
                "#!/bin/sh\necho 'DAP server listening at: 127.0.0.1:${listener.localPort}'\nexec /bin/sleep 60\n",
            )
            adapter.toFile().setExecutable(true)
            settings.delveExecutable = adapter.toString()
            val initialized = CompletableFuture<Void>()
            val disconnecting = CompletableFuture<Void>()
            val disconnected = CompletableFuture<Void>()
            val server =
                object : org.eclipse.lsp4j.debug.services.IDebugProtocolServer {
                    override fun initialize(
                        args: org.eclipse.lsp4j.debug.InitializeRequestArguments
                    ) =
                        CompletableFuture.completedFuture(org.eclipse.lsp4j.debug.Capabilities())
                            .also { initialized.complete(null) }

                    override fun launch(args: Map<String, Any>) = CompletableFuture<Void>()

                    override fun disconnect(args: org.eclipse.lsp4j.debug.DisconnectArguments) =
                        disconnected.also { disconnecting.complete(null) }
                }
            val factory =
                ConfigurationTypeUtil.findConfigurationType(GoConfigurationType::class.java).factory
            val configuration = factory.createTemplateConfiguration(project) as GoRunConfiguration
            configuration.packageDirectory =
                java.nio.file.Path.of("src/test/testData/hello").toAbsolutePath().toString()
            val process = configuration.launchDebug(object : IDebugEvents {}, emptyMap())
            try {
                listener.soTimeout = 30000
                process.startNotify()
                listener.accept().use { socket ->
                    org.eclipse.lsp4j.debug.launch.DSPLauncher.createServerLauncher(
                            server,
                            socket.getInputStream(),
                            socket.getOutputStream(),
                        )
                        .startListening()
                    initialized.get(10, TimeUnit.SECONDS)
                    process.destroyProcess()
                    disconnecting.get(10, TimeUnit.SECONDS)
                    try {
                        CompletableFuture.runAsync {
                                process.updateBreakpoints("/tmp/main.go", listOf(5))
                            }
                            .get(500, TimeUnit.MILLISECONDS)
                    } finally {
                        disconnected.complete(null)
                    }
                    assertTrue(process.waitFor(10000))
                    assertEquals(130, process.exitCode)
                }
            } finally {
                disconnected.complete(null)
                process.destroyProcess()
                process.waitFor(10000)
                settings.delveExecutable = previousDelve
                directory.toFile().deleteRecursively()
            }
        }
    }

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
