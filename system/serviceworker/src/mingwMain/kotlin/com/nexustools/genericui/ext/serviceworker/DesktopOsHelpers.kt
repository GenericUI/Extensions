@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.uuid.ExperimentalUuidApi::class)

package com.nexustools.genericui.ext.serviceworker

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.wcstr
import platform.posix.fclose
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import platform.posix.getenv
import platform.posix.memset
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix._wfopen
import platform.windows.CloseHandle
import platform.windows.CreateDirectoryW
import platform.windows.CreateMutexW
import platform.windows.CreateProcessW
import platform.windows.DETACHED_PROCESS
import platform.windows.DeleteFileW
import platform.windows.ERROR_ALREADY_EXISTS
import platform.windows.GetCurrentProcessId
import platform.windows.GetLastError
import platform.windows.GetModuleFileNameW
import platform.windows.HANDLE
import platform.windows.MAX_PATH
import platform.windows.MOVEFILE_COPY_ALLOWED
import platform.windows.MOVEFILE_REPLACE_EXISTING
import platform.windows.MoveFileExW
import platform.windows.PROCESS_INFORMATION
import platform.windows.STARTUPINFOW
import platform.windows.Sleep
import kotlin.uuid.Uuid

internal actual object DesktopOsHelpers {

    private var heldMutexHandle: HANDLE? = null
    private var heldMutexKey: String? = null

    actual fun currentExecutablePath(): String? = memScoped {
        val buf = allocArray<UShortVar>(MAX_PATH * 2)
        val len = GetModuleFileNameW(null, buf, (MAX_PATH * 2).toUInt()).toInt()
        if (len > 0) buf.toKString() else null
    }

    actual fun currentProcessId(): Long = GetCurrentProcessId().toLong()

    actual fun runtimeDataDirectory(appId: String): String {
        val cleanId = sanitizeId(appId)
        val localAppData = getenv("LOCALAPPDATA")?.toKString()?.takeIf { it.isNotBlank() }
            ?: getenv("USERPROFILE")?.toKString()?.takeIf { it.isNotBlank() }?.let { "$it\\AppData\\Local" }
            ?: "C:\\Temp"
        return "${localAppData.trimEnd('\\', '/')}\\$cleanId"
    }

    actual fun ensureDirectoryExists(path: String): Boolean {
        val normalized = path.replace('/', '\\').trimEnd('\\')
        if (normalized.isEmpty()) return false
        val parts = normalized.split('\\')
        var current = ""
        for (part in parts) {
            if (part.isEmpty()) continue
            current = if (current.isEmpty()) part else "$current\\$part"
            if (part.endsWith(':')) continue
            CreateDirectoryW(current, null)
        }
        return true
    }

    actual fun atomicWriteTextFile(
        path: String,
        content: String,
        ownerOnlyPermissions: Boolean
    ): Boolean = writeBytesAtomic(path, content.encodeToByteArray())

    actual fun readTextFileOrNull(path: String): String? = memScoped {
        val normalized = path.replace('/', '\\')
        val fp = _wfopen(normalized.wcstr, "rb".wcstr) ?: return null
        return try {
            fseek(fp, 0, SEEK_END)
            val size = ftell(fp)
            if (size <= 0) return ""
            fseek(fp, 0, SEEK_SET)
            val bytes = ByteArray(size)
            val read = bytes.usePinned { pinned ->
                fread(pinned.addressOf(0), 1u, size.convert(), fp).toInt()
            }
            if (read <= 0) "" else bytes.decodeToString(0, read)
        } finally {
            fclose(fp)
        }
    }

    actual fun deleteFileIfExists(path: String): Boolean =
        DeleteFileW(path.replace('/', '\\')) != 0

    actual fun acquireSingleInstanceLock(lockKey: String, lockFilePath: String): Boolean {
        if (heldMutexHandle != null && heldMutexKey == lockKey) {
            return false
        }
        val mutexName = "Local\\$lockKey"
        val handle = CreateMutexW(null, 0, mutexName) ?: return false
        if (GetLastError().toInt() == ERROR_ALREADY_EXISTS) {
            CloseHandle(handle)
            return false
        }
        heldMutexHandle = handle
        heldMutexKey = lockKey
        return true
    }

    actual fun releaseSingleInstanceLock() {
        val h = heldMutexHandle
        heldMutexHandle = null
        heldMutexKey = null
        if (h != null) {
            CloseHandle(h)
        }
    }

    actual fun spawnDetachedProcess(
        executablePath: String,
        args: List<String>,
        workingDir: String?
    ): Boolean = memScoped {
        val exeWin = executablePath.replace('/', '\\')
        val cmdLine = buildString {
            append('"').append(exeWin).append('"')
            for (arg in args) {
                append(' ')
                if (arg.any { it.isWhitespace() || it == '"' }) {
                    append('"').append(arg.replace("\"", "\\\"")).append('"')
                } else {
                    append(arg)
                }
            }
        }
        val cmdBuf = cmdLine.wcstr.getPointer(this)
        val si = alloc<STARTUPINFOW>()
        memset(si.ptr, 0, sizeOf<STARTUPINFOW>().convert())
        si.cb = sizeOf<STARTUPINFOW>().toUInt()
        val pi = alloc<PROCESS_INFORMATION>()
        memset(pi.ptr, 0, sizeOf<PROCESS_INFORMATION>().convert())

        val ok = CreateProcessW(
            null,
            cmdBuf,
            null,
            null,
            0,
            DETACHED_PROCESS.toUInt(),
            null,
            workingDir?.replace('/', '\\'),
            si.ptr,
            pi.ptr
        ) != 0
        if (ok) {
            pi.hProcess?.let { CloseHandle(it) }
            pi.hThread?.let { CloseHandle(it) }
        }
        ok
    }

    actual fun isAutostartEnabled(appId: String, appName: String): Boolean = memScoped {
        val path = autostartLnkPath(appId, appName)
        val fp = _wfopen(path.wcstr, "rb".wcstr) ?: return false
        fclose(fp)
        return true
    }

    actual fun setAutostartEnabled(
        appId: String,
        appName: String,
        targetExecutablePath: String,
        args: List<String>,
        enabled: Boolean
    ): Boolean {
        val path = autostartLnkPath(appId, appName)
        if (!enabled) {
            DeleteFileW(path)
            return true
        }
        val exeWin = targetExecutablePath.replace('/', '\\')
        val workDir = exeWin.substringBeforeLast('\\', "")
        val lnkBytes = WindowsShortcutWriter.buildLnkBytes(
            targetPath = exeWin,
            arguments = args.joinToString(" "),
            workingDirectory = workDir,
            description = appName
        )
        return writeBytesAtomic(path, lnkBytes)
    }

    actual fun generateRandomHexToken(byteCount: Int): String {
        val needed = byteCount.coerceAtLeast(16)
        val out = StringBuilder(needed * 2)
        while (out.length < needed * 2) {
            val bytes = Uuid.random().toByteArray()
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                out.append("0123456789abcdef"[v ushr 4])
                out.append("0123456789abcdef"[v and 0x0F])
            }
        }
        return out.substring(0, needed * 2)
    }

    actual fun sleepMillis(ms: Long) {
        if (ms > 0) {
            Sleep(ms.toUInt())
        }
    }

    actual fun resolveSiblingBinaryCommand(
        siblingBaseName: String,
        jvmMainClassFqn: String
    ): Pair<String, List<String>>? {
        val self = currentExecutablePath() ?: return null
        val dir = self.replace('/', '\\').substringBeforeLast('\\', "")
        if (dir.isEmpty()) return null
        val candidate = "$dir\\$siblingBaseName.exe"
        return candidate to emptyList()
    }

    private fun writeBytesAtomic(path: String, bytes: ByteArray): Boolean = memScoped {
        val normalized = path.replace('/', '\\')
        val parent = normalized.substringBeforeLast('\\', "")
        if (parent.isNotEmpty()) {
            ensureDirectoryExists(parent)
        }
        val tmp = "$normalized.${currentProcessId()}.tmp"
        val fp = _wfopen(tmp.wcstr, "wb".wcstr) ?: return false
        val wrote = try {
            if (bytes.isEmpty()) {
                true
            } else {
                bytes.usePinned { pinned ->
                    fwrite(pinned.addressOf(0), 1u, bytes.size.convert(), fp).toInt() == bytes.size
                }
            }
        } finally {
            fclose(fp)
        }
        if (!wrote) {
            DeleteFileW(tmp)
            return false
        }
        return MoveFileExW(tmp, normalized, (MOVEFILE_REPLACE_EXISTING or MOVEFILE_COPY_ALLOWED).toUInt()) != 0
    }

    private fun autostartLnkPath(appId: String, appName: String): String {
        val cleanName = appName.trim().ifEmpty { sanitizeId(appId) }
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val appData = getenv("APPDATA")?.toKString()?.takeIf { it.isNotBlank() }
            ?: getenv("USERPROFILE")?.toKString()?.takeIf { it.isNotBlank() }?.let { "$it\\AppData\\Roaming" }
            ?: "C:\\Temp"
        return "${appData.trimEnd('\\', '/')}\\Microsoft\\Windows\\Start Menu\\Programs\\Startup\\$cleanName.lnk"
    }

    private fun sanitizeId(raw: String): String =
        raw.trim().ifEmpty { "com.nexustools.genericui.app" }.replace(Regex("[^A-Za-z0-9_.-]"), "_")
}
