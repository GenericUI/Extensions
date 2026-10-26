@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.mediacontrols.windows

import com.nexustools.genericui.ext.mediacontrols.DefaultNoOpMediaControlsNativeImpl
import com.nexustools.genericui.ext.mediacontrols.MediaControlAction
import com.nexustools.genericui.ext.mediacontrols.MediaControlButtonConfig
import com.nexustools.genericui.ext.mediacontrols.MediaControlEvent
import com.nexustools.genericui.ext.mediacontrols.MediaControlEventSource
import com.nexustools.genericui.ext.mediacontrols.MediaControlMetadata
import com.nexustools.genericui.ext.mediacontrols.MediaControlsCapabilities
import com.nexustools.genericui.ext.mediacontrols.MediaPlaybackStatus
import com.nexustools.genericui.window.IWindow
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaque
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.plus
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toLong
import kotlinx.cinterop.value
import platform.windows.CallNextHookEx
import platform.windows.CallWindowProcW
import platform.windows.DWORDVar
import platform.windows.GWLP_WNDPROC
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
import platform.windows.RegisterWindowMessageW
import platform.windows.SetWindowLongPtrW
import platform.windows.SetWindowsHookExW
import platform.windows.UINT
import platform.windows.ULONG
import platform.windows.ULONGLONG
import platform.windows.UnhookWindowsHookEx
import platform.windows.WH_KEYBOARD_LL
import platform.windows.WM_COMMAND
import platform.windows.WM_KEYDOWN
import platform.windows.WM_SYSKEYDOWN
import platform.windows.WNDPROC
import platform.windows.WPARAM

private typealias OleCoInitializeExFn = CFunction<(COpaquePointer?, UInt) -> HRESULT>
private typealias OleCoCreateInstanceFn = CFunction<
    (COpaquePointer?, COpaquePointer?, UInt, COpaquePointer?, CPointer<COpaquePointerVar>?) -> HRESULT
>

private typealias ComReleaseFn = CFunction<(COpaquePointer?) -> ULONG>
private typealias ComHrInitFn = CFunction<(COpaquePointer?) -> HRESULT>
private typealias ComSetProgressValueFn = CFunction<(COpaquePointer?, HWND?, ULONGLONG, ULONGLONG) -> HRESULT>
private typealias ComSetProgressStateFn = CFunction<(COpaquePointer?, HWND?, Int) -> HRESULT>
private typealias ComThumbBarButtonsFn = CFunction<(COpaquePointer?, HWND?, UINT, COpaquePointer?) -> HRESULT>
private typealias ComSetThumbnailTooltipFn = CFunction<(COpaquePointer?, HWND?, CPointer<UShortVar>?) -> HRESULT>

private val activeWindowsMediaControlsInstances = mutableListOf<WindowsMediaControlsImpl>()
private var sharedKeyboardHookHandle: HHOOK? = null
private var subclassedHwnd: HWND? = null
private var originalWndProcPtr: Long = 0L
private var taskbarButtonCreatedMsgId: UInt = 0u

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun win32LowLevelKeyboardHookProc(nCode: Int, wParam: WPARAM, lParam: LPARAM): LRESULT {
    if (nCode >= 0 && (wParam.toInt() == WM_KEYDOWN || wParam.toInt() == WM_SYSKEYDOWN)) {
        val kbStruct = lParam.toCPointer<KBDLLHOOKSTRUCT>()
        if (kbStruct != null) {
            val vkCode = kbStruct.pointed.vkCode.toInt()
            val snapshot = activeWindowsMediaControlsInstances.toList()
            for (instance in snapshot) {
                instance.handleVirtualKey(vkCode)
            }
        }
    }
    return CallNextHookEx(sharedKeyboardHookHandle, nCode, wParam, lParam)
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun win32SubclassedMediaWndProc(hwnd: HWND?, msg: UINT, wParam: WPARAM, lParam: LPARAM): LRESULT {
    val msgInt = msg.toInt()
    if (taskbarButtonCreatedMsgId != 0u && msg == taskbarButtonCreatedMsgId) {
        val snapshot = activeWindowsMediaControlsInstances.toList()
        for (instance in snapshot) {
            instance.onTaskbarButtonCreated(hwnd)
        }
    } else if (msgInt == WindowsMediaControlsImpl.WM_APPCOMMAND) {
        val appCmd = ((lParam ushr 16) and 0x0FFFL).toInt()
        val snapshot = activeWindowsMediaControlsInstances.toList()
        var handled = false
        for (instance in snapshot) {
            if (instance.handleAppCommand(appCmd)) {
                handled = true
            }
        }
        if (handled) {
            return 1L
        }
    } else if (msgInt == WM_COMMAND) {
        val notifyCode = ((wParam.toLong() ushr 16) and 0xFFFFL).toInt()
        val buttonId = (wParam.toLong() and 0xFFFFL).toInt()
        if (notifyCode == WindowsMediaControlsImpl.THBN_CLICKED) {
            val snapshot = activeWindowsMediaControlsInstances.toList()
            for (instance in snapshot) {
                instance.handleTaskbarButtonClick(buttonId)
            }
        }
    }
    val prevProc = originalWndProcPtr.toCPointer<CFunction<(HWND?, UINT, WPARAM, LPARAM) -> LRESULT>>()
    return if (prevProc != null) {
        CallWindowProcW(prevProc, hwnd, msg, wParam, lParam)
    } else {
        0L
    }
}

/**
 * Windows (`mingwX64`) implementation of [com.nexustools.genericui.ext.mediacontrols.MediaControlsNativeImpl].
 *
 * Hooks hardware multimedia keys via `SetWindowsHookExW(WH_KEYBOARD_LL, ...)` and `WM_APPCOMMAND`,
 * and manages Windows Taskbar thumbnail preview transport buttons (`ITaskbarList3::ThumbBarAddButtons`
 * and `ThumbBarUpdateButtons`) for Previous (`1001`), Play/Pause (`1002`), Next (`1003`), and Stop (`1004`).
 */
class WindowsMediaControlsImpl : DefaultNoOpMediaControlsNativeImpl(
    capabilities = MediaControlsCapabilities(
        supportsSystemTransportControls = true,
        supportsTaskbarThumbnailButtons = true,
        supportsKeyboardMediaKeyHook = true
    )
) {
    companion object {
        const val VK_VOLUME_MUTE: Int = 0xAD
        const val VK_VOLUME_DOWN: Int = 0xAE
        const val VK_VOLUME_UP: Int = 0xAF
        const val VK_MEDIA_NEXT_TRACK: Int = 0xB0
        const val VK_MEDIA_PREV_TRACK: Int = 0xB1
        const val VK_MEDIA_STOP: Int = 0xB2
        const val VK_MEDIA_PLAY_PAUSE: Int = 0xB3

        const val WM_APPCOMMAND: Int = 0x0319
        const val APPCOMMAND_VOLUME_MUTE: Int = 8
        const val APPCOMMAND_VOLUME_DOWN: Int = 9
        const val APPCOMMAND_VOLUME_UP: Int = 10
        const val APPCOMMAND_MEDIA_NEXTTRACK: Int = 11
        const val APPCOMMAND_MEDIA_PREVIOUSTRACK: Int = 12
        const val APPCOMMAND_MEDIA_STOP: Int = 13
        const val APPCOMMAND_MEDIA_PLAY_PAUSE: Int = 14
        const val APPCOMMAND_MEDIA_PLAY: Int = 46
        const val APPCOMMAND_MEDIA_PAUSE: Int = 47
        const val APPCOMMAND_MEDIA_FAST_FORWARD: Int = 49
        const val APPCOMMAND_MEDIA_REWIND: Int = 50

        const val THBN_CLICKED: Int = 0x1800
        const val THUMB_BUTTON_PREVIOUS_ID: Int = 1001
        const val THUMB_BUTTON_PLAY_PAUSE_ID: Int = 1002
        const val THUMB_BUTTON_NEXT_ID: Int = 1003
        const val THUMB_BUTTON_STOP_ID: Int = 1004

        private const val THB_TOOLTIP: Int = 0x0004
        private const val THB_FLAGS: Int = 0x0008

        const val THBF_ENABLED: Int = 0x0000
        const val THBF_DISABLED: Int = 0x0001
        const val THBF_HIDDEN: Int = 0x0008

        private const val TBPF_NOPROGRESS: Int = 0x0000
        private const val TBPF_INDETERMINATE: Int = 0x0001
        private const val TBPF_NORMAL: Int = 0x0002
        private const val TBPF_PAUSED: Int = 0x0008

        private const val COINIT_APARTMENTTHREADED: UInt = 0x2u
        private const val CLSCTX_INPROC_SERVER: UInt = 0x1u
        private const val THUMBBUTTON_SIZE_X64: Int = 552
    }

    private var taskbarList3Ptr: COpaquePointer? = null

    var targetHwnd: HWND? = null

    var taskbarButtonsAdded: Boolean = false
        private set

    var isLowLevelKeyboardHookInstalled: Boolean = false
        private set

    val taskbarButtonCreatedMessageId: UInt
        get() = taskbarButtonCreatedMsgId

    private fun resolveActiveProcessHwnd(): HWND? {
        targetHwnd?.let { return it }
        val active = GetActiveWindow() ?: return null
        return memScoped {
            val pidVar = alloc<DWORDVar>()
            GetWindowThreadProcessId(active, pidVar.ptr)
            if (pidVar.value == GetCurrentProcessId()) active else null
        }
    }

    override fun bindWindow(window: IWindow?) {
        super.bindWindow(window)
        if (isActive()) {
            ensureWin32Bindings()
            syncTaskbarButtons()
        }
    }

    override fun setActive(active: Boolean) {
        val wasActive = isActive()
        super.setActive(active)
        if (active && !wasActive) {
            if (this !in activeWindowsMediaControlsInstances) {
                activeWindowsMediaControlsInstances.add(this)
            }
            ensureWin32Bindings()
            syncKeyboardHookState()
            syncTaskbarButtons()
        } else if (!active && wasActive) {
            activeWindowsMediaControlsInstances.remove(this)
            syncKeyboardHookState()
            syncTaskbarButtons()
        }
    }

    override fun setKeyboardMediaKeysEnabled(enabled: Boolean) {
        super.setKeyboardMediaKeysEnabled(enabled)
        syncKeyboardHookState()
    }

    override fun setTaskbarPreviewControlsEnabled(enabled: Boolean) {
        super.setTaskbarPreviewControlsEnabled(enabled)
        syncTaskbarButtons()
    }

    override fun updateMetadata(metadata: MediaControlMetadata) {
        super.updateMetadata(metadata)
        if (isActive()) {
            syncTaskbarTooltipAndProgress()
        }
    }

    override fun updatePlaybackStatus(
        status: MediaPlaybackStatus,
        positionMs: Long,
        durationMs: Long,
        playbackRate: Float
    ) {
        super.updatePlaybackStatus(status, positionMs, durationMs, playbackRate)
        if (isActive()) {
            syncTaskbarButtons()
            syncTaskbarTooltipAndProgress()
        }
    }

    override fun updateButtonConfig(config: MediaControlButtonConfig) {
        super.updateButtonConfig(config)
        if (isActive()) {
            syncTaskbarButtons()
        }
    }

    fun handleVirtualKey(vkCode: Int): Boolean {
        if (!isActive() || !keyboardMediaKeysEnabled) return false
        val action = when (vkCode) {
            VK_MEDIA_PLAY_PAUSE -> MediaControlAction.PLAY_PAUSE
            VK_MEDIA_STOP -> MediaControlAction.STOP
            VK_MEDIA_NEXT_TRACK -> MediaControlAction.NEXT
            VK_MEDIA_PREV_TRACK -> MediaControlAction.PREVIOUS
            VK_VOLUME_MUTE -> MediaControlAction.MUTE_TOGGLE
            VK_VOLUME_DOWN -> MediaControlAction.VOLUME_DOWN
            VK_VOLUME_UP -> MediaControlAction.VOLUME_UP
            else -> return false
        }
        listener?.onMediaControlEvent(
            MediaControlEvent(
                action = action,
                source = MediaControlEventSource.KEYBOARD_MEDIA_KEY
            )
        )
        return true
    }

    fun handleAppCommand(appCommand: Int): Boolean {
        if (!isActive()) return false
        val action = when (appCommand) {
            APPCOMMAND_MEDIA_PLAY_PAUSE -> MediaControlAction.PLAY_PAUSE
            APPCOMMAND_MEDIA_PLAY -> MediaControlAction.PLAY
            APPCOMMAND_MEDIA_PAUSE -> MediaControlAction.PAUSE
            APPCOMMAND_MEDIA_STOP -> MediaControlAction.STOP
            APPCOMMAND_MEDIA_NEXTTRACK -> MediaControlAction.NEXT
            APPCOMMAND_MEDIA_PREVIOUSTRACK -> MediaControlAction.PREVIOUS
            APPCOMMAND_MEDIA_FAST_FORWARD -> MediaControlAction.FAST_FORWARD
            APPCOMMAND_MEDIA_REWIND -> MediaControlAction.REWIND
            APPCOMMAND_VOLUME_MUTE -> MediaControlAction.MUTE_TOGGLE
            APPCOMMAND_VOLUME_DOWN -> MediaControlAction.VOLUME_DOWN
            APPCOMMAND_VOLUME_UP -> MediaControlAction.VOLUME_UP
            else -> return false
        }
        listener?.onMediaControlEvent(
            MediaControlEvent(
                action = action,
                source = MediaControlEventSource.SYSTEM_TRANSPORT
            )
        )
        return true
    }

    fun handleTaskbarButtonClick(buttonId: Int): Boolean {
        if (!isActive() || !taskbarPreviewControlsEnabled) return false
        val cfg = currentButtonConfig
        val action = when (buttonId) {
            THUMB_BUTTON_PREVIOUS_ID -> if (cfg.previousEnabled) MediaControlAction.PREVIOUS else return false
            THUMB_BUTTON_PLAY_PAUSE_ID -> if (cfg.playPauseEnabled) {
                if (currentPlaybackStatus == MediaPlaybackStatus.PLAYING) {
                    MediaControlAction.PAUSE
                } else {
                    MediaControlAction.PLAY
                }
            } else {
                return false
            }
            THUMB_BUTTON_NEXT_ID -> if (cfg.nextEnabled) MediaControlAction.NEXT else return false
            THUMB_BUTTON_STOP_ID -> if (cfg.stopEnabled) MediaControlAction.STOP else return false
            else -> return false
        }
        listener?.onMediaControlEvent(
            MediaControlEvent(
                action = action,
                source = MediaControlEventSource.TASKBAR_THUMBNAIL_TOOLBAR
            )
        )
        return true
    }

    internal fun onTaskbarButtonCreated(hwnd: HWND?) {
        taskbarButtonsAdded = false
        if (isActive() && hwnd != null) {
            ensureTaskbarListComInitialized()
            syncTaskbarButtons()
            syncTaskbarTooltipAndProgress()
        }
    }

    /**
     * Returns the current resolved state of the 4 Windows Taskbar thumbnail buttons
     * (`id`, `tooltip`, `flags`) for deterministic inspection and testing.
     */
    fun resolveTaskbarButtonsSnapshot(): List<Triple<Int, String, Int>> {
        val enabled = isActive() && taskbarPreviewControlsEnabled
        val cfg = currentButtonConfig
        val isPlaying = currentPlaybackStatus == MediaPlaybackStatus.PLAYING

        val prevFlags = when {
            !enabled -> THBF_HIDDEN or THBF_DISABLED
            cfg.previousEnabled -> THBF_ENABLED
            else -> THBF_DISABLED
        }
        val playPauseFlags = when {
            !enabled -> THBF_HIDDEN or THBF_DISABLED
            cfg.playPauseEnabled -> THBF_ENABLED
            else -> THBF_DISABLED
        }
        val playPauseTip = if (isPlaying) cfg.pauseTooltip else cfg.playTooltip
        val nextFlags = when {
            !enabled -> THBF_HIDDEN or THBF_DISABLED
            cfg.nextEnabled -> THBF_ENABLED
            else -> THBF_DISABLED
        }
        val stopFlags = when {
            !enabled || !cfg.stopEnabled -> THBF_HIDDEN or THBF_DISABLED
            else -> THBF_ENABLED
        }

        return listOf(
            Triple(THUMB_BUTTON_PREVIOUS_ID, cfg.previousTooltip, prevFlags),
            Triple(THUMB_BUTTON_PLAY_PAUSE_ID, playPauseTip, playPauseFlags),
            Triple(THUMB_BUTTON_NEXT_ID, cfg.nextTooltip, nextFlags),
            Triple(THUMB_BUTTON_STOP_ID, cfg.stopTooltip, stopFlags)
        )
    }

    private fun ensureWin32Bindings() {
        if (taskbarButtonCreatedMsgId == 0u) {
            taskbarButtonCreatedMsgId = RegisterWindowMessageW("TaskbarButtonCreated")
        }
        val hwnd = resolveActiveProcessHwnd()
        if (hwnd != null && subclassedHwnd != hwnd) {
            val newProc: WNDPROC = staticCFunction(::win32SubclassedMediaWndProc)
            val prev = SetWindowLongPtrW(hwnd, GWLP_WNDPROC, newProc.toLong())
            if (prev != 0L) {
                originalWndProcPtr = prev
                subclassedHwnd = hwnd
            }
        }
        if (hwnd != null) {
            ensureTaskbarListComInitialized()
        }
    }

    private fun syncKeyboardHookState() {
        val anyNeedsHook = activeWindowsMediaControlsInstances.any {
            it.isActive() && it.keyboardMediaKeysEnabled
        }
        val canInstallLiveHook = resolveActiveProcessHwnd() != null
        if (anyNeedsHook && canInstallLiveHook) {
            if (sharedKeyboardHookHandle == null) {
                sharedKeyboardHookHandle = SetWindowsHookExW(
                    WH_KEYBOARD_LL,
                    staticCFunction(::win32LowLevelKeyboardHookProc),
                    GetModuleHandleW(null),
                    0u
                )
            }
            isLowLevelKeyboardHookInstalled = sharedKeyboardHookHandle != null
        } else {
            if (!anyNeedsHook && sharedKeyboardHookHandle != null) {
                UnhookWindowsHookEx(sharedKeyboardHookHandle)
                sharedKeyboardHookHandle = null
            }
            isLowLevelKeyboardHookInstalled = anyNeedsHook && sharedKeyboardHookHandle != null
        }
    }

    private fun writeGuidBytes(
        dst: CPointer<ByteVar>,
        data1: UInt,
        data2: UShort,
        data3: UShort,
        b0: Int,
        b1: Int,
        b2: Int,
        b3: Int,
        b4: Int,
        b5: Int,
        b6: Int,
        b7: Int
    ) {
        dst.reinterpret<UIntVar>().pointed.value = data1
        (dst + 4)!!.reinterpret<UShortVar>().pointed.value = data2
        (dst + 6)!!.reinterpret<UShortVar>().pointed.value = data3
        dst[8] = b0.toByte()
        dst[9] = b1.toByte()
        dst[10] = b2.toByte()
        dst[11] = b3.toByte()
        dst[12] = b4.toByte()
        dst[13] = b5.toByte()
        dst[14] = b6.toByte()
        dst[15] = b7.toByte()
    }

    private fun ensureTaskbarListComInitialized() {
        if (taskbarList3Ptr != null) return
        val ole32 = LoadLibraryW("ole32.dll") ?: return
        val coInitProc = GetProcAddress(ole32, "CoInitializeEx")
        val coCreateProc = GetProcAddress(ole32, "CoCreateInstance") ?: return
        val coInitFn = coInitProc?.reinterpret<OleCoInitializeExFn>()
        val coCreateFn = coCreateProc.reinterpret<OleCoCreateInstanceFn>()

        memScoped {
            coInitFn?.invoke(null, COINIT_APARTMENTTHREADED)
            // CLSID_TaskbarList: {56FDF344-FD6D-11d0-958A-006097C9A090}
            val clsid = allocArray<ByteVar>(16)
            writeGuidBytes(
                clsid,
                0x56FDF344u,
                0xFD6Du,
                0x11D0u,
                0x95,
                0x8A,
                0x00,
                0x60,
                0x97,
                0xC9,
                0xA0,
                0x90
            )

            // IID_ITaskbarList3: {EA1AFB91-9E28-4B86-90E9-9E9F8A5EEFAF}
            val iid = allocArray<ByteVar>(16)
            writeGuidBytes(
                iid,
                0xEA1AFB91u,
                0x9E28u,
                0x4B86u,
                0x90,
                0xE9,
                0x9E,
                0x9F,
                0x8A,
                0x5E,
                0xEF,
                0xAF
            )

            val outPtr = alloc<COpaquePointerVar>()
            val hr = coCreateFn(clsid, null, CLSCTX_INPROC_SERVER, iid, outPtr.ptr)
            val created = outPtr.value
            if (hr >= 0 && created != null) {
                val vtable = created.reinterpret<CPointerVar<CPointerVar<COpaque>>>().pointed.value
                if (vtable != null) {
                    val hrInitFn = (vtable + 3)!!.pointed.value?.reinterpret<ComHrInitFn>()
                    val initRes = hrInitFn?.invoke(created) ?: -1
                    if (initRes >= 0) {
                        taskbarList3Ptr = created
                    } else {
                        val releaseFn = (vtable + 2)!!.pointed.value?.reinterpret<ComReleaseFn>()
                        releaseFn?.invoke(created)
                    }
                }
            }
        }
    }

    private fun syncTaskbarButtons() {
        val hwnd = resolveActiveProcessHwnd() ?: return
        ensureTaskbarListComInitialized()
        val comPtr = taskbarList3Ptr ?: return
        val vtable = comPtr.reinterpret<CPointerVar<CPointerVar<COpaque>>>().pointed.value ?: return
        val buttons = resolveTaskbarButtonsSnapshot()

        memScoped {
            val rawBuffer = allocArray<ByteVar>(buttons.size * THUMBBUTTON_SIZE_X64)
            for ((index, entry) in buttons.withIndex()) {
                val base = (rawBuffer + (index * THUMBBUTTON_SIZE_X64))!!
                val dwMaskPtr = base.reinterpret<UIntVar>()
                dwMaskPtr.pointed.value = (THB_TOOLTIP or THB_FLAGS).toUInt()

                val iIdPtr = (base + 4)!!.reinterpret<UIntVar>()
                iIdPtr.pointed.value = entry.first.toUInt()

                val iBitmapPtr = (base + 8)!!.reinterpret<UIntVar>()
                iBitmapPtr.pointed.value = 0u

                val hIconPtr = (base + 16)!!.reinterpret<LongVar>()
                hIconPtr.pointed.value = 0L

                val szTipPtr = (base + 24)!!.reinterpret<UShortVar>()
                val tip = entry.second
                val maxChars = tip.length.coerceAtMost(259)
                for (cIdx in 0 until maxChars) {
                    szTipPtr[cIdx] = tip[cIdx].code.toUShort()
                }
                szTipPtr[maxChars] = 0u

                val dwFlagsPtr = (base + 544)!!.reinterpret<IntVar>()
                dwFlagsPtr.pointed.value = entry.third
            }

            val slotIndex = if (!taskbarButtonsAdded) 15 else 16
            val thumbFn = (vtable + slotIndex)!!.pointed.value?.reinterpret<ComThumbBarButtonsFn>()
            val hr = thumbFn?.invoke(comPtr, hwnd, buttons.size.convert(), rawBuffer) ?: -1
            if (hr >= 0 && !taskbarButtonsAdded) {
                taskbarButtonsAdded = true
            }
        }
    }

    private fun syncTaskbarTooltipAndProgress() {
        val hwnd = resolveActiveProcessHwnd() ?: return
        val comPtr = taskbarList3Ptr ?: return
        val vtable = comPtr.reinterpret<CPointerVar<CPointerVar<COpaque>>>().pointed.value ?: return

        memScoped {
            val meta = currentMetadata
            val tooltipText = buildString {
                if (meta.title.isNotBlank()) append(meta.title)
                if (meta.artist.isNotBlank()) {
                    if (isNotEmpty()) append(" - ")
                    append(meta.artist)
                }
            }
            val tipWide = allocArray<UShortVar>(tooltipText.length + 1)
            for (i in tooltipText.indices) {
                tipWide[i] = tooltipText[i].code.toUShort()
            }
            tipWide[tooltipText.length] = 0u

            val setTooltipFn = (vtable + 19)!!.pointed.value?.reinterpret<ComSetThumbnailTooltipFn>()
            setTooltipFn?.invoke(comPtr, hwnd, if (tooltipText.isNotEmpty()) tipWide else null)

            val setProgressStateFn = (vtable + 10)!!.pointed.value?.reinterpret<ComSetProgressStateFn>()
            val setProgressValueFn = (vtable + 9)!!.pointed.value?.reinterpret<ComSetProgressValueFn>()
            when (currentPlaybackStatus) {
                MediaPlaybackStatus.STOPPED -> {
                    setProgressStateFn?.invoke(comPtr, hwnd, TBPF_NOPROGRESS)
                }
                MediaPlaybackStatus.LOADING -> {
                    setProgressStateFn?.invoke(comPtr, hwnd, TBPF_INDETERMINATE)
                }
                MediaPlaybackStatus.PLAYING -> {
                    if (currentDurationMs > 0L) {
                        setProgressStateFn?.invoke(comPtr, hwnd, TBPF_NORMAL)
                        setProgressValueFn?.invoke(
                            comPtr,
                            hwnd,
                            currentPositionMs.coerceAtLeast(0L).toULong(),
                            currentDurationMs.coerceAtLeast(1L).toULong()
                        )
                    } else {
                        setProgressStateFn?.invoke(comPtr, hwnd, TBPF_NOPROGRESS)
                    }
                }
                MediaPlaybackStatus.PAUSED -> {
                    if (currentDurationMs > 0L) {
                        setProgressStateFn?.invoke(comPtr, hwnd, TBPF_PAUSED)
                        setProgressValueFn?.invoke(
                            comPtr,
                            hwnd,
                            currentPositionMs.coerceAtLeast(0L).toULong(),
                            currentDurationMs.coerceAtLeast(1L).toULong()
                        )
                    } else {
                        setProgressStateFn?.invoke(comPtr, hwnd, TBPF_NOPROGRESS)
                    }
                }
            }
        }
    }

    override fun dispose() {
        activeWindowsMediaControlsInstances.remove(this)
        syncKeyboardHookState()
        val comPtr = taskbarList3Ptr
        taskbarList3Ptr = null
        taskbarButtonsAdded = false
        if (comPtr != null) {
            val vtable = comPtr.reinterpret<CPointerVar<CPointerVar<COpaque>>>().pointed.value
            val releaseFn = vtable?.let { (it + 2)?.pointed?.value?.reinterpret<ComReleaseFn>() }
            releaseFn?.invoke(comPtr)
        }
        super.dispose()
    }
}
