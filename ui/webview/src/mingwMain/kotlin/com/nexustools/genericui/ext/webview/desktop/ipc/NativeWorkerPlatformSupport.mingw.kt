@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.webview.desktop.ipc

import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.cinterop.wcstr
import platform.posix.memset
import platform.windows.CREATE_NO_WINDOW
import platform.windows.CloseHandle
import platform.windows.CreateDirectoryW
import platform.windows.CreateProcessW
import platform.windows.FILE_ATTRIBUTE_DIRECTORY
import platform.windows.GetCurrentDirectoryW
import platform.windows.GetCurrentProcessId
import platform.windows.GetExitCodeProcess
import platform.windows.GetFileAttributesW
import platform.windows.GetLastError
import platform.windows.GetModuleFileNameW
import platform.windows.GetTempPathW
import platform.windows.HANDLE
import platform.windows.INVALID_FILE_ATTRIBUTES
import platform.windows.MAX_PATH
import platform.windows.PROCESS_INFORMATION
import platform.windows.STARTUPINFOW
import platform.windows.TerminateProcess
import platform.windows.WAIT_TIMEOUT
import platform.windows.WCHARVar
import platform.windows.WaitForSingleObject
import kotlin.concurrent.atomics.AtomicBoolean

internal actual object NativeWorkerPlatformSupport {
    actual fun currentPid(): Long = GetCurrentProcessId().toLong()

    actual fun resolveExecutableDirectory(): String? {
        var capacity = MAX_PATH.coerceAtLeast(260)
        var exePath: String? = null
        while (capacity <= 32768) {
            var truncated = false
            val candidate = memScoped {
                val buf = allocArray<WCHARVar>(capacity)
                val len = GetModuleFileNameW(null, buf, capacity.toUInt()).toInt()
                when {
                    len <= 0 -> null
                    len >= capacity - 1 && capacity < 32768 -> {
                        truncated = true
                        null
                    }
                    else -> buf.toKString()
                }
            }
            if (candidate != null) {
                exePath = candidate
                break
            }
            if (!truncated) return null
            capacity = (capacity * 2).coerceAtMost(32768)
        }
        val resolved = exePath ?: return null
        val sepIdx = maxOf(resolved.lastIndexOf('\\'), resolved.lastIndexOf('/'))
        if (sepIdx < 0) return null
        if (sepIdx == 2 && resolved.length >= 3 && resolved[1] == ':') {
            return resolved.substring(0, 3)
        }
        return if (sepIdx == 0) resolved.substring(0, 1) else resolved.substring(0, sepIdx)
    }

    actual fun resolveCurrentWorkingDirectory(): String? = memScoped {
        val len = GetCurrentDirectoryW(0u, null).toInt()
        if (len <= 0) return@memScoped null
        val buf = allocArray<WCHARVar>(len + 1)
        val actualLen = GetCurrentDirectoryW((len + 1).toUInt(), buf).toInt()
        if (actualLen <= 0) null else buf.toKString()
    }

    actual fun resolveTempBaseDir(): String = memScoped {
        val buf = allocArray<WCHARVar>(MAX_PATH + 64)
        val len = GetTempPathW((MAX_PATH + 64).toUInt(), buf).toInt()
        if (len <= 0) "." else buf.toKString().trimEnd('\\', '/')
    }

    actual fun fileExists(path: String): Boolean {
        val attrs = GetFileAttributesW(path)
        return attrs != INVALID_FILE_ATTRIBUTES && (attrs and FILE_ATTRIBUTE_DIRECTORY.toUInt()) == 0u
    }

    actual fun ensureDirectoryExists(path: String): Boolean {
        val normalized = path.replace('/', '\\').trimEnd('\\')
        if (normalized.isEmpty()) return false
        var current = ""
        val parts = normalized.split('\\')
        for (i in parts.indices) {
            val part = parts[i]
            if (part.isEmpty()) continue
            current = if (i == 0 && part.endsWith(':')) {
                "$part\\"
            } else if (current.isEmpty()) {
                part
            } else if (current.endsWith('\\')) {
                "$current$part"
            } else {
                "$current\\$part"
            }
            if (i == 0 && part.endsWith(':')) continue
            CreateDirectoryW(current, null)
        }
        val attrs = GetFileAttributesW(normalized)
        return attrs != INVALID_FILE_ATTRIBUTES && (attrs and FILE_ATTRIBUTE_DIRECTORY.toUInt()) != 0u
    }

    actual fun spawnExecutable(executablePath: String, args: List<String>): WebViewWorkerProcessHandle {
        val cmdLine = buildWindowsCommandLine(executablePath, args)
        return memScoped {
            val startupInfo = alloc<STARTUPINFOW>()
            memset(startupInfo.ptr, 0, sizeOf<STARTUPINFOW>().convert())
            startupInfo.cb = sizeOf<STARTUPINFOW>().toUInt()
            val procInfo = alloc<PROCESS_INFORMATION>()
            memset(procInfo.ptr, 0, sizeOf<PROCESS_INFORMATION>().convert())
            val cmdBuf = cmdLine.wcstr.getPointer(this)
            val created = CreateProcessW(
                lpApplicationName = executablePath,
                lpCommandLine = cmdBuf,
                lpProcessAttributes = null,
                lpThreadAttributes = null,
                bInheritHandles = 0,
                dwCreationFlags = CREATE_NO_WINDOW.toUInt(),
                lpEnvironment = null,
                lpCurrentDirectory = null,
                lpStartupInfo = startupInfo.ptr,
                lpProcessInformation = procInfo.ptr
            )
            if (created == 0) {
                val err = GetLastError()
                throw IllegalStateException("CreateProcessW failed for '$executablePath' (GetLastError=$err)")
            }
            procInfo.hThread?.let { CloseHandle(it) }
            val processHandle = procInfo.hProcess
                ?: throw IllegalStateException("CreateProcessW succeeded but returned null process handle")
            WindowsWorkerProcessHandle(
                handle = processHandle,
                pid = procInfo.dwProcessId.toLong(),
                executablePath = executablePath
            )
        }
    }

    private fun buildWindowsCommandLine(exePath: String, args: List<String>): String {
        fun quoteArg(arg: String): String {
            if (arg.isNotEmpty() && arg.none { it == ' ' || it == '\t' || it == '"' || it == '\n' }) {
                return arg
            }
            val sb = StringBuilder("\"")
            var backslashes = 0
            for (ch in arg) {
                when (ch) {
                    '\\' -> backslashes++
                    '"' -> {
                        repeat(backslashes * 2 + 1) { sb.append('\\') }
                        backslashes = 0
                        sb.append('"')
                    }
                    else -> {
                        repeat(backslashes) { sb.append('\\') }
                        backslashes = 0
                        sb.append(ch)
                    }
                }
            }
            repeat(backslashes * 2) { sb.append('\\') }
            sb.append('"')
            return sb.toString()
        }
        return (listOf(exePath) + args).joinToString(" ") { quoteArg(it) }
    }

    private class WindowsWorkerProcessHandle(
        private val handle: HANDLE,
        override val pid: Long,
        private val executablePath: String
    ) : WebViewWorkerProcessHandle {
        private val closed = AtomicBoolean(false)

        override fun isAlive(): Boolean {
            if (closed.load()) return false
            return WaitForSingleObject(handle, 0u) == WAIT_TIMEOUT.toUInt()
        }

        override fun killForcibly() {
            if (closed.load()) return
            TerminateProcess(handle, 137u)
            WaitForSingleObject(handle, 3_000u)
        }

        override fun waitForExit(timeoutMs: Int): Boolean {
            if (closed.load()) return true
            return WaitForSingleObject(handle, timeoutMs.coerceAtLeast(0).toUInt()) != WAIT_TIMEOUT.toUInt()
        }

        override fun exitCodeOrNull(): Int? {
            if (closed.load() || isAlive()) return null
            return memScoped {
                val out = alloc<UIntVar>()
                if (GetExitCodeProcess(handle, out.ptr) != 0) out.value.toInt() else null
            }
        }

        override fun diagnosticsSummary(): String =
            "pid=$pid exe=$executablePath alive=${isAlive()} exitCode=${exitCodeOrNull()}"

        override fun closeHandle() {
            if (closed.compareAndSet(false, true)) {
                CloseHandle(handle)
            }
        }
    }
}
