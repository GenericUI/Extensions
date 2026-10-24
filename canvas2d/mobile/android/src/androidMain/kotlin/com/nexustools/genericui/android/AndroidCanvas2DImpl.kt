package com.nexustools.genericui.android

import com.nexustools.genericui.component.media.Canvas2DContext
import com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl

/**
 * Android hardware-accelerated `android.view.View.onDraw(Canvas)` implementation of [Canvas2DNativeImpl].
 */
class AndroidCanvas2DImpl : AndroidAbstractCanvasImpl<Canvas2DContext>("android.view.View"), Canvas2DNativeImpl<AndroidViewHandle> {
    private val context2D = AndroidCanvas2DContextImpl(surfaceW, surfaceH)

    override fun getRenderingContext(): Canvas2DContext = context2D
}
