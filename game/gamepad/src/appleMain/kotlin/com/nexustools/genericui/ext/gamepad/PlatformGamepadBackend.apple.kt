@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.gamepad

import kotlinx.cinterop.interpretObjCPointer
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.useContents
import platform.CoreHaptics.CHHapticEngine
import platform.CoreHaptics.CHHapticEvent
import platform.CoreHaptics.CHHapticEventParameter
import platform.CoreHaptics.CHHapticEventParameterIDHapticIntensity
import platform.CoreHaptics.CHHapticEventParameterIDHapticSharpness
import platform.CoreHaptics.CHHapticEventTypeHapticContinuous
import platform.CoreHaptics.CHHapticPattern
import platform.GameController.GCColor
import platform.GameController.GCController
import platform.GameController.GCControllerDirectionPad
import platform.GameController.GCControllerPlayerIndex1
import platform.GameController.GCControllerPlayerIndex2
import platform.GameController.GCControllerPlayerIndex3
import platform.GameController.GCControllerPlayerIndex4
import platform.GameController.GCControllerTouchpad
import platform.GameController.GCDualSenseAdaptiveTrigger
import platform.GameController.GCDualSenseGamepad
import platform.GameController.GCDualShockGamepad
import platform.GameController.GCHapticsLocalityDefault
import platform.GameController.GCHapticsLocalityHandles
import platform.GameController.GCHapticsLocalityLeftTrigger
import platform.GameController.GCHapticsLocalityRightTrigger
import platform.GameController.GCMotion
import platform.GameController.GCTouchState
import platform.GameController.GCXboxGamepad
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.time.TimeSource

/**
 * Apple (`appleMain` — macOS & iOS) [PlatformGamepadBackend] powered by `GCController`,
 * `GCDualSenseGamepad` / `GCDualShockGamepad` (touchpad + RGB lightbar + adaptive triggers),
 * `GCXboxGamepad` (Share + Elite rear paddles), `GCMotion` (6-axis IMU + attitude), and
 * `GCDeviceHaptics` / `CoreHaptics`.
 */
actual object PlatformGamepadBackend {
    actual val isHardwarePollingSupported: Boolean = true

    private val hapticEngines = mutableMapOf<String, CHHapticEngine>()
    private val slotControllers = arrayOfNulls<GCController>(GamepadManager.MAX_PLAYERS)
    private val sensorFusions = Array(GamepadManager.MAX_PLAYERS) { SixAxisSensorFusion() }
    private val timeMark = TimeSource.Monotonic.markNow()
    private var lastMotionSampleMs = LongArray(GamepadManager.MAX_PLAYERS)

    actual fun pollControllers(
        maxPlayers: Int,
        previousSnapshots: List<GamepadFrameSnapshot>
    ): List<GamepadFrameSnapshot> {
        val rawControllers = GCController.controllers()
        for (i in slotControllers.indices) {
            slotControllers[i] = null
        }
        if (rawControllers.isEmpty()) return emptyList()

        val result = mutableListOf<GamepadFrameSnapshot>()
        val usedSlots = BooleanArray(maxPlayers)

        for (raw in rawControllers) {
            val ctrl = raw as? GCController ?: continue
            val assignedSlot = resolveSlot(ctrl, usedSlots, maxPlayers) ?: continue
            usedSlots[assignedSlot] = true
            slotControllers[assignedSlot] = ctrl

            val name = (ctrl.vendorName ?: ctrl.productCategory).ifEmpty { "Apple GameController" }
            val (vid, pid) = inferVendorProduct(ctrl, name)
            val battery = ctrl.battery?.batteryLevel
            val nowMs = timeMark.elapsedMicros() / 1000L
            val prevSnapshot = previousSnapshots.firstOrNull { it.playerIndex == assignedSlot }
            val motion = readMotion(assignedSlot, ctrl.motion, nowMs)

            val ext = ctrl.extendedGamepad
            if (ext != null) {
                val buttons = BooleanArray(GamepadButton.STANDARD_BUTTON_COUNT)
                buttons[GamepadButton.A.standardIndex] = ext.buttonA.isPressed()
                buttons[GamepadButton.B.standardIndex] = ext.buttonB.isPressed()
                buttons[GamepadButton.X.standardIndex] = ext.buttonX.isPressed()
                buttons[GamepadButton.Y.standardIndex] = ext.buttonY.isPressed()
                buttons[GamepadButton.LEFT_BUMPER.standardIndex] = ext.leftShoulder.isPressed()
                buttons[GamepadButton.RIGHT_BUMPER.standardIndex] = ext.rightShoulder.isPressed()
                buttons[GamepadButton.LEFT_TRIGGER_BUTTON.standardIndex] = ext.leftTrigger.isPressed()
                buttons[GamepadButton.RIGHT_TRIGGER_BUTTON.standardIndex] = ext.rightTrigger.isPressed()
                buttons[GamepadButton.VIEW.standardIndex] = ext.buttonOptions?.isPressed() ?: false
                buttons[GamepadButton.MENU.standardIndex] = ext.buttonMenu.isPressed()
                buttons[GamepadButton.LEFT_STICK.standardIndex] = ext.leftThumbstickButton?.isPressed() ?: false
                buttons[GamepadButton.RIGHT_STICK.standardIndex] = ext.rightThumbstickButton?.isPressed() ?: false
                buttons[GamepadButton.DPAD_UP.standardIndex] = ext.dpad.up.isPressed()
                buttons[GamepadButton.DPAD_DOWN.standardIndex] = ext.dpad.down.isPressed()
                buttons[GamepadButton.DPAD_LEFT.standardIndex] = ext.dpad.left.isPressed()
                buttons[GamepadButton.DPAD_RIGHT.standardIndex] = ext.dpad.right.isPressed()
                buttons[GamepadButton.GUIDE.standardIndex] = ext.buttonHome?.isPressed() ?: false

                var xboxShare = false
                var xboxPaddles = 0
                val xboxPad = ext as? GCXboxGamepad
                if (xboxPad != null) {
                    xboxShare = xboxPad.buttonShare?.isPressed() ?: false
                    buttons[GamepadButton.SHARE.standardIndex] = xboxShare
                    if (xboxPad.paddleButton1?.isPressed() == true) xboxPaddles = xboxPaddles or 1
                    if (xboxPad.paddleButton2?.isPressed() == true) xboxPaddles = xboxPaddles or 2
                    if (xboxPad.paddleButton3?.isPressed() == true) xboxPaddles = xboxPaddles or 4
                    if (xboxPad.paddleButton4?.isPressed() == true) xboxPaddles = xboxPaddles or 8
                }

                val dsPad = ext as? GCDualSenseGamepad
                val ds4Pad = ext as? GCDualShockGamepad
                val psFeatures = when {
                    dsPad != null -> buildPlayStationFeatures(
                        ctrl = ctrl,
                        tp1 = dsPad.touchpadPrimary,
                        tp2 = dsPad.touchpadSecondary,
                        clicked = dsPad.touchpadButton.isPressed(),
                        previous = prevSnapshot?.playStation
                    )
                    ds4Pad != null -> buildPlayStationFeatures(
                        ctrl = ctrl,
                        tp1 = ds4Pad.touchpadPrimary,
                        tp2 = ds4Pad.touchpadSecondary,
                        clicked = ds4Pad.touchpadButton?.isPressed() ?: false,
                        previous = prevSnapshot?.playStation
                    )
                    else -> null
                }

                val xboxFeatures = if (xboxPad != null) {
                    XboxControllerFeatures(
                        isSharePressed = xboxShare,
                        elitePaddleMask = xboxPaddles,
                        lastImpulseRumble = prevSnapshot?.xbox?.lastImpulseRumble
                    )
                } else {
                    null
                }

                val axes = floatArrayOf(
                    ext.leftThumbstick.xAxis.value,
                    ext.leftThumbstick.yAxis.value,
                    ext.rightThumbstick.xAxis.value,
                    ext.rightThumbstick.yAxis.value,
                    ext.leftTrigger.value,
                    ext.rightTrigger.value
                )

                result.add(
                    GamepadLayoutMapper.mapRawInput(
                        playerIndex = assignedSlot,
                        buttons = buttons,
                        axes = axes,
                        vendorId = vid,
                        productId = pid,
                        deviceName = name,
                        deviceId = "apple-gc-$assignedSlot",
                        batteryLevel = battery,
                        alreadyStandardLayout = true,
                        usePositionalSwitchLayout = prevSnapshot?.switchFeatures?.usePositionalActionButtons ?: true,
                        motion = motion,
                        playStationFeatures = psFeatures,
                        xboxFeatures = xboxFeatures
                    )
                )
            } else {
                val micro = ctrl.microGamepad ?: continue
                val buttons = BooleanArray(GamepadButton.STANDARD_BUTTON_COUNT)
                buttons[GamepadButton.A.standardIndex] = micro.buttonA.isPressed()
                buttons[GamepadButton.X.standardIndex] = micro.buttonX.isPressed()
                buttons[GamepadButton.MENU.standardIndex] = micro.buttonMenu.isPressed()
                buttons[GamepadButton.DPAD_UP.standardIndex] = micro.dpad.up.isPressed()
                buttons[GamepadButton.DPAD_DOWN.standardIndex] = micro.dpad.down.isPressed()
                buttons[GamepadButton.DPAD_LEFT.standardIndex] = micro.dpad.left.isPressed()
                buttons[GamepadButton.DPAD_RIGHT.standardIndex] = micro.dpad.right.isPressed()
                val axes = floatArrayOf(micro.dpad.xAxis.value, micro.dpad.yAxis.value, 0f, 0f, 0f, 0f)
                result.add(
                    GamepadLayoutMapper.mapRawInput(
                        playerIndex = assignedSlot,
                        buttons = buttons,
                        axes = axes,
                        vendorId = vid,
                        productId = pid,
                        deviceName = name,
                        deviceId = "apple-micro-$assignedSlot",
                        batteryLevel = battery,
                        alreadyStandardLayout = true,
                        motion = motion
                    )
                )
            }
        }
        return result
    }

    actual fun setRumble(playerIndex: Int, request: GamepadRumbleRequest): Boolean {
        val ctrl = slotControllers.getOrNull(playerIndex) ?: return false
        val clamped = request.clamped()
        var triggered = false
        val mainStrength = maxOf(clamped.lowFrequency, clamped.highFrequency)
        val mainLocality = GCHapticsLocalityHandles ?: GCHapticsLocalityDefault
        if (mainLocality != null) {
            triggered = playHapticOnLocality(
                ctrl = ctrl,
                engineKey = "$playerIndex:handles",
                locality = mainLocality,
                intensity = mainStrength,
                sharpness = clamped.highFrequency,
                durationSec = (clamped.durationMs.coerceAtLeast(1)) / 1000.0
            ) || triggered
        }
        if (clamped.leftTrigger > 0f && GCHapticsLocalityLeftTrigger != null) {
            triggered = playHapticOnLocality(
                ctrl = ctrl,
                engineKey = "$playerIndex:lt",
                locality = GCHapticsLocalityLeftTrigger!!,
                intensity = clamped.leftTrigger,
                sharpness = 0.7f,
                durationSec = (clamped.durationMs.coerceAtLeast(1)) / 1000.0
            ) || triggered
        }
        if (clamped.rightTrigger > 0f && GCHapticsLocalityRightTrigger != null) {
            triggered = playHapticOnLocality(
                ctrl = ctrl,
                engineKey = "$playerIndex:rt",
                locality = GCHapticsLocalityRightTrigger!!,
                intensity = clamped.rightTrigger,
                sharpness = 0.7f,
                durationSec = (clamped.durationMs.coerceAtLeast(1)) / 1000.0
            ) || triggered
        }
        return triggered
    }

    actual fun setPlayStationLightbar(playerIndex: Int, r: Int, g: Int, b: Int): Boolean {
        val ctrl = slotControllers.getOrNull(playerIndex) ?: return false
        val light = ctrl.light ?: return false
        light.color = GCColor(
            red = r.coerceIn(0, 255) / 255f,
            green = g.coerceIn(0, 255) / 255f,
            blue = b.coerceIn(0, 255) / 255f
        )
        return true
    }

    actual fun setPlayStationPlayerLedMask(playerIndex: Int, mask: Int): Boolean {
        val ctrl = slotControllers.getOrNull(playerIndex) ?: return false
        ctrl.playerIndex = when {
            (mask and 0x01) != 0 -> GCControllerPlayerIndex1
            (mask and 0x02) != 0 -> GCControllerPlayerIndex2
            (mask and 0x04) != 0 -> GCControllerPlayerIndex3
            (mask and 0x08) != 0 -> GCControllerPlayerIndex4
            else -> GCControllerPlayerIndex1
        }
        return true
    }

    actual fun setPlayStationMuteLed(playerIndex: Int, state: MuteLedState): Boolean {
        // Apple's public GameController framework does not expose DualSense mute LED control separately.
        return slotControllers.getOrNull(playerIndex)?.extendedGamepad is GCDualSenseGamepad
    }

    actual fun setPlayStationAdaptiveTrigger(
        playerIndex: Int,
        side: AdaptiveTriggerSide,
        effect: AdaptiveTriggerEffect
    ): Boolean {
        val ctrl = slotControllers.getOrNull(playerIndex) ?: return false
        val ds = ctrl.extendedGamepad as? GCDualSenseGamepad ?: return false
        var applied = false
        if (side == AdaptiveTriggerSide.LEFT || side == AdaptiveTriggerSide.BOTH) {
            val leftAdaptive = ds.leftTrigger as? GCDualSenseAdaptiveTrigger
            if (leftAdaptive != null) {
                applyAdaptiveTriggerEffect(leftAdaptive, effect)
                applied = true
            }
        }
        if (side == AdaptiveTriggerSide.RIGHT || side == AdaptiveTriggerSide.BOTH) {
            val rightAdaptive = ds.rightTrigger as? GCDualSenseAdaptiveTrigger
            if (rightAdaptive != null) {
                applyAdaptiveTriggerEffect(rightAdaptive, effect)
                applied = true
            }
        }
        return applied
    }

    actual fun setSwitchHdRumble(playerIndex: Int, command: SwitchHdRumbleCommand): Boolean {
        val clamped = command.clamped()
        val maxAmp = maxOf(clamped.leftLowAmp, clamped.leftHighAmp, clamped.rightLowAmp, clamped.rightHighAmp)
        return setRumble(
            playerIndex,
            GamepadRumbleRequest(
                lowFrequency = maxOf(clamped.leftLowAmp, clamped.rightLowAmp),
                highFrequency = maxOf(clamped.leftHighAmp, clamped.rightHighAmp),
                durationMs = if (maxAmp <= 0f) 0 else clamped.durationMs
            )
        )
    }

    actual fun setSwitchPlayerLeds(playerIndex: Int, mask: Int): Boolean =
        setPlayStationPlayerLedMask(playerIndex, mask)

    actual fun setSwitchHomeLedPattern(playerIndex: Int, pattern: SwitchHomeLedPattern): Boolean {
        val ctrl = slotControllers.getOrNull(playerIndex) ?: return false
        val light = ctrl.light ?: return false
        val level = pattern.clamped().maxIntensity
        light.color = GCColor(red = 0f, green = level * 0.45f, blue = level)
        return true
    }

    private fun applyAdaptiveTriggerEffect(
        trigger: GCDualSenseAdaptiveTrigger,
        effect: AdaptiveTriggerEffect
    ) {
        when (effect) {
            AdaptiveTriggerEffect.Off -> trigger.setModeOff()
            is AdaptiveTriggerEffect.ContinuousResistance -> trigger.setModeFeedbackWithStartPosition(
                startPosition = effect.startPosition.coerceIn(0f, 1f),
                resistiveStrength = effect.force.coerceIn(0f, 1f)
            )
            is AdaptiveTriggerEffect.SectionResistance -> trigger.setModeWeaponWithStartPosition(
                startPosition = effect.startPosition.coerceIn(0f, 1f),
                endPosition = effect.endPosition.coerceIn(0f, 1f),
                resistiveStrength = effect.force.coerceIn(0f, 1f)
            )
            is AdaptiveTriggerEffect.VibrationPulse -> trigger.setModeVibrationWithStartPosition(
                startPosition = effect.startPosition.coerceIn(0f, 1f),
                amplitude = effect.amplitude.coerceIn(0f, 1f),
                frequency = (effect.frequencyHz / 255f).coerceIn(0f, 1f)
            )
        }
    }

    private fun buildPlayStationFeatures(
        ctrl: GCController,
        tp1: GCControllerDirectionPad?,
        tp2: GCControllerDirectionPad?,
        clicked: Boolean,
        previous: PlayStationControllerFeatures?
    ): PlayStationControllerFeatures {
        val f1 = readTouchpadFinger(0, tp1)
        val f2 = readTouchpadFinger(1, tp2)
        val dx = if (f1.active && previous?.touchpad?.finger1?.active == true) {
            f1.x - previous.touchpad.finger1.x
        } else 0f
        val dy = if (f1.active && previous?.touchpad?.finger1?.active == true) {
            f1.y - previous.touchpad.finger1.y
        } else 0f
        val lightColor = ctrl.light?.color
        val r = lightColor?.let { (it.red * 255f).toInt().coerceIn(0, 255) } ?: (previous?.lightbarR ?: 0)
        val g = lightColor?.let { (it.green * 255f).toInt().coerceIn(0, 255) } ?: (previous?.lightbarG ?: 0)
        val b = lightColor?.let { (it.blue * 255f).toInt().coerceIn(0, 255) } ?: (previous?.lightbarB ?: 255)
        return PlayStationControllerFeatures(
            touchpad = TouchpadState(
                finger1 = f1,
                finger2 = f2,
                isTouchpadClicked = clicked,
                deltaX = dx,
                deltaY = dy
            ),
            lightbarR = r,
            lightbarG = g,
            lightbarB = b,
            playerIndicatorLedMask = previous?.playerIndicatorLedMask ?: 0,
            muteLedState = previous?.muteLedState ?: MuteLedState.OFF,
            leftAdaptiveTrigger = previous?.leftAdaptiveTrigger ?: AdaptiveTriggerEffect.Off,
            rightAdaptiveTrigger = previous?.rightAdaptiveTrigger ?: AdaptiveTriggerEffect.Off
        )
    }

    private fun readTouchpadFinger(id: Int, pad: GCControllerDirectionPad?): TouchpadFingerState {
        if (pad == null) return TouchpadFingerState(touchId = id, active = false)
        val rawX = pad.xAxis.value
        val rawY = pad.yAxis.value
        val active = abs(rawX) > 1e-4f || abs(rawY) > 1e-4f
        return TouchpadFingerState(
            touchId = id,
            active = active,
            x = if (active) ((rawX + 1f) * 0.5f).coerceIn(0f, 1f) else 0f,
            y = if (active) ((rawY + 1f) * 0.5f).coerceIn(0f, 1f) else 0f
        )
    }

    private fun readMotion(slot: Int, motion: GCMotion?, nowMs: Long): SixAxisMotionState {
        if (motion == null) return SixAxisMotionState.UNAVAILABLE
        if (motion.sensorsRequireManualActivation && !motion.sensorsActive) {
            motion.setSensorsActive(true)
        }
        val prevMs = lastMotionSampleMs[slot]
        lastMotionSampleMs[slot] = nowMs
        val dtSec = if (prevMs > 0L) ((nowMs - prevMs).coerceIn(1L, 100L) / 1000f) else (1f / 60f)

        var gx = 0f
        var gy = 0f
        var gz = 0f
        if (motion.hasRotationRate) {
            motion.rotationRate.useContents {
                gx = x.toFloat()
                gy = y.toFloat()
                gz = z.toFloat()
            }
        }
        var ax = 0f
        var ay = 1f
        var az = 0f
        if (motion.hasGravityAndUserAcceleration) {
            var gravX = 0f
            var gravY = -1f
            var gravZ = 0f
            motion.gravity.useContents {
                gravX = x.toFloat()
                gravY = y.toFloat()
                gravZ = z.toFloat()
            }
            var userX = 0f
            var userY = 0f
            var userZ = 0f
            motion.userAcceleration.useContents {
                userX = x.toFloat()
                userY = y.toFloat()
                userZ = z.toFloat()
            }
            ax = userX - gravX
            ay = userY - gravY
            az = userZ - gravZ
        } else {
            motion.acceleration.useContents {
                ax = x.toFloat()
                ay = y.toFloat()
                az = z.toFloat()
            }
        }

        if (motion.hasAttitude) {
            var qx = 0f
            var qy = 0f
            var qz = 0f
            var qw = 1f
            motion.attitude.useContents {
                qx = x.toFloat()
                qy = y.toFloat()
                qz = z.toFloat()
                qw = w.toFloat()
            }
            var gravX = 0f
            var gravY = -1f
            var gravZ = 0f
            if (motion.hasGravityAndUserAcceleration) {
                motion.gravity.useContents {
                    gravX = x.toFloat()
                    gravY = y.toFloat()
                    gravZ = z.toFloat()
                }
            }
            val sinp = (2f * (qw * qx - qy * qz)).coerceIn(-1f, 1f)
            val pitch = asin(sinp)
            val yaw = atan2(2f * (qw * qy + qx * qz), 1f - 2f * (qx * qx + qy * qy))
            val roll = atan2(2f * (qw * qz + qx * qy), 1f - 2f * (qx * qx + qz * qz))
            return SixAxisMotionState(
                gyroRadPerSecX = gx,
                gyroRadPerSecY = gy,
                gyroRadPerSecZ = gz,
                accelGX = ax,
                accelGY = ay,
                accelGZ = az,
                gravityX = gravX,
                gravityY = gravY,
                gravityZ = gravZ,
                orientationQuatX = qx,
                orientationQuatY = qy,
                orientationQuatZ = qz,
                orientationQuatW = qw,
                pitchRad = pitch,
                yawRad = yaw,
                rollRad = roll,
                timestampMs = nowMs,
                isAvailable = true
            )
        }
        return sensorFusions[slot].update(gx, gy, gz, ax, ay, az, dtSec, nowMs)
    }

    private fun playHapticOnLocality(
        ctrl: GCController,
        engineKey: String,
        locality: String,
        intensity: Float,
        sharpness: Float,
        durationSec: Double
    ): Boolean {
        val haptics = ctrl.haptics ?: return false
        val engine = hapticEngines.getOrPut(engineKey) {
            val rawEngine = haptics.createEngineWithLocality(locality) ?: return false
            interpretObjCPointer<CHHapticEngine>(rawEngine.objcPtr())
        }
        engine.startAndReturnError(null)
        if (intensity <= 0.001f || durationSec <= 0.0) {
            return true
        }
        val intensityParam = CHHapticEventParameter(
            parameterID = CHHapticEventParameterIDHapticIntensity,
            value = intensity
        )
        val sharpnessParam = CHHapticEventParameter(
            parameterID = CHHapticEventParameterIDHapticSharpness,
            value = sharpness
        )
        val event = CHHapticEvent(
            eventType = CHHapticEventTypeHapticContinuous,
            parameters = listOf(intensityParam, sharpnessParam),
            relativeTime = 0.0,
            duration = durationSec
        )
        val pattern = CHHapticPattern(
            events = listOf(event),
            parameters = emptyList<Any>(),
            error = null
        )
        val player = engine.createPlayerWithPattern(pattern, error = null) ?: return false
        player.startAtTime(0.0, error = null)
        return true
    }

    private fun resolveSlot(
        ctrl: GCController,
        used: BooleanArray,
        maxPlayers: Int
    ): Int? {
        val preferred = when (ctrl.playerIndex) {
            GCControllerPlayerIndex1 -> 0
            GCControllerPlayerIndex2 -> 1
            GCControllerPlayerIndex3 -> 2
            GCControllerPlayerIndex4 -> 3
            else -> -1
        }
        if (preferred in 0 until maxPlayers && !used[preferred]) return preferred
        for (i in 0 until maxPlayers) {
            if (!used[i]) return i
        }
        return null
    }

    private fun inferVendorProduct(ctrl: GCController, name: String): Pair<Int, Int> {
        val ext = ctrl.extendedGamepad
        return when {
            ext is GCDualSenseGamepad -> GamepadLayoutMapper.VENDOR_SONY to 0x0CE6
            ext is GCDualShockGamepad -> GamepadLayoutMapper.VENDOR_SONY to 0x09CC
            ext is GCXboxGamepad -> GamepadLayoutMapper.VENDOR_MICROSOFT to 0x0B12
            else -> {
                val lower = name.lowercase()
                when {
                    "dualsense" in lower || "ps5" in lower -> GamepadLayoutMapper.VENDOR_SONY to 0x0CE6
                    "dualshock" in lower || "sony" in lower || "playstation" in lower ->
                        GamepadLayoutMapper.VENDOR_SONY to 0x09CC
                    "switch" in lower || "joy-con" in lower || "nintendo" in lower ->
                        GamepadLayoutMapper.VENDOR_NINTENDO to 0x2009
                    "elite" in lower -> GamepadLayoutMapper.VENDOR_MICROSOFT to 0x0B00
                    "xbox" in lower -> GamepadLayoutMapper.VENDOR_MICROSOFT to 0x0B12
                    else -> GamepadLayoutMapper.VENDOR_MICROSOFT to 0x02EA
                }
            }
        }
    }

    private fun TimeSource.Monotonic.ValueTimeMark.elapsedMicros(): Long =
        elapsedNow().inWholeMicroseconds
}
