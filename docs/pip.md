# `:extensions:ui:pip` — Cross-Platform Picture-in-Picture (`@PiPCompatible`, `FragmentPipHelper`, `PipControlOverlay`)

> **Navigation**: [**GenericUI Root (`../../README.md`)**](../../README.md) · [**Extensions Index (`../README.md`)**](../README.md) · [**Root Extensions Guide (`../../docs/extensions.md`)**](../../docs/extensions.md) · [**Fragments & Windows (`../../docs/fragments-and-windows.md`)**](../../docs/fragments-and-windows.md) · [**Source Sets (`../../docs/sourcesets.md`)**](../../docs/sourcesets.md) · [**Backends (`../../docs/backends.md`)**](../../docs/backends.md) · [**Gradle Plugin (`../../docs/gradle-plugin.md`)**](../../docs/gradle-plugin.md)

`:extensions:ui:pip` (`com.nexustools.genericui.ext.pip`) is a **Single Consolidated KMP Project** depending on `:common` and [`:extensions:ui:overlay`](overlay.md) (`api`), with zero separate `:dsl` companion module. It provides an architecture-agnostic Picture-in-Picture framework allowing `@PiPCompatible` fragments to detach a target component (video player, interactive 2D/3D canvas, or custom widget subtree) into an OS-native or always-on-top floating PiP window with non-destructive DOM/hierarchy restoration upon exit.

---

## 1. Platform Backend Matrix (`PipBackend`)

| Target Family | Backend Implementation | Supported Content (`PipContentType`) | Behavior & OS / Gradle Integration |
| :--- | :--- | :--- | :--- |
| **Android (`:android`)** | [`AndroidPipBackend`](../ui/pip/src/androidMain/kotlin/com/nexustools/genericui/ext/pip/android/AndroidPipBackend.kt) + [`AndroidPipActivityDelegate`](../ui/pip/src/androidMain/kotlin/com/nexustools/genericui/ext/pip/android/AndroidPipActivityDelegate.kt) | `VIDEO`, `CANVAS`, `ANY_COMPONENT` | Configures Android `PictureInPictureParams` (aspect ratio, tracked source-rect hint via `AndroidPipSourceRectTracker`, remote `PipAction` buttons via `AndroidPipIconRenderer`, and target view isolation via `AndroidPipTargetIsolator`). [`PipAndroidActivityExtensionContributor`](../../gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/PipAndroidActivityExtensionContributor.kt) automatically wires `@PiPCompatible` fragment Activities with `android:supportsPictureInPicture="true"` and orientation/size `configChanges`. Supports auto-enter on backgrounding. |
| **iOS & iPadOS (`:ios`)** | [`IosPipBackend`](../ui/pip/src/iosMain/kotlin/com/nexustools/genericui/ext/pip/ios/IosPipBackend.kt) + [`IosPipControllerDelegate`](../ui/pip/src/iosMain/kotlin/com/nexustools/genericui/ext/pip/ios/IosPipControllerDelegate.kt) | `VIDEO` | Locates the underlying `AVPlayerLayer` via `IosPipLayerFinder` without compile-time coupling to `:extensions:media:media`, activates `AVAudioSession` playback category, and drives Apple `AVPictureInPictureController`. [`PipExtensionContributor`](../../gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/spi/PipExtensionContributor.kt) merges `UIBackgroundModes = ["audio"]` into `Info.plist`. |
| **Native Desktop** (`:windows`, `:macos`, `:gtk`, `:kde`) | [`DesktopFloatingPipBackend`](../ui/pip/src/nativeDesktopMain/kotlin/com/nexustools/genericui/ext/pip/desktop/DesktopFloatingPipBackend.kt) | `VIDEO`, `CANVAS`, `ANY_COMPONENT` | Moves or mirrors (`DesktopPipSurfaceMirror`) the target component via [`ComponentReparentSlot`](../ui/pip/src/commonMain/kotlin/com/nexustools/genericui/ext/pip/ComponentReparentSlot.kt) into an always-on-top undecorated `Window` overlaid with [`PipControlOverlay`](../ui/pip/src/commonMain/kotlin/com/nexustools/genericui/ext/pip/PipControlOverlay.kt) (`2000ms` auto-hide HUD with drag surface, Restore `⤢`, Close `✕`, and `PipAction` buttons). Supports auto-enter on window minimize, background, and tab switch. |
| **Desktop JVM (`:swing`)** | [`SwingFloatingPipBackend`](../ui/pip/src/jvmMain/kotlin/com/nexustools/genericui/ext/pip/swing/SwingFloatingPipBackend.kt) | `VIDEO`, `CANVAS`, `ANY_COMPONENT` | Reparents the target component via `ComponentReparentSlot` into an always-on-top undecorated utility `Window` with `PipControlOverlay`, restoring the exact slot index when exiting PiP. |
| **Web (`:js`, `:wasmJs`)** | [`WebPipBackend`](../ui/pip/src/webMain/kotlin/com/nexustools/genericui/ext/pip/web/WebPipBackend.kt) | `VIDEO`, `CANVAS`, `ANY_COMPONENT` | Three-tier strategy: (1) W3C **Document Picture-in-Picture (`window.documentPictureInPicture`)** for arbitrary components with `PipControlOverlay` and `ComponentReparentSlot`, (2) **native `<video>` PiP** fallback for video nodes when Document PiP is unavailable, and (3) **in-place fixed corner mini-player** (`position: fixed`) when browser Document PiP is blocked or unavailable. |

---

## 2. Gradle Plugin Configuration

Enabling `pip()` automatically enables `overlay()` and registers [`PlatformPipBootstrap`](../ui/pip/src/commonMain/kotlin/com/nexustools/genericui/ext/pip/PlatformPipBootstrap.kt) (and configures `@PiPCompatible` Android Activities with `android:supportsPictureInPicture="true"` via [`PipAndroidActivityExtensionContributor`](../../gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/PipAndroidActivityExtensionContributor.kt)):

```kotlin
plugins {
    id("com.nexustools.genericui")
}

genericui {
    backends {
        allExceptSwing()
    }
    extensions {
        pip() // Enables :extensions:ui:pip + :extensions:ui:overlay
    }
}
```

---

## 3. Core API Reference (`com.nexustools.genericui.ext.pip`)

| Symbol | Role & Key Members |
| :--- | :--- |
| `@PiPCompatible` | Annotation marking a `Fragment` or custom component as Picture-in-Picture capable; triggers Android manifest `supportsPictureInPicture="true"` injection via `PipAndroidActivityExtensionContributor`. |
| [`FragmentPipHelper`](../ui/pip/src/commonMain/kotlin/com/nexustools/genericui/ext/pip/FragmentPipHelper.kt) | Lifecycle helper coordinating target discovery (`pipTarget`, `pipTargetProvider`), `childDelegateResolver`, `configProvider`, `actionsProvider`, `aspectRatioProvider`, `isEnabledProvider`, `isActivelyPlayingProvider`, `onPauseRequested`, `onPipModeChanged`, `bindWindow`, `unbindWindow`, `enterPip()`, `exitPip()`, `togglePip()`, and `notifyStateChanged()`. |
| [`PipSessionConfig`](../ui/pip/src/commonMain/kotlin/com/nexustools/genericui/ext/pip/PipSessionConfig.kt) | Immutable session config (`enabled`, `aspectRatioNumerator = 16`, `aspectRatioDenominator = 9`, `autoEnterOnMinimize`, `autoEnterOnBackground`, `autoEnterOnTabSwitch`, `pauseOnCloseButton`, `preferredWidthPx = 420`, `preferredHeightPx = 236`, `screenMarginPx = 24`, `title`). |
| [`PipAction`](../ui/pip/src/commonMain/kotlin/com/nexustools/genericui/ext/pip/PipAction.kt) & [`PipActionIcon`](../ui/pip/src/commonMain/kotlin/com/nexustools/genericui/ext/pip/PipActionIcon.kt) | Action descriptor (`id`, `label`, `icon`, `enabled`, `customResourceUri`, `onTrigger`) with icons `PLAY`, `PAUSE`, `PREVIOUS`, `NEXT`, `SKIP_BACKWARD`, `SKIP_FORWARD`, `MUTE`, `UNMUTE`, `RESTORE`, `CLOSE`, `CUSTOM`. |
| [`PipCapabilities`](../ui/pip/src/commonMain/kotlin/com/nexustools/genericui/ext/pip/PipCapabilities.kt) & [`PipContentType`](../ui/pip/src/commonMain/kotlin/com/nexustools/genericui/ext/pip/PipContentType.kt) | Capability snapshot (`isSupported`, `supportedContentTypes: Set<PipContentType>` of `VIDEO` / `CANVAS` / `ANY_COMPONENT`, `supportsCustomControlOverlay`, `supportsInPlaceWindowPip`, `supportsAutoEnterOnMinimize`, `supportsAutoEnterOnBackground`, `supportsAutoEnterOnTabSwitch`). |
| [`PipControlOverlay`](../ui/pip/src/commonMain/kotlin/com/nexustools/genericui/ext/pip/PipControlOverlay.kt) | `AbstractOverlay` subclass styled via `:extensions:ui:pip/src/commonMain/resources/styles/style.scss` (zero direct Kotlin component styling), using `.hidden` CSS fade (`AutoHideController`, `AUTO_HIDE_DELAY_MS = 2000L`), `allowInputInPipOverlay = true`, `windowDragEnabled = true`, `OverlayInputRouter` stage gestures (`>= 5px` drag, `250ms` Play/Pause click, double-click restore), `dragSurface`, `restoreButton`, `closeButton`, and bottom action strip. |
| [`ComponentReparentSlot`](../ui/pip/src/commonMain/kotlin/com/nexustools/genericui/ext/pip/ComponentReparentSlot.kt) | Moves a component out of its parent `AbstractContainer` (replacing it with a matching placeholder preserving layout/constraints) and restores it cleanly on exit. |
| [`PipRegistry`](../ui/pip/src/commonMain/kotlin/com/nexustools/genericui/ext/pip/PipRegistry.kt) & [`FragmentPipRegistry`](../ui/pip/src/commonMain/kotlin/com/nexustools/genericui/ext/pip/FragmentPipRegistry.kt) | Lazy platform backend bootstrap (`registerBackendFactory`, `createBackend`, `capabilities()`, `supportsComponent`) and weak-identity lookup associating `Fragment` / `IWindow` instances with their `FragmentPipHelper`. |

---

## 4. Usage Example (`@PiPCompatible` & `FragmentPipHelper`)

```kotlin
import com.nexustools.genericui.component.fragment.AppFragment
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.ext.dsl.build
import com.nexustools.genericui.ext.dsl.button
import com.nexustools.genericui.ext.dsl.div
import com.nexustools.genericui.ext.media.dsl.videoOverlayHost
import com.nexustools.genericui.ext.overlay.dsl.attachYoutubePlayerOverlay
import com.nexustools.genericui.ext.pip.FragmentPipHelper
import com.nexustools.genericui.ext.pip.PiPCompatible
import com.nexustools.genericui.ext.pip.PipAction
import com.nexustools.genericui.ext.pip.PipActionIcon
import com.nexustools.genericui.ext.pip.PipSessionConfig

@PiPCompatible
@AppFragment(label = "PiP Theatre", route = "theatre")
class PipTheatreFragment : Fragment() {
    private var pipHelper: FragmentPipHelper? = null

    override fun onCreate(savedState: FragmentBundle?) {
        super.onCreate(savedState)

        rootContainer.build {
            div(classes = "p-3") {
                val overlayHost = videoOverlayHost(
                    sourceUrl = "https://cdn.example.com/streams/keynote.mp4"
                ) {
                    attachYoutubePlayerOverlay()
                }
                val player = overlayHost.videoPlayer

                val helper = FragmentPipHelper(this@PipTheatreFragment).apply {
                    pipTarget = overlayHost
                    configProvider = {
                        PipSessionConfig(
                            title = "Keynote Broadcast",
                            aspectRatioNumerator = 16,
                            aspectRatioDenominator = 9,
                            autoEnterOnMinimize = true,
                            autoEnterOnBackground = true
                        )
                    }
                    isActivelyPlayingProvider = { player.isPlaying }
                    actionsProvider = {
                        listOf(
                            PipAction(
                                id = "play-pause",
                                label = if (player.isPlaying) "Pause" else "Play",
                                icon = if (player.isPlaying) PipActionIcon.PAUSE else PipActionIcon.PLAY,
                                onTrigger = {
                                    player.togglePlayPause()
                                    notifyStateChanged()
                                }
                            )
                        )
                    }
                    onPauseRequested = { player.pause() }
                    onPipModeChanged = { inPip ->
                        overlayHost.setPictureInPictureMode(inPip)
                    }
                    bindWindow()
                }
                pipHelper = helper

                button("Toggle PiP") {
                    onClick = { helper.togglePip() }
                }
            }
        }
    }

    override fun onDestroy() {
        pipHelper?.dispose()
        pipHelper = null
        super.onDestroy()
    }
}
```

---

## 5. Related Documentation

- [`:extensions:ui:overlay` (`overlay.md`)](overlay.md) — Base overlay framework extended by `PipControlOverlay` and suppressed cleanly via `setPictureInPictureMode(true)`.
- [`:extensions:media:media` (`media.md`)](media.md) — `VideoPlayer` & `VideoOverlayHost` integration with PiP.
- [GenericUI Root (`../../README.md`)](../../README.md) · [Extensions Architecture (`../../docs/extensions.md`)](../../docs/extensions.md) · [Gradle Plugin (`../../docs/gradle-plugin.md`)](../../docs/gradle-plugin.md)
