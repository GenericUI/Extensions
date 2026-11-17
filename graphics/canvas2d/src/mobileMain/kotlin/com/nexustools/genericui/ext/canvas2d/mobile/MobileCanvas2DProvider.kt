package com.nexustools.genericui.ext.canvas2d.mobile

import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.ext.canvas2d.Canvas2DExtension
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.ext.imageprocessing.context2d.SystemGpuImageBuffer

/**
 * Registers mobile (Android, iOS, and iPadOS) hardware-accelerated 2D rendering contexts
 * ([ImageBuffer2DContext] backed by [SystemGpuImageBuffer]) and platform [Canvas2DNativeImpl]
 * implementations into [Canvas2DExtension].
 *
 * [BackendTarget.IPadOS] is supported identically to [BackendTarget.IOS].
 */
object MobileCanvas2DProvider {
    fun supportsTarget(target: BackendTarget): Boolean =
        target == BackendTarget.Android || target.isIosFamily

    fun install() {
        SystemGpuImageBuffer.installFactory(preferVulkan = true)
        Canvas2DExtension.registerContextFactory { width, height ->
            createMobileCanvas2DContext(width, height)
        }
        Canvas2DExtension.registerCanvas2DFactory { width, height ->
            createMobileCanvas2DNativeImpl(width, height)
        }
    }

    fun createMobileCanvas2DContext(width: Int, height: Int): ImageBuffer2DContext =
        SystemGpuImageBuffer(
            width = width,
            height = height,
            doubleBuffer = true,
            initialAntialias = true,
            preferVulkan = true
        ).getContext()
}
