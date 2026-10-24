package com.nexustools.genericui.component.media

import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.ext.canvas2d.Canvas2DExtension
import com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl

/**
 * Dedicated 2D vector and pixel drawing canvas extending [AbstractCanvas].
 */
open class Canvas2D(
    width: Int = 800,
    height: Int = 600
) : AbstractCanvas<Canvas2DContext>() {
    init {
        this.widthPx = width
        this.heightPx = height
    }

    override fun createNativeImpl(factory: NativeFactory): Canvas2DNativeImpl<*> =
        Canvas2DExtension.createCanvas2DNative(
            width = (widthPx ?: 800).coerceAtLeast(1),
            height = (heightPx ?: 600).coerceAtLeast(1)
        )
}
