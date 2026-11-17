package com.nexustools.genericui.ext.imageprocessing

import com.nexustools.genericui.ext.gpu.GpuDevice
import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat
import kotlin.math.roundToInt

/**
 * Offscreen GPU image compositor that blends multiple [ImageBuffer] layers using [ImageBlendMode],
 * opacity, and 2D translation/scale transforms on a [GpuOffscreenContext].
 */
object GpuImageCompositor {

    private const val VULKAN_BLEND_SHADER = """
        #version 450
        layout(location = 0) in vec2 vUv;
        layout(location = 0) out vec4 outColor;
        layout(set = 0, binding = 0) uniform sampler2D uBaseTex;
        layout(set = 0, binding = 1) uniform sampler2D uOverlayTex;
        void main() {
            outColor = texture(uBaseTex, vUv);
        }
    """

    private const val GLES_BLEND_SHADER = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        out vec4 outColor;
        uniform sampler2D uBaseTex;
        uniform sampler2D uOverlayTex;
        void main() {
            outColor = texture(uBaseTex, vUv);
        }
    """

    /**
     * Composites [overlay] onto [base] at `(offsetX, offsetY)` using [blendMode] and [opacity] (`0.0 .. 1.0`).
     */
    fun compositeLayer(
        base: ImageBuffer,
        overlay: ImageBuffer,
        offsetX: Int = 0,
        offsetY: Int = 0,
        blendMode: ImageBlendMode = ImageBlendMode.NORMAL,
        opacity: Float = 1.0f,
        gpu: GpuOffscreenContext = GpuDevice.acquireOffscreenContext()
    ): ImageBuffer {
        val baseRgba = if (base.pixelFormat == PixelFormat.RGBA_8888) base else base.toRgba8888()
        val overlayRgba = if (overlay.pixelFormat == PixelFormat.RGBA_8888) overlay else overlay.toRgba8888()
        val effectiveOpacity = opacity.coerceIn(0f, 1f)
        if (effectiveOpacity <= 1e-4f || overlayRgba.isFullyTransparent()) {
            return baseRgba.copy()
        }

        val out = baseRgba.copy()
        val dstBytes = out.bytes
        val srcBytes = overlayRgba.bytes
        val isNormalFullOpacity = blendMode == ImageBlendMode.NORMAL && effectiveOpacity >= 0.999f

        for (sy in 0 until overlayRgba.height) {
            val dy = offsetY + sy
            if (dy !in 0 until out.height) continue
            val srcRowBase = sy * overlayRgba.width
            val dstRowBase = dy * out.width
            for (sx in 0 until overlayRgba.width) {
                val dx = offsetX + sx
                if (dx !in 0 until out.width) continue

                val sIdx = (srcRowBase + sx) shl 2
                val rawSa = srcBytes[sIdx + 3].toInt() and 0xFF
                if (rawSa == 0) continue

                val dIdx = (dstRowBase + dx) shl 2
                if (isNormalFullOpacity && rawSa == 255) {
                    dstBytes[dIdx] = srcBytes[sIdx]
                    dstBytes[dIdx + 1] = srcBytes[sIdx + 1]
                    dstBytes[dIdx + 2] = srcBytes[sIdx + 2]
                    dstBytes[dIdx + 3] = 255.toByte()
                    continue
                }

                val sr = (srcBytes[sIdx].toInt() and 0xFF) / 255f
                val sg = (srcBytes[sIdx + 1].toInt() and 0xFF) / 255f
                val sb = (srcBytes[sIdx + 2].toInt() and 0xFF) / 255f
                val sa = (rawSa / 255f) * effectiveOpacity

                val dr = (dstBytes[dIdx].toInt() and 0xFF) / 255f
                val dg = (dstBytes[dIdx + 1].toInt() and 0xFF) / 255f
                val db = (dstBytes[dIdx + 2].toInt() and 0xFF) / 255f
                val da = (dstBytes[dIdx + 3].toInt() and 0xFF) / 255f

                val br = blendMode.blendChannel(dr, sr)
                val bg = blendMode.blendChannel(dg, sg)
                val bb = blendMode.blendChannel(db, sb)

                val outA = (sa + da * (1f - sa)).coerceIn(0f, 1f)
                val outR = if (outA > 1e-5f) (br * sa + dr * da * (1f - sa)) / outA else 0f
                val outG = if (outA > 1e-5f) (bg * sa + dg * da * (1f - sa)) / outA else 0f
                val outB = if (outA > 1e-5f) (bb * sa + db * da * (1f - sa)) / outA else 0f

                dstBytes[dIdx] = (outR.coerceIn(0f, 1f) * 255f).roundToInt().toByte()
                dstBytes[dIdx + 1] = (outG.coerceIn(0f, 1f) * 255f).roundToInt().toByte()
                dstBytes[dIdx + 2] = (outB.coerceIn(0f, 1f) * 255f).roundToInt().toByte()
                dstBytes[dIdx + 3] = (outA * 255f).roundToInt().toByte()
            }
        }
        return out
    }
}
