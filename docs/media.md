# `:extensions:media:media` & `:extensions:media:media:dsl` — `VideoPlayer`, `VideoFragment`, `SeekBar : StyledSlider`, `ExpandingVolumeSlider : Composite`, `style.scss`/`theme.scss`, & `YoutubeMediaOverlayHelper`

> **Navigation**: [**GenericUI Root (`../../README.md`)**](../../README.md) · [**Extensions Index (`../README.md`)**](../README.md) · [**Root Extensions Guide (`../../docs/extensions.md`)**](../../docs/extensions.md) · [**Resources (`../../docs/resources.md`)**](../../docs/resources.md) · [**Source Sets (`../../docs/sourcesets.md`)**](../../docs/sourcesets.md) · [**Backends (`../../docs/backends.md`)**](../../docs/backends.md) · [**Gradle Plugin (`../../docs/gradle-plugin.md`)**](../../docs/gradle-plugin.md)

`:extensions:media:media` is a **Single Consolidated KMP Project** delivering [`VideoPlayer`](../media/media/src/commonMain/kotlin/com/nexustools/genericui/component/media/VideoPlayer.kt), [`VideoFragment`](../media/media/src/commonMain/kotlin/com/nexustools/genericui/component/media/VideoFragment.kt) (`: Fragment(), Player, OverlayStageProvider`), [`AudioPlayer`](../media/media/src/commonMain/kotlin/com/nexustools/genericui/component/media/AudioPlayer.kt), real-time FFT [`AudioAnalyzer`](../media/media/src/commonMain/kotlin/com/nexustools/genericui/component/media/AudioAnalyzer.kt) (`AudioEnergyBands`), [`SeekBar`](../media/media/src/commonMain/kotlin/com/nexustools/genericui/component/media/SeekBar.kt) (`: StyledSlider`, zero `SeekBarNativeImpl`), the 3-wave animated [`ExpandingVolumeSlider`](../media/media/src/commonMain/kotlin/com/nexustools/genericui/component/media/ExpandingVolumeSlider.kt) (`: Composite`, containing mute/volume `StyledButton` + `StyledSlider`), [`MediaExtension`](../media/media/src/commonMain/kotlin/com/nexustools/genericui/ext/media/MediaExtension.kt) transparent codec support & schema settings, [`VideoOverlayHost`](../media/media/src/commonMain/kotlin/com/nexustools/genericui/ext/media/overlay/VideoOverlayHost.kt) / [`YoutubeMediaOverlayHelper`](../media/media/src/commonMain/kotlin/com/nexustools/genericui/ext/media/overlay/YoutubeMediaOverlayHelper.kt) (built atop [`:extensions:ui:overlay`](overlay.md)), OS-level `MediaSessionManager`, and `WebVttSrtParser`.

Its companion module `:extensions:media:media:dsl` (`com.nexustools.genericui.ext.media.dsl`) provides `videoPlayer`, `audioPlayer`, `seekBar`, `audioAnalyzer`, `videoOverlayHost`, `expandingVolumeSlider`, and `buildExpandingVolumeSlider`.

> [!IMPORTANT]
> **Platform Playback Engines**:
> - **Native Desktop (`:windows`, `:macos`, `:gtk`, `:kde`)**: Uses native `libvlc` (`LibVlcBindings`, `DesktopLibVlcVideoPlayerImpl`, `DesktopLibVlcAudioPlayerImpl`, `DesktopLibVlcAudioAnalyzerImpl`) inside `nativeDesktopMain`.
> - **JVM Desktop (`:swing`)**: Uses `libvlcj` (`uk.co.caprica:vlcj` via `SwingVideoPlayerImpl`, `SwingAudioPlayerImpl`, `SwingAudioAnalyzerImpl`) inside `jvmMain`.
> - **Mobile (`:android`, `:ios`)**: Uses native OS media engines (`MediaPlayer` / `MediaCodec` + `Visualizer` on Android, `AVPlayer` + `vDSP` on iOS / iPadOS).
> - **Web (`:js`, `:wasmJs`)**: Uses native HTML5 `<video>`, `<audio>`, and Web Audio API `AnalyserNode` components inside `webMain`.

---

## 1. Gradle Plugin Configuration

Enabling `media()` in `build.gradle.kts` automatically enables [`:extensions:ui:overlay`](overlay.md) (`overlay()`) and [`:extensions:data:schema`](schema.md) (`schema()`), registers the active platform's media providers inside `GenericUiAppDescriptor`, includes `:extensions:media:media:dsl` when `dsl()` is active, and auto-adds & auto-registers **`:extensions:media:media:alien`** ([`MediaAlienExtension`](../media/media/alien/src/commonMain/kotlin/com/nexustools/genericui/ext/media/alien/MediaAlienExtension.kt) $\rightarrow$ `VideoPlayerAlienImpl` offscreen frame capture into Alien's `Canvas2D` scene graph) whenever `alien()` (or Desktop `webView()`) is active:

```kotlin
plugins {
    id("com.nexustools.genericui")
}

genericui {
    backends {
        allExceptSwing()
    }
    extensions {
        dsl()   // Enables :extensions:ui:dsl + :extensions:media:media:dsl + :extensions:ui:overlay:dsl
        media() // Enables :extensions:media:media (+ overlay() + schema())
    }
}
```

---

## 2. Transparent Codec & Capability Probing (`MediaCodecSupportProvider` & `MediaExtension`)

Codec and media capability checks do **not** live on `VideoPlayer` or instantiate any player widgets/surfaces. Instead, [`MediaExtension`](../media/media/src/commonMain/kotlin/com/nexustools/genericui/ext/media/MediaExtension.kt) queries the registered [`MediaCodecSupportProvider`](../media/media/src/commonMain/kotlin/com/nexustools/genericui/ext/media/MediaCodecSupportProvider.kt) SPI directly:

- **`LibVlcCodecSupportProvider` (`nativeDesktop` & `jvm` Swing)**: Returns flat `true` codec/track support and a precomputed immutable [`MediaCapabilitiesReport`](../media/media/src/commonMain/kotlin/com/nexustools/genericui/component/media/MediaCapabilitiesReport.kt) (`ALL_SUPPORTED_CAPABILITIES`) with zero caching or runtime probing overhead.
- **`CachingMediaCodecSupportProvider` (`Android`, `iOS`, `Web`)**: Lock-free base class backed by `AtomicReference` copy-on-write caches (`AndroidMediaCodecSupportProvider`, `IosMediaCodecSupportProvider`, `WebMediaCodecSupportProvider`) so OS/DOM capability probes execute at most once per `VideoCodec` / `AudioCodec` and once for `queryMediaCapabilities()`.

### Query APIs on `MediaExtension`
- `MediaExtension.isVideoCodecSupported(codec: VideoCodec): Boolean`
- `MediaExtension.isAudioCodecSupported(codec: AudioCodec): Boolean`
- `MediaExtension.getSupportedVideoCodecs(): Set<VideoCodec>`
- `MediaExtension.getSupportedAudioCodecs(): Set<AudioCodec>`
- `MediaExtension.getMediaCapabilities(): MediaCapabilitiesReport`
- `MediaExtension.supportsAudioTrackSelection(): Boolean`
- `MediaExtension.supportsSubtitleTrackSelection(): Boolean`

---

## 3. Built-In Volume SVGs (`com.nexustools.genericui.component.media.res.R`), `SeekBar : StyledSlider`, `ExpandingVolumeSlider : Composite`, & `style.scss` / `theme.scss`

### 3.1 `com.nexustools.genericui.component.media.res.R` (5 Pure-Bezier Volume SVGs)
[`MediaVolumeIcons`](../media/media/src/commonMain/kotlin/com/nexustools/genericui/component/media/MediaVolumeIcons.kt) exposes five pure-Bezier SVG icons bundled in `src/commonMain/resources/drawable/` under `com.nexustools.genericui.component.media.res.R`:

| `MediaVolumeIcons` Property | Generated `R.drawable` Resource ID | SVG File |
| :--- | :--- | :--- |
| `MediaVolumeIcons.speaker` | `R.drawable.ic_volume_speaker` | [`ic_volume_speaker.svg`](../media/media/src/commonMain/resources/drawable/ic_volume_speaker.svg) |
| `MediaVolumeIcons.muteCross` | `R.drawable.ic_volume_mute_cross` | [`ic_volume_mute_cross.svg`](../media/media/src/commonMain/resources/drawable/ic_volume_mute_cross.svg) |
| `MediaVolumeIcons.wave1` | `R.drawable.ic_volume_wave_1` | [`ic_volume_wave_1.svg`](../media/media/src/commonMain/resources/drawable/ic_volume_wave_1.svg) |
| `MediaVolumeIcons.wave2` | `R.drawable.ic_volume_wave_2` | [`ic_volume_wave_2.svg`](../media/media/src/commonMain/resources/drawable/ic_volume_wave_2.svg) |
| `MediaVolumeIcons.wave3` | `R.drawable.ic_volume_wave_3` | [`ic_volume_wave_3.svg`](../media/media/src/commonMain/resources/drawable/ic_volume_wave_3.svg) |

### 3.2 Shared `StyledSlider` / `SeekBar` / `ExpandingVolumeSlider` `Composite` Architecture & Zero Direct Kotlin Styling
Both `SeekBar` and `ExpandingVolumeSlider` use `:common`'s **`Composite`** (`AbstractComponent`, NOT `AbstractContainer`, hiding internal `compositeChildren` while realizing uniformly across all 9 backends via `StyledContainerNativeImpl`) and **`StyledComponent`** (`StyledComponentNativeImpl`) — with **ZERO custom `SeekBarNativeImpl` or backend fast-paths**:
- **`SeekBar : StyledSlider`**: Inherits `:common`'s `StyledSlider : Composite` — which composes a clickable track `StyledButton` (`.styled-slider-track`, `z-index: 1`), `$accent`-colored progress `StyledComponent` (`.styled-slider-progress`, `z-index: 3`, `pointer-events: none`), and knob `StyledButton` (`.styled-slider-knob`, `z-index: 4`, vertically centered via `top: 50%; transform: translate(-50%, -50%)`) — and appends `preloadSegment: StyledComponent` (`.seek-bar-preload`, `z-index: 2`, `pointer-events: none`) to the end of `compositeChildren` so `z-index` renders preload below progress and knob while passing pointer events cleanly through to the track.
- **`ExpandingVolumeSlider : Composite`**: Composes the borderless mute/volume `StyledButton` (`.expanding-volume-mute-btn` with 5-layer SVG icon stack: `speakerIconLayer`, `wave1Layer`, `wave2Layer`, `wave3Layer`, `muteCrossLayer`), collapsible `sliderContainer`, and an inline **`StyledSlider`** (`slider: StyledSlider`, NOT `SeekBar`).
- **`style.scss` + Raw `theme.scss` Template vs. Consumer-App Theme Compilation**: `:extensions:media:media` ships `src/commonMain/resources/styles/style.scss` (precompiled static dimensions, layouts, `:hover`/`:focus-visible`/`:active`/`:disabled` states, `pointer-events: none`, vertical-centering `transform`s, and `.expanded` / `.volume-wave-*` / `.volume-muted` transition rules) and a raw uncompiled `src/commonMain/resources/styles/theme.scss` template referencing `$accent` and `rgba($accent, ...)`. Like `:common` and all extensions, `:extensions:media:media` NEVER compiles or integrates resulting `theme.scss` code into its own `.jar`/`.klib` library artifact — it packages only the raw template inside `META-INF/genericui/themes/<scopeId>.scss` and `META-INF/genericui/module-resources/<scopeId>.json`. Only the consumer application's `:gradle-plugin` build extracts that template, substitutes its `genericui { theme { ... } }` settings from `build.gradle.kts`, and integrates `GenericUiGeneratedThemeStyleSheet` (`:system`) and `gui/genericui-theme.css` (`:web`) solely into the consumer app (with `ThemeStyleSheetRegistry` handling dynamic runtime updates). Direct Kotlin styles/color overrides are forbidden; only runtime-dynamic percentage values (such as `StyledSlider` fill/knob `%` and `SeekBar` preload `%`) are updated in Kotlin.
- **Direct Player Binding & DSL**: Call `volumeSlider.bindTo(player)` (`VideoPlayer` or `AudioPlayer`) for automatic two-way volume/mute sync, or instantiate via `:extensions:media:media:dsl` using `expandingVolumeSlider(...)` / `buildExpandingVolumeSlider(...)`.

---

## 4. `VlcConfiguration` (`@SchemaData`) & Dynamic Player Settings Schema (`MediaExtension`)

[`VlcConfiguration`](../media/media/src/commonMain/kotlin/com/nexustools/genericui/ext/media/vlc/VlcConfiguration.kt) is annotated with `@SchemaData(id = "vlc_configuration", title = "VLC Player Settings", ...)` and exposes [`VlcConfigurationSchemaDescriptor`](../media/media/src/commonMain/kotlin/com/nexustools/genericui/ext/media/vlc/VlcConfigurationSchemaDescriptor.kt) (auto-registered in `SchemaRegistry` during `MediaExtension` initialization) for zero-boilerplate integration with [`:extensions:data:schema`](schema.md):

- **Fields on `VlcConfiguration`**:
  - `audioOutputModule: String` (`--aout`), dynamically populated via `MediaExtension.listVlcAudioOutputModules()`
  - `audioOutputDeviceId: String` (`VlcAudioOutputDevice`), reactively filtered by selected `audioOutputModule` via `MediaExtension.listVlcAudioOutputDevices(...)`
  - `videoRenderer: VlcVideoRendererMode` (`AUTO`, etc., queried via `listSupportedVlcVideoRenderers()`)
  - `hardwareDecode: VlcHardwareDecodeMode` (`ANY`, etc., queried via `listSupportedVlcHardwareDecodeModes()`)
  - `deinterlaceMode: VlcDeinterlaceMode` (`OFF`, etc.)
  - `spdifPassthrough: Boolean` (`SchemaWidgetHint.SWITCH`)
  - `networkCachingMs: Int` (`0..60000 ms`, default `1000`) & `fileCachingMs: Int` (`0..60000 ms`, default `300`)
- **Backend-Agnostic Player Settings APIs on `MediaExtension`**:
  - `MediaExtension.hasConfigurablePlayerSettings(target = MediaPlayerSettingsTarget.BOTH): Boolean`
  - `MediaExtension.getPlayerSettingsSchema(target = MediaPlayerSettingsTarget.BOTH): ObjectSchema`
  - `MediaExtension.getPlayerSettingsValues(): Map<String, Any?>`
  - `MediaExtension.applyPlayerSettingsValues(values: Map<String, Any?>)`
  - Built dynamically by [`MediaPlayerSettingsSchemaFactory`](../media/media/src/commonMain/kotlin/com/nexustools/genericui/ext/media/MediaPlayerSettingsSchemaFactory.kt) for LibVLC backends (or delegates to any registered `MediaPlayerSettingsProvider`).

---

## 5. `VideoOverlayHost`, `VideoPlayerOverlayBinder`, & `YoutubeMediaOverlayHelper`

- **[`VideoOverlayHost`](../media/media/src/commonMain/kotlin/com/nexustools/genericui/ext/media/overlay/VideoOverlayHost.kt) & [`VideoPlayerOverlayBinder`](../media/media/src/commonMain/kotlin/com/nexustools/genericui/ext/media/overlay/VideoPlayerOverlayBinder.kt)**: Specialized `OverlayHostBox` wrapping a `VideoPlayer`, binding playback state, `bufferedRanges`, `audioTracks`, `subtitleTracks`, WebVTT/SRT subtitle cues (`loadSubtitleContent`, `attachSubtitleCues`), fullscreen targeting, PiP overlay suppression, and bidirectional `OverlayActions` via a 1000ms playing poll timer + state observers.
- **[`YoutubeMediaOverlayHelper`](../media/media/src/commonMain/kotlin/com/nexustools/genericui/ext/media/overlay/YoutubeMediaOverlayHelper.kt)**: Attaches `:extensions:media:media`'s `SeekBar` (with hover timestamp preview via `YoutubePlayerOverlay.showTimelineHover` / `hideTimelineHover`) and optional 3-wave `ExpandingVolumeSlider` directly into a `YoutubePlayerOverlay`'s `timelineContainer` and `leftAccessoryContainer`, synchronizing state passively via an `OverlayElement` listener.

---

## 6. Core API Reference & `:extensions:media:media:dsl`

| Symbol | Package | Key Members |
| :--- | :--- | :--- |
| `MediaExtension` | `ext.media` | `registerProviders`, `isVideoCodecSupported`, `isAudioCodecSupported`, `getSupportedVideoCodecs`, `getSupportedAudioCodecs`, `getMediaCapabilities`, `supportsAudioTrackSelection`, `supportsSubtitleTrackSelection`, `hasConfigurablePlayerSettings`, `getPlayerSettingsSchema`, `getPlayerSettingsValues`, `applyPlayerSettingsValues`, `vlcConfiguration`, `listVlcAudioOutputModules`, `listVlcAudioOutputDevices`, `listSupportedVlcHardwareDecodeModes`, `listSupportedVlcVideoRenderers`. |
| `VideoPlayer` | `component.media` | `setSource`, `play()`, `pause()`, `stop()`, `seekTo(seconds)`, `skipDelta(seconds)`, `togglePlayPause()`, `volume`, `isMuted`, `playbackSpeed`, `resizeMode: VideoResizeMode`, `getAudioTracks()`, `selectAudioTrack`, `getSubtitleTracks()`, `selectSubtitleTrack`, `loadSubtitleContent`, `toggleFullscreen()`, `attachAudioAnalyzer`. |
| `AudioPlayer` | `component.media` | `setSource`, `play()`, `pause()`, `stop()`, `seekTo(seconds)`, `volume`, `isMuted`, `isPlaying`, `currentTimeSeconds`, `durationSeconds`, `attachAudioAnalyzer`. |
| `AudioAnalyzer` | `component.media` | `fftSize`, `frequencyBinCount`, `getByteTimeDomainData`, `getFloatTimeDomainData`, `getByteFrequencyData`, `getFloatFrequencyData`, `computeEnergyBands(): AudioEnergyBands`. |
| `SeekBar` | `component.media` | Extends `:common` `StyledSlider : Composite` (zero `SeekBarNativeImpl`), appending `preloadSegment: StyledComponent` (`.seek-bar-preload`, `z-index: 2`, `pointer-events: none`), `bufferedValue`, `bufferedRanges`, `value`, `min`, `max`, `bindTo(player)`. |
| `ExpandingVolumeSlider` | `component.media` | Extends `Composite`; 3-wave icon stack (`speakerIconLayer`, `wave1Layer`, `wave2Layer`, `wave3Layer`, `muteCrossLayer`), `slider: StyledSlider`, `muteButton: StyledButton`, `setExpanded`, `updateVolumeState`, `bindTo(player)`. |
| `YoutubeMediaOverlayHelper` | `ext.media.overlay` | `seekBar: SeekBar`, `volumeSlider: ExpandingVolumeSlider?`, `syncState`, `updateVolumeState` (inherits theme colors automatically via `theme.scss`). |
| `:media:dsl` Builders | `ext.media.dsl` | `videoPlayer`, `audioPlayer`, `seekBar`, `audioAnalyzer`, `videoOverlayHost`, `expandingVolumeSlider`, `buildExpandingVolumeSlider`. |

---

## 7. Usage Examples

### Example 1: Codec Introspection, `VlcConfiguration` Schema Form, & `VideoOverlayHost`

```kotlin
import com.nexustools.genericui.enums.media.VideoCodec
import com.nexustools.genericui.ext.dsl.buildDiv
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.ext.media.MediaPlayerSettingsTarget
import com.nexustools.genericui.ext.media.dsl.videoOverlayHost
import com.nexustools.genericui.ext.media.overlay.YoutubeMediaOverlayHelper
import com.nexustools.genericui.ext.overlay.dsl.attachYoutubePlayerOverlay
import com.nexustools.genericui.ext.overlay.dsl.hybridOverlayDifference
import com.nexustools.genericui.ext.schema.dsl.schemaForm

fun createStudioPlayerAndSettings() = buildDiv(classes = "p-3 gap-3") {
    val hevcSupported = MediaExtension.isVideoCodecSupported(VideoCodec.H265)
    println("H.265/HEVC supported=$hevcSupported • caps=${MediaExtension.getMediaCapabilities()}")

    val host = videoOverlayHost(sourceUrl = "https://cdn.example.com/movies/cosmos.mp4") {
        val overlay = attachYoutubePlayerOverlay {
            hybridOverlayDifference()
        }
        YoutubeMediaOverlayHelper(overlay = overlay, includeVolumeSlider = true)
        loadSubtitleContent(
            """
            WEBVTT

            00:00:01.000 --> 00:00:05.000
            Welcome to GenericUI Media.
            """.trimIndent()
        )
    }

    if (MediaExtension.hasConfigurablePlayerSettings(MediaPlayerSettingsTarget.VIDEO)) {
        schemaForm(
            schema = MediaExtension.getPlayerSettingsSchema(MediaPlayerSettingsTarget.VIDEO),
            initialData = MediaExtension.getPlayerSettingsValues()
        ) {
            onChange { _, _ ->
                MediaExtension.applyPlayerSettingsValues(getData())
            }
        }
    }
}
```

### Example 2: Standalone 3-Wave `ExpandingVolumeSlider` in `:dsl`

```kotlin
import com.nexustools.genericui.ext.dsl.buildDiv
import com.nexustools.genericui.ext.media.dsl.audioPlayer
import com.nexustools.genericui.ext.media.dsl.expandingVolumeSlider

fun createAudioBar() = buildDiv(classes = "d-flex align-items-center gap-2") {
    val player = audioPlayer(sourceUrl = "https://cdn.example.com/audio/ambient.ogg")
    expandingVolumeSlider(value = 75.0, muted = false) {
        bindTo(player)
    }
}
```

---

## 8. Related Extensions & Cross-References

- [`:extensions:ui:overlay` (`overlay.md`)](overlay.md) — `YoutubePlayerOverlay`, `HybridOverlayDifferenceController`, and pure-Bezier overlay icons.
- [`:extensions:data:schema` (`schema.md`)](schema.md) — Meta-schema UI and persistence engine backing `VlcConfigurationSchemaDescriptor`.
- [`:extensions:ui:pip` (`pip.md`)](pip.md) — Picture-in-Picture support for `VideoOverlayHost` and `VideoPlayer`.
- [`:extensions:ui:alien` (`alien.md`)](alien.md) — Auto-registers `:extensions:media:media:alien` (`MediaAlienExtension` / `VideoPlayerAlienImpl`) for zero-underlay-hole video compositing inside Alien and Desktop WebView workers.
- [`:extensions:media:mediacontrols` (`mediacontrols.md`)](mediacontrols.md) — OS taskbar thumbnail buttons, SMTC / Now Playing, and hardware media key hooks.
- [`:extensions:ui:dsl` (`dsl.md`)](dsl.md) — Base declarative Kotlin UI & Style DSL.
