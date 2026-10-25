package com.nexustools.genericui.component.media

/**
 * Callback invoked on each VBlank / VSync render tick of an [AbstractCanvas].
 *
 * @param context The typed rendering context (`ImageBuffer2DContext`, `GLContext`, or `VKContext`).
 * @param deltaMs High-resolution elapsed time in milliseconds (`Double`) since the previous render frame,
 *                enabling frame-rate-independent animations and physics steps.
 * @param totalElapsedMs High-resolution total elapsed time in milliseconds (`Double`) since the VBlank loop started.
 * @param frameNumber Monotonically increasing 64-bit frame counter (`0L`, `1L`, `2L`, ...).
 */
fun interface VBlankRenderCallback<in C> {
    fun onRender(
        context: C,
        deltaMs: Double,
        totalElapsedMs: Double,
        frameNumber: Long
    )
}
