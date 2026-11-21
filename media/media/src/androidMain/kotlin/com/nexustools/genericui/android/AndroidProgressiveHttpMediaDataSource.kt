package com.nexustools.genericui.android

import android.media.MediaDataSource
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Progressive caching [MediaDataSource] for live/chunked HTTP streams (such as server FFmpeg
 * transcodes without `Content-Range` support).
 *
 * Streams the HTTP response sequentially into a temporary cache file on a background daemon
 * thread while allowing Android [android.media.MediaPlayer]'s extractors to seek freely backward
 * and forward across buffered bytes without triggering [java.net.ProtocolException] reconnects.
 */
class AndroidProgressiveHttpMediaDataSource(
    cacheDir: File,
    val url: String,
    private val customStreamOpener: ((String) -> InputStream)? = null
) : MediaDataSource() {

    private val tempFile: File = File.createTempFile(
        "genericui_http_stream_",
        ".tmp",
        cacheDir.apply { mkdirs() }
    )
    private val stateLock = Object()
    private val readLock = Any()

    private var bytesWritten: Long = 0L
    private var downloadComplete: Boolean = false
    private var downloadError: Throwable? = null
    private var writerFinished: Boolean = false

    @Volatile
    private var closed: Boolean = false

    @Volatile
    private var activeConnection: HttpURLConnection? = null

    private var readerRaf: RandomAccessFile? = null

    private val writerThread: Thread = Thread(
        { pumpStreamToTempFile() },
        "GenericUI-HttpMediaStream"
    ).apply {
        isDaemon = true
        start()
    }

    private fun pumpStreamToTempFile() {
        var input: InputStream? = null
        var output: FileOutputStream? = null
        try {
            output = FileOutputStream(tempFile)
            input = if (customStreamOpener != null) {
                customStreamOpener.invoke(url)
            } else {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    setRequestProperty("Accept-Encoding", "identity")
                }
                activeConnection = conn
                if (closed) {
                    runCatching { conn.disconnect() }
                    return
                }
                val status = conn.responseCode
                if (status !in 200..299) {
                    throw IOException("HTTP $status while streaming $url")
                }
                conn.inputStream
            }
            val buf = ByteArray(64 * 1024)
            while (!closed) {
                val read = input.read(buf)
                if (read < 0) break
                if (read == 0) continue
                output.write(buf, 0, read)
                synchronized(stateLock) {
                    bytesWritten += read.toLong()
                    stateLock.notifyAll()
                }
            }
            runCatching { output.flush() }
        } catch (t: Throwable) {
            if (!closed) {
                synchronized(stateLock) {
                    downloadError = t
                    stateLock.notifyAll()
                }
            }
        } finally {
            runCatching { input?.close() }
            runCatching { output?.close() }
            val conn = activeConnection
            activeConnection = null
            if (conn != null) {
                runCatching { conn.disconnect() }
            }
            var shouldDelete = false
            synchronized(stateLock) {
                downloadComplete = true
                writerFinished = true
                shouldDelete = closed
                stateLock.notifyAll()
            }
            if (shouldDelete) {
                runCatching { tempFile.delete() }
            }
        }
    }

    override fun getSize(): Long = synchronized(stateLock) {
        if (downloadComplete && downloadError == null) bytesWritten else -1L
    }

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (closed || position < 0L) return -1
        if (size <= 0) return 0

        val avail: Int
        synchronized(stateLock) {
            while (!closed && !downloadComplete && bytesWritten <= position) {
                stateLock.wait(200L)
            }
            if (!closed && !downloadComplete && bytesWritten < position + minOf(size, 4096).toLong()) {
                stateLock.wait(15L)
            }
            if (closed) return -1
            val currentWritten = bytesWritten
            if (currentWritten <= position) {
                val err = downloadError
                if (err != null && currentWritten == 0L) {
                    throw IOException("HTTP stream failed", err)
                }
                return -1
            }
            avail = (currentWritten - position).coerceAtMost(size.toLong()).toInt()
        }

        synchronized(readLock) {
            if (closed) return -1
            var raf = readerRaf
            if (raf == null) {
                raf = RandomAccessFile(tempFile, "r")
                readerRaf = raf
            }
            raf.seek(position)
            return raf.read(buffer, offset, avail)
        }
    }

    /**
     * Non-blocking abort helper safe to invoke from the main thread before tearing down
     * `MediaPlayer`, waking any blocked `readAt` calls immediately.
     */
    fun signalAbort() {
        if (closed) return
        closed = true
        synchronized(stateLock) {
            stateLock.notifyAll()
        }
        val conn = activeConnection
        activeConnection = null
        if (conn != null) {
            Thread { runCatching { conn.disconnect() } }.apply { isDaemon = true }.start()
        }
    }

    override fun close() {
        signalAbort()
        synchronized(readLock) {
            runCatching { readerRaf?.close() }
            readerRaf = null
        }
        val canDelete = synchronized(stateLock) { writerFinished }
        if (canDelete) {
            runCatching { tempFile.delete() }
        }
    }

    fun existsTempFileForTest(): Boolean = tempFile.exists()

    companion object {
        fun isLiveOrTranscodeHttpUrl(url: String): Boolean {
            if (!url.startsWith("http://", ignoreCase = true) &&
                !url.startsWith("https://", ignoreCase = true)
            ) {
                return false
            }
            if (url.contains("/transcode/", ignoreCase = true)) {
                return true
            }
            val query = url.substringAfter('?', "")
            if (query.isEmpty()) return false
            return query.contains("mode=transcode", ignoreCase = true) ||
                query.contains("tv=", ignoreCase = true) ||
                query.contains("ta=", ignoreCase = true) ||
                query.contains("tc=", ignoreCase = true) ||
                query.contains("start=", ignoreCase = true) ||
                query.contains("start_seconds=", ignoreCase = true)
        }
    }
}
