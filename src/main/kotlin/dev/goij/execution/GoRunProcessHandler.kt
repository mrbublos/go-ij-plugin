package dev.goij.execution

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import dev.goij.toolchain.GoTools
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/** A launch snapshot; a session owns each process and temporary executable it creates. */
data class GoRunInput(
    val packageDirectory: String,
    val goExecutable: String,
    val workingDirectory: String = packageDirectory,
    val programArguments: List<String> = emptyList(),
    val buildFlags: List<String> = emptyList(),
    val environment: Map<String, String> = emptyMap(),
)

class GoRunProcessHandler(private val input: GoRunInput) : ProcessHandler() {
    private val stopped = AtomicBoolean()
    private val started = AtomicBoolean()
    private val processLock = Any()
    private var child: Process? = null

    override fun startNotify() {
        super.startNotify()
        if (started.compareAndSet(false, true)) Thread.ofVirtual().name("go-run").start { run() }
    }

    private fun run() {
        var temporary: Path? = null
        var exit = 1
        try {
            val go = GoTools.findGo(input.goExecutable)
            val directory = Path.of(input.packageDirectory)
            require(Files.isDirectory(directory)) {
                "Select an existing Go main-package directory."
            }
            val workingDirectory = Path.of(input.workingDirectory)
            require(Files.isDirectory(workingDirectory)) {
                "Working directory must be an existing directory: $workingDirectory"
            }
            val packageName = StringBuilder()
            val validation =
                execute(GoTools.packageInspectionCommand(go, input.buildFlags), directory) { text ->
                    packageName.append(text)
                }
            GoTools.requireMainPackage(validation, packageName.toString(), directory)
            temporary = Files.createTempDirectory("go-ij-run-")
            val executable = temporary.resolve("application")
            val build =
                execute(
                    listOf(go, "build") +
                        input.buildFlags +
                        listOf("-o", executable.toString(), "."),
                    directory,
                    ::output,
                )
            exit =
                if (build == 0 && !stopped.get())
                    execute(
                        listOf(executable.toString()) + input.programArguments,
                        workingDirectory,
                        ::output,
                    )
                else build
        } catch (error: Exception) {
            if (!stopped.get()) notifyTextAvailable("${error.message}\n", ProcessOutputTypes.STDERR)
        } finally {
            synchronized(processLock) {
                child?.let(::terminate)
                child = null
            }
            temporary?.toFile()?.deleteRecursively()
            notifyProcessTerminated(if (stopped.get()) 130 else exit)
        }
    }

    private fun execute(command: List<String>, directory: Path, output: (String) -> Unit): Int {
        val process =
            synchronized(processLock) {
                check(!stopped.get()) { "Launch stopped" }
                ProcessBuilder(command)
                    .directory(directory.toFile())
                    .apply { environment().putAll(input.environment) }
                    .start()
                    .also { child = it }
            }
        val stderr =
            Thread.ofVirtual().name("go-stderr").start {
                process.errorStream.bufferedReader().use { reader ->
                    val buffer = CharArray(4096)
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        notifyTextAvailable(String(buffer, 0, count), ProcessOutputTypes.STDERR)
                    }
                }
            }
        process.inputStream.bufferedReader().use { reader ->
            val buffer = CharArray(4096)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                output(String(buffer, 0, count))
            }
        }
        val result = process.waitFor()
        stderr.join()
        synchronized(processLock) { if (child === process) child = null }
        return result
    }

    private fun output(text: String) = notifyTextAvailable(text, ProcessOutputTypes.STDOUT)

    private fun terminate(process: Process) {
        process.descendants().use { descendants -> descendants.forEach { it.destroyForcibly() } }
        process.destroyForcibly()
    }

    override fun destroyProcessImpl() {
        stopped.set(true)
        Thread.ofVirtual().name("go-stop").start {
            synchronized(processLock) { child?.let(::terminate) }
        }
    }

    override fun detachProcessImpl() = destroyProcessImpl()

    override fun detachIsDefault() = false

    override fun getProcessInput(): OutputStream? =
        synchronized(processLock) { child?.outputStream }
}
