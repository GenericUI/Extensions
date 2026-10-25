package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Platform bridge for `:extensions:alien`.
 *
 * - On **System targets** (`:extensions:alien:sharedSystem`), `isWebPassThrough` is `false`:
 *   [resolveChildFactory] returns an [AlienFactory] backed by an [AlienRenderSession], and
 *   [createAlienRootContainerImpl] returns an [com.nexustools.genericui.ext.alien.impl.AlienHostCanvasContainerAlienImpl]
 *   that attaches a double-buffered `Canvas2D` (and any underlay [UnalienContainer] or extension widgets below it).
 * - On **Web targets** (`:extensions:alien:web`), `isWebPassThrough` is `true`:
 *   [resolveChildFactory] and [createAlienRootContainerImpl] forward directly to the parent container's
 *   `WebNativeFactory` so native browser DOM implementations are used with zero overhead.
 */
interface AlienPlatformBridge {
    val isWebPassThrough: Boolean

    fun resolveChildFactory(
        alienRoot: AbstractContainer,
        parentFactory: NativeFactory?,
        sessionProvider: () -> AlienRenderSession?
    ): NativeFactory?

    fun createAlienRootContainerImpl(
        alienRoot: AbstractContainer,
        hostFactory: NativeFactory,
        sessionProvider: () -> AlienRenderSession
    ): ContainerNativeImpl<*>
}
