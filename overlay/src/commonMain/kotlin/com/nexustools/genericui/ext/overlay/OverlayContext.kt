package com.nexustools.genericui.ext.overlay

/**
 * Context passed to [OverlayElementFactory.create] when instantiating an [OverlayElement].
 */
data class OverlayContext(
    val host: OverlayHost,
    val state: OverlayState = host.state,
    val actions: OverlayActions = host.actions,
    val themeColorHex: String? = null
) {
    /**
     * Resolves the effective primary theme color hex string (defaulting to [OverlayExtension.defaultThemeColorHex]).
     */
    val effectiveThemeColorHex: String
        get() = themeColorHex?.takeIf { it.isNotBlank() } ?: OverlayExtension.effectiveDefaultThemeColorHex()
}
