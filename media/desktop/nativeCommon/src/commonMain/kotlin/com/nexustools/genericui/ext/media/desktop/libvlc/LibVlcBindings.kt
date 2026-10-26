@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.media.desktop.libvlc

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString

/**
 * Shared Kotlin/Native bindings to the official `libvlc` C API (`libvlc.h`, `libvlc_media.h`,
 * `libvlc_media_player.h`) used by the `:qt`, `:gtk`, `:windows`, and `:macos` media extension backends.
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

    fun libvlc_new(args: List<String> = emptyList()): COpaquePointer? {
        val fn = symbol("libvlc_new")
            ?.reinterpret<CFunction<(Int, CPointer<CPointerVar<ByteVar>>?) -> COpaquePointer?>>()
            ?: return null
        return memScoped {
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
        return fn.invoke(player) != 0
    }

    fun libvlc_audio_set_track(player: COpaquePointer?, trackId: Int): Int {
        if (player == null) return -1
        val fn = symbol("libvlc_audio_set_track")
            ?.reinterpret<CFunction<(COpaquePointer, Int) -> Int>>()
            ?: return -1
        return fn.invoke(player, trackId)
    }

    fun libvlc_video_set_spu(player: COpaquePointer?, spuId: Int): Int {
        if (player == null) return -1
        val fn = symbol("libvlc_video_set_spu")
            ?.reinterpret<CFunction<(COpaquePointer, Int) -> Int>>()
            ?: return -1
        return fn.invoke(player, spuId)
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
}
