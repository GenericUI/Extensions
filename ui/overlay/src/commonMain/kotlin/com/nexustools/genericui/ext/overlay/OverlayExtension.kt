package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.shell.IApplication
import com.nexustools.genericui.core.ApplicationLifecycleExtensionHook
import com.nexustools.genericui.core.GenericUiAppDescriptor
import com.nexustools.genericui.core.GenericUiExtensionRegistry
import com.nexustools.genericui.ext.overlay.res.GeneratedResourceRegistry
import com.nexustools.genericui.ext.overlay.res.R
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.sass.SassCompiler
import com.nexustools.genericui.style.theme.ThemeStyleSheetRegistry

/**
 * Central entry point for `:extensions:ui:overlay`, exposing the global [elementRegistry],
 * default theme accent color ([defaultThemeColorHex]), theme-independent stylesheet
 * registration (`style.scss`), and pure [Overlay] factory helper.
 */
object OverlayExtension {

    const val DEFAULT_STYLE_MODULE_ID: String = "ext:overlay:style"

    private var cachedDefaultStyleSheet: CssStyleSheet? = null
    private var lifecycleHookInstalled: Boolean = false

    /**
     * Optional reference to the active [IApplication] used to inherit [IApplication.getThemeColor]
     * when no explicit `themeColorHex` is set on [OverlayHost] or [AbstractOverlay].
     */
    var activeApplication: IApplication? = null

    /**
     * Default primary theme accent color used by overlay components when no explicit
     * `themeColorHex` is set on [OverlayHost], [AbstractOverlay], or [activeApplication].
     */
    var defaultThemeColorHex: String = "#0d6efd"

    /**
     * Resolves the effective default theme color hex from [activeApplication] or [defaultThemeColorHex].
     */
    fun effectiveDefaultThemeColorHex(application: IApplication? = activeApplication): String =
        application?.getThemeColor()?.takeIf { it.isNotBlank() } ?: defaultThemeColorHex

    /**
     * Global registry of custom [OverlayElementFactory] instances.
     */
    val elementRegistry: OverlayElementRegistry = OverlayElementRegistry()

    /**
     * Lazily compiles and caches `:extensions:ui:overlay`'s generic, theme-independent `style.scss`
     * stylesheet (`R.style.style`). Never compiles `theme.scss`.
     */
    fun defaultStyleSheet(): CssStyleSheet {
        cachedDefaultStyleSheet?.let { return it }
        GeneratedResourceRegistry.ensureInitialized()
        val rawScss = Resources.getRawText(R.style.style).orEmpty()
        return SassCompiler.compileToStyleSheet(rawScss).also { sheet ->
            cachedDefaultStyleSheet = sheet
        }
    }

    /**
     * Registers [defaultStyleSheet] with [ThemeStyleSheetRegistry] (`"ext:overlay:style"`) and
     * installs an application lifecycle hook so consumer apps receive overlay base styling and
     * fade `@keyframes` automatically even without `:gradle-plugin` Sass task wiring.
     */
    fun ensureStyleSheetRegistered(): CssStyleSheet {
        val sheet = defaultStyleSheet()
        if (ThemeStyleSheetRegistry.getCompiledThemeEntries().none { it.first == DEFAULT_STYLE_MODULE_ID }) {
            ThemeStyleSheetRegistry.registerCompiledThemeStyleSheet(DEFAULT_STYLE_MODULE_ID, sheet)
        }
        if (!lifecycleHookInstalled) {
            lifecycleHookInstalled = true
            GenericUiExtensionRegistry.registerLifecycleHook(
                object : ApplicationLifecycleExtensionHook {
                    override fun onAppPrepared(app: IApplication, descriptor: GenericUiAppDescriptor) {
                        if (ThemeStyleSheetRegistry.getCompiledThemeEntries().none { it.first == DEFAULT_STYLE_MODULE_ID }) {
                            ThemeStyleSheetRegistry.registerCompiledThemeStyleSheet(DEFAULT_STYLE_MODULE_ID, defaultStyleSheet())
                        }
                    }
                }
            )
        }
        return sheet
    }

    /**
     * Creates a pure, background-free [Overlay] and optionally attaches it to [host].
     */
    fun createOverlay(
        tagName: String = "genericui:overlay",
        classPrefix: String = "gui-overlay overlay-fading-root",
        autoHideSpec: AutoHideSpec = AutoHideSpec(),
        host: OverlayHost? = null
    ): Overlay = Overlay(tagName, classPrefix, autoHideSpec).also { overlay ->
        host?.attachOverlay(overlay)
    }
}
