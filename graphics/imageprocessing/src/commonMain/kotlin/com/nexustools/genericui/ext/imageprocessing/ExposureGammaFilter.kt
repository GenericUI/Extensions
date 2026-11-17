package com.nexustools.genericui.ext.imageprocessing

import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Offscreen GPU filter that applies photographic exposure compensation ([exposureEv] in stops)
 * and a non-linear [gamma] curve.
 */
class ExposureGammaFilter(
    val exposureEv: Float = 0.0f,
    val gamma: Float = 1.0f
) : ImageFilterOperation {

    override val name: String = "ExposureGamma"

    override val vulkanShaderSource: String = """
        #version 450
        layout(location = 0) in vec2 vUv;
        layout(location = 0) out vec4 outColor;
        layout(set = 0, binding = 0) uniform sampler2D uTexture;
        layout(push_constant) uniform Params {
            float exposureGain;
            float invGamma;
        } pc;
        void main() {
            vec4 c = texture(uTexture, vUv);
            vec3 rgb = pow(max(c.rgb * pc.exposureGain, vec3(0.0)), vec3(pc.invGamma));
            outColor = vec4(clamp(rgb, 0.0, 1.0), c.a);
        }
    """.trimIndent()

    override val glesShaderSource: String = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        out vec4 outColor;
        uniform sampler2D uTexture;
        uniform float uExposureGain;
        uniform float uInvGamma;
        void main() {
            vec4 c = texture(uTexture, vUv);
            vec3 rgb = pow(max(c.rgb * uExposureGain, vec3(0.0)), vec3(uInvGamma));
            outColor = vec4(clamp(rgb, 0.0, 1.0), c.a);
        }
    """.trimIndent()

    override fun executeOnGpuOrFallback(input: ImageBuffer, gpu: GpuOffscreenContext): ImageBuffer {
        val srcRgba = input.toRgba8888()
        val gain = 2.0f.pow(exposureEv.coerceIn(-10f, 10f))
        val invGamma = 1.0f / gamma.coerceIn(0.1f, 10.0f)

        val srcTex = gpu.uploadTexture(srcRgba)
        val dstTex = gpu.createEmptyTexture(srcRgba.width, srcRgba.height, PixelFormat.RGBA_8888)
        val program = gpu.compileComputeOrFragmentKernel(vulkanShaderSource, glesShaderSource)
        gpu.dispatchImageKernel(program, listOf(srcTex), dstTex, floatArrayOf(gain, invGamma))
        gpu.destroyShaderProgram(program)
        gpu.destroyTexture(srcTex)
        gpu.destroyTexture(dstTex)

        val out = ImageBuffer(srcRgba.width, srcRgba.height, PixelFormat.RGBA_8888, srcRgba.colorSpace)
        val inBytes = srcRgba.bytes
        val outBytes = out.bytes
        var i = 0
        while (i + 3 < inBytes.size) {
            val r = ((inBytes[i].toInt() and 0xFF) / 255f * gain).coerceAtLeast(0f).pow(invGamma).coerceIn(0f, 1f)
            val g = ((inBytes[i + 1].toInt() and 0xFF) / 255f * gain).coerceAtLeast(0f).pow(invGamma).coerceIn(0f, 1f)
            val b = ((inBytes[i + 2].toInt() and 0xFF) / 255f * gain).coerceAtLeast(0f).pow(invGamma).coerceIn(0f, 1f)

            outBytes[i] = (r * 255f).roundToInt().toByte()
            outBytes[i + 1] = (g * 255f).roundToInt().toByte()
            outBytes[i + 2] = (b * 255f).roundToInt().toByte()
            outBytes[i + 3] = inBytes[i + 3]
            i += 4
        }
        return out
    }
}
