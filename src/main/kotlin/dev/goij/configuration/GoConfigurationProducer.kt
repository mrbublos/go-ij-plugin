package dev.goij.configuration

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import java.nio.file.Path

class GoConfigurationProducer : LazyRunConfigurationProducer<GoRunConfiguration>(), DumbAware {
    override fun getConfigurationFactory() =
        ConfigurationTypeUtil.findConfigurationType(GoConfigurationType::class.java).factory

    override fun setupConfigurationFromContext(
        configuration: GoRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val directory = packageDirectory(context) ?: return false
        configuration.packageDirectory = directory.path
        configuration.name = directory.name
        return true
    }

    override fun isConfigurationFromContext(
        configuration: GoRunConfiguration,
        context: ConfigurationContext,
    ): Boolean {
        val directory = packageDirectory(context) ?: return false
        return runCatching {
                Path.of(configuration.packageDirectory).toAbsolutePath().normalize() ==
                    Path.of(directory.path).toAbsolutePath().normalize()
            }
            .getOrDefault(false)
    }

    private fun packageDirectory(context: ConfigurationContext): VirtualFile? {
        if (context.containsMultipleSelection()) return null
        val element = context.psiLocation ?: return null
        val file =
            (element as? PsiDirectory)?.virtualFile
                ?: element.containingFile?.virtualFile
                ?: return null
        if (!file.isInLocalFileSystem) return null
        return if (file.isDirectory) {
            file.takeIf {
                it.children.any { child -> !child.isDirectory && child.extension == "go" }
            }
        } else {
            file.parent.takeIf { file.extension == "go" }
        }
    }
}
