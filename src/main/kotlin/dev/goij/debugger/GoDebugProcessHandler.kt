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

    private fun launch() {
        try {
            val go = GoTools.findGo(input.goExecutable)
            val delve = GoTools.findDelve(input.delveExecutable)
            require(Files.isDirectory(Path.of(input.packageDirectory))) {
                "Select an existing Go main-package directory."
            }
            require(Files.isDirectory(Path.of(input.workingDirectory))) {
                "Working directory must be an existing directory: ${input.workingDirectory}"
            }
            val launchEnvironment = input.environment.toMutableMap()
            val process =
                synchronized(lock) {
                    check(!stopped.get()) { "Debug session stopped." }
                    temporary = Files.createTempDirectory("go-ij-debug-")
                    val toolDirectory = Files.createDirectory(temporary!!.resolve("bin"))
                    Files.createSymbolicLink(
                        toolDirectory.resolve("go"),
                        Path.of(go).toAbsolutePath(),
                    )
                    ProcessBuilder(delve, "dap", "--listen=127.0.0.1:0")
                        .directory(Path.of(input.packageDirectory).toFile())
                        .redirectErrorStream(true)
                        .apply {
                            environment().putAll(input.environment)
                            environment()["PATH"] =
                                toolDirectory.toString() +
                                    java.io.File.pathSeparator +
                                    environment()["PATH"].orEmpty()
                            launchEnvironment["PATH"] = environment()["PATH"]!!
                        }
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
                        "mode" to "debug",
                        "program" to Path.of(input.packageDirectory).toRealPath().toString(),
                        "dlvCwd" to Path.of(input.packageDirectory).toRealPath().toString(),
                        "cwd" to input.workingDirectory,
                        "args" to input.programArguments,
                        "env" to launchEnvironment,
                        "buildFlags" to input.buildFlags,
                        "output" to temporary!!.resolve("application").toString(),
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
            try {
                synchronized(lock) {
                    val process = child
                    val descendants = process?.descendants()?.toList().orEmpty()
                    try {
                        connection
                            ?.server
                            ?.disconnect(DisconnectArguments().apply { terminateDebuggee = true })
                            ?.get(2, TimeUnit.SECONDS)
                    } catch (_: Exception) {
                        /* A dead adapter cannot acknowledge disconnect. */
                    }
                    runCatching { connection?.close() }
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
                    temporary?.toFile()?.deleteRecursively()
                }
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
