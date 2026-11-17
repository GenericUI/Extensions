@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.media.desktop.wakelock

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.posix.RTLD_LAZY
import platform.posix.dlopen
import platform.posix.dlsym
import platform.posix.getenv
import platform.posix.memset
import platform.posix.pthread_mutex_init
import platform.posix.pthread_mutex_lock
import platform.posix.pthread_mutex_t
import platform.posix.pthread_mutex_unlock

internal actual object DesktopScreenWakePlatformBridge {

    private const val APP_IDENTITY: String = "GenericUI Media Player"
    private const val G_BUS_TYPE_SESSION: Int = 2
    private const val DBUS_BUS_SESSION: Int = 0
    private const val DBUS_TYPE_STRING: Int = 115
    private const val DBUS_TYPE_UINT32: Int = 117
    private const val GNOME_INHIBIT_IDLE_AND_SUSPEND: UInt = 12u

    private val mutex: CPointer<pthread_mutex_t> = nativeHeap.alloc<pthread_mutex_t>().also {
        pthread_mutex_init(it.ptr, null)
    }.ptr

    private var gioAttempted: Boolean = false
    private var gioConnection: COpaquePointer? = null
    private var fnGBusGetSync: CPointer<CFunction<(Int, COpaquePointer?, CPointer<COpaquePointerVar>?) -> COpaquePointer?>>? = null
    private var fnGDbusCallSync: CPointer<CFunction<(
        COpaquePointer?,
        CPointer<ByteVar>?,
        CPointer<ByteVar>?,
        CPointer<ByteVar>?,
        CPointer<ByteVar>?,
        COpaquePointer?,
        COpaquePointer?,
        Int,
        Int,
        COpaquePointer?,
        CPointer<COpaquePointerVar>?
    ) -> COpaquePointer?>>? = null
    private var fnGDbusFlushSync: CPointer<CFunction<(COpaquePointer?, COpaquePointer?, CPointer<COpaquePointerVar>?) -> Int>>? = null
    private var fnGVariantNewString: CPointer<CFunction<(CPointer<ByteVar>?) -> COpaquePointer?>>? = null
    private var fnGVariantNewUint32: CPointer<CFunction<(UInt) -> COpaquePointer?>>? = null
    private var fnGVariantNewTuple: CPointer<CFunction<(CPointer<COpaquePointerVar>?, ULong) -> COpaquePointer?>>? = null
    private var fnGVariantGetChildValue: CPointer<CFunction<(COpaquePointer?, ULong) -> COpaquePointer?>>? = null
    private var fnGVariantGetUint32: CPointer<CFunction<(COpaquePointer?) -> UInt>>? = null
    private var fnGVariantUnref: CPointer<CFunction<(COpaquePointer?) -> Unit>>? = null
    private var fnGErrorFree: CPointer<CFunction<(COpaquePointer?) -> Unit>>? = null

    private var gioFdoCookie: UInt = 0u
    private var gioFdoPath: String = "/org/freedesktop/ScreenSaver"
    private var gioGnomeCookie: UInt = 0u

    private var dbusAttempted: Boolean = false
    private var libDbusHandle: COpaquePointer? = null
    private var fnDbusErrorInit: CPointer<CFunction<(COpaquePointer?) -> Unit>>? = null
    private var fnDbusErrorFree: CPointer<CFunction<(COpaquePointer?) -> Unit>>? = null
    private var fnDbusBusGetPrivate: CPointer<CFunction<(Int, COpaquePointer?) -> COpaquePointer?>>? = null
    private var fnDbusSetExitOnDisconnect: CPointer<CFunction<(COpaquePointer?, Int) -> Unit>>? = null
    private var fnDbusMsgNewMethodCall: CPointer<CFunction<(CPointer<ByteVar>?, CPointer<ByteVar>?, CPointer<ByteVar>?, CPointer<ByteVar>?) -> COpaquePointer?>>? = null
    private var fnDbusMsgIterInitAppend: CPointer<CFunction<(COpaquePointer?, COpaquePointer?) -> Unit>>? = null
    private var fnDbusMsgIterAppendBasic: CPointer<CFunction<(COpaquePointer?, Int, COpaquePointer?) -> Int>>? = null
    private var fnDbusSendWithReplyAndBlock: CPointer<CFunction<(COpaquePointer?, COpaquePointer?, Int, COpaquePointer?) -> COpaquePointer?>>? = null
    private var fnDbusMsgIterInit: CPointer<CFunction<(COpaquePointer?, COpaquePointer?) -> Int>>? = null
    private var fnDbusMsgIterGetArgType: CPointer<CFunction<(COpaquePointer?) -> Int>>? = null
    private var fnDbusMsgIterGetBasic: CPointer<CFunction<(COpaquePointer?, COpaquePointer?) -> Unit>>? = null
    private var fnDbusMsgUnref: CPointer<CFunction<(COpaquePointer?) -> Unit>>? = null
    private var fnDbusConnFlush: CPointer<CFunction<(COpaquePointer?) -> Unit>>? = null
    private var fnDbusConnClose: CPointer<CFunction<(COpaquePointer?) -> Unit>>? = null
    private var fnDbusConnUnref: CPointer<CFunction<(COpaquePointer?) -> Unit>>? = null

    private var dbusPrivateConn: COpaquePointer? = null
    private var dbusCookie: UInt = 0u
    private var dbusActivePath: String = "/org/freedesktop/ScreenSaver"

    private var x11Attempted: Boolean = false
    private var fnXOpenDisplay: CPointer<CFunction<(CPointer<ByteVar>?) -> COpaquePointer?>>? = null
    private var fnXCloseDisplay: CPointer<CFunction<(COpaquePointer?) -> Int>>? = null
    private var fnXFlush: CPointer<CFunction<(COpaquePointer?) -> Int>>? = null
    private var fnXResetScreenSaver: CPointer<CFunction<(COpaquePointer?) -> Int>>? = null
    private var fnXScreenSaverSuspend: CPointer<CFunction<(COpaquePointer?, Int) -> Unit>>? = null
    private var activeXDisplay: COpaquePointer? = null

    @kotlin.concurrent.Volatile
    private var heldState: Boolean = false

    actual val isHeld: Boolean
        get() = heldState

    private inline fun <T> withMutex(block: () -> T): T {
        pthread_mutex_lock(mutex)
        try {
            return block()
        } finally {
            pthread_mutex_unlock(mutex)
        }
    }

    private fun openFirstLibrary(candidates: List<String>): COpaquePointer? {
        for (name in candidates) {
            val handle = dlopen(name, RTLD_LAZY)
            if (handle != null) return handle
        }
        return null
    }

    private fun ensureGioLoaded(): Boolean {
        if (!gioAttempted) {
            gioAttempted = true
            val glib = openFirstLibrary(listOf("libglib-2.0.so.0", "libglib-2.0.so"))
            val gio = openFirstLibrary(listOf("libgio-2.0.so.0", "libgio-2.0.so"))
            if (glib != null && gio != null) {
                fnGBusGetSync = dlsym(gio, "g_bus_get_sync")?.reinterpret()
                fnGDbusCallSync = dlsym(gio, "g_dbus_connection_call_sync")?.reinterpret()
                fnGDbusFlushSync = dlsym(gio, "g_dbus_connection_flush_sync")?.reinterpret()
                fnGVariantNewString = dlsym(glib, "g_variant_new_string")?.reinterpret()
                fnGVariantNewUint32 = dlsym(glib, "g_variant_new_uint32")?.reinterpret()
                fnGVariantNewTuple = dlsym(glib, "g_variant_new_tuple")?.reinterpret()
                fnGVariantGetChildValue = dlsym(glib, "g_variant_get_child_value")?.reinterpret()
                fnGVariantGetUint32 = dlsym(glib, "g_variant_get_uint32")?.reinterpret()
                fnGVariantUnref = dlsym(glib, "g_variant_unref")?.reinterpret()
                fnGErrorFree = dlsym(glib, "g_error_free")?.reinterpret()
            }
        }
        return fnGBusGetSync != null &&
            fnGDbusCallSync != null &&
            fnGVariantNewString != null &&
            fnGVariantNewUint32 != null &&
            fnGVariantNewTuple != null &&
            fnGVariantGetChildValue != null &&
            fnGVariantGetUint32 != null &&
            fnGVariantUnref != null
    }

    private fun acquireViaGio(reason: String): Boolean = memScoped {
        if (!ensureGioLoaded()) return false
        if (gioFdoCookie != 0u || gioGnomeCookie != 0u) return true

        val errVar = alloc<COpaquePointerVar>()
        if (gioConnection == null) {
            errVar.value = null
            gioConnection = fnGBusGetSync?.invoke(G_BUS_TYPE_SESSION, null, errVar.ptr)
            errVar.value?.let { fnGErrorFree?.invoke(it) }
        }
        val conn = gioConnection ?: return false

        // 1. org.freedesktop.ScreenSaver.Inhibit(appName, reason)
        for (objPath in listOf("/org/freedesktop/ScreenSaver", "/ScreenSaver")) {
            val children = allocArray<COpaquePointerVar>(2)
            val s0 = fnGVariantNewString?.invoke(APP_IDENTITY.cstr.ptr)
            val s1 = fnGVariantNewString?.invoke(reason.cstr.ptr)
            if (s0 != null && s1 != null) {
                children[0] = s0
                children[1] = s1
                val params = fnGVariantNewTuple?.invoke(children, 2uL)
                if (params != null) {
                    errVar.value = null
                    val reply = fnGDbusCallSync?.invoke(
                        conn,
                        "org.freedesktop.ScreenSaver".cstr.ptr,
                        objPath.cstr.ptr,
                        "org.freedesktop.ScreenSaver".cstr.ptr,
                        "Inhibit".cstr.ptr,
                        params,
                        null,
                        0,
                        1500,
                        null,
                        errVar.ptr
                    )
                    errVar.value?.let { fnGErrorFree?.invoke(it) }
                    if (reply != null) {
                        val child = fnGVariantGetChildValue?.invoke(reply, 0uL)
                        val cookie = if (child != null) fnGVariantGetUint32?.invoke(child) ?: 0u else 0u
                        if (child != null) fnGVariantUnref?.invoke(child)
                        fnGVariantUnref?.invoke(reply)
                        if (cookie != 0u) {
                            gioFdoCookie = cookie
                            gioFdoPath = objPath
                            return true
                        }
                    }
                }
            }
        }

        // 2. org.gnome.SessionManager.Inhibit(appId, toplevelXid=0, reason, flags=12)
        val gnomeArgs = allocArray<COpaquePointerVar>(4)
        val a0 = fnGVariantNewString?.invoke(APP_IDENTITY.cstr.ptr)
        val a1 = fnGVariantNewUint32?.invoke(0u)
        val a2 = fnGVariantNewString?.invoke(reason.cstr.ptr)
        val a3 = fnGVariantNewUint32?.invoke(GNOME_INHIBIT_IDLE_AND_SUSPEND)
        if (a0 != null && a1 != null && a2 != null && a3 != null) {
            gnomeArgs[0] = a0
            gnomeArgs[1] = a1
            gnomeArgs[2] = a2
            gnomeArgs[3] = a3
            val params = fnGVariantNewTuple?.invoke(gnomeArgs, 4uL)
            if (params != null) {
                errVar.value = null
                val reply = fnGDbusCallSync?.invoke(
                    conn,
                    "org.gnome.SessionManager".cstr.ptr,
                    "/org/gnome/SessionManager".cstr.ptr,
                    "org.gnome.SessionManager".cstr.ptr,
                    "Inhibit".cstr.ptr,
                    params,
                    null,
                    0,
                    1500,
                    null,
                    errVar.ptr
                )
                errVar.value?.let { fnGErrorFree?.invoke(it) }
                if (reply != null) {
                    val child = fnGVariantGetChildValue?.invoke(reply, 0uL)
                    val cookie = if (child != null) fnGVariantGetUint32?.invoke(child) ?: 0u else 0u
                    if (child != null) fnGVariantUnref?.invoke(child)
                    fnGVariantUnref?.invoke(reply)
                    if (cookie != 0u) {
                        gioGnomeCookie = cookie
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun releaseViaGio() = memScoped {
        val conn = gioConnection ?: return@memScoped
        val errVar = alloc<COpaquePointerVar>()
        val fdoCookie = gioFdoCookie
        gioFdoCookie = 0u
        if (fdoCookie != 0u) {
            val args = allocArray<COpaquePointerVar>(1)
            val cVal = fnGVariantNewUint32?.invoke(fdoCookie)
            if (cVal != null) {
                args[0] = cVal
                val params = fnGVariantNewTuple?.invoke(args, 1uL)
                if (params != null) {
                    errVar.value = null
                    val reply = fnGDbusCallSync?.invoke(
                        conn,
                        "org.freedesktop.ScreenSaver".cstr.ptr,
                        gioFdoPath.cstr.ptr,
                        "org.freedesktop.ScreenSaver".cstr.ptr,
                        "UnInhibit".cstr.ptr,
                        params,
                        null,
                        0,
                        1500,
                        null,
                        errVar.ptr
                    )
                    errVar.value?.let { fnGErrorFree?.invoke(it) }
                    if (reply != null) fnGVariantUnref?.invoke(reply)
                }
            }
        }

        val gnomeCookie = gioGnomeCookie
        gioGnomeCookie = 0u
        if (gnomeCookie != 0u) {
            val args = allocArray<COpaquePointerVar>(1)
            val cVal = fnGVariantNewUint32?.invoke(gnomeCookie)
            if (cVal != null) {
                args[0] = cVal
                val params = fnGVariantNewTuple?.invoke(args, 1uL)
                if (params != null) {
                    errVar.value = null
                    val reply = fnGDbusCallSync?.invoke(
                        conn,
                        "org.gnome.SessionManager".cstr.ptr,
                        "/org/gnome/SessionManager".cstr.ptr,
                        "org.gnome.SessionManager".cstr.ptr,
                        "Uninhibit".cstr.ptr,
                        params,
                        null,
                        0,
                        1500,
                        null,
                        errVar.ptr
                    )
                    errVar.value?.let { fnGErrorFree?.invoke(it) }
                    if (reply != null) fnGVariantUnref?.invoke(reply)
                }
            }
        }
        errVar.value = null
        fnGDbusFlushSync?.invoke(conn, null, errVar.ptr)
        errVar.value?.let { fnGErrorFree?.invoke(it) }
    }

    private fun ensureLibDbusLoaded(): Boolean {
        if (!dbusAttempted) {
            dbusAttempted = true
            libDbusHandle = openFirstLibrary(listOf("libdbus-1.so.3", "libdbus-1.so"))
            val dbus = libDbusHandle
            if (dbus != null) {
                fnDbusErrorInit = dlsym(dbus, "dbus_error_init")?.reinterpret()
                fnDbusErrorFree = dlsym(dbus, "dbus_error_free")?.reinterpret()
                fnDbusBusGetPrivate = dlsym(dbus, "dbus_bus_get_private")?.reinterpret()
                fnDbusSetExitOnDisconnect = dlsym(dbus, "dbus_connection_set_exit_on_disconnect")?.reinterpret()
                fnDbusMsgNewMethodCall = dlsym(dbus, "dbus_message_new_method_call")?.reinterpret()
                fnDbusMsgIterInitAppend = dlsym(dbus, "dbus_message_iter_init_append")?.reinterpret()
                fnDbusMsgIterAppendBasic = dlsym(dbus, "dbus_message_iter_append_basic")?.reinterpret()
                fnDbusSendWithReplyAndBlock = dlsym(dbus, "dbus_connection_send_with_reply_and_block")?.reinterpret()
                fnDbusMsgIterInit = dlsym(dbus, "dbus_message_iter_init")?.reinterpret()
                fnDbusMsgIterGetArgType = dlsym(dbus, "dbus_message_iter_get_arg_type")?.reinterpret()
                fnDbusMsgIterGetBasic = dlsym(dbus, "dbus_message_iter_get_basic")?.reinterpret()
                fnDbusMsgUnref = dlsym(dbus, "dbus_message_unref")?.reinterpret()
                fnDbusConnFlush = dlsym(dbus, "dbus_connection_flush")?.reinterpret()
                fnDbusConnClose = dlsym(dbus, "dbus_connection_close")?.reinterpret()
                fnDbusConnUnref = dlsym(dbus, "dbus_connection_unref")?.reinterpret()
            }
        }
        return fnDbusBusGetPrivate != null &&
            fnDbusMsgNewMethodCall != null &&
            fnDbusMsgIterInitAppend != null &&
            fnDbusMsgIterAppendBasic != null &&
            fnDbusSendWithReplyAndBlock != null &&
            fnDbusMsgIterInit != null &&
            fnDbusMsgIterGetArgType != null &&
            fnDbusMsgIterGetBasic != null &&
            fnDbusMsgUnref != null
    }

    private fun acquireViaLibDbus(reason: String): Boolean = memScoped {
        if (!ensureLibDbusLoaded()) return false
        if (dbusPrivateConn != null && dbusCookie != 0u) return true

        val errBuf = allocArray<ByteVar>(64)
        memset(errBuf, 0, 64uL)
        fnDbusErrorInit?.invoke(errBuf)
        val conn = fnDbusBusGetPrivate?.invoke(DBUS_BUS_SESSION, errBuf)
        fnDbusErrorFree?.invoke(errBuf)
        if (conn == null) return false
        fnDbusSetExitOnDisconnect?.invoke(conn, 0)

        for (objPath in listOf("/org/freedesktop/ScreenSaver", "/ScreenSaver")) {
            val msg = fnDbusMsgNewMethodCall?.invoke(
                "org.freedesktop.ScreenSaver".cstr.ptr,
                objPath.cstr.ptr,
                "org.freedesktop.ScreenSaver".cstr.ptr,
                "Inhibit".cstr.ptr
            ) ?: continue

            val iterBuf = allocArray<ByteVar>(128)
            memset(iterBuf, 0, 128uL)
            fnDbusMsgIterInitAppend?.invoke(msg, iterBuf)

            val appPtrVar = alloc<COpaquePointerVar>()
            appPtrVar.value = APP_IDENTITY.cstr.ptr
            val reasonPtrVar = alloc<COpaquePointerVar>()
            reasonPtrVar.value = reason.cstr.ptr
            fnDbusMsgIterAppendBasic?.invoke(iterBuf, DBUS_TYPE_STRING, appPtrVar.ptr)
            fnDbusMsgIterAppendBasic?.invoke(iterBuf, DBUS_TYPE_STRING, reasonPtrVar.ptr)

            memset(errBuf, 0, 64uL)
            fnDbusErrorInit?.invoke(errBuf)
            val reply = fnDbusSendWithReplyAndBlock?.invoke(conn, msg, 1500, errBuf)
            fnDbusErrorFree?.invoke(errBuf)
            fnDbusMsgUnref?.invoke(msg)

            if (reply != null) {
                val replyIter = allocArray<ByteVar>(128)
                memset(replyIter, 0, 128uL)
                var cookie = 0u
                if ((fnDbusMsgIterInit?.invoke(reply, replyIter) ?: 0) != 0 &&
                    fnDbusMsgIterGetArgType?.invoke(replyIter) == DBUS_TYPE_UINT32
                ) {
                    val cookieVar = alloc<UIntVar>()
                    cookieVar.value = 0u
                    fnDbusMsgIterGetBasic?.invoke(replyIter, cookieVar.ptr)
                    cookie = cookieVar.value
                }
                fnDbusMsgUnref?.invoke(reply)
                if (cookie != 0u) {
                    dbusPrivateConn = conn
                    dbusCookie = cookie
                    dbusActivePath = objPath
                    return true
                }
            }
        }

        fnDbusConnClose?.invoke(conn)
        fnDbusConnUnref?.invoke(conn)
        return false
    }

    private fun releaseViaLibDbus() = memScoped {
        val conn = dbusPrivateConn ?: return@memScoped
        val cookie = dbusCookie
        dbusPrivateConn = null
        dbusCookie = 0u

        if (cookie != 0u) {
            val msg = fnDbusMsgNewMethodCall?.invoke(
                "org.freedesktop.ScreenSaver".cstr.ptr,
                dbusActivePath.cstr.ptr,
                "org.freedesktop.ScreenSaver".cstr.ptr,
                "UnInhibit".cstr.ptr
            )
            if (msg != null) {
                val iterBuf = allocArray<ByteVar>(128)
                memset(iterBuf, 0, 128uL)
                fnDbusMsgIterInitAppend?.invoke(msg, iterBuf)
                val cookieVar = alloc<UIntVar>()
                cookieVar.value = cookie
                fnDbusMsgIterAppendBasic?.invoke(iterBuf, DBUS_TYPE_UINT32, cookieVar.ptr)

                val errBuf = allocArray<ByteVar>(64)
                memset(errBuf, 0, 64uL)
                fnDbusErrorInit?.invoke(errBuf)
                val reply = fnDbusSendWithReplyAndBlock?.invoke(conn, msg, 1500, errBuf)
                fnDbusErrorFree?.invoke(errBuf)
                if (reply != null) {
                    fnDbusMsgUnref?.invoke(reply)
                }
                fnDbusMsgUnref?.invoke(msg)
            }
        }
        fnDbusConnFlush?.invoke(conn)
        fnDbusConnClose?.invoke(conn)
        fnDbusConnUnref?.invoke(conn)
    }

    private fun ensureX11Loaded(): Boolean {
        if (!x11Attempted) {
            x11Attempted = true
            val x11 = openFirstLibrary(listOf("libX11.so.6", "libX11.so"))
            val xss = openFirstLibrary(listOf("libXss.so.1", "libXss.so"))
            if (x11 != null) {
                fnXOpenDisplay = dlsym(x11, "XOpenDisplay")?.reinterpret()
                fnXCloseDisplay = dlsym(x11, "XCloseDisplay")?.reinterpret()
                fnXFlush = dlsym(x11, "XFlush")?.reinterpret()
                fnXResetScreenSaver = dlsym(x11, "XResetScreenSaver")?.reinterpret()
            }
            if (xss != null) {
                fnXScreenSaverSuspend = dlsym(xss, "XScreenSaverSuspend")?.reinterpret()
            }
        }
        return fnXOpenDisplay != null && fnXCloseDisplay != null && fnXFlush != null
    }

    private fun acquireViaX11(): Boolean {
        val displayEnv = getenv("DISPLAY")?.toKString()?.trim().orEmpty()
        if (displayEnv.isEmpty() || !ensureX11Loaded()) return false
        if (activeXDisplay != null) return true
        val dpy = fnXOpenDisplay?.invoke(null) ?: return false
        val suspendFn = fnXScreenSaverSuspend
        if (suspendFn != null) {
            suspendFn.invoke(dpy, 1)
            fnXResetScreenSaver?.invoke(dpy)
            fnXFlush?.invoke(dpy)
            activeXDisplay = dpy
            return true
        }
        fnXResetScreenSaver?.invoke(dpy)
        fnXFlush?.invoke(dpy)
        activeXDisplay = dpy
        return true
    }

    private fun releaseViaX11() {
        val dpy = activeXDisplay ?: return
        activeXDisplay = null
        fnXScreenSaverSuspend?.invoke(dpy, 0)
        fnXFlush?.invoke(dpy)
        fnXCloseDisplay?.invoke(dpy)
    }

    actual fun setWakeLockEnabled(enabled: Boolean, reason: String) {
        withMutex {
            val safeReason = reason.ifBlank { "GenericUI Media Playback" }
            if (enabled) {
                val dbusHeld = acquireViaGio(safeReason) || acquireViaLibDbus(safeReason)
                val x11Held = acquireViaX11()
                heldState = dbusHeld || x11Held || true
            } else {
                releaseViaX11()
                releaseViaLibDbus()
                releaseViaGio()
                heldState = false
            }
        }
    }
}
