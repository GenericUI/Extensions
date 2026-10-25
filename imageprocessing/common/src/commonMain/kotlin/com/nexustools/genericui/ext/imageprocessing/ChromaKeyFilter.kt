package com.nexustools.genericui.ext.imageprocessing

import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Offscreen GPU chroma-key (green/blue screen) filter that extracts alpha transparency based on
 * Euclidean chroma distance from `(keyR, keyG, keyB)` with [threshold] and soft [feather].
 */
class ChromaKeyFilter(
    val keyR: Int = 0,
    val keyG: Int = 255,
    val keyB: Int = 0,
    val threshold: Float = 0.25f,
    val feather: Float = 0.10f
) : ImageFilterOperation {

    override val name: String = "ChromaKey"

    override val vulkanShaderSource: String = """
        #version 450
        layout(location = 0) in vec2 vUv;
        layout(location = 0) out vec4 outColor;
        layout(set = 0, binding = 0) uniform sampler2D uTexture;
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
        void main() {
            outColor = texture(uTexture, vUv);
        }
    """.trimIndent()

    override fun executeOnGpuOrFallback(input: ImageBuffer, gpu: GpuOffscreenContext): ImageBuffer {
        val srcRgba = input.toRgba8888()
        val kr = keyR.coerceIn(0, 255) / 255f
        val kg = keyG.coerceIn(0, 255) / 255f
        val kb = keyB.coerceIn(0, 255) / 255f

        val srcTex = gpu.uploadTexture(srcRgba)
        val dstTex = gpu.createEmptyTexture(srcRgba.width, srcRgba.height, PixelFormat.RGBA_8888)
        val program = gpu.compileComputeOrFragmentKernel(vulkanShaderSource, glesShaderSource)
        gpu.dispatchImageKernel(program, listOf(srcTex), dstTex, floatArrayOf(kr, kg, kb, threshold, feather))
        gpu.destroyShaderProgram(program)
        gpu.destroyTexture(srcTex)
        gpu.destroyTexture(dstTex)

        val out = ImageBuffer(srcRgba.width, srcRgba.height, PixelFormat.RGBA_8888, srcRgba.colorSpace)
        val inBytes = srcRgba.bytes
        val outBytes = out.bytes
        val softSpan = feather.coerceAtLeast(1e-4f)

        var i = 0
        while (i + 3 < inBytes.size) {
            val r = (inBytes[i].toInt() and 0xFF) / 255f
            val g = (inBytes[i + 1].toInt() and 0xFF) / 255f
            val b = (inBytes[i + 2].toInt() and 0xFF) / 255f
            val a = (inBytes[i + 3].toInt() and 0xFF) / 255f

            val dr = r - kr
            val dg = g - kg
            val db = b - kb
            val dist = sqrt(dr * dr + dg * dg + db * db)
            val mask = when {
                dist <= threshold -> 0f
                dist >= threshold + softSpan -> 1f
                else -> (dist - threshold) / softSpan
            }

            outBytes[i] = inBytes[i]
            outBytes[i + 1] = inBytes[i + 1]
            outBytes[i + 2] = inBytes[i + 2]
            outBytes[i + 3] = ((a * mask).coerceIn(0f, 1f) * 255f).roundToInt().toByte()
            i += 4
        }
        return out
    }
}
