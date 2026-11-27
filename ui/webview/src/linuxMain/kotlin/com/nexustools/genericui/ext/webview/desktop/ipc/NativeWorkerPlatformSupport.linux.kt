@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.webview.desktop.ipc

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.posix.ECHILD
import platform.posix.EINTR
import platform.posix.EEXIST
import platform.posix.F_OK
import platform.posix.SIGKILL
import platform.posix.S_IRWXU
import platform.posix.S_ISREG
import platform.posix.WNOHANG
import platform.posix._exit
import platform.posix.access
import platform.posix.errno
import platform.posix.execv
import platform.posix.fork
import platform.posix.getcwd
import platform.posix.getenv
import platform.posix.getpid
import platform.posix.kill
import platform.posix.mkdir
import platform.posix.readlink
import platform.posix.stat
import platform.posix.usleep
import platform.posix.waitpid
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt

internal actual object NativeWorkerPlatformSupport {
    actual fun currentPid(): Long = getpid().toLong()

    actual fun resolveExecutableDirectory(): String? = memScoped {
        val buf = allocArray<ByteVar>(4096)
        val n = readlink("/proc/self/exe", buf, 4095.convert()).toInt()
        if (n <= 0) return@memScoped null
        buf[n] = 0
        val exePath = buf.toKString()
        val sepIdx = exePath.lastIndexOf('/')
        if (sepIdx < 0) return@memScoped null
        if (sepIdx == 0) "/" else exePath.substring(0, sepIdx)
    }

    actual fun resolveCurrentWorkingDirectory(): String? = memScoped {
        val buf = allocArray<ByteVar>(4096)
        getcwd(buf, 4095.convert())?.toKString()
    }

    actual fun resolveTempBaseDir(): String =
        getenv("TMPDIR")?.toKString()?.trim()?.takeIf { it.isNotEmpty() } ?: "/tmp"

    actual fun fileExists(path: String): Boolean = memScoped {
        if (access(path, F_OK) != 0) return@memScoped false
        val st = alloc<stat>()
        if (stat(path, st.ptr) != 0) return@memScoped false
        (st.st_mode.toInt() and 0xF000) == 0x8000
    }

    actual fun ensureDirectoryExists(path: String): Boolean {
        val clean = path.trimEnd('/')
        if (clean.isEmpty()) return false
        var current = if (clean.startsWith('/')) "/" else ""
        for (segment in clean.split('/')) {
            if (segment.isEmpty()) continue
            current = if (current == "/") "/$segment" else if (current.isEmpty()) segment else "$current/$segment"
            val rc = mkdir(current, 0x1ED.convert())
            if (rc != 0 && errno != EEXIST) {
                return false
            }
        }
        return true
    }

    actual fun spawnExecutable(executablePath: String, args: List<String>): WebViewWorkerProcessHandle {
        return memScoped {
            val allArgs = listOf(executablePath) + args
            val argv = allocArray<CPointerVar<ByteVar>>(allArgs.size + 1)
            for (i in allArgs.indices) {
                argv[i] = allArgs[i].cstr.ptr
            }
            argv[allArgs.size] = null
            val exeC = executablePath.cstr.ptr

            val childPid = fork()
            if (childPid < 0) {
                throw IllegalStateException("fork() failed for '$executablePath' with errno=$errno")
            }
            if (childPid == 0) {
                execv(exeC, argv)
                _exit(127)
            }
            LinuxWorkerProcessHandle(childPid, executablePath)
        }
    }

    private class LinuxWorkerProcessHandle(
        private val osPid: Int,
        private val executablePath: String
    ) : WebViewWorkerProcessHandle {
        override val pid: Long = osPid.toLong()
        private val lock = AtomicInt(0)
        private val reaped = AtomicBoolean(false)
        private var cachedExitCode: Int? = null

        private inline fun <T> withLock(block: () -> T): T {
            while (!lock.compareAndSet(0, 1)) {
                usleep(100u)
            }
            return try {
                block()
            } finally {
                lock.store(0)
            }
        }

        private fun pollReap(): Boolean = withLock {
            if (reaped.load()) return@withLock true
            memScoped {
                val status = alloc<IntVar>()
                val rc = waitpid(osPid, status.ptr, WNOHANG)
                if (rc == osPid) {
                    val s = status.value
                    cachedExitCode = if ((s and 0x7F) == 0) (s shr 8) and 0xFF else 128 + (s and 0x7F)
                    reaped.store(true)
                    true
                } else if (rc < 0 && errno == ECHILD) {
                    reaped.store(true)
                    true
                } else {
                    false
                }
            }
        }

        override fun isAlive(): Boolean = !pollReap()

        override fun killForcibly() {
            if (reaped.load()) return
            kill(osPid, SIGKILL)
            withLock {
                if (!reaped.load()) {
                    memScoped {
                        val status = alloc<IntVar>()
                        while (true) {
                            val rc = waitpid(osPid, status.ptr, 0)
                            if (rc == osPid) {
                                val s = status.value
                                cachedExitCode = if ((s and 0x7F) == 0) (s shr 8) and 0xFF else 128 + (s and 0x7F)
                                reaped.store(true)
                                break
                            }
                            if (rc < 0 && errno == EINTR) continue
                            reaped.store(true)
                            break
                        }
                    }
                }
            }
        }

        override fun waitForExit(timeoutMs: Int): Boolean {
            if (pollReap()) return true
            val stepUs = 5_000L
            var remainingUs = timeoutMs.coerceAtLeast(0).toLong() * 1_000L
            while (remainingUs > 0L) {
                usleep(stepUs.toUInt())
                remainingUs -= stepUs
                if (pollReap()) return true
            }
            return pollReap()
        }

        override fun exitCodeOrNull(): Int? {
            pollReap()
            return cachedExitCode
        }

        override fun diagnosticsSummary(): String =
            "pid=$pid exe=$executablePath alive=${isAlive()} exitCode=${exitCodeOrNull()}"
    }
}
