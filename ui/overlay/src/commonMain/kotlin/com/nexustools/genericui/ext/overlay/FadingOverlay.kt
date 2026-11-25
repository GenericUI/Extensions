package com.nexustools.genericui.ext.overlay

/**
 * Atomic auto-fading overlay container whose entire visual tree—including any embedded
 * [OverlayWindowTitleBar] and all HUD bars—shares one unified `.overlay-fading-root` root.
 *
 * Visibility transitions toggle the `"hidden"` CSS class via [autoHideController], driving
 * `@keyframes overlayFadeIn` / `overlayFadeOut` from `:extensions:ui:overlay/styles/style.scss`
 * cleanly without imperative alpha timers or double-fade occlusion bugs. Persistent items
 * such as captions and buffering indicators mount outside in `OverlayHostBox.persistentOverlayRoot`.
 */
open class FadingOverlay(
    tagName: String = "genericui:overlay",
    classPrefix: String = "gui-overlay overlay-fading-root",
    autoHideSpec: AutoHideSpec = AutoHideSpec()
) : AbstractOverlay(
    tagName = tagName,
    classPrefix = classPrefix,
    autoHideSpec = autoHideSpec
) {

    constructor(
        overlayId: String,
        autoHideSpec: AutoHideSpec
    ) : this(
        tagName = overlayId,
        classPrefix = "gui-overlay overlay-fading-root",
        autoHideSpec = autoHideSpec
    )

    init {
        addClass("overlay-fading-root")
        autoHideController.targetComponent = this
    }
}
