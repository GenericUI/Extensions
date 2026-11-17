package com.nexustools.genericui.ext.alien.web

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.ext.alien.AlienPlatformBridge
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Web-target (`js` and `wasmJs`) implementation of [AlienPlatformBridge].
 *
 * On `:web`, `AlienContainer`, `AlienFragment`, and `UnalienContainer` do nothing extra and
 * forward directly to the parent container's `WebNativeFactory`, using standard browser DOM
 * `Web*Impl` native implementations with zero `Canvas2D` overhead.
 */
object WebAlienPlatformBridge : AlienPlatformBridge {

    override val isWebPassThrough: Boolean = true

    override fun resolveChildFactory(
        alienRoot: AbstractContainer,
        parentFactory: NativeFactory?,
        sessionProvider: () -> AlienRenderSession?
    ): NativeFactory? = parentFactory

    override fun createAlienRootContainerImpl(
        alienRoot: AbstractContainer,
        hostFactory: NativeFactory,
        sessionProvider: () -> AlienRenderSession
    ): ContainerNativeImpl<*> =
        hostFactory.createContainer(alienRoot.componentClassPrefix, alienRoot.htmlTag)
}
