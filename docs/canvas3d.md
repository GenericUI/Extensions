# `:extensions:canvas3d` & `:extensions:canvas3d:dsl` — Hardware-Accelerated Vulkan & OpenGL ES 3D Canvases, `ScrollCanvas3D`, & Overlay Hosts

> **Navigation**: [**GenericUI Root (`../../README.md`)**](../../README.md) · [**Extensions Index (`../README.md`)**](../README.md) · [**Root Extensions Guide (`../../docs/extensions.md`)**](../../docs/extensions.md) · [**Fragments & Windows (`../../docs/fragments-and-windows.md`)**](../../docs/fragments-and-windows.md) · [**Source Sets (`../../docs/sourcesets.md`)**](../../docs/sourcesets.md) · [**Backends (`../../docs/backends.md`)**](../../docs/backends.md) · [**Gradle Plugin (`../../docs/gradle-plugin.md`)**](../../docs/gradle-plugin.md)

`:extensions:canvas3d` is a **Multi-Module Project-of-Projects** extension (`:common`, `:systemCommon`, `:desktop:nativeCommon`, `:desktop:{windows,macos,gtk,qt,swing}`, `:mobile:common`, `:mobile:{android,ios}`, `:web`, plus `:dsl`) providing interactive, VBlank-synchronized 3D and programmable shader canvases—**`VKCanvasExt`** (Vulkan 1.2+ / MoltenVK / WebGPU), **`GLCanvasExt`** (OpenGL ES 3.0+ / Google ANGLE / LWJGL 3 / WebGL2), simulated viewport-only scrollable 3D canvases (**`ScrollCanvas3D`**, **`ScrollGLCanvas`**, **`ScrollVKCanvas`**), [`:extensions:overlay`](overlay.md) integration (**`Canvas3DOverlayHost`**, **`ScrollCanvas3DOverlayHost`**), and headless **`OffscreenCanvas3D`** rendering. Built directly atop [`:extensions:gpu`](gpu.md) and `:common`'s `AbstractCanvas`, it serves as the rendering viewport foundation for [`:extensions:threejs`](threejs.md) and [`:extensions:box3d`](box3d.md).

---

## 1. Architecture & Platform Swapchain Matrix

```mermaid
flowchart TD
    Canvas3D["Canvas3DExtension<br/>(:extensions:canvas3d:common)"]
    VKCanvas["VKCanvasExt (Vulkan 1.2+ Swapchain)<br/>registerVBlankRender { vkCtx, deltaMs, elapsedMs, frameIdx -> ... }"]
    GLCanvas["GLCanvasExt (OpenGL ES 3.0+ Swapchain)<br/>registerVBlankRender { glCtx, deltaMs, elapsedMs, frameIdx -> ... }"]
    Scroll3D["ScrollCanvas3D / ScrollGLCanvas / ScrollVKCanvas<br/>(Viewport-Only 3D Swapchain + Simulated Scroll)"]
    OverlayHosts["Canvas3DOverlayHost & ScrollCanvas3DOverlayHost<br/>(:extensions:overlay Integration)"]
    Offscreen["OffscreenCanvas3D<br/>(Headless 3D Render-to-ImageBuffer)"]
    Gpu[":extensions:gpu<br/>(GpuDevice, GpuOffscreenContext, VKContext, GLContext)"]

    Canvas3D --> VKCanvas
    Canvas3D --> GLCanvas
    Canvas3D --> Scroll3D
    Canvas3D --> Offscreen
    VKCanvas --> OverlayHosts
    GLCanvas --> OverlayHosts
    Scroll3D --> OverlayHosts
    VKCanvas --> Gpu
    GLCanvas --> Gpu
    Scroll3D --> Gpu
    Offscreen --> Gpu
```

| Target Backend | Submodules | `VKCanvasExt` Backend | `GLCanvasExt` Backend | Backend Documentation |
| :--- | :--- | :--- | :--- | :--- |
| **Windows (`:windows`)** | `:extensions:canvas3d:desktop:windows` + `:desktop:nativeCommon` | Native `VkWin32SurfaceKHR` (`vulkan-1.dll`) | **Google ANGLE** (`libEGL.dll` + `libGLESv2.dll` over Direct3D 11) | [Desktop Backends (`../../desktop/README.md`)](../../desktop/README.md) |
| **macOS (`:macos`)** | `:extensions:canvas3d:desktop:macos` + `:desktop:nativeCommon` | **MoltenVK** (`VK_EXT_metal_surface` over `CAMetalLayer`) | Metal-backed OpenGL ES / `NSOpenGLView` | [Desktop Backends (`../../desktop/README.md`)](../../desktop/README.md) |
| **Linux GTK4 (`:gtk`)** | `:extensions:canvas3d:desktop:gtk` + `:desktop:nativeCommon` | Native `VkWaylandSurfaceKHR` / `VkXlibSurfaceKHR` | Native EGL / `GtkGLArea` OpenGL ES 3.0+ | [Desktop Backends (`../../desktop/README.md`)](../../desktop/README.md) |
| **Linux Qt6 (`:qt`)** | `:extensions:canvas3d:desktop:qt` + `:desktop:nativeCommon` | Qt6 `QVulkanWindow` / native Vulkan surface | Qt6 `QOpenGLWidget` OpenGL ES 3.0+ | [Desktop Backends (`../../desktop/README.md`)](../../desktop/README.md) |
| **Desktop JVM (`:swing`)** | `:extensions:canvas3d:desktop:swing` | **LWJGL 3 Vulkan** (`SwingVKCanvasImpl`) | **LWJGL 3 OpenGL ES** (`SwingGLCanvasImpl`) | [Desktop Backends (`../../desktop/README.md`)](../../desktop/README.md) |
| **Android (`:android`)** | `:extensions:canvas3d:mobile:android` + `:mobile:common` | `SurfaceView` / `ANativeWindow` Vulkan 1.1+ (`AndroidVKCanvasImpl`) | `GLSurfaceView` EGL14 / GLES 3.0 (`AndroidGLCanvasImpl`) | [Mobile Backends (`../../mobile/README.md`)](../../mobile/README.md) |
| **iOS (`:ios`)** | `:extensions:canvas3d:mobile:ios` + `:mobile:common` | **MoltenVK** over `CAMetalLayer` (`IosVKCanvasImpl`) | `GLKView` / `CAEAGLLayer` OpenGL ES 3.0 (`IosGLCanvasImpl`) | [Mobile Backends (`../../mobile/README.md`)](../../mobile/README.md) |
| **Web (`:web`)** | `:extensions:canvas3d:web` | **WebGPU** `<canvas>` (`WebVKCanvasImpl`) | **WebGL2** `<canvas>` (`WebGLCanvasImpl`) | [Web Backend (`../../web/README.md`)](../../web/README.md) |

---

## 2. Gradle Plugin Configuration

Enabling `canvas3d()` in the [GenericUI Gradle Plugin (`../../docs/gradle-plugin.md`)](../../docs/gradle-plugin.md) automatically configures `:extensions:canvas3d`, `:extensions:gpu`, `:extensions:canvas2d`, and `:extensions:overlay`, registers the platform `Canvas3D` provider in generated launchers, and (when `dsl()` is active) includes `:extensions:canvas3d:dsl`:

```kotlin
plugins {
    id("com.nexustools.genericui")
}

genericui {
    app {
        id = "com.example.shaderstudio"
        name = "3D Shader Studio"
    }
    backends {
        allExceptSwing()
    }
    extensions {
        dsl()      // Enables :extensions:dsl + :extensions:canvas3d:dsl + :extensions:overlay:dsl
        canvas3d() // Automatically includes :extensions:canvas3d, :extensions:gpu, :extensions:canvas2d, and :extensions:overlay
    }
}
```

---

## 3. Core API Reference (`com.nexustools.genericui.ext.canvas3d` & `ext.gpu`)

| Class / Function | Package | Description & Key Members |
| :--- | :--- | :--- |
| `Canvas3DExtension` | `ext.canvas3d` | Singleton coordinator for 3D context creation (`createGLContext(config, width, height)`, `createVKContext(config, width, height)`, `acquireGpuOffscreenContext(preferVulkan)`). |
| `GLCanvasExt` | `ext.canvas3d` | Extended OpenGL ES 3.0+ / Google ANGLE 3D canvas widget extending `GLCanvas`. Key members: `config: GLCanvasConfig`, `gpuContext: GpuOffscreenContext`, `uploadImageBufferTexture(imageBuffer): GLTexture`, `uploadGpuTexture(imageBuffer): GpuTexture`, `registerVBlankRender { glCtx, deltaMs, elapsedMs, frameIdx -> ... }`, `attachOverlay(overlay): Canvas3DOverlayHost`, `withOverlay(overlay): Canvas3DOverlayHost`, `resolveRenderingContext(): GLContext?`. |
| `VKCanvasExt` | `ext.canvas3d` | Extended Vulkan 1.2+ / MoltenVK / WebGPU 3D canvas widget extending `VKCanvas`. Key members: `config: VKCanvasConfig`, `gpuContext: GpuOffscreenContext`, `uploadImageBufferTexture(imageBuffer): Long`, `uploadGpuTexture(imageBuffer): GpuTexture`, `registerVBlankRender { vkCtx, deltaMs, elapsedMs, frameIdx -> ... }`, `attachOverlay(overlay): Canvas3DOverlayHost`, `withOverlay(overlay): Canvas3DOverlayHost`, `resolveRenderingContext(): VKContext?`. |
| `OffscreenCanvas3D` | `ext.canvas3d` | Headless 3D rendering surface backed by `:extensions:gpu` (`width`, `height`, `preferVulkan`, `renderToImageBuffer { gpuCtx -> ... }: ImageBuffer`). |
| `ScrollCanvas3D` / `ScrollGLCanvas` / `ScrollVKCanvas` | `ext.canvas3d` | Scrollable 3D canvases with virtual `contentWidth` x `contentHeight` where the physical 3D swapchain only fills `viewportWidth` x `viewportHeight` and scrolling is simulated via `onScroll`. Key methods: `setViewportSize`, `setContentSize`, `scrollTo`, `scrollBy`, `onScroll`, `attachOverlay(overlay): ScrollCanvas3DOverlayHost`, `withOverlay(overlay): ScrollCanvas3DOverlayHost`. |
| `ScrollCanvas3DListener` | `ext.canvas3d` | `fun interface` (`onScrollChanged(scrollX, scrollY, viewportWidth, viewportHeight, contentWidth, contentHeight)`) invoked whenever simulated scroll position or dimensions change. |
| `Canvas3DOverlayHost` | `ext.canvas3d.overlay` | `OverlayHostBox` subclass wrapping a `GLCanvasExt`, `VKCanvasExt`, or 3D component (`glCanvas`, `vkCanvas`, `resizeCanvas(width, height)`, `syncOverlayBoundsToCanvas()`). |
| `ScrollCanvas3DOverlayHost` | `ext.canvas3d.overlay` | `OverlayHostBox` subclass wrapping a `ScrollCanvas3D` (`ScrollGLCanvas` / `ScrollVKCanvas`) where `overlayRoot` covers only the viewport area (`overlayCoversScrollbars == false`). |
| `GLCanvasConfig` | `ext.gpu` | Configuration for `GLCanvasExt`: `versionMajor: Int = 3`, `versionMinor: Int = 0`, `depthBits: Int = 24`, `stencilBits: Int = 8`, `msaaSamples: Int = 4`, `vsync: Boolean = true`, `preferAngleOnWindows: Boolean = true`. |
| `VKCanvasConfig` | `ext.gpu` | Configuration for `VKCanvasExt`: `enableValidationLayers: Boolean = false`, `preferDiscreteGpu: Boolean = true`, `srgbSwapchain: Boolean = true`, `depthFormatBits: Int = 32`, `msaaSamples: Int = 4`, `vsync: Boolean = true`. |
| `glCanvasExt` / `glCanvas` / `vkCanvasExt` / `vkCanvas` | `ext.canvas3d.dsl` | Declarative DSL builders in `:extensions:canvas3d:dsl` for mounting `GLCanvasExt`, `GLCanvas`, `VKCanvasExt`, and `VKCanvas`. |
| `canvas3DOverlayHost` / `glCanvasOverlayHost` / `vkCanvasOverlayHost` | `ext.canvas3d.dsl` | Declarative DSL builders in `:extensions:canvas3d:dsl` for mounting `Canvas3DOverlayHost` over 3D canvases. |
| `scrollCanvas3D` / `scrollGlCanvas` / `scrollVkCanvas` / `scrollCanvas3DOverlayHost` / `scrollGlCanvasOverlayHost` / `scrollVkCanvasOverlayHost` | `ext.canvas3d.dsl` | Declarative DSL builders in `:extensions:canvas3d:dsl` for mounting scrollable 3D canvases and viewport-only `ScrollCanvas3DOverlayHost` containers. |

---

## 3.1 `ScrollCanvas3D` & Overlay Hosts (`Canvas3DOverlayHost`, `ScrollCanvas3DOverlayHost`)

1. **Viewport-Only 3D Swapchain (`ScrollCanvas3D`, `ScrollGLCanvas`, `ScrollVKCanvas`)**:
   - Implements a scrollable 3D canvas with a configurable virtual content size (`contentWidth` x `contentHeight`), while **the actual 3D `<canvas>` swapchain only fills the visible viewport (`viewportWidth` x `viewportHeight`) and scrolling is simulated** via `ScrollCanvas3DListener`.
   - On **Web (`:extensions:canvas3d:web` — `WebScrollCanvas3DImpl`)**, renders `<genericui:scrollcanvas3d>` with `position: relative`, an inner `<div>` sized to `contentWidth` x `contentHeight`, and an HTML `<canvas>` positioned at `left: 0; top: 0` whose WebGPU/WebGL2 swapchain only allocates `viewportWidth` x `viewportHeight`.
   - On **System targets (`:desktop`, `:mobile`, `:swing`)**, composes the viewport-sized 3D canvas (`GLCanvasExt` or `VKCanvasExt`) with `rightScrollBar: RangeSlider` and `bottomScrollBar: RangeSlider`.
2. **Overlay Integration (`Canvas3DOverlayHost` & `ScrollCanvas3DOverlayHost`)**:
   - `GLCanvasExt.attachOverlay(overlay)` and `VKCanvasExt.attachOverlay(overlay)` wrap the 3D canvas in a `Canvas3DOverlayHost`.
   - `ScrollCanvas3D.attachOverlay(overlay)` wraps the scrollable 3D canvas in a `ScrollCanvas3DOverlayHost` whose `overlayRoot` covers **only the viewport area (`viewportWidth` x `viewportHeight`)**, excluding scrollbars.
   - No default overlay is attached unless explicitly supplied by the consumer.

---

## 4. Imperative Kotlin Examples

### Example 1: OpenGL ES 3.0 / Google ANGLE Shader Rendering with `GLCanvasExt`

```kotlin
import com.nexustools.genericui.ext.canvas3d.GLCanvasExt
import com.nexustools.genericui.ext.gpu.GLCanvasConfig
import com.nexustools.genericui.ext.gpu.GLContext
import com.nexustools.genericui.image.ImageBuffer

fun createCustomShaderGlCanvas(albedoImage: ImageBuffer): GLCanvasExt {
    val config = GLCanvasConfig(
        versionMajor = 3,
        versionMinor = 0,
        depthBits = 24,
        stencilBits = 8,
        msaaSamples = 4,
        preferAngleOnWindows = true
    )

    val canvas = GLCanvasExt(config = config, width = 1024, height = 576)

    // Upload a :common ImageBuffer directly as an OpenGL ES 2D texture
    val glTexture = canvas.uploadImageBufferTexture(albedoImage)

    // Register a VBlank-synchronized render loop receiving deltaMs and elapsedMs
    canvas.registerVBlankRender { gl, _, _, _ ->
        gl.viewport(0, 0, canvas.surfaceWidth, canvas.surfaceHeight)
        gl.clearColor(0.06f, 0.09f, 0.16f, 1.0f)
        gl.clear(GLContext.GL_COLOR_BUFFER_BIT or GLContext.GL_DEPTH_BUFFER_BIT)

        // Bind texture and issue draw calls
        gl.bindTexture(GLContext.GL_TEXTURE_2D, glTexture)
        gl.drawArrays(mode = GLContext.GL_TRIANGLES, first = 0, count = 3)
    }

    return canvas
}
```

### Example 2: Vulkan 1.2+ / MoltenVK / WebGPU Swapchain with an Interactive Overlay

```kotlin
import com.nexustools.genericui.ext.canvas3d.VKCanvasExt
import com.nexustools.genericui.ext.canvas3d.overlay.Canvas3DOverlayHost
import com.nexustools.genericui.ext.gpu.VKCanvasConfig
import com.nexustools.genericui.ext.overlay.OverlayPresetResolver

fun createVulkanViewportWithOverlay(): Canvas3DOverlayHost {
    val vkCanvas = VKCanvasExt(
        config = VKCanvasConfig(preferDiscreteGpu = true, srgbSwapchain = true, vsync = true),
        width = 1280,
        height = 720
    )

    vkCanvas.registerVBlankRender { vkCtx, _, _, _ ->
        vkCtx.beginFrame()
        vkCtx.setClearColor(0.05f, 0.07f, 0.12f, 1.0f)
        vkCtx.endFrameAndPresent()
    }

    val overlay = OverlayPresetResolver.resolveForBackend()
    return vkCanvas.attachOverlay(overlay).apply {
        updateState { it.copy(title = "Vulkan 1.2+ Real-Time Visualizer", isPlaying = true) }
    }
}
```

---

## 5. Declarative `:extensions:canvas3d:dsl` Examples

Use `glCanvasExt { ... }`, `vkCanvasExt { ... }`, `glCanvasOverlayHost { ... }`, and `vkCanvasOverlayHost { ... }` inside any [`:extensions:dsl`](dsl.md) layout container:

```kotlin
import com.nexustools.genericui.ext.canvas3d.dsl.glCanvasExt
import com.nexustools.genericui.ext.canvas3d.dsl.vkCanvasOverlayHost
import com.nexustools.genericui.ext.dsl.ColorRgba
import com.nexustools.genericui.ext.dsl.buildDiv
import com.nexustools.genericui.ext.dsl.col
import com.nexustools.genericui.ext.dsl.h4
import com.nexustools.genericui.ext.dsl.row
import com.nexustools.genericui.ext.gpu.GLCanvasConfig
import com.nexustools.genericui.ext.gpu.GLContext
import com.nexustools.genericui.ext.gpu.VKCanvasConfig
import com.nexustools.genericui.ext.overlay.dsl.defaultOverlayPreset

val dual3DViewportComparison = buildDiv(classes = "p-3") {
    row(classes = "gap-3") {
        col(span = 6) {
            h4("Vulkan 1.2+ / MoltenVK / WebGPU Primary Viewport + Overlay")
            vkCanvasOverlayHost(
                config = VKCanvasConfig(preferDiscreteGpu = true, srgbSwapchain = true),
                width = 600,
                height = 400,
                onRender = { vk, _, _, _ ->
                    vk.beginFrame()
                    vk.setClearColor(0.08f, 0.12f, 0.22f, 1.0f)
                    vk.endFrameAndPresent()
                }
            ) {
                defaultOverlayPreset()
                updateState { it.copy(title = "Vulkan Primary Swapchain") }
            }
        }

        col(span = 6) {
            h4("OpenGL ES 3.0 / Google ANGLE Fallback Viewport")
            glCanvasExt(
                config = GLCanvasConfig(msaaSamples = 4, preferAngleOnWindows = true),
                width = 600,
                height = 400,
                onRender = { gl, _, _, _ ->
                    gl.clearColor(0.08f, 0.18f, 0.14f, 1.0f)
                    gl.clear(GLContext.GL_COLOR_BUFFER_BIT or GLContext.GL_DEPTH_BUFFER_BIT)
                }
            ) {
                style {
                    borderRadiusPx = 8f
                    borderWidthPx = 1f
                    borderColor = ColorRgba(16, 185, 129, 0.6)
                }
            }
        }
    }
}
```

---

## 6. Related Extensions & Cross-References

- **Related Extensions**:
  - [`:extensions:gpu` (`gpu.md`)](gpu.md) — Underlying GPU device, `VKContext`, `GLContext`, and shader compilation foundation.
  - [`:extensions:overlay` (`overlay.md`)](overlay.md) — Universal domain-agnostic overlay framework (`OverlayHost`, `MobileOverlayPreset`, `WebSystemOverlayPreset`).
  - [`:extensions:threejs` (`threejs.md`)](threejs.md) — High-level 3D scene graph (`Scene`, `PerspectiveCamera`, `WebGLRenderer`, PBR materials, lights, `OrbitControls`) rendered onto `GLCanvasExt` and `VKCanvasExt`.
  - [`:extensions:box3d` (`box3d.md`)](box3d.md) — 3D rigid-body physics engine with `Box3DDebugRenderer` bound to `GLCanvasExt`.
  - [`:extensions:canvas2d` (`canvas2d.md`)](canvas2d.md) — Sister 2D vector/raster canvas extension (`Canvas2DExtSurface`, `ScrollCanvas2D`, `CurveEditorExt`).
  - [`:extensions:dsl` (`dsl.md`)](dsl.md) — Base declarative Kotlin UI & Style DSL.
- **Root & Backend Documentation**:
  - [GenericUI Root (`../../README.md`)](../../README.md) · [Extensions Architecture (`../../docs/extensions.md`)](../../docs/extensions.md) · [Source Sets (`../../docs/sourcesets.md`)](../../docs/sourcesets.md) · [Backends (`../../docs/backends.md`)](../../docs/backends.md) · [Gradle Plugin (`../../docs/gradle-plugin.md`)](../../docs/gradle-plugin.md)
  - [Desktop Backends (`../../desktop/README.md`)](../../desktop/README.md) · [Mobile Backends (`../../mobile/README.md`)](../../mobile/README.md) · [Web Backend (`../../web/README.md`)](../../web/README.md)
