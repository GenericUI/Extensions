package com.nexustools.genericui.ext.alien

/**
 * Snapshot of a changed rectangular tile captured from an [AlienRenderSession]
 * framebuffer in tightly packed row-major RGBA byte order (`width * height * 4` bytes).
 */
data class AlienDirtyTile(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val rgbaBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AlienDirtyTile) return false
        return x == other.x &&
            y == other.y &&
            width == other.width &&
            height == other.height &&
            rgbaBytes.contentEquals(other.rgbaBytes)
    }

    override fun hashCode(): Int {
        var result = x
        result = 31 * result + y
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + rgbaBytes.contentHashCode()
        return result
    }
}
