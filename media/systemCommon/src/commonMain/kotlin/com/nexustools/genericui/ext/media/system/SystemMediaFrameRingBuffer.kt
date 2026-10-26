package com.nexustools.genericui.ext.media.system

import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat

/**
 * Shared system-target (`:extensions:media:systemCommon`) pre-allocated video frame ring buffer
 * for zero-allocation hardware/software decoded video frame staging on Desktop, Swing, and Mobile.
 */
class SystemMediaFrameRingBuffer(
    width: Int = 1280,
    height: Int = 720,
    val capacity: Int = 3
) {
    private val slots: Array<ImageBuffer> = Array(capacity.coerceAtLeast(2)) {
        ImageBuffer(width.coerceAtLeast(1), height.coerceAtLeast(1), PixelFormat.RGBA_8888)
    }
    private var writeIndex: Int = 0
    private var readIndex: Int = 0

    fun acquireWriteSlot(): ImageBuffer {
        val slot = slots[writeIndex]
        readIndex = writeIndex
        writeIndex = (writeIndex + 1) % slots.size
        return slot
    }

    fun latestPresentedSlot(): ImageBuffer = slots[readIndex]
}
