package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.PrecompiledStyleSheet

/**
 * Self-rendering Bootstrap 5.3 [Fragment] powered by `:extensions:canvas2d` and [AlienRepaintManager]
 * on System targets (`:desktop`, `:mobile`, `:swing`), while acting as a zero-overhead pass-through
 * to the native DOM fragment on the `:web` target.
 *
 * - Overrides [resolveChildFactory] to provide [AlienFactory] to all child components on System targets
 *   so `:common` children receive `*AlienImpl` implementations that render onto this fragment's `Canvas2D`.
 * - Non-Alien children (such as [UnalienContainer] or extension components like `GLCanvas`, `VKCanvas`,
 *   `VideoPlayer`, `AudioAnalyzer`, and `WebView`) are attached to the host container **below** the `Canvas2D`,
 *   and the `Canvas2D` cuts a transparent hole with mouse/pointer passthrough or draws higher-Z Alien
 *   components on top of them.
 */
abstract class AlienFragment : Fragment(), IAlien {

    var renderSession: AlienRenderSession? = null
        private set

    private val activeStyleSheets: MutableList<PrecompiledStyleSheet> =
        mutableListOf(AlienPrecompiledBootstrapStyleSheet)

    override var insertApplicationStyleSheets: Int = 1
        set(value) {
            if (field != value) {
                field = value
                renderSession?.let {
                    syncRootNodeState(it)
                    it.requestLayoutAndRepaint()
                }
            }
        }

    init {
        htmlTag = "genericui:alien-fragment"
        AlienPrecompiledBootstrapStyleSheet.ensureRegistered()
    }

    override fun addStyleSheet(styleSheetPath: String) {
        val resolved = AlienPrecompiledBootstrapStyleSheet.resolveSheetByPath(styleSheetPath) ?: return
        addStyleSheet(resolved)
    }

    override fun addStyleSheet(styleSheetResourceId: ResourceID) {
        val resolved = AlienPrecompiledBootstrapStyleSheet.resolveSheetByResourceId(styleSheetResourceId) ?: return
        addStyleSheet(resolved)
    }

    override fun addStyleSheet(styleSheet: PrecompiledStyleSheet) {
        if (activeStyleSheets.none { it === styleSheet || it.name == styleSheet.name }) {
            if (styleSheet === AlienPrecompiledBootstrapStyleSheet ||
                styleSheet.name.equals(AlienPrecompiledBootstrapStyleSheet.name, ignoreCase = true)
            ) {
                activeStyleSheets.add(0, styleSheet)
            } else {
                activeStyleSheets.add(styleSheet)
            }
            renderSession?.let {
                syncRootNodeState(it)
                it.requestLayoutAndRepaint()
            }
        }
    }

    override fun addStyleSheet(styleSheet: CssStyleSheet) {
        if (activeStyleSheets.none { it.styleSheet === styleSheet }) {
            activeStyleSheets.add(AlienPrecompiledBootstrapStyleSheet.wrapCssStyleSheet(styleSheet))
            renderSession?.let {
                syncRootNodeState(it)
                it.requestLayoutAndRepaint()
            }
        }
    }

    override fun removeStyleSheet(styleSheetPath: String): Boolean {
        val removed = activeStyleSheets.removeAll {
            AlienPrecompiledBootstrapStyleSheet.matchesPath(it, styleSheetPath)
        }
        if (removed) {
            renderSession?.let {
                syncRootNodeState(it)
                it.requestLayoutAndRepaint()
            }
        }
        return removed
    }

    override fun removeStyleSheet(styleSheetResourceId: ResourceID): Boolean {
        val removed = activeStyleSheets.removeAll {
            AlienPrecompiledBootstrapStyleSheet.matchesResourceId(it, styleSheetResourceId)
        }
        if (removed) {
            renderSession?.let {
                syncRootNodeState(it)
                it.requestLayoutAndRepaint()
            }
        }
        return removed
    }

    override fun removeStyleSheet(styleSheet: PrecompiledStyleSheet): Boolean {
        val removed = activeStyleSheets.removeAll { it === styleSheet || it.name == styleSheet.name }
        if (removed) {
            renderSession?.let {
                syncRootNodeState(it)
                it.requestLayoutAndRepaint()
            }
        }
        return removed
    }

    override fun removeStyleSheet(styleSheet: CssStyleSheet): Boolean {
        val removed = activeStyleSheets.removeAll { it.styleSheet === styleSheet }
        if (removed) {
            renderSession?.let {
                syncRootNodeState(it)
                it.requestLayoutAndRepaint()
            }
        }
        return removed
    }

    override fun hasStyleSheet(styleSheetPath: String): Boolean =
        activeStyleSheets.any { AlienPrecompiledBootstrapStyleSheet.matchesPath(it, styleSheetPath) }

    override fun hasStyleSheet(styleSheetResourceId: ResourceID): Boolean =
        activeStyleSheets.any { AlienPrecompiledBootstrapStyleSheet.matchesResourceId(it, styleSheetResourceId) }

    override fun hasStyleSheet(styleSheet: PrecompiledStyleSheet): Boolean =
        activeStyleSheets.any { it === styleSheet || it.name == styleSheet.name }

    override fun getStyleSheets(): List<PrecompiledStyleSheet> = activeStyleSheets.toList()

    override fun getStyleSheetPaths(): List<String> = activeStyleSheets.map { it.cssResourcePath }

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
        session.insertApplicationStyleSheets = insertApplicationStyleSheets
        session.styleSheets.clear()
        session.styleSheets.addAll(activeStyleSheets)
        session.rootNode.tagName = htmlTag
        val snap = getBootstrap().toStyleSnapshot()
        session.rootNode.styleSnapshot = snap.copy(
            componentPrefix = "alien-root",
            customClasses = LinkedHashSet(getClasses())
        )
        if (!getStyle().isEmpty()) {
            session.rootNode.inlineComponentStyle = getStyle()
        }
        if (insertApplicationStyleSheets < 0) {
            session.rootNode.computedStyle = null
        } else {
            computedStyle?.let { session.rootNode.computedStyle = it }
        }
    }

    override fun onAttachNative(impl: NativeImpl<*>) {
        super.onAttachNative(impl)
        renderSession?.let {
            syncRootNodeState(it)
            it.requestLayoutAndRepaint()
        }
    }

    override fun applyStyleSheet(styleSheet: CssStyleSheet, viewportWidthPx: Int) {
        if (insertApplicationStyleSheets >= 0 || activeStyleSheets.any { it.styleSheet === styleSheet }) {
            super.applyStyleSheet(styleSheet, viewportWidthPx)
        }
        renderSession?.let {
            syncRootNodeState(it)
            it.requestLayoutAndRepaint()
        }
    }

    override fun applyPrecompiledStyleSheet(precompiled: PrecompiledStyleSheet, viewportWidthPx: Int) {
        if (insertApplicationStyleSheets >= 0 || hasStyleSheet(precompiled)) {
            super.applyPrecompiledStyleSheet(precompiled, viewportWidthPx)
        }
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
     * Resizes the Alien viewport and underlying `Canvas2D` surface on System targets.
     */
    fun resizeAlienViewport(width: Int, height: Int) {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        widthPx = safeW
        heightPx = safeH
        setPreferredSize(com.nexustools.genericui.geometry.Size(safeW, safeH))
        if (nativeImpl == null && !AlienExtension.platformBridge.isWebPassThrough && resolveActiveFactory() != null) {
            runCatching { ensureNativeImpl() }
        }
        getOrCreateRenderSession()?.resizeViewport(safeW, safeH)
    }

    /**
     * Triggers an immediate animation/layout/dirty-region repaint pass on System targets.
     */
    override fun repaintAlien(deltaMs: Float) {
        if (nativeImpl == null && !AlienExtension.platformBridge.isWebPassThrough && resolveActiveFactory() != null) {
            runCatching { ensureNativeImpl() }
        }
        getOrCreateRenderSession()?.repaintIfDirty(deltaMs)
    }
}
