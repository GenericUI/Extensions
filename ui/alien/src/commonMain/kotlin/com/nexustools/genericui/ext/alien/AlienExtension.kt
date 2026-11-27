package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.ext.alien.impl.AlienHostCanvasContainerAlienImpl
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl
import com.nexustools.genericui.resources.ResourceID
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Global registry and configuration entry point for `:extensions:alien`.
 */
@OptIn(ExperimentalAtomicApi::class)
object AlienExtension {

    val BOOTSTRAP_STYLE_SHEET_ID: ResourceID = IAlien.BOOTSTRAP_STYLE_SHEET_ID

    val BOOTSTRAP_RESOURCE_ID: ResourceID = IAlien.BOOTSTRAP_RESOURCE_ID

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
    private val extensionImplProvidersRef =
        AtomicReference<List<AlienExtensionImplProvider>>(emptyList())

    /**
     * Registers an [AlienExtensionImplProvider] used to synthesize self-rendered Alien widget
     * implementations (`Svg`, `Canvas2D`, `Canvas3D`, `VideoPlayer`, etc.) inside Alien sessions.
     * Registration is idempotent while preserving insertion order.
     */
    fun registerAlienExtensionImpl(provider: AlienExtensionImplProvider) {
        while (true) {
            val current = extensionImplProvidersRef.load()
            if (current.any { it === provider }) return
            val updated = ArrayList<AlienExtensionImplProvider>(current.size + 1).apply {
                addAll(current)
                add(provider)
            }
            if (extensionImplProvidersRef.compareAndSet(current, updated)) return
        }
    }

    /**
     * Unregisters a previously registered [AlienExtensionImplProvider].
     */
    fun unregisterAlienExtensionImpl(provider: AlienExtensionImplProvider) {
        while (true) {
            val current = extensionImplProvidersRef.load()
            if (current.none { it === provider }) return
            val updated = current.filter { it !== provider }
            if (extensionImplProvidersRef.compareAndSet(current, updated)) return
        }
    }

    /**
     * Resolves the first non-null self-rendered Alien [NativeImpl] from registered
     * [AlienExtensionImplProvider]s for [component], or `null` to fall back to underlay/native handling.
     */
    fun createExtensionNativeImpl(
        component: AbstractComponent,
        session: AlienRenderSession?,
        hostFactory: NativeFactory
    ): NativeImpl<*>? {
        val providers = extensionImplProvidersRef.load()
        for (i in 0 until providers.size) {
            val created = providers[i].createImpl(component, session, hostFactory)
            if (created != null) return created
        }
        return null
    }

    /**
     * Clears all registered [AlienExtensionImplProvider] entries (primarily for isolated unit tests).
     */
    fun clearAlienExtensionImplsForTests() {
        extensionImplProvidersRef.store(emptyList())
    }

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
