package dev.goij.configuration

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project

class GoConfigurationType : ConfigurationType {
    val factory =
        object : ConfigurationFactory(this) {
            override fun getId() = "Go Application"

            override fun createTemplateConfiguration(project: Project): RunConfiguration =
                GoRunConfiguration(project, this, "Go Application")
        }

    override fun getDisplayName() = "Go Application"

    override fun getConfigurationTypeDescription() = "Build and run a Go main package"

    override fun getIcon() = AllIcons.RunConfigurations.Application

    override fun getId() = "StandaloneGoApplication"

    override fun getConfigurationFactories() = arrayOf(factory)
}
