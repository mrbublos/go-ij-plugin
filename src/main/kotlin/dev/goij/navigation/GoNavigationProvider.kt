package dev.goij.navigation

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.ProjectWideLspClientDescriptor
import dev.goij.toolchain.GoTools

class GoNavigationProvider : LspIntegrationProvider {
    override fun fileOpened(
        project: Project,
        file: VirtualFile,
        clientStarter: LspIntegrationProvider.LspClientStarter,
    ) {
        if (file.isLocalGoSource()) clientStarter.ensureClientStarted(GoClientDescriptor(project))
    }
}

private fun VirtualFile.isLocalGoSource() = isInLocalFileSystem && !isDirectory && extension == "go"

private class GoClientDescriptor(project: Project) :
    ProjectWideLspClientDescriptor(project, "gopls") {
    override fun isSupportedFile(file: VirtualFile) = file.isLocalGoSource()

    override fun getLanguageId(file: VirtualFile) = "go"

    override fun createCommandLine() =
        GeneralCommandLine(GoTools.findGopls()).withWorkDirectory(project.basePath)
}
