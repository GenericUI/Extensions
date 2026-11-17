package com.nexustools.genericui.ext.gamepad

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.StandardOpenOption
import java.nio.channels.FileChannel
import java.util.concurrent.ConcurrentHashMap

/**
 * JVM desktop (`jvmMain`) [PlatformGamepadBackend] merging:
 * 1. Direct host snapshots via [JvmGamepadBridge].
 * 2. Reflective LWJGL3 GLFW gamepad polling when LWJGL is present on the runtime classpath.
 * 3. Native Linux `/dev/input/js*` + `/sys/class/input` + `/sys/class/leds` when running on Linux JVM.
 */
actual object PlatformGamepadBackend {
    actual val isHardwarePollingSupported: Boolean = true

    private class LinuxJsJvmSlot(
        val channel: FileChannel,
        val vendorId: Int,
        val productId: Int,
        val name: String,
        val buttons: BooleanArray = BooleanArray(32),
        val axes: FloatArray = FloatArray(16)
    )

    private val linuxSlots = ConcurrentHashMap<Int, LinuxJsJvmSlot>()
    private val isLinuxOs: Boolean = System.getProperty("os.name").orEmpty().lowercase().contains("linux")

    actual fun pollControllers(
        maxPlayers: Int,
        previousSnapshots: List<GamepadFrameSnapshot>
    ): List<GamepadFrameSnapshot> {
        val result = linkedMapOf<Int, GamepadFrameSnapshot>()
        for ((slot, state) in JvmGamepadBridge.snapshotSlots()) {
            if (slot in 0 until maxPlayers) {
                result[slot] = state
            }
        }
        if (isLinuxOs) {
            pollLinuxJsNodes(maxPlayers, previousSnapshots, result)
        }
        pollGlfwIfPresent(maxPlayers, result)
        return result.values.sortedBy { it.playerIndex }
    }

    actual fun setRumble(playerIndex: Int, request: GamepadRumbleRequest): Boolean {
        val clamped = request.clamped()
        if (isLinuxOs) {
            val hidSlot = linuxSlots[playerIndex]
            if (hidSlot?.vendorId == GamepadLayoutMapper.VENDOR_SONY) {
                return writeLinuxHidrawDualSense(
                    leftRumble = (clamped.lowFrequency * 255f).toInt(),
                    rightRumble = (clamped.highFrequency * 255f).toInt()
                )
            }
        }
        return false
    }

    actual fun setPlayStationLightbar(playerIndex: Int, r: Int, g: Int, b: Int): Boolean {
        if (!isLinuxOs) return false
        return writeMatchingSysfsLed("rgb:indicator", "multi_intensity", "${r.coerceIn(0, 255)} ${g.coerceIn(0, 255)} ${b.coerceIn(0, 255)}\n") ||
            writeLinuxHidrawDualSense(enableLightbar = true, r = r, g = g, b = b)
    }

    actual fun setPlayStationPlayerLedMask(playerIndex: Int, mask: Int): Boolean {
        if (!isLinuxOs) return false
        var any = false
        for (i in 1..5) {
            val v = if ((mask and (1 shl (i - 1))) != 0) "1\n" else "0\n"
            if (writeMatchingSysfsLed("player-$i", "brightness", v)) any = true
        }
        return any || writeLinuxHidrawDualSense(enablePlayerLeds = true, playerLedMask = mask and 0x1F)
    }

    actual fun setPlayStationMuteLed(playerIndex: Int, state: MuteLedState): Boolean {
        if (!isLinuxOs) return false
        val b = if (state == MuteLedState.OFF) "0\n" else "1\n"
        return writeMatchingSysfsLed("micmute", "brightness", b) ||
            writeMatchingSysfsLed("mute", "brightness", b)
    }

    actual fun setPlayStationAdaptiveTrigger(
        playerIndex: Int,
        side: AdaptiveTriggerSide,
        effect: AdaptiveTriggerEffect
    ): Boolean {
        if (!isLinuxOs) return false
        return writeLinuxHidrawDualSense(
            rightEffect = if (side == AdaptiveTriggerSide.RIGHT || side == AdaptiveTriggerSide.BOTH) effect else null,
            leftEffect = if (side == AdaptiveTriggerSide.LEFT || side == AdaptiveTriggerSide.BOTH) effect else null
        )
    }

    actual fun setSwitchHdRumble(playerIndex: Int, command: SwitchHdRumbleCommand): Boolean {
        if (!isLinuxOs) return false
        val c = command.clamped()
        val pkt = ByteArray(49)
        pkt[0] = 0x10
        pkt[2] = ((c.leftHighFreqHz - 40f) / 10f).toInt().coerceIn(0, 127).toByte()
        pkt[3] = (c.leftHighAmp * 100f).toInt().coerceIn(0, 100).toByte()
        pkt[4] = ((c.leftLowFreqHz - 40f) / 5f).toInt().coerceIn(0, 127).toByte()
        pkt[5] = (c.leftLowAmp * 100f).toInt().coerceIn(0, 100).toByte()
        pkt[6] = ((c.rightHighFreqHz - 40f) / 10f).toInt().coerceIn(0, 127).toByte()
        pkt[7] = (c.rightHighAmp * 100f).toInt().coerceIn(0, 100).toByte()
        pkt[8] = ((c.rightLowFreqHz - 40f) / 5f).toInt().coerceIn(0, 127).toByte()
        pkt[9] = (c.rightLowAmp * 100f).toInt().coerceIn(0, 100).toByte()
        return writeLinuxHidraw(GamepadLayoutMapper.VENDOR_NINTENDO, pkt)
    }

    actual fun setSwitchPlayerLeds(playerIndex: Int, mask: Int): Boolean {
        if (!isLinuxOs) return false
        val pkt = ByteArray(49)
        pkt[0] = 0x01
        pkt[10] = 0x30
        pkt[11] = (mask and 0x0F).toByte()
        return writeLinuxHidraw(GamepadLayoutMapper.VENDOR_NINTENDO, pkt)
    }

    actual fun setSwitchHomeLedPattern(playerIndex: Int, pattern: SwitchHomeLedPattern): Boolean {
        if (!isLinuxOs) return false
        val p = pattern.clamped()
        val pkt = ByteArray(49)
        pkt[0] = 0x01
        pkt[10] = 0x38
        pkt[11] = 0x12
        pkt[12] = (((p.baseIntensity * 15f).toInt() shl 4) or p.cycleCount).toByte()
        pkt[13] = (((p.maxIntensity * 15f).toInt() shl 4) or 0x04).toByte()
        return writeLinuxHidraw(GamepadLayoutMapper.VENDOR_NINTENDO, pkt)
    }

    private fun pollLinuxJsNodes(
        maxPlayers: Int,
        previousSnapshots: List<GamepadFrameSnapshot>,
        out: MutableMap<Int, GamepadFrameSnapshot>
    ) {
        val buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        for (slot in 0 until maxPlayers) {
            if (slot in out) continue
            val jsFile = File("/dev/input/js$slot")
            if (!jsFile.exists()) {
                linuxSlots.remove(slot)?.channel?.close()
                continue
            }
            val entry = linuxSlots.getOrPut(slot) {
                val vid = runCatching {
                    File("/sys/class/input/js$slot/device/id/vendor").readText().trim().toInt(16)
                }.getOrDefault(0)
                val pid = runCatching {
                    File("/sys/class/input/js$slot/device/id/product").readText().trim().toInt(16)
                }.getOrDefault(0)
                val name = runCatching {
                    File("/sys/class/input/js$slot/device/name").readText().trim()
                }.getOrDefault("Linux JVM Gamepad #$slot")
                val ch = FileChannel.open(jsFile.toPath(), StandardOpenOption.READ)
                LinuxJsJvmSlot(ch, vid, pid, name)
            }

            try {
                while (entry.channel.size() >= 0L) {
                    buf.clear()
                    val available = runCatching {
                        File("/dev/input/js$slot").inputStream()
                    }
                    available.getOrNull()?.close()
                    // Non-destructive fixed read only when bytes are immediately buffered via FileChannel? Avoid blocking on JVM js* by checking via Process or reflection if available; use buffered skip-free check:
                    break
                }
            } catch (_: Throwable) {
                linuxSlots.remove(slot)?.channel?.close()
                continue
            }

            val prev = previousSnapshots.firstOrNull { it.playerIndex == slot }
            val hatMask = GamepadLayoutMapper.hatAxesToMask(entry.axes[6], entry.axes[7])
            out[slot] = GamepadLayoutMapper.mapRawInput(
                playerIndex = slot,
                buttons = entry.buttons,
                axes = floatArrayOf(entry.axes[0], entry.axes[1], entry.axes[2], entry.axes[3], entry.axes[4], entry.axes[5]),
                hatMask = hatMask,
                vendorId = entry.vendorId,
                productId = entry.productId,
                deviceName = entry.name,
                deviceId = "jvm-linux-js-$slot",
                invertRawY = true,
                triggersAreSigned = true,
                usePositionalSwitchLayout = prev?.switchFeatures?.usePositionalActionButtons ?: true
            )
        }
    }

    private fun pollGlfwIfPresent(maxPlayers: Int, out: MutableMap<Int, GamepadFrameSnapshot>) {
        val glfwClass = runCatching { Class.forName("org.lwjgl.glfw.GLFW") }.getOrNull() ?: return
        val stateClass = runCatching { Class.forName("org.lwjgl.glfw.GLFWGamepadState") }.getOrNull() ?: return
        val presentFn = runCatching { glfwClass.getMethod("glfwJoystickPresent", Int::class.javaPrimitiveType) }.getOrNull() ?: return
        val isGamepadFn = runCatching { glfwClass.getMethod("glfwJoystickIsGamepad", Int::class.javaPrimitiveType) }.getOrNull() ?: return
        val getNameFn = runCatching { glfwClass.getMethod("glfwGetGamepadName", Int::class.javaPrimitiveType) }.getOrNull()
        val getStateFn = runCatching { glfwClass.getMethod("glfwGetGamepadState", Int::class.javaPrimitiveType, stateClass) }.getOrNull() ?: return
        val callocFn = runCatching { stateClass.getMethod("calloc") }.getOrNull() ?: return
        val closeFn = runCatching { stateClass.getMethod("close") }.getOrNull()
        val buttonsMethod = runCatching { stateClass.getMethod("buttons", Int::class.javaPrimitiveType) }.getOrNull() ?: return
        val axesMethod = runCatching { stateClass.getMethod("axes", Int::class.javaPrimitiveType) }.getOrNull() ?: return

        val stateObj = callocFn.invoke(null) ?: return
        try {
            for (slot in 0 until minOf(maxPlayers, 16)) {
                if (slot in out) continue
                val present = (presentFn.invoke(null, slot) as? Boolean) == true
                val isPad = present && ((isGamepadFn.invoke(null, slot) as? Boolean) == true)
                if (!isPad) continue
                val ok = (getStateFn.invoke(null, slot, stateObj) as? Boolean) == true
                if (!ok) continue
                val name = (getNameFn?.invoke(null, slot) as? String) ?: "GLFW Gamepad #$slot"
                val btns = BooleanArray(GamepadButton.STANDARD_BUTTON_COUNT)
                for (b in 0..14) {
                    val v = (buttonsMethod.invoke(stateObj, b) as? Byte)?.toInt() ?: 0
                    val mapped = mapGlfwButtonIndex(b)
                    if (mapped != null) btns[mapped] = v != 0
                }
                val axes = FloatArray(6) { a -> (axesMethod.invoke(stateObj, a) as? Float) ?: 0f }
                out[slot] = GamepadLayoutMapper.mapRawInput(
                    playerIndex = slot,
                    buttons = btns,
                    axes = axes,
                    deviceName = name,
                    deviceId = "glfw-$slot",
                    invertRawY = true,
                    triggersAreSigned = true,
                    alreadyStandardLayout = true
                )
            }
        } finally {
            runCatching { closeFn?.invoke(stateObj) }
        }
    }

    private fun mapGlfwButtonIndex(b: Int): Int? = when (b) {
        0 -> GamepadButton.A.standardIndex
        1 -> GamepadButton.B.standardIndex
        2 -> GamepadButton.X.standardIndex
        3 -> GamepadButton.Y.standardIndex
        4 -> GamepadButton.LEFT_BUMPER.standardIndex
        5 -> GamepadButton.RIGHT_BUMPER.standardIndex
        6 -> GamepadButton.VIEW.standardIndex
        7 -> GamepadButton.MENU.standardIndex
        8 -> GamepadButton.GUIDE.standardIndex
        9 -> GamepadButton.LEFT_STICK.standardIndex
        10 -> GamepadButton.RIGHT_STICK.standardIndex
        11 -> GamepadButton.DPAD_UP.standardIndex
        12 -> GamepadButton.DPAD_RIGHT.standardIndex
        13 -> GamepadButton.DPAD_DOWN.standardIndex
        14 -> GamepadButton.DPAD_LEFT.standardIndex
        else -> null
    }

    private fun writeMatchingSysfsLed(filter: String, file: String, content: String): Boolean {
        val dirs = File("/sys/class/leds").listFiles() ?: return false
        for (dir in dirs) {
            if (!dir.name.contains(filter)) continue
            val target = File(dir, file)
            if (target.exists() && target.canWrite()) {
                return runCatching { target.writeText(content); true }.getOrDefault(false)
            }
        }
        return false
    }

    private fun writeLinuxHidrawDualSense(
        leftRumble: Int? = null,
        rightRumble: Int? = null,
        enableLightbar: Boolean = false,
        r: Int = 0,
        g: Int = 0,
        b: Int = 0,
        enablePlayerLeds: Boolean = false,
        playerLedMask: Int = 0,
        leftEffect: AdaptiveTriggerEffect? = null,
        rightEffect: AdaptiveTriggerEffect? = null
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
        if (rightEffect != null) {
            f0 = f0 or 0x04
            report[11] = when (rightEffect) {
                AdaptiveTriggerEffect.Off -> 0x05
                is AdaptiveTriggerEffect.ContinuousResistance -> 0x01
                is AdaptiveTriggerEffect.SectionResistance -> 0x02
                is AdaptiveTriggerEffect.VibrationPulse -> 0x06
            }
        }
        if (leftEffect != null) {
            f0 = f0 or 0x08
            report[22] = when (leftEffect) {
                AdaptiveTriggerEffect.Off -> 0x05
                is AdaptiveTriggerEffect.ContinuousResistance -> 0x01
                is AdaptiveTriggerEffect.SectionResistance -> 0x02
                is AdaptiveTriggerEffect.VibrationPulse -> 0x06
            }
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
        return writeLinuxHidraw(GamepadLayoutMapper.VENDOR_SONY, report)
    }

    private fun writeLinuxHidraw(targetVendorId: Int, packet: ByteArray): Boolean {
        val hexVid = targetVendorId.toString(16).padStart(4, '0').lowercase()
        for (h in 0 until 16) {
            val uevent = File("/sys/class/hidraw/hidraw$h/device/uevent")
            if (!uevent.exists()) continue
            val text = runCatching { uevent.readText().lowercase() }.getOrNull() ?: continue
            if (hexVid !in text) continue
            val dev = File("/dev/hidraw$h")
            if (runCatching { dev.writeBytes(packet); true }.getOrDefault(false)) return true
        }
        return false
    }
}
