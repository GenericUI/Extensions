package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl

/**
 * Extension factory hook for creating self-rendered Alien [NativeImpl] instances
 * (such as SVG, Canvas2D, Canvas3D, or VideoPlayer) inside an [AlienRenderSession].
 */
fun interface AlienExtensionImplProvider {
    fun createImpl(
        component: AbstractComponent,
        session: AlienRenderSession?,
        hostFactory: NativeFactory
    ): NativeImpl<*>?
}
