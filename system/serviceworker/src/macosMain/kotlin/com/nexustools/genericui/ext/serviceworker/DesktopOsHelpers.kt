@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.uuid.ExperimentalUuidApi::class)

package com.nexustools.genericui.ext.serviceworker

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSTask
import platform.Foundation.NSURL
import platform.posix.F_OK
import platform.posix.LOCK_EX
import platform.posix.LOCK_NB
import platform.posix.O_CREAT
import platform.posix.O_RDWR
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.S_IRUSR
import platform.posix.S_IWUSR
import platform.posix.access
import platform.posix.chmod
import platform.posix.close
import platform.posix.fclose
import platform.posix.flock
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import platform.posix.getenv
import platform.posix.getpid
import platform.posix.mkdir
import platform.posix.open
import platform.posix.rename
import platform.posix.unlink
import platform.posix.usleep
import kotlinx.cinterop.toKString
import kotlin.uuid.Uuid

internal actual object DesktopOsHelpers {

    private var heldLockFd: Int = -1
    private var heldLockKey: String? = null

    actual fun currentExecutablePath(): String? =
        NSBundle.mainBundle.executablePath

    actual fun currentProcessId(): Long = getpid().toLong()

    actual fun runtimeDataDirectory(appId: String): String {
        val home = getenv("HOME")?.toKString()?.takeIf { it.isNotBlank() } ?: "/tmp"
        return "${home.trimEnd('/')}/Library/Application Support/${sanitizeId(appId)}"
    }

    actual fun ensureDirectoryExists(path: String): Boolean {
        val clean = path.trimEnd('/')
        if (clean.isEmpty()) return false
        var current = if (clean.startsWith('/')) "/" else ""
        for (part in clean.split('/')) {
            if (part.isEmpty()) continue
            current = if (current == "/" || current.isEmpty()) "$current$part" else "$current/$part"
            mkdir(current, 493u) // 0755
        }
        return true
    }

    actual fun atomicWriteTextFile(
        path: String,
        content: String,
        ownerOnlyPermissions: Boolean
    ): Boolean {
        val parent = path.substringBeforeLast('/', "")
        if (parent.isNotEmpty()) {
            ensureDirectoryExists(parent)
        }
        val tmp = "$path.${currentProcessId()}.tmp"
        val bytes = content.encodeToByteArray()
        val fp = fopen(tmp, "wb") ?: return false
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
            unlink(tmp)
            return false
        }
        if (ownerOnlyPermissions) {
            chmod(tmp, (S_IRUSR or S_IWUSR).convert())
        }
        if (rename(tmp, path) != 0) {
            unlink(tmp)
            return false
        }
        if (ownerOnlyPermissions) {
            chmod(path, (S_IRUSR or S_IWUSR).convert())
        }
        return true
    }

    actual fun readTextFileOrNull(path: String): String? {
        val fp = fopen(path, "rb") ?: return null
        return try {
            fseek(fp, 0, SEEK_END)
            val size = ftell(fp).toInt()
            if (size <= 0) return ""
            fseek(fp, 0, SEEK_SET)
            val bytes = ByteArray(size)
            val n = bytes.usePinned { pinned ->
                fread(pinned.addressOf(0), 1u, size.convert(), fp).toInt()
            }
            if (n <= 0) "" else bytes.decodeToString(0, n)
        } finally {
            fclose(fp)
        }
    }

    actual fun deleteFileIfExists(path: String): Boolean =
        unlink(path) == 0

    actual fun acquireSingleInstanceLock(lockKey: String, lockFilePath: String): Boolean {
        if (heldLockFd >= 0 && heldLockKey == lockKey) {
            return false
        }
        val parent = lockFilePath.substringBeforeLast('/', "")
        if (parent.isNotEmpty()) {
            ensureDirectoryExists(parent)
        }
        val fd = open(lockFilePath, O_CREAT or O_RDWR, S_IRUSR or S_IWUSR)
        if (fd < 0) return false
        if (flock(fd, LOCK_EX or LOCK_NB) != 0) {
            close(fd)
            return false
        }
        heldLockFd = fd
        heldLockKey = lockKey
        return true
    }

    actual fun releaseSingleInstanceLock() {
        val fd = heldLockFd
        heldLockFd = -1
        heldLockKey = null
        if (fd >= 0) {
            close(fd)
        }
    }

    actual fun spawnDetachedProcess(
        executablePath: String,
        args: List<String>,
        workingDir: String?
    ): Boolean = runCatching {
        val task = NSTask()
        task.executableURL = NSURL.fileURLWithPath(executablePath)
        task.arguments = args
        if (!workingDir.isNullOrBlank()) {
            task.currentDirectoryURL = NSURL.fileURLWithPath(workingDir)
        }
        task.launchAndReturnError(null)
    }.getOrDefault(false)

    actual fun isAutostartEnabled(appId: String, appName: String): Boolean =
        access(launchAgentPlistPath(appId), F_OK) == 0

    actual fun setAutostartEnabled(
        appId: String,
        appName: String,
        targetExecutablePath: String,
        args: List<String>,
        enabled: Boolean
    ): Boolean {
        val path = launchAgentPlistPath(appId)
        if (!enabled) {
            unlink(path)
            return true
        }
        val label = "${sanitizeId(appId)}.serviceworker"
        val argXml = buildList {
            add(targetExecutablePath)
            addAll(args)
        }.joinToString("\n        ") { "<string>${escapeXml(it)}</string>" }
        val plist = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>${escapeXml(label)}</string>
    <key>ProgramArguments</key>
    <array>
        $argXml
    </array>
    <key>RunAtLoad</key>
    <true/>
</dict>
</plist>
"""
        return atomicWriteTextFile(path, plist, ownerOnlyPermissions = true)
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
            usleep((ms * 1000L).toUInt())
        }
    }

    actual fun resolveSiblingBinaryCommand(
        siblingBaseName: String,
        jvmMainClassFqn: String
    ): Pair<String, List<String>>? {
        val self = currentExecutablePath() ?: return null
        val dir = self.substringBeforeLast('/', "")
        if (dir.isEmpty()) return null
        return "$dir/$siblingBaseName" to emptyList()
    }

    private fun launchAgentPlistPath(appId: String): String {
        val home = getenv("HOME")?.toKString()?.takeIf { it.isNotBlank() } ?: "/tmp"
        return "${home.trimEnd('/')}/Library/LaunchAgents/${sanitizeId(appId)}.serviceworker.plist"
    }

    private fun sanitizeId(raw: String): String =
        raw.trim().ifEmpty { "com.nexustools.genericui.app" }.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    private fun escapeXml(raw: String): String =
        raw.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
