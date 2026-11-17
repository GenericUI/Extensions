package com.nexustools.genericui.ext.threejs.core

/**
 * 32-bit visibility layer mask (`THREE.Layers`) for selective camera rendering and raycasting.
 */
class Layers {
    var mask: Int = 1

    fun set(channel: Int) {
        mask = 1 shl (channel and 31)
    }

    fun enable(channel: Int) {
        mask = mask or (1 shl (channel and 31))
    }

    fun enableAll() {
        mask = -1
    }

    fun toggle(channel: Int) {
        mask = mask xor (1 shl (channel and 31))
    }

    fun disable(channel: Int) {
        mask = mask and (1 shl (channel and 31)).inv()
    }

    fun disableAll() {
        mask = 0
    }

    fun test(layers: Layers): Boolean =
        (mask and layers.mask) != 0

    fun isEnabled(channel: Int): Boolean =
        (mask and (1 shl (channel and 31))) != 0
}
