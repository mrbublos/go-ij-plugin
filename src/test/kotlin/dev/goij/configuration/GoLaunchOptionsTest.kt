package dev.goij.configuration

import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.util.Key
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import org.jdom.Element

class GoLaunchOptionsTest : BasePlatformTestCase() {
    fun testLaunchOptionsSurvivePersistence() {
        val configuration = configuration()
        configuration.packageDirectory = "/package with spaces"
        configuration.workingDirectory = "/runtime directory"
        configuration.programArguments = "one \"two words\""
        configuration.buildFlags = "-tags special"
        configuration.environment = mapOf("VALUE" to "a;b=c")
        val xml = Element("configuration")
        configuration.writeExternal(xml)
        val restored = configuration()
        restored.readExternal(xml)
        assertEquals(configuration.workingDirectory, restored.workingDirectory)
        assertEquals(configuration.programArguments, restored.programArguments)
        assertEquals(configuration.buildFlags, restored.buildFlags)
        assertEquals(configuration.environment, restored.environment)
    }

    fun testLaunchUsesSavedOptionsAndFreshSettings() {
        val directory = Files.createTempDirectory("go options package ")
        val runtime = Files.createTempDirectory("go runtime directory ")
        try {
            Files.writeString(
                directory.resolve("go.mod"),
                "module example.com/options\n\ngo 1.20\n",
            )
            Files.writeString(
                directory.resolve("main.go"),
                """
                    //go:build special
                    package main
                    import ("fmt"; "os")
                    func main() {
                        data, err := os.ReadFile("relative.txt"); if err != nil { panic(err) }
                        fmt.Printf("%q|%s|%s|%t", os.Args[1:], os.Getenv("GO_IJ_VALUE"), data, os.Getenv("HOME") != "")
                    }
                """
                    .trimIndent(),
            )
            Files.writeString(runtime.resolve("relative.txt"), "runtime file")
            val configuration = configuration()
            configuration.packageDirectory = directory.toString()
            configuration.workingDirectory = runtime.toString()
            configuration.programArguments = "one \"two words\""
            configuration.buildFlags = "-tags special"
            configuration.environment = mapOf("GO_IJ_VALUE" to "first")
            assertEquals("[\"one\" \"two words\"]|first|runtime file|true", launch(configuration))
            configuration.programArguments = "changed"
            configuration.environment = mapOf("GO_IJ_VALUE" to "second")
            assertEquals("[\"changed\"]|second|runtime file|true", launch(configuration))
            configuration.buildFlags = ""
            configuration.environment =
                mapOf("GO_IJ_VALUE" to "environment build", "GOFLAGS" to "-tags=special")
            assertEquals("[\"changed\"]|environment build|runtime file|true", launch(configuration))
        } finally {
            directory.toFile().deleteRecursively()
            runtime.toFile().deleteRecursively()
        }
    }

    fun testInspectionEnvironmentAndStderrDoNotChangePackageName() {
        val directory = Files.createTempDirectory("go inspection ")
        val settings = dev.goij.toolchain.GoToolchainSettings.getInstance().state
        val previous = settings.goExecutable
        try {
            val go = directory.resolve("go")
            Files.writeString(
                go,
                """
                    #!/bin/sh
                    [ "${'$'}GO_IJ_VALUE" = "expected" ] || exit 7
                    if [ "${'$'}1" = list ]; then echo 'downloading module' >&2; echo main; exit 0; fi
                    shift
                    while [ "${'$'}1" != -o ]; do shift; done
                    shift
                    printf '#!/bin/sh\necho built\n' > "${'$'}1"
                    chmod +x "${'$'}1"
                """
                    .trimIndent(),
            )
            go.toFile().setExecutable(true)
            settings.goExecutable = go.toString()
            val configuration = configuration()
            configuration.packageDirectory = directory.toString()
            configuration.environment = mapOf("GO_IJ_VALUE" to "expected")
            assertTrue(launch(configuration).contains("built"))
        } finally {
            settings.goExecutable = previous
            directory.toFile().deleteRecursively()
        }
    }

    fun testMissingWorkingDirectoryRejected() {
        val configuration = configuration()
        configuration.packageDirectory = "/tmp"
        configuration.workingDirectory = "/no/such/runtime/directory"
        try {
            configuration.checkConfiguration()
            fail("Missing working directory must be rejected")
        } catch (error: RuntimeConfigurationError) {
            assertTrue(error.message!!, error.message!!.contains("Working directory"))
        }
    }

    private fun launch(configuration: GoRunConfiguration): String {
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
        assertEquals(output.toString(), 0, process.exitCode)
        return output.toString()
    }

    private fun configuration() =
        ConfigurationTypeUtil.findConfigurationType(GoConfigurationType::class.java)
            .factory
            .createTemplateConfiguration(project) as GoRunConfiguration
}
