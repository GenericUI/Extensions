package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.shell.IApplication
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.system.BackendTarget

/**
 * Central entry point for `:extensions:overlay`, exposing the global [elementRegistry],
 * default theme accent color ([defaultThemeColorHex]), and overlay preset factory lookup.
 */
object OverlayExtension {

    /**
     * Optional reference to the active [IApplication] used to inherit [IApplication.getThemeColor]
     * when no explicit `themeColorHex` is set on [OverlayHost] or [AbstractOverlay].
     */
    var activeApplication: IApplication? = null

    /**
     * Default primary theme accent color used by overlay seek bars and active badges
     * when no explicit `themeColorHex` is set on [OverlayHost], [AbstractOverlay], or [activeApplication].
     */
    var defaultThemeColorHex: String = "#0d6efd"

    /**
     * Resolves the effective default theme color hex from [activeApplication] or [defaultThemeColorHex].
     */
    fun effectiveDefaultThemeColorHex(application: IApplication? = activeApplication): String =
        application?.getThemeColor()?.takeIf { it.isNotBlank() } ?: defaultThemeColorHex

    /**
     * Global registry of custom and built-in [OverlayElementFactory] instances.
     */
    val elementRegistry: OverlayElementRegistry = OverlayElementRegistry()

    private val presetFactories = linkedMapOf<String, (OverlayHost?) -> AbstractOverlay>()

    init {
        resetBuiltInPresets()
    }

    fun resetBuiltInPresets() {
        registerPresetFactory("mobile") { host ->
            MobileOverlayPreset().also { preset -> host?.attachOverlay(preset) }
        }
        registerPresetFactory("websystem") { host ->
            WebSystemOverlayPreset().also { preset -> host?.attachOverlay(preset) }
        }
        registerPresetFactory("web_system") { host ->
            WebSystemOverlayPreset().also { preset -> host?.attachOverlay(preset) }
        }
        registerPresetFactory("system") { host ->
            WebSystemOverlayPreset().also { preset -> host?.attachOverlay(preset) }
        }
        registerPresetFactory("default") { host ->
            createDefaultPreset(host = host)
        }
    }

    fun createDefaultPreset(
        backend: BackendTarget = GenericUI.activeFactory?.backendTarget ?: BackendTarget.Windows,
        host: OverlayHost? = null
    ): PresetOverlay {
        val preset = OverlayPresetResolver.resolveForBackend(backend)
        host?.attachOverlay(preset)
        return preset
    }

    fun registerPresetFactory(presetId: String, factory: (OverlayHost?) -> AbstractOverlay) {
        val key = presetId.trim().lowercase()
        require(key.isNotEmpty()) { "Preset ID must not be blank." }
        presetFactories[key] = factory
    }

    fun unregisterPresetFactory(presetId: String): Boolean =
        presetFactories.remove(presetId.trim().lowercase()) != null

    fun createPresetOrNull(presetId: String, host: OverlayHost? = null): AbstractOverlay? =
        presetFactories[presetId.trim().lowercase()]?.invoke(host)

    fun registeredPresetIds(): Set<String> =
        presetFactories.keys.toSet()
}
