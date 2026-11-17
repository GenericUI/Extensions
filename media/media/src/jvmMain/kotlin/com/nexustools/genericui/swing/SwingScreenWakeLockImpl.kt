package com.nexustools.genericui.swing

import com.nexustools.genericui.nativeimpl.system.ScreenWakeLockNativeImpl
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference

/**
 * JVM/Swing [ScreenWakeLockNativeImpl] backed by JNA native OS power/screensaver APIs:
 * - macOS: `IOKit` (`IOPMAssertionCreateWithName` / `IOPMAssertionRelease` for display & idle sleep)
 * - Windows: `kernel32` (`SetThreadExecutionState` pinned to a dedicated keeper thread)
 * - Linux: `libgio-2.0` (`org.freedesktop.ScreenSaver.Inhibit`) and `libX11` + `libXss` (`XScreenSaverSuspend`)
 */
class SwingScreenWakeLockImpl : ScreenWakeLockNativeImpl {

    private val lock = Any()

    @Volatile
    override var isDisposed: Boolean = false
        private set

    @Volatile
    private var heldState: Boolean = false

    override val isWakeLockHeld: Boolean
        get() = !isDisposed && heldState

    // macOS IOKit assertion IDs
    private var macDisplayAssertionId: Int = 0
    private var macSystemAssertionId: Int = 0

    // Windows dedicated thread state (ES_CONTINUOUS is tracked per OS thread on Windows)
    private var winThread: Thread? = null
    private var winThreadLock = Object()
    private var winDesiredEnabled: Boolean = false
    private var winAppliedEnabled: Boolean = false
    private var winShutdownThread: Boolean = false

    // Linux X11 + GIO state
    private var linuxXDisplay: Pointer? = null
    private var linuxXssActive: Boolean = false
    private var linuxFdoBus: Pointer? = null
    private var linuxFdoCookie: Int = 0
    private var linuxHasFdoCookie: Boolean = false

    override fun setWakeLockEnabled(enabled: Boolean, reason: String) {
        synchronized(lock) {
            if (isDisposed && enabled) return
            val safeReason = reason.ifBlank { "GenericUI Media Playback" }
            when {
                Platform.isMac() -> applyMacWakeLock(enabled, safeReason)
                Platform.isWindows() -> applyWindowsWakeLock(enabled)
                Platform.isLinux() -> applyLinuxWakeLock(enabled, safeReason)
                else -> heldState = enabled
            }
        }
    }

    override fun dispose() {
        synchronized(lock) {
            if (isDisposed) return
            isDisposed = true
            when {
                Platform.isMac() -> applyMacWakeLock(false, "GenericUI Media Playback")
                Platform.isWindows() -> stopWindowsKeeperThread()
                Platform.isLinux() -> applyLinuxWakeLock(false, "GenericUI Media Playback")
            }
            heldState = false
        }
    }

    private fun createMacCfString(text: String): Pointer? = runCatching {
        val cf = NativeLibrary.getInstance("CoreFoundation")
        val createFn = cf.getFunction("CFStringCreateWithCString")
        val utf8Bytes = text.toByteArray(Charsets.UTF_8)
        val cStrMem = Memory((utf8Bytes.size + 1).toLong())
        cStrMem.write(0L, utf8Bytes, 0, utf8Bytes.size)
        cStrMem.setByte(utf8Bytes.size.toLong(), 0)
        createFn.invokePointer(arrayOf(Pointer.NULL, cStrMem, 0x08000100))
    }.getOrNull()

    private fun releaseMacCfType(ptr: Pointer?) {
        if (ptr == null || ptr == Pointer.NULL) return
        runCatching {
            NativeLibrary.getInstance("CoreFoundation")
                .getFunction("CFRelease")
                .invokeVoid(arrayOf(ptr))
        }
    }

    private fun createMacIoPmAssertion(assertionType: String, reason: String): Int = runCatching {
        val typeCf = createMacCfString(assertionType) ?: return 0
        val reasonCf = createMacCfString(reason)
        if (reasonCf == null) {
            releaseMacCfType(typeCf)
            return 0
        }
        try {
            val ioKit = NativeLibrary.getInstance("IOKit")
            val createAssertion = ioKit.getFunction("IOPMAssertionCreateWithName")
            val idRef = IntByReference(0)
            val kr = createAssertion.invokeInt(arrayOf(typeCf, 255, reasonCf, idRef))
            if (kr == 0) idRef.value else 0
        } finally {
            releaseMacCfType(reasonCf)
            releaseMacCfType(typeCf)
        }
    }.getOrDefault(0)

    private fun releaseMacIoPmAssertion(assertionId: Int) {
        if (assertionId == 0) return
        runCatching {
            NativeLibrary.getInstance("IOKit")
                .getFunction("IOPMAssertionRelease")
                .invokeInt(arrayOf(assertionId))
        }
    }

    private fun applyMacWakeLock(enabled: Boolean, reason: String) {
        if (enabled) {
            if (macDisplayAssertionId == 0) {
                macDisplayAssertionId = createMacIoPmAssertion("PreventUserIdleDisplaySleep", reason)
            }
            if (macSystemAssertionId == 0) {
                macSystemAssertionId = createMacIoPmAssertion("PreventUserIdleSystemSleep", reason)
            }
            heldState = (macDisplayAssertionId != 0 || macSystemAssertionId != 0)
        } else {
            val disp = macDisplayAssertionId
            macDisplayAssertionId = 0
            releaseMacIoPmAssertion(disp)

            val sys = macSystemAssertionId
            macSystemAssertionId = 0
            releaseMacIoPmAssertion(sys)

            heldState = false
        }
    }

    private fun ensureWindowsKeeperThread() {
        if (winThread != null && winThread!!.isAlive) return
        winShutdownThread = false
        val thread = Thread({
            val setExecState = runCatching {
                NativeLibrary.getInstance("kernel32").getFunction("SetThreadExecutionState")
            }.getOrNull()
            val esContinuous = 0x80000000L.toInt()
            val esActive = (0x80000000L or 0x00000002L or 0x00000001L).toInt()
            while (true) {
                val targetEnabled: Boolean
                val shouldExit: Boolean
                synchronized(winThreadLock) {
                    while (!winShutdownThread && winDesiredEnabled == winAppliedEnabled) {
                        runCatching { winThreadLock.wait(25_000L) }
                        if (!winShutdownThread && winDesiredEnabled && winAppliedEnabled) {
                            // Periodic refresh while active
                            break
                        }
                    }
                    targetEnabled = if (winShutdownThread) false else winDesiredEnabled
                    shouldExit = winShutdownThread
                }
                runCatching {
                    setExecState?.invokeInt(arrayOf(if (targetEnabled) esActive else esContinuous))
                }
                synchronized(winThreadLock) {
                    winAppliedEnabled = targetEnabled
                    winThreadLock.notifyAll()
                }
                if (shouldExit) break
            }
        }, "GenericUI-Swing-ScreenWakeLock").apply {
            isDaemon = true
            start()
        }
        winThread = thread
    }

    private fun applyWindowsWakeLock(enabled: Boolean) {
        if (enabled) {
            ensureWindowsKeeperThread()
        }
        if (winThread != null) {
            synchronized(winThreadLock) {
                winDesiredEnabled = enabled
                winThreadLock.notifyAll()
                var spins = 0
                while (winAppliedEnabled != enabled && spins < 20) {
                    runCatching { winThreadLock.wait(15L) }
                    spins++
                }
            }
        }
        heldState = enabled
    }

    private fun stopWindowsKeeperThread() {
        val t = winThread ?: return
        synchronized(winThreadLock) {
            winDesiredEnabled = false
            winShutdownThread = true
            winThreadLock.notifyAll()
        }
        runCatching { t.join(500L) }
        winThread = null
    }

    private fun applyLinuxWakeLock(enabled: Boolean, reason: String) {
        if (enabled) {
            if (!linuxHasFdoCookie) {
                runCatching {
                    val glib = NativeLibrary.getInstance("glib-2.0")
                    val gio = NativeLibrary.getInstance("gio-2.0")
                    val bus = linuxFdoBus ?: gio.getFunction("g_bus_get_sync")
                        .invokePointer(arrayOf(2, Pointer.NULL, Pointer.NULL))
                        ?.also { linuxFdoBus = it }
                    if (bus != null && bus != Pointer.NULL) {
                        val appVar = glib.getFunction("g_variant_new_string").invokePointer(arrayOf("GenericUI"))
                        val reasonVar = glib.getFunction("g_variant_new_string").invokePointer(arrayOf(reason))
                        val children = Memory((Native.POINTER_SIZE * 2).toLong()).apply {
                            setPointer(0L, appVar)
                            setPointer(Native.POINTER_SIZE.toLong(), reasonVar)
                        }
                        val paramsTuple = glib.getFunction("g_variant_new_tuple").invokePointer(arrayOf(children, 2L))
                        val reply = gio.getFunction("g_dbus_connection_call_sync").invokePointer(
                            arrayOf(
                                bus,
                                "org.freedesktop.ScreenSaver",
                                "/org/freedesktop/ScreenSaver",
                                "org.freedesktop.ScreenSaver",
                                "Inhibit",
                                paramsTuple,
                                Pointer.NULL,
                                0,
                                1500,
                                Pointer.NULL,
                                Pointer.NULL
                            )
                        )
                        if (reply != null && reply != Pointer.NULL) {
                            val child0 = glib.getFunction("g_variant_get_child_value").invokePointer(arrayOf(reply, 0L))
                            if (child0 != null && child0 != Pointer.NULL) {
                                linuxFdoCookie = glib.getFunction("g_variant_get_uint32").invokeInt(arrayOf(child0))
                                linuxHasFdoCookie = true
                                glib.getFunction("g_variant_unref").invokeVoid(arrayOf(child0))
                            }
                            glib.getFunction("g_variant_unref").invokeVoid(arrayOf(reply))
                        }
                    }
                }
            }
            if (!linuxXssActive) {
                runCatching {
                    val x11 = NativeLibrary.getInstance("X11")
                    val xss = NativeLibrary.getInstance("Xss")
                    val dpy = linuxXDisplay ?: x11.getFunction("XOpenDisplay")
                        .invokePointer(arrayOf(Pointer.NULL))
                        ?.also { linuxXDisplay = it }
                    if (dpy != null && dpy != Pointer.NULL) {
                        val evBase = IntByReference(0)
                        val errBase = IntByReference(0)
                        val hasExt = xss.getFunction("XScreenSaverQueryExtension")
                            .invokeInt(arrayOf(dpy, evBase, errBase))
                        if (hasExt != 0) {
                            xss.getFunction("XScreenSaverSuspend").invokeVoid(arrayOf(dpy, 1))
                            x11.getFunction("XFlush").invokeInt(arrayOf(dpy))
                            linuxXssActive = true
                        }
                    }
                }
            }
            heldState = true
        } else {
            if (linuxHasFdoCookie && linuxFdoBus != null) {
                runCatching {
                    val glib = NativeLibrary.getInstance("glib-2.0")
                    val gio = NativeLibrary.getInstance("gio-2.0")
                    val cookieVar = glib.getFunction("g_variant_new_uint32").invokePointer(arrayOf(linuxFdoCookie))
                    val children = Memory(Native.POINTER_SIZE.toLong()).apply {
                        setPointer(0L, cookieVar)
                    }
                    val paramsTuple = glib.getFunction("g_variant_new_tuple").invokePointer(arrayOf(children, 1L))
                    val reply = gio.getFunction("g_dbus_connection_call_sync").invokePointer(
                        arrayOf(
                            linuxFdoBus,
                            "org.freedesktop.ScreenSaver",
                            "/org/freedesktop/ScreenSaver",
                            "org.freedesktop.ScreenSaver",
                            "UnInhibit",
                            paramsTuple,
                            Pointer.NULL,
                            0,
                            1500,
                            Pointer.NULL,
                            Pointer.NULL
                        )
                    )
                    if (reply != null && reply != Pointer.NULL) {
                        glib.getFunction("g_variant_unref").invokeVoid(arrayOf(reply))
                    }
                }
                linuxFdoCookie = 0
                linuxHasFdoCookie = false
            }
            if (linuxXssActive && linuxXDisplay != null) {
                runCatching {
                    val x11 = NativeLibrary.getInstance("X11")
                    val xss = NativeLibrary.getInstance("Xss")
                    xss.getFunction("XScreenSaverSuspend").invokeVoid(arrayOf(linuxXDisplay, 0))
                    x11.getFunction("XFlush").invokeInt(arrayOf(linuxXDisplay))
                }
                linuxXssActive = false
            }
            if (isDisposed && linuxXDisplay != null) {
                runCatching {
                    NativeLibrary.getInstance("X11").getFunction("XCloseDisplay").invokeInt(arrayOf(linuxXDisplay))
                }
                linuxXDisplay = null
            }
            heldState = false
        }
    }
}
