@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.gamepad

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKStringFromUtf16
import kotlinx.cinterop.value
import platform.windows.CloseHandle
import platform.windows.CreateFileW
import platform.windows.ERROR_SUCCESS
import platform.windows.FILE_SHARE_READ
import platform.windows.FILE_SHARE_WRITE
import platform.windows.GENERIC_READ
import platform.windows.GENERIC_WRITE
import platform.windows.GetProcAddress
import platform.windows.GetRawInputDeviceInfoW
import platform.windows.GetRawInputDeviceList
import platform.windows.HANDLE
import platform.windows.HINSTANCE__
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.JOYINFOEX
import platform.windows.LoadLibraryA
import platform.windows.OPEN_EXISTING
import platform.windows.RIDI_DEVICEINFO
import platform.windows.RIDI_DEVICENAME
import platform.windows.RID_DEVICE_INFO
import platform.windows.RIM_TYPEHID
import platform.windows.WriteFile
import platform.windows.tagJOYCAPSW
import platform.windows.tagRAWINPUTDEVICELIST
import kotlin.experimental.and

private const val JOY_RETURNALL_FLAGS: UInt = 0x000000FFu

private typealias XInputGetStateFn = CFunction<(UInt, CPointer<ByteVar>) -> UInt>
private typealias XInputSetStateFn = CFunction<(UInt, CPointer<ByteVar>) -> UInt>
private typealias HidSetOutputReportFn = CFunction<(HANDLE?, CPointer<ByteVar>, UInt) -> Int>
private typealias JoyGetPosExFn = CFunction<(UInt, CPointer<JOYINFOEX>) -> UInt>
private typealias JoyGetDevCapsWFn = CFunction<(ULong, CPointer<tagJOYCAPSW>, UInt) -> UInt>

/**
 * Windows (`mingwMain`) [PlatformGamepadBackend] combining:
 * 1. Dynamic `XInput` (`xinput1_4.dll` / `xinput1_3.dll` / `xinput9_1_0.dll`) for Xbox controllers
 *    and Dual-motor grip rumble.
 * 2. WinMM `joyGetPosEx` / `joyGetDevCapsW` for DirectInput controllers (DualShock 4, DualSense,
 *    Switch Pro, 8BitDo, Logitech).
 * 3. Windows RawInput + HID (`WriteFile` / `hid.dll` `HidD_SetOutputReport`) for DualSense lightbar,
 *    mute LED, player LEDs, and adaptive triggers, plus Switch Pro HD Rumble / player LED / Home LED.
 */
actual object PlatformGamepadBackend {
    actual val isHardwarePollingSupported: Boolean = true

    private var xinputModule: CPointer<HINSTANCE__>? = null
    private var xinputGetStateFn: CPointer<XInputGetStateFn>? = null
    private var xinputSetStateFn: CPointer<XInputSetStateFn>? = null
    private var xinputInitialized: Boolean = false

    private var hidModule: CPointer<HINSTANCE__>? = null
    private var hidSetOutputReportFn: CPointer<HidSetOutputReportFn>? = null
    private var hidInitialized: Boolean = false

    private var winmmModule: CPointer<HINSTANCE__>? = null
    private var joyGetPosExFn: CPointer<JoyGetPosExFn>? = null
    private var joyGetDevCapsWFn: CPointer<JoyGetDevCapsWFn>? = null
    private var winmmInitialized: Boolean = false

    private fun ensureWinMmLoaded() {
        if (winmmInitialized) return
        winmmInitialized = true
        winmmModule = LoadLibraryA("winmm.dll")
        val mod = winmmModule ?: return
        GetProcAddress(mod, "joyGetPosEx")?.let {
            joyGetPosExFn = interpretCPointer<JoyGetPosExFn>(it.rawValue)
        }
        GetProcAddress(mod, "joyGetDevCapsW")?.let {
            joyGetDevCapsWFn = interpretCPointer<JoyGetDevCapsWFn>(it.rawValue)
        }
    }

    private val slotVendors = IntArray(GamepadManager.MAX_PLAYERS)
    private val slotProducts = IntArray(GamepadManager.MAX_PLAYERS)
    private var switchPacketCounter: Byte = 0

    private fun ensureXInputLoaded() {
        if (xinputInitialized) return
        xinputInitialized = true
        xinputModule = LoadLibraryA("xinput1_4.dll")
            ?: LoadLibraryA("xinput1_3.dll")
            ?: LoadLibraryA("xinput9_1_0.dll")
        val mod = xinputModule ?: return
        GetProcAddress(mod, "XInputGetState")?.let {
            xinputGetStateFn = interpretCPointer<XInputGetStateFn>(it.rawValue)
        }
        GetProcAddress(mod, "XInputSetState")?.let {
            xinputSetStateFn = interpretCPointer<XInputSetStateFn>(it.rawValue)
        }
    }

    private fun ensureHidLoaded() {
        if (hidInitialized) return
        hidInitialized = true
        hidModule = LoadLibraryA("hid.dll")
        val mod = hidModule ?: return
        GetProcAddress(mod, "HidD_SetOutputReport")?.let {
            hidSetOutputReportFn = interpretCPointer<HidSetOutputReportFn>(it.rawValue)
        }
    }

    actual fun pollControllers(
        maxPlayers: Int,
        previousSnapshots: List<GamepadFrameSnapshot>
    ): List<GamepadFrameSnapshot> {
        ensureXInputLoaded()
        val result = mutableListOf<GamepadFrameSnapshot>()
        val usedSlots = BooleanArray(maxPlayers)

        val getState = xinputGetStateFn
        if (getState != null) {
            memScoped {
                val stateBuf = allocArray<ByteVar>(16)
                for (slot in 0 until minOf(4, maxPlayers)) {
                    val rc = getState(slot.toUInt(), stateBuf)
                    if (rc != ERROR_SUCCESS.toUInt()) continue
                    usedSlots[slot] = true
                    slotVendors[slot] = GamepadLayoutMapper.VENDOR_MICROSOFT
                    slotProducts[slot] = 0x0B12

                    val wButtons = ((stateBuf[5].toInt() and 0xFF) shl 8) or (stateBuf[4].toInt() and 0xFF)
                    val lt = (stateBuf[6].toInt() and 0xFF) / 255f
                    val rt = (stateBuf[7].toInt() and 0xFF) / 255f
                    val lx = readLeShort(stateBuf, 8) / 32767f
                    val ly = readLeShort(stateBuf, 10) / 32767f
                    val rx = readLeShort(stateBuf, 12) / 32767f
                    val ry = readLeShort(stateBuf, 14) / 32767f

                    val buttons = BooleanArray(GamepadButton.STANDARD_BUTTON_COUNT)
                    buttons[GamepadButton.DPAD_UP.standardIndex] = (wButtons and 0x0001) != 0
                    buttons[GamepadButton.DPAD_DOWN.standardIndex] = (wButtons and 0x0002) != 0
                    buttons[GamepadButton.DPAD_LEFT.standardIndex] = (wButtons and 0x0004) != 0
                    buttons[GamepadButton.DPAD_RIGHT.standardIndex] = (wButtons and 0x0008) != 0
                    buttons[GamepadButton.MENU.standardIndex] = (wButtons and 0x0010) != 0
                    buttons[GamepadButton.VIEW.standardIndex] = (wButtons and 0x0020) != 0
                    buttons[GamepadButton.LEFT_STICK.standardIndex] = (wButtons and 0x0040) != 0
                    buttons[GamepadButton.RIGHT_STICK.standardIndex] = (wButtons and 0x0080) != 0
                    buttons[GamepadButton.LEFT_BUMPER.standardIndex] = (wButtons and 0x0100) != 0
                    buttons[GamepadButton.RIGHT_BUMPER.standardIndex] = (wButtons and 0x0200) != 0
                    buttons[GamepadButton.GUIDE.standardIndex] = (wButtons and 0x0400) != 0
                    buttons[GamepadButton.A.standardIndex] = (wButtons and 0x1000) != 0
                    buttons[GamepadButton.B.standardIndex] = (wButtons and 0x2000) != 0
                    buttons[GamepadButton.X.standardIndex] = (wButtons and 0x4000) != 0
                    buttons[GamepadButton.Y.standardIndex] = (wButtons and 0x8000) != 0

                    result.add(
                        GamepadLayoutMapper.mapRawInput(
                            playerIndex = slot,
                            buttons = buttons,
                            axes = floatArrayOf(lx, ly, rx, ry, lt, rt),
                            vendorId = GamepadLayoutMapper.VENDOR_MICROSOFT,
                            productId = 0x0B12,
                            deviceName = "Xbox XInput Controller #${slot + 1}",
                            deviceId = "xinput-$slot",
                            alreadyStandardLayout = true,
                            xboxFeatures = previousSnapshots.firstOrNull { it.playerIndex == slot }?.xbox
                        )
                    )
                }
            }
        }

        ensureWinMmLoaded()
        val getPosFn = joyGetPosExFn
        val getCapsFn = joyGetDevCapsWFn
        if (getPosFn != null) {
            memScoped {
                val info = alloc<JOYINFOEX>()
                val caps = alloc<tagJOYCAPSW>()
                info.dwSize = sizeOf<JOYINFOEX>().toUInt()
                info.dwFlags = JOY_RETURNALL_FLAGS
                val capsSize = sizeOf<tagJOYCAPSW>().toUInt()

                for (joyId in 0u until 16u) {
                    val targetSlot = (0 until maxPlayers).firstOrNull { !usedSlots[it] } ?: break
                    if (getPosFn(joyId, info.ptr) != 0u) continue

                    var vid = 0
                    var pid = 0
                    var devName = "WinMM Joystick #$joyId"
                    if (getCapsFn != null && getCapsFn(joyId.toULong(), caps.ptr, capsSize) == 0u) {
                        vid = caps.wMid.toInt()
                        pid = caps.wPid.toInt()
                        devName = caps.szPname.toKStringFromUtf16().ifEmpty { devName }
                    }
                    if (vid == GamepadLayoutMapper.VENDOR_MICROSOFT && joyId.toInt() in 0..3 && result.any { it.playerIndex == joyId.toInt() }) {
                        continue
                    }

                    usedSlots[targetSlot] = true
                    slotVendors[targetSlot] = vid
                    slotProducts[targetSlot] = pid

                    val rawBtns = info.dwButtons.toInt()
                    val buttons = BooleanArray(GamepadButton.STANDARD_BUTTON_COUNT) { b ->
                        (rawBtns and (1 shl b)) != 0
                    }
                    val axes = floatArrayOf(
                        normUIntAxis(info.dwXpos),
                        normUIntAxis(info.dwYpos),
                        normUIntAxis(info.dwZpos),
                        normUIntAxis(info.dwRpos),
                        normUIntAxis(info.dwUpos),
                        normUIntAxis(info.dwVpos)
                    )
                    val hatMask = if (info.dwPOV == 0xFFFFu) 0 else GamepadLayoutMapper.povAngleToHatMask(info.dwPOV.toInt())
                    val prev = previousSnapshots.firstOrNull { it.playerIndex == targetSlot }

                    result.add(
                        GamepadLayoutMapper.mapRawInput(
                            playerIndex = targetSlot,
                            buttons = buttons,
                            axes = axes,
                            hatMask = hatMask,
                            vendorId = vid,
                            productId = pid,
                            deviceName = devName,
                            deviceId = "winmm-$joyId",
                            invertRawY = true,
                            usePositionalSwitchLayout = prev?.switchFeatures?.usePositionalActionButtons ?: true,
                            playStationFeatures = prev?.playStation,
                            switchFeatures = prev?.switchFeatures
                        )
                    )
                }
            }
        }
        return result
    }

    actual fun setRumble(playerIndex: Int, request: GamepadRumbleRequest): Boolean {
        ensureXInputLoaded()
        val setState = xinputSetStateFn
        val clamped = request.clamped()
        if (setState != null && playerIndex in 0..3 && slotVendors[playerIndex] == GamepadLayoutMapper.VENDOR_MICROSOFT) {
            memScoped {
                val vib = allocArray<ByteVar>(4)
                val leftMotor = (clamped.lowFrequency * 65535f).toInt().coerceIn(0, 65535)
                val rightMotor = (clamped.highFrequency * 65535f).toInt().coerceIn(0, 65535)
                vib[0] = (leftMotor and 0xFF).toByte()
                vib[1] = ((leftMotor ushr 8) and 0xFF).toByte()
                vib[2] = (rightMotor and 0xFF).toByte()
                vib[3] = ((rightMotor ushr 8) and 0xFF).toByte()
                if (setState(playerIndex.toUInt(), vib) == ERROR_SUCCESS.toUInt()) return true
            }
        }
        val vid = slotVendors.getOrElse(playerIndex) { 0 }
        if (vid == GamepadLayoutMapper.VENDOR_SONY) {
            return writeDualSenseOutputReport(
                leftRumble = (clamped.lowFrequency * 255f).toInt(),
                rightRumble = (clamped.highFrequency * 255f).toInt()
            )
        }
        return false
    }

    actual fun setPlayStationLightbar(playerIndex: Int, r: Int, g: Int, b: Int): Boolean =
        writeDualSenseOutputReport(
            enableLightbar = true,
            lightbarR = r,
            lightbarG = g,
            lightbarB = b
        )

    actual fun setPlayStationPlayerLedMask(playerIndex: Int, mask: Int): Boolean =
        writeDualSenseOutputReport(
            enablePlayerLeds = true,
            playerLedMask = mask and 0x1F
        )

    actual fun setPlayStationMuteLed(playerIndex: Int, state: MuteLedState): Boolean =
        writeDualSenseOutputReport(
            enableMuteLed = true,
            muteLedByte = when (state) {
                MuteLedState.OFF -> 0
                MuteLedState.SOLID -> 1
                MuteLedState.BREATHING -> 2
            }
        )

    actual fun setPlayStationAdaptiveTrigger(
        playerIndex: Int,
        side: AdaptiveTriggerSide,
        effect: AdaptiveTriggerEffect
    ): Boolean = writeDualSenseOutputReport(
        rightTriggerEffect = if (side == AdaptiveTriggerSide.RIGHT || side == AdaptiveTriggerSide.BOTH) effect else null,
        leftTriggerEffect = if (side == AdaptiveTriggerSide.LEFT || side == AdaptiveTriggerSide.BOTH) effect else null
    )

    actual fun setSwitchHdRumble(playerIndex: Int, command: SwitchHdRumbleCommand): Boolean {
        val clamped = command.clamped()
        val packet = ByteArray(49)
        packet[0] = 0x10
        packet[1] = (switchPacketCounter++.toInt() and 0x0F).toByte()
        encodeSwitchRumbleHalf(packet, 2, clamped.leftHighFreqHz, clamped.leftHighAmp, clamped.leftLowFreqHz, clamped.leftLowAmp)
        encodeSwitchRumbleHalf(packet, 6, clamped.rightHighFreqHz, clamped.rightHighAmp, clamped.rightLowFreqHz, clamped.rightLowAmp)
        return writeHidOutputReport(GamepadLayoutMapper.VENDOR_NINTENDO, packet)
    }

    actual fun setSwitchPlayerLeds(playerIndex: Int, mask: Int): Boolean {
        val packet = ByteArray(49)
        packet[0] = 0x01
        packet[1] = (switchPacketCounter++.toInt() and 0x0F).toByte()
        packet[10] = 0x30
        packet[11] = (mask and 0x0F).toByte()
        return writeHidOutputReport(GamepadLayoutMapper.VENDOR_NINTENDO, packet)
    }

    actual fun setSwitchHomeLedPattern(playerIndex: Int, pattern: SwitchHomeLedPattern): Boolean {
        val p = pattern.clamped()
        val packet = ByteArray(49)
        packet[0] = 0x01
        packet[1] = (switchPacketCounter++.toInt() and 0x0F).toByte()
        packet[10] = 0x38
        val miniCycleDur = ((1f / p.pulseRateHz.coerceAtLeast(0.5f)) * 4f).toInt().coerceIn(0, 15)
        val ledLevel = (p.maxIntensity * 15f).toInt().coerceIn(0, 15)
        val startIntensity = (p.baseIntensity * 15f).toInt().coerceIn(0, 15)
        packet[11] = ((1 shl 4) or miniCycleDur).toByte()
        packet[12] = ((startIntensity shl 4) or p.cycleCount).toByte()
        packet[13] = ((ledLevel shl 4) or 0x04).toByte()
        return writeHidOutputReport(GamepadLayoutMapper.VENDOR_NINTENDO, packet)
    }

    private fun writeDualSenseOutputReport(
        leftRumble: Int? = null,
        rightRumble: Int? = null,
        enableMuteLed: Boolean = false,
        muteLedByte: Int = 0,
        enableLightbar: Boolean = false,
        lightbarR: Int = 0,
        lightbarG: Int = 0,
        lightbarB: Int = 0,
        enablePlayerLeds: Boolean = false,
        playerLedMask: Int = 0,
        leftTriggerEffect: AdaptiveTriggerEffect? = null,
        rightTriggerEffect: AdaptiveTriggerEffect? = null
    ): Boolean {
        val report = ByteArray(48)
        report[0] = 0x02
        var flags0 = 0
        var flags1 = 0
        if (leftRumble != null || rightRumble != null) {
            flags0 = flags0 or 0x03
            report[3] = (rightRumble ?: 0).coerceIn(0, 255).toByte()
            report[4] = (leftRumble ?: 0).coerceIn(0, 255).toByte()
        }
        if (rightTriggerEffect != null) {
            flags0 = flags0 or 0x04
            encodeDualSenseTriggerBlock(report, 11, rightTriggerEffect)
        }
        if (leftTriggerEffect != null) {
            flags0 = flags0 or 0x08
            encodeDualSenseTriggerBlock(report, 22, leftTriggerEffect)
        }
        if (enableMuteLed) {
            flags1 = flags1 or 0x01
            report[9] = muteLedByte.toByte()
        }
        if (enableLightbar) {
            flags1 = flags1 or 0x04
            report[45] = lightbarR.coerceIn(0, 255).toByte()
            report[46] = lightbarG.coerceIn(0, 255).toByte()
            report[47] = lightbarB.coerceIn(0, 255).toByte()
        }
        if (enablePlayerLeds) {
            flags1 = flags1 or 0x10
            report[44] = playerLedMask.toByte()
        }
        report[1] = flags0.toByte()
        report[2] = flags1.toByte()
        return writeHidOutputReport(GamepadLayoutMapper.VENDOR_SONY, report)
    }

    private fun encodeDualSenseTriggerBlock(buf: ByteArray, offset: Int, effect: AdaptiveTriggerEffect) {
        when (effect) {
            AdaptiveTriggerEffect.Off -> {
                buf[offset] = 0x05 // Off
            }
            is AdaptiveTriggerEffect.ContinuousResistance -> {
                buf[offset] = 0x01
                buf[offset + 1] = (effect.startPosition.coerceIn(0f, 1f) * 255f).toInt().toByte()
                buf[offset + 2] = (effect.force.coerceIn(0f, 1f) * 255f).toInt().toByte()
            }
            is AdaptiveTriggerEffect.SectionResistance -> {
                buf[offset] = 0x02
                buf[offset + 1] = (effect.startPosition.coerceIn(0f, 1f) * 255f).toInt().toByte()
                buf[offset + 2] = (effect.endPosition.coerceIn(0f, 1f) * 255f).toInt().toByte()
                buf[offset + 3] = (effect.force.coerceIn(0f, 1f) * 255f).toInt().toByte()
            }
            is AdaptiveTriggerEffect.VibrationPulse -> {
                buf[offset] = 0x06
                buf[offset + 1] = effect.frequencyHz.toInt().coerceIn(1, 255).toByte()
                buf[offset + 2] = (effect.amplitude.coerceIn(0f, 1f) * 255f).toInt().toByte()
                buf[offset + 3] = (effect.startPosition.coerceIn(0f, 1f) * 255f).toInt().toByte()
            }
        }
    }

    private fun encodeSwitchRumbleHalf(
        buf: ByteArray,
        offset: Int,
        highFreqHz: Float,
        highAmp: Float,
        lowFreqHz: Float,
        lowAmp: Float
    ) {
        val hfEnc = ((highFreqHz.coerceIn(40f, 1252f) - 40f) / 10f).toInt().coerceIn(0, 127)
        val haEnc = (highAmp.coerceIn(0f, 1f) * 100f).toInt().coerceIn(0, 100)
        val lfEnc = ((lowFreqHz.coerceIn(40f, 626f) - 40f) / 5f).toInt().coerceIn(0, 127)
        val laEnc = (lowAmp.coerceIn(0f, 1f) * 100f).toInt().coerceIn(0, 100)
        buf[offset] = (hfEnc and 0xFF).toByte()
        buf[offset + 1] = (haEnc and 0xFF).toByte()
        buf[offset + 2] = (lfEnc and 0xFF).toByte()
        buf[offset + 3] = (laEnc and 0xFF).toByte()
    }

    private fun writeHidOutputReport(targetVendorId: Int, report: ByteArray): Boolean {
        ensureHidLoaded()
        return memScoped {
            val countVar = alloc<UIntVar>()
            val entrySize = sizeOf<tagRAWINPUTDEVICELIST>().toUInt()
            if (GetRawInputDeviceList(null, countVar.ptr, entrySize) != 0u) return false
            val count = countVar.value.toInt()
            if (count <= 0) return false

            val list = allocArray<tagRAWINPUTDEVICELIST>(count)
            val actualCountVar = alloc<UIntVar>()
            actualCountVar.value = count.toUInt()
            val got = GetRawInputDeviceList(list, actualCountVar.ptr, entrySize)
            if (got == UInt.MAX_VALUE) return false

            val devInfo = alloc<RID_DEVICE_INFO>()
            val infoSize = alloc<UIntVar>()
            val nameBuf = allocArray<kotlinx.cinterop.UShortVar>(512)
            val nameLen = alloc<UIntVar>()

            for (i in 0 until got.toInt()) {
                val item = list[i]
                if (item.dwType != RIM_TYPEHID.toUInt()) continue
                devInfo.cbSize = sizeOf<RID_DEVICE_INFO>().toUInt()
                infoSize.value = devInfo.cbSize
                if (GetRawInputDeviceInfoW(item.hDevice, RIDI_DEVICEINFO.toUInt(), devInfo.ptr, infoSize.ptr) == UInt.MAX_VALUE) {
                    continue
                }
                if (devInfo.hid.dwVendorId.toInt() != targetVendorId) continue

                nameLen.value = 512u
                if (GetRawInputDeviceInfoW(item.hDevice, RIDI_DEVICENAME.toUInt(), nameBuf, nameLen.ptr) == UInt.MAX_VALUE) {
                    continue
                }
                val devicePath = nameBuf.toKStringFromUtf16()
                val handle = CreateFileW(
                    devicePath,
                    GENERIC_READ or GENERIC_WRITE.toUInt(),
                    (FILE_SHARE_READ or FILE_SHARE_WRITE).toUInt(),
                    null,
                    OPEN_EXISTING.toUInt(),
                    0u,
                    null
                )
                if (handle == null || handle == INVALID_HANDLE_VALUE) continue
                try {
                    val buf = allocArray<ByteVar>(report.size)
                    for (bIdx in report.indices) {
                        buf[bIdx] = report[bIdx]
                    }
                    val written = alloc<UIntVar>()
                    if (WriteFile(handle, buf, report.size.toUInt(), written.ptr, null) != 0) {
                        return true
                    }
                    val hidFn = hidSetOutputReportFn
                    if (hidFn != null && hidFn(handle, buf, report.size.toUInt()) != 0) {
                        return true
                    }
                } finally {
                    CloseHandle(handle)
                }
            }
            false
        }
    }

    private fun readLeShort(buf: CPointer<ByteVar>, offset: Int): Int {
        val lo = buf[offset].toInt() and 0xFF
        val hi = buf[offset + 1].toInt()
        return (hi shl 8) or lo
    }

    private fun normUIntAxis(raw: UInt): Float {
        val centered = (raw.toDouble() - 32767.5) / 32767.5
        return centered.toFloat().coerceIn(-1f, 1f)
    }
}
