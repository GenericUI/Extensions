package com.nexustools.genericui.ext.imageprocessing

import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Offscreen GPU radial vignette filter that darkens or feathers image edges.
 */
class VignetteFilter(
    val innerRadius: Float = 0.45f,
    val outerRadius: Float = 0.95f,
    val intensity: Float = 0.65f
) : ImageFilterOperation {

    override val name: String = "Vignette"

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
        val srcTex = gpu.uploadTexture(srcRgba)
        val dstTex = gpu.createEmptyTexture(srcRgba.width, srcRgba.height, PixelFormat.RGBA_8888)
        val program = gpu.compileComputeOrFragmentKernel(vulkanShaderSource, glesShaderSource)
        gpu.dispatchImageKernel(program, listOf(srcTex), dstTex, floatArrayOf(innerRadius, outerRadius, intensity))
        gpu.destroyShaderProgram(program)
        gpu.destroyTexture(srcTex)
        gpu.destroyTexture(dstTex)

        val w = srcRgba.width
        val h = srcRgba.height
        val out = ImageBuffer(w, h, PixelFormat.RGBA_8888, srcRgba.colorSpace)
        val inBytes = srcRgba.bytes
        val outBytes = out.bytes
        val span = (outerRadius - innerRadius).coerceAtLeast(1e-4f)
        val strength = intensity.coerceIn(0f, 1f)

        for (y in 0 until h) {
            val ny = ((y + 0.5f) / h - 0.5f) * 2f
            for (x in 0 until w) {
                val nx = ((x + 0.5f) / w - 0.5f) * 2f
                val dist = sqrt(nx * nx + ny * ny)
                val t = ((dist - innerRadius) / span).coerceIn(0f, 1f)
                val smooth = t * t * (3f - 2f * t)
                val factor = 1f - smooth * strength
                val idx = (y * w + x) * 4
                outBytes[idx] = ((inBytes[idx].toInt() and 0xFF) * factor).roundToInt().coerceIn(0, 255).toByte()
                outBytes[idx + 1] = ((inBytes[idx + 1].toInt() and 0xFF) * factor).roundToInt().coerceIn(0, 255).toByte()
                outBytes[idx + 2] = ((inBytes[idx + 2].toInt() and 0xFF) * factor).roundToInt().coerceIn(0, 255).toByte()
                outBytes[idx + 3] = inBytes[idx + 3]
            }
        }
        return out
    }
}
