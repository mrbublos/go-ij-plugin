package dev.goij.configuration

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import dev.goij.execution.GoRunInput
import dev.goij.execution.GoRunProcessHandler
import dev.goij.toolchain.GoToolchainSettings
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import org.jdom.Element

class GoRunConfiguration(project: Project, factory: ConfigurationFactory, name: String) :
    RunConfigurationBase<Any>(project, factory, name) {
    var packageDirectory = ""

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        GoConfigurationEditor()

    override fun checkConfiguration() {
        if (packageDirectory.isBlank())
            throw RuntimeConfigurationError("Select a Go main-package directory.")
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        packageDirectory = element.getAttributeValue("packageDirectory").orEmpty()
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.setAttribute("packageDirectory", packageDirectory)
    }

    /**
     * Creates an unstarted, independent session; startNotify begins background build and launch.
     */
    fun launch(): GoRunProcessHandler =
        GoRunProcessHandler(
                GoRunInput(packageDirectory, GoToolchainSettings.getInstance().state.goExecutable)
            )
            .also { handler ->
                val lifetime = Disposable {
                    if (!handler.isProcessTerminated) handler.destroyProcess()
                }
                Disposer.register(project, lifetime)
                handler.addProcessListener(
                    object : ProcessListener {
                        override fun processTerminated(event: ProcessEvent) {
                            Disposer.dispose(lifetime)
                        }
                    }
                )
            }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        object : RunProfileState {
            override fun execute(executor: Executor, runner: ProgramRunner<*>) =
                launch().let { handler ->
                    val console =
                        TextConsoleBuilderFactory.getInstance().createBuilder(project).console
                    console.attachToProcess(handler)
                    DefaultExecutionResult(console, handler)
                }
        }
}

private class GoConfigurationEditor : SettingsEditor<GoRunConfiguration>() {
    private val directory = JTextField(40)

    override fun createEditor(): JComponent =
        JPanel(BorderLayout()).apply {
            add(JLabel("Main-package directory:"), BorderLayout.NORTH)
            add(directory, BorderLayout.CENTER)
        }

    override fun resetEditorFrom(configuration: GoRunConfiguration) {
        directory.text = configuration.packageDirectory
    }

    override fun applyEditorTo(configuration: GoRunConfiguration) {
        configuration.packageDirectory = directory.text.trim()
    }
}
