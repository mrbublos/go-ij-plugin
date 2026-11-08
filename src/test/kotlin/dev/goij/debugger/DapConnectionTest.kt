package dev.goij.debugger

import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.eclipse.lsp4j.debug.*
import org.eclipse.lsp4j.debug.launch.DSPLauncher
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer
import org.junit.Assert.*
import org.junit.Test

class DapConnectionTest {
    @Test
    fun launchFailureIsReportedWithoutConfigurationDone() {
        ServerSocket(0).use { listener ->
            val server =
                object : IDebugProtocolServer {
                    override fun initialize(args: InitializeRequestArguments) =
                        CompletableFuture.completedFuture(Capabilities())

                    override fun launch(args: Map<String, Any>): CompletableFuture<Void> =
                        CompletableFuture.failedFuture(
                            org.eclipse.lsp4j.jsonrpc.ResponseErrorException(
                                org.eclipse.lsp4j.jsonrpc.messages.ResponseError(
                                    org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
                                        .InvalidParams,
                                    "broken Go build",
                                    null,
                                )
                            )
                        )
                }
            val socket = Socket("127.0.0.1", listener.localPort)
            listener.accept().use { accepted ->
                DSPLauncher.createServerLauncher(
                        server,
                        accepted.getInputStream(),
                        accepted.getOutputStream(),
                    )
                    .startListening()
                DapConnection(socket, object : IDebugEvents {}).use { connection ->
                    try {
                        connection.initialize(emptyMap(), emptyMap()).get(5, TimeUnit.SECONDS)
                        fail("Expected rejected launch")
                    } catch (error: java.util.concurrent.ExecutionException) {
                        assertTrue(error.toString(), error.toString().contains("broken Go build"))
                    }
                }
            }
        }
    }

    @Test
    fun unexpectedDisconnectReportsFailureAndCloseIsIdempotent() {
        ServerSocket(0).use { listener ->
            val socket = Socket("127.0.0.1", listener.localPort)
            val accepted = listener.accept()
            val failure = CompletableFuture<Throwable>()
            val connection =
                DapConnection(
                    socket,
                    object : IDebugEvents {
                        override fun failed(error: Throwable) {
                            failure.complete(error)
                        }
                    },
                )
            accepted.close()
            assertTrue(failure.get(5, TimeUnit.SECONDS).message!!.contains("disconnected"))
            connection.close()
            connection.close()
            assertTrue(socket.isClosed)
        }
    }

    @Test
    fun initialBreakpointsPrecedeConfigurationDoneEvenWhenLaunchReplyIsDelayed() {
        ServerSocket(0).use { listener ->
            val calls = java.util.concurrent.CopyOnWriteArrayList<String>()
            val configured = CompletableFuture<Void>()
            val server =
                object : IDebugProtocolServer {
                    override fun initialize(args: InitializeRequestArguments) =
                        CompletableFuture.completedFuture(Capabilities()).also {
                            calls.add("initialize")
                        }

                    override fun launch(args: Map<String, Any>) =
                        configured.also { calls.add("launch") }

                    override fun setBreakpoints(args: SetBreakpointsArguments) =
                        CompletableFuture.completedFuture(
                                SetBreakpointsResponse().apply {
                                    breakpoints =
                                        args.breakpoints
                                            .map {
                                                Breakpoint().apply {
                                                    isVerified = true
                                                    line = it.line
                                                }
                                            }
                                            .toTypedArray()
                                }
                            )
                            .also {
                                calls.add(
                                    "breakpoints:${args.breakpoints.joinToString { it.line.toString() }}"
                                )
                            }

                    override fun configurationDone(args: ConfigurationDoneArguments) =
                        CompletableFuture.completedFuture<Void>(null).also {
                            calls.add("configured")
                            configured.complete(null)
                        }
                }
            val socket = Socket("127.0.0.1", listener.localPort)
            listener.accept().use { accepted ->
                val launcher =
                    DSPLauncher.createServerLauncher(
                        server,
                        accepted.getInputStream(),
                        accepted.getOutputStream(),
                    )
                launcher.startListening()
                val connection = DapConnection(socket, object : IDebugEvents {})
                val startup =
                    connection.initialize(
                        mapOf("program" to "/tmp/main"),
                        mapOf("/tmp/main.go" to listOf(5)),
                    )
                connection.setBreakpoints("/tmp/main.go", listOf(7))
                launcher.remoteProxy.initialized()
                startup.get(5, TimeUnit.SECONDS)
                assertEquals(
                    listOf("initialize", "launch", "breakpoints:7", "configured"),
                    calls.toList(),
                )
                connection.setBreakpoints("/tmp/main.go", emptyList()).get(5, TimeUnit.SECONDS)
                assertEquals("breakpoints:", calls.last())
                connection.close()
            }
        }
    }
}
