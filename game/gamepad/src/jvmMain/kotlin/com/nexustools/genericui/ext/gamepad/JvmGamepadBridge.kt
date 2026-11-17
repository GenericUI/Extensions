package com.nexustools.genericui.ext.gamepad

import java.util.concurrent.ConcurrentHashMap

/**
 * Direct input bridge allowing JVM desktop hosts (Swing/AWT, LWJGL, custom HID, or integration harnesses)
 * to feed physical controller snapshots directly into [PlatformGamepadBackend] on `jvmMain`.
 */
object JvmGamepadBridge {
    private val bridgedSnapshots = ConcurrentHashMap<Int, GamepadFrameSnapshot>()

    fun submitSnapshot(snapshot: GamepadFrameSnapshot) {
        if (snapshot.playerIndex !in 0 until GamepadManager.MAX_PLAYERS) return
        bridgedSnapshots[snapshot.playerIndex] = snapshot
    }

    fun submitRawState(
        playerIndex: Int,
        buttons: BooleanArray,
        axes: FloatArray,
        hatMask: Int = 0,
        vendorId: Int = 0,
        productId: Int = 0,
        deviceName: String = "JVM Bridged Gamepad",
        alreadyStandardLayout: Boolean = false,
        usePositionalSwitchLayout: Boolean = true,
        motion: SixAxisMotionState = SixAxisMotionState.UNAVAILABLE
    ) {
        if (playerIndex !in 0 until GamepadManager.MAX_PLAYERS) return
        bridgedSnapshots[playerIndex] = GamepadLayoutMapper.mapRawInput(
            playerIndex = playerIndex,
            buttons = buttons,
            axes = axes,
            hatMask = hatMask,
            vendorId = vendorId,
            productId = productId,
            deviceName = deviceName,
            deviceId = "jvm-bridge-$playerIndex",
            alreadyStandardLayout = alreadyStandardLayout,
            usePositionalSwitchLayout = usePositionalSwitchLayout,
            motion = motion
        )
    }

    fun removeSlot(playerIndex: Int) {
        bridgedSnapshots.remove(playerIndex)
    }

    fun clearAll() {
        bridgedSnapshots.clear()
    }

    internal fun snapshotSlots(): Map<Int, GamepadFrameSnapshot> = bridgedSnapshots.toMap()
}
