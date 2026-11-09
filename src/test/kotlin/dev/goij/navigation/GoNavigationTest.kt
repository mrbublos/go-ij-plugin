package dev.goij.navigation

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.lsp.api.LspClientDescriptor
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files

class GoNavigationTest : BasePlatformTestCase() {
    fun testRegisteredProviderResolvesLocalAndImportedDeclarations() {
        val module = Files.createTempDirectory("go declaration test ")
        try {
            Files.writeString(module.resolve("go.mod"), "module example.org/test\n\ngo 1.20\n")
            Files.createDirectory(module.resolve("config"))
            Files.writeString(
                module.resolve("config/config.go"),
                "package config\nfunc InitConfig() {}\n",
            )
            val main = module.resolve("main.go")
            Files.writeString(
                main,
                "package main\nimport cfg \"example.org/test/config\"\nfunc loadEnv() {}\nfunc main() { loadEnv(); cfg.InitConfig() }\n",
            )
            val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(main)!!
            val provider =
                LspIntegrationProvider.EP_NAME.extensionList
                    .filterIsInstance<GoNavigationProvider>()
                    .single()
            lateinit var descriptor: LspClientDescriptor
            provider.fileOpened(
                project,
                file,
                object : LspIntegrationProvider.LspClientStarter {
                    override fun ensureClientStarted(clientDescriptor: LspClientDescriptor) {
                        descriptor = clientDescriptor
                    }
                },
            )
            assertTrue(descriptor.isSupportedFile(file))
            assertEquals("go", descriptor.getLanguageId(file))
            for ((position, target) in
                listOf("4:16" to "main.go:3:6", "4:31" to "config/config.go:2:6")) {
                val command =
                    descriptor
                        .createCommandLine()
                        .withWorkDirectory(module.toFile())
                        .withParameters("definition", "main.go:$position")
                val result = CapturingProcessHandler(command).runProcess(60000)
                assertEquals(result.stderr, 0, result.exitCode)
                assertTrue(result.stdout, result.stdout.contains(target))
            }
        } finally {
            module.toFile().deleteRecursively()
        }
    }
}
