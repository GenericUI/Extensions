package com.nexustools.genericui.ext.gpu

import com.nexustools.genericui.component.media.VBlankSubscription
import com.nexustools.genericui.component.shell.Application

/**
 * Registers a global [VBlankFrameListener] on this [Application] that fires on every VBlank tick.
 */
fun Application.watchVBlank(listener: VBlankFrameListener): VBlankSubscription =
    VBlankWatcher.watch(listener)
