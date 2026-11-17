package com.nexustools.genericui.ext.imageprocessing

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Porter-Duff and separable blend modes supported by [GpuImageCompositor] for offscreen
 * GPU layer compositing.
 */
enum class ImageBlendMode {
    NORMAL,
    MULTIPLY,
    SCREEN,
    OVERLAY,
    DARKEN,
    LIGHTEN,
    COLOR_DODGE,
    COLOR_BURN,
    HARD_LIGHT,
    SOFT_LIGHT,
    DIFFERENCE,
    EXCLUSION,
    ADD;

    /**
     * Evaluates the blend function for a single normalized `[0.0 .. 1.0]` channel pair `(dst, src)`.
     */
    fun blendChannel(dst: Float, src: Float): Float = when (this) {
        NORMAL -> src
        MULTIPLY -> dst * src
        SCREEN -> 1f - (1f - dst) * (1f - src)
        OVERLAY -> if (dst < 0.5f) 2f * dst * src else 1f - 2f * (1f - dst) * (1f - src)
        DARKEN -> min(dst, src)
        LIGHTEN -> max(dst, src)
        COLOR_DODGE -> if (src >= 0.9999f) 1f else min(1f, dst / (1f - src))
        COLOR_BURN -> if (src <= 0.0001f) 0f else 1f - min(1f, (1f - dst) / src)
        HARD_LIGHT -> if (src < 0.5f) 2f * dst * src else 1f - 2f * (1f - dst) * (1f - src)
        SOFT_LIGHT -> {
            if (src <= 0.5f) {
                dst - (1f - 2f * src) * dst * (1f - dst)
            } else {
                val d = if (dst <= 0.25f) ((16f * dst - 12f) * dst + 4f) * dst else sqrt(dst)
                dst + (2f * src - 1f) * (d - dst)
            }
        }
        DIFFERENCE -> abs(dst - src)
        EXCLUSION -> dst + src - 2f * dst * src
        ADD -> min(1f, dst + src)
    }
}
