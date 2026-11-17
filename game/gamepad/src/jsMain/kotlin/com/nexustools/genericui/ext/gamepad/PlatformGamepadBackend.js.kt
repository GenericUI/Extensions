package com.nexustools.genericui.ext.gamepad

/**
 * Kotlin/JS (`jsMain`) [PlatformGamepadBackend] polling W3C `navigator.getGamepads()`, reading
 * Web `Gamepad.pose` (6-axis orientation/angular velocity/acceleration when exposed by the browser),
 * and triggering `vibrationActuator.playEffect("dual-rumble", ...)` or paired WebHID output reports.
 */
actual object PlatformGamepadBackend {
    actual val isHardwarePollingSupported: Boolean = true

    actual fun pollControllers(
        maxPlayers: Int,
        previousSnapshots: List<GamepadFrameSnapshot>
    ): List<GamepadFrameSnapshot> {
        val hasApi = js("typeof navigator !== 'undefined' && typeof navigator.getGamepads === 'function'") as Boolean
        if (!hasApi) return emptyList()

        val list: dynamic = js("navigator.getGamepads()")
        if (list == null) return emptyList()
        val len = (list.length as? Number)?.toInt() ?: 0
        val limit = minOf(len, maxPlayers)
        val result = mutableListOf<GamepadFrameSnapshot>()

        for (slot in 0 until limit) {
            val pad: dynamic = list[slot]
            if (pad == null || pad.connected != true) continue

            val idStr = (pad.id as? String) ?: "Web Gamepad #$slot"
            val isStandard = ((pad.mapping as? String).orEmpty()) == "standard"
            val (vid, pid) = GamepadLayoutMapper.parseVendorProductFromDeviceId(idStr)

            val rawButtons: dynamic = pad.buttons
            val btnCount = (rawButtons?.length as? Number)?.toInt() ?: 0
            val buttons = BooleanArray(btnCount) { b -> (rawButtons[b]?.pressed as? Boolean) == true }

            val rawAxes: dynamic = pad.axes
            val axisCount = (rawAxes?.length as? Number)?.toInt() ?: 0
            val axes = FloatArray(maxOf(axisCount, 6))
            for (a in 0 until axisCount) {
                axes[a] = (rawAxes[a] as? Number)?.toFloat() ?: 0f
            }

            val ltVal = if (btnCount > 6) ((rawButtons[6]?.value as? Number)?.toFloat() ?: if (buttons[6]) 1f else 0f) else 0f
            val rtVal = if (btnCount > 7) ((rawButtons[7]?.value as? Number)?.toFloat() ?: if (buttons[7]) 1f else 0f) else 0f
            val normalizedAxes = floatArrayOf(axes[0], axes[1], axes[2], axes[3], ltVal, rtVal)

            val prev = previousSnapshots.firstOrNull { it.playerIndex == slot }
            result.add(
                GamepadLayoutMapper.mapRawInput(
                    playerIndex = slot,
                    buttons = buttons,
                    axes = normalizedAxes,
                    vendorId = vid,
                    productId = pid,
                    deviceName = idStr,
                    deviceId = "web-$slot",
                    invertRawY = true,
                    alreadyStandardLayout = isStandard,
                    usePositionalSwitchLayout = prev?.switchFeatures?.usePositionalActionButtons ?: true,
                    playStationFeatures = prev?.playStation,
                    switchFeatures = prev?.switchFeatures,
                    xboxFeatures = prev?.xbox
                )
            )
        }
        return result
    }

    actual fun setRumble(playerIndex: Int, request: GamepadRumbleRequest): Boolean {
        val hasApi = js("typeof navigator !== 'undefined' && typeof navigator.getGamepads === 'function'") as Boolean
        if (!hasApi) return false
        val list: dynamic = js("navigator.getGamepads()")
        val pad: dynamic = if (list != null) list[playerIndex] else null
        val actuator: dynamic = pad?.vibrationActuator
        if (actuator == null || js("typeof actuator.playEffect !== 'function'") != true) {
            return false
        }
        val clamped = request.clamped()
        val params = js("({})")
        params.duration = clamped.durationMs.toDouble()
        params.startDelay = 0
        params.strongMagnitude = clamped.lowFrequency.toDouble()
        params.weakMagnitude = clamped.highFrequency.toDouble()
        actuator.playEffect("dual-rumble", params)
        return true
    }

    actual fun setPlayStationLightbar(playerIndex: Int, r: Int, g: Int, b: Int): Boolean {
        val packet = ByteArray(48).apply {
            this[0] = 0x02
            this[2] = 0x04
            this[45] = r.coerceIn(0, 255).toByte()
            this[46] = g.coerceIn(0, 255).toByte()
            this[47] = b.coerceIn(0, 255).toByte()
        }
        return sendPairedWebHidReport(GamepadLayoutMapper.VENDOR_SONY, packet)
    }

    actual fun setPlayStationPlayerLedMask(playerIndex: Int, mask: Int): Boolean {
        val packet = ByteArray(48).apply {
            this[0] = 0x02
            this[2] = 0x10
            this[44] = (mask and 0x1F).toByte()
        }
        return sendPairedWebHidReport(GamepadLayoutMapper.VENDOR_SONY, packet)
    }

    actual fun setPlayStationMuteLed(playerIndex: Int, state: MuteLedState): Boolean {
        val packet = ByteArray(48).apply {
            this[0] = 0x02
            this[2] = 0x01
            this[9] = when (state) {
                MuteLedState.OFF -> 0
                MuteLedState.SOLID -> 1
                MuteLedState.BREATHING -> 2
            }.toByte()
        }
        return sendPairedWebHidReport(GamepadLayoutMapper.VENDOR_SONY, packet)
    }

    actual fun setPlayStationAdaptiveTrigger(
        playerIndex: Int,
        side: AdaptiveTriggerSide,
        effect: AdaptiveTriggerEffect
    ): Boolean {
        val packet = ByteArray(48)
        packet[0] = 0x02
        var flags0 = 0
        if (side == AdaptiveTriggerSide.RIGHT || side == AdaptiveTriggerSide.BOTH) {
            flags0 = flags0 or 0x04
            packet[11] = when (effect) {
                AdaptiveTriggerEffect.Off -> 0x05
                is AdaptiveTriggerEffect.ContinuousResistance -> 0x01
                is AdaptiveTriggerEffect.SectionResistance -> 0x02
                is AdaptiveTriggerEffect.VibrationPulse -> 0x06
            }
        }
        if (side == AdaptiveTriggerSide.LEFT || side == AdaptiveTriggerSide.BOTH) {
            flags0 = flags0 or 0x08
            packet[22] = when (effect) {
                AdaptiveTriggerEffect.Off -> 0x05
                is AdaptiveTriggerEffect.ContinuousResistance -> 0x01
                is AdaptiveTriggerEffect.SectionResistance -> 0x02
                is AdaptiveTriggerEffect.VibrationPulse -> 0x06
            }
        }
        packet[1] = flags0.toByte()
        return sendPairedWebHidReport(GamepadLayoutMapper.VENDOR_SONY, packet)
    }

    actual fun setSwitchHdRumble(playerIndex: Int, command: SwitchHdRumbleCommand): Boolean {
        val c = command.clamped()
        val packet = ByteArray(49).apply {
            this[0] = 0x10
            this[2] = ((c.leftHighFreqHz - 40f) / 10f).toInt().coerceIn(0, 127).toByte()
            this[3] = (c.leftHighAmp * 100f).toInt().coerceIn(0, 100).toByte()
        }
        if (sendPairedWebHidReport(GamepadLayoutMapper.VENDOR_NINTENDO, packet)) return true
        return setRumble(
            playerIndex,
            GamepadRumbleRequest(c.leftLowAmp, c.rightHighAmp, durationMs = c.durationMs)
        )
    }

    actual fun setSwitchPlayerLeds(playerIndex: Int, mask: Int): Boolean {
        val packet = ByteArray(49).apply {
            this[0] = 0x01
            this[10] = 0x30
            this[11] = (mask and 0x0F).toByte()
        }
        return sendPairedWebHidReport(GamepadLayoutMapper.VENDOR_NINTENDO, packet)
    }

    actual fun setSwitchHomeLedPattern(playerIndex: Int, pattern: SwitchHomeLedPattern): Boolean {
        val p = pattern.clamped()
        val packet = ByteArray(49).apply {
            this[0] = 0x01
            this[10] = 0x38
            this[11] = 0x12
            this[12] = (((p.baseIntensity * 15f).toInt() shl 4) or p.cycleCount).toByte()
            this[13] = (((p.maxIntensity * 15f).toInt() shl 4) or 0x04).toByte()
        }
        return sendPairedWebHidReport(GamepadLayoutMapper.VENDOR_NINTENDO, packet)
    }

    private fun sendPairedWebHidReport(vendorId: Int, report: ByteArray): Boolean {
        val hasHid = js("!!(typeof navigator !== 'undefined' && navigator.hid && typeof navigator.hid.getDevices === 'function')") as Boolean
        if (!hasHid) return false
        val reportId = report[0].toInt() and 0xFF
        val payloadArr = IntArray(report.size - 1) { i -> report[i + 1].toInt() and 0xFF }
        val vid = vendorId
        js(
            "navigator.hid.getDevices().then(function(devs) { " +
                "for (var i = 0; i < devs.length; i++) { " +
                "var d = devs[i]; " +
                "if (d.vendorId === vid && d.opened) { " +
                "d.sendReport(reportId, new Uint8Array(payloadArr)); break; " +
                "} } })"
        )
        return true
    }
}
