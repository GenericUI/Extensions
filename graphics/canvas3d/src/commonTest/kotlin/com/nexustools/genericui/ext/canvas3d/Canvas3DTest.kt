package com.nexustools.genericui.ext.canvas3d

import com.nexustools.genericui.component.media.Canvas3D
import com.nexustools.genericui.component.media.GLBufferTarget
import com.nexustools.genericui.component.media.GLBufferUsage
import com.nexustools.genericui.component.media.GLClearBuffer
import com.nexustools.genericui.component.media.GLContext
import com.nexustools.genericui.component.media.GLDataType
import com.nexustools.genericui.component.media.GLFramebufferAttachment
import com.nexustools.genericui.component.media.GLFramebufferTarget
import com.nexustools.genericui.component.media.GLPrimitive
import com.nexustools.genericui.component.media.GLReadFormat
import com.nexustools.genericui.component.media.GLTextureFormat
import com.nexustools.genericui.component.media.GLTextureTarget
import com.nexustools.genericui.component.media.GLVertexAttribLayout
import com.nexustools.genericui.component.media.GpuShaderStageSource
import com.nexustools.genericui.component.media.VKBufferDescriptor
import com.nexustools.genericui.component.media.VKBufferUsage
import com.nexustools.genericui.component.media.VKContext
import com.nexustools.genericui.component.media.VKLoadOp
import com.nexustools.genericui.component.media.VKPrimitiveTopology
import com.nexustools.genericui.component.media.VKRenderPassDescriptor
import com.nexustools.genericui.component.media.VKRenderPipelineDescriptor
import com.nexustools.genericui.component.media.VKVertexAttribute
import com.nexustools.genericui.component.media.VKVertexLayout
import com.nexustools.genericui.desktop.canvas.DesktopGLContextImpl
import com.nexustools.genericui.desktop.canvas.DesktopVKContextImpl
import com.nexustools.genericui.enums.media.ShaderType
import com.nexustools.genericui.ext.gpu.GpuAcceleration
import com.nexustools.genericui.ext.gpu.GpuAccelerationPolicy
import com.nexustools.genericui.ext.gpu.GpuBackendSupport
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.style.color.RgbaColor
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Canvas3DTest {

    @AfterTest
    fun tearDown() {
        Canvas3DRegistry.resetForTesting()
    }

    @Test
    fun testFailFastWhenNoProviderRegistered() {
        Canvas3DRegistry.resetForTesting()
        assertFailsWith<IllegalStateException> {
            Canvas3D.detectSupport(GpuBackendType.VULKAN)
        }
        assertFailsWith<IllegalStateException> {
            Canvas3D(backend = GpuBackendType.VULKAN)
        }
    }

    @Test
    fun testDetectSupportAndHardwareOnlyPolicyRejectsSoftwareOnly() {
        Canvas3DRegistry.overrideSupportForTesting(
            GpuBackendType.VULKAN,
            GpuBackendSupport(
                backend = GpuBackendType.VULKAN,
                hardwareAvailable = false,
                softwareAvailable = true
            )
        )
        Canvas3DRegistry.overrideSupportForTesting(
            GpuBackendType.OPENGL_ES,
            GpuBackendSupport(
                backend = GpuBackendType.OPENGL_ES,
                hardwareAvailable = true,
                softwareAvailable = false
            )
        )

        val vkSupport = Canvas3D.detectSupport(GpuBackendType.VULKAN)
        assertEquals(GpuBackendType.VULKAN, vkSupport.backend)
        assertEquals(false, vkSupport.hardwareAvailable)
        assertEquals(true, vkSupport.softwareAvailable)

        // HARDWARE_ONLY must fail when only software is available
        assertFailsWith<UnsupportedOperationException> {
            Canvas3D(
                backend = GpuBackendType.VULKAN,
                acceleration = GpuAccelerationPolicy.HARDWARE_ONLY
            )
        }

        // HARDWARE_PREFERRED falls back to SOFTWARE when hardwareAvailable == false and softwareAvailable == true
        val softVkCanvas = Canvas3D(
            backend = GpuBackendType.VULKAN,
            acceleration = GpuAccelerationPolicy.HARDWARE_PREFERRED,
            width = 64,
            height = 64
        )
        val vkCtx = assertNotNull(softVkCanvas.context as? VKContext)
        assertEquals(GpuAcceleration.SOFTWARE, vkCtx.acceleration)

        // SOFTWARE_ONLY must fail when softwareAvailable == false
        assertFailsWith<UnsupportedOperationException> {
            Canvas3D(
                backend = GpuBackendType.OPENGL_ES,
                acceleration = GpuAccelerationPolicy.SOFTWARE_ONLY
            )
        }

        // HARDWARE_ONLY succeeds when hardwareAvailable == true
        val hwGlCanvas = Canvas3D(
            backend = GpuBackendType.OPENGL_ES,
            acceleration = GpuAccelerationPolicy.HARDWARE_ONLY,
            width = 64,
            height = 64
        )
        val glCtx = assertNotNull(hwGlCanvas.context as? GLContext)
        assertEquals(GpuAcceleration.HARDWARE, glCtx.acceleration)
    }

    @Test
    fun testDesktopGLContextImplRendersNonBlackPixelsAndPresentsThroughOffscreenCanvas3D() {
        val width = 64
        val height = 64
        val gl = DesktopGLContextImpl(initialWidth = width, initialHeight = height)
        val offscreen = OffscreenCanvas3D(width = width, height = height, preferVulkan = false)
        assertEquals(0L, offscreen.swapCount)

        val vs = gl.createShader(
            ShaderType.VERTEX,
            """
            #version 300 es
            layout(location = 0) in vec2 aPosition;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
            """.trimIndent()
        )
        val fs = gl.createShader(
            ShaderType.FRAGMENT,
            """
            #version 300 es
            precision mediump float;
            uniform vec4 uColor;
            out vec4 fragColor;
            void main() {
                fragColor = uColor;
            }
            """.trimIndent()
        )
        val prog = gl.createProgram(vs, fs)
        assertTrue(prog.linkStatus.success)

        // Render offscreen into an FBO first, then sample or render directly to default framebuffer
        val fboTex = gl.createTexture()
        gl.bindTexture(GLTextureTarget.TEXTURE_2D, fboTex)
        gl.texImage2D(GLTextureTarget.TEXTURE_2D, 0, GLTextureFormat.RGBA8, width, height, null)
        val fbo = gl.createFramebuffer()
        gl.bindFramebuffer(GLFramebufferTarget.FRAMEBUFFER, fbo)
        gl.framebufferTexture2D(
            GLFramebufferTarget.FRAMEBUFFER,
            GLFramebufferAttachment.COLOR_ATTACHMENT0,
            GLTextureTarget.TEXTURE_2D,
            fboTex
        )
        gl.clearColor(0.1f, 0.2f, 0.3f, 1.0f)
        gl.clear(setOf(GLClearBuffer.COLOR))

        // Bind default framebuffer and draw a bright orange/cyan fullscreen triangle pair via VBO
        gl.bindFramebuffer(GLFramebufferTarget.FRAMEBUFFER, null)
        gl.viewport(0, 0, width, height)
        gl.clearColor(0f, 0f, 0f, 1f)
        gl.clear(setOf(GLClearBuffer.COLOR, GLClearBuffer.DEPTH))

        val vbo = gl.createBuffer()
        gl.bindBuffer(GLBufferTarget.ARRAY_BUFFER, vbo)
        gl.bufferData(
            GLBufferTarget.ARRAY_BUFFER,
            floatArrayOf(
                -1f, -1f,
                1f, -1f,
                1f, 1f,
                -1f, -1f,
                1f, 1f,
                -1f, 1f
            ),
            GLBufferUsage.STATIC_DRAW
        )
        gl.useProgram(prog)
        val uColor = assertNotNull(gl.getUniformLocation(prog, "uColor"))
        gl.uniform4f(uColor, 0.9f, 0.4f, 0.15f, 1.0f)
        gl.enableVertexAttribArray(0)
        gl.vertexAttribPointer(GLVertexAttribLayout(index = 0, size = 2, type = GLDataType.FLOAT))
        gl.drawArrays(GLPrimitive.TRIANGLES, 0, 6)

        val rgba = gl.readPixels(0, 0, width, height, GLReadFormat.RGBA8)
        assertEquals(width * height * 4, rgba.size)
        val centerIdx = ((height / 2) * width + (width / 2)) * 4
        val r = rgba[centerIdx].toInt() and 0xFF
        val g = rgba[centerIdx + 1].toInt() and 0xFF
        val b = rgba[centerIdx + 2].toInt() and 0xFF
        assertTrue(r > 200, "Expected non-black red channel > 200, got $r")
        assertTrue(g > 80, "Expected non-black green channel > 80, got $g")
        assertTrue(b > 20, "Expected non-black blue channel > 20, got $b")

        offscreen.writeBackBufferRgba(rgba)
        offscreen.swapBuffers()
        assertEquals(1L, offscreen.swapCount)
        val presentedPx = offscreen.presentFrontImageBuffer().getPixel(width / 2, height / 2)
        assertEquals(r, presentedPx.r)
        assertEquals(g, presentedPx.g)
        assertEquals(b, presentedPx.b)
    }

    @Test
    fun testDesktopVKContextImplRendersNonBlackPixelsAndPresentsThroughOffscreenCanvas3D() {
        val width = 64
        val height = 64
        val vk = DesktopVKContextImpl(initialWidth = width, initialHeight = height)
        val offscreen = OffscreenCanvas3D(width = width, height = height, preferVulkan = true)
        assertEquals(0L, offscreen.swapCount)

        val vsMod = vk.createShaderModule(
            GpuShaderStageSource(
                stage = ShaderType.VERTEX,
                glsl450Source = "#version 450\nlayout(location=0) in vec2 aPos;\nvoid main(){ gl_Position = vec4(aPos,0,1); }"
            )
        )
        val fsMod = vk.createShaderModule(
            GpuShaderStageSource(
                stage = ShaderType.FRAGMENT,
                glsl450Source = "#version 450\nlayout(location=0) out vec4 oColor;\nvoid main(){ oColor = vec4(0.2, 0.8, 0.5, 1.0); }"
            )
        )
        val pipeline = vk.createRenderPipeline(
            VKRenderPipelineDescriptor(
                vertex = vsMod,
                fragment = fsMod,
                vertexLayout = VKVertexLayout(
                    strideBytes = 8,
                    attributes = listOf(VKVertexAttribute(location = 0, components = 2, offsetBytes = 0))
                ),
                topology = VKPrimitiveTopology.TRIANGLE_LIST
            )
        )

        val vbo = vk.createBuffer(
            VKBufferDescriptor(
                sizeBytes = 6L * 2L * 4L,
                usage = setOf(VKBufferUsage.VERTEX, VKBufferUsage.COPY_DST)
            )
        )
        vk.writeBuffer(
            vbo,
            floatArrayOf(
                -1f, -1f,
                1f, -1f,
                1f, 1f,
                -1f, -1f,
                1f, 1f,
                -1f, 1f
            )
        )

        vk.beginRenderPass(
            VKRenderPassDescriptor(
                loadOp = VKLoadOp.CLEAR,
                clearColor = RgbaColor(0, 0, 0, 255)
            )
        )
        vk.bindPipeline(pipeline)
        vk.bindVertexBuffer(vbo)
        vk.pushConstants(pipeline, floatArrayOf(0.2f, 0.85f, 0.55f, 1.0f))
        vk.draw(vertexCount = 6)
        vk.endRenderPass()

        val rgba = vk.readPixels(0, 0, width, height)
        val centerIdx = ((height / 2) * width + (width / 2)) * 4
        val r = rgba[centerIdx].toInt() and 0xFF
        val g = rgba[centerIdx + 1].toInt() and 0xFF
        val b = rgba[centerIdx + 2].toInt() and 0xFF
        assertTrue(g > 180, "Expected green channel > 180, got $g")
        assertTrue(b > 100, "Expected blue channel > 100, got $b")

        offscreen.writeBackBufferRgba(rgba)
        offscreen.swapBuffers()
        assertEquals(1L, offscreen.swapCount)
        val presentedPx = offscreen.presentFrontImageBuffer().getPixel(width / 2, height / 2)
        assertEquals(r, presentedPx.r)
        assertEquals(g, presentedPx.g)
        assertEquals(b, presentedPx.b)
    }
}
