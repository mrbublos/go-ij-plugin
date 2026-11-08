package dev.goij.toolchain

import java.nio.file.Files
import java.nio.file.Path

object GoTools {
    fun findGo(explicit: String): String {
        if (explicit.isNotBlank()) {
            requireExecutable(explicit)
            return explicit
        }
        val candidates =
            (System.getenv("PATH").orEmpty().split(java.io.File.pathSeparator) +
                listOf("/opt/homebrew/bin", "/usr/local/bin", "/usr/local/go/bin"))
        return candidates
            .filter { it.isNotBlank() }
            .map { Path.of(it, "go") }
            .firstOrNull { Files.isRegularFile(it) && Files.isExecutable(it) }
            ?.toString()
            ?: throw IllegalArgumentException(
                "Go was not found. Install Go and set its executable in Settings > Tools > Go Toolchain."
            )
    }

    private fun requireExecutable(path: String) {
        require(Files.isRegularFile(Path.of(path)) && Files.isExecutable(Path.of(path))) {
            "Go executable is invalid: $path. Set an executable in Settings > Tools > Go Toolchain."
        }
    }
}
