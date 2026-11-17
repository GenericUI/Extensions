package com.nexustools.genericui.ext.gamepad

/**
 * Hardware state unique to Xbox One, Series X|S, and Elite Wireless controllers:
 * Share button state, 4 rear Elite paddle bits (`P1`..`P4`), and 4-motor impulse rumble history.
 */
data class XboxControllerFeatures(
    val isSharePressed: Boolean = false,
    val elitePaddleMask: Int = 0,
    val lastImpulseRumble: XboxImpulseRumbleCommand? = null
) {
    /**
     * Returns `true` when Elite rear paddle [paddleIndex] (`1` through `4` for `P1`..`P4`) is pressed.
     */
    fun isElitePaddleDown(paddleIndex: Int): Boolean {
        if (paddleIndex !in 1..4) return false
        return (elitePaddleMask and (1 shl (paddleIndex - 1))) != 0
    }
}
