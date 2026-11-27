package com.nexustools.genericui.ext.ipc

import java.io.EOFException
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.concurrent.Volatile

/**
 * Fallback Windows Named Pipe client connection via [RandomAccessFile] for JVMs older than JDK 22
 * or where `java.lang.foreign` native access is disabled.
 */
internal class JvmFallbackNamedPipeClientConnection private constructor(
    private val raf: RandomAccessFile,
    maxPacketBytes: Int
) : BaseFramedIpcConnection(maxPacketBytes = maxPacketBytes) {

    @Volatile
    var lastTeardownError: Throwable? = null
        private set

    override fun writeFrameBytes(framedBytes: ByteArray): Boolean {
        return try {
            raf.write(framedBytes)
            true
        } catch (e: IOException) {
            lastTeardownError = lastTeardownError ?: e
            close()
            false
        }
    }

    override fun readFrameBytes(
        count: Int,
        initialTimeoutMs: Int,
        isHeader: Boolean
    ): ByteArray? {
        return try {
            val out = ByteArray(count)
            raf.readFully(out)
            out
        } catch (e: EOFException) {
            lastTeardownError = lastTeardownError ?: e
            close()
            null
        } catch (e: IOException) {
            lastTeardownError = lastTeardownError ?: e
            close()
            null
        }
    }

    override fun closeTransport() {
        try {
            raf.close()
        } catch (e: IOException) {
            lastTeardownError = lastTeardownError ?: e
        }
    }

    companion object {
        fun connect(
            pipeName: String,
            timeoutMs: Int,
            config: IpcConfig
        ): IpcConnection? {
            if (pipeName.isBlank()) return null
            val effectiveTimeoutMs = timeoutMs.coerceAtLeast(1).toLong()
            val deadlineNanos = System.nanoTime() + effectiveTimeoutMs * 1_000_000L
            while (true) {
                try {
                    val raf = RandomAccessFile(pipeName, "rw")
                    return JvmFallbackNamedPipeClientConnection(raf, config.maxPacketBytes)
                } catch (e: IOException) {
                    val msg = e.message?.lowercase().orEmpty()
                    val busy = msg.contains("busy") || msg.contains("231")
                    if (!busy || System.nanoTime() >= deadlineNanos) {
                        return null
                    }
                    try {
                        Thread.sleep(15L)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return null
                    }
                }
            }
        }
    }
}
