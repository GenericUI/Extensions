package com.nexustools.genericui.ext.imageprocessing

/**
 * 256-bin red, green, blue, and Rec.709 luminance histogram computed by [GpuImageProcessor.computeHistogram].
 */
data class ImageHistogramResult(
    val redBins: IntArray = IntArray(256),
    val greenBins: IntArray = IntArray(256),
    val blueBins: IntArray = IntArray(256),
    val luminanceBins: IntArray = IntArray(256),
    val totalPixels: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImageHistogramResult) return false
        return totalPixels == other.totalPixels &&
            redBins.contentEquals(other.redBins) &&
            greenBins.contentEquals(other.greenBins) &&
            blueBins.contentEquals(other.blueBins) &&
            luminanceBins.contentEquals(other.luminanceBins)
    }

    override fun hashCode(): Int {
        var result = redBins.contentHashCode()
        result = 31 * result + greenBins.contentHashCode()
        result = 31 * result + blueBins.contentHashCode()
        result = 31 * result + luminanceBins.contentHashCode()
        result = 31 * result + totalPixels
        return result
    }
}
