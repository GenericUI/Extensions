@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.nexustools.genericui.ext.gamepad

@JsFun("() => (typeof navigator !== 'undefined' && typeof navigator.getGamepads === 'function') ? navigator.getGamepads().length : 0")
private external fun wasmGetGamepadListLength(): Int

@JsFun("(idx) => { const list = navigator.getGamepads(); const p = list && list[idx]; return !!(p && p.connected); }")
private external fun wasmIsGamepadConnected(idx: Int): Boolean

@JsFun("(idx) => { const p = navigator.getGamepads()[idx]; return (p && p.id) ? String(p.id) : ''; }")
private external fun wasmGetGamepadId(idx: Int): String

@JsFun("(idx) => { const p = navigator.getGamepads()[idx]; return (p && p.mapping) ? String(p.mapping) : ''; }")
private external fun wasmGetGamepadMapping(idx: Int): String

@JsFun("(idx) => { const p = navigator.getGamepads()[idx]; return (p && p.buttons) ? p.buttons.length : 0; }")
private external fun wasmGetGamepadButtonCount(idx: Int): Int

@JsFun("(idx, b) => { const p = navigator.getGamepads()[idx]; return !!(p && p.buttons && p.buttons[b] && p.buttons[b].pressed); }")
private external fun wasmGetGamepadButtonPressed(idx: Int, b: Int): Boolean

@JsFun("(idx, b) => { const p = navigator.getGamepads()[idx]; return (p && p.buttons && p.buttons[b] && typeof p.buttons[b].value === 'number') ? p.buttons[b].value : 0.0; }")
private external fun wasmGetGamepadButtonValue(idx: Int, b: Int): Double

@JsFun("(idx) => { const p = navigator.getGamepads()[idx]; return (p && p.axes) ? p.axes.length : 0; }")
private external fun wasmGetGamepadAxisCount(idx: Int): Int

@JsFun("(idx, a) => { const p = navigator.getGamepads()[idx]; return (p && p.axes && typeof p.axes[a] === 'number') ? p.axes[a] : 0.0; }")
private external fun wasmGetGamepadAxisValue(idx: Int, a: Int): Double

@JsFun("(idx, durationMs, strongMag, weakMag) => { " +
    "if (typeof navigator === 'undefined' || typeof navigator.getGamepads !== 'function') return false; " +
    "const p = navigator.getGamepads()[idx]; " +
    "const act = p && p.vibrationActuator; " +
    "if (!act || typeof act.playEffect !== 'function') return false; " +
    "act.playEffect('dual-rumble', { startDelay: 0, duration: durationMs, strongMagnitude: strongMag, weakMagnitude: weakMag }); " +
    "return true; }")
private external fun wasmPlayGamepadRumble(idx: Int, durationMs: Double, strongMag: Double, weakMag: Double): Boolean

@JsFun("(vid, r, g, b) => { " +
    "if (typeof navigator === 'undefined' || !navigator.hid || typeof navigator.hid.getDevices !== 'function') return false; " +
    "navigator.hid.getDevices().then(devs => { for (const d of devs) { if (d.vendorId === vid && d.opened) { " +
    "const arr = new Uint8Array(47); arr[1] = 0x04; arr[44] = r; arr[45] = g; arr[46] = b; d.sendReport(2, arr); break; } } }); " +
    "return true; }")
private external fun wasmSendDualSenseLightbarHid(vid: Int, r: Int, g: Int, b: Int): Boolean

@JsFun("(vid, mask) => { " +
    "if (typeof navigator === 'undefined' || !navigator.hid || typeof navigator.hid.getDevices !== 'function') return false; " +
    "navigator.hid.getDevices().then(devs => { for (const d of devs) { if (d.vendorId === vid && d.opened) { " +
    "const arr = new Uint8Array(47); arr[1] = 0x10; arr[43] = mask & 0x1F; d.sendReport(2, arr); break; } } }); " +
    "return true; }")
private external fun wasmSendDualSensePlayerLedHid(vid: Int, mask: Int): Boolean

@JsFun("(vid, mode) => { " +
    "if (typeof navigator === 'undefined' || !navigator.hid || typeof navigator.hid.getDevices !== 'function') return false; " +
    "navigator.hid.getDevices().then(devs => { for (const d of devs) { if (d.vendorId === vid && d.opened) { " +
    "const arr = new Uint8Array(47); arr[1] = 0x01; arr[8] = mode; d.sendReport(2, arr); break; } } }); " +
    "return true; }")
private external fun wasmSendDualSenseMuteLedHid(vid: Int, mode: Int): Boolean

@JsFun("(vid, flags0, rightMode, leftMode) => { " +
    "if (typeof navigator === 'undefined' || !navigator.hid || typeof navigator.hid.getDevices !== 'function') return false; " +
    "navigator.hid.getDevices().then(devs => { for (const d of devs) { if (d.vendorId === vid && d.opened) { " +
    "const arr = new Uint8Array(47); arr[0] = flags0; arr[10] = rightMode; arr[21] = leftMode; d.sendReport(2, arr); break; } } }); " +
    "return true; }")
private external fun wasmSendDualSenseAdaptiveTriggerHid(vid: Int, flags0: Int, rightMode: Int, leftMode: Int): Boolean

@JsFun("(vid, subcmd, arg0) => { " +
    "if (typeof navigator === 'undefined' || !navigator.hid || typeof navigator.hid.getDevices !== 'function') return false; " +
    "navigator.hid.getDevices().then(devs => { for (const d of devs) { if (d.vendorId === vid && d.opened) { " +
    "const arr = new Uint8Array(48); arr[9] = subcmd; arr[10] = arg0; d.sendReport(1, arr); break; } } }); " +
    "return true; }")
private external fun wasmSendSwitchSubcmdHid(vid: Int, subcmd: Int, arg0: Int): Boolean

/**
 * Kotlin/Wasm (`wasmJsMain`) [PlatformGamepadBackend] polling W3C `navigator.getGamepads()`,
 * dispatching `vibrationActuator.playEffect("dual-rumble", ...)`, and forwarding DualSense/Switch Pro
 * WebHID reports via `@JsFun` interop bridges.
 */
actual object PlatformGamepadBackend {
    actual val isHardwarePollingSupported: Boolean = true

    actual fun pollControllers(
        maxPlayers: Int,
        previousSnapshots: List<GamepadFrameSnapshot>
    ): List<GamepadFrameSnapshot> {
        val len = wasmGetGamepadListLength()
        if (len <= 0) return emptyList()

        val limit = minOf(len, maxPlayers)
        val result = mutableListOf<GamepadFrameSnapshot>()
        for (slot in 0 until limit) {
            if (!wasmIsGamepadConnected(slot)) continue

            val idStr = wasmGetGamepadId(slot).ifEmpty { "Wasm Web Gamepad #$slot" }
            val isStandard = wasmGetGamepadMapping(slot) == "standard"
            val (vid, pid) = GamepadLayoutMapper.parseVendorProductFromDeviceId(idStr)

            val btnCount = wasmGetGamepadButtonCount(slot)
            val buttons = BooleanArray(btnCount) { b -> wasmGetGamepadButtonPressed(slot, b) }

            val axisCount = wasmGetGamepadAxisCount(slot)
            val rawAxes = FloatArray(axisCount) { a -> wasmGetGamepadAxisValue(slot, a).toFloat() }
            val ltVal = if (btnCount > 6) wasmGetGamepadButtonValue(slot, 6).toFloat() else 0f
            val rtVal = if (btnCount > 7) wasmGetGamepadButtonValue(slot, 7).toFloat() else 0f

            val normalizedAxes = floatArrayOf(
                if (rawAxes.isNotEmpty()) rawAxes[0] else 0f,
                if (rawAxes.size > 1) rawAxes[1] else 0f,
                if (rawAxes.size > 2) rawAxes[2] else 0f,
                if (rawAxes.size > 3) rawAxes[3] else 0f,
                ltVal,
                rtVal
            )
            val prev = previousSnapshots.firstOrNull { it.playerIndex == slot }

            result.add(
                GamepadLayoutMapper.mapRawInput(
                    playerIndex = slot,
                    buttons = buttons,
                    axes = normalizedAxes,
                    vendorId = vid,
                    productId = pid,
                    deviceName = idStr,
                    deviceId = "wasm-$slot",
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
        val clamped = request.clamped()
        return wasmPlayGamepadRumble(
            idx = playerIndex,
            durationMs = clamped.durationMs.toDouble(),
            strongMag = clamped.lowFrequency.toDouble(),
            weakMag = clamped.highFrequency.toDouble()
        )
    }

    actual fun setPlayStationLightbar(playerIndex: Int, r: Int, g: Int, b: Int): Boolean =
        wasmSendDualSenseLightbarHid(
            GamepadLayoutMapper.VENDOR_SONY,
            r.coerceIn(0, 255),
            g.coerceIn(0, 255),
            b.coerceIn(0, 255)
        )

    actual fun setPlayStationPlayerLedMask(playerIndex: Int, mask: Int): Boolean =
        wasmSendDualSensePlayerLedHid(GamepadLayoutMapper.VENDOR_SONY, mask and 0x1F)

    actual fun setPlayStationMuteLed(playerIndex: Int, state: MuteLedState): Boolean =
        wasmSendDualSenseMuteLedHid(
            GamepadLayoutMapper.VENDOR_SONY,
            when (state) {
                MuteLedState.OFF -> 0
                MuteLedState.SOLID -> 1
                MuteLedState.BREATHING -> 2
            }
        )

    actual fun setPlayStationAdaptiveTrigger(
        playerIndex: Int,
        side: AdaptiveTriggerSide,
        effect: AdaptiveTriggerEffect
    ): Boolean {
        val mode = when (effect) {
            AdaptiveTriggerEffect.Off -> 0x05
            is AdaptiveTriggerEffect.ContinuousResistance -> 0x01
            is AdaptiveTriggerEffect.SectionResistance -> 0x02
            is AdaptiveTriggerEffect.VibrationPulse -> 0x06
        }
        var f0 = 0
        var rMode = 0
        var lMode = 0
        if (side == AdaptiveTriggerSide.RIGHT || side == AdaptiveTriggerSide.BOTH) {
            f0 = f0 or 0x04
            rMode = mode
        }
        if (side == AdaptiveTriggerSide.LEFT || side == AdaptiveTriggerSide.BOTH) {
            f0 = f0 or 0x08
            lMode = mode
        }
        return wasmSendDualSenseAdaptiveTriggerHid(GamepadLayoutMapper.VENDOR_SONY, f0, rMode, lMode)
    }

    actual fun setSwitchHdRumble(playerIndex: Int, command: SwitchHdRumbleCommand): Boolean {
        val c = command.clamped()
        return setRumble(
            playerIndex,
            GamepadRumbleRequest(c.leftLowAmp, c.rightHighAmp, durationMs = c.durationMs)
        )
    }

    actual fun setSwitchPlayerLeds(playerIndex: Int, mask: Int): Boolean =
        wasmSendSwitchSubcmdHid(GamepadLayoutMapper.VENDOR_NINTENDO, 0x30, mask and 0x0F)

    actual fun setSwitchHomeLedPattern(playerIndex: Int, pattern: SwitchHomeLedPattern): Boolean =
        wasmSendSwitchSubcmdHid(
            GamepadLayoutMapper.VENDOR_NINTENDO,
            0x38,
            (pattern.clamped().maxIntensity * 15f).toInt()
        )
}
