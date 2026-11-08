package dev.goij.configuration

import com.intellij.execution.RunManager
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files

class GoContextTest : BasePlatformTestCase() {
    fun testFileContextTargetsContainingPackage() {
        val file = myFixture.addFileToProject("command/main.go", "package main")
        val context = ConfigurationContext(file)
        val produced = context.configuration
        assertNotNull("Go file must offer a run configuration", produced)
        val configuration = produced!!.configuration as GoRunConfiguration
        assertEquals(file.virtualFile.parent.path, configuration.packageDirectory)
        assertEquals("command", configuration.name)
    }

    fun testDirectoryContextAndReusePreserveSavedConfiguration() {
        val file = myFixture.addFileToProject("command/main.go", "package main")
        val directory = PsiManager.getInstance(project).findDirectory(file.virtualFile.parent)!!
        val first = ConfigurationContext(directory).configuration!!
        first.name = "My customized command"
        val saved = first.configuration as GoRunConfiguration
        saved.workingDirectory = "/custom runtime"
        saved.programArguments = "custom arguments"
        saved.buildFlags = "-tags custom"
        saved.environment = mapOf("CUSTOM" to "value")
        RunManager.getInstance(project).addConfiguration(first)
        assertSame(first, ConfigurationContext(file).findExisting())
        assertEquals("My customized command", first.name)
        assertSame(first, ConfigurationContext(directory).findExisting())
        assertEquals("/custom runtime", saved.workingDirectory)
        assertEquals("custom arguments", saved.programArguments)
        assertEquals("-tags custom", saved.buildFlags)
        assertEquals(mapOf("CUSTOM" to "value"), saved.environment)
    }

    fun testUnrelatedContextsDoNotOfferGo() {
        val file = myFixture.addFileToProject("notes/readme.txt", "hello")
        assertNull(ConfigurationContext(file).configuration)
        val directory = PsiManager.getInstance(project).findDirectory(file.virtualFile.parent)!!
        assertNull(ConfigurationContext(directory).configuration)
    }

    fun testContextRunBuildsSiblingFilesAndRejectsLibrary() {
        val directory = Files.createTempDirectory("go context run ")
        try {
            Files.writeString(
                directory.resolve("go.mod"),
                "module example.com/context\n\ngo 1.20\n",
            )
            Files.writeString(
                directory.resolve("main.go"),
                "package main\nimport \"fmt\"\nfunc main() { fmt.Println(message) }",
            )
            Files.writeString(
                directory.resolve("sibling.go"),
                "package main\nconst message = \"context whole package\"",
            )
            val virtualFile =
                LocalFileSystem.getInstance()
                    .refreshAndFindFileByNioFile(directory.resolve("main.go"))!!
            val file = PsiManager.getInstance(project).findFile(virtualFile)!!
            val configuration =
                ConfigurationContext(file).configuration!!.configuration as GoRunConfiguration
            val (exit, output) = launch(configuration)
            assertEquals(output, 0, exit)
            assertTrue(output, output.contains("context whole package"))
            Files.writeString(directory.resolve("main.go"), "package library\n")
            Files.writeString(directory.resolve("sibling.go"), "package library\n")
            val (libraryExit, libraryOutput) = launch(configuration)
            assertEquals(1, libraryExit)
            assertTrue(libraryOutput, libraryOutput.contains("not a Go main package"))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun launch(configuration: GoRunConfiguration): Pair<Int, String> {
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
            fail("Context launch timed out")
        }
        return process.exitCode!! to output.toString()
    }
}
