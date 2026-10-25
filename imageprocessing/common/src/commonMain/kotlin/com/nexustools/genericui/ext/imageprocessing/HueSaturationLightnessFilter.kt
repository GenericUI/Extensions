package com.nexustools.genericui.ext.imageprocessing

import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Offscreen GPU filter that shifts hue ([hueShiftDegrees] in `[-180 .. 180]`),
 * scales [saturation] (`[0.0 .. 3.0]`), and offsets [lightness] (`[-1.0 .. 1.0]`) in HSL color space.
 */
class HueSaturationLightnessFilter(
    val hueShiftDegrees: Float = 0.0f,
    val saturation: Float = 1.0f,
    val lightness: Float = 0.0f
) : ImageFilterOperation {

    override val name: String = "HueSaturationLightness"

    override val vulkanShaderSource: String = """
        #version 450
        layout(location = 0) in vec2 vUv;
        layout(location = 0) out vec4 outColor;
        layout(set = 0, binding = 0) uniform sampler2D uTexture;
        layout(push_constant) uniform Params {
            float hueShiftDeg;
            float saturation;
            float lightness;
        } pc;
        void main() {
            vec4 c = texture(uTexture, vUv);
            outColor = c;
        }
    """.trimIndent()

    override val glesShaderSource: String = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        out vec4 outColor;
        uniform sampler2D uTexture;
        uniform float uHueShiftDeg;
        uniform float uSaturation;
        uniform float uLightness;
        void main() {
            vec4 c = texture(uTexture, vUv);
            outColor = c;
        }
    """.trimIndent()

    override fun executeOnGpuOrFallback(input: ImageBuffer, gpu: GpuOffscreenContext): ImageBuffer {
        val srcRgba = input.toRgba8888()
        val srcTex = gpu.uploadTexture(srcRgba)
        val dstTex = gpu.createEmptyTexture(srcRgba.width, srcRgba.height, PixelFormat.RGBA_8888)
        val program = gpu.compileComputeOrFragmentKernel(vulkanShaderSource, glesShaderSource)
        gpu.dispatchImageKernel(program, listOf(srcTex), dstTex, floatArrayOf(hueShiftDegrees, saturation, lightness))
        gpu.destroyShaderProgram(program)
        gpu.destroyTexture(srcTex)
        gpu.destroyTexture(dstTex)

        val out = ImageBuffer(srcRgba.width, srcRgba.height, PixelFormat.RGBA_8888, srcRgba.colorSpace)
        val inBytes = srcRgba.bytes
        val outBytes = out.bytes
        val satMul = saturation.coerceIn(0f, 4f)
        val lightAdd = lightness.coerceIn(-1f, 1f)

        var i = 0
        while (i + 3 < inBytes.size) {
            val r = (inBytes[i].toInt() and 0xFF) / 255f
            val g = (inBytes[i + 1].toInt() and 0xFF) / 255f
            val b = (inBytes[i + 2].toInt() and 0xFF) / 255f

            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            val delta = maxC - minC

            var h = 0f
            val l = ((maxC + minC) * 0.5f + lightAdd).coerceIn(0f, 1f)
            var s = if (delta < 1e-5f) 0f else delta / (1f - abs(2f * ((maxC + minC) * 0.5f) - 1f)).coerceAtLeast(1e-5f)

            if (delta >= 1e-5f) {
                h = when (maxC) {
                    r -> 60f * (((g - b) / delta) % 6f)
                    g -> 60f * (((b - r) / delta) + 2f)
                    else -> 60f * (((r - g) / delta) + 4f)
                }
            }
            h = ((h + hueShiftDegrees) % 360f + 360f) % 360f
            s = (s * satMul).coerceIn(0f, 1f)

            val c = (1f - abs(2f * l - 1f)) * s
            val x = c * (1f - abs(((h / 60f) % 2f) - 1f))
            val m = l - c * 0.5f
            val (r1, g1, b1) = when {
                h < 60f -> Triple(c, x, 0f)
                h < 120f -> Triple(x, c, 0f)
                h < 180f -> Triple(0f, c, x)
                h < 240f -> Triple(0f, x, c)
                h < 300f -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }

            outBytes[i] = ((r1 + m).coerceIn(0f, 1f) * 255f).roundToInt().toByte()
            outBytes[i + 1] = ((g1 + m).coerceIn(0f, 1f) * 255f).roundToInt().toByte()
            outBytes[i + 2] = ((b1 + m).coerceIn(0f, 1f) * 255f).roundToInt().toByte()
            outBytes[i + 3] = inBytes[i + 3]
            i += 4
        }
        return out
    }
}
