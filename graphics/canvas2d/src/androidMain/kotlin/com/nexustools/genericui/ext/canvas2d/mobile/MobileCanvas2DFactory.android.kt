package com.nexustools.genericui.ext.canvas2d.mobile

import com.nexustools.genericui.android.AndroidCanvas2DImpl
import com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl

internal actual fun createMobileCanvas2DNativeImpl(width: Int, height: Int): Canvas2DNativeImpl<*> =
    AndroidCanvas2DImpl(initialWidth = width, initialHeight = height)
