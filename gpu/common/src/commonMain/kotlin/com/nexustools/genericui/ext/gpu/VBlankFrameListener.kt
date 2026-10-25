package com.nexustools.genericui.ext.gpu

/**
 * Listener invoked on each global VBlank / VSync tick managed by [VBlankWatcher].
 *
 * @param deltaMs High-resolution elapsed time in milliseconds (`Double`) since the previous VBlank frame.
 * @param totalElapsedMs High-resolution total elapsed time in milliseconds (`Double`) since the global VBlank loop started.
 * @param frameNumber Monotonically increasing 64-bit frame counter (`0L`, `1L`, `2L`, ...).
 */
fun interface VBlankFrameListener {
    fun onVBlankFrame(
        deltaMs: Double,
        totalElapsedMs: Double,
        frameNumber: Long
    )
}
