# `:extensions:graphics:gpu` — Unified Vulkan Primary & OpenGL ES Fallback GPU Foundation

> **Navigation**: [**GenericUI Root (`../../README.md`)**](../../README.md) · [**Extensions Index (`../README.md`)**](../README.md) · [**Root Extensions Guide (`../../docs/extensions.md`)**](../../docs/extensions.md) · [**Fragments & Windows (`../../docs/fragments-and-windows.md`)**](../../docs/fragments-and-windows.md) · [**Source Sets (`../../docs/sourcesets.md`)**](../../docs/sourcesets.md) · [**Backends (`../../docs/backends.md`)**](../../docs/backends.md) · [**Gradle Plugin (`../../docs/gradle-plugin.md`)**](../../docs/gradle-plugin.md)

`:extensions:graphics:gpu` is the foundational **Single Consolidated KMP Project** GPU compute, shader compilation, texture/buffer allocation, VBlank synchronization (`VBlankWatcher`, `VBlankTimer`), and double-buffered surface extension for GenericUI. It enforces a strict **Vulkan 1.2+ SPIR-V Primary + OpenGL ES 3.0+ Fallback** architecture across Desktop, Mobile, Swing, and Web, with typed `GpuAcceleration` (`HARDWARE`, `SOFTWARE`) and `GpuAccelerationPolicy` (`PREFER_HARDWARE`, `REQUIRE_HARDWARE`, `SOFTWARE_ONLY`), and powers [`:extensions:graphics:imageprocessing`](imageprocessing.md), [`:extensions:graphics:canvas2d`](canvas2d.md), [`:extensions:graphics:canvas3d`](canvas3d.md), [`:extensions:graphics:threejs`](threejs.md), [`:extensions:physics:box3d`](box3d.md), and [`:extensions:ui:alien`](alien.md).

---

## 1. Architecture, Source-Set Layout, & Platform Backend Priority

`:extensions:graphics:gpu` compiles all platform GPU backends cleanly inside a consolidated multiplatform source-set hierarchy (`commonMain`, `systemMain`, `desktopMain` / `nativeDesktopMain`, `jvmMain`, `mobileMain`, `androidMain`, `iosMain`, `webMain`) so each platform target links only its native GPU drivers:

```mermaid
flowchart LR
    GpuDevice["GpuDevice.acquireOffscreenContext(preferVulkan = true, policy = PREFER_HARDWARE)"]
    Primary["1. Primary Backend: GpuBackendType.VULKAN<br/>(Vulkan 1.2+ SPIR-V / MoltenVK / WebGPU)"]
    Fallback["2. Secondary Fallback: GpuBackendType.OPENGL_ES<br/>(OpenGL ES 3.0+ / Google ANGLE D3D11 / WebGL2)"]
    Software["3. Software Emulation: GpuAcceleration.SOFTWARE<br/>(Deterministic CPU Shader & Rasterization Pipeline)"]

    GpuDevice --> Primary
    Primary -->|If Vulkan unavailable| Fallback
    Fallback -->|If hardware unavailable & policy allows| Software
```

| Target Family | Source Set | Primary GPU Pipeline (`GpuBackendType.VULKAN`) | Secondary Fallback (`GpuBackendType.OPENGL_ES`) | Backend Documentation |
| :--- | :--- | :--- | :--- | :--- |
| **Windows (`mingwX64`)** | `nativeDesktopMain` | Native `vulkan-1.dll` (`DesktopVKContextImpl` + `DesktopGpuDynamicLoader`) | **Google ANGLE** (`libEGL.dll` + `libGLESv2.dll` over Direct3D 11 via `DesktopGLContextImpl`) | [Desktop Backends (`../../desktop/README.md`)](../../desktop/README.md) |
| **macOS (`macosArm64`, `macosX64`)** | `nativeDesktopMain` | **MoltenVK** (`libMoltenVK.dylib` over Apple Metal) | Native Metal-backed OpenGL ES / CGL | [Desktop Backends (`../../desktop/README.md`)](../../desktop/README.md) |
| **Linux GTK4 & KDE (`linuxX64`, `linuxArm64`)** | `nativeDesktopMain` | Native `libvulkan.so.1` (`VKContext`) | Native `libEGL.so.1` + `libGLESv2.so.2` (`GLContext`) | [Desktop Backends (`../../desktop/README.md`)](../../desktop/README.md) |
| **Desktop JVM (`:swing`)** | `jvmMain` | **LWJGL 3 Vulkan** (`org.lwjgl.vulkan` + `org.lwjgl.shaderc` via `SwingLwjglGpuContext`) | **LWJGL 3 OpenGL / GLES** (`org.lwjgl.opengl` / `org.lwjgl.opengles`) | [Desktop Backends (`../../desktop/README.md`)](../../desktop/README.md) |
| **Android (`:android`)** | `androidMain` | Android NDK Vulkan 1.1/1.3 (`AndroidVKContextImpl`) | Android `EGL14` / `GLES30` (`AndroidGLContextImpl`) | [Mobile Backends (`../../mobile/README.md`)](../../mobile/README.md) |
| **iOS (`:ios`)** | `iosMain` | **MoltenVK** over Metal (`IosVKContextImpl`) | Apple `EAGLContext` OpenGL ES 3.0 (`IosGLContextImpl`) | [Mobile Backends (`../../mobile/README.md`)](../../mobile/README.md) |
| **Web (`js`, `wasmJs`)** | `webMain` | **WebGPU** (`navigator.gpu` via `WebVKContextImpl`) | **WebGL2** (`WebGL2RenderingContext` via `WebGLContextImpl`) | [Web Backend (`../../web/README.md`)](../../web/README.md) |

---

## 2. Gradle Plugin Configuration

Enable `:extensions:graphics:gpu` via the [GenericUI Gradle Plugin (`../../docs/gradle-plugin.md`)](../../docs/gradle-plugin.md). The plugin automatically links `:extensions:graphics:gpu` across all configured backends and registers the backend GPU provider in each target's generated launcher:

```kotlin
plugins {
    id("com.nexustools.genericui")
}

genericui {
    app {
        id = "com.example.gpulab"
        name = "GPU Compute Lab"
    }
    backends {
        allExceptSwing()
    }
    extensions {
        gpu() // Wires :extensions:graphics:gpu across all configured backends automatically
    }
}
```

> **Note**: Enabling higher-level GPU extensions such as `imageProcessing()`, `canvas2d()`, `canvas3d()`, `threeJs()`, `box2d()`, `box3d()`, or `alien()` automatically enables `:extensions:graphics:gpu` transitively.

---

## 3. Core API Reference (`com.nexustools.genericui.ext.gpu` & `component.media`)

| Class / Interface / Enum | Description & Key Members |
| :--- | :--- |
| `GpuDevice` | Singleton factory & provider registry. Key methods: `querySupport(backendType): GpuBackendSupport`, `isSupported(backendType): Boolean`, `isHardwareAccelerated(backendType): Boolean`, `acquireOffscreenContext(preferVulkan: Boolean = true, accelerationPolicy: GpuAccelerationPolicy = GpuAccelerationPolicy.PREFER_HARDWARE): GpuOffscreenContext`, `createGLContext(width, height, attributes, accelerationPolicy): GLContext`, `createVKContext(width, height, attributes, accelerationPolicy): VKContext`. |
| `GpuBackendType` | Enum identifying the GPU API family: `VULKAN` (Vulkan 1.2+ / MoltenVK / WebGPU), `OPENGL_ES` (OpenGL ES 3.0+ / ANGLE / WebGL2). |
| `GpuAcceleration` | Enum identifying the execution path: `HARDWARE` (native GPU driver), `SOFTWARE` (CPU software rasterizer/emulator). |
| `GpuAccelerationPolicy` | Enum specifying acceleration requirement: `PREFER_HARDWARE`, `REQUIRE_HARDWARE`, `SOFTWARE_ONLY`. |
| `GpuBackendSupport` | Value descriptor reporting runtime support (`backendType`, `isSupported`, `isHardwareAccelerated`, `isSoftwareAvailable`, `driverDescription`). |
| `GpuOffscreenContext` | Cross-platform headless/offscreen GPU compute & fragment shader context. Key members: `backendType`, `deviceName`, `apiVersion`, `vkContext: VKContext?`, `glContext: GLContext?`, `uploadTexture(imageBuffer): GpuTexture`, `updateTexture(texture, imageBuffer)`, `createEmptyTexture(width, height, pixelFormat): GpuTexture`, `downloadTexture(texture): ImageBuffer`, `destroyTexture(texture)`, `createBuffer(sizeBytes, usage): GpuBuffer`, `uploadBufferFloats(buffer, data)`, `destroyBuffer(buffer)`, `compileComputeOrFragmentKernel(vulkanGlsl450, gles300Shader): GpuShaderProgram`, `dispatchImageKernel(program, inputs, output, uniforms)`, `destroyShaderProgram(program)`. |
| `GpuRenderContext` | Common super-interface for `GLContext` and `VKContext` (`backendType`, `acceleration`, `surfaceWidth`, `surfaceHeight`, `resizeSurface`, `readPixels`, `dispose`). |
| `GLContext` / `GLContextAttributes` | Strongly-typed WebGL2-style OpenGL ES 3.0+ / ANGLE context (`contextInfo: GLContextInfo`, `compileShader`, `createProgram`, `createBuffer`, `createTexture`, `createFramebuffer`, `texImage2D`, `drawArrays`, `drawElements`, `readPixels`) using typed enums (`GLClearBuffer`, `GLCapability`, `GLBlendFactor`, `GLBlendEquation`, `GLCompareFunction`, `GLBufferTarget`, `GLBufferUsage`, `GLDataType`, `GLTextureTarget`, `GLTextureFormat`, `GLPrimitive`, `GLIndexType`, etc.). |
| `VKContext` / `VKDeviceAttributes` | Strongly-typed WebGPU-style Vulkan 1.2+ / MoltenVK / WebGPU context (`contextInfo: VKContextInfo`, `createShaderModule`, `createRenderPipeline`, `createBuffer`, `writeBuffer`, `createTexture`, `writeTexture`, `createSampler`, `createBindGroup`, `beginRenderPass`, `setPipeline`, `setVertexBuffer`, `setIndexBuffer`, `setBindGroup`, `draw`, `drawIndexed`, `endRenderPass`, `readPixels`). |

---

## 4. Imperative Kotlin Examples

### Example 1: Custom Dual-Target GPU Kernel Dispatch (`Vulkan #version 450` + `GLES #version 300 es`)

```kotlin
import com.nexustools.genericui.ext.gpu.GpuDevice
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat

fun applyCustomSepiaToneOnGpu(inputImage: ImageBuffer, intensity: Float = 0.85f): ImageBuffer {
    // 1. Acquire the active GPU context (Vulkan 1.2+ primary, OpenGL ES / ANGLE fallback)
    val gpu = GpuDevice.acquireOffscreenContext(preferVulkan = true)
    println("Executing on GPU backend: ${gpu.backendType} (${gpu.deviceName} - ${gpu.apiVersion})")

    // 2. Upload the source ImageBuffer and allocate an output RGBA_8888 texture
    val srcTex = gpu.uploadTexture(inputImage)
    val dstTex = gpu.createEmptyTexture(inputImage.width, inputImage.height, PixelFormat.RGBA_8888)

    // 3. Compile a dual-target shader program (SPIR-V #version 450 primary + #version 300 es fallback)
    val vulkanGlsl450 = """
        #version 450
        layout(location = 0) in vec2 vUv;
        layout(location = 0) out vec4 outColor;
        layout(set = 0, binding = 0) uniform sampler2D uInputTex;
        layout(push_constant) uniform PushConstants {
            float intensity;
        } ubo;
        void main() {
            vec4 c = texture(uInputTex, vUv);
            vec3 sepia = vec3(
                dot(c.rgb, vec3(0.393, 0.769, 0.189)),
                dot(c.rgb, vec3(0.349, 0.686, 0.168)),
                dot(c.rgb, vec3(0.272, 0.534, 0.131))
            );
            outColor = vec4(mix(c.rgb, clamp(sepia, 0.0, 1.0), ubo.intensity), c.a);
        }
    """.trimIndent()

    val gles300Shader = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        out vec4 outColor;
        uniform sampler2D uInputTex;
        uniform float uIntensity;
        void main() {
            vec4 c = texture(uInputTex, vUv);
            vec3 sepia = vec3(
                dot(c.rgb, vec3(0.393, 0.769, 0.189)),
                dot(c.rgb, vec3(0.349, 0.686, 0.168)),
                dot(c.rgb, vec3(0.272, 0.534, 0.131))
            );
            outColor = vec4(mix(c.rgb, clamp(sepia, 0.0, 1.0), uIntensity), c.a);
        }
    """.trimIndent()

    val program = gpu.compileComputeOrFragmentKernel(vulkanGlsl450, gles300Shader)

    try {
        // 4. Dispatch the GPU kernel and download the processed ImageBuffer
        gpu.dispatchImageKernel(
            program = program,
            inputs = listOf(srcTex),
            output = dstTex,
            uniforms = floatArrayOf(intensity)
        )
        return gpu.downloadTexture(dstTex)
    } finally {
        // 5. Release GPU resources deterministically
        gpu.destroyShaderProgram(program)
        gpu.destroyTexture(srcTex)
        gpu.destroyTexture(dstTex)
    }
}
```

### Example 2: Allocating Storage Buffers & Double-Buffered GPU Surfaces

```kotlin
import com.nexustools.genericui.ext.gpu.GpuBufferUsage
import com.nexustools.genericui.ext.gpu.GpuDevice
import com.nexustools.genericui.ext.gpu.GpuDoubleBufferedSurface

fun setupDoubleBufferedGpuSurface() {
    val gpu = GpuDevice.acquireOffscreenContext(preferVulkan = true)

    // Allocate a uniform/storage float buffer on the GPU
    val matrixFloats = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )
    val ubo = gpu.createBuffer(sizeBytes = matrixFloats.size * 4L, usage = GpuBufferUsage.UNIFORM)
    gpu.uploadBufferFloats(ubo, matrixFloats)

    // Create a tear-free double-buffered surface for offscreen rendering
    val surface = GpuDoubleBufferedSurface(width = 1920, height = 1080, context = gpu)
    surface.swapBuffers()

    // Cleanup
    gpu.destroyBuffer(ubo)
    surface.dispose()
}
```

---

## 5. Declarative `:dsl` Integration

While `:extensions:graphics:gpu` is a device/context API without its own UI widgets, its `GpuDevice`, `VKDeviceAttributes`, and `GLContextAttributes` types integrate directly into [`:extensions:ui:dsl`](dsl.md) and [`:extensions:graphics:canvas3d:dsl`](canvas3d.md):

```kotlin
import com.nexustools.genericui.component.media.VKDeviceAttributes
import com.nexustools.genericui.component.media.VKRenderPassDescriptor
import com.nexustools.genericui.ext.canvas3d.dsl.canvas3DVK
import com.nexustools.genericui.ext.dsl.buildDiv
import com.nexustools.genericui.ext.dsl.h3
import com.nexustools.genericui.ext.dsl.span
import com.nexustools.genericui.ext.gpu.GpuDevice

val gpuStatusPanel = buildDiv(classes = "p-3") {
    val ctx = GpuDevice.acquireOffscreenContext(preferVulkan = true)
    h3("Active GPU: ${ctx.deviceName}")
    span("Backend: ${ctx.backendType} • API: ${ctx.apiVersion}")

    canvas3DVK(
        attributes = VKDeviceAttributes(srgbSwapchain = true),
        width = 800,
        height = 450,
        onRender = { vkCtx, _, _, _ ->
            vkCtx.beginRenderPass(VKRenderPassDescriptor())
            // Issue Vulkan draw calls synchronized to VBlank
            vkCtx.endRenderPass()
        }
    )
}
```

---

## 6. Related Extensions & Cross-References

- **Consumer Extensions**:
  - [`:extensions:graphics:imageprocessing` (`imageprocessing.md`)](imageprocessing.md) — Uses `GpuOffscreenContext` for hardware-accelerated `ImageBuffer2DContext`, multi-pass GPU image filters, and layer compositing.
  - [`:extensions:graphics:canvas2d` (`canvas2d.md`)](canvas2d.md) — Uses `AbstractCanvas`, `VBlankWatcher`, and `ImageBuffer2DContext` for tear-free 2D rendering.
  - [`:extensions:graphics:canvas3d` (`canvas3d.md`)](canvas3d.md) — Exposes interactive `Canvas3D` and `ScrollCanvas3D` widgets backed by `:extensions:graphics:gpu`.
  - [`:extensions:graphics:threejs` (`threejs.md`)](threejs.md) — Renders 3D PBR scene graphs atop `:extensions:graphics:canvas3d` and `:extensions:graphics:gpu`.
  - [`:extensions:physics:box3d` (`box3d.md`)](box3d.md) — Uses `:extensions:graphics:threejs` and `:extensions:graphics:canvas3d` for 3D rigid-body visualization.
  - [`:extensions:ui:dsl` (`dsl.md`)](dsl.md) — Declarative Kotlin UI & Style DSL.
- **Root & Backend Documentation**:
  - [GenericUI Root (`../../README.md`)](../../README.md) · [Extensions Architecture (`../../docs/extensions.md`)](../../docs/extensions.md) · [Source Sets (`../../docs/sourcesets.md`)](../../docs/sourcesets.md) · [Backends (`../../docs/backends.md`)](../../docs/backends.md) · [Gradle Plugin (`../../docs/gradle-plugin.md`)](../../docs/gradle-plugin.md)
  - [Desktop Backends (`../../desktop/README.md`)](../../desktop/README.md) · [Mobile Backends (`../../mobile/README.md`)](../../mobile/README.md) · [Web Backend (`../../web/README.md`)](../../web/README.md)
