package com.nexustools.genericui.ext.imageprocessing.context2d

import com.nexustools.genericui.style.RgbaColor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Strongly-typed enum representing all 26 HTML5 2D Context Porter-Duff and W3C compositing
 * and blending operations (`globalCompositeOperation`) on an [ImageBuffer2DContext].
 */
enum class ImageBufferCompositeOperation(
    val gpuCode: Int,
    val webValue: String
) {
    SOURCE_OVER(0, "source-over"),
    SOURCE_IN(1, "source-in"),
    SOURCE_OUT(2, "source-out"),
    SOURCE_ATOP(3, "source-atop"),
    DESTINATION_OVER(4, "destination-over"),
    DESTINATION_IN(5, "destination-in"),
    DESTINATION_OUT(6, "destination-out"),
    DESTINATION_ATOP(7, "destination-atop"),
    LIGHTER(8, "lighter"),
    COPY(9, "copy"),
    XOR(10, "xor"),
    MULTIPLY(11, "multiply"),
    SCREEN(12, "screen"),
    OVERLAY(13, "overlay"),
    DARKEN(14, "darken"),
    LIGHTEN(15, "lighten"),
    COLOR_DODGE(16, "color-dodge"),
    COLOR_BURN(17, "color-burn"),
    HARD_LIGHT(18, "hard-light"),
    SOFT_LIGHT(19, "soft-light"),
    DIFFERENCE(20, "difference"),
    EXCLUSION(21, "exclusion"),
    HUE(22, "hue"),
    SATURATION(23, "saturation"),
    COLOR(24, "color"),
    LUMINOSITY(25, "luminosity");

    /**
     * Evaluates this composite operation between [src] and [dst], modulated by [globalAlpha] and [coverage].
     */
    fun blend(
        src: RgbaColor,
        dst: RgbaColor,
        globalAlpha: Float = 1f,
        coverage: Float = 1f
    ): RgbaColor {
        val sa = ((src.a / 255f) * globalAlpha.coerceIn(0f, 1f) * coverage.coerceIn(0f, 1f)).coerceIn(0f, 1f)
        val da = (dst.a / 255f).coerceIn(0f, 1f)
        val sr = (src.r / 255f).coerceIn(0f, 1f)
        val sg = (src.g / 255f).coerceIn(0f, 1f)
        val sb = (src.b / 255f).coerceIn(0f, 1f)
        val dr = (dst.r / 255f).coerceIn(0f, 1f)
        val dg = (dst.g / 255f).coerceIn(0f, 1f)
        val db = (dst.b / 255f).coerceIn(0f, 1f)

        val sra = sr * sa
        val sga = sg * sa
        val sba = sb * sa
        val dra = dr * da
        val dga = dg * da
        val dba = db * da

        var outRa: Float
        var outGa: Float
        var outBa: Float
        var outA: Float

        when (this) {
            SOURCE_OVER -> {
                outA = sa + da * (1f - sa)
                outRa = sra + dra * (1f - sa)
                outGa = sga + dga * (1f - sa)
                outBa = sba + dba * (1f - sa)
            }
            SOURCE_IN -> {
                outA = sa * da
                outRa = sra * da
                outGa = sga * da
                outBa = sba * da
            }
            SOURCE_OUT -> {
                outA = sa * (1f - da)
                outRa = sra * (1f - da)
                outGa = sga * (1f - da)
                outBa = sba * (1f - da)
            }
            SOURCE_ATOP -> {
                outA = da
                outRa = sra * da + dra * (1f - sa)
                outGa = sga * da + dga * (1f - sa)
                outBa = sba * da + dba * (1f - sa)
            }
            DESTINATION_OVER -> {
                outA = da + sa * (1f - da)
                outRa = dra + sra * (1f - da)
                outGa = dga + sga * (1f - da)
                outBa = dba + sba * (1f - da)
            }
            DESTINATION_IN -> {
                outA = da * sa
                outRa = dra * sa
                outGa = dga * sa
                outBa = dba * sa
            }
            DESTINATION_OUT -> {
                outA = da * (1f - sa)
                outRa = dra * (1f - sa)
                outGa = dga * (1f - sa)
                outBa = dba * (1f - sa)
            }
            DESTINATION_ATOP -> {
                outA = sa
                outRa = dra * sa + sra * (1f - da)
                outGa = dga * sa + sga * (1f - da)
                outBa = dba * sa + sba * (1f - da)
            }
            LIGHTER -> {
                outA = (sa + da).coerceIn(0f, 1f)
                outRa = (sra + dra).coerceIn(0f, 1f)
                outGa = (sga + dga).coerceIn(0f, 1f)
                outBa = (sba + dba).coerceIn(0f, 1f)
            }
            COPY -> {
                outA = sa
                outRa = sra
                outGa = sga
                outBa = sba
            }
            XOR -> {
                outA = sa * (1f - da) + da * (1f - sa)
                outRa = sra * (1f - da) + dra * (1f - sa)
                outGa = sga * (1f - da) + dga * (1f - sa)
                outBa = sba * (1f - da) + dba * (1f - sa)
            }
            else -> {
                val br: Float
                val bg: Float
                val bb: Float
                when (this) {
                    MULTIPLY -> {
                        br = sr * dr
                        bg = sg * dg
                        bb = sb * db
                    }
                    SCREEN -> {
                        br = sr + dr - sr * dr
                        bg = sg + dg - sg * dg
                        bb = sb + db - sb * db
                    }
                    OVERLAY -> {
                        br = hardLightChannel(dr, sr)
                        bg = hardLightChannel(dg, sg)
                        bb = hardLightChannel(db, sb)
                    }
                    DARKEN -> {
                        br = min(sr, dr)
                        bg = min(sg, dg)
                        bb = min(sb, db)
                    }
                    LIGHTEN -> {
                        br = max(sr, dr)
                        bg = max(sg, dg)
                        bb = max(sb, db)
                    }
                    COLOR_DODGE -> {
                        br = colorDodgeChannel(dr, sr)
                        bg = colorDodgeChannel(dg, sg)
                        bb = colorDodgeChannel(db, sb)
                    }
                    COLOR_BURN -> {
                        br = colorBurnChannel(dr, sr)
                        bg = colorBurnChannel(dg, sg)
                        bb = colorBurnChannel(db, sb)
                    }
                    HARD_LIGHT -> {
                        br = hardLightChannel(sr, dr)
                        bg = hardLightChannel(sg, dg)
                        bb = hardLightChannel(sb, db)
                    }
                    SOFT_LIGHT -> {
                        br = softLightChannel(dr, sr)
                        bg = softLightChannel(dg, sg)
                        bb = softLightChannel(db, sb)
                    }
                    DIFFERENCE -> {
                        br = abs(dr - sr)
                        bg = abs(dg - sg)
                        bb = abs(db - sb)
                    }
                    EXCLUSION -> {
                        br = dr + sr - 2f * dr * sr
                        bg = dg + sg - 2f * dg * sg
                        bb = db + sb - 2f * db * sb
                    }
                    HUE -> {
                        val c = setLum(setSat(sr, sg, sb, sat(dr, dg, db)), lum(dr, dg, db))
                        br = c[0]; bg = c[1]; bb = c[2]
                    }
                    SATURATION -> {
                        val c = setLum(setSat(dr, dg, db, sat(sr, sg, sb)), lum(dr, dg, db))
                        br = c[0]; bg = c[1]; bb = c[2]
                    }
                    COLOR -> {
                        val c = setLum(floatArrayOf(sr, sg, sb), lum(dr, dg, db))
                        br = c[0]; bg = c[1]; bb = c[2]
                    }
                    LUMINOSITY -> {
                        val c = setLum(floatArrayOf(dr, dg, db), lum(sr, sg, sb))
                        br = c[0]; bg = c[1]; bb = c[2]
                    }
                    else -> {
                        br = sr; bg = sg; bb = sb
                    }
                }
                outA = sa + da - sa * da
                outRa = (1f - da) * sra + (1f - sa) * dra + sa * da * br
                outGa = (1f - da) * sga + (1f - sa) * dga + sa * da * bg
                outBa = (1f - da) * sba + (1f - sa) * dba + sa * da * bb
            }
        }

        val clampedA = outA.coerceIn(0f, 1f)
        if (clampedA <= 1e-5f) return RgbaColor.TRANSPARENT
        val unpremulR = (outRa / clampedA).coerceIn(0f, 1f)
        val unpremulG = (outGa / clampedA).coerceIn(0f, 1f)
        val unpremulB = (outBa / clampedA).coerceIn(0f, 1f)
        return RgbaColor(
            r = (unpremulR * 255f).roundToInt().coerceIn(0, 255),
            g = (unpremulG * 255f).roundToInt().coerceIn(0, 255),
            b = (unpremulB * 255f).roundToInt().coerceIn(0, 255),
            a = (clampedA * 255f).roundToInt().coerceIn(0, 255)
        )
    }

    private fun hardLightChannel(s: Float, d: Float): Float =
        if (s <= 0.5f) 2f * s * d else 1f - 2f * (1f - s) * (1f - d)

    private fun colorDodgeChannel(d: Float, s: Float): Float =
        when {
            d <= 0f -> 0f
            s >= 1f -> 1f
            else -> min(1f, d / (1f - s))
        }

    private fun colorBurnChannel(d: Float, s: Float): Float =
        when {
            d >= 1f -> 1f
            s <= 0f -> 0f
            else -> 1f - min(1f, (1f - d) / s)
        }

    private fun softLightChannel(d: Float, s: Float): Float {
        return if (s <= 0.5f) {
            d - (1f - 2f * s) * d * (1f - d)
        } else {
            val g = if (d <= 0.25f) ((16f * d - 12f) * d + 4f) * d else sqrt(d)
            d + (2f * s - 1f) * (g - d)
        }
    }

    private fun lum(r: Float, g: Float, b: Float): Float =
        0.3f * r + 0.59f * g + 0.11f * b

    private fun sat(r: Float, g: Float, b: Float): Float =
        max(r, max(g, b)) - min(r, min(g, b))

    private fun setLum(rgb: FloatArray, l: Float): FloatArray {
        val d = l - lum(rgb[0], rgb[1], rgb[2])
        var r = rgb[0] + d
        var g = rgb[1] + d
        var b = rgb[2] + d
        val curL = lum(r, g, b)
        val n = min(r, min(g, b))
        val x = max(r, max(g, b))
        if (n < 0f && curL - n > 1e-5f) {
            r = curL + ((r - curL) * curL) / (curL - n)
            g = curL + ((g - curL) * curL) / (curL - n)
            b = curL + ((b - curL) * curL) / (curL - n)
        }
        if (x > 1f && x - curL > 1e-5f) {
            r = curL + ((r - curL) * (1f - curL)) / (x - curL)
            g = curL + ((g - curL) * (1f - curL)) / (x - curL)
            b = curL + ((b - curL) * (1f - curL)) / (x - curL)
        }
        return floatArrayOf(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
    }

    private fun setSat(r: Float, g: Float, b: Float, s: Float): FloatArray {
        val cMin = min(r, min(g, b))
        val cMax = max(r, max(g, b))
        if (cMax <= cMin + 1e-5f) return floatArrayOf(0f, 0f, 0f)
        val scale = s / (cMax - cMin)
        return floatArrayOf((r - cMin) * scale, (g - cMin) * scale, (b - cMin) * scale)
    }

    companion object {
        fun fromWebValue(value: String): ImageBufferCompositeOperation {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.webValue == normalized } ?: SOURCE_OVER
        }
    }
}
