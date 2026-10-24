package com.nexustools.genericui.ext.imageprocessing

import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat
import kotlin.math.roundToInt

/**
 * Offscreen GPU filter that adjusts image [brightness] (`[-1.0 .. 1.0]`) and [contrast] (`[0.0 .. 4.0]`).
 */
class BrightnessContrastFilter(
    val brightness: Float = 0.0f,
    val contrast: Float = 1.0f
) : ImageFilterOperation {

    override val name: String = "BrightnessContrast"

    override val vulkanShaderSource: String = """
        #version 450
        layout(location = 0) in vec2 vUv;
        layout(location = 0) out vec4 outColor;
        layout(set = 0, binding = 0) uniform sampler2D uTexture;
        layout(push_constant) uniform Params {
            float brightness;
            float contrast;
        } pc;
        void main() {
            vec4 c = texture(uTexture, vUv);
            vec3 rgb = (c.rgb - 0.5) * pc.contrast + 0.5 + pc.brightness;
            outColor = vec4(clamp(rgb, 0.0, 1.0), c.a);
        }
    """.trimIndent()

    override val glesShaderSource: String = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        out vec4 outColor;
        uniform sampler2D uTexture;
        uniform float uBrightness;
        uniform float uContrast;
        void main() {
            vec4 c = texture(uTexture, vUv);
            vec3 rgb = (c.rgb - 0.5) * uContrast + 0.5 + uBrightness;
            outColor = vec4(clamp(rgb, 0.0, 1.0), c.a);
        }
    """.trimIndent()

    override fun executeOnGpuOrFallback(input: ImageBuffer, gpu: GpuOffscreenContext): ImageBuffer {
        val srcRgba = input.toRgba8888()
        val srcTex = gpu.uploadTexture(srcRgba)
        val dstTex = gpu.createEmptyTexture(srcRgba.width, srcRgba.height, PixelFormat.RGBA_8888)
        val program = gpu.compileComputeOrFragmentKernel(vulkanShaderSource, glesShaderSource)
        val b = brightness.coerceIn(-1f, 1f)
        val c = contrast.coerceIn(0f, 4f)
        gpu.dispatchImageKernel(program, listOf(srcTex), dstTex, floatArrayOf(b, c))
        gpu.destroyShaderProgram(program)
        gpu.destroyTexture(srcTex)
        gpu.destroyTexture(dstTex)

        val out = ImageBuffer(srcRgba.width, srcRgba.height, PixelFormat.RGBA_8888, srcRgba.colorSpace)
        val inBytes = srcRgba.bytes
        val outBytes = out.bytes
        var i = 0
        while (i + 3 < inBytes.size) {
            val r = (inBytes[i].toInt() and 0xFF) / 255f
            val g = (inBytes[i + 1].toInt() and 0xFF) / 255f
            val bl = (inBytes[i + 2].toInt() and 0xFF) / 255f

            val nr = (((r - 0.5f) * c + 0.5f + b).coerceIn(0f, 1f) * 255f).roundToInt().toByte()
            val ng = (((g - 0.5f) * c + 0.5f + b).coerceIn(0f, 1f) * 255f).roundToInt().toByte()
            val nb = (((bl - 0.5f) * c + 0.5f + b).coerceIn(0f, 1f) * 255f).roundToInt().toByte()

            outBytes[i] = nr
            outBytes[i + 1] = ng
            outBytes[i + 2] = nb
            outBytes[i + 3] = inBytes[i + 3]
            i += 4
        }
        return out
    }
}
