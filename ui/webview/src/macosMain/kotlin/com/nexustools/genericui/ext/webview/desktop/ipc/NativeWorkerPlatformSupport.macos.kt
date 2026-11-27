@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.webview.desktop.ipc

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.Foundation.NSBundle
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSTask
import platform.Foundation.NSURL
import platform.posix.F_OK
import platform.posix.SIGKILL
import platform.posix.access
import platform.posix.getcwd
import platform.posix.getenv
import platform.posix.getpid
import platform.posix.kill
import platform.posix.stat
import platform.posix.usleep

internal actual object NativeWorkerPlatformSupport {
    actual fun currentPid(): Long = getpid().toLong()

    actual fun resolveExecutableDirectory(): String? {
        val exePath = NSBundle.mainBundle.executablePath?.takeIf { it.isNotBlank() }
            ?: (NSProcessInfo.processInfo.arguments.firstOrNull() as? String)
                ?.takeIf { it.isNotBlank() && '/' in it }
            ?: return null
        val sepIdx = exePath.lastIndexOf('/')
        if (sepIdx < 0) return null
        return if (sepIdx == 0) "/" else exePath.substring(0, sepIdx)
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

    actual fun ensureDirectoryExists(path: String): Boolean =
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )

    actual fun spawnExecutable(executablePath: String, args: List<String>): WebViewWorkerProcessHandle {
        val task = NSTask()
        task.executableURL = NSURL.fileURLWithPath(executablePath)
        task.arguments = args
        memScoped {
            val errPtr = alloc<ObjCObjectVar<NSError?>>()
            if (!task.launchAndReturnError(errPtr.ptr)) {
                val desc = errPtr.value?.localizedDescription ?: "unknown launch error"
                throw IllegalStateException("NSTask launch failed for '$executablePath': $desc")
            }
        }
        return MacosWorkerProcessHandle(task, executablePath)
    }

    private class MacosWorkerProcessHandle(
        private val task: NSTask,
        private val executablePath: String
    ) : WebViewWorkerProcessHandle {
        override val pid: Long = task.processIdentifier.toLong()

        override fun isAlive(): Boolean = task.isRunning()

        override fun killForcibly() {
            if (!task.isRunning()) return
            kill(task.processIdentifier, SIGKILL)
            task.waitUntilExit()
        }

        override fun waitForExit(timeoutMs: Int): Boolean {
            if (!task.isRunning()) return true
            val stepUs = 5_000L
            var remainingUs = timeoutMs.coerceAtLeast(0).toLong() * 1_000L
            while (remainingUs > 0L) {
                usleep(stepUs.toUInt())
                remainingUs -= stepUs
                if (!task.isRunning()) return true
            }
            return !task.isRunning()
        }

        override fun exitCodeOrNull(): Int? =
            if (task.isRunning()) null else task.terminationStatus

        override fun diagnosticsSummary(): String =
            "pid=$pid exe=$executablePath alive=${isAlive()} exitCode=${exitCodeOrNull()}"
    }
}
