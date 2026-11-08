package dev.goij.debugger

import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import org.eclipse.lsp4j.debug.*
import org.eclipse.lsp4j.debug.launch.DSPLauncher
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient

interface IDebugEvents {
    fun output(text: String) {}

    fun stopped(event: StoppedEventArguments) {}

    fun exited(code: Int) {}

    fun failed(error: Throwable) {}

    fun breakpoints(path: String, lines: List<Int>, results: Array<Breakpoint>) {}
}

/** Owns one external DAP connection. All requests are asynchronous; close is idempotent. */
class DapConnection(private val socket: Socket, private val events: IDebugEvents) : AutoCloseable {
    private val closed = AtomicBoolean()
    private val initialized = CompletableFuture<Void>()
    private val executor =
        Executors.newCachedThreadPool { task -> Thread(task, "Go DAP").apply { isDaemon = true } }
    private val client =
        object : IDebugProtocolClient {
            override fun initialized() {
                initialized.complete(null)
            }

            override fun output(args: OutputEventArguments) {
                events.output(args.output)
            }

            override fun stopped(args: StoppedEventArguments) {
                events.stopped(args)
            }

            override fun exited(args: ExitedEventArguments) {
                events.exited(args.exitCode)
            }

            override fun terminated(args: TerminatedEventArguments?) {
                events.exited(0)
            }
        }
    private val launcher =
        DSPLauncher.createClientLauncher(
            client,
            socket.getInputStream(),
            socket.getOutputStream(),
            executor,
        ) {
            it
        }
    val server = launcher.remoteProxy
    private var breakpointTail = CompletableFuture.completedFuture<Void>(null)
    private val desiredBreakpoints = linkedMapOf<String, List<Int>>()
    private val pending = mutableListOf<CompletableFuture<Void>>()
    private var acceptingBreakpoints = false

    init {
        val listening = launcher.startListening()
        executor.execute {
            try {
                listening.get()
                if (!closed.get())
                    events.failed(IllegalStateException("Delve disconnected unexpectedly."))
            } catch (error: Exception) {
                if (!closed.get()) events.failed(error)
            }
        }
    }

    fun initialize(
        arguments: Map<String, Any>,
        breakpoints: Map<String, List<Int>>,
    ): CompletableFuture<Void> {
        synchronized(this) {
            breakpoints.forEach { (path, lines) -> desiredBreakpoints.putIfAbsent(path, lines) }
        }
        return CompletableFuture.supplyAsync(
                {
                    server.initialize(
                        InitializeRequestArguments().apply {
                            adapterID = "go"
                            clientID = "standalone-go"
                            linesStartAt1 = true
                            columnsStartAt1 = true
                            pathFormat = "path"
                        }
                    )
                },
                executor,
            )
            .thenCompose { it }
            .thenCompose {
                // Delve may hold the launch response until configurationDone.
                val launch = server.launch(arguments)
                launch.whenComplete { _, error ->
                    if (error != null) initialized.completeExceptionally(error)
                }
                initialized.thenCompose { beginBreakpoints() }.thenCompose { launch }
            }
    }

    @Synchronized
    private fun beginBreakpoints(): CompletableFuture<Void> {
        acceptingBreakpoints = true
        desiredBreakpoints.forEach { (path, lines) -> enqueueBreakpoints(path, lines) }
        val initial = breakpointTail
        initial.whenComplete { _, error ->
            synchronized(this) {
                pending.forEach {
                    if (error == null) it.complete(null) else it.completeExceptionally(error)
                }
                pending.clear()
            }
        }
        return configurationWhenSynchronized()
    }

    @Synchronized
    private fun configurationWhenSynchronized(): CompletableFuture<Void> {
        val tail = breakpointTail
        return tail.thenComposeAsync(
            {
                synchronized(this) {
                    if (tail !== breakpointTail) configurationWhenSynchronized()
                    else server.configurationDone(ConfigurationDoneArguments())
                }
            },
            executor,
        )
    }

    @Synchronized
    fun setBreakpoints(path: String, lines: List<Int>): CompletableFuture<Void> {
        desiredBreakpoints[path] = lines.toList()
        if (!acceptingBreakpoints) return CompletableFuture<Void>().also { pending.add(it) }
        return enqueueBreakpoints(path, lines.toList())
    }

    private fun enqueueBreakpoints(path: String, lines: List<Int>): CompletableFuture<Void> {
        breakpointTail =
            breakpointTail.thenComposeAsync(
                {
                    server
                        .setBreakpoints(
                            SetBreakpointsArguments().apply {
                                source = Source().apply { this.path = path }
                                breakpoints =
                                    lines
                                        .map { SourceBreakpoint().apply { line = it } }
                                        .toTypedArray()
                            }
                        )
                        .thenAccept { events.breakpoints(path, lines, it.breakpoints) }
                },
                executor,
            )
        return breakpointTail
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        initialized.completeExceptionally(IllegalStateException("Debug session stopped."))
        synchronized(this) {
            pending.forEach {
                it.completeExceptionally(IllegalStateException("Debug session stopped."))
            }
            pending.clear()
        }
        socket.close()
        executor.shutdownNow()
    }
}
