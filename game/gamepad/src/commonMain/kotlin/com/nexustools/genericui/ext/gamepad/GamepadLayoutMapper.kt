package com.nexustools.genericui.ext.gamepad

import kotlin.math.hypot

/**
 * Auto-remaps DualShock 4, DualSense, Nintendo Switch Pro, Joy-Con, Xbox Series/Elite, 8BitDo,
 * Logitech, generic HID controllers, and SDL2/SDL3 GameControllerDB mapping strings into the
 * canonical Xbox-style [GamepadFrameSnapshot] layout with radial thumbstick deadzones, POV hat
 * decoding, and optional Switch positional vs printed face-button remapping.
 */
object GamepadLayoutMapper {
    const val VENDOR_MICROSOFT: Int = 0x045E
    const val VENDOR_SONY: Int = 0x054C
    const val VENDOR_NINTENDO: Int = 0x057E
    const val VENDOR_8BITDO: Int = 0x2DC8
    const val VENDOR_LOGITECH: Int = 0x046D

    const val HAT_UP: Int = 1
    const val HAT_RIGHT: Int = 2
    const val HAT_DOWN: Int = 4
    const val HAT_LEFT: Int = 8

    const val DEFAULT_STICK_DEADZONE: Float = 0.14f
    const val DEFAULT_TRIGGER_DEADZONE: Float = 0.05f

    private val customMappingsByVidPid = mutableMapOf<Long, GamepadHardwareMapping>()
    private val customMappingsByName = mutableMapOf<String, GamepadHardwareMapping>()

    fun detectProfile(
        vendorId: Int,
        productId: Int,
        deviceName: String
    ): ControllerHardwareProfile {
        val lower = deviceName.lowercase()
        return when (vendorId) {
            VENDOR_MICROSOFT -> if (
                productId == 0x02E3 || productId == 0x0B00 || productId == 0x0B05 || productId == 0x0B22 ||
                "elite" in lower
            ) {
                ControllerHardwareProfile.XBOX_ELITE
            } else {
                ControllerHardwareProfile.XBOX_ONE_SERIES
            }

            VENDOR_SONY -> if (
                productId == 0x0CE6 || productId == 0x0DF2 || "dualsense" in lower || "ps5" in lower
            ) {
                ControllerHardwareProfile.PLAYSTATION_DUALSENSE
            } else {
                ControllerHardwareProfile.PLAYSTATION_DUALSHOCK4
            }

            VENDOR_NINTENDO -> if (
                productId == 0x2006 || productId == 0x2007 || productId == 0x200E ||
                "joy-con" in lower || "joycon" in lower
            ) {
                ControllerHardwareProfile.NINTENDO_JOYCON_PAIR
            } else {
                ControllerHardwareProfile.NINTENDO_SWITCH_PRO
            }

            VENDOR_8BITDO -> if ("xbox" in lower || "xinput" in lower || "x-input" in lower || productId == 0x2002) {
                ControllerHardwareProfile.XBOX_ONE_SERIES
            } else {
                ControllerHardwareProfile.NINTENDO_SWITCH_PRO
            }

            VENDOR_LOGITECH -> if ("f310" in lower || "f710" in lower || "xinput" in lower ||
                productId == 0xC21D || productId == 0xC21F
            ) {
                ControllerHardwareProfile.XBOX_ONE_SERIES
            } else {
                ControllerHardwareProfile.GENERIC_XINPUT
            }

            else -> when {
                "dualsense" in lower || "ps5" in lower -> ControllerHardwareProfile.PLAYSTATION_DUALSENSE
                "dualshock" in lower || "sony" in lower || "playstation" in lower || "ps4" in lower ->
                    ControllerHardwareProfile.PLAYSTATION_DUALSHOCK4
                "joy-con" in lower || "joycon" in lower -> ControllerHardwareProfile.NINTENDO_JOYCON_PAIR
                "nintendo" in lower || "switch" in lower || "pro controller" in lower ->
                    ControllerHardwareProfile.NINTENDO_SWITCH_PRO
                "elite" in lower -> ControllerHardwareProfile.XBOX_ELITE
                "xbox" in lower || "xinput" in lower || "x-input" in lower || "x-box" in lower ->
                    ControllerHardwareProfile.XBOX_ONE_SERIES
                else -> ControllerHardwareProfile.GENERIC_XINPUT
            }
        }
    }

    fun applyRadialDeadzone(
        x: Float,
        y: Float,
        deadzone: Float = DEFAULT_STICK_DEADZONE
    ): GamepadAxis2D {
        val dz = deadzone.coerceIn(0f, 0.95f)
        val mag = hypot(x, y)
        if (mag <= dz || mag <= 1e-6f) {
            return GamepadAxis2D.ZERO
        }
        val clampedMag = mag.coerceAtMost(1f)
        val rescaled = ((clampedMag - dz) / (1f - dz)).coerceIn(0f, 1f)
        val invMag = 1f / mag
        return GamepadAxis2D(x * invMag * rescaled, y * invMag * rescaled)
    }

    fun normalizeTrigger(
        raw: Float,
        isSignedMinusOneToOne: Boolean = false,
        deadzone: Float = DEFAULT_TRIGGER_DEADZONE
    ): Float {
        val normalized = if (isSignedMinusOneToOne) {
            ((raw + 1f) * 0.5f).coerceIn(0f, 1f)
        } else {
            raw.coerceIn(0f, 1f)
        }
        val dz = deadzone.coerceIn(0f, 0.95f)
        if (normalized <= dz) return 0f
        return ((normalized - dz) / (1f - dz)).coerceIn(0f, 1f)
    }

    fun povAngleToHatMask(povHundredthsDeg: Int): Int {
        if (povHundredthsDeg < 0 || povHundredthsDeg >= 36000) return 0
        var mask = 0
        if (povHundredthsDeg >= 31500 || povHundredthsDeg <= 4500) mask = mask or HAT_UP
        if (povHundredthsDeg in 4500..13500) mask = mask or HAT_RIGHT
        if (povHundredthsDeg in 13500..22500) mask = mask or HAT_DOWN
        if (povHundredthsDeg in 22500..31500) mask = mask or HAT_LEFT
        return mask
    }

    fun hatAxesToMask(hatX: Float, hatY: Float): Int {
        var mask = 0
        if (hatY <= -0.5f) mask = mask or HAT_UP
        if (hatY >= 0.5f) mask = mask or HAT_DOWN
        if (hatX <= -0.5f) mask = mask or HAT_LEFT
        if (hatX >= 0.5f) mask = mask or HAT_RIGHT
        return mask
    }

    fun parseSdlMappingString(sdlLine: String): GamepadHardwareMapping? {
        val trimmed = sdlLine.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
        val parts = trimmed.split(',').map { it.trim() }
        if (parts.size < 3) return null

        val guid = parts[0]
        val name = parts[1].ifEmpty { "SDL Gamepad" }
        val buttons = IntArray(GamepadButton.entries.size) { GamepadHardwareMapping.UNBOUND }
        val axes = IntArray(GamepadAxis.entries.size) { -1 }
        val inverted = BooleanArray(GamepadAxis.entries.size)

        for (i in 2 until parts.size) {
            val token = parts[i]
            if (token.isEmpty()) continue
            val colonIdx = token.indexOf(':')
            if (colonIdx <= 0 || colonIdx >= token.lastIndex) continue
            val key = token.substring(0, colonIdx).lowercase()
            val value = token.substring(colonIdx + 1).lowercase()

            when (key) {
                "a" -> parseSdlButtonTarget(value)?.let { buttons[GamepadButton.A.standardIndex] = it }
                "b" -> parseSdlButtonTarget(value)?.let { buttons[GamepadButton.B.standardIndex] = it }
                "x" -> parseSdlButtonTarget(value)?.let { buttons[GamepadButton.X.standardIndex] = it }
                "y" -> parseSdlButtonTarget(value)?.let { buttons[GamepadButton.Y.standardIndex] = it }
                "leftshoulder" -> parseSdlButtonTarget(value)?.let { buttons[GamepadButton.LEFT_BUMPER.standardIndex] = it }
                "rightshoulder" -> parseSdlButtonTarget(value)?.let { buttons[GamepadButton.RIGHT_BUMPER.standardIndex] = it }
                "back" -> parseSdlButtonTarget(value)?.let { buttons[GamepadButton.VIEW.standardIndex] = it }
                "start" -> parseSdlButtonTarget(value)?.let { buttons[GamepadButton.MENU.standardIndex] = it }
                "leftstick" -> parseSdlButtonTarget(value)?.let { buttons[GamepadButton.LEFT_STICK.standardIndex] = it }
                "rightstick" -> parseSdlButtonTarget(value)?.let { buttons[GamepadButton.RIGHT_STICK.standardIndex] = it }
                "dpup" -> parseSdlButtonTarget(value)?.let { buttons[GamepadButton.DPAD_UP.standardIndex] = it }
                "dpdown" -> parseSdlButtonTarget(value)?.let { buttons[GamepadButton.DPAD_DOWN.standardIndex] = it }
                "dpleft" -> parseSdlButtonTarget(value)?.let { buttons[GamepadButton.DPAD_LEFT.standardIndex] = it }
                "dpright" -> parseSdlButtonTarget(value)?.let { buttons[GamepadButton.DPAD_RIGHT.standardIndex] = it }
                "guide" -> parseSdlButtonTarget(value)?.let { buttons[GamepadButton.GUIDE.standardIndex] = it }
                "misc1" -> parseSdlButtonTarget(value)?.let { buttons[GamepadButton.SHARE.standardIndex] = it }
                "lefttrigger" -> {
                    parseSdlButtonTarget(value)?.let { buttons[GamepadButton.LEFT_TRIGGER_BUTTON.standardIndex] = it }
                    parseSdlAxisTarget(value)?.let { (idx, inv) ->
                        axes[GamepadAxis.LEFT_TRIGGER.standardIndex] = idx
                        inverted[GamepadAxis.LEFT_TRIGGER.standardIndex] = inv
                    }
                }
                "righttrigger" -> {
                    parseSdlButtonTarget(value)?.let { buttons[GamepadButton.RIGHT_TRIGGER_BUTTON.standardIndex] = it }
                    parseSdlAxisTarget(value)?.let { (idx, inv) ->
                        axes[GamepadAxis.RIGHT_TRIGGER.standardIndex] = idx
                        inverted[GamepadAxis.RIGHT_TRIGGER.standardIndex] = inv
                    }
                }
                "leftx" -> parseSdlAxisTarget(value)?.let { (idx, inv) ->
                    axes[GamepadAxis.LEFT_STICK_X.standardIndex] = idx
                    inverted[GamepadAxis.LEFT_STICK_X.standardIndex] = inv
                }
                "lefty" -> parseSdlAxisTarget(value)?.let { (idx, inv) ->
                    axes[GamepadAxis.LEFT_STICK_Y.standardIndex] = idx
                    inverted[GamepadAxis.LEFT_STICK_Y.standardIndex] = inv
                }
                "rightx" -> parseSdlAxisTarget(value)?.let { (idx, inv) ->
                    axes[GamepadAxis.RIGHT_STICK_X.standardIndex] = idx
                    inverted[GamepadAxis.RIGHT_STICK_X.standardIndex] = inv
                }
                "righty" -> parseSdlAxisTarget(value)?.let { (idx, inv) ->
                    axes[GamepadAxis.RIGHT_STICK_Y.standardIndex] = idx
                    inverted[GamepadAxis.RIGHT_STICK_Y.standardIndex] = inv
                }
            }
        }

        val (vid, pid) = extractVendorProductFromSdlGuid(guid)
        val profile = detectProfile(vid, pid, name)
        return GamepadHardwareMapping(
            profileName = name,
            profile = profile,
            vendorId = vid,
            productId = pid,
            buttonIndices = buttons,
            axisIndices = axes,
            axisInverted = inverted,
            usesPositionalSwitchSwap = (profile.family == ControllerFamily.NINTENDO_SWITCH)
        )
    }

    fun registerSdlMapping(sdlLine: String): GamepadHardwareMapping? {
        val parsed = parseSdlMappingString(sdlLine) ?: return null
        if (parsed.vendorId != 0 || parsed.productId != 0) {
            customMappingsByVidPid[vidPidKey(parsed.vendorId, parsed.productId)] = parsed
        }
        customMappingsByName[parsed.profileName.lowercase()] = parsed
        return parsed
    }

    fun clearCustomMappings() {
        customMappingsByVidPid.clear()
        customMappingsByName.clear()
    }

    fun resolveMapping(
        vendorId: Int,
        productId: Int,
        deviceName: String,
        usePositionalSwitchLayout: Boolean = true
    ): GamepadHardwareMapping {
        customMappingsByVidPid[vidPidKey(vendorId, productId)]?.let { return it }
        customMappingsByName[deviceName.lowercase()]?.let { return it }
        val profile = detectProfile(vendorId, productId, deviceName)
        return when (profile) {
            ControllerHardwareProfile.XBOX_ONE_SERIES,
            ControllerHardwareProfile.XBOX_ELITE -> GamepadHardwareMapping.standardXbox(profile)
            ControllerHardwareProfile.PLAYSTATION_DUALSHOCK4,
            ControllerHardwareProfile.PLAYSTATION_DUALSENSE -> GamepadHardwareMapping.sonyDualSenseHid(profile)
            ControllerHardwareProfile.NINTENDO_SWITCH_PRO,
            ControllerHardwareProfile.NINTENDO_JOYCON_PAIR ->
                GamepadHardwareMapping.nintendoSwitchPro(usePositionalSwitchLayout, profile)
            ControllerHardwareProfile.GENERIC_XINPUT -> GamepadHardwareMapping(
                profileName = deviceName.ifEmpty { "Generic Controller" },
                profile = profile,
                vendorId = vendorId,
                productId = productId,
                usesPositionalSwitchSwap = false
            )
        }
    }

    fun mapRawInput(
        playerIndex: Int,
        buttons: BooleanArray,
        axes: FloatArray,
        hatMask: Int = 0,
        vendorId: Int = 0,
        productId: Int = 0,
        deviceName: String = "",
        deviceId: String = "pad-$playerIndex",
        alreadyStandardLayout: Boolean = false,
        usePositionalSwitchLayout: Boolean = true,
        invertRawY: Boolean = false,
        triggersAreSigned: Boolean = false,
        stickDeadzone: Float = DEFAULT_STICK_DEADZONE,
        triggerDeadzone: Float = DEFAULT_TRIGGER_DEADZONE,
        batteryLevel: Float? = null,
        motion: SixAxisMotionState = SixAxisMotionState.UNAVAILABLE,
        playStationFeatures: PlayStationControllerFeatures? = null,
        switchFeatures: SwitchControllerFeatures? = null,
        xboxFeatures: XboxControllerFeatures? = null,
        mappingOverride: GamepadHardwareMapping? = null
    ): GamepadFrameSnapshot {
        val detectedProfile = detectProfile(vendorId, productId, deviceName)
        val activeMapping = mappingOverride ?: if (alreadyStandardLayout) {
            if (detectedProfile.family == ControllerFamily.NINTENDO_SWITCH && !usePositionalSwitchLayout) {
                GamepadHardwareMapping.standardXbox(detectedProfile).let { std ->
                    std.copy(
                        buttonIndices = std.buttonIndices.copyOf().apply {
                            this[GamepadButton.A.standardIndex] = GamepadButton.B.standardIndex
                            this[GamepadButton.B.standardIndex] = GamepadButton.A.standardIndex
                            this[GamepadButton.X.standardIndex] = GamepadButton.Y.standardIndex
                            this[GamepadButton.Y.standardIndex] = GamepadButton.X.standardIndex
                        },
                        usesPositionalSwitchSwap = false
                    )
                }
            } else {
                GamepadHardwareMapping.standardXbox(detectedProfile)
            }
        } else {
            resolveMapping(vendorId, productId, deviceName, usePositionalSwitchLayout)
        }

        var pressedMask = 0
        for (btn in GamepadButton.entries) {
            val target = activeMapping.rawButtonFor(btn)
            if (target == GamepadHardwareMapping.UNBOUND) continue
            val hatReq = GamepadHardwareMapping.decodeHatMask(target)
            val axisBtn = GamepadHardwareMapping.decodeAxisButton(target)
            val down = when {
                hatReq != null -> (hatMask and hatReq) != 0
                axisBtn != null -> axes.getOrElse(axisBtn) { 0f } > 0.5f
                target in buttons.indices -> buttons[target]
                else -> false
            }
            if (down) {
                pressedMask = pressedMask or btn.bitMask
            }
        }

        if (hatMask != 0) {
            if ((hatMask and HAT_UP) != 0) pressedMask = pressedMask or GamepadButton.DPAD_UP.bitMask
            if ((hatMask and HAT_RIGHT) != 0) pressedMask = pressedMask or GamepadButton.DPAD_RIGHT.bitMask
            if ((hatMask and HAT_DOWN) != 0) pressedMask = pressedMask or GamepadButton.DPAD_DOWN.bitMask
            if ((hatMask and HAT_LEFT) != 0) pressedMask = pressedMask or GamepadButton.DPAD_LEFT.bitMask
        }

        fun readAxis(axis: GamepadAxis): Float {
            val idx = activeMapping.axisIndexFor(axis)
            val raw = if (idx in axes.indices) axes[idx] else 0f
            return if (activeMapping.isAxisInverted(axis)) -raw else raw
        }

        val rawLx = readAxis(GamepadAxis.LEFT_STICK_X).coerceIn(-1f, 1f)
        val rawLy = (readAxis(GamepadAxis.LEFT_STICK_Y) * if (invertRawY) -1f else 1f).coerceIn(-1f, 1f)
        val rawRx = readAxis(GamepadAxis.RIGHT_STICK_X).coerceIn(-1f, 1f)
        val rawRy = (readAxis(GamepadAxis.RIGHT_STICK_Y) * if (invertRawY) -1f else 1f).coerceIn(-1f, 1f)

        val leftStick = applyRadialDeadzone(rawLx, rawLy, stickDeadzone)
        val rightStick = applyRadialDeadzone(rawRx, rawRy, stickDeadzone)

        val ltAxisIdx = activeMapping.axisIndexFor(GamepadAxis.LEFT_TRIGGER)
        val rtAxisIdx = activeMapping.axisIndexFor(GamepadAxis.RIGHT_TRIGGER)
        val signedTrigs = triggersAreSigned || activeMapping.triggersSignedMinusOneToOne

        var lt = if (ltAxisIdx in axes.indices) {
            normalizeTrigger(readAxis(GamepadAxis.LEFT_TRIGGER), signedTrigs, triggerDeadzone)
        } else 0f
        var rt = if (rtAxisIdx in axes.indices) {
            normalizeTrigger(readAxis(GamepadAxis.RIGHT_TRIGGER), signedTrigs, triggerDeadzone)
        } else 0f

        if (lt <= 0f && (pressedMask and GamepadButton.LEFT_TRIGGER_BUTTON.bitMask) != 0) lt = 1f
        if (rt <= 0f && (pressedMask and GamepadButton.RIGHT_TRIGGER_BUTTON.bitMask) != 0) rt = 1f
        if (lt > 0.25f) pressedMask = pressedMask or GamepadButton.LEFT_TRIGGER_BUTTON.bitMask
        if (rt > 0.25f) pressedMask = pressedMask or GamepadButton.RIGHT_TRIGGER_BUTTON.bitMask

        val resolvedPs = if (detectedProfile.family == ControllerFamily.PLAYSTATION) {
            (playStationFeatures ?: PlayStationControllerFeatures()).let { ps ->
                if ((pressedMask and GamepadButton.SHARE.bitMask) != 0 &&
                    detectedProfile == ControllerHardwareProfile.PLAYSTATION_DUALSHOCK4 &&
                    !ps.touchpad.isTouchpadClicked
                ) {
                    ps.copy(touchpad = ps.touchpad.copy(isTouchpadClicked = true))
                } else {
                    ps
                }
            }
        } else {
            playStationFeatures
        }

        val resolvedSwitch = if (detectedProfile.family == ControllerFamily.NINTENDO_SWITCH) {
            val base = switchFeatures ?: SwitchControllerFeatures(usePositionalActionButtons = usePositionalSwitchLayout)
            base.copy(
                isCapturePressed = base.isCapturePressed || ((pressedMask and GamepadButton.SHARE.bitMask) != 0),
                usePositionalActionButtons = usePositionalSwitchLayout
            )
        } else {
            switchFeatures
        }

        val resolvedXbox = if (detectedProfile.family == ControllerFamily.XBOX) {
            val base = xboxFeatures ?: XboxControllerFeatures()
            base.copy(
                isSharePressed = base.isSharePressed || ((pressedMask and GamepadButton.SHARE.bitMask) != 0)
            )
        } else {
            xboxFeatures
        }

        return GamepadFrameSnapshot(
            playerIndex = playerIndex,
            connected = true,
            deviceId = deviceId,
            deviceName = deviceName,
            vendorId = vendorId,
            productId = productId,
            profile = detectedProfile,
            batteryLevel = batteryLevel,
            pressedButtonsMask = pressedMask,
            leftStick = leftStick,
            rightStick = rightStick,
            leftTrigger = lt,
            rightTrigger = rt,
            motion = motion,
            playStation = resolvedPs,
            switchFeatures = resolvedSwitch,
            xbox = resolvedXbox
        )
    }

    fun parseVendorProductFromDeviceId(idString: String): Pair<Int, Int> {
        val vendorMatch = Regex("Vendor:\\s*([0-9a-fA-F]{4})").find(idString)
        val productMatch = Regex("Product:\\s*([0-9a-fA-F]{4})").find(idString)
        if (vendorMatch != null && productMatch != null) {
            val vid = vendorMatch.groupValues[1].toIntOrNull(16) ?: 0
            val pid = productMatch.groupValues[1].toIntOrNull(16) ?: 0
            return vid to pid
        }
        val dashMatch = Regex("([0-9a-fA-F]{4})-([0-9a-fA-F]{4})-").find(idString)
        if (dashMatch != null) {
            val vid = dashMatch.groupValues[1].toIntOrNull(16) ?: 0
            val pid = dashMatch.groupValues[2].toIntOrNull(16) ?: 0
            return vid to pid
        }
        return 0 to 0
    }

    private fun extractVendorProductFromSdlGuid(guid: String): Pair<Int, Int> {
        if (guid.length < 24) return 0 to 0
        fun parseLe16(offset: Int): Int {
            val lo = guid.substring(offset, offset + 2).toIntOrNull(16) ?: return 0
            val hi = guid.substring(offset + 2, offset + 4).toIntOrNull(16) ?: return 0
            return (hi shl 8) or lo
        }
        return parseLe16(8) to parseLe16(16)
    }

    private fun parseSdlButtonTarget(value: String): Int? {
        val clean = value.removeSuffix("~")
        if (clean.startsWith("b")) {
            return clean.substring(1).toIntOrNull()
        }
        if (clean.startsWith("h")) {
            val dotIdx = clean.indexOf('.')
            if (dotIdx > 0 && dotIdx < clean.lastIndex) {
                val mask = clean.substring(dotIdx + 1).toIntOrNull() ?: return null
                return GamepadHardwareMapping.encodeHatMask(mask)
            }
        }
        val axis = parseSdlAxisTarget(clean)
        if (axis != null) {
            return GamepadHardwareMapping.encodeAxisButton(axis.first)
        }
        return null
    }

    private fun parseSdlAxisTarget(value: String): Pair<Int, Boolean>? {
        var s = value.trim()
        val inverted = s.endsWith("~")
        if (inverted) s = s.dropLast(1)
        if (s.startsWith("+") || s.startsWith("-")) {
            s = s.substring(1)
        }
        if (!s.startsWith("a")) return null
        val idx = s.substring(1).toIntOrNull() ?: return null
        return idx to inverted
    }

    private fun vidPidKey(vid: Int, pid: Int): Long =
        ((vid.toLong() and 0xFFFFL) shl 16) or (pid.toLong() and 0xFFFFL)
}
