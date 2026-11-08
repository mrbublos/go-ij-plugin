package dev.goij.toolchain

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(name = "StandaloneGoToolchain", storages = [Storage("standalone-go.xml")])
class GoToolchainSettings : PersistentStateComponent<GoToolchainSettings.Paths> {
    class Paths {
        var goExecutable: String = ""
        var delveExecutable: String = ""
    }

    private var paths = Paths()

    override fun getState() = paths

    override fun loadState(state: Paths) {
        paths = state
    }

    companion object {
        fun getInstance(): GoToolchainSettings =
            ApplicationManager.getApplication().getService(GoToolchainSettings::class.java)
    }
}
