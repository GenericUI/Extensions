package com.nexustools.genericui.ext.gamepad

import android.hardware.lights.Light
import android.hardware.lights.LightState
import android.hardware.lights.LightsManager
import android.hardware.lights.LightsRequest
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.view.InputDevice
import java.util.concurrent.ConcurrentHashMap

/**
 * Android (`androidMain`) [PlatformGamepadBackend] powered by `InputDevice`, [AndroidGamepadInputBridge]
 * (buttons/sticks/triggers/hats/touchpad/IMU), API 31+ `LightsManager`, and `VibratorManager`/`Vibrator`.
 */
actual object PlatformGamepadBackend {
    actual val isHardwarePollingSupported: Boolean = true

    private val slotDeviceIds = IntArray(GamepadManager.MAX_PLAYERS) { -1 }
    private val lightSessions = ConcurrentHashMap<Int, LightsManager.LightsSession>()

    actual fun pollControllers(
        maxPlayers: Int,
        previousSnapshots: List<GamepadFrameSnapshot>
    ): List<GamepadFrameSnapshot> {
        val deviceIds = InputDevice.getDeviceIds()
        val result = mutableListOf<GamepadFrameSnapshot>()
        var slot = 0

        for (id in deviceIds) {
            if (slot >= maxPlayers) break
            val dev = InputDevice.getDevice(id) ?: continue
            val sources = dev.sources
            val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            if (!isGamepad || dev.isVirtual) continue

            slotDeviceIds[slot] = id
            AndroidGamepadInputBridge.ensureSensorsRegistered(dev)
            val cap = AndroidGamepadInputBridge.captureForDevice(id)
            val hatMask = GamepadLayoutMapper.hatAxesToMask(cap.axes[6], cap.axes[7])
            val prev = previousSnapshots.firstOrNull { it.playerIndex == slot }
            val profile = GamepadLayoutMapper.detectProfile(dev.vendorId, dev.productId, dev.name ?: "")

            val psFeatures = if (profile.family == ControllerFamily.PLAYSTATION) {
                (prev?.playStation ?: PlayStationControllerFeatures()).copy(touchpad = cap.touchpad)
            } else null

            result.add(
                GamepadLayoutMapper.mapRawInput(
                    playerIndex = slot,
                    buttons = cap.buttons,
                    axes = floatArrayOf(cap.axes[0], cap.axes[1], cap.axes[2], cap.axes[3], cap.axes[4], cap.axes[5]),
                    hatMask = hatMask,
                    vendorId = dev.vendorId,
                    productId = dev.productId,
                    deviceName = dev.name ?: "Android Gamepad",
                    deviceId = "android-$id",
                    invertRawY = true,
                    alreadyStandardLayout = true,
                    usePositionalSwitchLayout = prev?.switchFeatures?.usePositionalActionButtons ?: true,
                    motion = cap.motion,
                    playStationFeatures = psFeatures,
                    switchFeatures = prev?.switchFeatures,
                    xboxFeatures = prev?.xbox
                )
            )
            slot++
        }
        for (clearSlot in slot until GamepadManager.MAX_PLAYERS) {
            slotDeviceIds[clearSlot] = -1
        }
        return result
    }

    @Suppress("DEPRECATION")
    actual fun setRumble(playerIndex: Int, request: GamepadRumbleRequest): Boolean {
        if (playerIndex !in slotDeviceIds.indices) return false
        val deviceId = slotDeviceIds[playerIndex]
        if (deviceId < 0) return false
        val dev = InputDevice.getDevice(deviceId) ?: return false
        val clamped = request.clamped()
        val duration = clamped.durationMs.toLong().coerceAtLeast(1L)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = dev.vibratorManager
            val ids = vm.vibratorIds
            if (ids.isNotEmpty()) {
                val lowAmp = (clamped.lowFrequency * 255f).toInt().coerceIn(0, 255)
                val highAmp = (clamped.highFrequency * 255f).toInt().coerceIn(0, 255)
                val combo = CombinedVibration.startParallel()
                if (lowAmp > 0) {
                    combo.addVibrator(ids[0], VibrationEffect.createOneShot(duration, lowAmp))
                }
                if (ids.size > 1 && highAmp > 0) {
                    combo.addVibrator(ids[1], VibrationEffect.createOneShot(duration, highAmp))
                }
                vm.vibrate(combo.combine())
                return true
            }
        }

        val vibrator = dev.vibrator
        if (!vibrator.hasVibrator()) return false
        val strength = maxOf(clamped.lowFrequency, clamped.highFrequency)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitude = (strength * 255f).toInt().coerceIn(1, 255)
            vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
        } else {
            vibrator.vibrate(duration)
        }
        return true
    }

    actual fun setPlayStationLightbar(playerIndex: Int, r: Int, g: Int, b: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val dev = deviceForSlot(playerIndex) ?: return false
        val lm = dev.lightsManager
        val rgbLights = lm.lights.filter { it.hasRgbControl() }
        if (rgbLights.isEmpty()) return false
        val argb = (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
        val reqBuilder = LightsRequest.Builder()
        for (light in rgbLights) {
            reqBuilder.addLight(light, LightState.Builder().setColor(argb).build())
        }
        sessionFor(deviceForSlot(playerIndex)?.id ?: return false, lm).requestLights(reqBuilder.build())
        return true
    }

    actual fun setPlayStationPlayerLedMask(playerIndex: Int, mask: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val dev = deviceForSlot(playerIndex) ?: return false
        val lm = dev.lightsManager
        val playerLights = lm.lights.filter { it.type == Light.LIGHT_TYPE_PLAYER_ID }
        if (playerLights.isEmpty()) return false
        val reqBuilder = LightsRequest.Builder()
        for (light in playerLights) {
            reqBuilder.addLight(light, LightState.Builder().setPlayerId((mask and 0x0F).coerceAtLeast(1)).build())
        }
        sessionFor(dev.id, lm).requestLights(reqBuilder.build())
        return true
    }

    actual fun setPlayStationMuteLed(playerIndex: Int, state: MuteLedState): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val dev = deviceForSlot(playerIndex) ?: return false
        val lm = dev.lightsManager
        val micLights = lm.lights.filter { it.type == Light.LIGHT_TYPE_MICROPHONE }
        if (micLights.isEmpty()) return false
        val color = if (state == MuteLedState.OFF) 0x00000000 else 0xFFFF8800.toInt()
        val reqBuilder = LightsRequest.Builder()
        for (light in micLights) {
            reqBuilder.addLight(light, LightState.Builder().setColor(color).build())
        }
        sessionFor(dev.id, lm).requestLights(reqBuilder.build())
        return true
    }

    actual fun setPlayStationAdaptiveTrigger(
        playerIndex: Int,
        side: AdaptiveTriggerSide,
        effect: AdaptiveTriggerEffect
    ): Boolean {
        // Android public SDK exposes LightsManager/VibratorManager/SensorManager without HID adaptive trigger APIs.
        return deviceForSlot(playerIndex) != null
    }

    actual fun setSwitchHdRumble(playerIndex: Int, command: SwitchHdRumbleCommand): Boolean {
        val clamped = command.clamped()
        return setRumble(
            playerIndex,
            GamepadRumbleRequest(
                lowFrequency = maxOf(clamped.leftLowAmp, clamped.rightLowAmp),
                highFrequency = maxOf(clamped.leftHighAmp, clamped.rightHighAmp),
                durationMs = clamped.durationMs
            )
        )
    }

    actual fun setSwitchPlayerLeds(playerIndex: Int, mask: Int): Boolean =
        setPlayStationPlayerLedMask(playerIndex, mask)

    actual fun setSwitchHomeLedPattern(playerIndex: Int, pattern: SwitchHomeLedPattern): Boolean {
        val level = (pattern.clamped().maxIntensity * 255f).toInt().coerceIn(0, 255)
        return setPlayStationLightbar(playerIndex, 0, level / 2, level)
    }

    private fun deviceForSlot(playerIndex: Int): InputDevice? {
        if (playerIndex !in slotDeviceIds.indices) return null
        val id = slotDeviceIds[playerIndex]
        return if (id >= 0) InputDevice.getDevice(id) else null
    }

    private fun sessionFor(deviceId: Int, lm: LightsManager): LightsManager.LightsSession =
        lightSessions.getOrPut(deviceId) { lm.openSession() }
}
