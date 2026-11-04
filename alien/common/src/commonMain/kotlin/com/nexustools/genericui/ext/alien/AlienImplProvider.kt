package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import kotlin.reflect.KClass

/**
 * Shared implementation provider for Alien root containers and fragments
 * ([AlienContainer], [AlienFragment], [AlienScrollFragment], [AlienFragmentHostFragment]).
 *
 * Centralizes Alien render-session lifecycle, stylesheet cascade management, custom component
 * native implementation creators, and factory delegation without implementing [IAlien] itself.
 */
class AlienImplProvider(
    val alienRoot: AbstractContainer
) {
    var renderSession: AlienRenderSession? = null
        private set

    val alienSession: AlienRenderSession?
        get() = renderSession

    val isAlienModeActive: Boolean
        get() = !AlienExtension.platformBridge.isWebPassThrough

    private val customAlienImplCreators = mutableMapOf<KClass<*>, (AbstractComponent) -> NativeImpl<*>>()

    val customImplCreators: Map<KClass<*>, (AbstractComponent) -> NativeImpl<*>>
        get() = customAlienImplCreators

    private val activeStyleSheets: MutableList<PrecompiledStyleSheet> =
        mutableListOf(AlienPrecompiledBootstrapStyleSheet)

    private var isCreatingNativeImpl: Boolean = false

    var insertApplicationStyleSheets: Int = 1
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
        AlienPrecompiledBootstrapStyleSheet.ensureRegistered()
    }

    fun addStyleSheet(styleSheetPath: String) {
        val resolved = AlienPrecompiledBootstrapStyleSheet.resolveSheetByPath(styleSheetPath) ?: return
        addStyleSheet(resolved)
    }

    fun addStyleSheet(styleSheetResourceId: ResourceID) {
        val resolved = AlienPrecompiledBootstrapStyleSheet.resolveSheetByResourceId(styleSheetResourceId) ?: return
        addStyleSheet(resolved)
    }

    fun addStyleSheet(styleSheet: PrecompiledStyleSheet) {
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

    fun addStyleSheet(styleSheet: CssStyleSheet) {
        if (activeStyleSheets.none { it.styleSheet === styleSheet }) {
            activeStyleSheets.add(AlienPrecompiledBootstrapStyleSheet.wrapCssStyleSheet(styleSheet))
            renderSession?.let {
                syncRootNodeState(it)
                it.requestLayoutAndRepaint()
            }
        }
    }

    fun removeStyleSheet(styleSheetPath: String): Boolean {
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

    fun removeStyleSheet(styleSheetResourceId: ResourceID): Boolean {
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

    fun removeStyleSheet(styleSheet: PrecompiledStyleSheet): Boolean {
        val removed = activeStyleSheets.removeAll { it === styleSheet || it.name == styleSheet.name }
        if (removed) {
            renderSession?.let {
                syncRootNodeState(it)
                it.requestLayoutAndRepaint()
            }
        }
        return removed
    }

    fun removeStyleSheet(styleSheet: CssStyleSheet): Boolean {
        val removed = activeStyleSheets.removeAll { it.styleSheet === styleSheet }
        if (removed) {
            renderSession?.let {
                syncRootNodeState(it)
                it.requestLayoutAndRepaint()
            }
        }
        return removed
    }

    fun hasStyleSheet(styleSheetPath: String): Boolean =
        activeStyleSheets.any { AlienPrecompiledBootstrapStyleSheet.matchesPath(it, styleSheetPath) }

    fun hasStyleSheet(styleSheetResourceId: ResourceID): Boolean =
        activeStyleSheets.any { AlienPrecompiledBootstrapStyleSheet.matchesResourceId(it, styleSheetResourceId) }

    fun hasStyleSheet(styleSheet: PrecompiledStyleSheet): Boolean =
        activeStyleSheets.any { it === styleSheet || it.name == styleSheet.name }

    fun hasStyleSheet(styleSheet: CssStyleSheet): Boolean =
        activeStyleSheets.any { it.styleSheet === styleSheet }

    fun getStyleSheets(): List<PrecompiledStyleSheet> = activeStyleSheets.toList()

    fun getStyleSheetPaths(): List<String> = activeStyleSheets.map { it.cssResourcePath }

    val isBootstrapStyleSheetEnabled: Boolean
        get() = hasStyleSheet(IAlien.BOOTSTRAP_STYLE_SHEET_ID)

    fun <T : NativeImpl<*>> registerAlienComponentImpl(
        componentClass: KClass<*>,
        creator: (AbstractComponent) -> T
    ) {
        customAlienImplCreators[componentClass] = creator
    }

    fun hasCustomAlienComponentImpl(componentClass: KClass<*>): Boolean =
        customAlienImplCreators.containsKey(componentClass)

    @Suppress("UNCHECKED_CAST")
    fun <T : NativeImpl<*>> createCustomAlienComponentImpl(component: AbstractComponent): T? =
        customAlienImplCreators[component::class]?.invoke(component) as? T

    fun getOrCreateRenderSession(): AlienRenderSession? {
        if (AlienExtension.platformBridge.isWebPassThrough) return null
        val parentFactory = alienRoot.resolveActiveFactory()
        if (parentFactory is AlienFactory && parentFactory.session != null) {
            return parentFactory.session
        }
        val existing = renderSession
        if (existing != null) {
            syncRootNodeState(existing)
            return existing
        }
        val hostFactory = AlienFactory.unwrapHostFactory(parentFactory ?: GenericUI.activeFactory) ?: return null
        val w = (alienRoot.widthPx ?: 800).coerceAtLeast(1)
        val h = (alienRoot.heightPx ?: 600).coerceAtLeast(1)
        return AlienRenderSession(
            hostFactory = hostFactory,
            initialWidth = w,
            initialHeight = h
        ).also {
            syncRootNodeState(it)
            renderSession = it
        }
    }

    fun syncRootNodeState(session: AlienRenderSession) {
        session.insertApplicationStyleSheets = insertApplicationStyleSheets
        session.styleSheets.clear()
        session.styleSheets.addAll(activeStyleSheets)
        session.rootNode.tagName = alienRoot.htmlTag
        val snap = alienRoot.getBootstrap().toStyleSnapshot()
        session.rootNode.styleSnapshot = snap.copy(
            componentPrefix = "alien-root",
            customClasses = LinkedHashSet(alienRoot.getClasses())
        )
        if (!alienRoot.getStyle().isEmpty()) {
            session.rootNode.inlineComponentStyle = alienRoot.getStyle()
        }
        if (insertApplicationStyleSheets < 0) {
            session.rootNode.computedStyle = null
        } else {
            alienRoot.computedStyle?.let { session.rootNode.computedStyle = it }
        }
    }

    fun resolveActiveFactory(superResolveActiveFactory: () -> NativeFactory?): NativeFactory? {
        if (!alienRoot.isVisible) return null
        return superResolveActiveFactory() ?: GenericUI.activeFactory
    }

    fun resolveChildFactory(
        superResolveChildFactory: () -> NativeFactory? = { alienRoot.resolveActiveFactory() }
    ): NativeFactory? {
        val parentFactory = alienRoot.resolveActiveFactory() ?: superResolveChildFactory()
        return AlienExtension.platformBridge.resolveChildFactory(
            alienRoot = alienRoot,
            parentFactory = parentFactory,
            sessionProvider = { getOrCreateRenderSession() }
        )
    }

    fun createNativeImpl(
        factory: NativeFactory,
        superCreateNativeImpl: (NativeFactory) -> ContainerNativeImpl<*>
    ): ContainerNativeImpl<*> {
        if (AlienExtension.platformBridge.isWebPassThrough) {
            return superCreateNativeImpl(factory)
        }
        val prevCreating = isCreatingNativeImpl
        isCreatingNativeImpl = true
        return try {
            AlienExtension.platformBridge.createAlienRootContainerImpl(
                alienRoot = alienRoot,
                hostFactory = factory,
                sessionProvider = {
                    getOrCreateRenderSession() ?: AlienRenderSession(
                        hostFactory = AlienFactory.unwrapHostFactory(factory) ?: factory,
                        initialWidth = (alienRoot.widthPx ?: 800).coerceAtLeast(1),
                        initialHeight = (alienRoot.heightPx ?: 600).coerceAtLeast(1)
                    ).also {
                        syncRootNodeState(it)
                        renderSession = it
                    }
                }
            )
        } finally {
            isCreatingNativeImpl = prevCreating
        }
    }

    fun onAttachNative(impl: NativeImpl<*>, superOnAttachNative: (NativeImpl<*>) -> Unit) {
        superOnAttachNative(impl)
        renderSession?.let {
            syncRootNodeState(it)
            it.requestLayoutAndRepaint()
        }
    }

    fun onAttachNative(superOnAttachNative: () -> Unit) {
        superOnAttachNative()
        renderSession?.let {
            syncRootNodeState(it)
            it.requestLayoutAndRepaint()
        }
    }

    fun applyStyleSheet(
        styleSheet: CssStyleSheet,
        viewportWidthPx: Int = 1280,
        superApply: (CssStyleSheet, Int) -> Unit = { _, _ -> }
    ) {
        if (insertApplicationStyleSheets >= 0 || activeStyleSheets.any { it.styleSheet === styleSheet }) {
            superApply(styleSheet, viewportWidthPx)
        }
        renderSession?.let {
            syncRootNodeState(it)
            it.requestLayoutAndRepaint()
        }
    }

    fun applyStyleSheet(
        cssContent: String,
        sourcePath: String? = null,
        superApply: (String, String?) -> Unit = { _, _ -> }
    ) {
        superApply(cssContent, sourcePath)
        val parsed = CssStyleSheet.parse(cssContent)
        addStyleSheet(parsed)
    }

    fun applyStyleSheet(
        resourceId: ResourceID,
        superApply: (ResourceID) -> Unit = {}
    ) {
        superApply(resourceId)
        addStyleSheet(resourceId)
    }

    fun applyPrecompiledStyleSheet(
        precompiled: PrecompiledStyleSheet,
        viewportWidthPx: Int = 1280,
        superApply: (PrecompiledStyleSheet, Int) -> Unit
    ) {
        if (insertApplicationStyleSheets >= 0 || hasStyleSheet(precompiled)) {
            superApply(precompiled, viewportWidthPx)
        }
        renderSession?.let {
            syncRootNodeState(it)
            it.requestLayoutAndRepaint()
        }
    }

    fun applyPrecompiledStyleSheet(
        sheet: PrecompiledStyleSheet,
        superApply: (PrecompiledStyleSheet) -> Unit
    ) {
        if (insertApplicationStyleSheets >= 0 || hasStyleSheet(sheet)) {
            superApply(sheet)
        }
        renderSession?.let {
            syncRootNodeState(it)
            it.requestLayoutAndRepaint()
        }
    }

    fun resizeAlienViewport(width: Int, height: Int) {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        if (alienRoot.widthPx != safeW) {
            alienRoot.widthPx = safeW
        }
        if (alienRoot.heightPx != safeH) {
            alienRoot.heightPx = safeH
        }
        alienRoot.setPreferredSize(Size(safeW, safeH))
        if (!isCreatingNativeImpl &&
            alienRoot.nativeImpl == null &&
            !AlienExtension.platformBridge.isWebPassThrough &&
            alienRoot.resolveActiveFactory() != null
        ) {
            runCatching { alienRoot.ensureNativeImpl() }
        }
        if (isCreatingNativeImpl) {
            renderSession?.resizeViewport(safeW, safeH)
        } else {
            getOrCreateRenderSession()?.resizeViewport(safeW, safeH)
        }
    }

    fun repaintAlien(deltaMs: Float = 16.67f) {
        if (!isCreatingNativeImpl &&
            alienRoot.nativeImpl == null &&
            !AlienExtension.platformBridge.isWebPassThrough &&
            alienRoot.resolveActiveFactory() != null
        ) {
            runCatching { alienRoot.ensureNativeImpl() }
        }
        if (isCreatingNativeImpl) {
            renderSession?.requestLayoutAndRepaint()
        } else {
            getOrCreateRenderSession()?.repaintIfDirty(deltaMs)
        }
    }

    fun requestAlienRepaint() {
        renderSession?.requestLayoutAndRepaint()
        if (!isCreatingNativeImpl) {
            repaintAlien()
        }
    }
}
