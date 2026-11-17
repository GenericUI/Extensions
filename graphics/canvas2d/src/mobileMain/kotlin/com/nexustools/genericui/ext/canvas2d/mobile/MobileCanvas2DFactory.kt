package com.nexustools.genericui.ext.canvas2d.mobile

import com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl

internal expect fun createMobileCanvas2DNativeImpl(width: Int, height: Int): Canvas2DNativeImpl<*>
