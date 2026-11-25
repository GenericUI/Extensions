@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.uuid.ExperimentalUuidApi::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class
)

package com.nexustools.genericui.ext.serviceworker

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSCondition
import platform.Foundation.NSDate
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSLock
import platform.Foundation.NSTask
import platform.Foundation.NSURL
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.fileHandleWithNullDevice
import platform.posix.FD_CLOEXEC
import platform.posix.F_GETFD
import platform.posix.F_OK
import platform.posix.F_SETFD
import platform.posix.LOCK_EX
import platform.posix.LOCK_NB
import platform.posix.O_CREAT
import platform.posix.O_RDWR
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.S_IRUSR
import platform.posix.S_IWUSR
import platform.posix.X_OK
import platform.posix.access
import platform.posix.chmod
import platform.posix.close
import platform.posix.fclose
import platform.posix.fcntl
import platform.posix.flock
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import platform.posix.getenv
import platform.posix.getpid
import platform.posix.getuid
import platform.posix.mkdir
import platform.posix.open
import platform.posix.rename
import platform.posix.unlink
import platform.posix.usleep
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.uuid.Uuid

internal actual object DesktopOsHelpers {

    private val lockMutex = NSLock()
    private val heldLockFds = linkedMapOf<String, Int>()

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
        lockMutex.lock()
        try {
            if (heldLockFds.containsKey(lockKey)) {
                return false
            }
            val parent = lockFilePath.substringBeforeLast('/', "")
            if (parent.isNotEmpty()) {
                ensureDirectoryExists(parent)
            }
            val fd = open(lockFilePath, O_CREAT or O_RDWR, S_IRUSR or S_IWUSR)
            if (fd < 0) return false
            setCloseOnExec(fd)
            if (flock(fd, LOCK_EX or LOCK_NB) != 0) {
                close(fd)
                return false
            }
            heldLockFds[lockKey] = fd
            return true
        } finally {
            lockMutex.unlock()
        }
    }

    actual fun releaseSingleInstanceLock(lockKey: String) {
        lockMutex.lock()
        val fd = try {
            heldLockFds.remove(lockKey) ?: -1
        } finally {
            lockMutex.unlock()
        }
        if (fd >= 0) {
            close(fd)
        }
    }

    actual fun releaseSingleInstanceLock() {
        val fdsToClose: List<Int>
        lockMutex.lock()
        try {
            fdsToClose = heldLockFds.values.toList()
            heldLockFds.clear()
        } finally {
            lockMutex.unlock()
        }
        for (fd in fdsToClose) {
            if (fd >= 0) {
                close(fd)
            }
        }
    }

    actual fun registerShutdownHook(hook: () -> Unit) {
        // Kernel automatically closes open FDs and releases flock locks upon process exit.
    }

    actual fun spawnDetachedProcess(
        executablePath: String,
        args: List<String>,
        workingDir: String?
    ): Boolean {
        val task = NSTask()
        task.executableURL = NSURL.fileURLWithPath(executablePath)
        task.arguments = args
        if (!workingDir.isNullOrBlank()) {
            task.currentDirectoryURL = NSURL.fileURLWithPath(workingDir)
        }
        task.standardInput = NSFileHandle.fileHandleWithNullDevice
        task.standardOutput = NSFileHandle.fileHandleWithNullDevice
        task.standardError = NSFileHandle.fileHandleWithNullDevice
        return task.launchAndReturnError(null)
    }

    private fun setCloseOnExec(fd: Int) {
        if (fd < 0) return
        val flags = fcntl(fd, F_GETFD)
        if (flags >= 0) {
            fcntl(fd, F_SETFD, flags or FD_CLOEXEC)
        } else {
            fcntl(fd, F_SETFD, FD_CLOEXEC)
        }
    }

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
        val label = "${sanitizeId(appId)}.serviceworker"
        val uid = getuid()
        if (!enabled) {
            runLaunchctl(listOf("bootout", "gui/$uid/$label"))
            unlink(path)
            return true
        }
        val plist = buildLaunchAgentPlistXmlForTest(
            appId = appId,
            targetExecutablePath = targetExecutablePath,
            args = args
        )
        if (!atomicWriteTextFile(path, plist, ownerOnlyPermissions = true)) {
            return false
        }
        runLaunchctl(listOf("bootstrap", "gui/$uid", path))
        return true
    }

    actual fun tryWakeRegisteredBackgroundService(appId: String): Boolean {
        val path = launchAgentPlistPath(appId)
        if (access(path, F_OK) != 0) {
            return false
        }
        val uid = getuid()
        val label = "${sanitizeId(appId)}.serviceworker"
        if (runLaunchctl(listOf("kickstart", "gui/$uid/$label")) == 0) {
            return true
        }
        runLaunchctl(listOf("bootstrap", "gui/$uid", path))
        return runLaunchctl(listOf("kickstart", "gui/$uid/$label")) == 0
    }

    internal fun buildLaunchAgentPlistXmlForTest(
        appId: String,
        targetExecutablePath: String,
        args: List<String>
    ): String {
        val label = "${sanitizeId(appId)}.serviceworker"
        val argXml = buildList {
            add(targetExecutablePath)
            addAll(args)
        }.joinToString("\n        ") { "<string>${escapeXml(it)}</string>" }
        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>${escapeXml(label)}</string>
    <key>ProgramArguments</key>
    <array>
        $argXml
    </array>
    <key>MachServices</key>
    <dict>
        <key>${escapeXml(label)}</key>
        <true/>
    </dict>
    <key>RunAtLoad</key>
    <true/>
</dict>
</plist>
"""
    }

    private fun runLaunchctl(args: List<String>, timeoutMs: Long = 2_000L): Int {
        val task = NSTask()
        task.executableURL = NSURL.fileURLWithPath("/bin/launchctl")
        task.arguments = args
        task.standardInput = NSFileHandle.fileHandleWithNullDevice
        task.standardOutput = NSFileHandle.fileHandleWithNullDevice
        task.standardError = NSFileHandle.fileHandleWithNullDevice

        val condition = NSCondition()
        val finished = AtomicBoolean(false)
        task.terminationHandler = { _ ->
            finished.store(true)
            condition.lock()
            try {
                condition.broadcast()
            } finally {
                condition.unlock()
            }
        }
        if (!task.launchAndReturnError(null)) {
            return -1
        }
        val deadline = NSDate.dateWithTimeIntervalSinceNow(timeoutMs.toDouble() / 1000.0)
        condition.lock()
        try {
            while (!finished.load() && task.isRunning()) {
                if (!condition.waitUntilDate(deadline)) {
                    break
                }
            }
        } finally {
            condition.unlock()
        }
        if (task.isRunning()) {
            task.terminate()
            return -1
        }
        return task.terminationStatus
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

        val directCandidate = "$dir/$siblingBaseName"
        if (access(directCandidate, X_OK) == 0) {
            return directCandidate to emptyList()
        }

        // Main bundle (Contents/MacOS/<main>) locating helper bundle under Contents/Helpers/*.app/Contents/MacOS/<sibling>
        val parentDir = dir.substringBeforeLast('/', "")
        if (dir.endsWith("/MacOS") && parentDir.endsWith("/Contents")) {
            findExecutableInHelperBundles("$parentDir/Helpers", siblingBaseName)?.let { helperExe ->
                return helperExe to emptyList()
            }
        }

        // Helper bundle (Contents/Helpers/<AppName> Service Worker.app/Contents/MacOS/<worker>)
        // locating outer Contents/MacOS/<sibling>
        val helperAppDir = parentDir.substringBeforeLast('/', "")
        val helpersContainerDir = helperAppDir.substringBeforeLast('/', "")
        if (dir.endsWith("/MacOS") &&
            parentDir.endsWith("/Contents") &&
            helpersContainerDir.endsWith("/Contents/Helpers")
        ) {
            val outerContentsDir = helpersContainerDir.substringBeforeLast('/', "")
            val outerMainCandidate = "$outerContentsDir/MacOS/$siblingBaseName"
            if (access(outerMainCandidate, X_OK) == 0) {
                return outerMainCandidate to emptyList()
            }
        }

        return directCandidate to emptyList()
    }

    private fun findExecutableInHelperBundles(helpersDir: String, siblingBaseName: String): String? {
        val rawNames = NSFileManager.defaultManager.contentsOfDirectoryAtPath(helpersDir, error = null)
            ?: return null
        val helperBundles = rawNames.mapNotNull { it as? String }
            .filter { it.endsWith(".app") }
            .sortedWith(
                compareBy<String>(
                    { !it.endsWith(" Service Worker.app") && it != "$siblingBaseName.app" },
                    { it }
                )
            )
        for (bundleName in helperBundles) {
            val candidate = "$helpersDir/$bundleName/Contents/MacOS/$siblingBaseName"
            if (access(candidate, X_OK) == 0) {
                return candidate
            }
        }
        return null
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
