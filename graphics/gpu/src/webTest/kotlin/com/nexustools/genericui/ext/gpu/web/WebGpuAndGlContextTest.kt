package com.nexustools.genericui.ext.gpu.web

import com.nexustools.genericui.component.media.GLBlendEquation
import com.nexustools.genericui.component.media.GLBlendFactor
import com.nexustools.genericui.component.media.GLBufferTarget
import com.nexustools.genericui.component.media.GLBufferUsage
import com.nexustools.genericui.component.media.GLCapability
import com.nexustools.genericui.component.media.GLClearBuffer
import com.nexustools.genericui.component.media.GLDataType
import com.nexustools.genericui.component.media.GLFramebufferAttachment
import com.nexustools.genericui.component.media.GLFramebufferStatus
import com.nexustools.genericui.component.media.GLFramebufferTarget
import com.nexustools.genericui.component.media.GLPrimitive
import com.nexustools.genericui.component.media.GLReadFormat
import com.nexustools.genericui.component.media.GLTextureFormat
import com.nexustools.genericui.component.media.GLTextureParameters
import com.nexustools.genericui.component.media.GLTextureTarget
import com.nexustools.genericui.component.media.GLVertexAttribLayout
import com.nexustools.genericui.enums.media.ShaderType
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.web.WebAbstractCanvasImpl
import com.nexustools.genericui.web.WebBrowserDomBridge
import com.nexustools.genericui.web.WebGLContextImpl
import com.nexustools.genericui.web.WebGl2BrowserBridge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebGpuAndGlContextTest {

    @Test
    fun testWebGpuProviderSupportDetectionAndFallback() {
        WebGpuProvider.install(enableWebGpu = false, enableWebGl2 = true)

        val vkSupport = WebGpuProvider.detectSupport(GpuBackendType.VULKAN)
        val glSupport = WebGpuProvider.detectSupport(GpuBackendType.OPENGL_ES)

        assertFalse(vkSupport.hardwareAvailable)
        assertTrue(vkSupport.softwareAvailable)
        assertTrue(glSupport.hardwareAvailable)
        assertTrue(glSupport.softwareAvailable)

        val acquired = WebGpuProvider.acquireWebContext(preferWebGpu = false)
        assertEquals(GpuBackendType.OPENGL_ES, acquired.backendType)
        assertNotNull(acquired.glContext)

        // Restore default headless configuration
        WebGpuProvider.install()
    }

    @Test
    fun testWebAbstractCanvasSingleFlightRafAndDomResize() {
        var resizedCount = 0
        var lastResizedW = 0
        var lastResizedH = 0

        val canvas = object : WebAbstractCanvasImpl<Unit>("test-canvas") {
            override fun getRenderingContext() = Unit

            override fun resizeSurface(width: Int, height: Int) {
                super.resizeSurface(width, height)
                resizedCount++
                lastResizedW = width
                lastResizedH = height
            }
        }

        var frameCount = 0
        canvas.setVBlankLoopActive(true) {
            frameCount++
        }
        assertTrue(canvas.isRafScheduled)
        assertEquals("true", canvas.nativeWidget.getAttribute("data-raf-scheduled"))

        // Simulate DOM layout size change on the <canvas> element before frame steps
        canvas.nativeWidget.clientWidth = 1280
        canvas.nativeWidget.clientHeight = 720

        canvas.stepRequestAnimationFrame(16.67)
        assertEquals(1, frameCount)
        assertEquals(1, resizedCount)
        assertEquals(1280, lastResizedW)
        assertEquals(720, lastResizedH)
        assertEquals(1280, canvas.getSurfaceWidth())
        assertEquals(720, canvas.getSurfaceHeight())
        assertTrue(canvas.isRafScheduled)

        // Stepping again with the same clientWidth/clientHeight does not trigger redundant resize
        canvas.stepRequestAnimationFrame(33.34)
        assertEquals(2, frameCount)
        assertEquals(1, resizedCount)

        canvas.setVBlankLoopActive(false) {}
        assertFalse(canvas.isRafScheduled)
        assertEquals("false", canvas.nativeWidget.getAttribute("data-raf-scheduled"))
    }

    @Test
    fun testWebGlContextFullPipelineAndFboRendering() {
        val gl = WebGLContextImpl(initialWidth = 64, initialHeight = 64)
        if (WebBrowserDomBridge.isBrowserEnvironment && WebGl2BrowserBridge.isWebGl2Supported()) {
            assertTrue(gl.hwContextId > 0, "Expected real browser WebGL2 hardware context ID > 0")
        } else {
            assertEquals(0, gl.hwContextId)
        }

        gl.viewport(0, 0, 64, 64)
        gl.clearColor(0.25f, 0.5f, 0.75f, 1.0f)
        gl.clear(setOf(GLClearBuffer.COLOR, GLClearBuffer.DEPTH))
        gl.enable(GLCapability.BLEND)
        gl.blendFunc(GLBlendFactor.SRC_ALPHA, GLBlendFactor.ONE_MINUS_SRC_ALPHA)
        gl.blendEquation(GLBlendEquation.FUNC_ADD)

        val vs = gl.createShader(
            ShaderType.VERTEX,
            """
            #version 300 es
            layout(location = 0) in vec2 aPosition;
            uniform float uScale;
            void main() {
                gl_Position = vec4(aPosition * uScale, 0.0, 1.0);
            }
            """.trimIndent()
        )
        val fs = gl.createShader(
            ShaderType.FRAGMENT,
            """
            #version 300 es
            precision highp float;
            out vec4 fragColor;
            void main() {
                fragColor = vec4(1.0, 0.0, 0.0, 1.0);
            }
            """.trimIndent()
        )
        assertTrue(vs.compileStatus.success)
        assertTrue(fs.compileStatus.success)

        val program = gl.createProgram(vs, fs)
        assertTrue(program.linkStatus.success)
        gl.useProgram(program)

        val uScale = gl.getUniformLocation(program, "uScale")
        assertNotNull(uScale)
        gl.uniform1f(uScale, 1.0f)

        val vbo = gl.createBuffer()
        gl.bindBuffer(GLBufferTarget.ARRAY_BUFFER, vbo)
        gl.bufferData(
            GLBufferTarget.ARRAY_BUFFER,
            floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f),
            GLBufferUsage.DYNAMIC_DRAW
        )
        gl.enableVertexAttribArray(0)
        gl.vertexAttribPointer(
            GLVertexAttribLayout(
                index = 0,
                size = 2,
                type = GLDataType.FLOAT,
                normalized = false,
                stride = 8,
                offset = 0
            )
        )

        val tex = gl.createTexture()
        gl.activeTexture(0)
        gl.bindTexture(GLTextureTarget.TEXTURE_2D, tex)
        gl.texImage2D(GLTextureTarget.TEXTURE_2D, 0, GLTextureFormat.RGBA8, 32, 32, null)
        gl.texParameters(GLTextureTarget.TEXTURE_2D, GLTextureParameters())

        val fbo = gl.createFramebuffer()
        gl.bindFramebuffer(GLFramebufferTarget.FRAMEBUFFER, fbo)
        gl.framebufferTexture2D(
            GLFramebufferTarget.FRAMEBUFFER,
            GLFramebufferAttachment.COLOR_ATTACHMENT0,
            GLTextureTarget.TEXTURE_2D,
            tex,
            0
        )
        assertEquals(GLFramebufferStatus.COMPLETE, gl.checkFramebufferStatus(GLFramebufferTarget.FRAMEBUFFER))

        gl.drawArrays(GLPrimitive.TRIANGLE_STRIP, 0, 4)
        gl.bindFramebuffer(GLFramebufferTarget.FRAMEBUFFER, null)

        val pixels = gl.readPixels(0, 0, 64, 64, GLReadFormat.RGBA8)
        assertEquals(64 * 64 * 4, pixels.size)

        gl.deleteFramebuffer(fbo)
        gl.deleteTexture(tex)
        gl.deleteBuffer(vbo)
        gl.deleteProgram(program)
        gl.deleteShader(vs)
        gl.deleteShader(fs)
        gl.destroyContext()
    }
}
