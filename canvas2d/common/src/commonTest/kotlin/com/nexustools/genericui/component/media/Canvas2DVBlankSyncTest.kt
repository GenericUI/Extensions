package com.nexustools.genericui.component.media

import com.nexustools.genericui.ext.gpu.VBlankWatcher
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.style.RgbaColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Canvas2DVBlankSyncTest {

    @Test
    fun testCanvas2DPresentsAtEndOfCurrentVBlankWhenDirtiedInsideVBlankAndOnNextVBlankWhenOutside() {
        VBlankWatcher.reset()

        val canvas = Canvas2D(width = 200, height = 150, doubleBuffer = true)
        val ctx: ImageBuffer2DContext = canvas.getContext()
        assertTrue(canvas.imageBuffer.doubleBuffer)
        assertEquals(0L, canvas.presentedFrameCount)

        // Case 1: Dirtied OUTSIDE a VBlank callback -> scheduled for next VBlank tick
        assertFalse(VBlankWatcher.isInVBlankCallback)
        ctx.setFillColor(RgbaColor(255, 64, 32, 255))
        ctx.fillRect(10f, 10f, 50f, 50f)

        // Before the next VBlank tick fires, the surface has not yet swapped
        assertEquals(0L, canvas.presentedFrameCount)

        // Step the global VBlank watcher -> surface swaps on this VBlank tick
        VBlankWatcher.stepFrame(16.67)
        assertEquals(1L, canvas.presentedFrameCount)
        assertFalse(canvas.imageBuffer.isDirty)

        // Case 2: Dirtied INSIDE a Canvas2D VBlank render callback -> presents immediately at end of callback
        var inCallbackVerified = false
        var presentedBeforeCallbackReturn = 0L
        canvas.registerVBlankRender { renderCtx, _, _, _ ->
            inCallbackVerified = VBlankWatcher.isInVBlankCallback
            renderCtx.setFillColor(RgbaColor(32, 180, 255, 255))
            renderCtx.fillRect(20f, 20f, 80f, 40f)
            presentedBeforeCallbackReturn = canvas.presentedFrameCount
        }

        canvas.dispatchVBlankFrame(33.34)
        assertTrue(inCallbackVerified)
        // Was 1L during the callback body, and immediately became 2L at the end of that VBlank callback
        assertEquals(1L, presentedBeforeCallbackReturn)
        assertEquals(2L, canvas.presentedFrameCount)
        assertFalse(canvas.imageBuffer.isDirty)
    }
}
