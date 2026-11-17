package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.shell.IApplication

/**
 * Central entry point for `:extensions:overlay`, exposing the global [elementRegistry],
 * default theme accent color ([defaultThemeColorHex]), and pure [Overlay] factory helper.
 */
object OverlayExtension {

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
     * Creates a pure, background-free [Overlay] and optionally attaches it to [host].
     */
    fun createOverlay(
        tagName: String = "genericui:overlay",
        classPrefix: String = "gui-overlay",
        autoHideSpec: AutoHideSpec = AutoHideSpec(),
        host: OverlayHost? = null
    ): Overlay = Overlay(tagName, classPrefix, autoHideSpec).also { overlay ->
        host?.attachOverlay(overlay)
    }
}
