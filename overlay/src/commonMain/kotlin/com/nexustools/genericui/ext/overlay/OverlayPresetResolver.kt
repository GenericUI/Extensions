package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.system.BackendTarget

/**
 * Resolves the appropriate default [PresetOverlay] subclass for a given [BackendTarget].
 *
 * - Returns [MobileOverlayPreset] for mobile touch targets (`BackendTarget.Android`, `BackendTarget.IOS`).
 * - Returns [WebSystemOverlayPreset] for `BackendTarget.Web` and all desktop/system backends
 *   (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`).
 */
object OverlayPresetResolver {

    fun resolveForBackend(
        backend: BackendTarget = GenericUI.activeFactory?.backendTarget ?: BackendTarget.Windows
    ): PresetOverlay =
        if (backend.isMobile) {
            MobileOverlayPreset()
        } else {
            WebSystemOverlayPreset()
        }
}
