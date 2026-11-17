@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.mediacontrols.linux

import com.nexustools.genericui.ext.mediacontrols.MediaControlAction
import com.nexustools.genericui.ext.mediacontrols.MediaControlButtonConfig
import com.nexustools.genericui.ext.mediacontrols.MediaControlMetadata
import com.nexustools.genericui.ext.mediacontrols.MediaPlaybackStatus
import com.nexustools.genericui.threading.GenericUiThreading
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
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
 * Self-contained Linux MPRIS2 (`org.mpris.MediaPlayer2` + `org.mpris.MediaPlayer2.Player`)
 * D-Bus service over `libglib-2.0.so.0` / `libgobject-2.0.so.0` / `libgio-2.0.so.0` shared by
 * [com.nexustools.genericui.ext.mediacontrols.gtk.GtkMediaControlsImpl] and
 * [com.nexustools.genericui.ext.mediacontrols.qt.QtMediaControlsImpl].
 */
internal class LinuxMprisDbusBridge(
    private val identity: String,
    private val isActiveProvider: () -> Boolean,
    private val metadataProvider: () -> MediaControlMetadata,
    private val statusProvider: () -> MediaPlaybackStatus,
    private val positionMsProvider: () -> Long,
    private val durationMsProvider: () -> Long,
    private val rateProvider: () -> Float,
    private val buttonConfigProvider: () -> MediaControlButtonConfig,
    private val onTransportAction: (action: MediaControlAction, seekPositionMs: Long?) -> Boolean
) {
    private var dbusConnection: COpaquePointer? = null
    private var nodeInfo: COpaquePointer? = null
    private var rootRegId: UInt = 0u
    private var playerRegId: UInt = 0u
    private var busOwnerId: UInt = 0u
    private var vtablePtr: COpaquePointer? = null
    private var selfRef: StableRef<LinuxMprisDbusBridge>? = null
    private var registeredBusName: String? = null
    private var disposed: Boolean = false

    val isConnectedToDbus: Boolean
        get() = dbusConnection != null && playerRegId != 0u

    val busName: String?
        get() = registeredBusName

    fun ensureStarted(): Boolean {
        if (disposed) return false
        if (playerRegId != 0u) return true
        if (!MprisGioBindings.ensureLoaded()) return false

        val conn = MprisGioBindings.busGetSessionSync() ?: return false
        val info = MprisGioBindings.dbusNodeInfoNewForXml(MPRIS_INTROSPECTION_XML) ?: return false
        val rootIface = MprisGioBindings.dbusNodeInfoLookupInterface(info, MPRIS_ROOT_INTERFACE)
        val playerIface = MprisGioBindings.dbusNodeInfoLookupInterface(info, MPRIS_PLAYER_INTERFACE)
        if (rootIface == null || playerIface == null) {
            MprisGioBindings.dbusNodeInfoUnref(info)
            return false
        }

        val vtable = MprisGioBindings.malloc0(88uL) ?: run {
            MprisGioBindings.dbusNodeInfoUnref(info)
            return false
        }
        val fnSlots = vtable.reinterpret<COpaquePointerVar>()
        val methodCb = staticCFunction {
                _: COpaquePointer?,
                _: CPointer<ByteVar>?,
                _: CPointer<ByteVar>?,
                interfaceName: CPointer<ByteVar>?,
                methodName: CPointer<ByteVar>?,
                parameters: COpaquePointer?,
                invocation: COpaquePointer?,
                userData: COpaquePointer? ->
            val bridge = userData?.asStableRef<LinuxMprisDbusBridge>()?.get()
            if (bridge != null && invocation != null) {
                bridge.handleMethodCall(
                    interfaceName = interfaceName?.toKString().orEmpty(),
                    methodName = methodName?.toKString().orEmpty(),
                    parameters = parameters,
                    invocation = invocation
                )
            }
        }
        val getPropCb = staticCFunction {
                _: COpaquePointer?,
                _: CPointer<ByteVar>?,
                _: CPointer<ByteVar>?,
                interfaceName: CPointer<ByteVar>?,
                propertyName: CPointer<ByteVar>?,
                _: CPointer<COpaquePointerVar>?,
                userData: COpaquePointer? ->
            val bridge = userData?.asStableRef<LinuxMprisDbusBridge>()?.get()
            bridge?.handleGetProperty(
                interfaceName = interfaceName?.toKString().orEmpty(),
                propertyName = propertyName?.toKString().orEmpty()
            )
        }
        fnSlots[0] = methodCb
        fnSlots[1] = getPropCb
        fnSlots[2] = null

        val ref = StableRef.create(this)
        val rId = MprisGioBindings.dbusRegisterObject(
            connection = conn,
            objectPath = MPRIS_OBJECT_PATH,
            interfaceInfo = rootIface,
            vtable = vtable,
            userData = ref.asCPointer()
        )
        val pId = MprisGioBindings.dbusRegisterObject(
            connection = conn,
            objectPath = MPRIS_OBJECT_PATH,
            interfaceInfo = playerIface,
            vtable = vtable,
            userData = ref.asCPointer()
        )
        if (rId == 0u || pId == 0u) {
            if (rId != 0u) MprisGioBindings.dbusUnregisterObject(conn, rId)
            if (pId != 0u) MprisGioBindings.dbusUnregisterObject(conn, pId)
            ref.dispose()
            MprisGioBindings.free(vtable)
            MprisGioBindings.dbusNodeInfoUnref(info)
            return false
        }

        val serviceName = "org.mpris.MediaPlayer2.genericui.instance${getpid()}_${++busCounter}"
        val ownerId = MprisGioBindings.busOwnName(serviceName)

        dbusConnection = conn
        nodeInfo = info
        rootRegId = rId
        playerRegId = pId
        busOwnerId = ownerId
        vtablePtr = vtable
        selfRef = ref
        registeredBusName = serviceName
        return true
    }

    fun emitPropertiesChanged() {
        val conn = dbusConnection ?: return
        if (disposed || !isActiveProvider()) return
        val changed = buildPlayerPropertiesDict(
            includePlayback = true,
            includeMetadata = true,
            includeButtons = true
        ) ?: return
        val strType = MprisGioBindings.variantTypeNew("s") ?: run {
            MprisGioBindings.variantUnref(changed)
            return
        }
        try {
            val ifaceVar = MprisGioBindings.variantNewString(MPRIS_PLAYER_INTERFACE) ?: return
            val invalidated = MprisGioBindings.variantNewArray(strType, emptyList()) ?: return
            val tuple = MprisGioBindings.variantNewTuple(listOf(ifaceVar, changed, invalidated)) ?: return
            MprisGioBindings.dbusEmitSignal(
                connection = conn,
                objectPath = MPRIS_OBJECT_PATH,
                interfaceName = DBUS_PROPERTIES_INTERFACE,
                signalName = "PropertiesChanged",
                parameters = tuple
            )
        } finally {
            MprisGioBindings.variantTypeFree(strType)
        }
    }

    fun emitSeeked(positionMs: Long) {
        val conn = dbusConnection ?: return
        if (disposed || !isActiveProvider()) return
        val us = positionMs.coerceAtLeast(0L) * 1000L
        val posVar = MprisGioBindings.variantNewInt64(us) ?: return
        val tuple = MprisGioBindings.variantNewTuple(listOf(posVar)) ?: return
        MprisGioBindings.dbusEmitSignal(
            connection = conn,
            objectPath = MPRIS_OBJECT_PATH,
            interfaceName = MPRIS_PLAYER_INTERFACE,
            signalName = "Seeked",
            parameters = tuple
        )
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        val conn = dbusConnection
        val rId = rootRegId
        val pId = playerRegId
        val oId = busOwnerId
        val info = nodeInfo
        val vt = vtablePtr
        val ref = selfRef
        dbusConnection = null
        rootRegId = 0u
        playerRegId = 0u
        busOwnerId = 0u
        nodeInfo = null
        vtablePtr = null
        selfRef = null
        registeredBusName = null

        if (conn != null) {
            if (rId != 0u) MprisGioBindings.dbusUnregisterObject(conn, rId)
            if (pId != 0u) MprisGioBindings.dbusUnregisterObject(conn, pId)
            MprisGioBindings.objectUnref(conn)
        }
        if (oId != 0u) {
            MprisGioBindings.busUnownName(oId)
        }
        if (info != null) {
            MprisGioBindings.dbusNodeInfoUnref(info)
        }
        if (vt != null) {
            MprisGioBindings.free(vt)
        }
        ref?.dispose()
    }

    private fun handleMethodCall(
        interfaceName: String,
        methodName: String,
        parameters: COpaquePointer?,
        invocation: COpaquePointer
    ) {
        if (interfaceName == MPRIS_ROOT_INTERFACE) {
            when (methodName) {
                "Raise", "Quit" -> MprisGioBindings.dbusMethodReturnValue(invocation, null)
                else -> MprisGioBindings.dbusMethodReturnError(
                    invocation,
                    "org.freedesktop.DBus.Error.UnknownMethod",
                    "Unknown MPRIS root method: $methodName"
                )
            }
            return
        }

        val cfg = buttonConfigProvider()
        when (methodName) {
            "Play" -> {
                MprisGioBindings.dbusMethodReturnValue(invocation, null)
                if (cfg.playPauseEnabled) {
                    GenericUiThreading.runOnLogic { onTransportAction(MediaControlAction.PLAY, null) }
                }
            }
            "Pause" -> {
                MprisGioBindings.dbusMethodReturnValue(invocation, null)
                if (cfg.playPauseEnabled) {
                    GenericUiThreading.runOnLogic { onTransportAction(MediaControlAction.PAUSE, null) }
                }
            }
            "PlayPause" -> {
                MprisGioBindings.dbusMethodReturnValue(invocation, null)
                if (cfg.playPauseEnabled) {
                    GenericUiThreading.runOnLogic { onTransportAction(MediaControlAction.PLAY_PAUSE, null) }
                }
            }
            "Stop" -> {
                MprisGioBindings.dbusMethodReturnValue(invocation, null)
                if (cfg.stopEnabled) {
                    GenericUiThreading.runOnLogic { onTransportAction(MediaControlAction.STOP, null) }
                }
            }
            "Next" -> {
                MprisGioBindings.dbusMethodReturnValue(invocation, null)
                if (cfg.nextEnabled) {
                    GenericUiThreading.runOnLogic { onTransportAction(MediaControlAction.NEXT, null) }
                }
            }
            "Previous" -> {
                MprisGioBindings.dbusMethodReturnValue(invocation, null)
                if (cfg.previousEnabled) {
                    GenericUiThreading.runOnLogic { onTransportAction(MediaControlAction.PREVIOUS, null) }
                }
            }
            "Seek" -> {
                MprisGioBindings.dbusMethodReturnValue(invocation, null)
                if (cfg.seekEnabled && parameters != null) {
                    val deltaChild = MprisGioBindings.variantGetChildValue(parameters, 0)
                    val deltaUs = if (deltaChild != null) MprisGioBindings.variantGetInt64(deltaChild) else 0L
                    MprisGioBindings.variantUnref(deltaChild)
                    val targetMs = (positionMsProvider() + (deltaUs / 1000L)).coerceAtLeast(0L)
                    GenericUiThreading.runOnLogic { onTransportAction(MediaControlAction.SEEK_TO, targetMs) }
                }
            }
            "SetPosition" -> {
                MprisGioBindings.dbusMethodReturnValue(invocation, null)
                if (cfg.seekEnabled && parameters != null) {
                    val posChild = MprisGioBindings.variantGetChildValue(parameters, 1)
                    val posUs = if (posChild != null) MprisGioBindings.variantGetInt64(posChild) else 0L
                    MprisGioBindings.variantUnref(posChild)
                    val targetMs = (posUs / 1000L).coerceAtLeast(0L)
                    GenericUiThreading.runOnLogic { onTransportAction(MediaControlAction.SEEK_TO, targetMs) }
                }
            }
            else -> MprisGioBindings.dbusMethodReturnError(
                invocation,
                "org.freedesktop.DBus.Error.UnknownMethod",
                "Unknown MPRIS player method: $methodName"
            )
        }
    }

    private fun handleGetProperty(interfaceName: String, propertyName: String): COpaquePointer? {
        if (interfaceName == MPRIS_ROOT_INTERFACE) {
            return when (propertyName) {
                "CanQuit", "CanRaise" -> MprisGioBindings.variantNewBoolean(true)
                "HasTrackList", "CanSetFullscreen", "Fullscreen" -> MprisGioBindings.variantNewBoolean(false)
                "Identity" -> MprisGioBindings.variantNewString(identity)
                "SupportedUriSchemes", "SupportedMimeTypes" -> {
                    val strType = MprisGioBindings.variantTypeNew("s") ?: return null
                    try {
                        MprisGioBindings.variantNewArray(strType, emptyList())
                    } finally {
                        MprisGioBindings.variantTypeFree(strType)
                    }
                }
                else -> null
            }
        }

        val cfg = buttonConfigProvider()
        val rate = if (statusProvider() == MediaPlaybackStatus.PLAYING) rateProvider().toDouble() else 0.0
        return when (propertyName) {
            "PlaybackStatus" -> MprisGioBindings.variantNewString(mprisStatusString())
            "LoopStatus" -> MprisGioBindings.variantNewString("None")
            "Rate" -> MprisGioBindings.variantNewDouble(rate.coerceAtLeast(0.0))
            "MinimumRate" -> MprisGioBindings.variantNewDouble(0.25)
            "MaximumRate" -> MprisGioBindings.variantNewDouble(4.0)
            "Shuffle" -> MprisGioBindings.variantNewBoolean(false)
            "Volume" -> MprisGioBindings.variantNewDouble(1.0)
            "Position" -> MprisGioBindings.variantNewInt64(positionMsProvider().coerceAtLeast(0L) * 1000L)
            "Metadata" -> buildMetadataDict()
            "CanGoNext" -> MprisGioBindings.variantNewBoolean(cfg.nextEnabled)
            "CanGoPrevious" -> MprisGioBindings.variantNewBoolean(cfg.previousEnabled)
            "CanPlay" -> MprisGioBindings.variantNewBoolean(cfg.playPauseEnabled)
            "CanPause" -> MprisGioBindings.variantNewBoolean(cfg.playPauseEnabled)
            "CanSeek" -> MprisGioBindings.variantNewBoolean(cfg.seekEnabled)
            "CanControl" -> MprisGioBindings.variantNewBoolean(isActiveProvider())
            else -> null
        }
    }

    private fun mprisStatusString(): String = when (statusProvider()) {
        MediaPlaybackStatus.PLAYING -> "Playing"
        MediaPlaybackStatus.PAUSED -> "Paused"
        MediaPlaybackStatus.STOPPED, MediaPlaybackStatus.LOADING -> "Stopped"
    }

    private fun buildMetadataDict(): COpaquePointer? {
        val dictEntryType = MprisGioBindings.variantTypeNew("{sv}") ?: return null
        val strType = MprisGioBindings.variantTypeNew("s") ?: run {
            MprisGioBindings.variantTypeFree(dictEntryType)
            return null
        }
        try {
            val meta = metadataProvider()
            val entries = ArrayList<COpaquePointer>(6)
            fun addEntry(key: String, valueVariant: COpaquePointer?) {
                val k = MprisGioBindings.variantNewString(key) ?: return
                val v = MprisGioBindings.variantNewVariant(valueVariant) ?: return
                MprisGioBindings.variantNewDictEntry(k, v)?.let { entries.add(it) }
            }
            addEntry("mpris:trackid", MprisGioBindings.variantNewObjectPath("/org/mpris/MediaPlayer2/Track/1"))
            val durUs = durationMsProvider().coerceAtLeast(0L) * 1000L
            if (durUs > 0L) {
                addEntry("mpris:length", MprisGioBindings.variantNewInt64(durUs))
            }
            if (meta.title.isNotEmpty()) {
                addEntry("xesam:title", MprisGioBindings.variantNewString(meta.title))
            }
            if (meta.artist.isNotEmpty()) {
                val artistItem = MprisGioBindings.variantNewString(meta.artist)
                if (artistItem != null) {
                    addEntry("xesam:artist", MprisGioBindings.variantNewArray(strType, listOf(artistItem)))
                }
            }
            if (meta.album.isNotEmpty()) {
                addEntry("xesam:album", MprisGioBindings.variantNewString(meta.album))
            }
            meta.artworkUrl?.takeIf { it.isNotBlank() }?.let { url ->
                addEntry("mpris:artUrl", MprisGioBindings.variantNewString(url))
            }
            return MprisGioBindings.variantNewArray(dictEntryType, entries)
        } finally {
            MprisGioBindings.variantTypeFree(strType)
            MprisGioBindings.variantTypeFree(dictEntryType)
        }
    }

    private fun buildPlayerPropertiesDict(
        includePlayback: Boolean,
        includeMetadata: Boolean,
        includeButtons: Boolean
    ): COpaquePointer? {
        val dictEntryType = MprisGioBindings.variantTypeNew("{sv}") ?: return null
        try {
            val entries = ArrayList<COpaquePointer>(10)
            fun addEntry(key: String, valueVariant: COpaquePointer?) {
                val k = MprisGioBindings.variantNewString(key) ?: return
                val v = MprisGioBindings.variantNewVariant(valueVariant) ?: return
                MprisGioBindings.variantNewDictEntry(k, v)?.let { entries.add(it) }
            }
            if (includePlayback) {
                addEntry("PlaybackStatus", MprisGioBindings.variantNewString(mprisStatusString()))
                val rate = if (statusProvider() == MediaPlaybackStatus.PLAYING) rateProvider().toDouble() else 0.0
                addEntry("Rate", MprisGioBindings.variantNewDouble(rate.coerceAtLeast(0.0)))
            }
            if (includeMetadata) {
                buildMetadataDict()?.let { addEntry("Metadata", it) }
            }
            if (includeButtons) {
                val cfg = buttonConfigProvider()
                addEntry("CanPlay", MprisGioBindings.variantNewBoolean(cfg.playPauseEnabled))
                addEntry("CanPause", MprisGioBindings.variantNewBoolean(cfg.playPauseEnabled))
                addEntry("CanGoNext", MprisGioBindings.variantNewBoolean(cfg.nextEnabled))
                addEntry("CanGoPrevious", MprisGioBindings.variantNewBoolean(cfg.previousEnabled))
                addEntry("CanSeek", MprisGioBindings.variantNewBoolean(cfg.seekEnabled))
                addEntry("CanControl", MprisGioBindings.variantNewBoolean(isActiveProvider()))
            }
            return MprisGioBindings.variantNewArray(dictEntryType, entries)
        } finally {
            MprisGioBindings.variantTypeFree(dictEntryType)
        }
    }

    companion object {
        private const val MPRIS_OBJECT_PATH = "/org/mpris/MediaPlayer2"
        private const val MPRIS_ROOT_INTERFACE = "org.mpris.MediaPlayer2"
        private const val MPRIS_PLAYER_INTERFACE = "org.mpris.MediaPlayer2.Player"
        private const val DBUS_PROPERTIES_INTERFACE = "org.freedesktop.DBus.Properties"
        private var busCounter: Int = 0

        private const val MPRIS_INTROSPECTION_XML = """
<node>
  <interface name="org.mpris.MediaPlayer2">
    <method name="Raise"/>
    <method name="Quit"/>
    <property name="CanQuit" type="b" access="read"/>
    <property name="CanRaise" type="b" access="read"/>
    <property name="HasTrackList" type="b" access="read"/>
    <property name="Identity" type="s" access="read"/>
    <property name="SupportedUriSchemes" type="as" access="read"/>
    <property name="SupportedMimeTypes" type="as" access="read"/>
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
    <signal name="Seeked"><arg name="Position" type="x"/></signal>
    <property name="PlaybackStatus" type="s" access="read"/>
    <property name="LoopStatus" type="s" access="read"/>
    <property name="Rate" type="d" access="read"/>
    <property name="Shuffle" type="b" access="read"/>
    <property name="Metadata" type="a{sv}" access="read"/>
    <property name="Volume" type="d" access="read"/>
    <property name="Position" type="x" access="read"/>
    <property name="MinimumRate" type="d" access="read"/>
    <property name="MaximumRate" type="d" access="read"/>
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
}

private object MprisGioBindings {
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

    fun free(ptr: COpaquePointer?) {
        if (ptr != null) fnFree?.invoke(ptr)
    }

    fun malloc0(bytes: ULong): COpaquePointer? = fnMalloc0?.invoke(bytes)

    fun objectUnref(obj: COpaquePointer?) {
        if (obj != null) fnObjectUnref?.invoke(obj)
    }

    fun variantTypeNew(typeStr: String): COpaquePointer? = memScoped {
        fnVariantTypeNew?.invoke(typeStr.cstr.ptr)
    }

    fun variantTypeFree(vtype: COpaquePointer?) {
        if (vtype != null) fnVariantTypeFree?.invoke(vtype)
    }

    fun variantNewString(value: String): COpaquePointer? = memScoped {
        fnVariantNewString?.invoke(value.cstr.ptr)
    }

    fun variantNewObjectPath(path: String): COpaquePointer? = memScoped {
        fnVariantNewObjectPath?.invoke(path.cstr.ptr)
    }

    fun variantNewBoolean(value: Boolean): COpaquePointer? =
        fnVariantNewBoolean?.invoke(if (value) 1 else 0)

    fun variantNewInt64(value: Long): COpaquePointer? = fnVariantNewInt64?.invoke(value)
    fun variantNewDouble(value: Double): COpaquePointer? = fnVariantNewDouble?.invoke(value)
    fun variantNewVariant(child: COpaquePointer?): COpaquePointer? = child?.let { fnVariantNewVariant?.invoke(it) }

    fun variantNewArray(childType: COpaquePointer?, children: List<COpaquePointer>): COpaquePointer? = memScoped {
        val fn = fnVariantNewArray ?: return@memScoped null
        if (children.isEmpty()) {
            fn.invoke(childType, null, 0.convert())
        } else {
            val arr = allocArray<COpaquePointerVar>(children.size)
            for (i in children.indices) {
                arr[i] = children[i]
            }
            fn.invoke(childType, arr, children.size.convert())
        }
    }

    fun variantNewTuple(children: List<COpaquePointer>): COpaquePointer? = memScoped {
        val fn = fnVariantNewTuple ?: return@memScoped null
        if (children.isEmpty()) {
            fn.invoke(null, 0.convert())
        } else {
            val arr = allocArray<COpaquePointerVar>(children.size)
            for (i in children.indices) {
                arr[i] = children[i]
            }
            fn.invoke(arr, children.size.convert())
        }
    }

    fun variantNewDictEntry(key: COpaquePointer?, value: COpaquePointer?): COpaquePointer? =
        if (key != null && value != null) fnVariantNewDictEntry?.invoke(key, value) else null

    fun variantGetChildValue(container: COpaquePointer?, index: Int): COpaquePointer? =
        if (container != null) fnVariantGetChildValue?.invoke(container, index.convert()) else null

    fun variantGetInt64(variant: COpaquePointer?): Long =
        if (variant != null) fnVariantGetInt64?.invoke(variant) ?: 0L else 0L

    fun variantUnref(variant: COpaquePointer?) {
        if (variant != null) fnVariantUnref?.invoke(variant)
    }

    private fun clearError(errPtr: CPointer<COpaquePointerVar>) {
        val e = errPtr.pointed.value
        if (e != null) {
            fnErrorFree?.invoke(e)
            errPtr.pointed.value = null
        }
    }

    fun busGetSessionSync(): COpaquePointer? = memScoped {
        val err = alloc<COpaquePointerVar>()
        err.value = null
        val bus = fnBusGetSync?.invoke(G_BUS_TYPE_SESSION, null, err.ptr)
        clearError(err.ptr)
        bus
    }

    fun busOwnName(name: String): UInt = memScoped {
        fnBusOwnName?.invoke(G_BUS_TYPE_SESSION, name.cstr.ptr, 0u, null, null, null, null, null) ?: 0u
    }

    fun busUnownName(ownerId: UInt) {
        if (ownerId != 0u) fnBusUnownName?.invoke(ownerId)
    }

    fun dbusNodeInfoNewForXml(xml: String): COpaquePointer? = memScoped {
        val err = alloc<COpaquePointerVar>()
        err.value = null
        val info = fnDbusNodeInfoNewForXml?.invoke(xml.cstr.ptr, err.ptr)
        clearError(err.ptr)
        info
    }

    fun dbusNodeInfoLookupInterface(nodeInfo: COpaquePointer, name: String): COpaquePointer? = memScoped {
        fnDbusNodeInfoLookupInterface?.invoke(nodeInfo, name.cstr.ptr)
    }

    fun dbusNodeInfoUnref(nodeInfo: COpaquePointer?) {
        if (nodeInfo != null) fnDbusNodeInfoUnref?.invoke(nodeInfo)
    }

    fun dbusRegisterObject(
        connection: COpaquePointer,
        objectPath: String,
        interfaceInfo: COpaquePointer,
        vtable: COpaquePointer,
        userData: COpaquePointer?
    ): UInt = memScoped {
        val err = alloc<COpaquePointerVar>()
        err.value = null
        val regId = fnDbusRegisterObject?.invoke(
            connection,
            objectPath.cstr.ptr,
            interfaceInfo,
            vtable,
            userData,
            null,
            err.ptr
        ) ?: 0u
        clearError(err.ptr)
        regId
    }

    fun dbusUnregisterObject(connection: COpaquePointer, registrationId: UInt) {
        if (registrationId != 0u) fnDbusUnregisterObject?.invoke(connection, registrationId)
    }

    fun dbusEmitSignal(
        connection: COpaquePointer,
        objectPath: String,
        interfaceName: String,
        signalName: String,
        parameters: COpaquePointer?
    ) = memScoped {
        val err = alloc<COpaquePointerVar>()
        err.value = null
        fnDbusEmitSignal?.invoke(
            connection,
            null,
            objectPath.cstr.ptr,
            interfaceName.cstr.ptr,
            signalName.cstr.ptr,
            parameters,
            err.ptr
        )
        clearError(err.ptr)
    }

    fun dbusMethodReturnValue(invocation: COpaquePointer, value: COpaquePointer?) {
        fnDbusMethodReturnValue?.invoke(invocation, value)
    }

    fun dbusMethodReturnError(invocation: COpaquePointer, errorName: String, message: String) = memScoped {
        fnDbusMethodReturnError?.invoke(invocation, errorName.cstr.ptr, message.cstr.ptr)
    }
}
