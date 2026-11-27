# `:extensions:graphics:canvas2d` & `:extensions:graphics:canvas2d:dsl` — Hardware-Accelerated 2D Vector/Raster Canvas, `ScrollCanvas2D`, `CurveEditor`, & Overlay Hosts

> **Navigation**: [**GenericUI Root (`../../README.md`)**](../../README.md) · [**Extensions Index (`../README.md`)**](../README.md) · [**Root Extensions Guide (`../../docs/extensions.md`)**](../../docs/extensions.md) · [**Fragments & Windows (`../../docs/fragments-and-windows.md`)**](../../docs/fragments-and-windows.md) · [**Source Sets (`../../docs/sourcesets.md`)**](../../docs/sourcesets.md) · [**Backends (`../../docs/backends.md`)**](../../docs/backends.md) · [**Gradle Plugin (`../../docs/gradle-plugin.md`)**](../../docs/gradle-plugin.md)

`:extensions:graphics:canvas2d` is a **Single Consolidated KMP Project** (`commonMain`, `systemMain`, `nativeDesktopMain`, `jvmMain`, `androidMain`, `iosMain`, `webMain`, plus `:dsl`) providing hardware-accelerated 2D vector, path, typography, gradient, pattern, and `ImageBuffer` drawing across all GenericUI target backends, alongside VBlank-synchronized `deltaMs` render loops, simulated viewport-only scrollable 2D canvases (`ScrollCanvas2D`), interactive cubic-Bezier animation curve editing (`CurveEditor`), and [`:extensions:ui:overlay`](overlay.md) integration (`Canvas2DOverlayHost`, `ScrollCanvas2DOverlayHost`).

---

## 1. Architecture, `ImageBuffer2DContext`, & VBlank Double-Buffer Synchronization

`Canvas2D` (extending `:common`'s `AbstractCanvas<ImageBuffer2DContext>`) uses a double-buffered [`:extensions:graphics:imageprocessing`](imageprocessing.md) `ImageBuffer` (`doubleBuffer = true` by default) as its backing surface, and `canvas2D.getContext()` returns an instance of `ImageBuffer2DContext` directly (`imageBuffer.getContext()`).

- **System Targets (`systemMain`: Windows, macOS, Linux GTK4, Linux KDE, Desktop Swing, Android, iOS)**:
  - Backed by `SystemGpuImageBuffer(doubleBuffer = true)` and `GpuAcceleratedContext2DBackend` (`ImageBuffer2DContext`).
  - `Canvas2D` attaches an `ImageBufferDirtyListener` to its `ImageBuffer` and coordinates with `:extensions:graphics:gpu`'s `VBlankWatcher`:
    - When `ImageBuffer` is dirtied inside or outside a VBlank callback, `VBlankWatcher.notifySurfaceDirty` schedules presentation and swaps double buffers seamlessly.
- **Web Target (`webMain`)**:
  - Backed by `WebImageBuffer` and `WebImageBuffer2DContext` operating directly on the browser's `<canvas>` element (HTML5 `<canvas>` natively double-buffers).

### 1.1 `ScrollCanvas2D` — Viewport-Only Framebuffer with Simulated Scrolling

`ScrollCanvas2D` implements a scrollable 2D canvas with a configurable virtual content size (`contentWidth` x `contentHeight`), while **the actual rendered `<canvas>` / `ImageBuffer` only fills the visible viewport (`viewportWidth` x `viewportHeight`) and scrolling is simulated** via `ScrollCanvas2DListener`:
- **Web (`webMain` — `WebScrollCanvas2DImpl`)**: Renders a `<genericui:scrollcanvas2d>` element with `position: relative` and `overflow: auto`, containing a virtual content sizer `<div>` and a viewport-sized `<canvas>`.
- **System Targets (`:desktop`, `:mobile`, `:swing`)**: Composes the viewport-sized `viewportCanvas` (`Canvas2D`) with vertical and horizontal scrollbars updating `scrollX` / `scrollY` and invoking `onScroll`.

### 1.2 Overlay Integration (`Canvas2DOverlayHost` & `ScrollCanvas2DOverlayHost`)

`:extensions:graphics:canvas2d` integrates directly with [`:extensions:ui:overlay`](overlay.md) (`com.nexustools.genericui.ext.canvas2d.overlay`):
- **`Canvas2DOverlayHost`**: Wraps a `Canvas2D` (`canvas.attachOverlay(overlay)` or `canvas.withOverlay(overlay)`), synchronizing `overlayRoot` bounds to the canvas dimensions. No default overlay is attached unless explicitly supplied by the consumer.
- **`ScrollCanvas2DOverlayHost`**: Wraps a `ScrollCanvas2D` (`scrollCanvas.attachOverlay(overlay)` or `scrollCanvas.withOverlay(overlay)`), restricting `overlayRoot` strictly to the visible viewport (`0, 0, viewportWidth, viewportHeight`) so the overlay **never covers scrollbars** (`overlayCoversScrollbars == false`).

---

## 2. Gradle Plugin Configuration

Enabling `canvas2d()` in `genericui { extensions { ... } }` inside `build.gradle.kts` automatically enables `imageProcessing()`, `overlay()`, and `gpu()`, includes `:extensions:graphics:canvas2d:dsl` when `dsl()` is enabled, and auto-adds & auto-registers **`:extensions:graphics:canvas2d:alien`** ([`Canvas2DAlienExtension`](../graphics/canvas2d/alien/src/commonMain/kotlin/com/nexustools/genericui/ext/canvas2d/alien/Canvas2DAlienExtension.kt) $\rightarrow$ `Canvas2DAlienImpl`) whenever `alien()` (or Desktop `webView()`) is active:

```kotlin
plugins {
    id("com.nexustools.genericui")
}

genericui {
    app {
        id = "com.example.sketchpad"
        name = "Vector Sketchpad"
    }
    backends {
        allExceptSwing()
    }
    extensions {
        dsl()      // Enables :extensions:ui:dsl + :extensions:graphics:canvas2d:dsl + :extensions:ui:overlay:dsl
        canvas2d() // Includes :extensions:graphics:canvas2d, imageProcessing(), overlay(), and gpu()
    }
}
```

---

## 3. Core API Reference (`com.nexustools.genericui.ext.canvas2d` & `component.media`)

| Class / Function | Package | Description & Key Members |
| :--- | :--- | :--- |
| `Canvas2D` | `component.media` | Hardware-accelerated 2D canvas extending `AbstractCanvas<ImageBuffer2DContext>`. Key members: `doubleBuffer: Boolean = true`, `imageBuffer: ImageBuffer`, `getContext(): ImageBuffer2DContext`, `swapBuffers()`, `drawImage(buffer, dx, dy)`, `toImageBuffer()`, `syncSurfaceSize(width, height)`, `attachOverlay(overlay): Canvas2DOverlayHost`, `withOverlay(overlay): Canvas2DOverlayHost`, `registerVBlankRender { ctx, deltaMs, elapsedMs, frameIdx -> ... }`. |
| `ImageBuffer2DContext` | `ext.imageprocessing.context2d` | Returned by `canvas2D.getContext()` and `imageBuffer.getContext()`. Provides 100% HTML5 2D Context parity with `ImageBuffer*` enums and value types. |
| `Canvas2DExtension` | `ext.canvas2d` | Extension registry and helper singleton (`createImageBuffer`, `createContext`, `drawImage`, `extractImageBuffer`). |
| `ScrollCanvas2D` | `ext.canvas2d` | Scrollable 2D canvas with virtual `contentWidth` x `contentHeight` where the physical `viewportCanvas` only fills `viewportWidth` x `viewportHeight` and scrolling is simulated via `onScroll`. Key methods: `setViewportSize`, `setContentSize`, `scrollTo`, `scrollBy`, `onScroll`, `attachOverlay(overlay): ScrollCanvas2DOverlayHost`, `withOverlay(overlay): ScrollCanvas2DOverlayHost`. |
| `ScrollCanvas2DListener` | `ext.canvas2d` | `fun interface` (`onScrollChanged(scrollX, scrollY, viewportWidth, viewportHeight, contentWidth, contentHeight)`) invoked whenever simulated scroll position or dimensions change. |
| `Canvas2DOverlayHost` | `ext.canvas2d.overlay` | `OverlayHostBox` subclass wrapping a `Canvas2D` (`canvas`, `resizeCanvas(width, height)`, `syncOverlayBoundsToCanvas()`). |
| `ScrollCanvas2DOverlayHost` | `ext.canvas2d.overlay` | `OverlayHostBox` subclass wrapping a `ScrollCanvas2D` where `overlayRoot` covers only the viewport area (`overlayCoversScrollbars == false`). |
| `CurveEditor` | `component.media` | Interactive cubic-Bezier keyframe easing curve editor extending `Canvas2D` (`controlPoint1: Point`, `controlPoint2: Point`, `onCurveChanged`, `sampleCurveY(t)`, `sampleLut(size)`). |
| `CanvasTextLayoutHelper` | `ext.canvas2d` | Single-line auto-fitting (`fillTextFitted`) and multi-line word-wrapped overflow pagination helper for `ImageBuffer2DContext`. |
| `PrintableCanvasDocument` | `ext.canvas2d` | Multi-page printable 2D vector/raster document abstraction (`CanvasHandoutCardSpec`). |
| `canvas2D` / `canvas2DOverlayHost` | `ext.canvas2d.dsl` | Declarative DSL builders in `:extensions:graphics:canvas2d:dsl` for mounting `Canvas2D` and `Canvas2DOverlayHost`. |
| `scrollCanvas2D` / `scrollCanvas2DOverlayHost` | `ext.canvas2d.dsl` | Declarative DSL builders in `:extensions:graphics:canvas2d:dsl` for mounting `ScrollCanvas2D` and viewport-only `ScrollCanvas2DOverlayHost` containers. |
| `curveEditor` | `ext.canvas2d.dsl` | Declarative DSL builder in `:extensions:graphics:canvas2d:dsl` for mounting `CurveEditor`. |

---

## 4. Imperative Kotlin Examples

### Example 1: Animated Vector Graphics & `ImageBuffer` Blitting with `registerVBlankRender`

```kotlin
import com.nexustools.genericui.component.media.Canvas2D
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.style.color.RgbaColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun createAnimatedHudCanvas(spriteBuffer: ImageBuffer): Canvas2D {
    val surface = Canvas2D(width = 800, height = 600)
    var angleRad = 0.0

    // Synchronize rendering to hardware VBlank; dirty ImageBuffer presents automatically on VBlank
    surface.registerVBlankRender { ctx, deltaMs, _, frameIdx ->
        val w = ctx.width.toFloat()
        val h = ctx.height.toFloat()

        angleRad += (deltaMs / 1000.0) * (PI / 3.0)

        ctx.clearRect(0f, 0f, w, h)
        ctx.setFillColor(RgbaColor(15, 23, 42, 255))
        ctx.fillRect(0f, 0f, w, h)

        surface.drawImage(spriteBuffer, dx = 32f, dy = 32f)

        val cx = w * 0.5f
        val cy = h * 0.5f
        val orbitX = cx + (cos(angleRad) * 140.0).toFloat()
        val orbitY = cy + (sin(angleRad) * 140.0).toFloat()

        ctx.setStrokeColor(RgbaColor(56, 189, 248, 255))
        ctx.setLineWidth(3f)
        ctx.beginPath()
        ctx.arc(orbitX, orbitY, radius = 28f, startAngle = 0f, endAngle = (PI * 2.0).toFloat())
        ctx.stroke()

        ctx.setFont("Inter", 14f)
        ctx.setFillColor(RgbaColor(226, 232, 240, 255))
        ctx.fillText("Frame #$frameIdx • Δt = ${"%.2f".format(deltaMs)} ms", 24f, h - 24f)
    }

    return surface
}
```

### Example 2: Simulated Viewport Scrolling with `ScrollCanvas2D` & `ScrollCanvas2DOverlayHost`

```kotlin
import com.nexustools.genericui.component.layout.LayerAlignment
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.ext.canvas2d.ScrollCanvas2D
import com.nexustools.genericui.ext.canvas2d.overlay.ScrollCanvas2DOverlayHost
import com.nexustools.genericui.ext.overlay.Overlay
import com.nexustools.genericui.style.color.RgbaColor

fun createScrollableTilemapWithOverlay(): ScrollCanvas2DOverlayHost {
    val scrollCanvas = ScrollCanvas2D(
        viewportWidth = 800,
        viewportHeight = 600,
        contentWidth = 8000,
        contentHeight = 6000
    )

    scrollCanvas.onScroll { scrollX, scrollY, viewportW, viewportH, _, _ ->
        val ctx = scrollCanvas.getContext()
        ctx.clearRect(0f, 0f, viewportW.toFloat(), viewportH.toFloat())
        ctx.setFillColor(RgbaColor(15, 23, 42, 255))
        ctx.fillRect(0f, 0f, viewportW.toFloat(), viewportH.toFloat())

        ctx.save()
        ctx.translate(-scrollX.toFloat(), -scrollY.toFloat())
        ctx.setFillColor(RgbaColor(56, 189, 248, 255))
        ctx.fillText("Visible World Region @ ($scrollX, $scrollY)", (scrollX + 24).toFloat(), (scrollY + 36).toFloat())
        ctx.restore()
    }

    val overlay = Overlay().apply {
        addElement(
            component = Text("World Tilemap (8000x6000)"),
            elementId = "tilemap-badge"
        )
    }
    return scrollCanvas.attachOverlay(overlay)
}
```

---

## 5. Related Extensions & Cross-References

- [`:extensions:graphics:imageprocessing` (`imageprocessing.md`)](imageprocessing.md) — Defines `ImageBuffer`, `ImageBuffer2DContext`, `ScalableVectorFontEngine`, and offscreen GPU filter pipelines.
- [`:extensions:ui:overlay` (`overlay.md`)](overlay.md) — Pure `LayeredContainer` overlay framework (`OverlayHost`, `Overlay`, `YoutubePlayerOverlay`, `AutoHideController`).
- [`:extensions:graphics:gpu` (`gpu.md`)](gpu.md) — Defines `AbstractCanvas`, `VBlankWatcher`, `VBlankTimer`, and hardware GPU pipelines.
- [`:extensions:ui:codeeditor` (`codeeditor.md`)](codeeditor.md) — Uses `Canvas2D` to render syntax-highlighted source code and `CodeMinimap`.
- [`:extensions:physics:box2d` (`box2d.md`)](box2d.md) — Uses `ImageBuffer2DContext` in `Box2DDebugDrawer` to visualize 2D rigid bodies, fixtures, joints, and AABBs.
- [`:extensions:ui:alien` (`alien.md`)](alien.md) — Uses `Canvas2D` for self-rendering UI and auto-registers `:extensions:graphics:canvas2d:alien` (`Canvas2DAlienImpl`) for nested child canvases without OS underlay holes.
- [`:extensions:graphics:svg` (`svg.md`)](svg.md) — Sister vector scene graph extension (`Svg`, `SvgAlienImpl`).
- [`:extensions:graphics:canvas3d` (`canvas3d.md`)](canvas3d.md) — Sister 3D canvas extension (`Canvas3D`, `ScrollCanvas3D`, `Canvas3DOverlayHost`).
