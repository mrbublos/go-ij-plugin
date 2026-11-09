package dev.goij.configuration

import com.intellij.execution.RunManager
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.xdebugger.*
import java.nio.file.Files

class GoContextDebugTest : BasePlatformTestCase() {
    fun testContextDebugRejectsLibraryPackage() {
        val directory = Files.createTempDirectory("go context library ")
        try {
            Files.writeString(
                directory.resolve("go.mod"),
                "module example.com/library\n\ngo 1.20\n",
            )
            Files.writeString(directory.resolve("library.go"), "package library\n")
            val file =
                LocalFileSystem.getInstance()
                    .refreshAndFindFileByNioFile(directory.resolve("library.go"))!!
            val psi = PsiManager.getInstance(project).findFile(file)!!
            val configuration =
                ConfigurationContext(psi).configuration!!.configuration as GoRunConfiguration
            val failure = java.util.concurrent.CompletableFuture<Throwable>()
            val handler =
                configuration.launchDebug(
                    object : dev.goij.debugger.IDebugEvents {
                        override fun failed(error: Throwable) {
                            failure.complete(error)
                        }
                    },
                    emptyMap(),
                )
            try {
                handler.startNotify()
                assertTrue(
                    failure
                        .get(30, java.util.concurrent.TimeUnit.SECONDS)
                        .message
                        .orEmpty()
                        .contains("not a Go main package")
                )
                assertTrue(handler.waitFor(10000))
                assertEquals(1, handler.exitCode)
            } finally {
                handler.destroyProcess()
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    fun testFileAndDirectoryDebugReuseCustomOptionsAndWholePackage() {
        val directory = Files.createTempDirectory("go context debug ")
        val runtime = Files.createTempDirectory("go context runtime ")
        var session: XDebugSession? = null
        val output = StringBuffer()
        project.messageBus
            .connect(testRootDisposable)
            .subscribe(
                XDebuggerManager.TOPIC,
                object : XDebuggerManagerListener {
                    override fun processStarted(process: XDebugProcess) {
                        session = process.session
                        process.processHandler.addProcessListener(
                            object : com.intellij.execution.process.ProcessListener {
                                override fun onTextAvailable(
                                    event: com.intellij.execution.process.ProcessEvent,
                                    outputType: com.intellij.openapi.util.Key<*>,
                                ) {
                                    output.append(event.text)
                                }
                            }
                        )
                    }
                },
            )
        try {
            Files.writeString(
                directory.resolve("go.mod"),
                "module example.com/contextdebug\n\ngo 1.20\n",
            )
            Files.writeString(
                directory.resolve("main.go"),
                """
                    //go:build contextdebug
                    package main
                    import ("fmt"; "os"; "time")
                    func main() {
                        data, err := os.ReadFile("relative.txt"); if err != nil { panic(err) }
                        fmt.Fprint(os.Stderr, "stderr ready")
                        fmt.Printf("%s|%s|%s|%s", message, os.Args[1], os.Getenv("GO_CONTEXT"), data)
                        for { if _, err := os.Stat("finish"); err == nil { break }; time.Sleep(10 * time.Millisecond) }
                    }
                """
                    .trimIndent(),
            )
            Files.writeString(
                directory.resolve("sibling.go"),
                "package main\nconst message = \"whole package\"",
            )
            Files.writeString(runtime.resolve("relative.txt"), "runtime file")
            val virtualFile =
                LocalFileSystem.getInstance()
                    .refreshAndFindFileByNioFile(directory.resolve("main.go"))!!
            val file = PsiManager.getInstance(project).findFile(virtualFile)!!
            val packagePsi = PsiManager.getInstance(project).findDirectory(virtualFile.parent)!!
            val settings = ConfigurationContext(file).configuration!!
            val configuration = settings.configuration as GoRunConfiguration
            configuration.workingDirectory = runtime.toString()
            configuration.programArguments = "\"two words\""
            configuration.environment = mapOf("GO_CONTEXT" to "custom environment")
            configuration.buildFlags = "-tags=contextdebug"
            RunManager.getInstance(project).addConfiguration(settings)
            for (element in listOf(file, packagePsi)) {
                val reused = ConfigurationContext(element).findExisting()!!
                assertSame(settings, reused)
                val runner =
                    ProgramRunner.getRunner(
                        DefaultDebugExecutor.EXECUTOR_ID,
                        reused.configuration,
                    )!!
                val environment =
                    ExecutionEnvironmentBuilder.create(
                            DefaultDebugExecutor.getDebugExecutorInstance(),
                            reused,
                        )
                        .build()
                if (element == packagePsi) environment.setHeadless()
                session = null
                output.setLength(0)
                Files.deleteIfExists(runtime.resolve("finish"))
                runner.execute(environment)
                if (element == file) {
                    PlatformTestUtil.waitWithEventsDispatching(
                        "Debug console did not display live stdout and stderr",
                        {
                            val console = session?.consoleView as? com.intellij.execution.impl.ConsoleViewImpl
                            console?.component
                            val text = console?.editor?.document?.text.orEmpty()
                            text.contains("stderr ready") && text.contains("whole package|two words")
                        },
                        15,
                    )
                }
                Files.createFile(runtime.resolve("finish"))
                PlatformTestUtil.waitWithEventsDispatching(
                    "Context debug did not finish",
                    { session?.debugProcess?.processHandler?.isProcessTerminated == true },
                    60,
                )
                val active = session!!
                assertEquals(configuration.name, active.sessionName)
                assertEquals(output.toString(), 0, active.debugProcess.processHandler.exitCode)
                assertTrue(
                    output.toString(),
                    output.contains("whole package|two words|custom environment|runtime file"),
                )
                Disposer.dispose(active.consoleView)
                Disposer.dispose(active.runContentDescriptor)
            }
            assertEquals(
                1,
                RunManager.getInstance(project).allSettings.count {
                    it.configuration is GoRunConfiguration
                },
            )
        } finally {
            session?.stop()
            directory.toFile().deleteRecursively()
            runtime.toFile().deleteRecursively()
        }
    }
}
