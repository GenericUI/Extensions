package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import kotlin.reflect.KClass

/**
 * Self-rendering Bootstrap 5.3 [Container] powered by `:extensions:canvas2d` and [AlienRepaintManager]
 * on System targets (`:desktop`, `:mobile`, `:swing`), while acting as a zero-overhead pass-through
 * to the native DOM container on the `:web` target.
 *
 * - Overrides [resolveChildFactory] to provide [AlienFactory] to all child components on System targets
 *   so `:common` children receive `*AlienImpl` implementations that render onto this container's `Canvas2D`.
 * - Non-Alien children (such as [UnalienContainer] or extension components like `Canvas3D`,
 *   `VideoPlayer`, `AudioAnalyzer`, and `WebView`) are attached to the host container **below** the `Canvas2D`,
 *   and the `Canvas2D` cuts a transparent hole with mouse/pointer passthrough or draws higher-Z Alien
 *   components on top of them.
 */
open class AlienContainer(
    classPrefix: String = "container"
) : Container(classPrefix), IAlien {

    private var _alienImplProvider: AlienImplProvider? = null
    protected open val alienImplProvider: AlienImplProvider
        get() = _alienImplProvider ?: AlienImplProvider(this).also { _alienImplProvider = it }

    override val isAlienModeActive: Boolean
        get() = alienImplProvider.isAlienModeActive

    override val renderSession: AlienRenderSession?
        get() = alienImplProvider.renderSession

    override val alienSession: AlienRenderSession?
        get() = alienImplProvider.alienSession

    override var insertApplicationStyleSheets: Int
        get() = alienImplProvider.insertApplicationStyleSheets
        set(value) {
            alienImplProvider.insertApplicationStyleSheets = value
        }

    init {
        htmlTag = "genericui:alien-container"
        AlienPrecompiledBootstrapStyleSheet.ensureRegistered()
    }

    override fun addStyleSheet(styleSheetPath: String) =
        alienImplProvider.addStyleSheet(styleSheetPath)

    override fun addStyleSheet(styleSheetResourceId: ResourceID) =
        alienImplProvider.addStyleSheet(styleSheetResourceId)

    override fun addStyleSheet(styleSheet: PrecompiledStyleSheet) =
        alienImplProvider.addStyleSheet(styleSheet)

    override fun addStyleSheet(styleSheet: CssStyleSheet) =
        alienImplProvider.addStyleSheet(styleSheet)

    override fun removeStyleSheet(styleSheetPath: String): Boolean =
        alienImplProvider.removeStyleSheet(styleSheetPath)

    override fun removeStyleSheet(styleSheetResourceId: ResourceID): Boolean =
        alienImplProvider.removeStyleSheet(styleSheetResourceId)

    override fun removeStyleSheet(styleSheet: PrecompiledStyleSheet): Boolean =
        alienImplProvider.removeStyleSheet(styleSheet)

    override fun removeStyleSheet(styleSheet: CssStyleSheet): Boolean =
        alienImplProvider.removeStyleSheet(styleSheet)

    override fun hasStyleSheet(styleSheetPath: String): Boolean =
        alienImplProvider.hasStyleSheet(styleSheetPath)

    override fun hasStyleSheet(styleSheetResourceId: ResourceID): Boolean =
        alienImplProvider.hasStyleSheet(styleSheetResourceId)

    override fun hasStyleSheet(styleSheet: PrecompiledStyleSheet): Boolean =
        alienImplProvider.hasStyleSheet(styleSheet)

    override fun hasStyleSheet(styleSheet: CssStyleSheet): Boolean =
        alienImplProvider.hasStyleSheet(styleSheet)

    override fun getStyleSheets(): List<PrecompiledStyleSheet> =
        alienImplProvider.getStyleSheets()

    override fun getStyleSheetPaths(): List<String> =
        alienImplProvider.getStyleSheetPaths()

    override fun getOrCreateRenderSession(): AlienRenderSession? =
        alienImplProvider.getOrCreateRenderSession()

    override fun resizeAlienViewport(width: Int, height: Int) =
        alienImplProvider.resizeAlienViewport(width, height)

    override fun repaintAlien(deltaMs: Float) =
        alienImplProvider.repaintAlien(deltaMs)

    override fun requestAlienRepaint() =
        alienImplProvider.requestAlienRepaint()

    override fun <T : NativeImpl<*>> registerAlienComponentImpl(
        componentClass: KClass<*>,
        creator: (AbstractComponent) -> T
    ) = alienImplProvider.registerAlienComponentImpl(componentClass, creator)

    protected override fun onAttachNative(impl: NativeImpl<*>) {
        alienImplProvider.onAttachNative(impl) {
            super.onAttachNative(it)
        }
    }

    override fun applyStyleSheet(styleSheet: CssStyleSheet, viewportWidthPx: Int) {
        alienImplProvider.applyStyleSheet(styleSheet, viewportWidthPx) { sheet, width ->
            super.applyStyleSheet(sheet, width)
        }
    }

    override fun applyPrecompiledStyleSheet(precompiled: PrecompiledStyleSheet, viewportWidthPx: Int) {
        alienImplProvider.applyPrecompiledStyleSheet(precompiled, viewportWidthPx) { sheet, width ->
            super.applyPrecompiledStyleSheet(sheet, width)
        }
    }

    override fun resolveActiveFactory(): NativeFactory? =
        alienImplProvider.resolveActiveFactory { super.resolveActiveFactory() }

    override fun resolveChildFactory(): NativeFactory? =
        alienImplProvider.resolveChildFactory { super.resolveChildFactory() }

    protected override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        alienImplProvider.createNativeImpl(factory) { super.createNativeImpl(it) }
}
