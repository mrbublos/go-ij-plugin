package dev.goij.toolchain

import com.intellij.openapi.options.Configurable
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

class GoToolchainConfigurable : Configurable {
    private val go = JTextField(40)

    override fun getDisplayName() = "Go Toolchain"

    override fun createComponent(): JComponent =
        JPanel(BorderLayout()).apply {
            add(JLabel("Go executable (blank detects automatically):"), BorderLayout.NORTH)
            add(go, BorderLayout.CENTER)
        }

    override fun isModified() =
        go.text.trim() != GoToolchainSettings.getInstance().state.goExecutable

    override fun apply() {
        GoToolchainSettings.getInstance().state.goExecutable = go.text.trim()
    }

    override fun reset() {
        go.text = GoToolchainSettings.getInstance().state.goExecutable
    }
}
