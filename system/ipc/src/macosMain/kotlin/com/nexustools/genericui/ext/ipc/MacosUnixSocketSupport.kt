@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.ipc

import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.AF_UNIX
import platform.posix.EAGAIN
import platform.posix.ECONNREFUSED
import platform.posix.EEXIST
import platform.posix.EINPROGRESS
import platform.posix.EINTR
import platform.posix.ENOENT
import platform.posix.EWOULDBLOCK
import platform.posix.FD_CLOEXEC
import platform.posix.F_GETFD
import platform.posix.F_GETFL
import platform.posix.F_SETFD
import platform.posix.F_SETFL
import platform.posix.O_NONBLOCK
import platform.posix.POLLOUT
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_ERROR
import platform.posix.SO_NOSIGPIPE
import platform.posix.S_IFDIR
import platform.posix.S_IFMT
import platform.posix.S_IFSOCK
import platform.posix.S_IRUSR
import platform.posix.S_IWUSR
import platform.posix.S_IRWXU
import platform.posix.bind
import platform.posix.chmod
import platform.posix.close
import platform.posix.connect
import platform.posix.errno
import platform.posix.fcntl
import platform.posix.geteuid
import platform.posix.getpeereid
import platform.posix.getsockopt
import platform.posix.listen
import platform.posix.lstat
import platform.posix.mkdir
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.setsockopt
import platform.posix.sockaddr
import platform.posix.socket
import platform.posix.stat
import platform.posix.unlink

/**
 * Darwin `AF_UNIX` `sockaddr_un` encoding, `0700`/`0600` hardening, stale-socket probing,
 * `FD_CLOEXEC`, `SO_NOSIGPIPE`, and `getpeereid` same-UID verification helpers.
 */
internal object MacosUnixSocketSupport {

    private const val SOCKADDR_UN_MAX_SIZE: Int = 106
    private const val SOCKADDR_UN_MAX_PATH_BYTES: Int = 103

    fun buildSockaddrUn(socketPath: String): Pair<ByteArray, UInt>? {
        val pathBytes = socketPath.encodeToByteArray()
        if (pathBytes.isEmpty() || pathBytes.size > SOCKADDR_UN_MAX_PATH_BYTES) {
            return null
        }
        val totalLen = 2 + pathBytes.size + 1
        val buf = ByteArray(SOCKADDR_UN_MAX_SIZE)
        buf[0] = totalLen.toByte()
        buf[1] = AF_UNIX.toByte()
        pathBytes.copyInto(buf, destinationOffset = 2)
        buf[2 + pathBytes.size] = 0
        return buf to totalLen.toUInt()
    }

    fun configureSocketFd(fd: Int) {
        if (fd < 0) return
        val fdFlags = fcntl(fd, F_GETFD)
        if (fdFlags >= 0) {
            fcntl(fd, F_SETFD, fdFlags or FD_CLOEXEC)
        } else {
            fcntl(fd, F_SETFD, FD_CLOEXEC)
        }
        memScoped {
            val one = alloc<IntVar>().apply { value = 1 }
            setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, one.ptr, sizeOf<IntVar>().convert())
        }
    }

    fun setCloseOnExec(fd: Int) {
        if (fd < 0) return
        val fdFlags = fcntl(fd, F_GETFD)
        if (fdFlags >= 0) {
            fcntl(fd, F_SETFD, fdFlags or FD_CLOEXEC)
        } else {
            fcntl(fd, F_SETFD, FD_CLOEXEC)
        }
    }

    fun setNonBlocking(fd: Int) {
        if (fd < 0) return
        val flags = fcntl(fd, F_GETFL)
        if (flags >= 0) {
            fcntl(fd, F_SETFL, flags or O_NONBLOCK)
        } else {
            fcntl(fd, F_SETFL, O_NONBLOCK)
        }
    }

    fun verifyPeerSameUid(fd: Int): Boolean = memScoped {
        val uidVar = alloc<UIntVar>()
        val gidVar = alloc<UIntVar>()
        if (getpeereid(fd, uidVar.ptr, gidVar.ptr) != 0) {
            return@memScoped false
        }
        uidVar.value == geteuid()
    }

    fun bindAndListen(socketPath: String): Int {
        val parentDir = socketPath.substringBeforeLast('/', "")
        if (parentDir.isEmpty() || !ensureOwnerOnlyDirectoryTree(parentDir, geteuid())) {
            return -1
        }
        val (addrBuf, addrLen) = buildSockaddrUn(socketPath) ?: return -1
        memScoped {
            val st = alloc<stat>()
            if (lstat(socketPath, st.ptr) == 0) {
                if ((st.st_mode.toInt() and S_IFMT) != S_IFSOCK) {
                    return -1
                }
                if (isSocketAlive(addrBuf, addrLen)) {
                    return -1
                }
                unlink(socketPath)
            }
        }
        val fd = socket(AF_UNIX, SOCK_STREAM, 0)
        if (fd < 0) return -1
        configureSocketFd(fd)
        val bound = addrBuf.usePinned { pinned ->
            val saPtr = pinned.addressOf(0).reinterpret<sockaddr>()
            bind(fd, saPtr, addrLen) == 0
        }
        if (!bound) {
            close(fd)
            return -1
        }
        chmod(socketPath, (S_IRUSR or S_IWUSR).convert())
        if (listen(fd, 32) != 0) {
            close(fd)
            unlink(socketPath)
            return -1
        }
        return fd
    }

    fun connect(socketPath: String, timeoutMs: Int, config: IpcConfig): IpcConnection? {
        val (addrBuf, addrLen) = buildSockaddrUn(socketPath) ?: return null
        val fd = socket(AF_UNIX, SOCK_STREAM, 0)
        if (fd < 0) return null
        configureSocketFd(fd)

        val oldFlags = fcntl(fd, F_GETFL)
        val nonBlockingEnabled = oldFlags >= 0 &&
            fcntl(fd, F_SETFL, oldFlags or O_NONBLOCK) == 0

        val connectRc = addrBuf.usePinned { pinned ->
            connect(fd, pinned.addressOf(0).reinterpret(), addrLen)
        }
        if (connectRc != 0) {
            val err = errno
            if (!nonBlockingEnabled || (err != EINPROGRESS && err != EAGAIN && err != EWOULDBLOCK)) {
                close(fd)
                return null
            }
            if (!waitWritableAndCheckNoError(fd, timeoutMs.coerceAtLeast(1))) {
                close(fd)
                return null
            }
        }

        if (nonBlockingEnabled) {
            fcntl(fd, F_SETFL, oldFlags and O_NONBLOCK.inv())
        }
        if (!verifyPeerSameUid(fd)) {
            close(fd)
            return null
        }
        return MacosUnixSocketConnection(fd, config.maxPacketBytes)
    }

    private fun isSocketAlive(addrBuf: ByteArray, addrLen: UInt): Boolean {
        val probeFd = socket(AF_UNIX, SOCK_STREAM, 0)
        if (probeFd < 0) return false
        return try {
            configureSocketFd(probeFd)
            val flags = fcntl(probeFd, F_GETFL)
            if (flags >= 0) {
                fcntl(probeFd, F_SETFL, flags or O_NONBLOCK)
            }
            val rc = addrBuf.usePinned { pinned ->
                connect(probeFd, pinned.addressOf(0).reinterpret(), addrLen)
            }
            if (rc == 0) {
                true
            } else {
                val err = errno
                when (err) {
                    EINPROGRESS, EAGAIN, EWOULDBLOCK -> waitWritableAndCheckNoError(probeFd, 50)
                    ECONNREFUSED, ENOENT -> false
                    else -> false
                }
            }
        } finally {
            close(probeFd)
        }
    }

    private fun waitWritableAndCheckNoError(fd: Int, timeoutMs: Int): Boolean = memScoped {
        val pfd = alloc<pollfd>()
        pfd.fd = fd
        pfd.events = POLLOUT.toShort()
        pfd.revents = 0
        var rc: Int
        do {
            rc = poll(pfd.ptr, 1u, timeoutMs)
        } while (rc < 0 && errno == EINTR)
        if (rc <= 0) return@memScoped false

        val soError = alloc<IntVar>()
        val len = alloc<UIntVar>().apply { value = sizeOf<IntVar>().convert() }
        if (getsockopt(fd, SOL_SOCKET, SO_ERROR, soError.ptr, len.ptr) != 0) {
            return@memScoped false
        }
        soError.value == 0
    }

    private fun ensureOwnerOnlyDirectoryTree(dirPath: String, expectedUid: UInt): Boolean {
        val clean = dirPath.trimEnd('/')
        if (clean.isEmpty()) return false
        var current = if (clean.startsWith('/')) "/" else ""
        for (part in clean.split('/')) {
            if (part.isEmpty()) continue
            current = if (current == "/" || current.isEmpty()) "$current$part" else "$current/$part"
            if (mkdir(current, S_IRWXU.convert()) != 0 && errno != EEXIST) {
                return false
            }
        }
        return memScoped {
            val st = alloc<stat>()
            if (lstat(clean, st.ptr) != 0) {
                return@memScoped false
            }
            if ((st.st_mode.toInt() and S_IFMT) != S_IFDIR || st.st_uid != expectedUid) {
                return@memScoped false
            }
            chmod(clean, S_IRWXU.convert())
            true
        }
    }
}
