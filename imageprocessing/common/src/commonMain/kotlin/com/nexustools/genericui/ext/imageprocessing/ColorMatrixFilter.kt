package com.nexustools.genericui.ext.imageprocessing

import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat
import kotlin.math.roundToInt

/**
 * Offscreen GPU filter that multiplies each RGBA pixel by a 4x5 color transformation matrix
 * (20 floats in row-major order, where the 5th column is a normalized `[-1..1]` offset).
 */
class ColorMatrixFilter(
    val matrix4x5: FloatArray
) : ImageFilterOperation {

    init {
        require(matrix4x5.size == 20) { "ColorMatrixFilter requires a 20-element (4x5) FloatArray" }
    }

    override val name: String = "ColorMatrix"

    override val vulkanShaderSource: String = """
        #version 450
        layout(location = 0) in vec2 vUv;
        layout(location = 0) out vec4 outColor;
        layout(set = 0, binding = 0) uniform sampler2D uTexture;
        layout(push_constant) uniform ColorMatrix {
            vec4 row0;
            vec4 row1;
            vec4 row2;
            vec4 row3;
            vec4 offsets;
        } cm;
        void main() {
            vec4 c = texture(uTexture, vUv);
            vec4 res = vec4(
                dot(c, cm.row0) + cm.offsets.r,
                dot(c, cm.row1) + cm.offsets.g,
                dot(c, cm.row2) + cm.offsets.b,
                dot(c, cm.row3) + cm.offsets.a
            );
            outColor = clamp(res, 0.0, 1.0);
        }
    """.trimIndent()

    override val glesShaderSource: String = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        out vec4 outColor;
        uniform sampler2D uTexture;
        uniform vec4 uRow0;
        uniform vec4 uRow1;
        uniform vec4 uRow2;
        uniform vec4 uRow3;
        uniform vec4 uOffsets;
        void main() {
            vec4 c = texture(uTexture, vUv);
            vec4 res = vec4(
                dot(c, uRow0) + uOffsets.r,
                dot(c, uRow1) + uOffsets.g,
                dot(c, uRow2) + uOffsets.b,
                dot(c, uRow3) + uOffsets.a
            );
            outColor = clamp(res, 0.0, 1.0);
        }
    """.trimIndent()

    override fun executeOnGpuOrFallback(input: ImageBuffer, gpu: GpuOffscreenContext): ImageBuffer {
        val srcRgba = input.toRgba8888()
        val srcTex = gpu.uploadTexture(srcRgba)
        val dstTex = gpu.createEmptyTexture(srcRgba.width, srcRgba.height, PixelFormat.RGBA_8888)
        val program = gpu.compileComputeOrFragmentKernel(vulkanShaderSource, glesShaderSource)
        gpu.dispatchImageKernel(program, listOf(srcTex), dstTex, matrix4x5)
        gpu.destroyShaderProgram(program)
        gpu.destroyTexture(srcTex)
        gpu.destroyTexture(dstTex)

        val out = ImageBuffer(srcRgba.width, srcRgba.height, PixelFormat.RGBA_8888, srcRgba.colorSpace)
        val inBytes = srcRgba.bytes
        val outBytes = out.bytes
        val m = matrix4x5
        var i = 0
        while (i + 3 < inBytes.size) {
            val r = (inBytes[i].toInt() and 0xFF) / 255f
            val g = (inBytes[i + 1].toInt() and 0xFF) / 255f
            val b = (inBytes[i + 2].toInt() and 0xFF) / 255f
            val a = (inBytes[i + 3].toInt() and 0xFF) / 255f

            val nr = (m[0] * r + m[1] * g + m[2] * b + m[3] * a + m[4]).coerceIn(0f, 1f)
            val ng = (m[5] * r + m[6] * g + m[7] * b + m[8] * a + m[9]).coerceIn(0f, 1f)
            val nb = (m[10] * r + m[11] * g + m[12] * b + m[13] * a + m[14]).coerceIn(0f, 1f)
            val na = (m[15] * r + m[16] * g + m[17] * b + m[18] * a + m[19]).coerceIn(0f, 1f)

            outBytes[i] = (nr * 255f).roundToInt().toByte()
            outBytes[i + 1] = (ng * 255f).roundToInt().toByte()
            outBytes[i + 2] = (nb * 255f).roundToInt().toByte()
            outBytes[i + 3] = (na * 255f).roundToInt().toByte()
            i += 4
        }
        return out
    }

    companion object {
        fun grayscale(): ColorMatrixFilter = ColorMatrixFilter(
            floatArrayOf(
                0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        fun sepia(): ColorMatrixFilter = ColorMatrixFilter(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        fun invert(): ColorMatrixFilter = ColorMatrixFilter(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 1f,
                0f, -1f, 0f, 0f, 1f,
                0f, 0f, -1f, 0f, 1f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }
}
