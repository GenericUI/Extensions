package com.nexustools.genericui.ext.ipc

import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.net.ProtocolFamily
import java.net.SocketAddress
import java.net.StandardProtocolFamily
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.util.EnumSet

/**
 * Cached reflection bridge for JDK 16+ JEP 380 `AF_UNIX` channels while compiling under `JVM_11`,
 * paired with POSIX permission hardening, non-blocking client connect, and stale socket probing.
 */
internal object JvmUnixDomainSocketBridge {

    private const val UNSUPPORTED_MESSAGE: String =
        "GenericUI desktop JVM AF_UNIX IPC requires Java 16+ Unix domain socket support (JEP 380)."
    private const val PROBE_TIMEOUT_NS: Long = 150_000_000L

    private class JvmUnixReflection(
        val unixFamily: ProtocolFamily,
        val addressOfPath: Method,
        val serverOpen: Method,
        val clientOpen: Method
    )

    private val reflection: JvmUnixReflection by lazy {
        try {
            val family = StandardProtocolFamily.valueOf("UNIX")
            val addressClass = Class.forName("java.net.UnixDomainSocketAddress")
            val addressOf = addressClass.getMethod("of", Path::class.java)
            val serverOpen = ServerSocketChannel::class.java.getMethod("open", ProtocolFamily::class.java)
            val clientOpen = SocketChannel::class.java.getMethod("open", ProtocolFamily::class.java)
            JvmUnixReflection(
                unixFamily = family,
                addressOfPath = addressOf,
                serverOpen = serverOpen,
                clientOpen = clientOpen
            )
        } catch (e: ClassNotFoundException) {
            throw UnsupportedOperationException(UNSUPPORTED_MESSAGE, e)
        } catch (e: IllegalArgumentException) {
            throw UnsupportedOperationException(UNSUPPORTED_MESSAGE, e)
        } catch (e: NoSuchMethodException) {
            throw UnsupportedOperationException(UNSUPPORTED_MESSAGE, e)
        }
    }

    fun unixSocketAddressOf(path: Path): SocketAddress =
        invokeUnwrapped { reflection.addressOfPath.invoke(null, path) as SocketAddress }

    fun openServerSocketChannel(): ServerSocketChannel =
        invokeUnwrapped { reflection.serverOpen.invoke(null, reflection.unixFamily) as ServerSocketChannel }

    fun openSocketChannel(): SocketChannel =
        invokeUnwrapped { reflection.clientOpen.invoke(null, reflection.unixFamily) as SocketChannel }

    fun connect(endpointAddress: String, timeoutMs: Int, config: IpcConfig): IpcConnection? {
        if (endpointAddress.isBlank()) return null
        val socketPath = Paths.get(endpointAddress)
        val channel = openSocketChannel()
        var conn: JvmUnixSocketConnection? = null
        var connectFailure: Throwable? = null
        return try {
            channel.configureBlocking(false)
            val unixAddress = unixSocketAddressOf(socketPath)
            var connected = channel.connect(unixAddress)
            if (!connected) {
                val selector = Selector.open()
                val key = channel.register(selector, SelectionKey.OP_CONNECT)
                try {
                    val effectiveTimeout = timeoutMs.coerceAtLeast(1).toLong()
                    val deadlineNanos = System.nanoTime() + effectiveTimeout * 1_000_000L
                    while (!connected) {
                        val remNanos = deadlineNanos - System.nanoTime()
                        if (remNanos <= 0L) {
                            return null
                        }
                        val waitMs = ((remNanos + 999_999L) / 1_000_000L).coerceAtLeast(1L)
                        val ready = selector.select(waitMs)
                        selector.selectedKeys().clear()
                        if (ready > 0 && key.isValid && key.isConnectable) {
                            connected = channel.finishConnect()
                        }
                    }
                } finally {
                    key.cancel()
                    try {
                        selector.close()
                    } catch (e: IOException) {
                        connectFailure = connectFailure ?: e
                    }
                }
            }
            conn = JvmUnixSocketConnection(channel, config.maxPacketBytes)
            conn
        } catch (e: IOException) {
            connectFailure = connectFailure ?: e
            null
        } catch (e: SecurityException) {
            connectFailure = connectFailure ?: e
            null
        } finally {
            if (conn == null && channel.isOpen) {
                try {
                    channel.close()
                } catch (e: IOException) {
                    connectFailure = connectFailure ?: e
                }
            }
        }
    }

    fun applyOwnerOnlyDirPermissions(dir: Path) {
        if (!supportsPosixAttributes(dir)) return
        val perms = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE
        )
        Files.setPosixFilePermissions(dir, perms)
    }

    fun applyOwnerOnlyFilePermissions(file: Path) {
        if (!supportsPosixAttributes(file)) return
        val perms = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
        )
        Files.setPosixFilePermissions(file, perms)
    }

    fun isSocketAcceptingConnections(path: Path): Boolean {
        val channel = openSocketChannel()
        var closeErr: IOException? = null
        try {
            channel.configureBlocking(false)
            val address = unixSocketAddressOf(path)
            if (channel.connect(address)) {
                return true
            }
            val selector = Selector.open()
            val key = channel.register(selector, SelectionKey.OP_CONNECT)
            try {
                val deadlineNanos = System.nanoTime() + PROBE_TIMEOUT_NS
                while (true) {
                    val remNanos = deadlineNanos - System.nanoTime()
                    if (remNanos <= 0L) return false
                    val waitMs = ((remNanos + 999_999L) / 1_000_000L).coerceAtLeast(1L)
                    val ready = selector.select(waitMs)
                    selector.selectedKeys().clear()
                    if (ready > 0 && key.isValid && key.isConnectable) {
                        return channel.finishConnect()
                    }
                }
            } finally {
                key.cancel()
                try {
                    selector.close()
                } catch (e: IOException) {
                    closeErr = closeErr ?: e
                }
            }
        } catch (e: IOException) {
            closeErr = closeErr ?: e
            return false
        } finally {
            if (channel.isOpen) {
                try {
                    channel.close()
                } catch (e: IOException) {
                    closeErr = closeErr ?: e
                }
            }
        }
    }

    private fun supportsPosixAttributes(path: Path): Boolean =
        Files.getFileStore(path).supportsFileAttributeView("posix")

    private inline fun <T> invokeUnwrapped(block: () -> T): T {
        return try {
            block()
        } catch (e: InvocationTargetException) {
            when (val cause = e.cause) {
                is UnsupportedOperationException -> throw UnsupportedOperationException(UNSUPPORTED_MESSAGE, cause)
                is IOException -> throw cause
                is RuntimeException -> throw cause
                is Error -> throw cause
                else -> throw RuntimeException(cause ?: e)
            }
        }
    }
}
