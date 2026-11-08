package dev.goij.toolchain

import com.intellij.openapi.options.Configurable
import java.awt.GridLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

class GoToolchainConfigurable : Configurable {
    private val go = JTextField(40)
    private val delve = JTextField(40)

    override fun getDisplayName() = "Go Toolchain"

    override fun createComponent(): JComponent =
        JPanel(GridLayout(0, 2, 8, 8)).apply {
            add(JLabel("Go executable (blank detects automatically):"))
            add(go)
            add(JLabel("Delve executable (blank detects automatically):"))
            add(delve)
        }

    override fun isModified() =
        go.text.trim() != GoToolchainSettings.getInstance().state.goExecutable ||
            delve.text.trim() != GoToolchainSettings.getInstance().state.delveExecutable

    override fun apply() {
        GoToolchainSettings.getInstance().state.goExecutable = go.text.trim()
        GoToolchainSettings.getInstance().state.delveExecutable = delve.text.trim()
    }

    override fun reset() {
        go.text = GoToolchainSettings.getInstance().state.goExecutable
        delve.text = GoToolchainSettings.getInstance().state.delveExecutable
    }
}
