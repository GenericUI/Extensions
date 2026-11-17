@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.gamepad

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import platform.posix.CLOCK_MONOTONIC
import platform.posix.O_NONBLOCK
import platform.posix.O_RDONLY
import platform.posix.O_RDWR
import platform.posix.O_WRONLY
import platform.posix.clock_gettime
import platform.posix.close
import platform.posix.closedir
import platform.posix.ioctl
import platform.posix.open
import platform.posix.opendir
import platform.posix.read
import platform.posix.readdir
import platform.posix.timespec
import platform.posix.write
import kotlinx.cinterop.alloc

/**
 * Linux (`linuxMain` — `linuxX64` & `linuxArm64`) [PlatformGamepadBackend] reading:
 * 1. `/dev/input/js[N]` joystick API + sysfs `device/id/{vendor,product}` + `name`.
 * 2. Paired `/dev/input/event[N]` `evdev` 6-axis IMU gyro/accel reports (`ABS_RX/RY/RZ`, `ABS_X/Y/Z`)
 *    fused through per-slot [SixAxisSensorFusion], plus `EVIOCSFF` force-feedback rumble.
 * 3. `/sys/class/leds/` sysfs attributes for PlayStation RGB lightbar, player LEDs, and mute LED.
 * 4. `/dev/hidraw[N]` raw HID output reports for DualSense adaptive triggers & Switch Pro HD rumble/LEDs.
 */
actual object PlatformGamepadBackend {
    actual val isHardwarePollingSupported: Boolean = true

    private class JsSlot(val slotIndex: Int) {
        var fd: Int = -1
        var eventFd: Int = -1
        var ffEffectId: Short = -1
        var vendorId: Int = 0
        var productId: Int = 0
        var deviceName: String = ""
        val buttons = BooleanArray(32)
        val axes = FloatArray(16)
        var imuGyroX: Float = 0f
        var imuGyroY: Float = 0f
        var imuGyroZ: Float = 0f
        var imuAccelX: Float = 0f
        var imuAccelY: Float = 1f
        var imuAccelZ: Float = 0f
        var hasImuData: Boolean = false
        var lastImuMs: Long = 0L
        var latestMotion: SixAxisMotionState = SixAxisMotionState.UNAVAILABLE
        val fusion: SixAxisSensorFusion = SixAxisSensorFusion()
    }

    private val slots = Array(GamepadManager.MAX_PLAYERS) { JsSlot(it) }
    private var switchPacketSeq: Byte = 0

    actual fun pollControllers(
        maxPlayers: Int,
        previousSnapshots: List<GamepadFrameSnapshot>
    ): List<GamepadFrameSnapshot> {
        val result = mutableListOf<GamepadFrameSnapshot>()
        val limit = minOf(maxPlayers, GamepadManager.MAX_PLAYERS)
        val evBuf = ByteArray(24)

        for (i in 0 until limit) {
            val s = slots[i]
            if (s.fd < 0) {
                val opened = open("/dev/input/js$i", O_RDONLY or O_NONBLOCK)
                if (opened < 0) continue
                s.fd = opened
                s.buttons.fill(false)
                s.axes.fill(0f)
                s.deviceName = queryJsDeviceName(opened).ifEmpty { readSysfsName(i) ?: "Linux Gamepad #$i" }
                s.vendorId = readSysfsHex("/sys/class/input/js$i/device/id/vendor")
                s.productId = readSysfsHex("/sys/class/input/js$i/device/id/product")
                if (s.eventFd < 0) {
                    s.eventFd = findMatchingEvdevFd(s.vendorId, s.productId)
                }
            }

            memScoped {
                val buf = allocArray<ByteVar>(8)
                while (true) {
                    val bytes = read(s.fd, buf, 8uL)
                    if (bytes < 0L) break
                    if (bytes == 0L) {
                        close(s.fd)
                        s.fd = -1
                        if (s.eventFd >= 0) {
                            close(s.eventFd)
                            s.eventFd = -1
                        }
                        break
                    }
                    if (bytes < 8L) break
                    val lo = buf[4].toInt() and 0xFF
                    val hi = buf[5].toInt()
                    val value = ((hi shl 8) or lo).toShort()
                    val type = buf[6].toInt() and 0x7F
                    val number = buf[7].toInt() and 0xFF
                    when (type) {
                        1 -> if (number in s.buttons.indices) s.buttons[number] = value.toInt() != 0
                        2 -> if (number in s.axes.indices) s.axes[number] = (value / 32767f).coerceIn(-1f, 1f)
                    }
                }
            }

            if (s.fd < 0) continue

            if (s.eventFd >= 0) {
                pollEvdevImu(s, evBuf)
            }

            val prev = previousSnapshots.firstOrNull { it.playerIndex == i }
            val hatMask = GamepadLayoutMapper.hatAxesToMask(s.axes[6], s.axes[7])
            result.add(
                GamepadLayoutMapper.mapRawInput(
                    playerIndex = i,
                    buttons = s.buttons,
                    axes = floatArrayOf(s.axes[0], s.axes[1], s.axes[2], s.axes[3], s.axes[4], s.axes[5]),
                    hatMask = hatMask,
                    vendorId = s.vendorId,
                    productId = s.productId,
                    deviceName = s.deviceName,
                    deviceId = "linux-js-$i",
                    invertRawY = true,
                    triggersAreSigned = true,
                    usePositionalSwitchLayout = prev?.switchFeatures?.usePositionalActionButtons ?: true,
                    motion = if (s.hasImuData) s.latestMotion else SixAxisMotionState.UNAVAILABLE,
                    playStationFeatures = prev?.playStation,
                    switchFeatures = prev?.switchFeatures
                )
            )
        }
        return result
    }

    actual fun setRumble(playerIndex: Int, request: GamepadRumbleRequest): Boolean {
        val clamped = request.clamped()
        val s = slots.getOrNull(playerIndex) ?: return false
        if (s.eventFd >= 0 && uploadAndPlayFfEffect(s, clamped)) {
            return true
        }
        if (s.vendorId == GamepadLayoutMapper.VENDOR_SONY) {
            return sendDualSenseHidReport(
                leftRumble = (clamped.lowFrequency * 255f).toInt(),
                rightRumble = (clamped.highFrequency * 255f).toInt()
            )
        }
        if (s.vendorId == GamepadLayoutMapper.VENDOR_NINTENDO) {
            return setSwitchHdRumble(
                playerIndex,
                SwitchHdRumbleCommand(
                    leftLowAmp = clamped.lowFrequency,
                    leftHighAmp = clamped.highFrequency,
                    rightLowAmp = clamped.lowFrequency,
                    rightHighAmp = clamped.highFrequency,
                    durationMs = clamped.durationMs
                )
            )
        }
        return false
    }

    actual fun setPlayStationLightbar(playerIndex: Int, r: Int, g: Int, b: Int): Boolean {
        val rgbWritten = writeFirstMatchingSysfsLed(
            dirFilter = "rgb:indicator",
            fileName = "multi_intensity",
            content = "${r.coerceIn(0, 255)} ${g.coerceIn(0, 255)} ${b.coerceIn(0, 255)}\n"
        ) || (writeFirstMatchingSysfsLed(":red", "brightness", "${r.coerceIn(0, 255)}\n") &&
            writeFirstMatchingSysfsLed(":green", "brightness", "${g.coerceIn(0, 255)}\n") &&
            writeFirstMatchingSysfsLed(":blue", "brightness", "${b.coerceIn(0, 255)}\n"))
        if (rgbWritten) return true
        return sendDualSenseHidReport(enableLightbar = true, r = r, g = g, b = b)
    }

    actual fun setPlayStationPlayerLedMask(playerIndex: Int, mask: Int): Boolean {
        var anySysfs = false
        for (ledIdx in 1..5) {
            val bit = if ((mask and (1 shl (ledIdx - 1))) != 0) "1\n" else "0\n"
            if (writeFirstMatchingSysfsLed("player-$ledIdx", "brightness", bit)) {
                anySysfs = true
            }
        }
        if (anySysfs) return true
        return sendDualSenseHidReport(enablePlayerLeds = true, playerLedMask = mask and 0x1F)
    }

    actual fun setPlayStationMuteLed(playerIndex: Int, state: MuteLedState): Boolean {
        val brightness = if (state == MuteLedState.OFF) "0\n" else "1\n"
        if (writeFirstMatchingSysfsLed("micmute", "brightness", brightness) ||
            writeFirstMatchingSysfsLed("mute", "brightness", brightness)
        ) {
            return true
        }
        val modeByte = when (state) {
            MuteLedState.OFF -> 0
            MuteLedState.SOLID -> 1
            MuteLedState.BREATHING -> 2
        }
        return sendDualSenseHidReport(enableMuteLed = true, muteLedByte = modeByte)
    }

    actual fun setPlayStationAdaptiveTrigger(
        playerIndex: Int,
        side: AdaptiveTriggerSide,
        effect: AdaptiveTriggerEffect
    ): Boolean = sendDualSenseHidReport(
        rightTriggerEffect = if (side == AdaptiveTriggerSide.RIGHT || side == AdaptiveTriggerSide.BOTH) effect else null,
        leftTriggerEffect = if (side == AdaptiveTriggerSide.LEFT || side == AdaptiveTriggerSide.BOTH) effect else null
    )

    actual fun setSwitchHdRumble(playerIndex: Int, command: SwitchHdRumbleCommand): Boolean {
        val clamped = command.clamped()
        val packet = ByteArray(49)
        packet[0] = 0x10
        packet[1] = (switchPacketSeq++.toInt() and 0x0F).toByte()
        encodeSwitchRumble(packet, 2, clamped.leftHighFreqHz, clamped.leftHighAmp, clamped.leftLowFreqHz, clamped.leftLowAmp)
        encodeSwitchRumble(packet, 6, clamped.rightHighFreqHz, clamped.rightHighAmp, clamped.rightLowFreqHz, clamped.rightLowAmp)
        return writeFirstHidrawMatching(GamepadLayoutMapper.VENDOR_NINTENDO, packet)
    }

    actual fun setSwitchPlayerLeds(playerIndex: Int, mask: Int): Boolean {
        var wroteSysfs = false
        for (ledIdx in 1..4) {
            val v = if ((mask and (1 shl (ledIdx - 1))) != 0) "1\n" else "0\n"
            if (writeFirstMatchingSysfsLed("player$ledIdx", "brightness", v) ||
                writeFirstMatchingSysfsLed("player-$ledIdx", "brightness", v)
            ) {
                wroteSysfs = true
            }
        }
        if (wroteSysfs) return true
        val packet = ByteArray(49)
        packet[0] = 0x01
        packet[1] = (switchPacketSeq++.toInt() and 0x0F).toByte()
        packet[10] = 0x30
        packet[11] = (mask and 0x0F).toByte()
        return writeFirstHidrawMatching(GamepadLayoutMapper.VENDOR_NINTENDO, packet)
    }

    actual fun setSwitchHomeLedPattern(playerIndex: Int, pattern: SwitchHomeLedPattern): Boolean {
        val p = pattern.clamped()
        val packet = ByteArray(49)
        packet[0] = 0x01
        packet[1] = (switchPacketSeq++.toInt() and 0x0F).toByte()
        packet[10] = 0x38
        val miniDur = ((1f / p.pulseRateHz.coerceAtLeast(0.5f)) * 4f).toInt().coerceIn(0, 15)
        val maxL = (p.maxIntensity * 15f).toInt().coerceIn(0, 15)
        val baseL = (p.baseIntensity * 15f).toInt().coerceIn(0, 15)
        packet[11] = ((1 shl 4) or miniDur).toByte()
        packet[12] = ((baseL shl 4) or p.cycleCount).toByte()
        packet[13] = ((maxL shl 4) or 0x04).toByte()
        return writeFirstHidrawMatching(GamepadLayoutMapper.VENDOR_NINTENDO, packet)
    }

    private fun pollEvdevImu(slot: JsSlot, evBuf: ByteArray) {
        var updated = false
        memScoped {
            val raw = allocArray<ByteVar>(24)
            while (true) {
                val got = read(slot.eventFd, raw, 24uL)
                if (got < 24L) break
                val type = ((raw[17].toInt() and 0xFF) shl 8) or (raw[16].toInt() and 0xFF)
                val code = ((raw[19].toInt() and 0xFF) shl 8) or (raw[18].toInt() and 0xFF)
                val value = (raw[20].toInt() and 0xFF) or
                    ((raw[21].toInt() and 0xFF) shl 8) or
                    ((raw[22].toInt() and 0xFF) shl 16) or
                    ((raw[23].toInt() and 0xFF) shl 24)
                if (type == 0x03) { // EV_ABS
                    when (code) {
                        0x00 -> { slot.imuAccelX = value / 8192f; updated = true }
                        0x01 -> { slot.imuAccelY = value / 8192f; updated = true }
                        0x02 -> { slot.imuAccelZ = value / 8192f; updated = true }
                        0x03 -> { slot.imuGyroX = value * 0.001065f; updated = true }
                        0x04 -> { slot.imuGyroY = value * 0.001065f; updated = true }
                        0x05 -> { slot.imuGyroZ = value * 0.001065f; updated = true }
                    }
                }
            }
        }
        if (updated) {
            slot.hasImuData = true
            val nowMs = monotonicMs()
            val dt = if (slot.lastImuMs > 0L) ((nowMs - slot.lastImuMs).coerceIn(1L, 100L) / 1000f) else (1f / 120f)
            slot.lastImuMs = nowMs
            slot.latestMotion = slot.fusion.update(
                gyroRadX = slot.imuGyroX,
                gyroRadY = slot.imuGyroY,
                gyroRadZ = slot.imuGyroZ,
                accelGX = slot.imuAccelX,
                accelGY = slot.imuAccelY,
                accelGZ = slot.imuAccelZ,
                dtSeconds = dt,
                timestampMs = nowMs
            )
        }
    }

    private fun uploadAndPlayFfEffect(slot: JsSlot, request: GamepadRumbleRequest): Boolean {
        // Linux struct ff_effect (48 bytes on 64-bit) with FF_RUMBLE = 0x50
        return memScoped {
            val ff = allocArray<ByteVar>(48)
            for (k in 0 until 48) ff[k] = 0
            // __u16 type = 0x50 (FF_RUMBLE)
            ff[0] = 0x50
            ff[1] = 0x00
            // __s16 id (-1 allocates new slot)
            val idInt = slot.ffEffectId.toInt()
            ff[2] = (idInt and 0xFF).toByte()
            ff[3] = ((idInt ushr 8) and 0xFF).toByte()
            // struct ff_replay at offset 8: length (u16), delay (u16)
            val dur = request.durationMs.coerceIn(1, 60_000)
            ff[8] = (dur and 0xFF).toByte()
            ff[9] = ((dur ushr 8) and 0xFF).toByte()
            // struct ff_rumble_effect at offset 24: strong_magnitude (u16), weak_magnitude (u16)
            val strong = (request.lowFrequency * 65535f).toInt().coerceIn(0, 65535)
            val weak = (request.highFrequency * 65535f).toInt().coerceIn(0, 65535)
            ff[24] = (strong and 0xFF).toByte()
            ff[25] = ((strong ushr 8) and 0xFF).toByte()
            ff[26] = (weak and 0xFF).toByte()
            ff[27] = ((weak ushr 8) and 0xFF).toByte()

            // EVIOCSFF = _IOW('E', 0x80, struct ff_effect[48]) -> 0x40304580
            val eviocsff = ((1uL shl 30) or (48uL shl 16) or ('E'.code.toULong() shl 8) or 0x80uL)
            if (ioctl(slot.eventFd, eviocsff, ff) < 0) return false
            slot.ffEffectId = (((ff[3].toInt() and 0xFF) shl 8) or (ff[2].toInt() and 0xFF)).toShort()

            // Write EV_FF (type = 0x15) trigger input_event (24 bytes)
            val ev = allocArray<ByteVar>(24)
            for (k in 0 until 24) ev[k] = 0
            ev[16] = 0x15 // EV_FF
            ev[18] = ff[2]
            ev[19] = ff[3]
            ev[20] = if (strong > 0 || weak > 0) 1 else 0
            write(slot.eventFd, ev, 24uL) == 24L
        }
    }

    private fun findMatchingEvdevFd(vid: Int, pid: Int): Int {
        for (e in 0 until 32) {
            val evVid = readSysfsHex("/sys/class/input/event$e/device/id/vendor")
            val evPid = readSysfsHex("/sys/class/input/event$e/device/id/product")
            if (vid != 0 && evVid == vid && evPid == pid) {
                val fd = open("/dev/input/event$e", O_RDWR or O_NONBLOCK)
                if (fd >= 0) return fd
            }
        }
        return -1
    }

    private fun sendDualSenseHidReport(
        leftRumble: Int? = null,
        rightRumble: Int? = null,
        enableMuteLed: Boolean = false,
        muteLedByte: Int = 0,
        enableLightbar: Boolean = false,
        r: Int = 0,
        g: Int = 0,
        b: Int = 0,
        enablePlayerLeds: Boolean = false,
        playerLedMask: Int = 0,
        leftTriggerEffect: AdaptiveTriggerEffect? = null,
        rightTriggerEffect: AdaptiveTriggerEffect? = null
    ): Boolean {
        val report = ByteArray(48)
        report[0] = 0x02
        var f0 = 0
        var f1 = 0
        if (leftRumble != null || rightRumble != null) {
            f0 = f0 or 0x03
            report[3] = (rightRumble ?: 0).coerceIn(0, 255).toByte()
            report[4] = (leftRumble ?: 0).coerceIn(0, 255).toByte()
        }
        if (rightTriggerEffect != null) {
            f0 = f0 or 0x04
            encodeDualSenseTriggerBlock(report, 11, rightTriggerEffect)
        }
        if (leftTriggerEffect != null) {
            f0 = f0 or 0x08
            encodeDualSenseTriggerBlock(report, 22, leftTriggerEffect)
        }
        if (enableMuteLed) {
            f1 = f1 or 0x01
            report[9] = muteLedByte.toByte()
        }
        if (enableLightbar) {
            f1 = f1 or 0x04
            report[45] = r.coerceIn(0, 255).toByte()
            report[46] = g.coerceIn(0, 255).toByte()
            report[47] = b.coerceIn(0, 255).toByte()
        }
        if (enablePlayerLeds) {
            f1 = f1 or 0x10
            report[44] = playerLedMask.toByte()
        }
        report[1] = f0.toByte()
        report[2] = f1.toByte()
        return writeFirstHidrawMatching(GamepadLayoutMapper.VENDOR_SONY, report)
    }

    private fun encodeDualSenseTriggerBlock(buf: ByteArray, offset: Int, effect: AdaptiveTriggerEffect) {
        when (effect) {
            AdaptiveTriggerEffect.Off -> buf[offset] = 0x05
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

    private fun encodeSwitchRumble(buf: ByteArray, offset: Int, hf: Float, ha: Float, lf: Float, la: Float) {
        buf[offset] = ((hf.coerceIn(40f, 1252f) - 40f) / 10f).toInt().coerceIn(0, 127).toByte()
        buf[offset + 1] = (ha.coerceIn(0f, 1f) * 100f).toInt().coerceIn(0, 100).toByte()
        buf[offset + 2] = ((lf.coerceIn(40f, 626f) - 40f) / 5f).toInt().coerceIn(0, 127).toByte()
        buf[offset + 3] = (la.coerceIn(0f, 1f) * 100f).toInt().coerceIn(0, 100).toByte()
    }

    private fun writeFirstHidrawMatching(targetVendorId: Int, packet: ByteArray): Boolean {
        val hexVid = targetVendorId.toString(16).padStart(4, '0').lowercase()
        for (h in 0 until 16) {
            val uevent = readSmallSysfsText("/sys/class/hidraw/hidraw$h/device/uevent") ?: continue
            if (hexVid !in uevent.lowercase()) continue
            val fd = open("/dev/hidraw$h", O_WRONLY)
            if (fd < 0) continue
            try {
                memScoped {
                    val buf = allocArray<ByteVar>(packet.size)
                    for (idx in packet.indices) buf[idx] = packet[idx]
                    val written = write(fd, buf, packet.size.toULong())
                    if (written == packet.size.toLong()) return true
                }
            } finally {
                close(fd)
            }
        }
        return false
    }

    private fun writeFirstMatchingSysfsLed(dirFilter: String, fileName: String, content: String): Boolean {
        val dir = opendir("/sys/class/leds") ?: return false
        try {
            while (true) {
                val entry = readdir(dir) ?: break
                val name = entry.pointed.d_name.toKString()
                if (name == "." || name == "..") continue
                if (dirFilter !in name) continue
                val fd = open("/sys/class/leds/$name/$fileName", O_WRONLY)
                if (fd < 0) continue
                try {
                    memScoped {
                        val bytes = content.encodeToByteArray()
                        val buf = allocArray<ByteVar>(bytes.size)
                        for (idx in bytes.indices) buf[idx] = bytes[idx]
                        if (write(fd, buf, bytes.size.toULong()) > 0L) return true
                    }
                } finally {
                    close(fd)
                }
            }
        } finally {
            closedir(dir)
        }
        return false
    }

    private fun queryJsDeviceName(fd: Int): String = memScoped {
        val nameBuf = allocArray<ByteVar>(256)
        val request = ((2uL shl 30) or (256uL shl 16) or ('j'.code.toULong() shl 8) or 0x13uL)
        if (ioctl(fd, request, nameBuf) < 0) "" else nameBuf.toKString()
    }

    private fun readSysfsName(slot: Int): String? =
        readSmallSysfsText("/sys/class/input/js$slot/device/name")

    private fun readSysfsHex(path: String): Int =
        readSmallSysfsText(path)?.trim()?.toIntOrNull(16) ?: 0

    private fun readSmallSysfsText(path: String): String? {
        val fd = open(path, O_RDONLY)
        if (fd < 0) return null
        return try {
            memScoped {
                val buf = allocArray<ByteVar>(512)
                val n = read(fd, buf, 511uL).toInt()
                if (n <= 0) return null
                buf[n] = 0
                buf.toKString().trim()
            }
        } finally {
            close(fd)
        }
    }

    private fun monotonicMs(): Long = memScoped {
        val ts = alloc<timespec>()
        clock_gettime(CLOCK_MONOTONIC, ts.ptr)
        ts.tv_sec * 1000L + ts.tv_nsec / 1_000_000L
    }
}
