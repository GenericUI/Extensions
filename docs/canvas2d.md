# `:extensions:canvas2d` & `:extensions:canvas2d:dsl` — Hardware-Accelerated 2D Vector/Raster Canvas & Curve Editor

> **Navigation**: [**GenericUI Root (`../../README.md`)**](../../README.md) · [**Extensions Index (`../README.md`)**](../README.md) · [**Root Extensions Guide (`../../docs/extensions.md`)**](../../docs/extensions.md) · [**Source Sets (`../../docs/sourcesets.md`)**](../../docs/sourcesets.md) · [**Backends (`../../docs/backends.md`)**](../../docs/backends.md) · [**Gradle Plugin (`../../docs/gradle-plugin.md`)**](../../docs/gradle-plugin.md)

`:extensions:canvas2d` is a **Project-of-Projects** extension providing hardware-accelerated 2D vector, path, typography, gradient, pattern, and `ImageBuffer` drawing across all 8 GenericUI target backends, alongside VBlank-synchronized `deltaMs` render loops, simulated viewport-only scrollable 2D canvases (`ScrollCanvas2D`), and an interactive cubic-Bezier animation curve editor (`CurveEditorExt`). Its companion [`:extensions:canvas2d:dsl`](dsl.md) module provides declarative `canvas2DExt { ... }`, `scrollCanvas2D { ... }`, and `curveEditorExt { ... }` builders.

---

## 1. Architecture, `ImageBuffer2DContext`, & VBlank Double-Buffer Synchronization

`Canvas2D` (extending `:extensions:gpu`'s `AbstractCanvas<ImageBuffer2DContext>`) uses a double-buffered `:extensions:imageprocessing` [`ImageBuffer`](imageprocessing.md) (`doubleBuffer = true` by default) as its backing surface, and `canvas2D.getContext()` returns an instance of `ImageBuffer2DContext` directly (`imageBuffer.getContext()`).

- **System Targets (`sharedSystem`: Windows, macOS, Linux GTK4, Linux Qt6, Desktop Swing, Android, iOS)**:
  - Backed by `SystemGpuImageBuffer(doubleBuffer = true)` and `GpuAcceleratedContext2DBackend` (`ImageBuffer2DContext`).
  - `Canvas2D` attaches an `ImageBufferDirtyListener` to its `ImageBuffer` and coordinates with `:extensions:gpu`'s `VBlankWatcher` (`SystemVBlankSurfaceSynchronizer`):
    - When `ImageBuffer` is dirtied **inside** a VBlank callback (`VBlankWatcher.isInVBlankCallback == true`), `Canvas2D` swaps and presents the double-buffered surface **immediately at the end of that VBlank callback** (`VBlankWatcher.doAtEndOfCurrentVBlank`).
    - When `ImageBuffer` is dirtied **outside** a VBlank callback (`VBlankWatcher.isInVBlankCallback == false`), `Canvas2D` coalesces the dirty state and schedules presentation on the **next VBlank tick** (`VBlankWatcher.requestNextVBlank`).
- **Web Target (`:web`)**:
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

---

## 2. Gradle Plugin Configuration

Enabling `canvas2d()` in the [GenericUI Gradle Plugin (`../../docs/gradle-plugin.md`)](../../docs/gradle-plugin.md) wires `:extensions:canvas2d` for all active target backends, and automatically includes `:extensions:canvas2d:dsl` when `dsl()` is enabled:

```kotlin
plugins {
    id("com.nexustools.genericui")
}

genericui {
    backends {
        allExceptSwing()
    }
    extensions {
        dsl()      // Enables :extensions:dsl
        canvas2d() // Automatically includes :extensions:canvas2d AND :extensions:canvas2d:dsl
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
| `Canvas2DExtSurface` | `ext.canvas2d` | Extended 2D canvas widget extending `Canvas2D`. Key methods: `drawImage(buffer: ImageBuffer, dx: Float, dy: Float)`, `toImageBuffer(): ImageBuffer`, `syncSurfaceSize(width, height)`. |
| `ScrollCanvas2D` | `ext.canvas2d` | Scrollable 2D canvas with virtual `contentWidth` x `contentHeight` where the physical `viewportCanvas` only fills `viewportWidth` x `viewportHeight` and scrolling is simulated via `onScroll`. On Web renders `<genericui:scrollcanvas2d>` with `position: relative`, inner `<div>` sized to `contentWidth` x `contentHeight`, and `<canvas>` with `position: absolute; left: 0; top: 0; width: 0; height: 0;`; on System targets composes `viewportCanvas` with `rightScrollBar` and `bottomScrollBar`. |
| `ScrollCanvas2DListener` | `ext.canvas2d` | `fun interface` (`onScrollChanged(scrollX, scrollY, viewportWidth, viewportHeight, contentWidth, contentHeight)`) invoked whenever simulated scroll position or dimensions change. |
| `CurveEditorExt` | `ext.canvas2d` | Interactive cubic-Bezier keyframe easing curve editor extending `Canvas2DExtSurface`. Key members: `controlPoint1: Point`, `controlPoint2: Point`, `onCurveChanged`, `sampleCurveY(t: Float): Float`, `sampleLut(size: Int = 256): FloatArray`. |
| `CanvasTextLayoutHelper` | `ext.canvas2d` | Single-line auto-fitting (`fillTextFitted`) and multi-line word-wrapped overflow pagination (`fillTextAreaWithOverflow`) helper for `ImageBuffer2DContext`. |
| `PrintableCanvasDocument` | `ext.canvas2d` | Multi-page printable 2D vector/raster document abstraction (`CanvasHandoutCardSpec`). |
| `canvas2DExt` / `canvas2D` | `ext.canvas2d.dsl` | Declarative DSL builders in `:extensions:canvas2d:dsl` for mounting `Canvas2DExtSurface` and `Canvas2D` with `onRender` VBlank callbacks and `style { ... }`. |
| `scrollCanvas2D` | `ext.canvas2d.dsl` | Declarative DSL builder in `:extensions:canvas2d:dsl` for mounting `ScrollCanvas2D` with `viewportWidth`, `viewportHeight`, `contentWidth`, `contentHeight`, and `onScroll` listener. |
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

### Example 2: Simulated Viewport Scrolling over a Large Document with `ScrollCanvas2D`

```kotlin
import com.nexustools.genericui.ext.canvas2d.ScrollCanvas2D
import com.nexustools.genericui.style.color.RgbaColor

fun createScrollableTilemapCanvas(): ScrollCanvas2D {
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

    return scrollCanvas
}
```

---

## 5. Related Extensions & Cross-References

- [`:extensions:imageprocessing` (`imageprocessing.md`)](imageprocessing.md) — Defines `ImageBuffer`, `ImageBuffer2DContext`, `ScalableVectorFontEngine`, and offscreen GPU filter pipelines.
- [`:extensions:gpu` (`gpu.md`)](gpu.md) — Defines `AbstractCanvas`, `VBlankWatcher`, `VBlankTimer`, and `VBlankSurfaceSynchronizer`.
- [`:extensions:codeeditor` (`codeeditor.md`)](codeeditor.md) — Uses `Canvas2DExtSurface` to render syntax-highlighted source code and `CodeMinimap`.
- [`:extensions:box2d` (`box2d.md`)](box2d.md) — Uses `ImageBuffer2DContext` in `Box2DDebugDrawer` to visualize 2D rigid bodies, fixtures, joints, and AABBs.
- [`:extensions:canvas3d` (`canvas3d.md`)](canvas3d.md) — Sister 3D canvas extension (`GLCanvasExt`, `VKCanvasExt`) extending `:extensions:gpu`'s `AbstractCanvas`.
