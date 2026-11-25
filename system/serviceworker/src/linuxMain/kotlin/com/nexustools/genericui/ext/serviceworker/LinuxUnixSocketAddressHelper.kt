@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class
)

package com.nexustools.genericui.ext.serviceworker

import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.AF_UNIX
import platform.posix.EAGAIN
import platform.posix.ECONNREFUSED
import platform.posix.EINPROGRESS
import platform.posix.ENOENT
import platform.posix.EWOULDBLOCK
import platform.posix.FD_CLOEXEC
import platform.posix.F_GETFD
import platform.posix.F_OK
import platform.posix.F_SETFD
import platform.posix.POLLOUT
import platform.posix.SIGPIPE
import platform.posix.SIG_IGN
import platform.posix.SOCK_CLOEXEC
import platform.posix.SOCK_NONBLOCK
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_ERROR
import platform.posix.SO_PEERCRED
import platform.posix.S_IFDIR
import platform.posix.S_IFMT
import platform.posix.S_IRWXU
import platform.posix.access
import platform.posix.chmod
import platform.posix.close
import platform.posix.connect
import platform.posix.fcntl
import platform.posix.getenv
import platform.posix.geteuid
import platform.posix.getsockopt
import platform.posix.lstat
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.posix_errno
import platform.posix.signal
import platform.posix.sockaddr
import platform.posix.socket
import platform.posix.socklen_tVar
import platform.posix.stat
import platform.posix.unlink
import kotlin.concurrent.atomics.AtomicBoolean

/**
 * Linux `AF_UNIX` socket path resolution, `0700` directory hardening, ABI-exact
 * 110-byte `sockaddr_un` / 12-byte `ucred` marshaling, `SO_PEERCRED` verification,
 * and non-blocking stale socket recovery.
 */
internal object LinuxUnixSocketAddressHelper {

    const val MAX_SUN_PATH_BYTES: Int = 100
    const val SOCKADDR_UN_BYTES: Int = 110
    private const val UCRED_BYTES: Int = 12
    private const val MAX_SHORT_BASE_BYTES: Int = 70
    private const val PROBE_TIMEOUT_MS: Int = 100

    private val sigPipeIgnored = AtomicBoolean(false)

    /** Ensures `SIGPIPE` is ignored process-wide so peer disconnects return `EPIPE` cleanly. */
    fun ensureSigPipeIgnored() {
        if (sigPipeIgnored.compareAndSet(expectedValue = false, newValue = true)) {
            signal(SIGPIPE, SIG_IGN)
        }
    }

    /** Sets `FD_CLOEXEC` on [fd] to prevent file descriptor inheritance across `fork()`/`exec()`. */
    fun setCloseOnExec(fd: Int) {
        if (fd < 0) return
        val flags = fcntl(fd, F_GETFD)
        if (flags >= 0) {
            fcntl(fd, F_SETFD, flags or FD_CLOEXEC)
        } else {
            fcntl(fd, F_SETFD, FD_CLOEXEC)
        }
    }

    /**
     * Resolves a user-private `AF_UNIX` path whose UTF-8 length fits cleanly inside Linux's
     * 108-byte `sun_path` limit (capped at [MAX_SUN_PATH_BYTES] bytes).
     */
    fun resolveSocketPath(appId: String, runtimeDir: String): String {
        val cleanRuntime = runtimeDir.trimEnd('/')
        val candidate = if (cleanRuntime.isEmpty()) "/sw.sock" else "$cleanRuntime/sw.sock"
        if (candidate.encodeToByteArray().size <= MAX_SUN_PATH_BYTES) {
            return candidate
        }
        val hash = DesktopIpcHash.fnv1a64Hex("$appId|$runtimeDir")
        val xdg = getenv("XDG_RUNTIME_DIR")?.toKString()?.trimEnd('/')?.takeIf { it.isNotBlank() }
        val shortBase = if (xdg != null && xdg.encodeToByteArray().size <= MAX_SHORT_BASE_BYTES) {
            xdg
        } else {
            "/tmp/genericui-${geteuid()}"
        }
        return "$shortBase/gui-$hash.sock"
    }

    /**
     * Creates the parent directory of [socketPath] if needed, enforces `0700` permissions,
     * and verifies it is a real directory owned by `geteuid()`.
     */
    fun ensureSecureParentDir(socketPath: String): Boolean {
        val parentDir = socketPath.substringBeforeLast('/', "")
        if (parentDir.isEmpty()) return false
        if (!DesktopOsHelpers.ensureDirectoryExists(parentDir)) return false
        chmod(parentDir, S_IRWXU.convert())
        return memScoped {
            val st = alloc<stat>()
            if (lstat(parentDir, st.ptr) != 0) return@memScoped false
            val isDir = (st.st_mode.toInt() and S_IFMT) == S_IFDIR
            isDir && st.st_uid == geteuid()
        }
    }

    /**
     * Encodes an ABI-exact 110-byte Linux `struct sockaddr_un` (`sun_family = AF_UNIX`,
     * NUL-terminated UTF-8 `sun_path`). Returns `null` if [socketPath] is empty or exceeds 107 bytes.
     */
    fun buildSockaddrUn(socketPath: String): ByteArray? {
        val pathBytes = socketPath.encodeToByteArray()
        if (pathBytes.isEmpty() || pathBytes.size > SOCKADDR_UN_BYTES - 3) {
            return null
        }
        val buf = ByteArray(SOCKADDR_UN_BYTES)
        buf[0] = (AF_UNIX and 0xFF).toByte()
        buf[1] = ((AF_UNIX ushr 8) and 0xFF).toByte()
        pathBytes.copyInto(buf, destinationOffset = 2)
        buf[2 + pathBytes.size] = 0
        return buf
    }

    fun sockaddrUnLength(socketPath: String): Int =
        2 + socketPath.encodeToByteArray().size + 1

    /**
     * Reads `(pid, uid, gid)` from `SO_PEERCRED` (`12`-byte Linux `struct ucred`) on [fd].
     */
    fun readPeerCredentials(fd: Int): Triple<Int, UInt, UInt>? {
        if (fd < 0) return null
        return memScoped {
            val credBuf = ByteArray(UCRED_BYTES)
            val lenVar = alloc<socklen_tVar>()
            lenVar.value = UCRED_BYTES.convert()
            val rc = credBuf.usePinned { pinned ->
                getsockopt(fd, SOL_SOCKET, SO_PEERCRED, pinned.addressOf(0), lenVar.ptr)
            }
            if (rc != 0 || lenVar.value.toInt() < UCRED_BYTES) {
                return@memScoped null
            }
            val pid = readInt32LE(credBuf, 0)
            val uid = readUInt32LE(credBuf, 4)
            val gid = readUInt32LE(credBuf, 8)
            Triple(pid, uid, gid)
        }
    }

    /** Returns `true` iff `SO_PEERCRED` reports peer UID == `geteuid()`. */
    fun verifyPeerCredentials(fd: Int): Boolean {
        val creds = readPeerCredentials(fd) ?: return false
        return creds.second == geteuid()
    }

    /**
     * Checks whether [socketPath] already has a live listener before removing a stale socket file.
     * Returns `true` when [socketPath] is absent or was safely unlinked after `ECONNREFUSED`.
     */
    fun recoverStaleSocketIfNeeded(socketPath: String): Boolean {
        if (access(socketPath, F_OK) != 0) {
            return true
        }
        val sunBuf = buildSockaddrUn(socketPath) ?: return false
        val addrLen = sockaddrUnLength(socketPath)
        val probeFd = socket(AF_UNIX, SOCK_STREAM or SOCK_NONBLOCK or SOCK_CLOEXEC, 0)
        if (probeFd < 0) {
            return false
        }
        try {
            setCloseOnExec(probeFd)
            val connectRc = sunBuf.usePinned { pinned ->
                connect(probeFd, pinned.addressOf(0).reinterpret<sockaddr>(), addrLen.convert())
            }
            if (connectRc == 0) {
                // Live server is actively listening on this socket.
                return false
            }
            val err = posix_errno()
            if (err == ENOENT) {
                return true
            }
            if (err == ECONNREFUSED) {
                return unlink(socketPath) == 0
            }
            if (err != EINPROGRESS && err != EAGAIN && err != EWOULDBLOCK) {
                return false
            }
            return memScoped {
                val pfd = alloc<pollfd>()
                pfd.fd = probeFd
                pfd.events = POLLOUT.toShort()
                pfd.revents = 0
                val pollRc = poll(pfd.ptr, 1.convert(), PROBE_TIMEOUT_MS)
                if (pollRc <= 0) {
                    return@memScoped false
                }
                val soErr = alloc<IntVar>()
                val soLen = alloc<socklen_tVar>().apply { value = sizeOf<IntVar>().convert() }
                if (getsockopt(probeFd, SOL_SOCKET, SO_ERROR, soErr.ptr, soLen.ptr) != 0) {
                    return@memScoped false
                }
                when (soErr.value) {
                    ECONNREFUSED -> unlink(socketPath) == 0
                    ENOENT -> true
                    else -> false
                }
            }
        } finally {
            close(probeFd)
        }
    }

    private fun readInt32LE(buf: ByteArray, offset: Int): Int =
        (buf[offset].toInt() and 0xFF) or
            ((buf[offset + 1].toInt() and 0xFF) shl 8) or
            ((buf[offset + 2].toInt() and 0xFF) shl 16) or
            ((buf[offset + 3].toInt() and 0xFF) shl 24)

    private fun readUInt32LE(buf: ByteArray, offset: Int): UInt =
        (buf[offset].toUInt() and 0xFFu) or
            ((buf[offset + 1].toUInt() and 0xFFu) shl 8) or
            ((buf[offset + 2].toUInt() and 0xFFu) shl 16) or
            ((buf[offset + 3].toUInt() and 0xFFu) shl 24)
}
