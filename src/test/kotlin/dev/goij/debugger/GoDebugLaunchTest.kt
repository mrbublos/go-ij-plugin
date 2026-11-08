package dev.goij.debugger

import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.goij.configuration.GoConfigurationType
import dev.goij.configuration.GoRunConfiguration
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.eclipse.lsp4j.debug.*

class GoDebugLaunchTest : BasePlatformTestCase() {
    fun testDebugLaunchOptionsRejectedBreakpointAndBuildFailure() {
        val directory = java.nio.file.Files.createTempDirectory("go debug options ")
        val working = java.nio.file.Files.createTempDirectory("go debug runtime ")
        val settings = dev.goij.toolchain.GoToolchainSettings.getInstance().state
        val previousGo = settings.goExecutable
        val selectedGo = directory.resolve("custom-go")
        val marker = directory.resolve("selected-go-used")
        java.nio.file.Files.writeString(
            selectedGo,
            "#!/bin/sh\nprintf used >> '${marker}'\nexec '${dev.goij.toolchain.GoTools.findGo(previousGo)}' \"${'$'}@\"\n",
        )
        selectedGo.toFile().setExecutable(true)
        settings.goExecutable = selectedGo.toString()
        try {
            java.nio.file.Files.writeString(
                directory.resolve("go.mod"),
                "module example.com/debugoptions\n\ngo 1.20\n",
            )
            java.nio.file.Files.writeString(working.resolve("relative.txt"), "runtime-file")
            java.nio.file.Files.writeString(
                directory.resolve("main.go"),
                """
                    //go:build debugfixture
                    package main
                    import ("fmt"; "os")
                    func main() {
                        data, _ := os.ReadFile("relative.txt")
                        fmt.Printf("%s|%s|%s", os.Args[1], os.Getenv("GO_IJ_DEBUG"), data)
                    }
                """
                    .trimIndent(),
            )
            val factory =
                ConfigurationTypeUtil.findConfigurationType(GoConfigurationType::class.java)
                    .configurationFactories
                    .single()
            val configuration = factory.createTemplateConfiguration(project) as GoRunConfiguration
            configuration.packageDirectory = directory.toString()
            configuration.workingDirectory = working.toString()
            configuration.programArguments = "\"two words\""
            configuration.buildFlags = "-tags=debugfixture"
            configuration.environment = mapOf("GO_IJ_DEBUG" to "configured")
            val feedback = CompletableFuture<Array<Breakpoint>>()
            val output = StringBuffer()
            val handler =
                configuration.launchDebug(
                    object : IDebugEvents {
                        override fun breakpoints(
                            path: String,
                            lines: List<Int>,
                            results: Array<Breakpoint>,
                        ) {
                            feedback.complete(results)
                        }

                        override fun output(text: String) {
                            output.append(text)
                        }

                        override fun failed(error: Throwable) {
                            feedback.completeExceptionally(error)
                        }
                    },
                    mapOf(directory.resolve("main.go").toString() to listOf(2)),
                )
            handler.startNotify()
            try {
                assertFalse(feedback.get(60, TimeUnit.SECONDS).single().isVerified)
                assertTrue(handler.waitFor(15000))
                assertEquals(0, handler.exitCode)
                assertTrue(output.toString(), output.contains("two words|configured|runtime-file"))
                assertTrue(
                    "Explicitly selected custom Go executable was not used",
                    java.nio.file.Files.exists(marker),
                )
            } finally {
                handler.destroyProcess()
            }
            java.nio.file.Files.writeString(
                directory.resolve("main.go"),
                "package main\nfunc main() { invalid syntax }\n",
            )
            val failure = CompletableFuture<Throwable>()
            val failed =
                configuration.launchDebug(
                    object : IDebugEvents {
                        override fun failed(error: Throwable) {
                            failure.complete(error)
                        }
                    },
                    emptyMap(),
                )
            failed.startNotify()
            try {
                assertTrue(
                    failure
                        .get(60, TimeUnit.SECONDS)
                        .message
                        .orEmpty()
                        .contains("Build error", ignoreCase = true)
                )
                assertTrue(failed.waitFor(15000))
                assertEquals(1, failed.exitCode)
            } finally {
                failed.destroyProcess()
            }
        } finally {
            settings.goExecutable = previousGo
            directory.toFile().deleteRecursively()
            working.toFile().deleteRecursively()
        }
    }

    fun testInvalidDelveAndStopDuringStartupTerminateCleanly() {
        val settings = dev.goij.toolchain.GoToolchainSettings.getInstance().state
        val previous = settings.delveExecutable
        val factory =
            ConfigurationTypeUtil.findConfigurationType(GoConfigurationType::class.java)
                .configurationFactories
                .single()
        val configuration = factory.createTemplateConfiguration(project) as GoRunConfiguration
        configuration.packageDirectory =
            Path.of("src/test/testData/hello").toAbsolutePath().toString()
        try {
            settings.delveExecutable = "/missing/dlv"
            val failure = CompletableFuture<Throwable>()
            val invalid =
                configuration.launchDebug(
                    object : IDebugEvents {
                        override fun failed(error: Throwable) {
                            failure.complete(error)
                        }
                    },
                    emptyMap(),
                )
            invalid.startNotify()
            assertTrue(
                failure
                    .get(10, TimeUnit.SECONDS)
                    .message!!
                    .contains("Settings > Tools > Go Toolchain")
            )
            assertTrue(invalid.waitFor(10000))
            assertEquals(1, invalid.exitCode)
            val run = configuration.launch()
            run.startNotify()
            assertTrue(run.waitFor(30000))
            assertEquals(0, run.exitCode)
            settings.delveExecutable = previous
            val stopped = configuration.launchDebug(object : IDebugEvents {}, emptyMap())
            stopped.startNotify()
            stopped.destroyProcess()
            assertTrue(stopped.waitFor(10000))
            assertEquals(130, stopped.exitCode)
        } finally {
            settings.delveExecutable = previous
        }
    }

    fun testConfigurationLaunchHitsInitialBreakpointAndResumes() {
        val factory =
            ConfigurationTypeUtil.findConfigurationType(GoConfigurationType::class.java)
                .configurationFactories
                .single()
        val configuration = factory.createTemplateConfiguration(project) as GoRunConfiguration
        configuration.packageDirectory =
            Path.of("src/test/testData/hello").toAbsolutePath().toString()
        val file = Path.of(configuration.packageDirectory, "main.go").toString()
        val stopped = CompletableFuture<StoppedEventArguments>()
        val verified = CompletableFuture<Boolean>()
        val handler =
            configuration.launchDebug(
                object : IDebugEvents {
                    override fun stopped(event: StoppedEventArguments) {
                        stopped.complete(event)
                    }

                    override fun breakpoints(
                        path: String,
                        lines: List<Int>,
                        results: Array<Breakpoint>,
                    ) {
                        verified.complete(results.first().isVerified)
                    }

                    override fun failed(error: Throwable) {
                        stopped.completeExceptionally(error)
                    }
                },
                mapOf(file to listOf(5)),
            )
        try {
            handler.startNotify()
            val stop = stopped.get(60, TimeUnit.SECONDS)
            assertTrue(verified.get(5, TimeUnit.SECONDS))
            handler.connection!!
                .server
                .continue_(ContinueArguments().apply { threadId = stop.threadId })
                .get(5, TimeUnit.SECONDS)
            assertTrue(handler.waitFor(10000))
            assertEquals(0, handler.exitCode)
        } finally {
            handler.destroyProcess()
        }
    }
}
