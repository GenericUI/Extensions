@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class
)

package com.nexustools.genericui.ext.serviceworker

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.plus
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSLock
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
import platform.posix.POLLIN
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
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.sockaddr
import platform.posix.socket
import platform.posix.stat
import platform.posix.unlink
import kotlin.concurrent.atomics.AtomicBoolean

/**
 * macOS `AF_UNIX` `SOCK_STREAM` framed [DesktopIpcConnection] (`[4-byte BE length][body]`).
 */
internal class MacosUnixSocketConnection(
    private val fd: Int
) : DesktopIpcConnection {

    private val writeLock = NSLock()
    private val closedFlag = AtomicBoolean(fd < 0)

    init {
        if (fd >= 0) {
            MacosUnixSocketSupport.configureSocketFd(fd)
        }
    }

    override val isClosed: Boolean
        get() = closedFlag.load() || fd < 0

    override fun sendBody(bodyBytes: ByteArray): Boolean {
        if (isClosed) return false
        if (bodyBytes.size > ServiceWorkerFrameCodec.MAX_FRAME_BYTES) return false
        val framed = ByteArray(4 + bodyBytes.size)
        ServiceWorkerFrameCodec.writeIntBE(framed, 0, bodyBytes.size)
        bodyBytes.copyInto(framed, destinationOffset = 4)
        writeLock.lock()
        return try {
            if (isClosed) false else writeExact(framed)
        } finally {
            writeLock.unlock()
        }
    }

    override fun receiveBody(timeoutMs: Int): ByteArray? {
        if (isClosed) return null
        if (timeoutMs > 0 && !waitReadable(timeoutMs)) {
            return null
        }
        val header = readExact(4) ?: return null
        val len = ServiceWorkerFrameCodec.readIntBE(header, 0)
        if (len <= 0 || len > ServiceWorkerFrameCodec.MAX_FRAME_BYTES) {
            close()
            return null
        }
        return readExact(len)
    }

    override fun close() {
        if (!closedFlag.compareAndSet(expectedValue = false, newValue = true)) {
            return
        }
        if (fd >= 0) {
            close(fd)
        }
    }

    private fun waitReadable(timeoutMs: Int): Boolean = memScoped {
        val pfd = alloc<pollfd>()
        pfd.fd = fd
        pfd.events = POLLIN.toShort()
        pfd.revents = 0
        while (!isClosed) {
            val rc = poll(pfd.ptr, 1u, timeoutMs)
            if (rc > 0) return@memScoped true
            if (rc == 0) return@memScoped false
            if (errno != EINTR) return@memScoped false
        }
        false
    }

    private fun readExact(count: Int): ByteArray? {
        val out = ByteArray(count)
        var offset = 0
        out.usePinned { pinned ->
            val basePtr = pinned.addressOf(0).reinterpret<ByteVar>()
            while (offset < count) {
                val n = recv(fd, basePtr + offset, (count - offset).convert(), 0).toInt()
                if (n < 0 && errno == EINTR) {
                    continue
                }
                if (n <= 0) {
                    close()
                    return null
                }
                offset += n
            }
        }
        return out
    }

    private fun writeExact(bytes: ByteArray): Boolean {
        var offset = 0
        bytes.usePinned { pinned ->
            val basePtr = pinned.addressOf(0).reinterpret<ByteVar>()
            while (offset < bytes.size) {
                val n = send(fd, basePtr + offset, (bytes.size - offset).convert(), 0).toInt()
                if (n < 0 && errno == EINTR) {
                    continue
                }
                if (n <= 0) {
                    close()
                    return false
                }
                offset += n
            }
        }
        return true
    }
}

/**
 * Darwin `AF_UNIX` path resolution, `sockaddr_un` encoding, stale-socket probing,
 * `FD_CLOEXEC`, `SO_NOSIGPIPE`, and `getpeereid` same-UID verification helpers.
 */
internal object MacosUnixSocketSupport {

    private const val MAX_DIRECT_PATH_BYTES: Int = 96
    private const val SOCKADDR_UN_MAX_SIZE: Int = 106
    private const val SOCKADDR_UN_MAX_PATH_BYTES: Int = 103
    private const val MODE_0700: Int = 448

    fun resolveSocketPath(appId: String, runtimeDir: String): String? {
        val cleanDir = runtimeDir.trimEnd('/')
        if (cleanDir.isEmpty()) return null
        val direct = "$cleanDir/sw.sock"
        if (direct.encodeToByteArray().size <= MAX_DIRECT_PATH_BYTES) {
            DesktopOsHelpers.ensureDirectoryExists(cleanDir)
            return direct
        }
        val uid = geteuid()
        val tmpDir = "/tmp/genericui-$uid"
        if (!ensureOwnerOnlyDirectory(tmpDir, uid)) {
            return null
        }
        val hash = DesktopIpcHash.fnv1a64Hex("$appId|$runtimeDir")
        return "$tmpDir/$hash.sock"
    }

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

    fun verifyPeerSameUid(fd: Int): Boolean = memScoped {
        val uidVar = alloc<UIntVar>()
        val gidVar = alloc<UIntVar>()
        if (getpeereid(fd, uidVar.ptr, gidVar.ptr) != 0) {
            return@memScoped false
        }
        uidVar.value == geteuid()
    }

    fun bindAndListen(socketPath: String): Int {
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

    fun connect(socketPath: String, timeoutMs: Int): DesktopIpcConnection? {
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
        return MacosUnixSocketConnection(fd)
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

    private fun ensureOwnerOnlyDirectory(dirPath: String, expectedUid: UInt): Boolean {
        if (mkdir(dirPath, MODE_0700.convert()) != 0 && errno != EEXIST) {
            return false
        }
        return memScoped {
            val st = alloc<stat>()
            if (lstat(dirPath, st.ptr) != 0) {
                return@memScoped false
            }
            if ((st.st_mode.toInt() and S_IFMT) != S_IFDIR || st.st_uid != expectedUid) {
                return@memScoped false
            }
            chmod(dirPath, MODE_0700.convert())
            true
        }
    }
}
