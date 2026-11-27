# GenericUI — First-Party Extensions (`:extensions`)

> **Parent Repository**: [**GenericUI Root (`../README.md`)**](../README.md) · [**Root Extensions Guide (`../docs/extensions.md`)**](../docs/extensions.md) · [**Layouts & Responsive Helpers (`../docs/layouts-and-helpers.md`)**](../docs/layouts-and-helpers.md) · [**Fragments & Windows (`../docs/fragments-and-windows.md`)**](../docs/fragments-and-windows.md) · [**Resources (`../docs/resources.md`)**](../docs/resources.md) · [**Application & Targets (`../docs/application.md`)**](../docs/application.md) · [**Source Sets Hierarchy (`../docs/sourcesets.md`)**](../docs/sourcesets.md) · [**Native Backends (`../docs/backends.md`)**](../docs/backends.md) · [**Gradle Plugin (`../docs/gradle-plugin.md`)**](../docs/gradle-plugin.md)

The **`extensions/`** tree houses all **24 First-Party GenericUI Extension Modules** (`23` domain extensions + `:extensions:ui:dsl`), **16 companion `:dsl` modules**, and **4 companion `:alien` modules** (`= 44 extension Gradle projects` organized across **7 categories**). Every extension is a **Single Consolidated KMP Project** compiling cleanly inside a unified project without nested Project-of-Projects subtrees (`svg`, `gpu`, `imageprocessing`, `canvas2d`, `canvas3d`, `threejs`, `box2d`, `box3d`, `gamepad`, `engine`, `engine2d`, `engine3d`, `media`, `mediacontrols`, `schema`, `serviceworker`, `dsl`, `overlay`, `pip`, `alien`, `webview`, `codeeditor`, `aichat` across all 9 targets, and target-scoped `ipc` on Desktop + Android only).

---

## 1. Categorized Master Index of GenericUI Extensions

### Category 1 — Graphics (`:extensions:graphics:*`)
- [**`:extensions:graphics:gpu`**](docs/gpu.md) — Cross-platform GPU device management (`GpuDevice`), offscreen compute & shader contexts (`GpuOffscreenContext`), double-buffered swapchains (`GpuDoubleBufferedSurface`), textures (`GpuTexture`), buffers (`GpuBuffer`), and dual-target shader programs (`GpuShaderProgram`: Vulkan 1.2+ `#version 450` SPIR-V primary + OpenGL ES 3.0 `#version 300 es` / Google ANGLE / MoltenVK / WebGPU / WebGL2 fallback).
- [**`:extensions:graphics:imageprocessing`**](docs/imageprocessing.md) — Universal hardware-accelerated `ImageBuffer` & `ImageBuffer2DContext` (`imageBuffer.getContext()`) plus offscreen GPU manipulation (`GpuImageProcessor`, `GpuImagePipelineBuilder`, `GpuImageCompositor`), 12 Porter-Duff & artistic blend modes (`ImageBlendMode`), Lanczos-3 resampling, 256-bin histograms (`ImageHistogramResult`), and built-in GPU filters.
- [**`:extensions:graphics:svg` (`+ :dsl`, `:alien`)**](docs/svg.md) — Standalone W3C SVG DOM scene graph (`Svg`, `SvgElement`, `SvgDefs`, `SvgPath`, `SvgDocumentParser`, `SvgXmlSerializer`, `SvgSoftwareRasterizer`) depending **only on `:common`**, rasterized via `SystemSvgProvider` (`systemMain`), live `<svg>` DOM via `WebSvgProvider` (`webMain`), declarative `:extensions:graphics:svg:dsl` builders, and self-rendered `:extensions:graphics:svg:alien` (`SvgAlienExtension` / `SvgAlienImpl`).
- [**`:extensions:graphics:canvas2d` (`+ :dsl`, `:alien`)**](docs/canvas2d.md) — Hardware-accelerated 2D vector & raster surface (`Canvas2D`, `ScrollCanvas2D`, `CurveEditor`), viewport-only overlay hosts (`Canvas2DOverlayHost`, `ScrollCanvas2DOverlayHost`), direct `:common` `ImageBuffer` drawing/extraction, VBlank-synchronized `registerVBlankRender`, declarative `:dsl` builders, and `:extensions:graphics:canvas2d:alien` (`Canvas2DAlienExtension` / `Canvas2DAlienImpl`).
- [**`:extensions:graphics:canvas3d` (`+ :dsl`, `:alien`)**](docs/canvas3d.md) — Unified hardware-accelerated 3D & programmable shader canvas (`Canvas3D`, `Canvas3DConfig`, fail-fast `Canvas3DRegistry`, `ScrollCanvas3D`, `OffscreenCanvas3D`), overlay hosts (`Canvas3DOverlayHost`, `ScrollCanvas3DOverlayHost`), and `:extensions:graphics:canvas3d:alien` (`Canvas3DAlienExtension` / `Canvas3DAlienImpl`) powered by `:extensions:graphics:gpu`.
- [**`:extensions:graphics:threejs` & `:dsl`**](docs/threejs.md) — Pure Kotlin Multiplatform port of Three.js (`Scene`, `PerspectiveCamera`, `OrthographicCamera`, `WebGLRenderer`, `OrbitControls`, `TransformControls`, lights, PBR/shader materials, geometries, skeletons, raycasting, `GltfLoader`, `ObjLoader`).

### Category 2 — Physics (`:extensions:physics:*`)
- [**`:extensions:physics:box2d` & `:dsl`**](docs/box2d.md) — Deterministic 2D rigid-body physics (`World2D`, `BodyDef2D`, `Body2D`, shapes, joints, raycasting, `ContactListener2D`) and `Box2DDebugDrawer` integrated with `:extensions:graphics:canvas2d` & `canvas3d`.
- [**`:extensions:physics:box3d` & `:dsl`**](docs/box3d.md) — 3D rigid-body physics (`World3D`, `RigidBodyDesc3D`, `RigidBody3D`, shapes, joints, `CharacterController3D`, `Box3DDebugRenderer`) synchronized automatically with `:extensions:graphics:threejs` `Object3D` transforms.

### Category 3 — Game & Input (`:extensions:game:*`)
- [**`:extensions:game:gamepad`**](../docs/gamepad.md) — Unified Switch Pro/Joy-Con, DualSense/DualShock 4, Xbox, & HID controller input (`GamepadManager`, `ControllerHardwareProfile`, `GamepadLayoutMapper`), six-axis IMU fusion, vendor haptics/triggers/LEDs, and zero-config `enableGamepadUiNavigation(...)` (`GamepadUiNavigator`, zero `:dsl` module).
- [**`:extensions:game:engine`, `engine2d`, `engine3d` & `:dsl`**](../docs/game-engines.md) — Modular 3-layer game engine suite with dedicated logic tick thread (`PlatformGameLogicThread`), VBlank `AnimationScheduler` + 31 curves, `Skeleton2D` SVG rigging + `TwoBoneIkSolver2D`, pure-UI `UiStageHost`, `Game2DCanvasHost` (`Box2D`), and `Game3DCanvasHost` (`Three.js` + `Box3D`).

### Category 4 — Media & System Controls (`:extensions:media:*`)
- [**`:extensions:media:media` (`+ :dsl`, `:alien`)**](docs/media.md) — Cross-platform `VideoPlayer`, `VideoFragment : OverlayStageProvider`, `AudioPlayer`, FFT `AudioAnalyzer` (`AudioEnergyBands`), 5-SVG pure-Bezier `MediaVolumeIcons` (`com.nexustools.genericui.component.media.res.R`), `SeekBar : StyledSlider` (zero `SeekBarNativeImpl`, appending `.seek-bar-preload` `StyledComponent`), `ExpandingVolumeSlider : Composite` (mute/volume `StyledButton` + `StyledSlider` + 5-layer SVG stack), module `style.scss` + raw uncompiled `theme.scss` template (compiled solely in the consumer app's `:gradle-plugin` build against `$accent` into `GenericUiGeneratedThemeStyleSheet` / `gui/genericui-theme.css`), transparent codec introspection, `@SchemaData` **`VlcConfiguration`**, `VideoOverlayHost`, `YoutubeMediaOverlayHelper`, and `:extensions:media:media:alien` (`MediaAlienExtension` / `VideoPlayerAlienImpl`).
- [**`:extensions:media:mediacontrols`**](docs/mediacontrols.md) — Standalone OS media transport controls and hardware keyboard media key hooks (`MediaControlsSession`, `MediaControlMetadata`, `MediaControlsRegistry`, `DefaultPlatformMediaControlsFactory` across Windows SMTC / `ITaskbarList3`, macOS/iOS `MPNowPlayingInfoCenter` / `MPRemoteCommandCenter`, Linux MPRIS2, Swing, Android `MediaSession`, and Web `navigator.mediaSession`) depending **only on `:common`** (zero `:dsl` module).

### Category 5 — Data & Meta-Schema (`:extensions:data:*`)
- [**`:extensions:data:schema` & `:dsl`**](docs/schema.md) — Declarative meta-schema engine (`ObjectSchema`, 12 typed `SchemaFieldSpec`s + `SchemaGroupSpec` / `SchemaIndexedGroupSpec` / `SchemaAsyncGroupSpec` / `SchemaSeparatorSpec`, `SchemaCondition` `visibleWhen` / `enabledWhen`, `SchemaPreset`, `SchemaLayoutMode` `STACKED`/`RESPONSIVE_GRID`/`COMPACT_INLINE`/`TABBED`, `SchemaForm`, `SchemaModal`, `@SchemaData` + `@SchemaField(..., legacyKeys = ...)` compile-time codegen via `SchemaExtensionContributor`, `SchemaCustomFieldRenderer` / `SchemaRegistry`, `SchemaJsonCodec`, and `SchemaPreferenceCodec`).

### Category 6 — System, IPC, & Background Execution (`:extensions:system:*`)
- [**`:extensions:system:ipc`**](docs/ipc.md) — Target-scoped **Desktop + Android ONLY** IPC layer (zero `web`/`ios`/`ipados` targets; zero `:dsl` module) providing `IpcServer`, `IpcClient`, `IpcChannelAddress`, and length-framed `BinaryJson` `IpcPacket` transport (with `PreferenceElement.ofBytes` `TYPE_BYTES` payloads) over Win32 Named Pipes, `AF_UNIX` domain sockets, and macOS XPC, plus Android Service Binder helpers (`AndroidServiceIpcBinder`, `AndroidServiceIpcClient`, `AndroidBinderChunkAssembler` chunking > 240 KB).
- [**`:extensions:system:serviceworker`**](docs/serviceworker.md) — Persistent background service lifecycle & typed IPC (`ServiceWorker`, `@MainServiceWorker`, `ServiceWorkerOptions`, `ServiceWorkerExtension`, `ServiceWorkerFragmentBinding`, `@ServiceWorkerMessage` codegen + `ServiceWorkerCodecRegistry`, `ServiceWorkerChannel`, zero `:dsl` module). Runs as secondary singleton `serviceworker` executable (`serviceworker.exe`, `Contents/MacOS/serviceworker`, `serviceworker`, or Swing `GeneratedSwingServiceWorkerMain`) with tray & login autostart on Desktop, `GeneratedAndroidServiceWorkerService` (`ServiceCompat.startForeground` + default `"Stop"` notification button + conditional `BOOT_COMPLETED` receiver) on Android, and unified `./serviceworker.js` + `${appBaseName}.sw.js`/`${appBaseName}.sw.wasm` on Web, while **honestly reporting `ServiceWorkerExtension.isSupported == false` on iOS & iPadOS with zero simulation**.

### Category 7 — UI, Overlays, PiP, WebView, Editor, & AI (`:extensions:ui:*`)
- [**`:extensions:ui:dsl`**](docs/dsl.md) — `kotlinx.html`-inspired declarative UI builder DSL (`@GenericUiDslMarker`, `buildDiv`, `Container.build`), strictly isolated `style { ... }` (`ComponentStyle`), `bootstrap { ... }` (`ComponentBootstrap`), `important { ... }` (`DslStyleAndBootstrapBuilders.kt`), hardware `backdropFilter` on `StyledButton` + `StyledContainer`, hybrid responsive layouts (`HybridLayoutDsl.kt`), and MVC (`MvcModel`, `MvcView`, `MvcController`).
- [**`:extensions:ui:overlay` & `:dsl`**](docs/overlay.md) — Pure, background-free `LayeredContainer` overlay framework (`OverlayHost`, `OverlayHostBox`, `AbstractOverlay`, `FadingOverlay`, `Overlay`, `AutoHideController`, `OverlayInputRouter`) with `overlay/style.scss` (`.hidden` fade `@keyframes` driven by `Animator` / `SystemCssAnimationController` on `:system` and native CSS on `:web`) & raw uncompiled `theme.scss` template (packaged uncompiled in library metadata and compiled solely in the consumer app's `:gradle-plugin` build against `genericui { theme { ... } }` `$accent`), 9 pure-Bezier SVG icons (`com.nexustools.genericui.ext.overlay.res.R` via `OverlayIcons`), `OverlayControlButton` / `OverlayFullscreenButton` / `OverlayControlButtons`, 3-zone `YoutubePlayerOverlay`, and `HybridOverlayDifferenceController`.
- [**`:extensions:ui:pip`**](docs/pip.md) — Cross-platform Picture-in-Picture framework (`@PiPCompatible`, `FragmentPipHelper`, `FragmentPipRegistry`, `PipRegistry`, `PipControlOverlay` using `pip/style.scss` `.hidden` fade & `OverlayInputRouter`, `ComponentReparentSlot`, zero `:dsl` module) backed by `AndroidPipBackend`, `IosPipBackend` (`AVPictureInPictureController`), always-on-top `DesktopFloatingPipBackend` / `SwingFloatingPipBackend`, and `WebPipBackend` (`documentPictureInPicture` / native `<video>` PiP / in-place fallback).
- [**`:extensions:ui:alien` & `:dsl`**](docs/alien.md) — Self-rendering Bootstrap 5.3 component engine (`IAlien`, `com.nexustools.genericui.ext.alien.res.R.style.bootstrap`, `AlienPrecompiledBootstrapStyleSheet`, `AlienContainer`, `AlienFragment`, `AlienScrollFragment`, `AlienFragmentHostFragment`, `UnalienContainer`, `*AlienImpl`) supporting 100% of CSS properties & transitions over `Canvas2D` on System targets (auto-wiring `:extensions:graphics:{svg,canvas2d,canvas3d}:alien` & `:extensions:media:media:alien`) and zero-overhead DOM pass-through on Web.
- [**`:extensions:ui:webview` & `:dsl`**](docs/webview.md) — Hybrid embedded browser (`WebView`, `WebViewConfig`, `WebViewExtension`, `WebViewEngineType`) running **Alien + `:system` Stylesheet Engine + `svg:alien` + `canvas2d:alien` + `canvas3d:alien` + `media:alien` + [`:NJS` (`../lib/NJS/README.md`)](../lib/NJS/README.md)** inside an isolated per-instance OS worker subprocess over `:extensions:system:ipc` on Desktop/Swing (`${baseName}-webview(.exe)` / `<genPkg>.GeneratedSwingWebViewWorkerMain`), native `android.webkit.WebView` on Android, `WKWebView` on iOS/iPadOS, and sandboxed `<iframe>` (`WebNativeIframeWebViewImpl`) on Web.
- [**`:extensions:ui:codeeditor` & `:dsl`**](docs/codeeditor.md) — IDE code editor (`IdeCodeEditor`, `CodeDocumentBuffer`, `SyntaxTokenizer`, `CodeEditorTheme`, `CodeMinimap`) built atop `:extensions:graphics:canvas2d`.
- [**`:extensions:ui:aichat` & `:dsl`**](docs/aichat.md) — Multi-provider AI Studio chat & tool-orchestration UI (`AiChatContainer`, `AiChatSession`, `AiBackendType`, `ReasoningEffort`, `AiToolExecutor`) supporting `bindHeaderActionsToTitleBar` (`TitleCapabilities`).

---

## 2. Inter-Extension Dependency Graph

```mermaid
flowchart TD
    Common[":common<br/>(Widgets, IWindow, IFragment, StyledButton/StyledContainer w/ backdropFilter, ResourceID, ImageBuffer)"]

    Gpu[":extensions:graphics:gpu"]
    ImageProc[":extensions:graphics:imageprocessing"]
    Svg[":extensions:graphics:svg + :dsl + :alien"]
    Overlay[":extensions:ui:overlay + :dsl"]
    Schema[":extensions:data:schema + :dsl"]
    Ipc[":extensions:system:ipc (Desktop+Android)"]
    ServiceWorker[":extensions:system:serviceworker"]
    Pip[":extensions:ui:pip"]
    Canvas2D[":extensions:graphics:canvas2d + :dsl + :alien"]
    Canvas3D[":extensions:graphics:canvas3d + :dsl + :alien"]
    Media[":extensions:media:media + :dsl + :alien"]
    MediaControls[":extensions:media:mediacontrols"]
    Gamepad[":extensions:game:gamepad"]
    Engine[":extensions:game:engine + :dsl"]
    Engine2D[":extensions:game:engine2d + :dsl"]
    Engine3D[":extensions:game:engine3d + :dsl"]
    WebView[":extensions:ui:webview + :dsl"]
    CodeEditor[":extensions:ui:codeeditor + :dsl"]
    AiChat[":extensions:ui:aichat + :dsl"]
    ThreeJs[":extensions:graphics:threejs + :dsl"]
    Box2D[":extensions:physics:box2d + :dsl"]
    Box3D[":extensions:physics:box3d + :dsl"]
    Alien[":extensions:ui:alien + :dsl"]
    Dsl[":extensions:ui:dsl"]

    Gpu --> Common
    ImageProc --> Gpu
    Svg --> Common
    Overlay --> Common
    Schema --> Common
    Ipc --> Common
    ServiceWorker --> Common
    ServiceWorker -.->|Desktop + Android| Ipc
    Pip --> Overlay
    Media --> Overlay
    Media --> Schema
    MediaControls --> Common
    Gamepad --> Common
    Engine --> Gamepad
    Canvas2D --> ImageProc
    Canvas2D --> Overlay
    Canvas3D --> Gpu
    Canvas3D --> Canvas2D
    Canvas3D --> Overlay
    ThreeJs --> Canvas3D
    Box2D --> Canvas2D
    Box2D --> Canvas3D
    Box3D --> ThreeJs
    Engine2D --> Engine
    Engine2D --> Canvas2D
    Engine2D --> Box2D
    Engine3D --> Engine
    Engine3D --> ThreeJs
    Engine3D --> Box3D
    CodeEditor --> Canvas2D
    AiChat --> CodeEditor
    Alien --> Canvas2D
    WebView --> Common
    WebView -.->|Desktop Subprocess| Alien
    WebView -.->|Desktop Subprocess| Ipc
    Dsl --> Common
```

---

## 3. Consolidated Extension Catalog & Submodule Matrix

All **24 extensions** follow a single-project KMP layout under `extensions/<category>/<name>` (plus optional `:dsl` child project `extensions/<category>/<name>/dsl` and `:alien` child project `extensions/<category>/<name>/alien`):

| Extension Module | Companion `:dsl` / `:alien` Modules | Gradle DSL Function | Direct Extension & Core Dependencies | Dedicated Guide |
| :--- | :--- | :--- | :--- | :--- |
| `:extensions:graphics:gpu` | *(Context API)* | `gpu()` | `:common` | [`docs/gpu.md`](docs/gpu.md) |
| `:extensions:graphics:imageprocessing` | *(Pipeline API)* | `imageProcessing()` | `:extensions:graphics:gpu` | [`docs/imageprocessing.md`](docs/imageprocessing.md) |
| `:extensions:graphics:svg` | `:dsl`, `:alien` | `svg()` | `:common` | [`docs/svg.md`](docs/svg.md) |
| `:extensions:graphics:canvas2d` | `:dsl`, `:alien` | `canvas2d()` | `:extensions:graphics:imageprocessing`, `:extensions:ui:overlay` | [`docs/canvas2d.md`](docs/canvas2d.md) |
| `:extensions:graphics:canvas3d` | `:dsl`, `:alien` | `canvas3d()` | `:extensions:graphics:gpu`, `:extensions:graphics:canvas2d`, `:extensions:ui:overlay` | [`docs/canvas3d.md`](docs/canvas3d.md) |
| `:extensions:graphics:threejs` | `:dsl` | `threeJs()` | `:extensions:graphics:gpu`, `:extensions:graphics:canvas3d` | [`docs/threejs.md`](docs/threejs.md) |
| `:extensions:physics:box2d` | `:dsl` | `box2d()` | `:extensions:graphics:canvas2d`, `:extensions:graphics:canvas3d` | [`docs/box2d.md`](docs/box2d.md) |
| `:extensions:physics:box3d` | `:dsl` | `box3d()` | `:extensions:graphics:canvas3d`, `:extensions:graphics:threejs` | [`docs/box3d.md`](docs/box3d.md) |
| `:extensions:game:gamepad` | *(Core API only)* | `gamepad()` | `:common` | [`../docs/gamepad.md`](../docs/gamepad.md) |
| `:extensions:game:engine` | `:dsl` | `engine()` | `:common`, `:extensions:game:gamepad` | [`../docs/game-engines.md`](../docs/game-engines.md) |
| `:extensions:game:engine2d` | `:dsl` | `engine2d()` | `:extensions:game:engine`, `:extensions:graphics:canvas2d`, `:extensions:physics:box2d` | [`../docs/game-engines.md`](../docs/game-engines.md) |
| `:extensions:game:engine3d` | `:dsl` | `engine3d()` | `:extensions:game:engine`, `:extensions:graphics:threejs`, `:extensions:physics:box3d` | [`../docs/game-engines.md`](../docs/game-engines.md) |
| `:extensions:media:media` | `:dsl`, `:alien` | `media()` | `:extensions:ui:overlay`, `:extensions:data:schema` (+ `libvlc`/`vlcj` on Desktop) | [`docs/media.md`](docs/media.md) |
| `:extensions:media:mediacontrols` | *(Core API only)* | `mediaControls()` | `:common` | [`docs/mediacontrols.md`](docs/mediacontrols.md) |
| `:extensions:data:schema` | `:dsl` | `schema()` | `:common` | [`docs/schema.md`](docs/schema.md) |
| `:extensions:system:ipc` | *(Desktop + Android only)* | `ipc()` | `:common` | [`docs/ipc.md`](docs/ipc.md) |
| `:extensions:system:serviceworker` | *(Core API only)* | `serviceWorker()` / `serviceWorker { ... }` | `:common` (+ `:extensions:system:ipc` on Desktop/Android) | [`docs/serviceworker.md`](docs/serviceworker.md) |
| `:extensions:ui:dsl` | *(Base DSL Module)* | `dsl()` | `:common` | [`docs/dsl.md`](docs/dsl.md) |
| `:extensions:ui:overlay` | `:dsl` | `overlay()` | `:common` | [`docs/overlay.md`](docs/overlay.md) |
| `:extensions:ui:pip` | *(Core API only)* | `pip()` | `:extensions:ui:overlay`, `:common` | [`docs/pip.md`](docs/pip.md) |
| `:extensions:ui:alien` | `:dsl` | `alien()` | `:extensions:graphics:canvas2d` *(auto-adds `:alien` companions for active `svg`, `canvas2d`, `canvas3d`, `media`)* | [`docs/alien.md`](docs/alien.md) |
| `:extensions:ui:webview` | `:dsl` | `webView()` / `webView { ... }` | `:common` (+ Desktop subprocess: Alien, Stylesheet Engine, `svg:alien`, `canvas2d:alien`, `canvas3d:alien`, `media:alien`, `:ipc`, `:NJS`) | [`docs/webview.md`](docs/webview.md) |
| `:extensions:ui:codeeditor` | `:dsl` | `codeEditor()` | `:extensions:graphics:canvas2d` | [`docs/codeeditor.md`](docs/codeeditor.md) |
| `:extensions:ui:aichat` | `:dsl` | `aiChat()` | `:extensions:ui:codeeditor` | [`docs/aichat.md`](docs/aichat.md) |

---

## 4. Automatic Provider Wiring & Codegen via the GenericUI Gradle Plugin

```kotlin
plugins {
    id("com.nexustools.genericui")
}

genericui {
    app {
        id = "com.example.studio"
        name = "GenericUI Studio"
        version = "1.0.0"
    }

    backends {
        allExceptSwing()
    }

    extensions {
        dsl()              // Enables :extensions:ui:dsl + auto-wires every enabled UI/graphics/game/data :dsl
        gpu()
        imageProcessing()
        svg()
        overlay()
        pip()              // Auto-enables overlay()
        canvas2d()         // Auto-enables imageProcessing() + overlay()
        canvas3d()         // Auto-enables gpu() + canvas2d() + overlay()
        schema()           // @SchemaData codegen & SchemaForm / SchemaModal
        ipc()              // Target-scoped Desktop + Android IPC helpers (excluded from web/ios/ipados)
        serviceWorker {    // @MainServiceWorker + @ServiceWorkerMessage codegen
            autostart = true
            openWindowOnLaunch = true
        }
        media()            // Auto-enables overlay() + schema()
        mediaControls()
        gamepad()
        engine()           // Auto-enables gamepad()
        engine2d()         // Auto-enables engine() + canvas2d() + box2d()
        engine3d()         // Auto-enables engine() + threeJs() + box3d()
        webView()          // Auto-wires Desktop worker subprocess + Alien + IPC + capability companions
        codeEditor()
        aiChat()
        threeJs()
        box2d()
        box3d()
        alien()            // Auto-enables canvas2d() + matching :alien companions (svg/canvas2d/canvas3d/media)
    }
}
```

---

## 5. Cross-References across the GenericUI Ecosystem

- [GenericUI Root (`../README.md`)](../README.md) · [First-Party Extensions Guide (`../docs/extensions.md`)](../docs/extensions.md) · [Fragments & Windows (`../docs/fragments-and-windows.md`)](../docs/fragments-and-windows.md) · [Multi-Backend Resources (`../docs/resources.md`)](../docs/resources.md) · [Application Lifecycle (`../docs/application.md`)](../docs/application.md) · [Source Sets (`../docs/sourcesets.md`)](../docs/sourcesets.md) · [Native Backends (`../docs/backends.md`)](../docs/backends.md) · [Gradle Plugin (`../docs/gradle-plugin.md`)](../docs/gradle-plugin.md)
- [Desktop Backends (`../desktop/README.md`)](../desktop/README.md) · [Mobile Backends (`../mobile/README.md`)](../mobile/README.md) · [Web Backend (`../web/README.md`)](../web/README.md)
