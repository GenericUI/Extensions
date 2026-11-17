package com.nexustools.genericui.ext.engine.scene

/**
 * Classification metadata for a [SceneEntity] using a textual [tag] and 32-bit [layerMask].
 */
data class TagAndLayerMask(
    var tag: String = "Default",
    var layerMask: Int = 1
) {
    fun hasLayer(layerBitIndex: Int): Boolean {
        if (layerBitIndex !in 0..31) return false
        return (layerMask and (1 shl layerBitIndex)) != 0
    }

    fun setLayer(layerBitIndex: Int, enabled: Boolean) {
        if (layerBitIndex !in 0..31) return
        val bit = 1 shl layerBitIndex
        layerMask = if (enabled) (layerMask or bit) else (layerMask and bit.inv())
    }

    fun matchesMask(queryMask: Int): Boolean = (layerMask and queryMask) != 0
}
