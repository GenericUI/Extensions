package com.nexustools.genericui.ext.canvas2d.mobile

import com.nexustools.genericui.ios.IosCanvas2DImpl
import com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl

internal actual fun createMobileCanvas2DNativeImpl(width: Int, height: Int): Canvas2DNativeImpl<*> =
    IosCanvas2DImpl(initialWidth = width, initialHeight = height)
