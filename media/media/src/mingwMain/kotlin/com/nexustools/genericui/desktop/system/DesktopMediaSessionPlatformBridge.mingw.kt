@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.desktop.system

import com.nexustools.genericui.component.system.MediaSessionMetadata
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaque
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.plus
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.value
import platform.windows.CallNextHookEx
import platform.windows.DWORDVar
import platform.windows.FindWindowExW
import platform.windows.GetActiveWindow
import platform.windows.GetCurrentProcessId
import platform.windows.GetModuleHandleW
import platform.windows.GetProcAddress
import platform.windows.GetWindowThreadProcessId
import platform.windows.HHOOK
import platform.windows.HRESULT
import platform.windows.HWND
import platform.windows.KBDLLHOOKSTRUCT
import platform.windows.LPARAM
import platform.windows.LRESULT
import platform.windows.LoadLibraryW
import platform.windows.SetWindowsHookExW
import platform.windows.ULONG
import platform.windows.ULONGLONG
import platform.windows.UnhookWindowsHookEx
import platform.windows.WH_KEYBOARD_LL
import platform.windows.WM_KEYDOWN
import platform.windows.WM_SYSKEYDOWN
import platform.windows.WPARAM
import kotlin.math.roundToLong

private typealias WinSessionCoInitializeExFn = CFunction<(COpaquePointer?, UInt) -> HRESULT>
private typealias WinSessionCoCreateInstanceFn = CFunction<
    (COpaquePointer?, COpaquePointer?, UInt, COpaquePointer?, CPointer<COpaquePointerVar>?) -> HRESULT
>
private typealias WinSessionComReleaseFn = CFunction<(COpaquePointer?) -> ULONG>
private typealias WinSessionComHrInitFn = CFunction<(COpaquePointer?) -> HRESULT>
private typealias WinSessionComSetProgressValueFn = CFunction<(COpaquePointer?, HWND?, ULONGLONG, ULONGLONG) -> HRESULT>
private typealias WinSessionComSetProgressStateFn = CFunction<(COpaquePointer?, HWND?, Int) -> HRESULT>
private typealias WinSessionComSetTooltipFn = CFunction<(COpaquePointer?, HWND?, CPointer<UShortVar>?) -> HRESULT>

private val activeWinMediaSessionBridges = mutableListOf<DesktopMediaSessionPlatformBridge>()
private var winMediaSessionKeyHook: HHOOK? = null

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun winMediaSessionKeyboardHookProc(nCode: Int, wParam: WPARAM, lParam: LPARAM): LRESULT {
    if (nCode >= 0 && (wParam.toInt() == WM_KEYDOWN || wParam.toInt() == WM_SYSKEYDOWN)) {
        val kb = lParam.toCPointer<KBDLLHOOKSTRUCT>()
        if (kb != null) {
            val vkCode = kb.pointed.vkCode.toInt()
            for (bridge in activeWinMediaSessionBridges.toList()) {
                bridge.handleVirtualKey(vkCode)
            }
        }
    }
    return CallNextHookEx(winMediaSessionKeyHook, nCode, wParam, lParam)
}

/**
 * Windows (`mingwX64`) actual [DesktopMediaSessionPlatformBridge] driving Win32 taskbar
 * progress/tooltip state (`ITaskbarList3`) and `WH_KEYBOARD_LL` multimedia key dispatch.
 */
internal actual class DesktopMediaSessionPlatformBridge actual constructor(
    private val backendToolkit: String
) {
    private companion object {
        const val VK_MEDIA_NEXT_TRACK = 0xB0
        const val VK_MEDIA_PREV_TRACK = 0xB1
        const val VK_MEDIA_PLAY_PAUSE = 0xB3
        const val COINIT_APARTMENTTHREADED = 0x2u
        const val CLSCTX_INPROC_SERVER = 0x1u
        const val TBPF_NOPROGRESS = 0x0000
        const val TBPF_NORMAL = 0x0002
        const val TBPF_PAUSED = 0x0008
    }

    private var metadata: MediaSessionMetadata? = null
    private var playing: Boolean = false
    private var positionSec: Double = 0.0
    private var durationSec: Double = 0.0
    private var taskbarList3Ptr: COpaquePointer? = null
    private var disposed: Boolean = false

    private var onPlay: (() -> Unit)? = null
    private var onPause: (() -> Unit)? = null
    private var onNext: (() -> Unit)? = null
    private var onPrevious: (() -> Unit)? = null
    private var onSeekTo: ((Double) -> Unit)? = null

    init {
        if (this !in activeWinMediaSessionBridges) {
            activeWinMediaSessionBridges.add(this)
        }
        syncKeyboardHook()
    }

    actual fun updateMetadata(metadata: MediaSessionMetadata) {
        if (disposed) return
        this.metadata = metadata
        syncTaskbarState()
    }

    actual fun updatePlaybackState(isPlaying: Boolean, positionSeconds: Double, durationSeconds: Double) {
        if (disposed) return
        this.playing = isPlaying
        this.positionSec = positionSeconds.coerceAtLeast(0.0)
        this.durationSec = durationSeconds.coerceAtLeast(0.0)
        syncTaskbarState()
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
        syncKeyboardHook()
    }

    fun handleVirtualKey(vkCode: Int): Boolean {
        if (disposed) return false
        return when (vkCode) {
            VK_MEDIA_PLAY_PAUSE -> {
                val cb = if (playing) onPause else onPlay
                cb?.invoke()
                cb != null
            }
            VK_MEDIA_NEXT_TRACK -> {
                val cb = onNext ?: return false
                cb()
                true
            }
            VK_MEDIA_PREV_TRACK -> {
                val cb = onPrevious ?: return false
                cb()
                true
            }
            else -> false
        }
    }

    private fun syncKeyboardHook() {
        val wantsHook = !disposed && activeWinMediaSessionBridges.isNotEmpty() &&
            (onPlay != null || onPause != null || onNext != null || onPrevious != null)
        if (wantsHook && winMediaSessionKeyHook == null) {
            winMediaSessionKeyHook = SetWindowsHookExW(
                WH_KEYBOARD_LL,
                staticCFunction(::winMediaSessionKeyboardHookProc),
                GetModuleHandleW(null),
                0u
            )
        } else if (activeWinMediaSessionBridges.isEmpty() && winMediaSessionKeyHook != null) {
            UnhookWindowsHookEx(winMediaSessionKeyHook)
            winMediaSessionKeyHook = null
        }
    }

    private fun resolveActiveHwnd(): HWND? {
        val currentPid = GetCurrentProcessId()
        val active = GetActiveWindow()
        if (active != null && hwndMatchesPid(active, currentPid)) {
            return active
        }
        var candidate: HWND? = FindWindowExW(null, null, "GenericUIWin32HostWindowW", null)
        while (candidate != null) {
            if (hwndMatchesPid(candidate, currentPid)) {
                return candidate
            }
            candidate = FindWindowExW(null, candidate, "GenericUIWin32HostWindowW", null)
        }
        return null
    }

    private fun hwndMatchesPid(hwnd: HWND, expectedPid: UInt): Boolean = memScoped {
        val pidVar = alloc<DWORDVar>()
        GetWindowThreadProcessId(hwnd, pidVar.ptr)
        pidVar.value == expectedPid
    }

    private fun writeGuid(
        dst: CPointer<ByteVar>,
        d1: UInt,
        d2: UShort,
        d3: UShort,
        b0: Int,
        b1: Int,
        b2: Int,
        b3: Int,
        b4: Int,
        b5: Int,
        b6: Int,
        b7: Int
    ) {
        dst.reinterpret<UIntVar>().pointed.value = d1
        (dst + 4)!!.reinterpret<UShortVar>().pointed.value = d2
        (dst + 6)!!.reinterpret<UShortVar>().pointed.value = d3
        dst[8] = b0.toByte()
        dst[9] = b1.toByte()
        dst[10] = b2.toByte()
        dst[11] = b3.toByte()
        dst[12] = b4.toByte()
        dst[13] = b5.toByte()
        dst[14] = b6.toByte()
        dst[15] = b7.toByte()
    }

    private fun ensureTaskbarListCom() {
        if (taskbarList3Ptr != null) return
        val ole32 = LoadLibraryW("ole32.dll") ?: return
        val coInit = GetProcAddress(ole32, "CoInitializeEx")?.reinterpret<WinSessionCoInitializeExFn>()
        val coCreate = GetProcAddress(ole32, "CoCreateInstance")?.reinterpret<WinSessionCoCreateInstanceFn>() ?: return
        memScoped {
            coInit?.invoke(null, COINIT_APARTMENTTHREADED)
            val clsid = allocArray<ByteVar>(16)
            writeGuid(clsid, 0x56FDF344u, 0xFD6Du, 0x11D0u, 0x95, 0x8A, 0x00, 0x60, 0x97, 0xC9, 0xA0, 0x90)
            val iid = allocArray<ByteVar>(16)
            writeGuid(iid, 0xEA1AFB91u, 0x9E28u, 0x4B86u, 0x90, 0xE9, 0x9E, 0x9F, 0x8A, 0x5E, 0xEF, 0xAF)
            val outPtr = alloc<COpaquePointerVar>()
            val hr = coCreate(clsid, null, CLSCTX_INPROC_SERVER, iid, outPtr.ptr)
            val created = outPtr.value
            if (hr >= 0 && created != null) {
                val vtable = created.reinterpret<CPointerVar<CPointerVar<COpaque>>>().pointed.value
                val hrInit = vtable?.let { (it + 3)!!.pointed.value?.reinterpret<WinSessionComHrInitFn>() }
                if ((hrInit?.invoke(created) ?: -1) >= 0) {
                    taskbarList3Ptr = created
                } else {
                    val rel = vtable?.let { (it + 2)!!.pointed.value?.reinterpret<WinSessionComReleaseFn>() }
                    rel?.invoke(created)
                }
            }
        }
    }

    private fun syncTaskbarState() {
        val hwnd = resolveActiveHwnd() ?: return
        ensureTaskbarListCom()
        val comPtr = taskbarList3Ptr ?: return
        val vtable = comPtr.reinterpret<CPointerVar<CPointerVar<COpaque>>>().pointed.value ?: return
        val meta = metadata
        val tooltip = buildString {
            if (meta != null && meta.title.isNotBlank()) {
                append(meta.title)
                if (meta.artist.isNotBlank()) {
                    append(" - ")
                    append(meta.artist)
                }
            }
        }
        val stateFlag = when {
            playing && durationSec > 0.0 -> TBPF_NORMAL
            !playing && durationSec > 0.0 -> TBPF_PAUSED
            else -> TBPF_NOPROGRESS
        }
        memScoped {
            if (tooltip.isNotEmpty()) {
                val wide = allocArray<UShortVar>(tooltip.length + 1)
                for (i in tooltip.indices) wide[i] = tooltip[i].code.toUShort()
                wide[tooltip.length] = 0u
                (vtable + 19)!!.pointed.value?.reinterpret<WinSessionComSetTooltipFn>()?.invoke(comPtr, hwnd, wide)
            }
            (vtable + 10)!!.pointed.value?.reinterpret<WinSessionComSetProgressStateFn>()?.invoke(comPtr, hwnd, stateFlag)
            if (durationSec > 0.0) {
                val posMs = (positionSec * 1000.0).roundToLong().coerceAtLeast(0L).toULong()
                val durMs = (durationSec * 1000.0).roundToLong().coerceAtLeast(1L).toULong()
                (vtable + 9)!!.pointed.value?.reinterpret<WinSessionComSetProgressValueFn>()?.invoke(comPtr, hwnd, posMs, durMs)
            }
        }
    }

    actual fun dispose() {
        if (disposed) return
        disposed = true
        activeWinMediaSessionBridges.remove(this)
        syncKeyboardHook()
        val comPtr = taskbarList3Ptr
        taskbarList3Ptr = null
        if (comPtr != null) {
            val vtable = comPtr.reinterpret<CPointerVar<CPointerVar<COpaque>>>().pointed.value
            val releaseFn = vtable?.let { (it + 2)?.pointed?.value?.reinterpret<WinSessionComReleaseFn>() }
            releaseFn?.invoke(comPtr)
        }
        onPlay = null
        onPause = null
        onNext = null
        onPrevious = null
        onSeekTo = null
        metadata = null
    }
}
