package com.nexustools.genericui.ios

import com.nexustools.genericui.component.media.Canvas2DContext
import com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl

/**
 * iOS CoreGraphics (`CGContext`) + `CADisplayLink` implementation of [Canvas2DNativeImpl].
 */
class IosCanvas2DImpl : IosAbstractCanvasImpl<Canvas2DContext>("UIView", "canvas-2d"), Canvas2DNativeImpl<IosNativeView> {
    private val context = IosCanvas2DContextImpl()

    override fun getRenderingContext(): Canvas2DContext {
        context.width = getSurfaceWidth()
        context.height = getSurfaceHeight()
        return context
    }
}
