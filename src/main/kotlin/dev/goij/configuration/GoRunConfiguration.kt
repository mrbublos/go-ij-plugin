package dev.goij.configuration

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configuration.EnvironmentVariablesDialog
import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton
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
import com.intellij.util.execution.ParametersListUtil
import dev.goij.debugger.GoDebugInput
import dev.goij.debugger.GoDebugProcessHandler
import dev.goij.debugger.IDebugEvents
import dev.goij.execution.GoRunInput
import dev.goij.execution.GoRunProcessHandler
import dev.goij.toolchain.GoToolchainSettings
import java.awt.GridLayout
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import org.jdom.Element

class GoRunConfiguration(project: Project, factory: ConfigurationFactory, name: String) :
    RunConfigurationBase<Any>(project, factory, name) {
    var packageDirectory = ""
    var workingDirectory = ""
    var programArguments = ""
    var buildFlags = ""
    var environment: Map<String, String> = emptyMap()

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        GoConfigurationEditor(project)

    override fun checkConfiguration() {
        if (packageDirectory.isBlank())
            throw RuntimeConfigurationError("Select a Go main-package directory.")
        if (workingDirectory.isNotBlank() && !Files.isDirectory(Path.of(workingDirectory)))
            throw RuntimeConfigurationError(
                "Working directory must be an existing directory: $workingDirectory"
            )
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        packageDirectory = element.getAttributeValue("packageDirectory").orEmpty()
        workingDirectory = element.getAttributeValue("workingDirectory").orEmpty()
        programArguments = element.getAttributeValue("programArguments").orEmpty()
        buildFlags = element.getAttributeValue("buildFlags").orEmpty()
        environment =
            element
                .getChild("environment")
                ?.getChildren("variable")
                ?.associate { it.getAttributeValue("name") to it.getAttributeValue("value") }
                .orEmpty()
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.setAttribute("packageDirectory", packageDirectory)
        element.setAttribute("workingDirectory", workingDirectory)
        element.setAttribute("programArguments", programArguments)
        element.setAttribute("buildFlags", buildFlags)
        element.removeChildren("environment")
        element.addContent(
            Element("environment").apply {
                environment.forEach { (name, value) ->
                    addContent(
                        Element("variable").setAttribute("name", name).setAttribute("value", value)
                    )
                }
            }
        )
    }

    /**
     * Creates an unstarted, independent session; startNotify begins background build and launch.
     */
    fun launch(): GoRunProcessHandler =
        GoRunProcessHandler(
                GoRunInput(
                    packageDirectory,
                    GoToolchainSettings.getInstance().state.goExecutable,
                    workingDirectory.ifBlank { packageDirectory },
                    ParametersListUtil.parse(programArguments),
                    ParametersListUtil.parse(buildFlags),
                    environment.toMap(),
                )
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

    fun launchDebug(
        events: IDebugEvents,
        breakpoints: Map<String, List<Int>>,
    ): GoDebugProcessHandler =
        GoDebugProcessHandler(
                GoDebugInput(
                    packageDirectory,
                    GoToolchainSettings.getInstance().state.goExecutable,
                    GoToolchainSettings.getInstance().state.delveExecutable,
                    workingDirectory.ifBlank { packageDirectory },
                    ParametersListUtil.parse(programArguments),
                    buildFlags,
                    environment.toMap(),
                ),
                events,
                breakpoints,
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

private class GoConfigurationEditor(project: Project) : SettingsEditor<GoRunConfiguration>() {
    private val directory = JTextField(40)
    private val workingDirectory = JTextField(40)
    private val arguments = JTextField(40)
    private val flags = JTextField(40)
    private val environment =
        object : EnvironmentVariablesTextFieldWithBrowseButton(project) {
            override fun createDialog() = object : EnvironmentVariablesDialog(this, true) {}
        }

    override fun createEditor(): JComponent =
        JPanel(GridLayout(0, 2, 8, 8)).apply {
            add(JLabel("Main-package directory:"))
            add(directory)
            add(JLabel("Working directory (blank uses package):"))
            add(workingDirectory)
            add(JLabel("Program arguments:"))
            add(arguments)
            add(JLabel("Build flags:"))
            add(flags)
            add(JLabel("Environment overrides:"))
            add(environment)
        }

    override fun resetEditorFrom(configuration: GoRunConfiguration) {
        directory.text = configuration.packageDirectory
        workingDirectory.text = configuration.workingDirectory
        arguments.text = configuration.programArguments
        flags.text = configuration.buildFlags
        environment.envs = configuration.environment
        environment.isPassParentEnvs = true
    }

    override fun applyEditorTo(configuration: GoRunConfiguration) {
        configuration.packageDirectory = directory.text.trim()
        configuration.workingDirectory = workingDirectory.text.trim()
        configuration.programArguments = arguments.text
        configuration.buildFlags = flags.text
        configuration.environment = environment.envs.toMap()
    }
}
