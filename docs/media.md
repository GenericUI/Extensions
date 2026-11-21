# `:extensions:media:media` & `:extensions:media:media:dsl` — `VideoPlayer`, `AudioPlayer`, `ExpandingVolumeSlider`, Transparent Codec Probing, `VlcConfiguration` Schema, & `YoutubeMediaOverlayHelper`

> **Navigation**: [**GenericUI Root (`../../README.md`)**](../../README.md) · [**Extensions Index (`../README.md`)**](../README.md) · [**Root Extensions Guide (`../../docs/extensions.md`)**](../../docs/extensions.md) · [**Resources (`../../docs/resources.md`)**](../../docs/resources.md) · [**Source Sets (`../../docs/sourcesets.md`)**](../../docs/sourcesets.md) · [**Backends (`../../docs/backends.md`)**](../../docs/backends.md) · [**Gradle Plugin (`../../docs/gradle-plugin.md`)**](../../docs/gradle-plugin.md)

`:extensions:media:media` is a **Single Consolidated KMP Project** delivering [`VideoPlayer`](../media/media/src/commonMain/kotlin/com/nexustools/genericui/component/media/VideoPlayer.kt), [`AudioPlayer`](../media/media/src/commonMain/kotlin/com/nexustools/genericui/component/media/AudioPlayer.kt), real-time FFT [`AudioAnalyzer`](../media/media/src/commonMain/kotlin/com/nexustools/genericui/component/media/AudioAnalyzer.kt) (`AudioEnergyBands`), [`SeekBar`](../media/media/src/commonMain/kotlin/com/nexustools/genericui/component/media/SeekBar.kt), the 3-wave animated [`ExpandingVolumeSlider`](../media/media/src/commonMain/kotlin/com/nexustools/genericui/component/media/ExpandingVolumeSlider.kt), [`MediaExtension`](../media/media/src/commonMain/kotlin/com/nexustools/genericui/ext/media/MediaExtension.kt) transparent codec support & schema settings, [`VideoOverlayHost`](../media/media/src/commonMain/kotlin/com/nexustools/genericui/ext/media/overlay/VideoOverlayHost.kt) / [`YoutubeMediaOverlayHelper`](../media/media/src/commonMain/kotlin/com/nexustools/genericui/ext/media/overlay/YoutubeMediaOverlayHelper.kt) (built atop [`:extensions:ui:overlay`](overlay.md)), OS-level `MediaSessionManager`, and `WebVttSrtParser`.

Its companion module `:extensions:media:media:dsl` (`com.nexustools.genericui.ext.media.dsl`) provides `videoPlayer`, `audioPlayer`, `seekBar`, `audioAnalyzer`, `videoOverlayHost`, `expandingVolumeSlider`, and `buildExpandingVolumeSlider`.

> [!IMPORTANT]
> **Platform Playback Engines**:
> - **Native Desktop (`:windows`, `:macos`, `:gtk`, `:qt`)**: Uses native `libvlc` (`LibVlcBindings`, `DesktopLibVlcVideoPlayerImpl`, `DesktopLibVlcAudioPlayerImpl`, `DesktopLibVlcAudioAnalyzerImpl`) inside `nativeDesktopMain`.
> - **JVM Desktop (`:swing`)**: Uses `libvlcj` (`uk.co.caprica:vlcj` via `SwingVideoPlayerImpl`, `SwingAudioPlayerImpl`, `SwingAudioAnalyzerImpl`) inside `jvmMain`.
> - **Mobile (`:android`, `:ios`)**: Uses native OS media engines (`MediaPlayer` / `MediaCodec` + `Visualizer` on Android, `AVPlayer` + `vDSP` on iOS / iPadOS).
> - **Web (`:js`, `:wasmJs`)**: Uses native HTML5 `<video>`, `<audio>`, and Web Audio API `AnalyserNode` components inside `webMain`.

---

## 1. Gradle Plugin Configuration

Enabling `media()` in `build.gradle.kts` automatically enables [`:extensions:ui:overlay`](overlay.md) (`overlay()`) and [`:extensions:data:schema`](schema.md) (`schema()`), registers the active platform's media providers inside `GenericUiAppDescriptor`, and includes `:extensions:media:media:dsl` when `dsl()` is active:

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

## 3. Built-In Volume SVGs (`com.nexustools.genericui.component.media.res.R`) & 3-Wave `ExpandingVolumeSlider`

### 3.1 `com.nexustools.genericui.component.media.res.R` (5 Pure-Bezier Volume SVGs)
[`MediaVolumeIcons`](../media/media/src/commonMain/kotlin/com/nexustools/genericui/component/media/MediaVolumeIcons.kt) exposes five pure-Bezier SVG icons bundled in `src/commonMain/resources/drawable/` under `com.nexustools.genericui.component.media.res.R`:

| `MediaVolumeIcons` Property | Generated `R.drawable` Resource ID | SVG File |
| :--- | :--- | :--- |
| `MediaVolumeIcons.speaker` | `R.drawable.ic_volume_speaker` | [`ic_volume_speaker.svg`](../media/media/src/commonMain/resources/drawable/ic_volume_speaker.svg) |
| `MediaVolumeIcons.muteCross` | `R.drawable.ic_volume_mute_cross` | [`ic_volume_mute_cross.svg`](../media/media/src/commonMain/resources/drawable/ic_volume_mute_cross.svg) |
| `MediaVolumeIcons.wave1` | `R.drawable.ic_volume_wave_1` | [`ic_volume_wave_1.svg`](../media/media/src/commonMain/resources/drawable/ic_volume_wave_1.svg) |
| `MediaVolumeIcons.wave2` | `R.drawable.ic_volume_wave_2` | [`ic_volume_wave_2.svg`](../media/media/src/commonMain/resources/drawable/ic_volume_wave_2.svg) |
| `MediaVolumeIcons.wave3` | `R.drawable.ic_volume_wave_3` | [`ic_volume_wave_3.svg`](../media/media/src/commonMain/resources/drawable/ic_volume_wave_3.svg) |

### 3.2 3-Wave `ExpandingVolumeSlider` Architecture & Desktop Stage HUD Sync
[`ExpandingVolumeSlider`](../media/media/src/commonMain/kotlin/com/nexustools/genericui/component/media/ExpandingVolumeSlider.kt) pairs a borderless `muteButton` (`StyledButton`) with a collapsible inline `SeekBar` (`slider` inside `sliderContainer`) and a 5-layer `LayeredContainer` icon stack (`muteIconOverlay`: `speakerIconLayer`, `wave1Layer`, `wave2Layer`, `wave3Layer`, `muteCrossLayer`):
- **Smooth Threshold Wave & Mute Fades**: Computes per-layer target opacities based on `value` (`0..100`) and `isMuted` — dimming waves below `0%`, progressively illuminating `wave1`, `wave2` (`> 33%`), and `wave3` (`> 66%`), and cross-fading `muteCrossLayer` when muted (`iconAnimationDurationMs = 160L`).
- **Animated Expansion**: Expands `sliderContainer` on pointer hover, focus, or active scrubbing (`expandedSliderWidthPx = 92`, `animationDurationMs = 180L`), syncing both CSS width/opacity transitions and numeric attributes (`data-expansion-progress`, `data-animated-width-px`).
- **Desktop Stage HUD Wave-Alpha Sync**: Syncs `data-wave-1-opacity`, `data-wave-2-opacity`, `data-wave-3-opacity`, `data-mute-cross-opacity`, and `data-volume-layer` attributes onto the widget so desktop stage/GPU HUD renderers (such as `MacosStageOverlayRenderUtil` and `Win32WorkspaceWindowRenderUtil`) render identical per-wave alpha interpolation natively.
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
| `ExpandingVolumeSlider` | `component.media` | 3-wave icon stack (`speakerIconLayer`, `wave1Layer`, `wave2Layer`, `wave3Layer`, `muteCrossLayer`), `slider: SeekBar`, `muteButton`, `setExpanded`, `updateVolumeState`, `bindTo(player)`. |
| `YoutubeMediaOverlayHelper` | `ext.media.overlay` | `seekBar`, `volumeSlider`, `syncState`, `updateVolumeState`, `setAccentColor`. |
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
- [`:extensions:media:mediacontrols` (`mediacontrols.md`)](mediacontrols.md) — OS taskbar thumbnail buttons, SMTC / Now Playing, and hardware media key hooks.
- [`:extensions:ui:dsl` (`dsl.md`)](dsl.md) — Base declarative Kotlin UI & Style DSL.
