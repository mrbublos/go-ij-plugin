package dev.goij.debugger

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import dev.goij.toolchain.GoTools
import java.io.OutputStream
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.eclipse.lsp4j.debug.*

/** Immutable launch input owned by the debugger, independent of configuration UI. */
data class GoDebugInput(
    val packageDirectory: String,
    val goExecutable: String,
    val delveExecutable: String,
    val workingDirectory: String = packageDirectory,
    val programArguments: List<String> = emptyList(),
    val buildFlags: String = "",
    val environment: Map<String, String> = emptyMap(),
)

class GoDebugProcessHandler(
    private val input: GoDebugInput,
    private val events: IDebugEvents,
    initialBreakpoints: Map<String, List<Int>>,
) : ProcessHandler() {
    private val desiredBreakpoints = initialBreakpoints.toMutableMap()
    private val started = AtomicBoolean()
    private val stopped = AtomicBoolean()
    private val lock = Any()
    private var child: Process? = null
    private var temporary: Path? = null
    @Volatile
    var connection: DapConnection? = null
        private set

    val ready = CompletableFuture<DapConnection>()

    override fun startNotify() {
        super.startNotify()
        if (started.compareAndSet(false, true))
            java.lang.Thread.ofVirtual().name("go-debug-start").start { launch() }
    }

    private fun inspectPackage(go: String) {
        val process =
            synchronized(lock) {
                check(!stopped.get()) { "Debug session stopped." }
                ProcessBuilder(
                        GoTools.packageInspectionCommand(
                            go,
                            com.intellij.util.execution.ParametersListUtil.parse(input.buildFlags),
                        )
                    )
                    .directory(Path.of(input.packageDirectory).toFile())
                    .apply { environment().putAll(input.environment) }
                    .start()
                    .also { child = it }
            }
        val errors =
            java.lang.Thread.ofVirtual().name("go-debug-inspect-errors").start {
                process.errorStream.bufferedReader().useLines { lines ->
                    lines.forEach { output("$it\n") }
                }
            }
        val packageName = process.inputStream.bufferedReader().use { it.readText() }
        val exit = process.waitFor()
        errors.join()
        synchronized(lock) { if (child === process) child = null }
        GoTools.requireMainPackage(exit, packageName, Path.of(input.packageDirectory))
    }

    private fun buildApplication(go: String): Path {
        val executable: Path
        val process =
            synchronized(lock) {
                check(!stopped.get()) { "Debug session stopped." }
                temporary = Files.createTempDirectory("go-ij-debug-")
                executable = temporary!!.resolve("application")
                ProcessBuilder(
                        listOf(go, "build") +
                            com.intellij.util.execution.ParametersListUtil.parse(input.buildFlags) +
                            listOf("-gcflags=all=-N -l", "-o", executable.toString(), ".")
                    )
                    .directory(Path.of(input.packageDirectory).toFile())
                    .redirectErrorStream(true)
                    .apply { environment().putAll(input.environment) }
                    .start()
                    .also { child = it }
            }
        process.inputStream.bufferedReader().useLines { lines -> lines.forEach { output("$it\n") } }
        val exit = process.waitFor()
        synchronized(lock) { if (child === process) child = null }
        check(exit == 0) {
            "Build error: Go debug build exited with code $exit. See the console for diagnostics."
        }
        return executable
    }

    private fun launch() {
        try {
            notifyTextAvailable("Starting Go debug session…\n", ProcessOutputTypes.SYSTEM)
            val go = GoTools.findGo(input.goExecutable)
            val delve = GoTools.findDelve(input.delveExecutable)
            require(Files.isDirectory(Path.of(input.packageDirectory))) {
                "Select an existing Go main-package directory."
            }
            require(Files.isDirectory(Path.of(input.workingDirectory))) {
                "Working directory must be an existing directory: ${input.workingDirectory}"
            }
            inspectPackage(go)
            val executable = buildApplication(go)
            val process =
                synchronized(lock) {
                    check(!stopped.get()) { "Debug session stopped." }
                    ProcessBuilder(delve, "dap", "--listen=127.0.0.1:0")
                        .directory(Path.of(input.packageDirectory).toFile())
                        .redirectErrorStream(true)
                        .apply { environment().putAll(input.environment) }
                        .start()
                        .also { child = it }
                }
            val endpoint = CompletableFuture<Int>()
            java.lang.Thread.ofVirtual().name("go-delve-output").start {
                try {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            val port =
                                Regex("DAP server listening at: 127\\.0\\.0\\.1:(\\d+)")
                                    .find(line)
                                    ?.groupValues
                                    ?.get(1)
                            if (port != null) endpoint.complete(port.toInt()) else output("$line\n")
                        }
                    }
                    endpoint.completeExceptionally(
                        IllegalStateException("Delve exited before opening its DAP server.")
                    )
                } catch (error: Exception) {
                    endpoint.completeExceptionally(error)
                }
            }
            val socket = Socket("127.0.0.1", endpoint.get(20, TimeUnit.SECONDS))
            val dap =
                synchronized(lock) {
                    if (stopped.get()) {
                        socket.close()
                        error("Debug session stopped.")
                    }
                    DapConnection(
                            socket,
                            object : IDebugEvents {
                                override fun output(text: String) {
                                    this@GoDebugProcessHandler.output(text)
                                }

                                override fun stopped(event: StoppedEventArguments) {
                                    events.stopped(event)
                                }

                                override fun continued() {
                                    events.continued()
                                }

                                override fun exited(code: Int) {
                                    events.exited(code)
                                    finish(code)
                                }

                                override fun failed(error: Throwable) {
                                    fail(error)
                                }

                                override fun breakpoints(
                                    path: String,
                                    lines: List<Int>,
                                    results: Array<Breakpoint>,
                                ) {
                                    events.breakpoints(path, lines, results)
                                }
                            },
                        )
                        .also { connection = it }
                }
            synchronized(lock) {
                desiredBreakpoints.forEach { (path, lines) -> dap.setBreakpoints(path, lines) }
            }
            dap.initialize(
                    mapOf(
                        "request" to "launch",
                        "mode" to "exec",
                        "outputMode" to "remote",
                        "program" to executable.toString(),
                        "dlvCwd" to Path.of(input.packageDirectory).toRealPath().toString(),
                        "cwd" to input.workingDirectory,
                        "args" to input.programArguments,
                        "env" to input.environment,
                    ),
                    emptyMap(),
                )
                .orTimeout(120, TimeUnit.SECONDS)
                .whenComplete { _, error ->
                    if (error == null) ready.complete(dap) else fail(error)
                }
        } catch (error: Exception) {
            fail(error)
        }
    }

    fun updateBreakpoints(path: String, lines: List<Int>) {
        synchronized(lock) {
            if (stopped.get()) return
            desiredBreakpoints[path] = lines.toList()
            connection?.setBreakpoints(path, lines)?.exceptionally {
                fail(it)
                null
            }
        }
    }

    fun fail(error: Throwable) {
        if (stopped.get()) return
        val cause = generateSequence(error) { it.cause }.last()
        val detail =
            (cause as? org.eclipse.lsp4j.jsonrpc.ResponseErrorException)?.responseError?.data
        val message = ((detail as? Map<*, *>)?.get("error") as? Map<*, *>)?.get("format") as? String
        val reported = if (message != null) IllegalStateException(message, cause) else cause
        notifyTextAvailable("Debug failed: ${reported.message}\n", ProcessOutputTypes.STDERR)
        events.failed(reported)
        finish(1)
    }

    private fun output(text: String) {
        notifyTextAvailable(text, ProcessOutputTypes.STDOUT)
        events.output(text)
    }

    private fun finish(code: Int) {
        if (!stopped.compareAndSet(false, true)) return
        ready.completeExceptionally(IllegalStateException("Debug session ended."))
        java.lang.Thread.ofVirtual().name("go-debug-cleanup").start {
            val (process, dap, directory) =
                synchronized(lock) {
                    Triple(child, connection, temporary).also {
                        child = null
                        connection = null
                        temporary = null
                    }
                }
            try {
                val descendants = process?.descendants()?.toList().orEmpty()
                try {
                    dap?.server
                        ?.disconnect(DisconnectArguments().apply { terminateDebuggee = true })
                        ?.get(2, TimeUnit.SECONDS)
                } catch (_: Exception) {
                    /* A dead adapter cannot acknowledge disconnect. */
                }
                runCatching { dap?.close() }
                descendants.reversed().forEach { it.destroy() }
                process?.destroy()
                if (process != null && !process.waitFor(2, TimeUnit.SECONDS))
                    process.destroyForcibly().waitFor()
                descendants.reversed().forEach {
                    if (it.isAlive) {
                        it.destroyForcibly()
                        runCatching { it.onExit().get(2, TimeUnit.SECONDS) }
                    }
                }
                directory?.toFile()?.deleteRecursively()
            } finally {
                notifyProcessTerminated(code)
            }
        }
    }

    override fun destroyProcessImpl() {
        finish(130)
    }

    override fun detachProcessImpl() {
        destroyProcessImpl()
    }

    override fun detachIsDefault() = false

    override fun getProcessInput(): OutputStream? = null
}
