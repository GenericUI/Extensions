@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.webview.desktop.ipc

import com.nexustools.genericui.component.system.PreferenceElement
import kotlin.concurrent.atomics.AtomicInt

/**
 * Thread-safe RGBA8888 host framebuffer receiving dirty rectangle tile updates from the isolated
 * worker OS process via BinaryJson `PreferenceElement.ofBytes` packets.
 */
class WebViewHostFramebuffer(
    initialWidth: Int = 1024,
    initialHeight: Int = 768
) {
    private val lock = AtomicInt(0)
    private var bufferWidth: Int = initialWidth.coerceAtLeast(1)
    private var bufferHeight: Int = initialHeight.coerceAtLeast(1)
    private var rgbaData: ByteArray = createBlankWhiteRgba(bufferWidth, bufferHeight)
    private var bgraData: ByteArray = createBlankWhiteRgba(bufferWidth, bufferHeight)
    private var sequenceCounter: Long = 0L

    val width: Int
        get() = withLock { bufferWidth }

    val height: Int
        get() = withLock { bufferHeight }

    val frameSequence: Long
        get() = withLock { sequenceCounter }

    private inline fun <T> withLock(block: () -> T): T {
        while (!lock.compareAndSet(0, 1)) {
            // Short spin lock protecting pixel buffer copies between IPC reader and paint thread.
        }
        return try {
            block()
        } finally {
            lock.store(0)
        }
    }

    fun resize(newWidth: Int, newHeight: Int) {
        val w = newWidth.coerceAtLeast(1)
        val h = newHeight.coerceAtLeast(1)
        withLock {
            if (bufferWidth == w && bufferHeight == h) return@withLock
            val nextRgba = createBlankWhiteRgba(w, h)
            val nextBgra = createBlankWhiteRgba(w, h)
            val copyW = minOf(bufferWidth, w)
            val copyH = minOf(bufferHeight, h)
            val copyRowBytes = copyW * 4
            for (y in 0 until copyH) {
                val srcOff = y * bufferWidth * 4
                val dstOff = y * w * 4
                rgbaData.copyInto(nextRgba, dstOff, srcOff, srcOff + copyRowBytes)
                bgraData.copyInto(nextBgra, dstOff, srcOff, srcOff + copyRowBytes)
            }
            bufferWidth = w
            bufferHeight = h
            rgbaData = nextRgba
            bgraData = nextBgra
            sequenceCounter += 1L
        }
    }

    fun applyDirtyTiles(
        frameWidth: Int,
        frameHeight: Int,
        frameSeq: Long,
        tiles: List<PreferenceElement>
    ) {
        val fw = frameWidth.coerceAtLeast(1)
        val fh = frameHeight.coerceAtLeast(1)
        withLock {
            val sizeChanged = bufferWidth != fw || bufferHeight != fh
            if (sizeChanged) {
                bufferWidth = fw
                bufferHeight = fh
                rgbaData = createBlankWhiteRgba(fw, fh)
                bgraData = createBlankWhiteRgba(fw, fh)
            }
            var wroteAnyTile = false
            for (tile in tiles) {
                val tx = tile["x"]?.asInt(0) ?: 0
                val ty = tile["y"]?.asInt(0) ?: 0
                val tw = tile["w"]?.asInt(0) ?: 0
                val th = tile["h"]?.asInt(0) ?: 0
                val bytes = tile["rgba"]?.bytesOrNull() ?: continue
                if (tw <= 0 || th <= 0 || bytes.size < tw * th * 4) continue
                val startX = tx.coerceIn(0, fw)
                val endX = (tx + tw).coerceIn(startX, fw)
                val startY = ty.coerceIn(0, fh)
                val endY = (ty + th).coerceIn(startY, fh)
                val rowCopyBytes = (endX - startX) * 4
                if (rowCopyBytes <= 0) continue
                wroteAnyTile = true
                val srcSkipX = (startX - tx) * 4
                for (y in startY until endY) {
                    val srcOffset = (y - ty) * tw * 4 + srcSkipX
                    val dstOffset = (y * fw + startX) * 4
                    bytes.copyInto(rgbaData, dstOffset, srcOffset, srcOffset + rowCopyBytes)
                    var s = srcOffset
                    var d = dstOffset
                    val sEnd = srcOffset + rowCopyBytes
                    while (s < sEnd) {
                        bgraData[d] = bytes[s + 2]
                        bgraData[d + 1] = bytes[s + 1]
                        bgraData[d + 2] = bytes[s]
                        bgraData[d + 3] = 0xFF.toByte()
                        s += 4
                        d += 4
                    }
                }
            }
            if (sizeChanged || wroteAnyTile || tiles.isNotEmpty()) {
                sequenceCounter = if (frameSeq > sequenceCounter) frameSeq else sequenceCounter + 1L
            }
        }
    }

    fun copyRgbaSnapshot(): ByteArray = withLock { rgbaData.copyOf() }

    fun copySize(): Pair<Int, Int> = withLock { bufferWidth to bufferHeight }

    fun copyBgraInto(dst: ByteArray): Int = withLock {
        val count = minOf(dst.size, bgraData.size)
        if (count > 0) {
            bgraData.copyInto(dst, 0, 0, count)
        }
        count
    }

    fun copyBgraSnapshot(): ByteArray = withLock { bgraData.copyOf() }

    private fun createBlankWhiteRgba(w: Int, h: Int): ByteArray =
        ByteArray(w * h * 4) { (-1).toByte() }
}
