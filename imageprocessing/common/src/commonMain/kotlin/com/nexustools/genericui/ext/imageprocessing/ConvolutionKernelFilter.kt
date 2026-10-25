package com.nexustools.genericui.ext.imageprocessing

import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat
import kotlin.math.roundToInt

/**
 * Offscreen GPU 2D spatial convolution filter supporting 3x3 and 5x5 kernels (sharpen, unsharp mask,
 * edge detect, emboss, Sobel).
 */
class ConvolutionKernelFilter(
    val kernelWidth: Int,
    val kernelHeight: Int,
    val weights: FloatArray,
    val divisor: Float = weights.sum().let { if (it == 0f) 1f else it },
    val bias: Float = 0.0f
) : ImageFilterOperation {

    init {
        require(kernelWidth >= 1 && kernelHeight >= 1 && weights.size == kernelWidth * kernelHeight) {
            "ConvolutionKernelFilter weights size (${weights.size}) must equal kernelWidth * kernelHeight ($kernelWidth * $kernelHeight)"
        }
    }

    override val name: String = "ConvolutionKernel(${kernelWidth}x$kernelHeight)"

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
        gpu.dispatchImageKernel(program, listOf(srcTex), dstTex, weights)
        gpu.destroyShaderProgram(program)
        gpu.destroyTexture(srcTex)
        gpu.destroyTexture(dstTex)

        val w = srcRgba.width
        val h = srcRgba.height
        val out = ImageBuffer(w, h, PixelFormat.RGBA_8888, srcRgba.colorSpace)
        val inBytes = srcRgba.bytes
        val outBytes = out.bytes
        val halfW = kernelWidth / 2
        val halfH = kernelHeight / 2
        val invDiv = if (divisor == 0f) 1f else 1f / divisor

        for (y in 0 until h) {
            for (x in 0 until w) {
                var accR = 0f
                var accG = 0f
                var accB = 0f
                for (ky in 0 until kernelHeight) {
                    val sy = (y + ky - halfH).coerceIn(0, h - 1)
                    for (kx in 0 until kernelWidth) {
                        val sx = (x + kx - halfW).coerceIn(0, w - 1)
                        val weight = weights[ky * kernelWidth + kx]
                        val srcIdx = (sy * w + sx) * 4
                        accR += (inBytes[srcIdx].toInt() and 0xFF) * weight
                        accG += (inBytes[srcIdx + 1].toInt() and 0xFF) * weight
                        accB += (inBytes[srcIdx + 2].toInt() and 0xFF) * weight
                    }
                }
                val dstIdx = (y * w + x) * 4
                outBytes[dstIdx] = (accR * invDiv + bias * 255f).roundToInt().coerceIn(0, 255).toByte()
                outBytes[dstIdx + 1] = (accG * invDiv + bias * 255f).roundToInt().coerceIn(0, 255).toByte()
                outBytes[dstIdx + 2] = (accB * invDiv + bias * 255f).roundToInt().coerceIn(0, 255).toByte()
                outBytes[dstIdx + 3] = inBytes[dstIdx + 3]
            }
        }
        return out
    }

    companion object {
        fun sharpen(): ConvolutionKernelFilter = ConvolutionKernelFilter(
            kernelWidth = 3,
            kernelHeight = 3,
            weights = floatArrayOf(
                0f, -1f, 0f,
                -1f, 5f, -1f,
                0f, -1f, 0f
            ),
            divisor = 1f
        )

        fun edgeDetect(): ConvolutionKernelFilter = ConvolutionKernelFilter(
            kernelWidth = 3,
            kernelHeight = 3,
            weights = floatArrayOf(
                -1f, -1f, -1f,
                -1f, 8f, -1f,
                -1f, -1f, -1f
            ),
            divisor = 1f
        )

        fun emboss(): ConvolutionKernelFilter = ConvolutionKernelFilter(
            kernelWidth = 3,
            kernelHeight = 3,
            weights = floatArrayOf(
                -2f, -1f, 0f,
                -1f, 1f, 1f,
                0f, 1f, 2f
            ),
            divisor = 1f,
            bias = 0.5f
        )
    }
}
