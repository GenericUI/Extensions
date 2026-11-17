package com.nexustools.genericui.ext.gamepad

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GamepadProfileAndSixAxisTest {
    @Test
    fun canonicalProfilesAndHardwareLabelsResolveAccurately() {
        assertEquals(
            ControllerHardwareProfile.PLAYSTATION_DUALSENSE,
            GamepadLayoutMapper.detectProfile(GamepadLayoutMapper.VENDOR_SONY, 0x0CE6, "DualSense Wireless Controller")
        )
        assertEquals(
            ControllerHardwareProfile.PLAYSTATION_DUALSHOCK4,
            GamepadLayoutMapper.detectProfile(GamepadLayoutMapper.VENDOR_SONY, 0x09CC, "DUALSHOCK 4")
        )
        assertEquals(
            ControllerHardwareProfile.NINTENDO_SWITCH_PRO,
            GamepadLayoutMapper.detectProfile(GamepadLayoutMapper.VENDOR_NINTENDO, 0x2009, "Pro Controller")
        )
        assertEquals(
            ControllerHardwareProfile.NINTENDO_JOYCON_PAIR,
            GamepadLayoutMapper.detectProfile(GamepadLayoutMapper.VENDOR_NINTENDO, 0x200E, "Joy-Con L/R")
        )
        assertEquals(
            ControllerHardwareProfile.XBOX_ELITE,
            GamepadLayoutMapper.detectProfile(GamepadLayoutMapper.VENDOR_MICROSOFT, 0x0B00, "Xbox Elite Series 2")
        )
        assertEquals(
            ControllerHardwareProfile.XBOX_ONE_SERIES,
            GamepadLayoutMapper.detectProfile(GamepadLayoutMapper.VENDOR_MICROSOFT, 0x0B12, "Xbox Wireless Controller")
        )

        // Xbox prompts
        assertEquals("A", ControllerButtonLabels.resolveLabel(GamepadButton.A, ControllerHardwareProfile.XBOX_ONE_SERIES))
        assertEquals("LB", ControllerButtonLabels.resolveLabel(GamepadButton.LEFT_BUMPER, ControllerHardwareProfile.XBOX_ELITE))
        assertEquals("Xbox", ControllerButtonLabels.resolveLabel(GamepadButton.GUIDE, ControllerHardwareProfile.XBOX_ONE_SERIES))

        // PlayStation prompts
        assertEquals("Cross", ControllerButtonLabels.resolveLabel(GamepadButton.A, ControllerHardwareProfile.PLAYSTATION_DUALSENSE))
        assertEquals("Circle", ControllerButtonLabels.resolveLabel(GamepadButton.B, ControllerHardwareProfile.PLAYSTATION_DUALSENSE))
        assertEquals("Square", ControllerButtonLabels.resolveLabel(GamepadButton.X, ControllerHardwareProfile.PLAYSTATION_DUALSENSE))
        assertEquals("Triangle", ControllerButtonLabels.resolveLabel(GamepadButton.Y, ControllerHardwareProfile.PLAYSTATION_DUALSENSE))
        assertEquals("L1", ControllerButtonLabels.resolveLabel(GamepadButton.LEFT_BUMPER, ControllerHardwareProfile.PLAYSTATION_DUALSHOCK4))
        assertEquals("Create", ControllerButtonLabels.resolveLabel(GamepadButton.VIEW, ControllerHardwareProfile.PLAYSTATION_DUALSENSE))
        assertEquals("Options", ControllerButtonLabels.resolveLabel(GamepadButton.MENU, ControllerHardwareProfile.PLAYSTATION_DUALSENSE))

        // Nintendo Switch Pro positional vs printed labels
        assertEquals("B", ControllerButtonLabels.resolveLabel(GamepadButton.A, ControllerHardwareProfile.NINTENDO_SWITCH_PRO, usePositionalSwitchLayout = true))
        assertEquals("A", ControllerButtonLabels.resolveLabel(GamepadButton.B, ControllerHardwareProfile.NINTENDO_SWITCH_PRO, usePositionalSwitchLayout = true))
        assertEquals("Y", ControllerButtonLabels.resolveLabel(GamepadButton.X, ControllerHardwareProfile.NINTENDO_SWITCH_PRO, usePositionalSwitchLayout = true))
        assertEquals("X", ControllerButtonLabels.resolveLabel(GamepadButton.Y, ControllerHardwareProfile.NINTENDO_SWITCH_PRO, usePositionalSwitchLayout = true))
        assertEquals("ZL", ControllerButtonLabels.resolveLabel(GamepadButton.LEFT_TRIGGER_BUTTON, ControllerHardwareProfile.NINTENDO_SWITCH_PRO))
        assertEquals("ZR", ControllerButtonLabels.resolveLabel(GamepadButton.RIGHT_TRIGGER_BUTTON, ControllerHardwareProfile.NINTENDO_SWITCH_PRO))
        assertEquals("+", ControllerButtonLabels.resolveLabel(GamepadButton.MENU, ControllerHardwareProfile.NINTENDO_SWITCH_PRO))
        assertEquals("-", ControllerButtonLabels.resolveLabel(GamepadButton.VIEW, ControllerHardwareProfile.NINTENDO_SWITCH_PRO))
        assertEquals("Capture", ControllerButtonLabels.resolveLabel(GamepadButton.SHARE, ControllerHardwareProfile.NINTENDO_SWITCH_PRO))

        assertEquals("A", ControllerButtonLabels.resolveLabel(GamepadButton.A, ControllerHardwareProfile.NINTENDO_SWITCH_PRO, usePositionalSwitchLayout = false))
        assertEquals("B", ControllerButtonLabels.resolveLabel(GamepadButton.B, ControllerHardwareProfile.NINTENDO_SWITCH_PRO, usePositionalSwitchLayout = false))
    }

    @Test
    fun switchPositionalVsPrintedRemappingAndDualSenseAndSdlDbMappingWork() {
        // Switch Pro raw HID button 0 = South B, 1 = East A
        val rawSwitchButtons = BooleanArray(18)
        rawSwitchButtons[0] = true

        val positionalSnap = GamepadLayoutMapper.mapRawInput(
            playerIndex = 0,
            buttons = rawSwitchButtons,
            axes = FloatArray(6),
            vendorId = GamepadLayoutMapper.VENDOR_NINTENDO,
            productId = 0x2009,
            deviceName = "Switch Pro",
            usePositionalSwitchLayout = true
        )
        assertTrue(positionalSnap.isButtonDown(GamepadButton.A))
        assertFalse(positionalSnap.isButtonDown(GamepadButton.B))

        val printedSnap = GamepadLayoutMapper.mapRawInput(
            playerIndex = 0,
            buttons = rawSwitchButtons,
            axes = FloatArray(6),
            vendorId = GamepadLayoutMapper.VENDOR_NINTENDO,
            productId = 0x2009,
            deviceName = "Switch Pro",
            usePositionalSwitchLayout = false
        )
        assertFalse(printedSnap.isButtonDown(GamepadButton.A))
        assertTrue(printedSnap.isButtonDown(GamepadButton.B))

        // Sony DualSense HID mapping: Square=0, Cross=1, L2=axis 3, R2=axis 4 (-1..1 -> 0..1), RY=axis 5
        val sonyButtons = BooleanArray(18).apply { this[1] = true; this[4] = true }
        val sonyAxes = floatArrayOf(0.03f, -0.02f, 0.8f, -1f, 0.5f, 0f)
        val dualSenseSnap = GamepadLayoutMapper.mapRawInput(
            playerIndex = 0,
            buttons = sonyButtons,
            axes = sonyAxes,
            hatMask = GamepadLayoutMapper.povAngleToHatMask(9000), // East -> DPAD_RIGHT
            vendorId = GamepadLayoutMapper.VENDOR_SONY,
            productId = 0x0CE6,
            deviceName = "DualSense"
        )
        assertTrue(dualSenseSnap.isButtonDown(GamepadButton.A))
        assertTrue(dualSenseSnap.isButtonDown(GamepadButton.LEFT_BUMPER))
        assertTrue(dualSenseSnap.isButtonDown(GamepadButton.DPAD_RIGHT))
        assertEquals(0f, dualSenseSnap.leftStick.x, 1e-5f) // inside radial deadzone
        assertTrue(dualSenseSnap.rightTrigger > 0.65f)
        assertNotNull(dualSenseSnap.playStation)

        // SDL GameControllerDB line parsing
        val sdl = GamepadLayoutMapper.parseSdlMappingString(
            "030000006d04000018c2000010010000,Logitech F310 Gamepad," +
                "a:b1,b:b2,x:b0,y:b3,back:b8,start:b9,leftshoulder:b4,rightshoulder:b5," +
                "dpup:h0.1,dpdown:h0.4,dpleft:h0.8,dpright:h0.2,leftx:a0,lefty:a1~,righttrigger:a5"
        )
        assertNotNull(sdl)
        assertEquals(1, sdl.rawButtonFor(GamepadButton.A))
        assertTrue(sdl.isAxisInverted(GamepadAxis.LEFT_STICK_Y))
    }

    @Test
    fun sixAxisSensorFusionCalibratesBiasIntegratesGyroAndFusesGravityTilt() {
        val fusion = SixAxisSensorFusion(alpha = 0.96f)
        fusion.calibrateFromStationarySamples(
            samplesGyroX = floatArrayOf(0.10f, 0.12f, 0.08f),
            samplesGyroY = floatArrayOf(-0.05f, -0.05f, -0.05f),
            samplesGyroZ = floatArrayOf(0.02f, 0.02f, 0.02f)
        )
        assertEquals(0.10f, fusion.gyroBiasX, 1e-4f)
        assertEquals(-0.05f, fusion.gyroBiasY, 1e-4f)

        // Stationary samples carrying constant bias produce near-zero calibrated gyro & flat pose
        val flat = fusion.update(
            gyroRadX = 0.10f,
            gyroRadY = -0.05f,
            gyroRadZ = 0.02f,
            accelGX = 0f,
            accelGY = 1f,
            accelGZ = 0f,
            dtSeconds = 0.016f,
            timestampMs = 16L
        )
        assertTrue(flat.isAvailable)
        assertEquals(0f, flat.gyroRadPerSecX, 1e-5f)
        assertEquals(0f, flat.pitchRad, 1e-3f)
        assertEquals(0f, flat.rollRad, 1e-3f)
        assertEquals(-1f, flat.gravityY, 1e-3f)

        // Pure gyro yaw integration: rotate +1.0 rad/s around Y for 0.5s -> ~0.5 rad yaw
        var state = flat
        for (step in 1..25) {
            state = fusion.update(
                gyroRadX = 0.10f,
                gyroRadY = -0.05f + 1.0f,
                gyroRadZ = 0.02f,
                accelGX = 0f,
                accelGY = 1f,
                accelGZ = 0f,
                dtSeconds = 0.02f,
                timestampMs = (step * 20L) + 16L
            )
        }
        assertEquals(0.5f, state.yawRad, 0.02f)
        val quatNorm = sqrt(
            state.orientationQuatX * state.orientationQuatX +
                state.orientationQuatY * state.orientationQuatY +
                state.orientationQuatZ * state.orientationQuatZ +
                state.orientationQuatW * state.orientationQuatW
        )
        assertEquals(1f, quatNorm, 1e-4f)

        // Pitch controller 45 degrees nose-up: accelGZ = -sin(45 deg), accelGY = cos(45 deg)
        val sin45 = 0.70710677f
        for (step in 1..150) {
            state = fusion.update(
                gyroRadX = 0.10f,
                gyroRadY = -0.05f,
                gyroRadZ = 0.02f,
                accelGX = 0f,
                accelGY = sin45,
                accelGZ = -sin45,
                dtSeconds = 0.02f
            )
        }
        assertEquals((PI / 4.0).toFloat(), state.pitchRad, 0.05f)
    }

    @Test
    fun controllerFamilyUniqueFeaturesWorkThroughHandleAndManager() {
        val manager = GamepadManager()

        // 1. PlayStation DualSense touchpad + lightbar + player LEDs + mute LED + adaptive triggers
        manager.setSyntheticController(
            GamepadFrameSnapshot(
                playerIndex = 0,
                connected = true,
                deviceName = "DualSense Wireless Controller",
                vendorId = GamepadLayoutMapper.VENDOR_SONY,
                productId = 0x0CE6,
                profile = ControllerHardwareProfile.PLAYSTATION_DUALSENSE,
                playStation = PlayStationControllerFeatures(
                    touchpad = TouchpadState(
                        finger1 = TouchpadFingerState(touchId = 1, active = true, x = 0.25f, y = 0.6f),
                        finger2 = TouchpadFingerState(touchId = 2, active = true, x = 0.75f, y = 0.4f),
                        isTouchpadClicked = true
                    )
                )
            )
        )
        manager.pollAll()
        val psHandle = manager.controller(0)
        assertEquals(ControllerFamily.PLAYSTATION, psHandle.family)
        assertEquals(2, psHandle.playStationFeatures?.touchpad?.activeFingerCount)
        assertTrue(psHandle.playStationFeatures?.touchpad?.isTouchpadClicked == true)

        psHandle.setLightbarColor(255, 64, 128)
        psHandle.setPlayerIndicatorLedMask(0b00101)
        psHandle.setMicrophoneMuteLed(MuteLedState.BREATHING)
        val rightEffect = AdaptiveTriggerEffect.SectionResistance(startPosition = 0.25f, endPosition = 0.65f, force = 0.9f)
        val leftEffect = AdaptiveTriggerEffect.VibrationPulse(startPosition = 0.1f, amplitude = 0.8f, frequencyHz = 32f)
        psHandle.setAdaptiveTrigger(AdaptiveTriggerSide.RIGHT, rightEffect)
        psHandle.setAdaptiveTrigger(AdaptiveTriggerSide.LEFT, leftEffect)

        assertEquals(255, psHandle.playStationFeatures?.lightbarR)
        assertEquals(64, psHandle.playStationFeatures?.lightbarG)
        assertEquals(128, psHandle.playStationFeatures?.lightbarB)
        assertEquals(MuteLedState.BREATHING, psHandle.playStationFeatures?.muteLedState)
        assertEquals(rightEffect, psHandle.playStationFeatures?.rightAdaptiveTrigger)
        assertEquals(leftEffect, psHandle.playStationFeatures?.leftAdaptiveTrigger)

        val swipe = TouchpadState(
            finger1 = TouchpadFingerState(touchId = 1, active = true, x = 0.55f, y = 0.7f)
        ).computeSwipeDelta(psHandle.playStationFeatures!!.touchpad)
        assertEquals(0.30f, swipe.x, 1e-4f)
        assertEquals(0.10f, swipe.y, 1e-4f)

        // 2. Nintendo Switch Pro HD Rumble + player LEDs + Home LED + NFC + positional toggle
        manager.setSyntheticController(
            GamepadFrameSnapshot(
                playerIndex = 1,
                connected = true,
                deviceName = "Nintendo Switch Pro Controller",
                vendorId = GamepadLayoutMapper.VENDOR_NINTENDO,
                productId = 0x2009,
                profile = ControllerHardwareProfile.NINTENDO_SWITCH_PRO,
                switchFeatures = SwitchControllerFeatures(
                    isCapturePressed = true,
                    nfcStatus = SwitchNfcStatus.TAG_DETECTED
                )
            )
        )
        manager.pollAll()
        val swHandle = manager.controller(1)
        val hd = SwitchHdRumbleCommand(leftHighFreqHz = 420f, leftHighAmp = 0.7f, rightLowFreqHz = 160f, rightLowAmp = 0.5f)
        swHandle.setHdRumble(hd)
        swHandle.setPlayerLeds(0b0011)
        swHandle.setHomeLedPattern(SwitchHomeLedPattern.SLOW_BREATHE)
        swHandle.setUsePositionalActionButtons(false)

        assertEquals(ControllerFamily.NINTENDO_SWITCH, swHandle.family)
        assertEquals(hd, swHandle.switchFeatures?.lastHdRumble)
        assertEquals(0b0011, swHandle.switchFeatures?.playerLedMask)
        assertEquals(SwitchHomeLedPattern.SLOW_BREATHE, swHandle.switchFeatures?.homeLedPattern)
        assertEquals(SwitchNfcStatus.TAG_DETECTED, swHandle.switchFeatures?.nfcStatus)
        assertFalse(swHandle.switchFeatures?.usePositionalActionButtons ?: true)
        assertEquals("A", swHandle.snapshot.resolveButtonLabel(GamepadButton.A))

        // 3. Xbox Elite 4-motor impulse triggers + Share + P1..P4 paddles
        manager.setSyntheticController(
            GamepadFrameSnapshot(
                playerIndex = 2,
                connected = true,
                deviceName = "Xbox Elite Series 2",
                vendorId = GamepadLayoutMapper.VENDOR_MICROSOFT,
                productId = 0x0B00,
                profile = ControllerHardwareProfile.XBOX_ELITE,
                xbox = XboxControllerFeatures(isSharePressed = true, elitePaddleMask = 0b0101)
            )
        )
        manager.pollAll()
        val xbHandle = manager.controller(2)
        val impulse = XboxImpulseRumbleCommand(0.4f, 0.6f, leftTriggerMotor = 0.85f, rightTriggerMotor = 0.25f)
        xbHandle.setImpulseRumble(impulse)

        assertTrue(xbHandle.xboxFeatures?.isSharePressed == true)
        assertTrue(xbHandle.xboxFeatures?.isElitePaddleDown(1) == true)
        assertFalse(xbHandle.xboxFeatures?.isElitePaddleDown(2) == true)
        assertTrue(xbHandle.xboxFeatures?.isElitePaddleDown(3) == true)
        assertEquals(impulse, xbHandle.xboxFeatures?.lastImpulseRumble)
    }
}
