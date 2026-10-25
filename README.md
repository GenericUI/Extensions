# GenericUI — Extensions Submodule (`:extensions`)

> **Main Repository**: [**GenericUI/GenericUI**](https://github.com/GenericUI/GenericUI)

This repository is the **Extensions Submodule (`:extensions`)** of the [**GenericUI**](https://github.com/GenericUI/GenericUI) Kotlin Multiplatform Native-First UI framework. It contains first-party cross-platform extension modules for GPU compute/rendering, image processing, 2D/3D canvases, VLC/hardware-accelerated media playback, WebView, IDE code editing, AI Studio chat, Three.js 3D scene rendering, Box2D/Box3D physics, declarative Kotlin UI DSLs, and each component extension's `:dsl` submodule.

---

## Extension Modules

| Extension Module | DSL Submodule | Description |
| :--- | :--- | :--- |
| **`:extensions:gpu`** | — | Cross-platform GPU device, double-buffered surface (`GpuDoubleBufferedSurface`), and offscreen shader/compute pipeline (Vulkan primary + OpenGL ES / Google ANGLE / MoltenVK / WebGPU / WebGL2 fallback) across `common`, `desktop`, `mobile`, `swing`, and `web`. |
| **`:extensions:imageprocessing`** | — | Offscreen GPU-accelerated image processing (`GpuImageProcessor`, `GpuImageCompositor`, `GaussianBlurFilter`, `ConvolutionKernelFilter`, `LevelsFilter`, `ColorMatrixFilter`, `ChromaKeyFilter`, `Histogram`). |
| **`:extensions:canvas2d`** | **`:extensions:canvas2d:dsl`** | Hardware-accelerated 2D vector/raster canvas (`Canvas2DExtSurface`), offscreen 2D canvas (`OffscreenCanvas2D`), and cubic-Bezier curve editor (`CurveEditorExt`). |
| **`:extensions:canvas3d`** | **`:extensions:canvas3d:dsl`** | Vulkan (`VKCanvasExt`), OpenGL ES (`GLCanvasExt`), and offscreen 3D canvas (`OffscreenCanvas3D`) with VBlank `deltaMs` render loops. |
| **`:extensions:media`** | **`:extensions:media:dsl`** | Cross-platform `VideoPlayerExt` (powered by VLC + HDR tone-mapping on Desktop), `AudioPlayerExt`, and real-time FFT `AudioAnalyzerExt`. |
| **`:extensions:webview`** | **`:extensions:webview:dsl`** | Native OS WebView integration across Desktop, Mobile, Swing, and Web. |
| **`:extensions:codeeditor`** | **`:extensions:codeeditor:dsl`** | Multi-language syntax-highlighted `CodeEditor` with gutter line numbers, diagnostics, and minimap (`CodeMinimap`). |
| **`:extensions:aichat`** | **`:extensions:aichat:dsl`** | Multi-provider AI Studio Chat container (`AiChatContainer`, `AiChatSession`, `AiToolExecutor`) supporting OpenAI, Anthropic Claude, Google Gemini, xAI Grok, Ollama, LM Studio, reasoning effort selectors, and automated multi-turn tool execution. |
| **`:extensions:threejs`** | **`:extensions:threejs:dsl`** | Kotlin Multiplatform port of Three.js (`Scene`, `PerspectiveCamera`, `OrthographicCamera`, `WebGLRenderer`, `Mesh`, PBR materials, lights, `OrbitControls`, `Raycaster`, loaders, animations). |
| **`:extensions:box2d`** | **`:extensions:box2d:dsl`** | 2D rigid-body physics engine (`World2D`, `Body2D`, shapes, joints, raycasting, and `Box2DDebugDrawer`). |
| **`:extensions:box3d`** | **`:extensions:box3d:dsl`** | 3D rigid-body physics engine (`World3D`, `RigidBody3D`, colliders, joints, character controller, and Three.js `Object3D` transform synchronization). |
| **`:extensions:dsl`** | *(Base DSL)* | `kotlinx.html`-inspired in-place declarative UI builder DSL (`div`, `row`, `col`, `button`, `select`, `table`, `style { ... }`) + MVC base classes (`MvcModel`, `MvcView`, `MvcController`). |

---

## Automatic `:dsl` Submodule Wiring

When using the [GenericUI Gradle Plugin](https://github.com/GenericUI/GenericUI), enabling `dsl()` alongside any component-providing extension (`aiChat()`, `codeEditor()`, `canvas2d()`, `canvas3d()`, `media()`, `webView()`, `threeJs()`, `box2d()`, `box3d()`) automatically adds both the extension and its `:extensions:<ext>:dsl` submodule:

```kotlin
plugins {
    id("com.nexustools.genericui")
}

genericui {
    backends {
        allExceptSwing()
    }
    extensions {
        dsl()
        aiChat()   // automatically includes :extensions:aichat and :extensions:aichat:dsl
        threeJs()  // automatically includes :extensions:threejs and :extensions:threejs:dsl
    }
}
```

---

## Related GenericUI Repositories

- **Main Repository**: [https://github.com/GenericUI/GenericUI](https://github.com/GenericUI/GenericUI)
- **Desktop Backends (`:desktop`)**: [https://github.com/GenericUI/Desktop](https://github.com/GenericUI/Desktop)
- **Mobile Backends (`:mobile`)**: [https://github.com/GenericUI/Mobile](https://github.com/GenericUI/Mobile)
- **Web Backend (`:web`)**: [https://github.com/GenericUI/Web](https://github.com/GenericUI/Web)
- **UI Testing Suite (`:tests` / `JestElectricRunner`)**: [https://github.com/GenericUI/Tests](https://github.com/GenericUI/Tests)
