@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.desktop.system

import com.nexustools.genericui.component.system.MediaSessionMetadata
import com.nexustools.genericui.threading.GenericUiThreading
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.posix.RTLD_GLOBAL
import platform.posix.RTLD_NOW
import platform.posix.dlopen
import platform.posix.dlsym
import platform.posix.getpid
import kotlin.math.roundToLong

/**
 * Linux (`linuxX64` / `linuxArm64`) actual [DesktopMediaSessionPlatformBridge] exposing an
 * `org.mpris.MediaPlayer2` / `org.mpris.MediaPlayer2.Player` D-Bus object via `libgio-2.0.so.0`.
 */
internal actual class DesktopMediaSessionPlatformBridge actual constructor(
    private val backendToolkit: String
) {
    private object GioSessionBindings {
        private const val G_BUS_TYPE_SESSION = 2
        private var initialized = false
        private var available = false

        private var fnFree: CPointer<CFunction<(COpaquePointer?) -> Unit>>? = null
        private var fnMalloc0: CPointer<CFunction<(ULong) -> COpaquePointer?>>? = null
        private var fnErrorFree: CPointer<CFunction<(COpaquePointer?) -> Unit>>? = null
        private var fnVariantTypeNew: CPointer<CFunction<(CPointer<ByteVar>?) -> COpaquePointer?>>? = null
        private var fnVariantTypeFree: CPointer<CFunction<(COpaquePointer?) -> Unit>>? = null
        private var fnVariantNewString: CPointer<CFunction<(CPointer<ByteVar>?) -> COpaquePointer?>>? = null
        private var fnVariantNewObjectPath: CPointer<CFunction<(CPointer<ByteVar>?) -> COpaquePointer?>>? = null
        private var fnVariantNewBoolean: CPointer<CFunction<(Int) -> COpaquePointer?>>? = null
        private var fnVariantNewInt64: CPointer<CFunction<(Long) -> COpaquePointer?>>? = null
        private var fnVariantNewDouble: CPointer<CFunction<(Double) -> COpaquePointer?>>? = null
        private var fnVariantNewVariant: CPointer<CFunction<(COpaquePointer?) -> COpaquePointer?>>? = null
        private var fnVariantNewArray: CPointer<CFunction<(COpaquePointer?, CPointer<COpaquePointerVar>?, ULong) -> COpaquePointer?>>? = null
        private var fnVariantNewTuple: CPointer<CFunction<(CPointer<COpaquePointerVar>?, ULong) -> COpaquePointer?>>? = null
        private var fnVariantNewDictEntry: CPointer<CFunction<(COpaquePointer?, COpaquePointer?) -> COpaquePointer?>>? = null
        private var fnVariantGetChildValue: CPointer<CFunction<(COpaquePointer?, ULong) -> COpaquePointer?>>? = null
        private var fnVariantGetInt64: CPointer<CFunction<(COpaquePointer?) -> Long>>? = null
        private var fnVariantUnref: CPointer<CFunction<(COpaquePointer?) -> Unit>>? = null
        private var fnObjectUnref: CPointer<CFunction<(COpaquePointer?) -> Unit>>? = null
        private var fnBusGetSync: CPointer<CFunction<(Int, COpaquePointer?, CPointer<COpaquePointerVar>?) -> COpaquePointer?>>? = null
        private var fnBusOwnName: CPointer<CFunction<(Int, CPointer<ByteVar>?, UInt, COpaquePointer?, COpaquePointer?, COpaquePointer?, COpaquePointer?, COpaquePointer?) -> UInt>>? = null
        private var fnBusUnownName: CPointer<CFunction<(UInt) -> Unit>>? = null
        private var fnDbusRegisterObject: CPointer<CFunction<(COpaquePointer?, CPointer<ByteVar>?, COpaquePointer?, COpaquePointer?, COpaquePointer?, COpaquePointer?, CPointer<COpaquePointerVar>?) -> UInt>>? = null
        private var fnDbusUnregisterObject: CPointer<CFunction<(COpaquePointer?, UInt) -> Int>>? = null
        private var fnDbusEmitSignal: CPointer<CFunction<(COpaquePointer?, CPointer<ByteVar>?, CPointer<ByteVar>?, CPointer<ByteVar>?, CPointer<ByteVar>?, COpaquePointer?, CPointer<COpaquePointerVar>?) -> Int>>? = null
        private var fnDbusNodeInfoNewForXml: CPointer<CFunction<(CPointer<ByteVar>?, CPointer<COpaquePointerVar>?) -> COpaquePointer?>>? = null
        private var fnDbusNodeInfoLookupInterface: CPointer<CFunction<(COpaquePointer?, CPointer<ByteVar>?) -> COpaquePointer?>>? = null
        private var fnDbusNodeInfoUnref: CPointer<CFunction<(COpaquePointer?) -> Unit>>? = null
        private var fnDbusMethodReturnValue: CPointer<CFunction<(COpaquePointer?, COpaquePointer?) -> Unit>>? = null
        private var fnDbusMethodReturnError: CPointer<CFunction<(COpaquePointer?, CPointer<ByteVar>?, CPointer<ByteVar>?) -> Unit>>? = null

        private fun openFirst(vararg names: String): COpaquePointer? {
            for (name in names) {
                val h = dlopen(name, RTLD_NOW or RTLD_GLOBAL)
                if (h != null) return h
            }
            return null
        }

        fun ensureLoaded(): Boolean {
            if (initialized) return available
            initialized = true
            val glib = openFirst("libglib-2.0.so.0", "libglib-2.0.so") ?: return false
            val gobject = openFirst("libgobject-2.0.so.0", "libgobject-2.0.so") ?: return false
            val gio = openFirst("libgio-2.0.so.0", "libgio-2.0.so") ?: return false

            fnFree = dlsym(glib, "g_free")?.reinterpret()
            fnMalloc0 = dlsym(glib, "g_malloc0")?.reinterpret()
            fnErrorFree = dlsym(glib, "g_error_free")?.reinterpret()
            fnVariantTypeNew = dlsym(glib, "g_variant_type_new")?.reinterpret()
            fnVariantTypeFree = dlsym(glib, "g_variant_type_free")?.reinterpret()
            fnVariantNewString = dlsym(glib, "g_variant_new_string")?.reinterpret()
            fnVariantNewObjectPath = dlsym(glib, "g_variant_new_object_path")?.reinterpret()
            fnVariantNewBoolean = dlsym(glib, "g_variant_new_boolean")?.reinterpret()
            fnVariantNewInt64 = dlsym(glib, "g_variant_new_int64")?.reinterpret()
            fnVariantNewDouble = dlsym(glib, "g_variant_new_double")?.reinterpret()
            fnVariantNewVariant = dlsym(glib, "g_variant_new_variant")?.reinterpret()
            fnVariantNewArray = dlsym(glib, "g_variant_new_array")?.reinterpret()
            fnVariantNewTuple = dlsym(glib, "g_variant_new_tuple")?.reinterpret()
            fnVariantNewDictEntry = dlsym(glib, "g_variant_new_dict_entry")?.reinterpret()
            fnVariantGetChildValue = dlsym(glib, "g_variant_get_child_value")?.reinterpret()
            fnVariantGetInt64 = dlsym(glib, "g_variant_get_int64")?.reinterpret()
            fnVariantUnref = dlsym(glib, "g_variant_unref")?.reinterpret()
            fnObjectUnref = dlsym(gobject, "g_object_unref")?.reinterpret()
            fnBusGetSync = dlsym(gio, "g_bus_get_sync")?.reinterpret()
            fnBusOwnName = dlsym(gio, "g_bus_own_name")?.reinterpret()
            fnBusUnownName = dlsym(gio, "g_bus_unown_name")?.reinterpret()
            fnDbusRegisterObject = dlsym(gio, "g_dbus_connection_register_object")?.reinterpret()
            fnDbusUnregisterObject = dlsym(gio, "g_dbus_connection_unregister_object")?.reinterpret()
            fnDbusEmitSignal = dlsym(gio, "g_dbus_connection_emit_signal")?.reinterpret()
            fnDbusNodeInfoNewForXml = dlsym(gio, "g_dbus_node_info_new_for_xml")?.reinterpret()
            fnDbusNodeInfoLookupInterface = dlsym(gio, "g_dbus_node_info_lookup_interface")?.reinterpret()
            fnDbusNodeInfoUnref = dlsym(gio, "g_dbus_node_info_unref")?.reinterpret()
            fnDbusMethodReturnValue = dlsym(gio, "g_dbus_method_invocation_return_value")?.reinterpret()
            fnDbusMethodReturnError = dlsym(gio, "g_dbus_method_invocation_return_dbus_error")?.reinterpret()

            available = fnMalloc0 != null && fnBusGetSync != null && fnDbusRegisterObject != null
            return available
        }

        fun free(ptr: COpaquePointer?) { if (ptr != null) fnFree?.invoke(ptr) }
        fun malloc0(bytes: ULong): COpaquePointer? = fnMalloc0?.invoke(bytes)
        fun objectUnref(obj: COpaquePointer?) { if (obj != null) fnObjectUnref?.invoke(obj) }
        fun variantTypeNew(s: String): COpaquePointer? = memScoped { fnVariantTypeNew?.invoke(s.cstr.ptr) }
        fun variantTypeFree(t: COpaquePointer?) { if (t != null) fnVariantTypeFree?.invoke(t) }
        fun variantNewString(s: String): COpaquePointer? = memScoped { fnVariantNewString?.invoke(s.cstr.ptr) }
        fun variantNewObjectPath(p: String): COpaquePointer? = memScoped { fnVariantNewObjectPath?.invoke(p.cstr.ptr) }
        fun variantNewBoolean(b: Boolean): COpaquePointer? = fnVariantNewBoolean?.invoke(if (b) 1 else 0)
        fun variantNewInt64(v: Long): COpaquePointer? = fnVariantNewInt64?.invoke(v)
        fun variantNewDouble(v: Double): COpaquePointer? = fnVariantNewDouble?.invoke(v)
        fun variantNewVariant(c: COpaquePointer?): COpaquePointer? = c?.let { fnVariantNewVariant?.invoke(it) }
        fun variantNewDictEntry(k: COpaquePointer?, v: COpaquePointer?): COpaquePointer? =
            if (k != null && v != null) fnVariantNewDictEntry?.invoke(k, v) else null
        fun variantGetChildValue(c: COpaquePointer?, idx: Int): COpaquePointer? =
            if (c != null) fnVariantGetChildValue?.invoke(c, idx.convert()) else null
        fun variantGetInt64(v: COpaquePointer?): Long = if (v != null) fnVariantGetInt64?.invoke(v) ?: 0L else 0L
        fun variantUnref(v: COpaquePointer?) { if (v != null) fnVariantUnref?.invoke(v) }

        fun variantNewArray(childType: COpaquePointer?, items: List<COpaquePointer>): COpaquePointer? = memScoped {
            val fn = fnVariantNewArray ?: return@memScoped null
            if (items.isEmpty()) fn.invoke(childType, null, 0.convert())
            else {
                val arr = allocArray<COpaquePointerVar>(items.size)
                for (i in items.indices) arr[i] = items[i]
                fn.invoke(childType, arr, items.size.convert())
            }
        }

        fun variantNewTuple(items: List<COpaquePointer>): COpaquePointer? = memScoped {
            val fn = fnVariantNewTuple ?: return@memScoped null
            val arr = allocArray<COpaquePointerVar>(items.size)
            for (i in items.indices) arr[i] = items[i]
            fn.invoke(arr, items.size.convert())
        }

        private fun clearErr(errPtr: kotlinx.cinterop.CPointer<COpaquePointerVar>) {
            val e = errPtr.pointed.value
            if (e != null) {
                fnErrorFree?.invoke(e)
                errPtr.pointed.value = null
            }
        }

        fun busGetSessionSync(): COpaquePointer? = memScoped {
            val err = alloc<COpaquePointerVar>()
            err.value = null
            val b = fnBusGetSync?.invoke(G_BUS_TYPE_SESSION, null, err.ptr)
            clearErr(err.ptr)
            b
        }

        fun busOwnName(name: String): UInt = memScoped {
            fnBusOwnName?.invoke(G_BUS_TYPE_SESSION, name.cstr.ptr, 0u, null, null, null, null, null) ?: 0u
        }

        fun busUnownName(id: UInt) { if (id != 0u) fnBusUnownName?.invoke(id) }

        fun dbusNodeInfoNewForXml(xml: String): COpaquePointer? = memScoped {
            val err = alloc<COpaquePointerVar>()
            err.value = null
            val info = fnDbusNodeInfoNewForXml?.invoke(xml.cstr.ptr, err.ptr)
            clearErr(err.ptr)
            info
        }

        fun dbusNodeInfoLookupInterface(info: COpaquePointer, name: String): COpaquePointer? =
            memScoped { fnDbusNodeInfoLookupInterface?.invoke(info, name.cstr.ptr) }

        fun dbusNodeInfoUnref(info: COpaquePointer?) { if (info != null) fnDbusNodeInfoUnref?.invoke(info) }

        fun dbusRegisterObject(
            conn: COpaquePointer,
            path: String,
            iface: COpaquePointer,
            vtable: COpaquePointer,
            userData: COpaquePointer?
        ): UInt = memScoped {
            val err = alloc<COpaquePointerVar>()
            err.value = null
            val id = fnDbusRegisterObject?.invoke(conn, path.cstr.ptr, iface, vtable, userData, null, err.ptr) ?: 0u
            clearErr(err.ptr)
            id
        }

        fun dbusUnregisterObject(conn: COpaquePointer, id: UInt) {
            if (id != 0u) fnDbusUnregisterObject?.invoke(conn, id)
        }

        fun dbusEmitSignal(
            conn: COpaquePointer,
            path: String,
            iface: String,
            sig: String,
            params: COpaquePointer?
        ) = memScoped {
            val err = alloc<COpaquePointerVar>()
            err.value = null
            fnDbusEmitSignal?.invoke(conn, null, path.cstr.ptr, iface.cstr.ptr, sig.cstr.ptr, params, err.ptr)
            clearErr(err.ptr)
        }

        fun returnValue(inv: COpaquePointer, value: COpaquePointer?) {
            fnDbusMethodReturnValue?.invoke(inv, value)
        }

        fun returnError(inv: COpaquePointer, name: String, msg: String) = memScoped {
            fnDbusMethodReturnError?.invoke(inv, name.cstr.ptr, msg.cstr.ptr)
        }
    }

    private companion object {
        const val MPRIS_PATH = "/org/mpris/MediaPlayer2"
        const val MPRIS_ROOT = "org.mpris.MediaPlayer2"
        const val MPRIS_PLAYER = "org.mpris.MediaPlayer2.Player"
        const val DBUS_PROPS = "org.freedesktop.DBus.Properties"
        var busCounter: Int = 0

        const val INTROSPECTION_XML = """
<node>
  <interface name="org.mpris.MediaPlayer2">
    <method name="Raise"/>
    <method name="Quit"/>
    <property name="CanQuit" type="b" access="read"/>
    <property name="CanRaise" type="b" access="read"/>
    <property name="HasTrackList" type="b" access="read"/>
    <property name="Identity" type="s" access="read"/>
  </interface>
  <interface name="org.mpris.MediaPlayer2.Player">
    <method name="Next"/>
    <method name="Previous"/>
    <method name="Pause"/>
    <method name="PlayPause"/>
    <method name="Stop"/>
    <method name="Play"/>
    <method name="Seek"><arg direction="in" name="Offset" type="x"/></method>
    <method name="SetPosition">
      <arg direction="in" name="TrackId" type="o"/>
      <arg direction="in" name="Position" type="x"/>
    </method>
    <property name="PlaybackStatus" type="s" access="read"/>
    <property name="Rate" type="d" access="read"/>
    <property name="Metadata" type="a{sv}" access="read"/>
    <property name="Position" type="x" access="read"/>
    <property name="CanGoNext" type="b" access="read"/>
    <property name="CanGoPrevious" type="b" access="read"/>
    <property name="CanPlay" type="b" access="read"/>
    <property name="CanPause" type="b" access="read"/>
    <property name="CanSeek" type="b" access="read"/>
    <property name="CanControl" type="b" access="read"/>
  </interface>
</node>
"""
    }

    private var metadata: MediaSessionMetadata? = null
    private var playing: Boolean = false
    private var positionSec: Double = 0.0
    private var durationSec: Double = 0.0
    private var onPlay: (() -> Unit)? = null
    private var onPause: (() -> Unit)? = null
    private var onNext: (() -> Unit)? = null
    private var onPrevious: (() -> Unit)? = null
    private var onSeekTo: ((Double) -> Unit)? = null

    private var dbusConn: COpaquePointer? = null
    private var nodeInfo: COpaquePointer? = null
    private var rootRegId: UInt = 0u
    private var playerRegId: UInt = 0u
    private var ownerId: UInt = 0u
    private var vtablePtr: COpaquePointer? = null
    private var selfRef: StableRef<DesktopMediaSessionPlatformBridge>? = null
    private var disposed: Boolean = false

    actual fun updateMetadata(metadata: MediaSessionMetadata) {
        if (disposed) return
        this.metadata = metadata
        ensureStarted()
        emitPropertiesChanged()
    }

    actual fun updatePlaybackState(isPlaying: Boolean, positionSeconds: Double, durationSeconds: Double) {
        if (disposed) return
        this.playing = isPlaying
        this.positionSec = positionSeconds.coerceAtLeast(0.0)
        this.durationSec = durationSeconds.coerceAtLeast(0.0)
        ensureStarted()
        emitPropertiesChanged()
    }

    actual fun setTransportCallbacks(
        onPlay: (() -> Unit)?,
        onPause: (() -> Unit)?,
        onNext: (() -> Unit)?,
        onPrevious: (() -> Unit)?,
        onSeekTo: ((Double) -> Unit)?
    ) {
        if (disposed) return
        this.onPlay = onPlay
        this.onPause = onPause
        this.onNext = onNext
        this.onPrevious = onPrevious
        this.onSeekTo = onSeekTo
        ensureStarted()
        emitPropertiesChanged()
    }

    private fun ensureStarted(): Boolean {
        if (disposed || playerRegId != 0u) return !disposed && playerRegId != 0u
        if (!GioSessionBindings.ensureLoaded()) return false
        val conn = GioSessionBindings.busGetSessionSync() ?: return false
        val info = GioSessionBindings.dbusNodeInfoNewForXml(INTROSPECTION_XML) ?: return false
        val rootIface = GioSessionBindings.dbusNodeInfoLookupInterface(info, MPRIS_ROOT)
        val playerIface = GioSessionBindings.dbusNodeInfoLookupInterface(info, MPRIS_PLAYER)
        if (rootIface == null || playerIface == null) {
            GioSessionBindings.dbusNodeInfoUnref(info)
            return false
        }
        val vt = GioSessionBindings.malloc0(88uL) ?: run {
            GioSessionBindings.dbusNodeInfoUnref(info)
            return false
        }
        val slots = vt.reinterpret<COpaquePointerVar>()
        slots[0] = staticCFunction {
                _: COpaquePointer?,
                _: CPointer<ByteVar>?,
                _: CPointer<ByteVar>?,
                iface: CPointer<ByteVar>?,
                method: CPointer<ByteVar>?,
                params: COpaquePointer?,
                inv: COpaquePointer?,
                userData: COpaquePointer? ->
            val bridge = userData?.asStableRef<DesktopMediaSessionPlatformBridge>()?.get()
            if (bridge != null && inv != null) {
                bridge.handleMethodCall(
                    iface?.toKString().orEmpty(),
                    method?.toKString().orEmpty(),
                    params,
                    inv
                )
            }
        }
        slots[1] = staticCFunction {
                _: COpaquePointer?,
                _: CPointer<ByteVar>?,
                _: CPointer<ByteVar>?,
                iface: CPointer<ByteVar>?,
                prop: CPointer<ByteVar>?,
                _: CPointer<COpaquePointerVar>?,
                userData: COpaquePointer? ->
            val bridge = userData?.asStableRef<DesktopMediaSessionPlatformBridge>()?.get()
            bridge?.handleGetProperty(iface?.toKString().orEmpty(), prop?.toKString().orEmpty())
        }
        slots[2] = null
        val ref = StableRef.create(this)
        val rId = GioSessionBindings.dbusRegisterObject(conn, MPRIS_PATH, rootIface, vt, ref.asCPointer())
        val pId = GioSessionBindings.dbusRegisterObject(conn, MPRIS_PATH, playerIface, vt, ref.asCPointer())
        if (rId == 0u || pId == 0u) {
            if (rId != 0u) GioSessionBindings.dbusUnregisterObject(conn, rId)
            if (pId != 0u) GioSessionBindings.dbusUnregisterObject(conn, pId)
            ref.dispose()
            GioSessionBindings.free(vt)
            GioSessionBindings.dbusNodeInfoUnref(info)
            return false
        }
        val busName = "org.mpris.MediaPlayer2.genericui.session${getpid()}_${++busCounter}"
        dbusConn = conn
        nodeInfo = info
        rootRegId = rId
        playerRegId = pId
        ownerId = GioSessionBindings.busOwnName(busName)
        vtablePtr = vt
        selfRef = ref
        return true
    }

    private fun handleMethodCall(iface: String, method: String, params: COpaquePointer?, inv: COpaquePointer) {
        if (iface == MPRIS_ROOT) {
            when (method) {
                "Raise", "Quit" -> GioSessionBindings.returnValue(inv, null)
                else -> GioSessionBindings.returnError(inv, "org.freedesktop.DBus.Error.UnknownMethod", method)
            }
            return
        }
        when (method) {
            "Play" -> {
                GioSessionBindings.returnValue(inv, null)
                onPlay?.let { cb -> GenericUiThreading.runOnLogic { cb() } }
            }
            "Pause", "Stop" -> {
                GioSessionBindings.returnValue(inv, null)
                onPause?.let { cb -> GenericUiThreading.runOnLogic { cb() } }
            }
            "PlayPause" -> {
                GioSessionBindings.returnValue(inv, null)
                val cb = if (playing) onPause else onPlay
                if (cb != null) GenericUiThreading.runOnLogic { cb() }
            }
            "Next" -> {
                GioSessionBindings.returnValue(inv, null)
                onNext?.let { cb -> GenericUiThreading.runOnLogic { cb() } }
            }
            "Previous" -> {
                GioSessionBindings.returnValue(inv, null)
                onPrevious?.let { cb -> GenericUiThreading.runOnLogic { cb() } }
            }
            "Seek" -> {
                GioSessionBindings.returnValue(inv, null)
                if (params != null) {
                    val child = GioSessionBindings.variantGetChildValue(params, 0)
                    val deltaSec = (if (child != null) GioSessionBindings.variantGetInt64(child) else 0L) / 1_000_000.0
                    GioSessionBindings.variantUnref(child)
                    val target = (positionSec + deltaSec).coerceAtLeast(0.0)
                    onSeekTo?.let { cb -> GenericUiThreading.runOnLogic { cb(target) } }
                }
            }
            "SetPosition" -> {
                GioSessionBindings.returnValue(inv, null)
                if (params != null) {
                    val child = GioSessionBindings.variantGetChildValue(params, 1)
                    val target = ((if (child != null) GioSessionBindings.variantGetInt64(child) else 0L) / 1_000_000.0).coerceAtLeast(0.0)
                    GioSessionBindings.variantUnref(child)
                    onSeekTo?.let { cb -> GenericUiThreading.runOnLogic { cb(target) } }
                }
            }
            else -> GioSessionBindings.returnError(inv, "org.freedesktop.DBus.Error.UnknownMethod", method)
        }
    }

    private fun handleGetProperty(iface: String, prop: String): COpaquePointer? {
        if (iface == MPRIS_ROOT) {
            return when (prop) {
                "CanQuit", "CanRaise" -> GioSessionBindings.variantNewBoolean(true)
                "HasTrackList" -> GioSessionBindings.variantNewBoolean(false)
                "Identity" -> GioSessionBindings.variantNewString("GenericUI ($backendToolkit)")
                else -> null
            }
        }
        return when (prop) {
            "PlaybackStatus" -> GioSessionBindings.variantNewString(if (playing) "Playing" else if (metadata != null) "Paused" else "Stopped")
            "Rate" -> GioSessionBindings.variantNewDouble(if (playing) 1.0 else 0.0)
            "Position" -> GioSessionBindings.variantNewInt64((positionSec * 1_000_000.0).roundToLong().coerceAtLeast(0L))
            "Metadata" -> buildMetadataDict()
            "CanPlay" -> GioSessionBindings.variantNewBoolean(onPlay != null)
            "CanPause" -> GioSessionBindings.variantNewBoolean(onPause != null)
            "CanGoNext" -> GioSessionBindings.variantNewBoolean(onNext != null)
            "CanGoPrevious" -> GioSessionBindings.variantNewBoolean(onPrevious != null)
            "CanSeek" -> GioSessionBindings.variantNewBoolean(onSeekTo != null)
            "CanControl" -> GioSessionBindings.variantNewBoolean(true)
            else -> null
        }
    }

    private fun buildMetadataDict(): COpaquePointer? {
        val dictType = GioSessionBindings.variantTypeNew("{sv}") ?: return null
        val strType = GioSessionBindings.variantTypeNew("s") ?: run {
            GioSessionBindings.variantTypeFree(dictType)
            return null
        }
        try {
            val entries = ArrayList<COpaquePointer>(5)
            fun add(k: String, v: COpaquePointer?) {
                val kv = GioSessionBindings.variantNewString(k) ?: return
                val vv = GioSessionBindings.variantNewVariant(v) ?: return
                GioSessionBindings.variantNewDictEntry(kv, vv)?.let { entries.add(it) }
            }
            add("mpris:trackid", GioSessionBindings.variantNewObjectPath("/org/mpris/MediaPlayer2/Track/1"))
            val durUs = (durationSec * 1_000_000.0).roundToLong().coerceAtLeast(0L)
            if (durUs > 0L) add("mpris:length", GioSessionBindings.variantNewInt64(durUs))
            metadata?.let { meta ->
                if (meta.title.isNotEmpty()) add("xesam:title", GioSessionBindings.variantNewString(meta.title))
                if (meta.artist.isNotEmpty()) {
                    GioSessionBindings.variantNewString(meta.artist)?.let { a ->
                        add("xesam:artist", GioSessionBindings.variantNewArray(strType, listOf(a)))
                    }
                }
                if (meta.album.isNotEmpty()) add("xesam:album", GioSessionBindings.variantNewString(meta.album))
                meta.artworkUrl?.takeIf { it.isNotBlank() }?.let { add("mpris:artUrl", GioSessionBindings.variantNewString(it)) }
            }
            return GioSessionBindings.variantNewArray(dictType, entries)
        } finally {
            GioSessionBindings.variantTypeFree(strType)
            GioSessionBindings.variantTypeFree(dictType)
        }
    }

    private fun emitPropertiesChanged() {
        val conn = dbusConn ?: return
        if (disposed) return
        val dictType = GioSessionBindings.variantTypeNew("{sv}") ?: return
        val strType = GioSessionBindings.variantTypeNew("s") ?: run {
            GioSessionBindings.variantTypeFree(dictType)
            return
        }
        try {
            val entries = ArrayList<COpaquePointer>(8)
            fun add(k: String, v: COpaquePointer?) {
                val kv = GioSessionBindings.variantNewString(k) ?: return
                val vv = GioSessionBindings.variantNewVariant(v) ?: return
                GioSessionBindings.variantNewDictEntry(kv, vv)?.let { entries.add(it) }
            }
            add("PlaybackStatus", GioSessionBindings.variantNewString(if (playing) "Playing" else if (metadata != null) "Paused" else "Stopped"))
            add("Rate", GioSessionBindings.variantNewDouble(if (playing) 1.0 else 0.0))
            buildMetadataDict()?.let { add("Metadata", it) }
            add("CanPlay", GioSessionBindings.variantNewBoolean(onPlay != null))
            add("CanPause", GioSessionBindings.variantNewBoolean(onPause != null))
            add("CanGoNext", GioSessionBindings.variantNewBoolean(onNext != null))
            add("CanGoPrevious", GioSessionBindings.variantNewBoolean(onPrevious != null))
            add("CanSeek", GioSessionBindings.variantNewBoolean(onSeekTo != null))
            val changed = GioSessionBindings.variantNewArray(dictType, entries) ?: return
            val ifaceVar = GioSessionBindings.variantNewString(MPRIS_PLAYER) ?: return
            val invalidated = GioSessionBindings.variantNewArray(strType, emptyList()) ?: return
            val tuple = GioSessionBindings.variantNewTuple(listOf(ifaceVar, changed, invalidated)) ?: return
            GioSessionBindings.dbusEmitSignal(conn, MPRIS_PATH, DBUS_PROPS, "PropertiesChanged", tuple)
        } finally {
            GioSessionBindings.variantTypeFree(strType)
            GioSessionBindings.variantTypeFree(dictType)
        }
    }

    actual fun dispose() {
        if (disposed) return
        disposed = true
        val conn = dbusConn
        val rId = rootRegId
        val pId = playerRegId
        val oId = ownerId
        val info = nodeInfo
        val vt = vtablePtr
        val ref = selfRef
        dbusConn = null
        rootRegId = 0u
        playerRegId = 0u
        ownerId = 0u
        nodeInfo = null
        vtablePtr = null
        selfRef = null
        if (conn != null) {
            if (rId != 0u) GioSessionBindings.dbusUnregisterObject(conn, rId)
            if (pId != 0u) GioSessionBindings.dbusUnregisterObject(conn, pId)
            GioSessionBindings.objectUnref(conn)
        }
        if (oId != 0u) GioSessionBindings.busUnownName(oId)
        if (info != null) GioSessionBindings.dbusNodeInfoUnref(info)
        if (vt != null) GioSessionBindings.free(vt)
        ref?.dispose()
        onPlay = null
        onPause = null
        onNext = null
        onPrevious = null
        onSeekTo = null
        metadata = null
    }
}
