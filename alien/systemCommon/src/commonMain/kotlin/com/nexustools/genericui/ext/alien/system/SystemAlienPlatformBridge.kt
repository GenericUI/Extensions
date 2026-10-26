package com.nexustools.genericui.ext.alien.system

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.ext.alien.AlienFactory
import com.nexustools.genericui.ext.alien.AlienPlatformBridge
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.impl.AlienHostCanvasContainerAlienImpl
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * System-target (`:desktop`, `:mobile`, `:swing`) implementation of [AlienPlatformBridge].
 *
 * Provides [AlienFactory] to children of `AlienContainer` and `AlienFragment` and renders them
 * onto a `Canvas2D` via [AlienRenderSession], [com.nexustools.genericui.ext.alien.AlienRepaintManager],
 * and [com.nexustools.genericui.ext.alien.AlienUpdateRegionTracker].
 */
object SystemAlienPlatformBridge : AlienPlatformBridge {

    override val isWebPassThrough: Boolean = false

    override fun resolveChildFactory(
        alienRoot: AbstractContainer,
        parentFactory: NativeFactory?,
        sessionProvider: () -> AlienRenderSession?
    ): NativeFactory? {
        if (parentFactory is AlienFactory) return parentFactory
        return sessionProvider()?.alienFactory ?: parentFactory?.let { host ->
            AlienFactory(hostFactory = host, session = sessionProvider())
        }
    }

    override fun createAlienRootContainerImpl(
        alienRoot: AbstractContainer,
        hostFactory: NativeFactory,
        sessionProvider: () -> AlienRenderSession
    ): ContainerNativeImpl<*> {
        if (hostFactory is AlienFactory) {
            return hostFactory.createContainer(alienRoot.componentClassPrefix, alienRoot.htmlTag)
        }
        val session = sessionProvider()
        return AlienHostCanvasContainerAlienImpl(
            alienRoot = alienRoot,
            hostFactory = hostFactory,
            session = session
        )
    }
}
