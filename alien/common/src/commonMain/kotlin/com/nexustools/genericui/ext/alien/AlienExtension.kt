package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.ext.alien.impl.AlienHostCanvasContainerAlienImpl
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Global registry and configuration entry point for `:extensions:alien`.
 */
object AlienExtension {

    private val defaultSystemBridge: AlienPlatformBridge = object : AlienPlatformBridge {
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

    /**
     * Active platform bridge (`defaultSystemBridge` on System targets, `WebAlienPlatformBridge` on `:web`).
     */
    var platformBridge: AlienPlatformBridge = defaultSystemBridge

    private val componentLayerModes = mutableMapOf<AbstractComponent, AlienExtensionLayerMode>()

    /**
     * Configures how an extension component or [UnalienContainer] is layered relative to the
     * [AlienContainer] / [AlienFragment] `Canvas2D` (`CUT_TRANSPARENT_HOLE` by default).
     */
    fun setExtensionLayerMode(component: AbstractComponent, mode: AlienExtensionLayerMode) {
        componentLayerModes[component] = mode
        component.setAttribute("data-alien-layer-mode", mode.name)
    }

    /**
     * Resolves the [AlienExtensionLayerMode] for an underlay component (`CUT_TRANSPARENT_HOLE` by default).
     */
    fun getExtensionLayerMode(component: AbstractComponent?): AlienExtensionLayerMode {
        if (component == null) return AlienExtensionLayerMode.CUT_TRANSPARENT_HOLE
        componentLayerModes[component]?.let { return it }
        return AlienExtensionLayerMode.CUT_TRANSPARENT_HOLE
    }

    /**
     * Restores the default System `Canvas2D` self-rendering bridge.
     */
    fun useSystemCanvasRenderer() {
        platformBridge = defaultSystemBridge
    }
}
