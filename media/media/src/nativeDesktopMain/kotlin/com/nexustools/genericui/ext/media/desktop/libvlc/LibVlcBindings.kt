@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.media.desktop.libvlc

import com.nexustools.genericui.ext.media.vlc.VlcAudioOutputDevice
import com.nexustools.genericui.ext.media.vlc.VlcAudioOutputModule
import com.nexustools.genericui.ext.media.vlc.VlcConfiguration
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.plus
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value

/**
 * Shared Kotlin/Native bindings to the official `libvlc` C API (`libvlc.h`, `libvlc_media.h`,
 * `libvlc_media_player.h`) used by the `:kde`, `:gtk`, `:windows`, and `:macos` media extension backends.
 */
object LibVlcBindings {
    const val SLAVE_TYPE_SUBTITLE: Int = 0
    const val SLAVE_TYPE_AUDIO: Int = 1

    private val libHandle: COpaquePointer? by lazy { LibVlcDynamicLoader.openLibVlc() }

    val isAvailable: Boolean
        get() = libHandle != null

    private fun symbol(name: String): COpaquePointer? {
        val handle = libHandle ?: return null
        return LibVlcDynamicLoader.findSymbol(handle, name)
    }

    fun libvlc_get_version(): String {
        val fn = symbol("libvlc_get_version")
            ?.reinterpret<CFunction<() -> CPointer<ByteVar>?>>()
            ?: return "libvlc (unavailable)"
        return fn.invoke()?.toKString() ?: "libvlc"
    }

    fun libvlc_errmsg(): String? {
        val fn = symbol("libvlc_errmsg")
            ?.reinterpret<CFunction<() -> CPointer<ByteVar>?>>()
            ?: return null
        return fn.invoke()?.toKString()
    }

    private fun invokeLibVlcNew(
        fn: CPointer<CFunction<(Int, CPointer<CPointerVar<ByteVar>>?) -> COpaquePointer?>>,
        args: List<String>
    ): COpaquePointer? = memScoped {
        if (args.isEmpty()) {
            fn.invoke(0, null)
        } else {
            val argv = allocArray<CPointerVar<ByteVar>>(args.size)
            for (i in args.indices) {
                argv[i] = args[i].cstr.ptr
            }
            fn.invoke(args.size, argv)
        }
    }

    fun libvlc_new(
        args: List<String> = listOf(
            "--no-video-title-show",
            "--no-sub-autodetect-file",
            "--avcodec-hw=any",
            "--quiet"
        )
    ): COpaquePointer? {
        val fn = symbol("libvlc_new")
            ?.reinterpret<CFunction<(Int, CPointer<CPointerVar<ByteVar>>?) -> COpaquePointer?>>()
            ?: return null
        invokeLibVlcNew(fn, args)?.let { return it }
        if (args.any { it.startsWith("--avcodec-") }) {
            val safeFallbackArgs = args.filterNot { it.startsWith("--avcodec-") }
            invokeLibVlcNew(fn, safeFallbackArgs)?.let { return it }
        }
        if (args.isNotEmpty()) {
            invokeLibVlcNew(fn, listOf("--quiet"))?.let { return it }
            return invokeLibVlcNew(fn, emptyList())
        }
        return null
    }

    fun libvlc_release(instance: COpaquePointer?) {
        if (instance == null) return
        val fn = symbol("libvlc_release")
            ?.reinterpret<CFunction<(COpaquePointer) -> Unit>>()
            ?: return
        fn.invoke(instance)
    }

    fun libvlc_media_new_location(instance: COpaquePointer?, mrl: String): COpaquePointer? {
        if (instance == null) return null
        val fn = symbol("libvlc_media_new_location")
            ?.reinterpret<CFunction<(COpaquePointer, CPointer<ByteVar>) -> COpaquePointer?>>()
            ?: return null
        return memScoped {
            fn.invoke(instance, mrl.cstr.ptr)
        }
    }

    fun libvlc_media_new_path(instance: COpaquePointer?, path: String): COpaquePointer? {
        if (instance == null) return null
        val fn = symbol("libvlc_media_new_path")
            ?.reinterpret<CFunction<(COpaquePointer, CPointer<ByteVar>) -> COpaquePointer?>>()
            ?: return null
        return memScoped {
            fn.invoke(instance, path.cstr.ptr)
        }
    }

    fun libvlc_media_add_option(media: COpaquePointer?, option: String) {
        if (media == null) return
        val fn = symbol("libvlc_media_add_option")
            ?.reinterpret<CFunction<(COpaquePointer, CPointer<ByteVar>) -> Unit>>()
            ?: return
        memScoped {
            fn.invoke(media, option.cstr.ptr)
        }
    }

    fun libvlc_media_release(media: COpaquePointer?) {
        if (media == null) return
        val fn = symbol("libvlc_media_release")
            ?.reinterpret<CFunction<(COpaquePointer) -> Unit>>()
            ?: return
        fn.invoke(media)
    }

    fun libvlc_media_player_new(instance: COpaquePointer?): COpaquePointer? {
        if (instance == null) return null
        val fn = symbol("libvlc_media_player_new")
            ?.reinterpret<CFunction<(COpaquePointer) -> COpaquePointer?>>()
            ?: return null
        return fn.invoke(instance)
    }

    fun libvlc_media_player_release(player: COpaquePointer?) {
        if (player == null) return
        val fn = symbol("libvlc_media_player_release")
            ?.reinterpret<CFunction<(COpaquePointer) -> Unit>>()
            ?: return
        fn.invoke(player)
    }

    fun libvlc_media_player_set_media(player: COpaquePointer?, media: COpaquePointer?) {
        if (player == null) return
        val fn = symbol("libvlc_media_player_set_media")
            ?.reinterpret<CFunction<(COpaquePointer, COpaquePointer?) -> Unit>>()
            ?: return
        fn.invoke(player, media)
    }

    fun libvlc_media_player_play(player: COpaquePointer?): Int {
        if (player == null) return -1
        val fn = symbol("libvlc_media_player_play")
            ?.reinterpret<CFunction<(COpaquePointer) -> Int>>()
            ?: return -1
        return fn.invoke(player)
    }

    fun libvlc_media_player_set_pause(player: COpaquePointer?, doPause: Int) {
        if (player == null) return
        val fn = symbol("libvlc_media_player_set_pause")
            ?.reinterpret<CFunction<(COpaquePointer, Int) -> Unit>>()
            ?: return
        fn.invoke(player, doPause)
    }

    fun libvlc_media_player_stop(player: COpaquePointer?) {
        if (player == null) return
        val fn = symbol("libvlc_media_player_stop")
            ?.reinterpret<CFunction<(COpaquePointer) -> Unit>>()
            ?: return
        fn.invoke(player)
    }

    fun libvlc_media_player_is_playing(player: COpaquePointer?): Boolean {
        if (player == null) return false
        val fn = symbol("libvlc_media_player_is_playing")
            ?.reinterpret<CFunction<(COpaquePointer) -> Int>>()
            ?: return false
        return fn.invoke(player) != 0
    }

    fun libvlc_media_player_get_state(player: COpaquePointer?): Int {
        if (player == null) return -1
        val fn = symbol("libvlc_media_player_get_state")
            ?.reinterpret<CFunction<(COpaquePointer) -> Int>>()
            ?: return -1
        return fn.invoke(player)
    }

    fun libvlc_media_player_get_time(player: COpaquePointer?): Long {
        if (player == null) return -1L
        val fn = symbol("libvlc_media_player_get_time")
            ?.reinterpret<CFunction<(COpaquePointer) -> Long>>()
            ?: return -1L
        return fn.invoke(player)
    }

    fun libvlc_media_player_set_time(player: COpaquePointer?, timeMs: Long) {
        if (player == null) return
        val fn = symbol("libvlc_media_player_set_time")
            ?.reinterpret<CFunction<(COpaquePointer, Long) -> Unit>>()
            ?: return
        fn.invoke(player, timeMs)
    }

    fun libvlc_media_player_get_length(player: COpaquePointer?): Long {
        if (player == null) return -1L
        val fn = symbol("libvlc_media_player_get_length")
            ?.reinterpret<CFunction<(COpaquePointer) -> Long>>()
            ?: return -1L
        return fn.invoke(player)
    }

    fun libvlc_media_player_set_rate(player: COpaquePointer?, rate: Float): Int {
        if (player == null) return -1
        val fn = symbol("libvlc_media_player_set_rate")
            ?.reinterpret<CFunction<(COpaquePointer, Float) -> Int>>()
            ?: return -1
        return fn.invoke(player, rate)
    }

    fun libvlc_media_player_get_rate(player: COpaquePointer?): Float {
        if (player == null) return 1.0f
        val fn = symbol("libvlc_media_player_get_rate")
            ?.reinterpret<CFunction<(COpaquePointer) -> Float>>()
            ?: return 1.0f
        return fn.invoke(player)
    }

    fun libvlc_media_player_get_fps(player: COpaquePointer?): Float {
        if (player == null) return 0.0f
        val fn = symbol("libvlc_media_player_get_fps")
            ?.reinterpret<CFunction<(COpaquePointer) -> Float>>()
            ?: return 0.0f
        return fn.invoke(player)
    }

    fun libvlc_audio_set_volume(player: COpaquePointer?, volumePercent: Int): Int {
        if (player == null) return -1
        val fn = symbol("libvlc_audio_set_volume")
            ?.reinterpret<CFunction<(COpaquePointer, Int) -> Int>>()
            ?: return -1
        return fn.invoke(player, volumePercent)
    }

    fun libvlc_audio_get_volume(player: COpaquePointer?): Int {
        if (player == null) return 100
        val fn = symbol("libvlc_audio_get_volume")
            ?.reinterpret<CFunction<(COpaquePointer) -> Int>>()
            ?: return 100
        return fn.invoke(player)
    }

    fun libvlc_audio_set_mute(player: COpaquePointer?, status: Int) {
        if (player == null) return
        val fn = symbol("libvlc_audio_set_mute")
            ?.reinterpret<CFunction<(COpaquePointer, Int) -> Unit>>()
            ?: return
        fn.invoke(player, status)
    }

    fun libvlc_audio_get_mute(player: COpaquePointer?): Boolean {
        if (player == null) return false
        val fn = symbol("libvlc_audio_get_mute")
            ?.reinterpret<CFunction<(COpaquePointer) -> Int>>()
            ?: return false
        return fn.invoke(player) > 0
    }

    fun libvlc_audio_output_list_get(instance: COpaquePointer?): COpaquePointer? {
        if (instance == null) return null
        val fn = symbol("libvlc_audio_output_list_get")
            ?.reinterpret<CFunction<(COpaquePointer) -> COpaquePointer?>>()
            ?: return null
        return fn.invoke(instance)
    }

    fun libvlc_audio_output_list_release(pList: COpaquePointer?) {
        if (pList == null) return
        val fn = symbol("libvlc_audio_output_list_release")
            ?.reinterpret<CFunction<(COpaquePointer) -> Unit>>()
            ?: return
        fn.invoke(pList)
    }

    fun libvlc_audio_output_set(player: COpaquePointer?, name: String): Int {
        if (player == null) return -1
        val fn = symbol("libvlc_audio_output_set")
            ?.reinterpret<CFunction<(COpaquePointer, CPointer<ByteVar>) -> Int>>()
            ?: return -1
        return memScoped {
            fn.invoke(player, name.cstr.ptr)
        }
    }

    fun libvlc_audio_output_device_enum(player: COpaquePointer?): COpaquePointer? {
        if (player == null) return null
        val fn = symbol("libvlc_audio_output_device_enum")
            ?.reinterpret<CFunction<(COpaquePointer) -> COpaquePointer?>>()
            ?: return null
        return fn.invoke(player)
    }

    fun libvlc_audio_output_device_list_get(instance: COpaquePointer?, aout: String): COpaquePointer? {
        if (instance == null) return null
        val fn = symbol("libvlc_audio_output_device_list_get")
            ?.reinterpret<CFunction<(COpaquePointer, CPointer<ByteVar>) -> COpaquePointer?>>()
            ?: return null
        return memScoped {
            fn.invoke(instance, aout.cstr.ptr)
        }
    }

    fun libvlc_audio_output_device_list_release(pList: COpaquePointer?) {
        if (pList == null) return
        val fn = symbol("libvlc_audio_output_device_list_release")
            ?.reinterpret<CFunction<(COpaquePointer) -> Unit>>()
            ?: return
        fn.invoke(pList)
    }

    fun libvlc_audio_output_device_set(player: COpaquePointer?, module: String?, deviceId: String?) {
        if (player == null) return
        val fn = symbol("libvlc_audio_output_device_set")
            ?.reinterpret<CFunction<(COpaquePointer, CPointer<ByteVar>?, CPointer<ByteVar>?) -> Unit>>()
            ?: return
        memScoped {
            fn.invoke(player, module?.cstr?.ptr, deviceId?.cstr?.ptr)
        }
    }

    internal fun applyAudioConfiguration(
        player: COpaquePointer?,
        config: VlcConfiguration,
        previousModule: String,
        previousDeviceId: String
    ): Pair<String, String> {
        if (player == null || !isAvailable) return previousModule to previousDeviceId
        val nextModule = if (config.audioOutputModule.isNotBlank()) {
            libvlc_audio_output_set(player, config.audioOutputModule)
            config.audioOutputModule
        } else if (previousModule.isNotBlank()) {
            libvlc_audio_output_set(player, "any")
            ""
        } else {
            ""
        }
        val nextDeviceId = if (config.audioOutputDeviceId.isNotBlank()) {
            libvlc_audio_output_device_set(
                player = player,
                module = config.audioOutputModule.ifBlank { null },
                deviceId = config.audioOutputDeviceId
            )
            config.audioOutputDeviceId
        } else if (previousDeviceId.isNotBlank()) {
            libvlc_audio_output_device_set(
                player = player,
                module = config.audioOutputModule.ifBlank { null },
                deviceId = ""
            )
            ""
        } else {
            ""
        }
        return nextModule to nextDeviceId
    }

    fun readAudioOutputModules(instance: COpaquePointer?): List<VlcAudioOutputModule> {
        val head = libvlc_audio_output_list_get(instance) ?: return emptyList()
        val result = mutableListOf<VlcAudioOutputModule>()
        val ptrSize = sizeOf<COpaquePointerVar>().toInt()
        try {
            var cur: CPointer<ByteVar>? = head.reinterpret()
            var safety = 0
            while (cur != null && safety < 256) {
                safety += 1
                // VLC 3.x libvlc_audio_output_t: +0 psz_name, +8 psz_description, +16 p_next
                val namePtr = (cur + 0)!!.reinterpret<CPointerVar<ByteVar>>().pointed.value
                val descPtr = (cur + ptrSize)!!.reinterpret<CPointerVar<ByteVar>>().pointed.value
                val nextPtr = (cur + (ptrSize * 2))!!.reinterpret<COpaquePointerVar>().pointed.value?.reinterpret<ByteVar>()
                val name = namePtr?.toKString()?.trim().orEmpty()
                val description = descPtr?.toKString()?.trim().orEmpty().ifEmpty { name }
                if (name.isNotEmpty()) {
                    result.add(VlcAudioOutputModule(name = name, description = description))
                }
                cur = nextPtr
            }
        } finally {
            libvlc_audio_output_list_release(head)
        }
        return result
    }

    fun readAudioOutputDevices(instance: COpaquePointer?, audioOutputModule: String): List<VlcAudioOutputDevice> {
        val trimmedModule = audioOutputModule.trim()
        if (trimmedModule.isEmpty()) return emptyList()
        val head = libvlc_audio_output_device_list_get(instance, trimmedModule) ?: return emptyList()
        return parseAudioOutputDeviceList(head)
    }

    fun readAudioOutputDevices(player: COpaquePointer?): List<VlcAudioOutputDevice> {
        val head = libvlc_audio_output_device_enum(player) ?: return emptyList()
        return parseAudioOutputDeviceList(head)
    }

    private fun parseAudioOutputDeviceList(head: COpaquePointer): List<VlcAudioOutputDevice> {
        val result = mutableListOf<VlcAudioOutputDevice>()
        val ptrSize = sizeOf<COpaquePointerVar>().toInt()
        try {
            var cur: CPointer<ByteVar>? = head.reinterpret()
            var safety = 0
            while (cur != null && safety < 256) {
                safety += 1
                // VLC 3.x libvlc_audio_output_device_t: +0 p_next, +8 psz_device, +16 psz_description
                val nextPtr = (cur + 0)!!.reinterpret<COpaquePointerVar>().pointed.value?.reinterpret<ByteVar>()
                val devicePtr = (cur + ptrSize)!!.reinterpret<CPointerVar<ByteVar>>().pointed.value
                val descPtr = (cur + (ptrSize * 2))!!.reinterpret<CPointerVar<ByteVar>>().pointed.value
                val deviceId = devicePtr?.toKString()?.trim().orEmpty()
                val description = descPtr?.toKString()?.trim().orEmpty().ifEmpty { deviceId }
                if (deviceId.isNotEmpty()) {
                    result.add(VlcAudioOutputDevice(deviceId = deviceId, description = description))
                }
                cur = nextPtr
            }
        } finally {
            libvlc_audio_output_device_list_release(head)
        }
        return result
    }

    fun libvlc_audio_get_track(player: COpaquePointer?): Int {
        if (player == null) return -1
        val fn = symbol("libvlc_audio_get_track")
            ?.reinterpret<CFunction<(COpaquePointer) -> Int>>()
            ?: return -1
        return fn.invoke(player)
    }

    fun libvlc_audio_get_track_count(player: COpaquePointer?): Int {
        if (player == null) return 0
        val fn = symbol("libvlc_audio_get_track_count")
            ?.reinterpret<CFunction<(COpaquePointer) -> Int>>()
            ?: return 0
        return fn.invoke(player)
    }

    fun libvlc_audio_get_track_description(player: COpaquePointer?): COpaquePointer? {
        if (player == null) return null
        val fn = symbol("libvlc_audio_get_track_description")
            ?.reinterpret<CFunction<(COpaquePointer) -> COpaquePointer?>>()
            ?: return null
        return fn.invoke(player)
    }

    fun libvlc_audio_set_track(player: COpaquePointer?, trackId: Int): Int {
        if (player == null) return -1
        val fn = symbol("libvlc_audio_set_track")
            ?.reinterpret<CFunction<(COpaquePointer, Int) -> Int>>()
            ?: return -1
        return fn.invoke(player, trackId)
    }

    fun libvlc_video_get_spu(player: COpaquePointer?): Int {
        if (player == null) return -1
        val fn = symbol("libvlc_video_get_spu")
            ?.reinterpret<CFunction<(COpaquePointer) -> Int>>()
            ?: return -1
        return fn.invoke(player)
    }

    fun libvlc_video_get_spu_count(player: COpaquePointer?): Int {
        if (player == null) return 0
        val fn = symbol("libvlc_video_get_spu_count")
            ?.reinterpret<CFunction<(COpaquePointer) -> Int>>()
            ?: return 0
        return fn.invoke(player)
    }

    fun libvlc_video_get_spu_description(player: COpaquePointer?): COpaquePointer? {
        if (player == null) return null
        val fn = symbol("libvlc_video_get_spu_description")
            ?.reinterpret<CFunction<(COpaquePointer) -> COpaquePointer?>>()
            ?: return null
        return fn.invoke(player)
    }

    fun libvlc_video_set_spu(player: COpaquePointer?, spuId: Int): Int {
        if (player == null) return -1
        val fn = symbol("libvlc_video_set_spu")
            ?.reinterpret<CFunction<(COpaquePointer, Int) -> Int>>()
            ?: return -1
        return fn.invoke(player, spuId)
    }

    fun libvlc_track_description_list_release(p_track_description: COpaquePointer?) {
        if (p_track_description == null) return
        val fn = symbol("libvlc_track_description_list_release")
            ?.reinterpret<CFunction<(COpaquePointer) -> Unit>>()
            ?: return
        fn.invoke(p_track_description)
    }

    fun readAudioTrackDescriptions(player: COpaquePointer?): List<Pair<Int, String>> =
        parseTrackDescriptionList(libvlc_audio_get_track_description(player))

    fun readSubtitleTrackDescriptions(player: COpaquePointer?): List<Pair<Int, String>> =
        parseTrackDescriptionList(libvlc_video_get_spu_description(player))

    private fun parseTrackDescriptionList(head: COpaquePointer?): List<Pair<Int, String>> {
        if (head == null) return emptyList()
        val result = mutableListOf<Pair<Int, String>>()
        try {
            var cur: CPointer<ByteVar>? = head.reinterpret()
            var safety = 0
            while (cur != null && safety < 256) {
                safety += 1
                val id = (cur + 0)!!.reinterpret<IntVar>().pointed.value
                val namePtr = (cur + 8)!!.reinterpret<CPointerVar<ByteVar>>().pointed.value
                val name = namePtr?.toKString()?.takeIf { it.isNotBlank() } ?: "Track $id"
                result.add(id to name)
                cur = (cur + 16)!!.reinterpret<CPointerVar<ByteVar>>().pointed.value
            }
        } finally {
            libvlc_track_description_list_release(head)
        }
        return result
    }

    fun libvlc_video_get_size(player: COpaquePointer?, num: UInt = 0u): Pair<Int, Int>? {
        if (player == null) return null
        val fn = symbol("libvlc_video_get_size")
            ?.reinterpret<CFunction<(COpaquePointer, UInt, CPointer<UIntVar>, CPointer<UIntVar>) -> Int>>()
            ?: return null
        return memScoped {
            val px = alloc<UIntVar>()
            val py = alloc<UIntVar>()
            val rc = fn.invoke(player, num, px.ptr, py.ptr)
            val w = px.value.toInt()
            val h = py.value.toInt()
            if (rc == 0 && w > 0 && h > 0) w to h else null
        }
    }

    fun libvlc_video_set_aspect_ratio(player: COpaquePointer?, aspect: String?) {
        if (player == null) return
        val fn = symbol("libvlc_video_set_aspect_ratio")
            ?.reinterpret<CFunction<(COpaquePointer, CPointer<ByteVar>?) -> Unit>>()
            ?: return
        memScoped {
            fn.invoke(player, aspect?.cstr?.ptr)
        }
    }

    fun libvlc_video_set_scale(player: COpaquePointer?, factor: Float) {
        if (player == null) return
        val fn = symbol("libvlc_video_set_scale")
            ?.reinterpret<CFunction<(COpaquePointer, Float) -> Unit>>()
            ?: return
        fn.invoke(player, factor)
    }

    fun libvlc_video_set_deinterlace(player: COpaquePointer?, mode: String?) {
        if (player == null) return
        val fn = symbol("libvlc_video_set_deinterlace")
            ?.reinterpret<CFunction<(COpaquePointer, CPointer<ByteVar>?) -> Unit>>()
            ?: return
        memScoped {
            fn.invoke(player, mode?.cstr?.ptr)
        }
    }

    fun libvlc_media_player_add_slave(player: COpaquePointer?, type: Int, uri: String, select: Boolean): Int {
        if (player == null) return -1
        val fn = symbol("libvlc_media_player_add_slave")
            ?.reinterpret<CFunction<(COpaquePointer, Int, CPointer<ByteVar>, Int) -> Int>>()
            ?: return -1
        return memScoped {
            fn.invoke(player, type, uri.cstr.ptr, if (select) 1 else 0)
        }
    }

    fun libvlc_media_player_set_hwnd(player: COpaquePointer?, hwnd: COpaquePointer?) {
        if (player == null) return
        val fn = symbol("libvlc_media_player_set_hwnd")
            ?.reinterpret<CFunction<(COpaquePointer, COpaquePointer?) -> Unit>>()
            ?: return
        fn.invoke(player, hwnd)
    }

    fun libvlc_media_player_set_nsobject(player: COpaquePointer?, nsObject: COpaquePointer?) {
        if (player == null) return
        val fn = symbol("libvlc_media_player_set_nsobject")
            ?.reinterpret<CFunction<(COpaquePointer, COpaquePointer?) -> Unit>>()
            ?: return
        fn.invoke(player, nsObject)
    }

    fun libvlc_media_player_set_xwindow(player: COpaquePointer?, drawable: UInt) {
        if (player == null) return
        val fn = symbol("libvlc_media_player_set_xwindow")
            ?.reinterpret<CFunction<(COpaquePointer, UInt) -> Unit>>()
            ?: return
        fn.invoke(player, drawable)
    }

    fun libvlc_audio_set_format(player: COpaquePointer?, formatFourCc: String, rate: Int, channels: Int) {
        if (player == null) return
        val fn = symbol("libvlc_audio_set_format")
            ?.reinterpret<CFunction<(COpaquePointer, CPointer<ByteVar>, UInt, UInt) -> Unit>>()
            ?: return
        memScoped {
            fn.invoke(player, formatFourCc.cstr.ptr, rate.toUInt(), channels.toUInt())
        }
    }

    fun libvlc_audio_set_callbacks(
        player: COpaquePointer?,
        playCb: COpaquePointer?,
        pauseCb: COpaquePointer? = null,
        resumeCb: COpaquePointer? = null,
        flushCb: COpaquePointer? = null,
        drainCb: COpaquePointer? = null,
        opaque: COpaquePointer? = null
    ) {
        if (player == null) return
        val fn = symbol("libvlc_audio_set_callbacks")
            ?.reinterpret<CFunction<(COpaquePointer, COpaquePointer?, COpaquePointer?, COpaquePointer?, COpaquePointer?, COpaquePointer?, COpaquePointer?) -> Unit>>()
            ?: return
        fn.invoke(player, playCb, pauseCb, resumeCb, flushCb, drainCb, opaque)
    }

    fun libvlc_video_set_format(player: COpaquePointer?, chroma: String, width: Int, height: Int, pitch: Int) {
        if (player == null) return
        val fn = symbol("libvlc_video_set_format")
            ?.reinterpret<CFunction<(COpaquePointer, CPointer<ByteVar>, UInt, UInt, UInt) -> Unit>>()
            ?: return
        memScoped {
            fn.invoke(player, chroma.cstr.ptr, width.toUInt(), height.toUInt(), pitch.toUInt())
        }
    }

    fun libvlc_video_set_format_callbacks(
        player: COpaquePointer?,
        setupCb: COpaquePointer?,
        cleanupCb: COpaquePointer? = null
    ) {
        if (player == null) return
        val fn = symbol("libvlc_video_set_format_callbacks")
            ?.reinterpret<CFunction<(COpaquePointer, COpaquePointer?, COpaquePointer?) -> Unit>>()
            ?: return
        fn.invoke(player, setupCb, cleanupCb)
    }

    fun libvlc_video_set_callbacks(
        player: COpaquePointer?,
        lockCb: COpaquePointer? = null,
        unlockCb: COpaquePointer? = null,
        displayCb: COpaquePointer? = null,
        opaque: COpaquePointer? = null
    ) {
        if (player == null) return
        val fn = symbol("libvlc_video_set_callbacks")
            ?.reinterpret<CFunction<(COpaquePointer, COpaquePointer?, COpaquePointer?, COpaquePointer?, COpaquePointer?) -> Unit>>()
            ?: return
        fn.invoke(player, lockCb, unlockCb, displayCb, opaque)
    }
}
