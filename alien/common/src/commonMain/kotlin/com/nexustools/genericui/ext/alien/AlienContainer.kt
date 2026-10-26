package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import com.nexustools.genericui.style.mapper.BootstrapClassMapper

/**
 * Self-rendering Bootstrap 5.3 [Container] powered by `:extensions:canvas2d` and [AlienRepaintManager]
 * on System targets (`:desktop`, `:mobile`, `:swing`), while acting as a zero-overhead pass-through
 * to the native DOM container on the `:web` target.
 *
 * - Overrides [resolveChildFactory] to provide [AlienFactory] to all child components on System targets
 *   so `:common` children receive `*AlienImpl` implementations that render onto this container's `Canvas2D`.
 * - Non-Alien children (such as [UnalienContainer] or extension components like `GLCanvas`, `VKCanvas`,
 *   `VideoPlayer`, `AudioAnalyzer`, and `WebView`) are attached to the host container **below** the `Canvas2D`,
 *   and the `Canvas2D` cuts a transparent hole with mouse/pointer passthrough or draws higher-Z Alien
 *   components on top of them.
 */
open class AlienContainer(
    classPrefix: String = "container"
) : Container(classPrefix) {

    var renderSession: AlienRenderSession? = null
        private set

    init {
        htmlTag = "genericui:alien-container"
    }

    fun getOrCreateRenderSession(): AlienRenderSession? {
        if (AlienExtension.platformBridge.isWebPassThrough) return null
        val parentFactory = resolveActiveFactory()
        if (parentFactory is AlienFactory && parentFactory.session != null) {
            return parentFactory.session
        }
        val existing = renderSession
        if (existing != null) {
            syncRootNodeState(existing)
            return existing
        }
        val hostFactory = AlienFactory.unwrapHostFactory(parentFactory ?: GenericUI.activeFactory) ?: return null
        val w = (widthPx ?: 800).coerceAtLeast(1)
        val h = (heightPx ?: 600).coerceAtLeast(1)
        return AlienRenderSession(
            hostFactory = hostFactory,
            initialWidth = w,
            initialHeight = h
        ).also {
            syncRootNodeState(it)
            renderSession = it
        }
    }

    private fun syncRootNodeState(session: AlienRenderSession) {
        session.rootNode.tagName = htmlTag
        session.rootNode.styleSnapshot = BootstrapClassMapper.fromClassString(
            getClass(),
            "alien-root"
        )
        computedStyle?.let { session.rootNode.computedStyle = it }
    }

    override fun onAttachNative(impl: NativeImpl<*>) {
        super.onAttachNative(impl)
        renderSession?.let {
            syncRootNodeState(it)
            it.requestLayoutAndRepaint()
        }
    }

    override fun applyStyleSheet(styleSheet: CssStyleSheet, viewportWidthPx: Int) {
        super.applyStyleSheet(styleSheet, viewportWidthPx)
        renderSession?.let {
            syncRootNodeState(it)
            it.requestLayoutAndRepaint()
        }
    }

    override fun applyPrecompiledStyleSheet(precompiled: PrecompiledStyleSheet, viewportWidthPx: Int) {
        super.applyPrecompiledStyleSheet(precompiled, viewportWidthPx)
        renderSession?.let {
            syncRootNodeState(it)
            it.requestLayoutAndRepaint()
        }
    }

    override fun resolveActiveFactory(): NativeFactory? {
        if (!isVisible) return null
        return super.resolveActiveFactory() ?: GenericUI.activeFactory
    }

    override fun resolveChildFactory(): NativeFactory? {
        val parentFactory = resolveActiveFactory()
        return AlienExtension.platformBridge.resolveChildFactory(
            alienRoot = this,
            parentFactory = parentFactory,
            sessionProvider = { getOrCreateRenderSession() }
        )
    }

    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> {
        if (AlienExtension.platformBridge.isWebPassThrough) {
            return super.createNativeImpl(factory)
        }
        return AlienExtension.platformBridge.createAlienRootContainerImpl(
            alienRoot = this,
            hostFactory = factory,
            sessionProvider = {
                getOrCreateRenderSession() ?: AlienRenderSession(
                    hostFactory = AlienFactory.unwrapHostFactory(factory) ?: factory,
                    initialWidth = (widthPx ?: 800).coerceAtLeast(1),
                    initialHeight = (heightPx ?: 600).coerceAtLeast(1)
                ).also { renderSession = it }
            }
        )
    }

    /**
     * Triggers an immediate animation/layout/dirty-region repaint pass on System targets.
     */
    fun repaintAlien(deltaMs: Float = 16.67f) {
        getOrCreateRenderSession()?.repaintIfDirty(deltaMs)
    }
}
