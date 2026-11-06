# `:extensions:canvas3d` & `:extensions:canvas3d:dsl` — Unified Hardware-Accelerated Vulkan & OpenGL ES 3D Canvas (`Canvas3D`), `ScrollCanvas3D`, & Overlay Hosts

> **Navigation**: [**GenericUI Root (`../../README.md`)**](../../README.md) · [**Extensions Index (`../README.md`)**](../README.md) · [**Root Extensions Guide (`../../docs/extensions.md`)**](../../docs/extensions.md) · [**Fragments & Windows (`../../docs/fragments-and-windows.md`)**](../../docs/fragments-and-windows.md) · [**Source Sets (`../../docs/sourcesets.md`)**](../../docs/sourcesets.md) · [**Backends (`../../docs/backends.md`)**](../../docs/backends.md) · [**Gradle Plugin (`../../docs/gradle-plugin.md`)**](../../docs/gradle-plugin.md)

`:extensions:canvas3d` is a **Multi-Module Project-of-Projects** extension (`:common`, `:systemCommon`, `:desktop:nativeCommon`, `:desktop:{windows,macos,gtk,qt,swing}`, `:mobile:common`, `:mobile:{android,ios}`, `:web`, plus `:dsl`) providing a unified, interactive, VBlank-synchronized 3D and programmable shader canvas—**`Canvas3D`** (`Canvas3DConfig`, `Canvas3DRegistry`) supporting both **`VKContext`** (Vulkan 1.2+ / MoltenVK / WebGPU) and **`GLContext`** (OpenGL ES 3.0+ / Google ANGLE / LWJGL 3 / WebGL2), simulated viewport-only scrollable 3D canvases (**`ScrollCanvas3D`**), [`:extensions:overlay`](overlay.md) integration (**`Canvas3DOverlayHost`**, **`ScrollCanvas3DOverlayHost`**), and headless **`OffscreenCanvas3D`** rendering. Built directly atop [`:extensions:gpu`](gpu.md) and `:common`'s `AbstractCanvas`, it serves as the rendering viewport foundation for [`:extensions:threejs`](threejs.md) and [`:extensions:box3d`](box3d.md).

---

## 1. Architecture & Platform Swapchain Matrix

```mermaid
flowchart TD
    Canvas3DExt["Canvas3DExtension & Canvas3DRegistry<br/>(:extensions:canvas3d:common)"]
    Canvas3D["Canvas3D (Unified 3D Canvas)<br/>getContext(GLContextAttributes) / getContext(VKDeviceAttributes)"]
    Scroll3D["ScrollCanvas3D<br/>(Viewport-Only 3D Swapchain + Simulated Scroll)"]
    OverlayHosts["Canvas3DOverlayHost & ScrollCanvas3DOverlayHost<br/>(:extensions:overlay Integration)"]
    Offscreen["OffscreenCanvas3D<br/>(Headless 3D Render-to-ImageBuffer)"]
    Gpu[":extensions:gpu<br/>(GpuDevice, GpuOffscreenContext, VKContext, GLContext)"]

    Canvas3DExt --> Canvas3D
    Canvas3DExt --> Scroll3D
    Canvas3DExt --> Offscreen
    Canvas3D --> OverlayHosts
    Scroll3D --> OverlayHosts
    Canvas3D --> Gpu
    Scroll3D --> Gpu
    Offscreen --> Gpu
```

| Target Backend | Submodules | `VKContext` Backend (`Canvas3D`) | `GLContext` Backend (`Canvas3D`) | Backend Documentation |
| :--- | :--- | :--- | :--- | :--- |
| **Windows (`:windows`)** | `:extensions:canvas3d:desktop:windows` + `:desktop:nativeCommon` | Native `VkWin32SurfaceKHR` (`vulkan-1.dll`) | **Google ANGLE** (`libEGL.dll` + `libGLESv2.dll` over Direct3D 11) | [Desktop Backends (`../../desktop/README.md`)](../../desktop/README.md) |
| **macOS (`:macos`)** | `:extensions:canvas3d:desktop:macos` + `:desktop:nativeCommon` | **MoltenVK** (`VK_EXT_metal_surface` over `CAMetalLayer`) | Metal-backed OpenGL ES / `NSOpenGLView` | [Desktop Backends (`../../desktop/README.md`)](../../desktop/README.md) |
| **Linux GTK4 (`:gtk`)** | `:extensions:canvas3d:desktop:gtk` + `:desktop:nativeCommon` | Native `VkWaylandSurfaceKHR` / `VkXlibSurfaceKHR` | Native EGL / `GtkGLArea` OpenGL ES 3.0+ | [Desktop Backends (`../../desktop/README.md`)](../../desktop/README.md) |
| **Linux Qt6 (`:qt`)** | `:extensions:canvas3d:desktop:qt` + `:desktop:nativeCommon` | Qt6 `QVulkanWindow` / native Vulkan surface | Qt6 `QOpenGLWidget` OpenGL ES 3.0+ | [Desktop Backends (`../../desktop/README.md`)](../../desktop/README.md) |
| **Desktop JVM (`:swing`)** | `:extensions:canvas3d:desktop:swing` | **LWJGL 3 Vulkan** (`SwingCanvas3DImpl`) | **LWJGL 3 OpenGL ES** (`SwingCanvas3DImpl`) | [Desktop Backends (`../../desktop/README.md`)](../../desktop/README.md) |
| **Android (`:android`)** | `:extensions:canvas3d:mobile:android` + `:mobile:common` | `SurfaceView` / `ANativeWindow` Vulkan 1.1+ (`AndroidCanvas3DImpl`) | `GLSurfaceView` EGL14 / GLES 3.0 (`AndroidCanvas3DImpl`) | [Mobile Backends (`../../mobile/README.md`)](../../mobile/README.md) |
| **iOS (`:ios`)** | `:extensions:canvas3d:mobile:ios` + `:mobile:common` | **MoltenVK** over `CAMetalLayer` (`IosCanvas3DImpl`) | `GLKView` / `CAEAGLLayer` OpenGL ES 3.0 (`IosCanvas3DImpl`) | [Mobile Backends (`../../mobile/README.md`)](../../mobile/README.md) |
| **Web (`:web`)** | `:extensions:canvas3d:web` | **WebGPU** `<canvas>` (`WebCanvas3DImpl`) | **WebGL2** `<canvas>` (`WebCanvas3DImpl`) | [Web Backend (`../../web/README.md`)](../../web/README.md) |

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

## 3. Core API Reference (`com.nexustools.genericui.component.media` & `ext.canvas3d`)

| Class / Function | Package | Description & Key Members |
| :--- | :--- | :--- |
| `Canvas3D` | `component.media` | Unified 3D canvas widget extending `AbstractCanvas`. Key members: `config: Canvas3DConfig`, `activeBackendType: GpuBackendType?`, `activeContext: GpuRenderContext?`, `glContext: GLContext?`, `vkContext: VKContext?`, `getContext(attributes: GLContextAttributes): GLContext`, `getContext(attributes: VKDeviceAttributes): VKContext`, `getContext(preferredBackend: GpuBackendType): GpuRenderContext`, `uploadImageBufferTexture(imageBuffer): GpuTexture`, `registerVBlankRender { ctx, deltaMs, elapsedMs, frameIdx -> ... }`, `attachOverlay(overlay): Canvas3DOverlayHost`, `withOverlay(overlay): Canvas3DOverlayHost`, `capturePixels(): ImageBuffer`. |
| `Canvas3DConfig` | `component.media` | Unified configuration for `Canvas3D` and `ScrollCanvas3D`: `accelerationPolicy: GpuAccelerationPolicy = GpuAccelerationPolicy.PREFER_HARDWARE`, `preferredBackend: GpuBackendType? = null`, `glAttributes: GLContextAttributes = GLContextAttributes()`, `vkAttributes: VKDeviceAttributes = VKDeviceAttributes()`, `vsync: Boolean = true`. |
| `Canvas3DRegistry` | `component.media` | Singleton registry for the active backend's `Canvas3DNativeImpl` and `ScrollCanvas3DNativeImpl` providers (`requireCanvas3D`, `requireScrollCanvas3D`). |
| `Canvas3DExtension` | `ext.canvas3d` | Singleton coordinator for 3D context creation (`createGLContext(attributes, width, height, policy)`, `createVKContext(attributes, width, height, policy)`, `acquireGpuOffscreenContext(preferVulkan, policy)`). |
| `OffscreenCanvas3D` | `ext.canvas3d` | Headless 3D rendering surface backed by `:extensions:gpu` (`width`, `height`, `preferVulkan`, `accelerationPolicy`, `renderToImageBuffer { gpuCtx -> ... }: ImageBuffer`). |
| `ScrollCanvas3D` | `ext.canvas3d` | Scrollable 3D canvas with virtual `contentWidth` x `contentHeight` where the physical 3D swapchain only fills `viewportWidth` x `viewportHeight` and scrolling is simulated via `onScroll`. Key methods: `getContext(GLContextAttributes)`, `getContext(VKDeviceAttributes)`, `setViewportSize`, `setContentSize`, `scrollTo`, `scrollBy`, `onScroll`, `attachOverlay(overlay): ScrollCanvas3DOverlayHost`, `withOverlay(overlay): ScrollCanvas3DOverlayHost`. |
| `ScrollCanvas3DListener` | `ext.canvas3d` | `fun interface` (`onScrollChanged(scrollX, scrollY, viewportWidth, viewportHeight, contentWidth, contentHeight)`) invoked whenever simulated scroll position or dimensions change. |
| `Canvas3DOverlayHost` | `ext.canvas3d.overlay` | `OverlayHostBox` subclass wrapping a `Canvas3D` or 3D component (`canvas3D`, `resizeCanvas(width, height)`, `syncOverlayBoundsToCanvas()`). |
| `ScrollCanvas3DOverlayHost` | `ext.canvas3d.overlay` | `OverlayHostBox` subclass wrapping a `ScrollCanvas3D` where `overlayRoot` covers only the viewport area (`overlayCoversScrollbars == false`). |
| `canvas3D` / `canvas3DGL` / `canvas3DVK` | `ext.canvas3d.dsl` | Declarative DSL builders in `:extensions:canvas3d:dsl` for mounting `Canvas3D` with generic, `GLContext`, or `VKContext` render loops. |
| `canvas3DOverlayHost` | `ext.canvas3d.dsl` | Declarative DSL builders in `:extensions:canvas3d:dsl` for mounting `Canvas3DOverlayHost` over `Canvas3D`. |
| `scrollCanvas3D` / `scrollCanvas3DOverlayHost` | `ext.canvas3d.dsl` | Declarative DSL builders in `:extensions:canvas3d:dsl` for mounting `ScrollCanvas3D` and viewport-only `ScrollCanvas3DOverlayHost` containers. |

---

## 3.1 `ScrollCanvas3D` & Overlay Hosts (`Canvas3DOverlayHost`, `ScrollCanvas3DOverlayHost`)

1. **Viewport-Only 3D Swapchain (`ScrollCanvas3D`)**:
   - Implements a scrollable 3D canvas with a configurable virtual content size (`contentWidth` x `contentHeight`), while **the actual 3D `<canvas>` swapchain only fills the visible viewport (`viewportWidth` x `viewportHeight`) and scrolling is simulated** via `ScrollCanvas3DListener`.
   - On **Web (`:extensions:canvas3d:web` — `WebScrollCanvas3DImpl`)**, renders `<genericui:scrollcanvas3d>` with `position: relative`, an inner `<div>` sized to `contentWidth` x `contentHeight`, and an HTML `<canvas>` positioned at `left: 0; top: 0` whose WebGPU/WebGL2 swapchain only allocates `viewportWidth` x `viewportHeight`.
   - On **System targets (`:desktop`, `:mobile`, `:swing`)**, composes the viewport-sized `Canvas3D` with `rightScrollBar: RangeSlider` and `bottomScrollBar: RangeSlider`.
2. **Overlay Integration (`Canvas3DOverlayHost` & `ScrollCanvas3DOverlayHost`)**:
   - `Canvas3D.attachOverlay(overlay)` wraps the 3D canvas in a `Canvas3DOverlayHost`.
   - `ScrollCanvas3D.attachOverlay(overlay)` wraps the scrollable 3D canvas in a `ScrollCanvas3DOverlayHost` whose `overlayRoot` covers **only the viewport area (`viewportWidth` x `viewportHeight`)**, excluding scrollbars.
   - No default overlay is attached unless explicitly supplied by the consumer.

---

## 4. Imperative Kotlin Examples

### Example 1: OpenGL ES 3.0 / Google ANGLE Shader Rendering with `Canvas3D` (`GLContext`)

```kotlin
import com.nexustools.genericui.component.media.Canvas3D
import com.nexustools.genericui.component.media.Canvas3DConfig
import com.nexustools.genericui.component.media.GLClearBuffer
import com.nexustools.genericui.component.media.GLContextAttributes
import com.nexustools.genericui.component.media.GLPrimitive
import com.nexustools.genericui.component.media.GLTextureTarget
import com.nexustools.genericui.image.ImageBuffer

fun createCustomShaderGlCanvas(albedoImage: ImageBuffer): Canvas3D {
    val config = Canvas3DConfig(
        glAttributes = GLContextAttributes(
            depth = true,
            stencil = true,
            antialias = true,
            msaaSamples = 4,
            preferAngleOnWindows = true
        )
    )

    val canvas = Canvas3D(config = config, width = 1024, height = 576)
    val gl = canvas.getContext(config.glAttributes)

    // Upload a :common ImageBuffer directly as an OpenGL ES 2D texture
    val glTexture = gl.createTexture()
    gl.bindTexture(GLTextureTarget.TEXTURE_2D, glTexture)
    gl.texImage2D(GLTextureTarget.TEXTURE_2D, 0, albedoImage)

    // Register a VBlank-synchronized render loop receiving deltaMs and elapsedMs
    canvas.registerVBlankRender { _, _, _, _ ->
        gl.viewport(0, 0, canvas.surfaceWidth, canvas.surfaceHeight)
        gl.clearColor(0.06f, 0.09f, 0.16f, 1.0f)
        gl.clear(setOf(GLClearBuffer.COLOR, GLClearBuffer.DEPTH))

        // Bind texture and issue draw calls
        gl.bindTexture(GLTextureTarget.TEXTURE_2D, glTexture)
        gl.drawArrays(mode = GLPrimitive.TRIANGLES, first = 0, count = 3)
    }

    return canvas
}
```

### Example 2: Vulkan 1.2+ / MoltenVK / WebGPU Swapchain with an Interactive Overlay

```kotlin
import com.nexustools.genericui.component.media.Canvas3D
import com.nexustools.genericui.component.media.Canvas3DConfig
import com.nexustools.genericui.component.media.VKDeviceAttributes
import com.nexustools.genericui.component.media.VKRenderPassDescriptor
import com.nexustools.genericui.ext.canvas3d.overlay.Canvas3DOverlayHost
import com.nexustools.genericui.ext.overlay.OverlayPresetResolver

fun createVulkanViewportWithOverlay(): Canvas3DOverlayHost {
    val vkCanvas = Canvas3D(
        config = Canvas3DConfig(
            vkAttributes = VKDeviceAttributes(srgbSwapchain = true),
            vsync = true
        ),
        width = 1280,
        height = 720
    )
    val vk = vkCanvas.getContext(vkCanvas.config.vkAttributes)

    vkCanvas.registerVBlankRender { _, _, _, _ ->
        vk.beginRenderPass(VKRenderPassDescriptor(clearR = 0.05f, clearG = 0.07f, clearB = 0.12f, clearA = 1.0f))
        vk.endRenderPass()
    }

    val overlay = OverlayPresetResolver.resolveForBackend()
    return vkCanvas.attachOverlay(overlay).apply {
        updateState { it.copy(title = "Vulkan 1.2+ Real-Time Visualizer", isPlaying = true) }
    }
}
```

---

## 5. Declarative `:extensions:canvas3d:dsl` Examples

Use `canvas3DGL { ... }`, `canvas3DVK { ... }`, `canvas3D { ... }`, and `canvas3DOverlayHost { ... }` inside any [`:extensions:dsl`](dsl.md) layout container:

```kotlin
import com.nexustools.genericui.component.media.Canvas3DConfig
import com.nexustools.genericui.component.media.GLClearBuffer
import com.nexustools.genericui.component.media.GLContextAttributes
import com.nexustools.genericui.component.media.VKDeviceAttributes
import com.nexustools.genericui.component.media.VKRenderPassDescriptor
import com.nexustools.genericui.ext.canvas3d.dsl.canvas3DGL
import com.nexustools.genericui.ext.canvas3d.dsl.canvas3DOverlayHost
import com.nexustools.genericui.ext.dsl.ColorRgba
import com.nexustools.genericui.ext.dsl.buildDiv
import com.nexustools.genericui.ext.dsl.col
import com.nexustools.genericui.ext.dsl.h4
import com.nexustools.genericui.ext.dsl.row
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.overlay.dsl.defaultOverlayPreset

val dual3DViewportComparison = buildDiv(classes = "p-3") {
    row(classes = "gap-3") {
        col(span = 6) {
            h4("Vulkan 1.2+ / MoltenVK / WebGPU Primary Viewport + Overlay")
            canvas3DOverlayHost(
                config = Canvas3DConfig(
                    preferredBackend = GpuBackendType.VULKAN,
                    vkAttributes = VKDeviceAttributes(srgbSwapchain = true)
                ),
                width = 600,
                height = 400,
                onRender = { _, _, _, _ ->
                    val vk = canvas3D?.vkContext ?: return@canvas3DOverlayHost
                    vk.beginRenderPass(VKRenderPassDescriptor(clearR = 0.08f, clearG = 0.12f, clearB = 0.22f, clearA = 1.0f))
                    vk.endRenderPass()
                }
            ) {
                defaultOverlayPreset()
                updateState { it.copy(title = "Vulkan Primary Swapchain") }
            }
        }

        col(span = 6) {
            h4("OpenGL ES 3.0 / Google ANGLE Viewport")
            canvas3DGL(
                attributes = GLContextAttributes(msaaSamples = 4, preferAngleOnWindows = true),
                width = 600,
                height = 400,
                onRender = { gl, _, _, _ ->
                    gl.clearColor(0.08f, 0.18f, 0.14f, 1.0f)
                    gl.clear(setOf(GLClearBuffer.COLOR, GLClearBuffer.DEPTH))
                }
            ) {
                style {
                    borderRadius = com.nexustools.genericui.style.css.CssCornerRadii.px(8f)
                    borderWidth = com.nexustools.genericui.style.css.CssEdgeLengths.px(1f)
                    borderStyle = com.nexustools.genericui.enums.css.CssBorderStyle.SOLID
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
  - [`:extensions:threejs` (`threejs.md`)](threejs.md) — High-level 3D scene graph (`Scene`, `PerspectiveCamera`, `WebGLRenderer`, PBR materials, lights, `OrbitControls`) rendered onto `Canvas3D`.
  - [`:extensions:box3d` (`box3d.md`)](box3d.md) — 3D rigid-body physics engine with `Box3DDebugRenderer` bound to `Canvas3D`.
  - [`:extensions:canvas2d` (`canvas2d.md`)](canvas2d.md) — Sister 2D vector/raster canvas extension (`Canvas2DExtSurface`, `ScrollCanvas2D`, `CurveEditorExt`).
  - [`:extensions:dsl` (`dsl.md`)](dsl.md) — Base declarative Kotlin UI & Style DSL.
- **Root & Backend Documentation**:
  - [GenericUI Root (`../../README.md`)](../../README.md) · [Extensions Architecture (`../../docs/extensions.md`)](../../docs/extensions.md) · [Source Sets (`../../docs/sourcesets.md`)](../../docs/sourcesets.md) · [Backends (`../../docs/backends.md`)](../../docs/backends.md) · [Gradle Plugin (`../../docs/gradle-plugin.md`)](../../docs/gradle-plugin.md)
  - [Desktop Backends (`../../desktop/README.md`)](../../desktop/README.md) · [Mobile Backends (`../../mobile/README.md`)](../../mobile/README.md) · [Web Backend (`../../web/README.md`)](../../web/README.md)
