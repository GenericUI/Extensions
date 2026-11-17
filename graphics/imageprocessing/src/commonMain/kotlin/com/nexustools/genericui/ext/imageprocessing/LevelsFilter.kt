package com.nexustools.genericui.ext.imageprocessing

import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Offscreen GPU levels adjustment filter mapping input `[inBlack .. inWhite]` with [midtoneGamma]
 * to `[outBlack .. outWhite]`.
 */
class LevelsFilter(
    val inBlack: Float = 0.0f,
    val inWhite: Float = 1.0f,
    val midtoneGamma: Float = 1.0f,
    val outBlack: Float = 0.0f,
    val outWhite: Float = 1.0f
) : ImageFilterOperation {

    override val name: String = "Levels"

    override val vulkanShaderSource: String = """
        #version 450
        layout(location = 0) in vec2 vUv;
        layout(location = 0) out vec4 outColor;
        layout(set = 0, binding = 0) uniform sampler2D uTexture;
        layout(push_constant) uniform LevelsParams {
            float inBlack;
            float inWhite;
            float invGamma;
            float outBlack;
            float outWhite;
        } pc;
        void main() {
            vec4 c = texture(uTexture, vUv);
            vec3 norm = clamp((c.rgb - vec3(pc.inBlack)) / max(pc.inWhite - pc.inBlack, 0.0001), 0.0, 1.0);
            vec3 curved = pow(norm, vec3(pc.invGamma));
            vec3 mapped = mix(vec3(pc.outBlack), vec3(pc.outWhite), curved);
            outColor = vec4(clamp(mapped, 0.0, 1.0), c.a);
        }
    """.trimIndent()

    override val glesShaderSource: String = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        out vec4 outColor;
        uniform sampler2D uTexture;
        uniform float uInBlack;
        uniform float uInWhite;
        uniform float uInvGamma;
        uniform float uOutBlack;
        uniform float uOutWhite;
        void main() {
            vec4 c = texture(uTexture, vUv);
            vec3 norm = clamp((c.rgb - vec3(uInBlack)) / max(uInWhite - uInBlack, 0.0001), 0.0, 1.0);
            vec3 curved = pow(norm, vec3(uInvGamma));
            vec3 mapped = mix(vec3(uOutBlack), vec3(uOutWhite), curved);
            outColor = vec4(clamp(mapped, 0.0, 1.0), c.a);
        }
    """.trimIndent()

    override fun executeOnGpuOrFallback(input: ImageBuffer, gpu: GpuOffscreenContext): ImageBuffer {
        val srcRgba = input.toRgba8888()
        val invGamma = 1.0f / midtoneGamma.coerceIn(0.1f, 10.0f)
        val inSpan = (inWhite - inBlack).coerceAtLeast(1e-4f)
        val outSpan = outWhite - outBlack

        val srcTex = gpu.uploadTexture(srcRgba)
        val dstTex = gpu.createEmptyTexture(srcRgba.width, srcRgba.height, PixelFormat.RGBA_8888)
        val program = gpu.compileComputeOrFragmentKernel(vulkanShaderSource, glesShaderSource)
        gpu.dispatchImageKernel(
            program,
            listOf(srcTex),
            dstTex,
            floatArrayOf(inBlack, inWhite, invGamma, outBlack, outWhite)
        )
        gpu.destroyShaderProgram(program)
        gpu.destroyTexture(srcTex)
        gpu.destroyTexture(dstTex)

        val out = ImageBuffer(srcRgba.width, srcRgba.height, PixelFormat.RGBA_8888, srcRgba.colorSpace)
        val inBytes = srcRgba.bytes
        val outBytes = out.bytes
        var i = 0
        while (i + 3 < inBytes.size) {
            for (ch in 0..2) {
                val v = (inBytes[i + ch].toInt() and 0xFF) / 255f
                val normalized = ((v - inBlack) / inSpan).coerceIn(0f, 1f)
                val gammaAdjusted = normalized.pow(invGamma)
                val mapped = (outBlack + gammaAdjusted * outSpan).coerceIn(0f, 1f)
                outBytes[i + ch] = (mapped * 255f).roundToInt().toByte()
            }
            outBytes[i + 3] = inBytes[i + 3]
            i += 4
        }
        return out
    }
}
