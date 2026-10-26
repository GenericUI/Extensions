# `:extensions:canvas2d` & `:extensions:canvas2d:dsl` — Hardware-Accelerated 2D Vector/Raster Canvas, `ScrollCanvas2D`, Curve Editor, & Overlay Hosts

> **Navigation**: [**GenericUI Root (`../../README.md`)**](../../README.md) · [**Extensions Index (`../README.md`)**](../README.md) · [**Root Extensions Guide (`../../docs/extensions.md`)**](../../docs/extensions.md) · [**Fragments & Windows (`../../docs/fragments-and-windows.md`)**](../../docs/fragments-and-windows.md) · [**Source Sets (`../../docs/sourcesets.md`)**](../../docs/sourcesets.md) · [**Backends (`../../docs/backends.md`)**](../../docs/backends.md) · [**Gradle Plugin (`../../docs/gradle-plugin.md`)**](../../docs/gradle-plugin.md)

`:extensions:canvas2d` is a **Multi-Module Project-of-Projects** extension (`:common`, `:systemCommon`, `:desktop:nativeCommon`, `:desktop:{windows,macos,gtk,qt,swing}`, `:mobile:common`, `:mobile:{android,ios}`, `:web`, plus `:dsl`) providing hardware-accelerated 2D vector, path, typography, gradient, pattern, and `ImageBuffer` drawing across all 8 GenericUI target backends, alongside VBlank-synchronized `deltaMs` render loops, simulated viewport-only scrollable 2D canvases (`ScrollCanvas2D`), interactive cubic-Bezier animation curve editing (`CurveEditorExt`), and [`:extensions:overlay`](overlay.md) integration (`Canvas2DOverlayHost`, `ScrollCanvas2DOverlayHost`).

---

## 1. Architecture, `ImageBuffer2DContext`, & VBlank Double-Buffer Synchronization

`Canvas2D` (extending `:extensions:gpu`'s `AbstractCanvas<ImageBuffer2DContext>`) uses a double-buffered [`:extensions:imageprocessing`](imageprocessing.md) `ImageBuffer` (`doubleBuffer = true` by default) as its backing surface, and `canvas2D.getContext()` returns an instance of `ImageBuffer2DContext` directly (`imageBuffer.getContext()`).

- **System Targets (`:extensions:canvas2d:systemCommon`: Windows, macOS, Linux GTK4, Linux Qt6, Desktop Swing, Android, iOS)**:
  - Backed by `SystemGpuImageBuffer(doubleBuffer = true)` and `GpuAcceleratedContext2DBackend` (`ImageBuffer2DContext`).
  - `Canvas2D` attaches an `ImageBufferDirtyListener` to its `ImageBuffer` and coordinates with `:extensions:gpu`'s `VBlankWatcher` (`SystemVBlankSurfaceSynchronizer`):
    - When `ImageBuffer` is dirtied **inside** a VBlank callback (`VBlankWatcher.isInVBlankCallback == true`), `Canvas2D` swaps and presents the double-buffered surface **immediately at the end of that VBlank callback** (`VBlankWatcher.doAtEndOfCurrentVBlank`).
    - When `ImageBuffer` is dirtied **outside** a VBlank callback (`VBlankWatcher.isInVBlankCallback == false`), `Canvas2D` coalesces the dirty state and schedules presentation on the **next VBlank tick** (`VBlankWatcher.requestNextVBlank`).
- **Web Target (`:extensions:canvas2d:web`)**:
  - Backed by `WebImageBuffer` and `WebImageBuffer2DContext` operating directly on the browser's `<canvas>` element.
  - Dirty tracking (`enableDirtyTracking = false`) and `WebVBlankSurfaceSynchronizer` are no-ops on Web because HTML5 `<canvas>` natively double-buffers and composites automatically in the browser.

### 1.1 `ScrollCanvas2D` — Viewport-Only Framebuffer with Simulated Scrolling

`ScrollCanvas2D` implements a scrollable 2D canvas with a configurable virtual content size (`contentWidth` x `contentHeight`), while **the actual rendered `<canvas>` / `ImageBuffer` only fills the visible viewport (`viewportWidth` x `viewportHeight`) and scrolling is simulated** via `ScrollCanvas2DListener` (`onScroll { scrollX, scrollY, viewportWidth, viewportHeight, contentWidth, contentHeight -> ... }`):
- **Web (`:extensions:canvas2d:web` — `WebScrollCanvas2DImpl`)**:
  - Renders a `<genericui:scrollcanvas2d>` element (`WebDomElement("genericui:scrollcanvas2d")`) with `position: relative` and `overflow: auto`.
  - Contains an inner `<div>` (`contentSizerDiv`) sized to `width: ${contentWidth}px; height: ${contentHeight}px;` so native browser scrollbars reflect the virtual document size.
  - Places an HTML `<canvas>` (`canvasElement`) overtop the `<div>` with `position: absolute; left: 0; top: 0; width: 0; height: 0;` whose pixel surface only allocates `viewportWidth` x `viewportHeight`.
- **System Targets (`:desktop`, `:mobile`, `:swing`)**:
  - Composes the viewport-sized `viewportCanvas` (`Canvas2D`) with a vertical scrollbar component at the right (`rightScrollBar: RangeSlider`) and a horizontal scrollbar component at the bottom (`bottomScrollBar: RangeSlider`).
  - Moving the right or bottom scrollbar (or scrolling with the mouse wheel) updates `scrollX` / `scrollY` and fires `onScroll` so the application re-renders the viewport canvas for `(scrollX, scrollY, viewportWidth, viewportHeight)` without ever allocating an oversized framebuffer.

### 1.2 Overlay Integration (`Canvas2DOverlayHost` & `ScrollCanvas2DOverlayHost`)

`:extensions:canvas2d` integrates directly with [`:extensions:overlay`](overlay.md) (`com.nexustools.genericui.ext.canvas2d.overlay`):
- **`Canvas2DOverlayHost`**: Wraps a `Canvas2DExtSurface` (`canvas.attachOverlay(overlay)` or `canvas.withOverlay(overlay)`), synchronizing `overlayRoot` bounds to the canvas dimensions. No default overlay is attached unless explicitly supplied by the consumer.
- **`ScrollCanvas2DOverlayHost`**: Wraps a `ScrollCanvas2D` (`scrollCanvas.attachOverlay(overlay)` or `scrollCanvas.withOverlay(overlay)`), restricting `overlayRoot` strictly to the visible viewport (`0, 0, viewportWidth, viewportHeight`) so the overlay **never covers the horizontal or vertical scrollbars** (`overlayCoversScrollbars == false`).

---

## 2. Gradle Plugin Configuration

Enabling `canvas2d()` in the [GenericUI Gradle Plugin (`../../docs/gradle-plugin.md`)](../../docs/gradle-plugin.md) automatically configures `:extensions:canvas2d`, `:extensions:imageprocessing`, `:extensions:overlay`, and `:extensions:gpu` for all active target backends, registers the platform `Canvas2D` provider in generated launchers, and automatically includes `:extensions:canvas2d:dsl` when `dsl()` is enabled:

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
        dsl()      // Enables :extensions:dsl + :extensions:canvas2d:dsl + :extensions:overlay:dsl
        canvas2d() // Automatically includes :extensions:canvas2d, :extensions:imageprocessing, :extensions:overlay, and :extensions:gpu
    }
}
```

---

## 3. Core API Reference (`com.nexustools.genericui.ext.canvas2d` & `component.media`)

| Class / Function | Package | Description & Key Members |
| :--- | :--- | :--- |
| `Canvas2D` | `component.media` | Hardware-accelerated 2D canvas extending `AbstractCanvas<ImageBuffer2DContext>`. Key members: `doubleBuffer: Boolean = true`, `imageBuffer: ImageBuffer`, `getContext(): ImageBuffer2DContext`, `swapBuffers()`, `registerVBlankRender { ctx, deltaMs, elapsedMs, frameIdx -> ... }`. |
| `ImageBuffer2DContext` | `ext.imageprocessing.context2d` | Returned by `canvas2D.getContext()` and `imageBuffer.getContext()`. Provides 100% HTML5 2D Context parity with `ImageBuffer*` enums and value types. |
| `Canvas2DExtension` | `ext.canvas2d` | Extension registry and helper singleton (`createImageBuffer(width, height, doubleBuffer, antialias)`, `createContext(width, height)`, `drawImage(ctx, buffer, dx, dy)`, `extractImageBuffer(ctx, x, y, width, height)`). |
| `Canvas2DExtSurface` | `ext.canvas2d` | Extended 2D canvas widget extending `Canvas2D`. Key methods: `drawImage(buffer: ImageBuffer, dx: Float, dy: Float)`, `toImageBuffer(): ImageBuffer`, `syncSurfaceSize(width, height)`, `attachOverlay(overlay): Canvas2DOverlayHost`, `withOverlay(overlay): Canvas2DOverlayHost`. |
| `ScrollCanvas2D` | `ext.canvas2d` | Scrollable 2D canvas with virtual `contentWidth` x `contentHeight` where the physical `viewportCanvas` only fills `viewportWidth` x `viewportHeight` and scrolling is simulated via `onScroll`. Key methods: `setViewportSize`, `setContentSize`, `scrollTo`, `scrollBy`, `onScroll`, `attachOverlay(overlay): ScrollCanvas2DOverlayHost`, `withOverlay(overlay): ScrollCanvas2DOverlayHost`. |
| `ScrollCanvas2DListener` | `ext.canvas2d` | `fun interface` (`onScrollChanged(scrollX, scrollY, viewportWidth, viewportHeight, contentWidth, contentHeight)`) invoked whenever simulated scroll position or dimensions change. |
| `Canvas2DOverlayHost` | `ext.canvas2d.overlay` | `OverlayHostBox` subclass wrapping a `Canvas2DExtSurface` (`canvas`, `resizeCanvas(width, height)`, `syncOverlayBoundsToCanvas()`). |
| `ScrollCanvas2DOverlayHost` | `ext.canvas2d.overlay` | `OverlayHostBox` subclass wrapping a `ScrollCanvas2D` where `overlayRoot` covers only the viewport area (`overlayCoversScrollbars == false`, `setViewportSize`, `setContentSize`, `scrollTo`, `scrollBy`). |
| `CurveEditorExt` | `ext.canvas2d` | Interactive cubic-Bezier keyframe easing curve editor extending `Canvas2DExtSurface`. Key members: `controlPoint1: Point`, `controlPoint2: Point`, `onCurveChanged`, `sampleCurveY(t: Float): Float`, `sampleLut(size: Int = 256): FloatArray`. |
| `CanvasTextLayoutHelper` | `ext.canvas2d` | Single-line auto-fitting (`fillTextFitted`) and multi-line word-wrapped overflow pagination (`fillTextAreaWithOverflow`) helper for `ImageBuffer2DContext`. |
| `PrintableCanvasDocument` | `ext.canvas2d` | Multi-page printable 2D vector/raster document abstraction (`CanvasHandoutCardSpec`). |
| `canvas2DExt` / `canvas2D` / `canvas2DOverlayHost` | `ext.canvas2d.dsl` | Declarative DSL builders in `:extensions:canvas2d:dsl` for mounting `Canvas2DExtSurface`, `Canvas2D`, and `Canvas2DOverlayHost`. |
| `scrollCanvas2D` / `scrollCanvas2DOverlayHost` | `ext.canvas2d.dsl` | Declarative DSL builders in `:extensions:canvas2d:dsl` for mounting `ScrollCanvas2D` and `ScrollCanvas2DOverlayHost`. |
| `curveEditorExt` / `curveEditor` | `ext.canvas2d.dsl` | Declarative DSL builders in `:extensions:canvas2d:dsl` for mounting `CurveEditorExt` and `CurveEditor`. |

---

## 4. Imperative Kotlin Examples

### Example 1: Animated Vector Graphics & `ImageBuffer` Blitting with `registerVBlankRender`

```kotlin
import com.nexustools.genericui.ext.canvas2d.Canvas2DExtSurface
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.style.color.RgbaColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun createAnimatedHudCanvas(spriteBuffer: ImageBuffer): Canvas2DExtSurface {
    val surface = Canvas2DExtSurface(width = 800, height = 600)
    var angleRad = 0.0

    // Synchronize rendering to hardware VBlank; dirty ImageBuffer presents automatically at end of callback
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
import com.nexustools.genericui.ext.canvas2d.ScrollCanvas2D
import com.nexustools.genericui.ext.canvas2d.overlay.ScrollCanvas2DOverlayHost
import com.nexustools.genericui.ext.overlay.OverlayPresetResolver
import com.nexustools.genericui.style.color.RgbaColor

fun createScrollableTilemapWithOverlay(): ScrollCanvas2DOverlayHost {
    // Physical canvas is only 800x600 (viewport), while virtual content is 8000x6000
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

        // Render only the visible viewport region at (-scrollX, -scrollY)
        ctx.save()
        ctx.translate(-scrollX.toFloat(), -scrollY.toFloat())
        ctx.setFillColor(RgbaColor(56, 189, 248, 255))
        ctx.fillText("Visible World Region @ ($scrollX, $scrollY)", (scrollX + 24).toFloat(), (scrollY + 36).toFloat())
        ctx.restore()
    }

    val overlay = OverlayPresetResolver.resolveForBackend()
    return scrollCanvas.attachOverlay(overlay).apply {
        updateState { it.copy(title = "World Tilemap (8000x6000)", subtitle = "Viewport-Only Framebuffer") }
    }
}
```

---

## 5. Related Extensions & Cross-References

- [`:extensions:imageprocessing` (`imageprocessing.md`)](imageprocessing.md) — Defines `ImageBuffer`, `ImageBuffer2DContext`, `ScalableVectorFontEngine`, and offscreen GPU filter pipelines.
- [`:extensions:overlay` (`overlay.md`)](overlay.md) — Universal domain-agnostic overlay framework (`OverlayHost`, `MobileOverlayPreset`, `WebSystemOverlayPreset`).
- [`:extensions:gpu` (`gpu.md`)](gpu.md) — Defines `AbstractCanvas`, `VBlankWatcher`, `VBlankTimer`, and `VBlankSurfaceSynchronizer`.
- [`:extensions:codeeditor` (`codeeditor.md`)](codeeditor.md) — Uses `Canvas2DExtSurface` to render syntax-highlighted source code and `CodeMinimap`.
- [`:extensions:box2d` (`box2d.md`)](box2d.md) — Uses `ImageBuffer2DContext` in `Box2DDebugDrawer` to visualize 2D rigid bodies, fixtures, joints, and AABBs.
- [`:extensions:canvas3d` (`canvas3d.md`)](canvas3d.md) — Sister 3D canvas extension (`GLCanvasExt`, `VKCanvasExt`, `ScrollCanvas3D`, `Canvas3DOverlayHost`).
