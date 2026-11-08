package dev.goij.configuration

import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.goij.toolchain.GoToolchainSettings
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.jdom.Element

class GoConfigurationTest : BasePlatformTestCase() {
    fun testLaunchBuildsWholePackageWithSpaces() {
        val directory = Files.createTempDirectory("go package with spaces ")
        try {
            Files.list(Path.of("src/test/testData/hello")).use { paths ->
                paths.forEach { Files.copy(it, directory.resolve(it.fileName)) }
            }
            val configuration =
                ConfigurationTypeUtil.findConfigurationType(GoConfigurationType::class.java)
                    .factory
                    .createTemplateConfiguration(project) as GoRunConfiguration
            configuration.packageDirectory = directory.toString()
            val stored = Element("configuration")
            configuration.writeExternal(stored)
            val restored =
                ConfigurationTypeUtil.findConfigurationType(GoConfigurationType::class.java)
                    .factory
                    .createTemplateConfiguration(project) as GoRunConfiguration
            restored.readExternal(stored)
            assertTrue(
                PluginManagerCore.getPlugin(PluginId.getId("dev.goij.standalone"))!!.isEnabled
            )
            assertTrue(
                PluginManagerCore.getPlugin(PluginId.getId("org.jetbrains.plugins.go"))
                    ?.isEnabled != true
            )
            val output = StringBuffer()
            val process = restored.launch()
            process.addProcessListener(
                object : ProcessListener {
                    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                        output.append(event.text)
                    }
                }
            )
            process.startNotify()
            assertTrue(process.waitFor(60000))
            assertEquals(output.toString(), 0, process.exitCode)
            assertTrue(output.toString(), output.contains("whole package works"))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    fun testSavedPackageSurvivesPersistence() {
        val first =
            ConfigurationTypeUtil.findConfigurationType(GoConfigurationType::class.java)
                .factory
                .createTemplateConfiguration(project) as GoRunConfiguration
        first.packageDirectory = "/a package/with spaces"
        val xml = Element("configuration")
        first.writeExternal(xml)
        val restored =
            ConfigurationTypeUtil.findConfigurationType(GoConfigurationType::class.java)
                .factory
                .createTemplateConfiguration(project) as GoRunConfiguration
        restored.readExternal(xml)
        assertEquals(first.packageDirectory, restored.packageDirectory)
    }

    fun testMissingGoIsActionable() {
        val settings = GoToolchainSettings.getInstance().state
        val previous = settings.goExecutable
        try {
            settings.goExecutable = "/no/such/go"
            val (code, output) = runSource("package main\nfunc main() {}")
            assertEquals(1, code)
            assertTrue(output, output.contains("Settings > Tools > Go Toolchain"))
        } finally {
            settings.goExecutable = previous
        }
    }

    fun testNonMainPackageRejected() {
        val (code, output) = runSource("package library\nfunc Value() int { return 1 }")
        assertEquals(1, code)
        assertTrue(output, output.contains("not a Go main package"))
    }

    fun testBuildFailureShowsCompilerOutput() {
        val (code, output) = runSource("package main\nfunc main() { undefinedFunction() }")
        assertTrue(code != 0)
        assertTrue(output, output.contains("undefined"))
    }

    fun testApplicationExitCodePreserved() {
        val (code, _) = runSource("package main\nimport \"os\"\nfunc main() { os.Exit(23) }")
        assertEquals(23, code)
    }

    fun testStopAndRerunUseIndependentSessions() {
        val directory = Files.createTempDirectory("go stop test ")
        try {
            Files.writeString(directory.resolve("go.mod"), "module example.com/stop\n\ngo 1.20\n")
            Files.writeString(
                directory.resolve("main.go"),
                "package main\nimport (\"fmt\"; \"time\"; \"os\")\nfunc main() { executable, _ := os.Executable(); fmt.Println(executable); time.Sleep(time.Minute) }",
            )
            val configuration =
                ConfigurationTypeUtil.findConfigurationType(GoConfigurationType::class.java)
                    .factory
                    .createTemplateConfiguration(project) as GoRunConfiguration
            configuration.packageDirectory = directory.toString()
            val executables = mutableSetOf<String>()
            repeat(2) {
                val ready = CountDownLatch(1)
                val output = StringBuffer()
                val process = configuration.launch()
                process.addProcessListener(
                    object : ProcessListener {
                        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                            output.append(event.text)
                            if (output.contains("application")) ready.countDown()
                        }
                    }
                )
                process.startNotify()
                try {
                    assertTrue(output.toString(), ready.await(60, TimeUnit.SECONDS))
                } finally {
                    process.destroyProcess()
                }
                assertTrue(process.waitFor(10000))
                assertEquals(130, process.exitCode)
                val executable = output.toString().trim()
                assertFalse(Files.exists(Path.of(executable)))
                assertTrue("Rerun must create a fresh executable", executables.add(executable))
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    fun testStopBeforeStartTerminates() {
        val configuration =
            ConfigurationTypeUtil.findConfigurationType(GoConfigurationType::class.java)
                .factory
                .createTemplateConfiguration(project) as GoRunConfiguration
        val process = configuration.launch()
        process.destroyProcess()
        process.startNotify()
        assertTrue(process.waitFor(10000))
        assertEquals(130, process.exitCode)
    }

    fun testStopDuringBuild() {
        val directory = Files.createTempDirectory("go build cancellation ")
        val settings = GoToolchainSettings.getInstance().state
        val previous = settings.goExecutable
        try {
            val go = directory.resolve("go")
            Files.writeString(
                go,
                "#!/bin/sh\nif [ \"\$1\" = list ]; then echo main; exit 0; fi\necho building\nsleep 60\n",
            )
            go.toFile().setExecutable(true)
            settings.goExecutable = go.toString()
            val configuration =
                ConfigurationTypeUtil.findConfigurationType(GoConfigurationType::class.java)
                    .factory
                    .createTemplateConfiguration(project) as GoRunConfiguration
            configuration.packageDirectory = directory.toString()
            val ready = CountDownLatch(1)
            val process = configuration.launch()
            process.addProcessListener(
                object : ProcessListener {
                    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                        if (event.text.contains("building")) ready.countDown()
                    }
                }
            )
            process.startNotify()
            try {
                assertTrue(ready.await(10, TimeUnit.SECONDS))
            } finally {
                process.destroyProcess()
            }
            assertTrue(process.waitFor(10000))
            assertEquals(130, process.exitCode)
        } finally {
            settings.goExecutable = previous
            directory.toFile().deleteRecursively()
        }
    }

    fun testProjectDisposalStopsLaunch() {
        val directory = Files.createTempDirectory("go closing project ")
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
                "package main\nimport (\"fmt\"; \"time\")\nfunc main() { fmt.Println(\"ready\"); time.Sleep(time.Minute) }",
            )
            val configuration =
                ConfigurationTypeUtil.findConfigurationType(GoConfigurationType::class.java)
                    .factory
                    .createTemplateConfiguration(launchedProject) as GoRunConfiguration
            configuration.packageDirectory = directory.toString()
            val ready = CountDownLatch(1)
            val process = configuration.launch()
            process.addProcessListener(
                object : ProcessListener {
                    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                        if (event.text.contains("ready")) ready.countDown()
                    }
                }
            )
            process.startNotify()
            try {
                assertTrue(ready.await(60, TimeUnit.SECONDS))
            } finally {
                ApplicationManager.getApplication().runWriteAction {
                    Disposer.dispose(launchedProject)
                }
            }
            assertTrue(process.waitFor(10000))
            assertEquals(130, process.exitCode)
        } finally {
            if (!launchedProject.isDisposed)
                ApplicationManager.getApplication().runWriteAction {
                    Disposer.dispose(launchedProject)
                }
            directory.toFile().deleteRecursively()
        }
    }

    private fun runSource(source: String): Pair<Int, String> {
        val directory = Files.createTempDirectory("go launch test ")
        try {
            Files.writeString(directory.resolve("go.mod"), "module example.com/test\n\ngo 1.20\n")
            Files.writeString(directory.resolve("main.go"), source)
            val configuration =
                ConfigurationTypeUtil.findConfigurationType(GoConfigurationType::class.java)
                    .factory
                    .createTemplateConfiguration(project) as GoRunConfiguration
            configuration.packageDirectory = directory.toString()
            val output = StringBuffer()
            val process = configuration.launch()
            process.addProcessListener(
                object : ProcessListener {
                    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                        output.append(event.text)
                    }
                }
            )
            process.startNotify()
            if (!process.waitFor(60000)) {
                process.destroyProcess()
                fail("Launch timed out")
            }
            return process.exitCode!! to output.toString()
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
