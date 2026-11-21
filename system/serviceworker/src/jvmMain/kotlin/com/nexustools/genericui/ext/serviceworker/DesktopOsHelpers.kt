package com.nexustools.genericui.ext.serviceworker

import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom

internal actual object DesktopOsHelpers {

    private val secureRandom = SecureRandom()
    private val lockSync = Any()
    private var heldChannel: FileChannel? = null
    private var heldLock: FileLock? = null
    private var heldLockKey: String? = null

    actual fun currentExecutablePath(): String? {
        val codeSource = runCatching {
            DesktopOsHelpers::class.java.protectionDomain?.codeSource?.location?.toURI()?.let { File(it) }
        }.getOrNull()
        if (codeSource != null && codeSource.exists()) {
            return codeSource.absolutePath
        }
        return ProcessHandle.current().info().command().orElse(null)
    }

    actual fun currentProcessId(): Long =
        runCatching { ProcessHandle.current().pid() }.getOrDefault(0L)

    actual fun runtimeDataDirectory(appId: String): String {
        val cleanId = sanitizeId(appId)
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val home = System.getProperty("user.home").orEmpty()
        val base = when {
            os.contains("win") -> {
                System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
                    ?: File(home, "AppData/Local").absolutePath
            }
            os.contains("mac") || os.contains("darwin") -> {
                File(home, "Library/Application Support").absolutePath
            }
            else -> {
                System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.isNotBlank() }
                    ?: System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
                    ?: File(home, ".local/share").absolutePath
            }
        }
        return File(base, cleanId).absolutePath
    }

    actual fun ensureDirectoryExists(path: String): Boolean {
        val f = File(path)
        return f.isDirectory || f.mkdirs()
    }

    actual fun atomicWriteTextFile(
        path: String,
        content: String,
        ownerOnlyPermissions: Boolean
    ): Boolean {
        return try {
            val target = File(path)
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
            tmp.writeText(content, Charsets.UTF_8)
            if (ownerOnlyPermissions) {
                runCatching {
                    val perms = setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
                    Files.setPosixFilePermissions(tmp.toPath(), perms)
                }
            }
            try {
                Files.move(
                    tmp.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: Throwable) {
                Files.move(
                    tmp.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
            if (ownerOnlyPermissions) {
                runCatching {
                    val perms = setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
                    Files.setPosixFilePermissions(target.toPath(), perms)
                }
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    actual fun readTextFileOrNull(path: String): String? =
        runCatching {
            val f = File(path)
            if (f.isFile) f.readText(Charsets.UTF_8) else null
        }.getOrNull()

    actual fun deleteFileIfExists(path: String): Boolean =
        runCatching { File(path).delete() }.getOrDefault(false)

    actual fun acquireSingleInstanceLock(lockKey: String, lockFilePath: String): Boolean {
        synchronized(lockSync) {
            if (heldLock != null && heldLockKey == lockKey) {
                return false
            }
            return try {
                val file = File(lockFilePath)
                file.parentFile?.mkdirs()
                val channel = FileChannel.open(
                    file.toPath(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE
                )
                val lock = try {
                    channel.tryLock()
                } catch (_: OverlappingFileLockException) {
                    channel.close()
                    return false
                }
                if (lock == null) {
                    channel.close()
                    false
                } else {
                    heldChannel = channel
                    heldLock = lock
                    heldLockKey = lockKey
                    true
                }
            } catch (_: Throwable) {
                false
            }
        }
    }

    actual fun releaseSingleInstanceLock() {
        synchronized(lockSync) {
            runCatching { heldLock?.release() }
            runCatching { heldChannel?.close() }
            heldLock = null
            heldChannel = null
            heldLockKey = null
        }
    }

    actual fun spawnDetachedProcess(
        executablePath: String,
        args: List<String>,
        workingDir: String?
    ): Boolean {
        return try {
            val command = ArrayList<String>(1 + args.size).apply {
                add(executablePath)
                addAll(args)
            }
            val pb = ProcessBuilder(command)
            if (!workingDir.isNullOrBlank()) {
                pb.directory(File(workingDir))
            }
            pb.redirectInput(ProcessBuilder.Redirect.DISCARD)
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
            pb.redirectError(ProcessBuilder.Redirect.DISCARD)
            pb.start()
            true
        } catch (_: Throwable) {
            false
        }
    }

    actual fun isAutostartEnabled(appId: String, appName: String): Boolean =
        autostartTargetFile(appId, appName).isFile

    actual fun setAutostartEnabled(
        appId: String,
        appName: String,
        targetExecutablePath: String,
        args: List<String>,
        enabled: Boolean
    ): Boolean {
        val file = autostartTargetFile(appId, appName)
        if (!enabled) {
            return !file.exists() || file.delete()
        }
        return try {
            file.parentFile?.mkdirs()
            val os = System.getProperty("os.name").orEmpty().lowercase()
            when {
                os.contains("win") -> {
                    val bytes = WindowsShortcutWriter.buildLnkBytes(
                        targetPath = targetExecutablePath,
                        arguments = args.joinToString(" "),
                        workingDirectory = File(targetExecutablePath).parentFile?.absolutePath.orEmpty(),
                        description = appName
                    )
                    file.writeBytes(bytes)
                }
                os.contains("mac") || os.contains("darwin") -> {
                    val progArgs = buildList {
                        add(targetExecutablePath)
                        addAll(args)
                    }.joinToString("\n        ") { "<string>${escapeXml(it)}</string>" }
                    val label = "${sanitizeId(appId)}.serviceworker"
                    file.writeText(
                        """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>${escapeXml(label)}</string>
    <key>ProgramArguments</key>
    <array>
        $progArgs
    </array>
    <key>RunAtLoad</key>
    <true/>
</dict>
</plist>
""",
                        Charsets.UTF_8
                    )
                }
                else -> {
                    val execLine = buildList {
                        add(quoteDesktopExecArg(targetExecutablePath))
                        args.forEach { add(quoteDesktopExecArg(it)) }
                    }.joinToString(" ")
                    file.writeText(
                        """[Desktop Entry]
Type=Application
Version=1.0
Name=$appName
Exec=$execLine
Terminal=false
X-GNOME-Autostart-enabled=true
""",
                        Charsets.UTF_8
                    )
                }
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    actual fun generateRandomHexToken(byteCount: Int): String {
        val bytes = ByteArray(byteCount.coerceAtLeast(16))
        secureRandom.nextBytes(bytes)
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append("0123456789abcdef"[v ushr 4])
            sb.append("0123456789abcdef"[v and 0x0F])
        }
        return sb.toString()
    }

    actual fun sleepMillis(ms: Long) {
        if (ms > 0) {
            runCatching { Thread.sleep(ms) }
        }
    }

    actual fun resolveSiblingBinaryCommand(
        siblingBaseName: String,
        jvmMainClassFqn: String
    ): Pair<String, List<String>>? {
        if (jvmMainClassFqn.isBlank()) return null
        val javaHome = System.getProperty("java.home").orEmpty()
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val javaBin = if (os.contains("win")) {
            File(javaHome, "bin/javaw.exe").takeIf { it.isFile }
                ?: File(javaHome, "bin/java.exe")
        } else {
            File(javaHome, "bin/java")
        }
        val cp = System.getProperty("java.class.path").orEmpty()
        val args = buildList {
            if (cp.isNotBlank()) {
                add("-cp")
                add(cp)
            }
            add(jvmMainClassFqn)
        }
        return javaBin.absolutePath to args
    }

    private fun autostartTargetFile(appId: String, appName: String): File {
        val cleanId = sanitizeId(appId)
        val cleanName = appName.trim().ifEmpty { cleanId }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val home = System.getProperty("user.home").orEmpty()
        return when {
            os.contains("win") -> {
                val appData = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
                    ?: File(home, "AppData/Roaming").absolutePath
                File(appData, "Microsoft/Windows/Start Menu/Programs/Startup/$cleanName.lnk")
            }
            os.contains("mac") || os.contains("darwin") -> {
                File(home, "Library/LaunchAgents/$cleanId.serviceworker.plist")
            }
            else -> {
                val configHome = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
                    ?: File(home, ".config").absolutePath
                File(configHome, "autostart/$cleanId-serviceworker.desktop")
            }
        }
    }

    private fun sanitizeId(raw: String): String =
        raw.trim().ifEmpty { "com.nexustools.genericui.app" }.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    private fun quoteDesktopExecArg(arg: String): String =
        if (arg.any { it.isWhitespace() || it == '"' }) {
            "\"${arg.replace("\"", "\\\"")}\""
        } else {
            arg
        }

    private fun escapeXml(raw: String): String =
        raw.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
