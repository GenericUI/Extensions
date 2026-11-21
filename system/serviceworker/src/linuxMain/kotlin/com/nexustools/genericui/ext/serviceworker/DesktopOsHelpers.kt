@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.uuid.ExperimentalUuidApi::class)

package com.nexustools.genericui.ext.serviceworker

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.F_OK
import platform.posix.F_SETLK
import platform.posix.F_WRLCK
import platform.posix.O_CREAT
import platform.posix.O_RDWR
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.S_IRUSR
import platform.posix.S_IWUSR
import platform.posix._exit
import platform.posix.access
import platform.posix.chmod
import platform.posix.close
import platform.posix.execv
import platform.posix.fclose
import platform.posix.fcntl
import platform.posix.flock
import platform.posix.fopen
import platform.posix.fork
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import platform.posix.getenv
import platform.posix.getpid
import platform.posix.mkdir
import platform.posix.open
import platform.posix.readlink
import platform.posix.rename
import platform.posix.setsid
import platform.posix.unlink
import platform.posix.usleep
import kotlin.uuid.Uuid

internal actual object DesktopOsHelpers {

    private var heldLockFd: Int = -1
    private var heldLockKey: String? = null

    actual fun currentExecutablePath(): String? = memScoped {
        val buf = allocArray<ByteVar>(4096)
        val n = readlink("/proc/self/exe", buf, 4095u).toInt()
        if (n > 0) {
            buf[n] = 0
            buf.toKString()
        } else {
            null
        }
    }

    actual fun currentProcessId(): Long = getpid().toLong()

    actual fun runtimeDataDirectory(appId: String): String {
        val base = getenv("XDG_RUNTIME_DIR")?.toKString()?.takeIf { it.isNotBlank() }
            ?: getenv("XDG_DATA_HOME")?.toKString()?.takeIf { it.isNotBlank() }
            ?: getenv("HOME")?.toKString()?.takeIf { it.isNotBlank() }?.let { "${it.trimEnd('/')}/.local/share" }
            ?: "/tmp"
        return "${base.trimEnd('/')}/${sanitizeId(appId)}"
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
        val lockOk = memScoped {
            val fl = alloc<flock>()
            fl.l_type = F_WRLCK.toShort()
            fl.l_whence = SEEK_SET.toShort()
            fl.l_start = 0
            fl.l_len = 0
            fcntl(fd, F_SETLK, fl.ptr) == 0
        }
        if (!lockOk) {
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
    ): Boolean {
        val pid = fork()
        if (pid < 0) return false
        if (pid == 0) {
            setsid()
            memScoped {
                val argv = allocArray<kotlinx.cinterop.CPointerVar<ByteVar>>(args.size + 2)
                argv[0] = executablePath.cstr.getPointer(this)
                for (i in args.indices) {
                    argv[i + 1] = args[i].cstr.getPointer(this)
                }
                argv[args.size + 1] = null
                execv(executablePath, argv)
            }
            _exit(127)
        }
        return true
    }

    actual fun isAutostartEnabled(appId: String, appName: String): Boolean =
        access(desktopEntryPath(appId), F_OK) == 0

    actual fun setAutostartEnabled(
        appId: String,
        appName: String,
        targetExecutablePath: String,
        args: List<String>,
        enabled: Boolean
    ): Boolean {
        val path = desktopEntryPath(appId)
        if (!enabled) {
            unlink(path)
            return true
        }
        val execLine = buildList {
            add(quoteDesktopArg(targetExecutablePath))
            args.forEach { add(quoteDesktopArg(it)) }
        }.joinToString(" ")
        val desktopEntry = """[Desktop Entry]
Type=Application
Version=1.0
Name=$appName
Exec=$execLine
Terminal=false
X-GNOME-Autostart-enabled=true
"""
        return atomicWriteTextFile(path, desktopEntry, ownerOnlyPermissions = false)
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
        val isQtSelf = self.endsWith("-qt") || self.endsWith("-qt.kexe")
        val isGtkSelf = self.endsWith("-gtk") || self.endsWith("-gtk.kexe")
        val candidates = buildList {
            add("$dir/$siblingBaseName")
            add("$dir/$siblingBaseName.kexe")
            if (isQtSelf) {
                add("$dir/$siblingBaseName-qt")
                add("$dir/$siblingBaseName-qt.kexe")
            } else if (isGtkSelf) {
                add("$dir/$siblingBaseName-gtk")
                add("$dir/$siblingBaseName-gtk.kexe")
            }
            add("$dir/$siblingBaseName-gtk")
            add("$dir/$siblingBaseName-gtk.kexe")
            add("$dir/$siblingBaseName-qt")
            add("$dir/$siblingBaseName-qt.kexe")
        }.distinct()
        val resolved = candidates.firstOrNull { access(it, F_OK) == 0 } ?: "$dir/$siblingBaseName"
        return resolved to emptyList()
    }

    private fun desktopEntryPath(appId: String): String {
        val configHome = getenv("XDG_CONFIG_HOME")?.toKString()?.takeIf { it.isNotBlank() }
            ?: getenv("HOME")?.toKString()?.takeIf { it.isNotBlank() }?.let { "${it.trimEnd('/')}/.config" }
            ?: "/tmp/.config"
        return "${configHome.trimEnd('/')}/autostart/${sanitizeId(appId)}-serviceworker.desktop"
    }

    private fun sanitizeId(raw: String): String =
        raw.trim().ifEmpty { "com.nexustools.genericui.app" }.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    private fun quoteDesktopArg(arg: String): String =
        if (arg.any { it.isWhitespace() || it == '"' }) {
            "\"${arg.replace("\"", "\\\"")}\""
        } else {
            arg
        }
}
