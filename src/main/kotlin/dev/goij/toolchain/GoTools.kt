package dev.goij.toolchain

import java.nio.file.Files
import java.nio.file.Path

object GoTools {
    fun findGo(explicit: String): String = find(explicit, "Go", "go", listOf("/usr/local/go/bin"))

    fun findDelve(explicit: String): String =
        find(explicit, "Delve", "dlv", listOf(System.getProperty("user.home") + "/go/bin"))

    private fun find(
        explicit: String,
        name: String,
        executable: String,
        extra: List<String>,
    ): String {
        if (explicit.isNotBlank()) {
            requireExecutable(explicit, name)
            return Path.of(explicit).toAbsolutePath().normalize().toString()
        }
        val candidates =
            (System.getenv("PATH").orEmpty().split(java.io.File.pathSeparator) +
                listOf("/opt/homebrew/bin", "/usr/local/bin") +
                extra)
        return candidates
            .filter { it.isNotBlank() }
            .map { Path.of(it, executable) }
            .firstOrNull { Files.isRegularFile(it) && Files.isExecutable(it) }
            ?.toString()
            ?: throw IllegalArgumentException(
                "$name was not found. Install $name and set its executable in Settings > Tools > Go Toolchain."
            )
    }

    private fun requireExecutable(path: String, name: String) {
        require(Files.isRegularFile(Path.of(path)) && Files.isExecutable(Path.of(path))) {
            "$name executable is invalid: $path. Set an executable in Settings > Tools > Go Toolchain."
        }
    }
}
