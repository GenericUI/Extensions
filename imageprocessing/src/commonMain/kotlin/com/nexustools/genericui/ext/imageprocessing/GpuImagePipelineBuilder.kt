package com.nexustools.genericui.ext.imageprocessing

import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.image.ImageBuffer

/**
 * Chainable multi-pass offscreen GPU image pipeline builder used by [GpuImageProcessor.process].
 */
class GpuImagePipelineBuilder(
    source: ImageBuffer,
    val gpu: GpuOffscreenContext
) {
    private var currentBuffer: ImageBuffer = source.toRgba8888()

    fun applyFilter(filter: ImageFilterOperation): GpuImagePipelineBuilder = apply {
        currentBuffer = filter.executeOnGpuOrFallback(currentBuffer, gpu)
    }

    fun brightnessContrast(brightness: Float = 0f, contrast: Float = 1f): GpuImagePipelineBuilder =
        applyFilter(BrightnessContrastFilter(brightness, contrast))

    fun hueSaturationLightness(
        hueShiftDegrees: Float = 0f,
        saturation: Float = 1f,
        lightness: Float = 0f
    ): GpuImagePipelineBuilder =
        applyFilter(HueSaturationLightnessFilter(hueShiftDegrees, saturation, lightness))

    fun exposureGamma(exposureEv: Float = 0f, gamma: Float = 1f): GpuImagePipelineBuilder =
        applyFilter(ExposureGammaFilter(exposureEv, gamma))

    fun colorMatrix(matrix4x5: FloatArray): GpuImagePipelineBuilder =
        applyFilter(ColorMatrixFilter(matrix4x5))

    fun levels(
        inBlack: Float = 0f,
        inWhite: Float = 1f,
        midtoneGamma: Float = 1f,
        outBlack: Float = 0f,
        outWhite: Float = 1f
    ): GpuImagePipelineBuilder =
        applyFilter(LevelsFilter(inBlack, inWhite, midtoneGamma, outBlack, outWhite))

    fun gaussianBlur(radiusPx: Int = 3, sigma: Float = radiusPx / 2f): GpuImagePipelineBuilder =
        applyFilter(GaussianBlurFilter(radiusPx, sigma))

    fun convolution(
        kernelWidth: Int,
        kernelHeight: Int,
        weights: FloatArray,
        divisor: Float = weights.sum().let { if (it == 0f) 1f else it },
        bias: Float = 0f
    ): GpuImagePipelineBuilder =
        applyFilter(ConvolutionKernelFilter(kernelWidth, kernelHeight, weights, divisor, bias))

    fun chromaKey(
        keyR: Int = 0,
        keyG: Int = 255,
        keyB: Int = 0,
        threshold: Float = 0.25f,
        feather: Float = 0.10f
    ): GpuImagePipelineBuilder =
        applyFilter(ChromaKeyFilter(keyR, keyG, keyB, threshold, feather))

    fun vignette(
        innerRadius: Float = 0.45f,
        outerRadius: Float = 0.95f,
        intensity: Float = 0.65f
    ): GpuImagePipelineBuilder =
        applyFilter(VignetteFilter(innerRadius, outerRadius, intensity))

    fun crop(x: Int, y: Int, width: Int, height: Int): GpuImagePipelineBuilder = apply {
        currentBuffer = GpuImageProcessor.crop(currentBuffer, x, y, width, height)
    }

    fun resizeLanczos(targetWidth: Int, targetHeight: Int, a: Int = 3): GpuImagePipelineBuilder = apply {
        currentBuffer = GpuImageProcessor.resizeLanczos(currentBuffer, targetWidth, targetHeight, a, gpu)
    }

    fun rotate(angleDegrees: Float): GpuImagePipelineBuilder = apply {
        currentBuffer = GpuImageProcessor.rotate(currentBuffer, angleDegrees)
    }

    fun composite(
        overlay: ImageBuffer,
        offsetX: Int = 0,
        offsetY: Int = 0,
        blendMode: ImageBlendMode = ImageBlendMode.NORMAL,
        opacity: Float = 1.0f
    ): GpuImagePipelineBuilder = apply {
        currentBuffer = GpuImageCompositor.compositeLayer(currentBuffer, overlay, offsetX, offsetY, blendMode, opacity, gpu)
    }

    fun build(): ImageBuffer = currentBuffer
}
