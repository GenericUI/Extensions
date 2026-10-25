# `:extensions:canvas2d` & `:extensions:canvas2d:dsl` — Hardware-Accelerated 2D Vector/Raster Canvas & Curve Editor

> **Navigation**: [**GenericUI Root (`../../README.md`)**](../../README.md) · [**Extensions Index (`../README.md`)**](../README.md) · [**Root Extensions Guide (`../../docs/extensions.md`)**](../../docs/extensions.md) · [**Source Sets (`../../docs/sourcesets.md`)**](../../docs/sourcesets.md) · [**Backends (`../../docs/backends.md`)**](../../docs/backends.md) · [**Gradle Plugin (`../../docs/gradle-plugin.md`)**](../../docs/gradle-plugin.md)

`:extensions:canvas2d` is a **Project-of-Projects** extension providing hardware-accelerated 2D vector, path, typography, gradient, and `:common` `ImageBuffer` drawing across all 8 GenericUI target backends, alongside VBlank-synchronized `deltaMs` render loops and an interactive cubic-Bezier animation curve editor (`CurveEditorExt`). Its companion [`:extensions:canvas2d:dsl`](dsl.md) module provides declarative `canvas2DExt { ... }` and `curveEditorExt { ... }` builders.

---

## 1. Architecture & Native 2D Rendering Backends

`:extensions:canvas2d` is organized into `:extensions:canvas2d:common`, `:extensions:canvas2d:desktop:{shared,windows,macos,gtk,qt,swing}`, `:extensions:canvas2d:mobile:{shared,android,ios}`, `:extensions:canvas2d:web`, and `:extensions:canvas2d:dsl`:

| Target Backend | Submodule | Native 2D Rendering Engine | Backend Documentation |
| :--- | :--- | :--- | :--- |
| **Windows (`:windows`)** | `:extensions:canvas2d:desktop:windows` | Direct2D + DirectWrite (`WindowsCanvas2DNativeImpl`) | [Desktop Backends (`../../desktop/README.md`)](../../desktop/README.md) |
| **macOS (`:macos`)** | `:extensions:canvas2d:desktop:macos` | Apple CoreGraphics (`CGContextRef`) + CoreText (`MacosCanvas2DNativeImpl`) | [Desktop Backends (`../../desktop/README.md`)](../../desktop/README.md) |
| **Linux GTK4 (`:gtk`)** | `:extensions:canvas2d:desktop:gtk` | Cairo 2D + Pango (`GtkCanvas2DNativeImpl`) | [Desktop Backends (`../../desktop/README.md`)](../../desktop/README.md) |
| **Linux Qt6 (`:qt`)** | `:extensions:canvas2d:desktop:qt` | Qt6 `QPainter` (`QtCanvas2DNativeImpl`) | [Desktop Backends (`../../desktop/README.md`)](../../desktop/README.md) |
| **Desktop JVM (`:swing`)** | `:extensions:canvas2d:desktop:swing` | Java2D `Graphics2D` with hardware pipeline (`SwingCanvas2DImpl`) | [Desktop Backends (`../../desktop/README.md`)](../../desktop/README.md) |
| **Android (`:android`)** | `:extensions:canvas2d:mobile:android` | `android.graphics.Canvas` + `Paint` (`AndroidCanvas2DImpl`) | [Mobile Backends (`../../mobile/README.md`)](../../mobile/README.md) |
| **iOS (`:ios`)** | `:extensions:canvas2d:mobile:ios` | CoreGraphics `UIGraphicsGetCurrentContext` (`IosCanvas2DImpl`) | [Mobile Backends (`../../mobile/README.md`)](../../mobile/README.md) |
| **Web (`:web`)** | `:extensions:canvas2d:web` | HTML5 `<canvas>` `CanvasRenderingContext2D` (`WebCanvas2DImpl`) | [Web Backend (`../../web/README.md`)](../../web/README.md) |

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
| `Canvas2DExtension` | `ext.canvas2d` | Extension registry and helper singleton (`createContext(width, height)`, `drawImage(ctx, buffer, dx, dy)`, `extractImageBuffer(ctx, x, y, width, height)`). |
| `Canvas2DExtSurface` | `ext.canvas2d` | Extended 2D canvas widget extending `Canvas2D` (`AbstractCanvas`). Key methods: `drawImage(buffer: ImageBuffer, dx: Float, dy: Float)`, `toImageBuffer(): ImageBuffer`, `registerVBlankRender { ctx, deltaMs, elapsedMs, frameIdx -> ... }`, `requestRender()`. |
| `CurveEditorExt` | `ext.canvas2d` | Interactive cubic-Bezier keyframe easing curve editor extending `Canvas2DExtSurface`. Key members: `controlPoint1: Point`, `controlPoint2: Point`, `onCurveChanged: ((Point, Point) -> Unit)?`, `sampleCurveY(t: Float): Float`, `sampleLut(size: Int = 256): FloatArray`, `renderCurveOverlay(ctx)`. |
| `OffscreenCanvas2D` | `ext.canvas2d` | Headless 2D vector/raster drawing surface (`width`, `height`, `context: Canvas2DContext`, `render { ctx -> ... }`, `toImageBuffer(): ImageBuffer`). |
| `Canvas2DContext` | `component.media` | Immediate-mode 2D drawing context: `clearRect`, `fillRect`, `strokeRect`, `beginPath`, `moveTo`, `lineTo`, `quadraticCurveTo`, `bezierCurveTo`, `arc`, `closePath`, `fill`, `stroke`, `fillText`, `strokeText`, `setFont`, `setFillColor`, `setStrokeColor`, `setLineWidth`, `save`, `restore`, `translate`, `rotate`, `scale`, `drawImageBuffer`. |
| `CanvasTextLayoutHelper` | `ext.canvas2d` | Multi-line word-wrapping and text metrics helper for `Canvas2DContext`. |
| `PrintableCanvasDocument` | `ext.canvas2d` | Multi-page printable 2D vector/raster document abstraction (`CanvasHandoutCardSpec`). |
| `canvas2DExt` / `canvas2D` | `ext.canvas2d.dsl` | Declarative DSL builders in `:extensions:canvas2d:dsl` for mounting `Canvas2DExtSurface` and `Canvas2D` with `onRender` VBlank callbacks and `style { ... }`. |
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

    // Synchronize rendering to hardware VBlank with high-precision deltaMs timing
    val subscription = surface.registerVBlankRender { ctx, deltaMs, elapsedMs, frameIdx ->
        val w = ctx.width.toFloat()
        val h = ctx.height.toFloat()

        // Advance rotation at 60 deg/sec using VBlank deltaMs
        angleRad += (deltaMs / 1000.0) * (PI / 3.0)

        ctx.clearRect(0f, 0f, w, h)
        ctx.setFillColor(RgbaColor(15, 23, 42, 255))
        ctx.fillRect(0f, 0f, w, h)

        // Blit an ImageBuffer from :common or :extensions:imageprocessing
        surface.drawImage(spriteBuffer, dx = 32f, dy = 32f)

        // Draw an orbiting vector ring
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

    // Snapshot the rendered canvas back into a :common ImageBuffer at any time
    val snapshot: ImageBuffer = surface.toImageBuffer()
    return surface
}
```

### Example 2: Cubic-Bezier Animation Easing with `CurveEditorExt`

```kotlin
import com.nexustools.genericui.ext.canvas2d.CurveEditorExt
import com.nexustools.genericui.geometry.Point

fun createEasingCurveEditor(): CurveEditorExt {
    val curveEditor = CurveEditorExt(width = 260, height = 260).apply {
        // Standard CSS ease-in-out control points
        controlPoint1 = Point(0.42f, 0.0f)
        controlPoint2 = Point(0.58f, 1.0f)

        onCurveChanged = { cp1, cp2 ->
            val midpointValue = sampleCurveY(0.5f)
            val lut256 = sampleLut(size = 256)
            println("Bezier updated: P1=(${cp1.x}, ${cp1.y}), P2=(${cp2.x}, ${cp2.y}), Y(0.5)=$midpointValue, LUT[128]=${lut256[128]}")
        }
    }
    return curveEditor
}
```

---

## 5. Declarative `:extensions:canvas2d:dsl` Examples

With `dsl()` and `canvas2d()` enabled, use `canvas2DExt { ... }` and `curveEditorExt { ... }` directly inside any GenericUI container or `MvcView`:

```kotlin
import com.nexustools.genericui.ext.canvas2d.dsl.canvas2DExt
import com.nexustools.genericui.ext.canvas2d.dsl.curveEditorExt
import com.nexustools.genericui.ext.dsl.ColorRgba
import com.nexustools.genericui.ext.dsl.buildDiv
import com.nexustools.genericui.ext.dsl.col
import com.nexustools.genericui.ext.dsl.h4
import com.nexustools.genericui.ext.dsl.row
import com.nexustools.genericui.ext.dsl.span
import com.nexustools.genericui.geometry.Point
import com.nexustools.genericui.style.color.RgbaColor

val animationTimelinePanel = buildDiv(classes = "p-4") {
    var easedProgress = 0.5f

    row(classes = "gap-4") {
        col(span = 4) {
            h4("Keyframe Easing Curve")
            val readout = span("Y(0.5) = 0.50")

            curveEditorExt(
                width = 240,
                height = 240,
                controlPoint1 = Point(0.25f, 0.1f),
                controlPoint2 = Point(0.25f, 1.0f),
                onCurveChanged = { cp1, cp2 ->
                    readout.text = "P1(${cp1.x}, ${cp1.y}) • P2(${cp2.x}, ${cp2.y})"
                }
            ) {
                style {
                    backgroundColor = ColorRgba(17, 24, 39, 1.0)
                    borderRadiusPx = 8f
                    borderWidthPx = 1f
                    borderColor = ColorRgba(55, 65, 81, 1.0)
                }
            }
        }

        col(span = 8) {
            h4("2D Vector Viewport")
            canvas2DExt(
                width = 640,
                height = 360,
                onRender = { ctx, deltaMs, elapsedMs, _ ->
                    val t = ((elapsedMs % 2000.0) / 2000.0).toFloat()
                    ctx.clearRect(0f, 0f, ctx.width.toFloat(), ctx.height.toFloat())
                    ctx.setFillColor(RgbaColor(16, 185, 129, 255))
                    ctx.fillRect(32f + t * 520f * easedProgress, 140f, 64f, 64f)
                }
            ) {
                style {
                    borderRadiusPx = 10f
                }
            }
        }
    }
}
```

---

## 6. Related Extensions & Cross-References

- **Extensions Built on `:extensions:canvas2d`**:
  - [`:extensions:codeeditor` (`codeeditor.md`)](codeeditor.md) — Uses `Canvas2DExtSurface` to render virtualized syntax-highlighted source code, gutter diagnostics, and `CodeMinimap`.
  - [`:extensions:box2d` (`box2d.md`)](box2d.md) — Uses `Canvas2DExtSurface` in `Box2DDebugDrawer` to visualize 2D rigid bodies, fixtures, joints, and AABBs.
  - [`:extensions:imageprocessing` (`imageprocessing.md`)](imageprocessing.md) — Processes `ImageBuffer` instances offscreen on the GPU before drawing them onto `Canvas2DExtSurface`.
  - [`:extensions:canvas3d` (`canvas3d.md`)](canvas3d.md) — Sister 3D canvas extension (`GLCanvasExt`, `VKCanvasExt`).
  - [`:extensions:dsl` (`dsl.md`)](dsl.md) — Base declarative Kotlin UI & Style DSL.
- **Root & Backend Documentation**:
  - [GenericUI Root (`../../README.md`)](../../README.md) · [Extensions Architecture (`../../docs/extensions.md`)](../../docs/extensions.md) · [Source Sets (`../../docs/sourcesets.md`)](../../docs/sourcesets.md) · [Backends (`../../docs/backends.md`)](../../docs/backends.md) · [Gradle Plugin (`../../docs/gradle-plugin.md`)](../../docs/gradle-plugin.md)
  - [Desktop Backends (`../../desktop/README.md`)](../../desktop/README.md) · [Mobile Backends (`../../mobile/README.md`)](../../mobile/README.md) · [Web Backend (`../../web/README.md`)](../../web/README.md)
