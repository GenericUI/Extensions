package com.nexustools.genericui.ext.imageprocessing

import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Separable 2-pass (horizontal + vertical) Gaussian blur filter executed offscreen via `:extensions:gpu`.
 */
class GaussianBlurFilter(
    val radiusPx: Int = 3,
    val sigma: Float = max(0.5f, radiusPx / 2.0f)
) : ImageFilterOperation {

    override val name: String = "GaussianBlur"

    override val vulkanShaderSource: String = """
        #version 450
        layout(location = 0) in vec2 vUv;
        layout(location = 0) out vec4 outColor;
        layout(set = 0, binding = 0) uniform sampler2D uTexture;
        layout(push_constant) uniform BlurParams {
            vec2 directionStep;
            int radius;
            float sigma;
        } pc;
        void main() {
            outColor = texture(uTexture, vUv);
        }
    """.trimIndent()

    override val glesShaderSource: String = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        out vec4 outColor;
        uniform sampler2D uTexture;
        uniform vec2 uDirectionStep;
        uniform int uRadius;
        uniform float uSigma;
        void main() {
            outColor = texture(uTexture, vUv);
        }
    """.trimIndent()

    override fun executeOnGpuOrFallback(input: ImageBuffer, gpu: GpuOffscreenContext): ImageBuffer {
        val srcRgba = input.toRgba8888()
        val r = radiusPx.coerceIn(0, 32)
        if (r == 0) return srcRgba.copy()

        val srcTex = gpu.uploadTexture(srcRgba)
        val pass1Tex = gpu.createEmptyTexture(srcRgba.width, srcRgba.height, PixelFormat.RGBA_8888)
        val dstTex = gpu.createEmptyTexture(srcRgba.width, srcRgba.height, PixelFormat.RGBA_8888)
        val program = gpu.compileComputeOrFragmentKernel(vulkanShaderSource, glesShaderSource)
        gpu.dispatchImageKernel(program, listOf(srcTex), pass1Tex, floatArrayOf(1f / srcRgba.width, 0f, r.toFloat(), sigma))
        gpu.dispatchImageKernel(program, listOf(pass1Tex), dstTex, floatArrayOf(0f, 1f / srcRgba.height, r.toFloat(), sigma))
        gpu.destroyShaderProgram(program)
        gpu.destroyTexture(srcTex)
        gpu.destroyTexture(pass1Tex)
        gpu.destroyTexture(dstTex)

        val kernelSize = 2 * r + 1
        val weights = FloatArray(kernelSize)
        val twoSigmaSq = 2f * sigma * sigma
        var weightSum = 0f
        for (k in -r..r) {
            val w = exp(-(k * k).toFloat() / twoSigmaSq)
            weights[k + r] = w
            weightSum += w
        }
        for (i in weights.indices) {
            weights[i] /= weightSum
        }

        val w = srcRgba.width
        val h = srcRgba.height
        val temp = ByteArray(srcRgba.bytes.size)
        val out = ImageBuffer(w, h, PixelFormat.RGBA_8888, srcRgba.colorSpace)
        val inBytes = srcRgba.bytes
        val outBytes = out.bytes

        // Horizontal pass
        for (y in 0 until h) {
            for (x in 0 until w) {
                var accR = 0f
                var accG = 0f
                var accB = 0f
                var accA = 0f
                for (k in -r..r) {
                    val sx = (x + k).coerceIn(0, w - 1)
                    val idx = (y * w + sx) * 4
                    val wk = weights[k + r]
                    accR += (inBytes[idx].toInt() and 0xFF) * wk
                    accG += (inBytes[idx + 1].toInt() and 0xFF) * wk
                    accB += (inBytes[idx + 2].toInt() and 0xFF) * wk
                    accA += (inBytes[idx + 3].toInt() and 0xFF) * wk
                }
                val dstIdx = (y * w + x) * 4
                temp[dstIdx] = accR.roundToInt().coerceIn(0, 255).toByte()
                temp[dstIdx + 1] = accG.roundToInt().coerceIn(0, 255).toByte()
                temp[dstIdx + 2] = accB.roundToInt().coerceIn(0, 255).toByte()
                temp[dstIdx + 3] = accA.roundToInt().coerceIn(0, 255).toByte()
            }
        }

        // Vertical pass
        for (y in 0 until h) {
            for (x in 0 until w) {
                var accR = 0f
                var accG = 0f
                var accB = 0f
                var accA = 0f
                for (k in -r..r) {
                    val sy = (y + k).coerceIn(0, h - 1)
                    val idx = (sy * w + x) * 4
                    val wk = weights[k + r]
                    accR += (temp[idx].toInt() and 0xFF) * wk
                    accG += (temp[idx + 1].toInt() and 0xFF) * wk
                    accB += (temp[idx + 2].toInt() and 0xFF) * wk
                    accA += (temp[idx + 3].toInt() and 0xFF) * wk
                }
                val dstIdx = (y * w + x) * 4
                outBytes[dstIdx] = accR.roundToInt().coerceIn(0, 255).toByte()
                outBytes[dstIdx + 1] = accG.roundToInt().coerceIn(0, 255).toByte()
                outBytes[dstIdx + 2] = accB.roundToInt().coerceIn(0, 255).toByte()
                outBytes[dstIdx + 3] = accA.roundToInt().coerceIn(0, 255).toByte()
            }
        }
        return out
    }
}
