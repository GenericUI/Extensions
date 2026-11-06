@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.mediacontrols.windows

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value
import platform.windows.BITMAPINFO
import platform.windows.BITMAPINFOHEADER
import platform.windows.BI_RGB
import platform.windows.CreateBitmap
import platform.windows.CreateDIBSection
import platform.windows.CreateIconIndirect
import platform.windows.DIB_RGB_COLORS
import platform.windows.DeleteObject
import platform.windows.DestroyIcon
import platform.windows.GetDC
import platform.windows.GetSystemMetrics
import platform.windows.HICON
import platform.windows.ICONINFO
import platform.windows.ReleaseDC
import platform.windows.SM_CXSMICON
import kotlin.math.abs

/**
 * Programmatically builds and caches 32-bit ARGB Win32 [HICON] handles for Windows Taskbar
 * thumbnail preview media transport buttons (`Previous`, `Play`, `Pause`, `Next`, `Stop`).
 */
internal class Win32MediaThumbIconFactory {

    private var prevIcon: HICON? = null
    private var playIcon: HICON? = null
    private var pauseIcon: HICON? = null
    private var nextIcon: HICON? = null
    private var stopIcon: HICON? = null

    fun getPrevIcon(): HICON? {
        prevIcon?.let { return it }
        return createGlyphIcon(GLYPH_PREV).also { prevIcon = it }
    }

    fun getPlayIcon(): HICON? {
        playIcon?.let { return it }
        return createGlyphIcon(GLYPH_PLAY).also { playIcon = it }
    }

    fun getPauseIcon(): HICON? {
        pauseIcon?.let { return it }
        return createGlyphIcon(GLYPH_PAUSE).also { pauseIcon = it }
    }

    fun getNextIcon(): HICON? {
        nextIcon?.let { return it }
        return createGlyphIcon(GLYPH_NEXT).also { nextIcon = it }
    }

    fun getStopIcon(): HICON? {
        stopIcon?.let { return it }
        return createGlyphIcon(GLYPH_STOP).also { stopIcon = it }
    }

    fun destroyIcons() {
        prevIcon?.let { DestroyIcon(it) }
        playIcon?.let { DestroyIcon(it) }
        pauseIcon?.let { DestroyIcon(it) }
        nextIcon?.let { DestroyIcon(it) }
        stopIcon?.let { DestroyIcon(it) }
        prevIcon = null
        playIcon = null
        pauseIcon = null
        nextIcon = null
        stopIcon = null
    }

    private fun createGlyphIcon(glyphKind: Int): HICON? = memScoped {
        val size = GetSystemMetrics(SM_CXSMICON).coerceIn(16, 32)

        val bmi = alloc<BITMAPINFO>()
        bmi.bmiHeader.biSize = sizeOf<BITMAPINFOHEADER>().toUInt()
        bmi.bmiHeader.biWidth = size
        bmi.bmiHeader.biHeight = -size // top-down 32-bpp ARGB DIB
        bmi.bmiHeader.biPlanes = 1u
        bmi.bmiHeader.biBitCount = 32u
        bmi.bmiHeader.biCompression = BI_RGB.toUInt()

        val bitsPtrVar = alloc<CPointerVar<ByteVar>>()
        val screenDc = GetDC(null)
        val hbmColor = CreateDIBSection(
            screenDc,
            bmi.ptr,
            DIB_RGB_COLORS.toUInt(),
            bitsPtrVar.ptr.reinterpret(),
            null,
            0u
        )
        ReleaseDC(null, screenDc)
        if (hbmColor == null) return@memScoped null

        val bitsPtr = bitsPtrVar.value
        if (bitsPtr != null) {
            val invSize = 1.0f / size.toFloat()
            for (py in 0 until size) {
                val rowBase = py * size * 4
                for (px in 0 until size) {
                    var subHits = 0
                    for (sy in 0 until 4) {
                        val v = (py + (sy + 0.5f) * 0.25f) * invSize
                        for (sx in 0 until 4) {
                            val u = (px + (sx + 0.5f) * 0.25f) * invSize
                            if (isGlyphSampleInside(glyphKind, u, v)) {
                                subHits++
                            }
                        }
                    }
                    val idx = rowBase + px * 4
                    if (subHits > 0) {
                        val alpha = ((subHits * 255 + 8) shr 4).coerceIn(0, 255).toByte()
                        bitsPtr[idx] = 0xFF.toByte()     // B
                        bitsPtr[idx + 1] = 0xFF.toByte() // G
                        bitsPtr[idx + 2] = 0xFF.toByte() // R
                        bitsPtr[idx + 3] = alpha         // A
                    } else {
                        bitsPtr[idx] = 0
                        bitsPtr[idx + 1] = 0
                        bitsPtr[idx + 2] = 0
                        bitsPtr[idx + 3] = 0
                    }
                }
            }
        }

        val hbmMask = CreateBitmap(size, size, 1u, 1u, null)
        if (hbmMask == null) {
            DeleteObject(hbmColor)
            return@memScoped null
        }

        val iconInfo = alloc<ICONINFO>()
        iconInfo.fIcon = 1
        iconInfo.xHotspot = 0u
        iconInfo.yHotspot = 0u
        iconInfo.hbmMask = hbmMask
        iconInfo.hbmColor = hbmColor

        val created = CreateIconIndirect(iconInfo.ptr)
        DeleteObject(hbmMask)
        DeleteObject(hbmColor)
        created
    }

    private fun isGlyphSampleInside(glyphKind: Int, u: Float, v: Float): Boolean = when (glyphKind) {
        GLYPH_PREV -> {
            // |◀: vertical bar on left (x in 20%..30%, y in 22%..78%) + left-pointing triangle (x in 34%..80%, y in 20%..80%)
            val inBar = u in 0.20f..0.30f && v in 0.22f..0.78f
            val inTriangle = if (u in 0.34f..0.80f && v in 0.20f..0.80f) {
                val t = (u - 0.34f) / (0.80f - 0.34f)
                abs(v - 0.50f) <= 0.30f * t
            } else {
                false
            }
            inBar || inTriangle
        }
        GLYPH_PLAY -> {
            // ▶: right-pointing triangle (x in 26%..78%, y in 20%..80%)
            if (u in 0.26f..0.78f && v in 0.20f..0.80f) {
                val t = (0.78f - u) / (0.78f - 0.26f)
                abs(v - 0.50f) <= 0.30f * t
            } else {
                false
            }
        }
        GLYPH_PAUSE -> {
            // ⏸: two vertical bars (x in 24%..42% and x in 58%..76%, y in 20%..80%)
            v in 0.20f..0.80f && (u in 0.24f..0.42f || u in 0.58f..0.76f)
        }
        GLYPH_NEXT -> {
            // ▶|: right-pointing triangle (x in 20%..66%, y in 20%..80%) + vertical bar on right (x in 70%..80%, y in 22%..78%)
            val inTriangle = if (u in 0.20f..0.66f && v in 0.20f..0.80f) {
                val t = (0.66f - u) / (0.66f - 0.20f)
                abs(v - 0.50f) <= 0.30f * t
            } else {
                false
            }
            val inBar = u in 0.70f..0.80f && v in 0.22f..0.78f
            inTriangle || inBar
        }
        GLYPH_STOP -> {
            // ■: solid square (x in 24%..76%, y in 24%..76%)
            u in 0.24f..0.76f && v in 0.24f..0.76f
        }
        else -> false
    }

    companion object {
        private const val GLYPH_PREV: Int = 0
        private const val GLYPH_PLAY: Int = 1
        private const val GLYPH_PAUSE: Int = 2
        private const val GLYPH_NEXT: Int = 3
        private const val GLYPH_STOP: Int = 4
    }
}
