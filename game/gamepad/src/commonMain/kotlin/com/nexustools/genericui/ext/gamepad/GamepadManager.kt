package com.nexustools.genericui.ext.gamepad

/**
 * Coordinates up to [MAX_PLAYERS] physical or synthetic game controllers, mapping hardware profiles
 * (Xbox, PlayStation DualShock/DualSense, Nintendo Switch Pro/Joy-Con, 8BitDo, Logitech, Generic/SDL)
 * into a canonical Xbox-style layout while preserving 6-axis IMU fusion and controller-unique features.
 */
class GamepadManager {
    private val previousSnapshots = Array(MAX_PLAYERS) { GamepadFrameSnapshot.disconnected(it) }
    private val currentSnapshots = Array(MAX_PLAYERS) { GamepadFrameSnapshot.disconnected(it) }
    private val syntheticOverrides = arrayOfNulls<GamepadFrameSnapshot>(MAX_PLAYERS)
    private val handles = Array(MAX_PLAYERS) { GamepadControllerHandle(it, this) }
    private val sensorFusions = Array(MAX_PLAYERS) { SixAxisSensorFusion() }

    private val lightbarOverrides = arrayOfNulls<IntArray>(MAX_PLAYERS)
    private val psPlayerLedOverrides = arrayOfNulls<Int>(MAX_PLAYERS)
    private val psMuteLedOverrides = arrayOfNulls<MuteLedState>(MAX_PLAYERS)
    private val psLeftTriggerOverrides = arrayOfNulls<AdaptiveTriggerEffect>(MAX_PLAYERS)
    private val psRightTriggerOverrides = arrayOfNulls<AdaptiveTriggerEffect>(MAX_PLAYERS)

    private val switchPlayerLedOverrides = arrayOfNulls<Int>(MAX_PLAYERS)
    private val switchHomeLedOverrides = arrayOfNulls<SwitchHomeLedPattern>(MAX_PLAYERS)
    private val switchHdRumbleOverrides = arrayOfNulls<SwitchHdRumbleCommand>(MAX_PLAYERS)
    private val switchPositionalFlags = BooleanArray(MAX_PLAYERS) { true }

    private val xboxImpulseRumbleOverrides = arrayOfNulls<XboxImpulseRumbleCommand>(MAX_PLAYERS)

    private val connectedListeners = mutableListOf<(Int, GamepadFrameSnapshot) -> Unit>()
    private val disconnectedListeners = mutableListOf<(Int, GamepadFrameSnapshot) -> Unit>()

    var onConnected: ((playerIndex: Int, snapshot: GamepadFrameSnapshot) -> Unit)? = null
    var onDisconnected: ((playerIndex: Int, previousSnapshot: GamepadFrameSnapshot) -> Unit)? = null

    fun addConnectionListener(
        onConnect: ((playerIndex: Int, snapshot: GamepadFrameSnapshot) -> Unit)? = null,
        onDisconnect: ((playerIndex: Int, previousSnapshot: GamepadFrameSnapshot) -> Unit)? = null
    ) {
        if (onConnect != null && onConnect !in connectedListeners) {
            connectedListeners.add(onConnect)
        }
        if (onDisconnect != null && onDisconnect !in disconnectedListeners) {
            disconnectedListeners.add(onDisconnect)
        }
    }

    fun setSyntheticController(snapshot: GamepadFrameSnapshot) {
        val idx = snapshot.playerIndex
        if (idx !in 0 until MAX_PLAYERS) return
        syntheticOverrides[idx] = snapshot
    }

    fun setSyntheticState(playerIndex: Int, snapshot: GamepadFrameSnapshot) {
        if (playerIndex !in 0 until MAX_PLAYERS) return
        syntheticOverrides[playerIndex] = snapshot.copy(playerIndex = playerIndex)
    }

    fun injectRawHardwareReport(
        playerIndex: Int,
        buttons: BooleanArray,
        axes: FloatArray,
        hatMask: Int = 0,
        vendorId: Int = 0,
        productId: Int = 0,
        deviceName: String = "",
        deviceId: String = "pad-$playerIndex",
        alreadyStandardLayout: Boolean = false,
        invertRawY: Boolean = false,
        triggersAreSigned: Boolean = false,
        batteryLevel: Float? = null,
        motion: SixAxisMotionState? = null,
        playStationFeatures: PlayStationControllerFeatures? = null,
        switchFeatures: SwitchControllerFeatures? = null,
        xboxFeatures: XboxControllerFeatures? = null
    ) {
        if (playerIndex !in 0 until MAX_PLAYERS) return
        val mapped = GamepadLayoutMapper.mapRawInput(
            playerIndex = playerIndex,
            buttons = buttons,
            axes = axes,
            hatMask = hatMask,
            vendorId = vendorId,
            productId = productId,
            deviceName = deviceName,
            deviceId = deviceId,
            alreadyStandardLayout = alreadyStandardLayout,
            usePositionalSwitchLayout = switchPositionalFlags[playerIndex],
            invertRawY = invertRawY,
            triggersAreSigned = triggersAreSigned,
            batteryLevel = batteryLevel,
            motion = motion ?: SixAxisMotionState.UNAVAILABLE,
            playStationFeatures = playStationFeatures,
            switchFeatures = switchFeatures,
            xboxFeatures = xboxFeatures
        )
        syntheticOverrides[playerIndex] = mapped
    }

    fun clearSyntheticController(playerIndex: Int) {
        if (playerIndex !in 0 until MAX_PLAYERS) return
        syntheticOverrides[playerIndex] = null
    }

    fun clearSyntheticControllers() {
        for (i in 0 until MAX_PLAYERS) {
            syntheticOverrides[i] = null
        }
    }

    fun updateSixAxisSample(
        playerIndex: Int,
        gyroRadX: Float,
        gyroRadY: Float,
        gyroRadZ: Float,
        accelGX: Float,
        accelGY: Float,
        accelGZ: Float,
        dtSeconds: Float,
        timestampMs: Long = 0L
    ): SixAxisMotionState {
        if (playerIndex !in 0 until MAX_PLAYERS) return SixAxisMotionState.UNAVAILABLE
        val fused = sensorFusions[playerIndex].update(
            gyroRadX = gyroRadX,
            gyroRadY = gyroRadY,
            gyroRadZ = gyroRadZ,
            accelGX = accelGX,
            accelGY = accelGY,
            accelGZ = accelGZ,
            dtSeconds = dtSeconds,
            timestampMs = timestampMs
        )
        currentSnapshots[playerIndex] = currentSnapshots[playerIndex].copy(motion = fused)
        syntheticOverrides[playerIndex]?.let { syn ->
            syntheticOverrides[playerIndex] = syn.copy(motion = fused)
        }
        return fused
    }

    fun pollAll(): List<GamepadFrameSnapshot> {
        val hasAnyRealSlot = syntheticOverrides.any { it == null }
        val polledHardware = if (hasAnyRealSlot) {
            PlatformGamepadBackend.pollControllers(MAX_PLAYERS, currentSnapshots.toList())
        } else {
            emptyList()
        }

        for (i in 0 until MAX_PLAYERS) {
            val prev = currentSnapshots[i]
            previousSnapshots[i] = prev
            val base = syntheticOverrides[i]
                ?: polledHardware.firstOrNull { it.playerIndex == i }
                ?: GamepadFrameSnapshot.disconnected(i)

            val enriched = applyFeatureStateOverrides(i, base).withTransitionFrom(prev)
            currentSnapshots[i] = enriched

            if (!prev.connected && enriched.connected) {
                onConnected?.invoke(i, enriched)
                for (listener in connectedListeners.toList()) {
                    listener(i, enriched)
                }
            } else if (prev.connected && !enriched.connected) {
                onDisconnected?.invoke(i, prev)
                for (listener in disconnectedListeners.toList()) {
                    listener(i, prev)
                }
            }
        }
        return currentSnapshots.toList()
    }

    fun poll() {
        pollAll()
    }

    fun snapshot(playerIndex: Int = 0): GamepadFrameSnapshot =
        if (playerIndex in 0 until MAX_PLAYERS) currentSnapshots[playerIndex] else GamepadFrameSnapshot.disconnected(playerIndex)

    fun state(playerIndex: Int = 0): GamepadFrameSnapshot = snapshot(playerIndex)

    fun previousSnapshot(playerIndex: Int = 0): GamepadFrameSnapshot =
        if (playerIndex in 0 until MAX_PLAYERS) previousSnapshots[playerIndex] else GamepadFrameSnapshot.disconnected(playerIndex)

    fun controller(playerIndex: Int = 0): GamepadControllerHandle {
        val clamped = playerIndex.coerceIn(0, MAX_PLAYERS - 1)
        return handles[clamped]
    }

    fun connectedPlayers(): List<Int> =
        (0 until MAX_PLAYERS).filter { currentSnapshots[it].connected }

    fun connectedControllers(): List<GamepadControllerHandle> =
        (0 until MAX_PLAYERS).filter { currentSnapshots[it].connected }.map { handles[it] }

    fun isButtonDown(playerIndex: Int = 0, button: GamepadButton): Boolean =
        snapshot(playerIndex).isButtonDown(button)

    fun isDown(playerIndex: Int = 0, button: GamepadButton): Boolean =
        isButtonDown(playerIndex, button)

    fun isButtonJustPressed(playerIndex: Int = 0, button: GamepadButton): Boolean =
        snapshot(playerIndex).isButtonJustPressed(button)

    fun wasJustPressed(playerIndex: Int = 0, button: GamepadButton): Boolean =
        isButtonJustPressed(playerIndex, button)

    fun isButtonJustReleased(playerIndex: Int = 0, button: GamepadButton): Boolean =
        snapshot(playerIndex).isButtonJustReleased(button)

    fun wasJustReleased(playerIndex: Int = 0, button: GamepadButton): Boolean =
        isButtonJustReleased(playerIndex, button)

    fun axis(playerIndex: Int = 0, axis: GamepadAxis): Float =
        snapshot(playerIndex).axisValue(axis)

    fun leftStick(playerIndex: Int = 0): GamepadAxis2D = snapshot(playerIndex).leftStick
    fun rightStick(playerIndex: Int = 0): GamepadAxis2D = snapshot(playerIndex).rightStick
    fun leftTrigger(playerIndex: Int = 0): Float = snapshot(playerIndex).leftTrigger
    fun rightTrigger(playerIndex: Int = 0): Float = snapshot(playerIndex).rightTrigger
    fun dpadVector(playerIndex: Int = 0): GamepadAxis2D = snapshot(playerIndex).dpadVector

    fun setRumble(playerIndex: Int = 0, request: GamepadRumbleRequest): Boolean {
        if (playerIndex !in 0 until MAX_PLAYERS) return false
        return PlatformGamepadBackend.setRumble(playerIndex, request.clamped())
    }

    fun setPlayStationLightbarColor(playerIndex: Int, r: Int, g: Int, b: Int): Boolean {
        if (playerIndex !in 0 until MAX_PLAYERS) return false
        val cr = r.coerceIn(0, 255)
        val cg = g.coerceIn(0, 255)
        val cb = b.coerceIn(0, 255)
        lightbarOverrides[playerIndex] = intArrayOf(cr, cg, cb)
        currentSnapshots[playerIndex] = applyFeatureStateOverrides(playerIndex, currentSnapshots[playerIndex])
        return PlatformGamepadBackend.setPlayStationLightbar(playerIndex, cr, cg, cb)
    }

    fun setPlayStationPlayerIndicatorLedMask(playerIndex: Int, mask: Int): Boolean {
        if (playerIndex !in 0 until MAX_PLAYERS) return false
        psPlayerLedOverrides[playerIndex] = mask
        currentSnapshots[playerIndex] = applyFeatureStateOverrides(playerIndex, currentSnapshots[playerIndex])
        return PlatformGamepadBackend.setPlayStationPlayerLedMask(playerIndex, mask)
    }

    fun setPlayStationMuteLed(playerIndex: Int, state: MuteLedState): Boolean {
        if (playerIndex !in 0 until MAX_PLAYERS) return false
        psMuteLedOverrides[playerIndex] = state
        currentSnapshots[playerIndex] = applyFeatureStateOverrides(playerIndex, currentSnapshots[playerIndex])
        return PlatformGamepadBackend.setPlayStationMuteLed(playerIndex, state)
    }

    fun setPlayStationAdaptiveTrigger(
        playerIndex: Int,
        side: AdaptiveTriggerSide,
        effect: AdaptiveTriggerEffect
    ): Boolean {
        if (playerIndex !in 0 until MAX_PLAYERS) return false
        if (side == AdaptiveTriggerSide.LEFT || side == AdaptiveTriggerSide.BOTH) {
            psLeftTriggerOverrides[playerIndex] = effect
        }
        if (side == AdaptiveTriggerSide.RIGHT || side == AdaptiveTriggerSide.BOTH) {
            psRightTriggerOverrides[playerIndex] = effect
        }
        currentSnapshots[playerIndex] = applyFeatureStateOverrides(playerIndex, currentSnapshots[playerIndex])
        return PlatformGamepadBackend.setPlayStationAdaptiveTrigger(playerIndex, side, effect)
    }

    fun setSwitchHdRumble(playerIndex: Int, command: SwitchHdRumbleCommand): Boolean {
        if (playerIndex !in 0 until MAX_PLAYERS) return false
        val clamped = command.clamped()
        switchHdRumbleOverrides[playerIndex] = clamped
        currentSnapshots[playerIndex] = applyFeatureStateOverrides(playerIndex, currentSnapshots[playerIndex])
        return PlatformGamepadBackend.setSwitchHdRumble(playerIndex, clamped)
    }

    fun setSwitchPlayerLeds(playerIndex: Int, mask: Int): Boolean {
        if (playerIndex !in 0 until MAX_PLAYERS) return false
        val masked = mask and 0x0F
        switchPlayerLedOverrides[playerIndex] = masked
        currentSnapshots[playerIndex] = applyFeatureStateOverrides(playerIndex, currentSnapshots[playerIndex])
        return PlatformGamepadBackend.setSwitchPlayerLeds(playerIndex, masked)
    }

    fun setSwitchHomeLedPattern(playerIndex: Int, pattern: SwitchHomeLedPattern): Boolean {
        if (playerIndex !in 0 until MAX_PLAYERS) return false
        val clamped = pattern.clamped()
        switchHomeLedOverrides[playerIndex] = clamped
        currentSnapshots[playerIndex] = applyFeatureStateOverrides(playerIndex, currentSnapshots[playerIndex])
        return PlatformGamepadBackend.setSwitchHomeLedPattern(playerIndex, clamped)
    }

    fun setSwitchUsePositionalActionButtons(playerIndex: Int, usePositional: Boolean) {
        if (playerIndex !in 0 until MAX_PLAYERS) return
        switchPositionalFlags[playerIndex] = usePositional
        currentSnapshots[playerIndex] = applyFeatureStateOverrides(playerIndex, currentSnapshots[playerIndex])
    }

    fun setXboxImpulseRumble(playerIndex: Int, command: XboxImpulseRumbleCommand): Boolean {
        if (playerIndex !in 0 until MAX_PLAYERS) return false
        xboxImpulseRumbleOverrides[playerIndex] = command
        currentSnapshots[playerIndex] = applyFeatureStateOverrides(playerIndex, currentSnapshots[playerIndex])
        return PlatformGamepadBackend.setRumble(playerIndex, command.toGamepadRumbleRequest())
    }

    fun calibrateGyroBias(playerIndex: Int, biasX: Float, biasY: Float, biasZ: Float) {
        if (playerIndex !in 0 until MAX_PLAYERS) return
        sensorFusions[playerIndex].calibrateGyroBias(biasX, biasY, biasZ)
    }

    fun calibrateGyroFromStationarySamples(
        playerIndex: Int,
        samplesGyroX: FloatArray,
        samplesGyroY: FloatArray,
        samplesGyroZ: FloatArray
    ) {
        if (playerIndex !in 0 until MAX_PLAYERS) return
        sensorFusions[playerIndex].calibrateFromStationarySamples(samplesGyroX, samplesGyroY, samplesGyroZ)
    }

    fun resetMotionOrientation(playerIndex: Int) {
        if (playerIndex !in 0 until MAX_PLAYERS) return
        sensorFusions[playerIndex].resetOrientation()
    }

    private fun applyFeatureStateOverrides(
        idx: Int,
        snapshot: GamepadFrameSnapshot
    ): GamepadFrameSnapshot {
        if (!snapshot.connected) return snapshot

        val ps = if (snapshot.family == ControllerFamily.PLAYSTATION) {
            val base = snapshot.playStation ?: PlayStationControllerFeatures()
            val rgb = lightbarOverrides[idx]
            base.copy(
                lightbarR = rgb?.get(0) ?: base.lightbarR,
                lightbarG = rgb?.get(1) ?: base.lightbarG,
                lightbarB = rgb?.get(2) ?: base.lightbarB,
                playerIndicatorLedMask = psPlayerLedOverrides[idx] ?: base.playerIndicatorLedMask,
                muteLedState = psMuteLedOverrides[idx] ?: base.muteLedState,
                leftAdaptiveTrigger = psLeftTriggerOverrides[idx] ?: base.leftAdaptiveTrigger,
                rightAdaptiveTrigger = psRightTriggerOverrides[idx] ?: base.rightAdaptiveTrigger
            )
        } else {
            snapshot.playStation
        }

        val sw = if (snapshot.family == ControllerFamily.NINTENDO_SWITCH) {
            val base = snapshot.switchFeatures ?: SwitchControllerFeatures()
            base.copy(
                playerLedMask = switchPlayerLedOverrides[idx] ?: base.playerLedMask,
                homeLedPattern = switchHomeLedOverrides[idx] ?: base.homeLedPattern,
                lastHdRumble = switchHdRumbleOverrides[idx] ?: base.lastHdRumble,
                usePositionalActionButtons = switchPositionalFlags[idx]
            )
        } else {
            snapshot.switchFeatures
        }

        val xb = if (snapshot.family == ControllerFamily.XBOX) {
            val base = snapshot.xbox ?: XboxControllerFeatures()
            base.copy(
                lastImpulseRumble = xboxImpulseRumbleOverrides[idx] ?: base.lastImpulseRumble
            )
        } else {
            snapshot.xbox
        }

        return snapshot.copy(
            playStation = ps,
            switchFeatures = sw,
            xbox = xb
        )
    }

    companion object {
        const val MAX_PLAYERS: Int = 4

        val shared: GamepadManager by lazy { GamepadManager() }
    }
}
