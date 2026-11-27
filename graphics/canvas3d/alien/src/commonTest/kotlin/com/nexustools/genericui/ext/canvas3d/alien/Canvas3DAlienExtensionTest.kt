package com.nexustools.genericui.ext.canvas3d.alien

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.media.Canvas3D
import com.nexustools.genericui.component.media.GLBackendKind
import com.nexustools.genericui.component.media.GLBlendEquation
import com.nexustools.genericui.component.media.GLBlendFactor
import com.nexustools.genericui.component.media.GLBuffer
import com.nexustools.genericui.component.media.GLBufferTarget
import com.nexustools.genericui.component.media.GLBufferUsage
import com.nexustools.genericui.component.media.GLCapability
import com.nexustools.genericui.component.media.GLClearBuffer
import com.nexustools.genericui.component.media.GLCompareFunction
import com.nexustools.genericui.component.media.GLCompileStatus
import com.nexustools.genericui.component.media.GLContext
import com.nexustools.genericui.component.media.GLContextInfo
import com.nexustools.genericui.component.media.GLContextVersion
import com.nexustools.genericui.component.media.GLDataType
import com.nexustools.genericui.component.media.GLFaceSide
import com.nexustools.genericui.component.media.GLFramebuffer
import com.nexustools.genericui.component.media.GLFramebufferAttachment
import com.nexustools.genericui.component.media.GLFramebufferStatus
import com.nexustools.genericui.component.media.GLFramebufferTarget
import com.nexustools.genericui.component.media.GLIndexType
import com.nexustools.genericui.component.media.GLPrimitive
import com.nexustools.genericui.component.media.GLProgram
import com.nexustools.genericui.component.media.GLReadFormat
import com.nexustools.genericui.component.media.GLShader
import com.nexustools.genericui.component.media.GLShadingLanguage
import com.nexustools.genericui.component.media.GLTexture
import com.nexustools.genericui.component.media.GLTextureFormat
import com.nexustools.genericui.component.media.GLTextureParameters
import com.nexustools.genericui.component.media.GLTextureTarget
import com.nexustools.genericui.component.media.GLUniformLocation
import com.nexustools.genericui.component.media.GLVertexAttribLayout
import com.nexustools.genericui.component.media.GLWinding
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.media.ShaderType
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.ext.alien.AlienExtension
import com.nexustools.genericui.ext.alien.AlienFactory
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.impl.AlienImpl
import com.nexustools.genericui.ext.alien.impl.ContainerAlienImpl
import com.nexustools.genericui.ext.canvas3d.Canvas3DRegistry
import com.nexustools.genericui.ext.canvas3d.ScrollCanvas3D
import com.nexustools.genericui.ext.gpu.GpuAcceleration
import com.nexustools.genericui.ext.gpu.GpuAccelerationPolicy
import com.nexustools.genericui.ext.gpu.GpuBackendSupport
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.action.ButtonNativeImpl
import com.nexustools.genericui.nativeimpl.action.StyledButtonNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.FileBrowserDialogNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.StandardDialogsNativeImpl
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl
import com.nexustools.genericui.nativeimpl.layout.StyledContainerNativeImpl
import com.nexustools.genericui.nativeimpl.shell.ApplicationNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import com.nexustools.genericui.nativeimpl.surface.StyledComponentNativeImpl
import com.nexustools.genericui.nativeimpl.system.NetworkMonitorNativeImpl
import com.nexustools.genericui.nativeimpl.system.NotificationManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.PermissionManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.SystemTrayNativeImpl
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Canvas3DAlienExtensionTest {

    private class TestHostNativeFactory : NativeFactory {
        override val backendTarget: BackendTarget = BackendTarget.Windows
        override val backendName: String = "test-host-os"
        override fun supportsTcp(): Boolean = true
        override fun supportsUdp(): Boolean = true

        private val delegate = AlienFactory(this)

        override fun createApplication(): ApplicationNativeImpl<*> = throw UnsupportedOperationException()
        override fun createWindow(): WindowNativeImpl<*> = throw UnsupportedOperationException()
        override fun createMenuBar() = delegate.createMenuBar()
        override fun createMenu() = delegate.createMenu()
        override fun createMenuItem(checkable: Boolean, radio: Boolean) = delegate.createMenuItem(checkable, radio)
        override fun createContextMenu() = delegate.createContextMenu()
        override fun createDockWorkspace() = delegate.createDockWorkspace()
        override fun createDockArea() = delegate.createDockArea()
        override fun createDockGroup() = delegate.createDockGroup()
        override fun createDockPanel() = delegate.createDockPanel()
        override fun createDockWindow() = delegate.createDockWindow()
        override fun createToolPalette() = delegate.createToolPalette()
        override fun createContainer(classPrefix: String): ContainerNativeImpl<*> =
            ContainerAlienImpl(componentClassPrefix = classPrefix, tagName = "div")
        override fun createContainer(classPrefix: String, tagName: String): ContainerNativeImpl<*> =
            ContainerAlienImpl(componentClassPrefix = classPrefix, tagName = tagName)
        override fun createStyledContainer(classPrefix: String, tagName: String): StyledContainerNativeImpl<*> =
            delegate.createStyledContainer(classPrefix, tagName)
        override fun createStyledComponent(classPrefix: String, tagName: String): StyledComponentNativeImpl<*> =
            delegate.createStyledComponent(classPrefix, tagName)
        override fun createBookContainer() = delegate.createBookContainer()
        override fun createScrollView() = delegate.createScrollView()
        override fun createDivider() = delegate.createDivider()
        override fun createText(tagName: String, classPrefix: String) = delegate.createText(tagName, classPrefix)
        override fun createMarkdownView() = delegate.createMarkdownView()
        override fun createImage() = delegate.createImage()
        override fun createTable() = delegate.createTable()
        override fun createButton(classPrefix: String): ButtonNativeImpl<*> =
            object : ButtonNativeImpl<Any?>, AlienImpl(classPrefix) {
                private var txt = ""
                private var icn: String? = null
                override fun setText(text: String) { txt = text }
                override fun getText(): String = txt
                override fun setIcon(icon: String?) { icn = icon }
                override fun getIcon(): String? = icn
            }
        override fun createStyledButton(classPrefix: String): StyledButtonNativeImpl<*> =
            delegate.createStyledButton(classPrefix)
        override fun createDropdown() = delegate.createDropdown()
        override fun createTextInput(multiline: Boolean) = delegate.createTextInput(multiline)
        override fun createStyledTextInput(multiline: Boolean, classPrefix: String) =
            delegate.createStyledTextInput(multiline, classPrefix)
        override fun createSelect() = delegate.createSelect()
        override fun createCheckbox(switchStyle: Boolean, radioStyle: Boolean) =
            delegate.createCheckbox(switchStyle, radioStyle)
        override fun createRangeSlider() = delegate.createRangeSlider()
        override fun createTab(closable: Boolean) = delegate.createTab(closable)
        override fun createTabContainer() = delegate.createTabContainer()
        override fun createNavbar() = delegate.createNavbar()
        override fun createBottomNavigationBar() = delegate.createBottomNavigationBar()
        override fun createProgressBar() = delegate.createProgressBar()
        override fun createSpinner() = delegate.createSpinner()
        override fun createPopover() = delegate.createPopover()
        override fun createDialogWindow() = delegate.createDialogWindow()
        override fun createFileBrowserDialog(): FileBrowserDialogNativeImpl = throw UnsupportedOperationException()
        override fun createStandardDialogs(): StandardDialogsNativeImpl = throw UnsupportedOperationException()
        override fun createLazyList() = delegate.createLazyList()
        override fun createLazyGrid() = delegate.createLazyGrid()
        override fun createTreeView() = delegate.createTreeView()
        override fun createPermissionManager(): PermissionManagerNativeImpl = throw UnsupportedOperationException()
        override fun createSystemTray(): SystemTrayNativeImpl = throw UnsupportedOperationException()
        override fun createNotificationManager(): NotificationManagerNativeImpl = throw UnsupportedOperationException()
        override fun createNetworkMonitor(): NetworkMonitorNativeImpl = throw UnsupportedOperationException()
    }

    private class MockHardwareGlContext(
        width: Int,
        height: Int
    ) : GLContext {
        override val contextInfo: GLContextInfo = GLContextInfo(
            backend = GLBackendKind.ANGLE_D3D11,
            version = GLContextVersion.ES_3_0,
            shadingLanguage = GLShadingLanguage.GLSL_ES_300,
            acceleration = GpuAcceleration.HARDWARE,
            renderer = "Mock Hardware GL Driver",
            vendor = "Test"
        )

        var createdFbos: Int = 0
            private set
        var attachedFboColor: Boolean = false
            private set
        var attachedFboDepth: Boolean = false
            private set
        var lastReadFboId: Int? = null
            private set
        var boundDrawFboId: Int? = null
            private set

        private val texPixels = mutableMapOf<Int, ByteArray>()
        private val texSizes = mutableMapOf<Int, Pair<Int, Int>>()
        private val fboColorTex = mutableMapOf<Int, Int>()
        private var nextId = 1
        private var boundTexId: Int? = null
        private var boundReadFboId: Int? = null
        private var clearRgba = byteArrayOf(0, 0, 0, -1)

        override fun viewport(x: Int, y: Int, width: Int, height: Int) {}
        override fun scissor(x: Int, y: Int, width: Int, height: Int) {}
        override fun clearColor(r: Float, g: Float, b: Float, a: Float) {
            clearRgba = byteArrayOf(
                (r * 255f).toInt().coerceIn(0, 255).toByte(),
                (g * 255f).toInt().coerceIn(0, 255).toByte(),
                (b * 255f).toInt().coerceIn(0, 255).toByte(),
                (a * 255f).toInt().coerceIn(0, 255).toByte()
            )
        }
        override fun clearDepth(depth: Float) {}
        override fun clearStencil(stencil: Int) {}
        override fun clear(buffers: Set<GLClearBuffer>) {
            if (GLClearBuffer.COLOR !in buffers) return
            val texId = boundDrawFboId?.let { fboColorTex[it] } ?: return
            val px = texPixels[texId] ?: return
            var i = 0
            while (i + 3 < px.size) {
                px[i] = clearRgba[0]
                px[i + 1] = clearRgba[1]
                px[i + 2] = clearRgba[2]
                px[i + 3] = clearRgba[3]
                i += 4
            }
        }
        override fun enable(capability: GLCapability) {}
        override fun disable(capability: GLCapability) {}
        override fun blendFunc(sfactor: GLBlendFactor, dfactor: GLBlendFactor) {}
        override fun blendFuncSeparate(srcRGB: GLBlendFactor, dstRGB: GLBlendFactor, srcAlpha: GLBlendFactor, dstAlpha: GLBlendFactor) {}
        override fun blendEquation(mode: GLBlendEquation) {}
        override fun depthFunc(func: GLCompareFunction) {}
        override fun depthMask(flag: Boolean) {}
        override fun cullFace(mode: GLFaceSide) {}
        override fun frontFace(mode: GLWinding) {}
        override fun createShader(type: ShaderType, source: String): GLShader =
            GLShader(nextId++, type, GLCompileStatus(true))
        override fun deleteShader(shader: GLShader) {}
        override fun createProgram(vertexShader: GLShader, fragmentShader: GLShader): GLProgram =
            GLProgram(nextId++, GLCompileStatus(true))
        override fun useProgram(program: GLProgram?) {}
        override fun deleteProgram(program: GLProgram) {}
        override fun getAttribLocation(program: GLProgram, name: String): Int = 0
        override fun getUniformLocation(program: GLProgram, name: String): GLUniformLocation =
            GLUniformLocation(nextId++, name)
        override fun uniform1i(location: GLUniformLocation, v0: Int) {}
        override fun uniform1f(location: GLUniformLocation, v0: Float) {}
        override fun uniform2f(location: GLUniformLocation, v0: Float, v1: Float) {}
        override fun uniform3f(location: GLUniformLocation, v0: Float, v1: Float, v2: Float) {}
        override fun uniform4f(location: GLUniformLocation, v0: Float, v1: Float, v2: Float, v3: Float) {}
        override fun uniformMatrix3fv(location: GLUniformLocation, transpose: Boolean, values: FloatArray) {}
        override fun uniformMatrix4fv(location: GLUniformLocation, transpose: Boolean, values: FloatArray) {}
        override fun createBuffer(): GLBuffer = GLBuffer(nextId++)
        override fun bindBuffer(target: GLBufferTarget, buffer: GLBuffer?) {}
        override fun bufferData(target: GLBufferTarget, data: FloatArray, usage: GLBufferUsage) {}
        override fun bufferData(target: GLBufferTarget, data: IntArray, usage: GLBufferUsage) {}
        override fun bufferData(target: GLBufferTarget, data: ShortArray, usage: GLBufferUsage) {}
        override fun bufferData(target: GLBufferTarget, data: ByteArray, usage: GLBufferUsage) {}
        override fun deleteBuffer(buffer: GLBuffer) {}
        override fun enableVertexAttribArray(index: Int) {}
        override fun disableVertexAttribArray(index: Int) {}
        override fun vertexAttribPointer(layout: GLVertexAttribLayout) {}
        override fun createTexture(): GLTexture = GLTexture(nextId++, 0, 0)
        override fun bindTexture(target: GLTextureTarget, texture: GLTexture?) { boundTexId = texture?.id }
        override fun activeTexture(unit: Int) {}
        override fun texImage2D(target: GLTextureTarget, level: Int, format: GLTextureFormat, width: Int, height: Int, bytes: ByteArray?) {
            val id = boundTexId ?: return
            texSizes[id] = width to height
            texPixels[id] = ByteArray(width * height * 4)
        }
        override fun texParameters(target: GLTextureTarget, params: GLTextureParameters) {}
        override fun generateMipmap(target: GLTextureTarget) {}
        override fun deleteTexture(texture: GLTexture) { texPixels.remove(texture.id) }
        override fun createFramebuffer(): GLFramebuffer {
            createdFbos++
            return GLFramebuffer(nextId++)
        }
        override fun bindFramebuffer(target: GLFramebufferTarget, framebuffer: GLFramebuffer?) {
            when (target) {
                GLFramebufferTarget.FRAMEBUFFER -> {
                    boundDrawFboId = framebuffer?.id
                    boundReadFboId = framebuffer?.id
                }
                GLFramebufferTarget.DRAW_FRAMEBUFFER -> boundDrawFboId = framebuffer?.id
                GLFramebufferTarget.READ_FRAMEBUFFER -> boundReadFboId = framebuffer?.id
            }
        }
        override fun framebufferTexture2D(
            target: GLFramebufferTarget,
            attachment: GLFramebufferAttachment,
            textarget: GLTextureTarget,
            texture: GLTexture,
            level: Int
        ) {
            val fboId = boundDrawFboId ?: return
            if (attachment == GLFramebufferAttachment.COLOR_ATTACHMENT0) {
                fboColorTex[fboId] = texture.id
                attachedFboColor = true
            } else {
                attachedFboDepth = true
            }
        }
        override fun checkFramebufferStatus(target: GLFramebufferTarget): GLFramebufferStatus =
            GLFramebufferStatus.COMPLETE
        override fun deleteFramebuffer(framebuffer: GLFramebuffer) { fboColorTex.remove(framebuffer.id) }
        override fun drawArrays(mode: GLPrimitive, first: Int, count: Int) {}
        override fun drawElements(mode: GLPrimitive, count: Int, type: GLIndexType, offset: Int) {}
        override fun drawArraysInstanced(mode: GLPrimitive, first: Int, count: Int, instanceCount: Int) {}
        override fun drawElementsInstanced(mode: GLPrimitive, count: Int, type: GLIndexType, offset: Int, instanceCount: Int) {}
        override fun readPixels(x: Int, y: Int, width: Int, height: Int, format: GLReadFormat): ByteArray {
            lastReadFboId = boundReadFboId
            val texId = boundReadFboId?.let { fboColorTex[it] } ?: return ByteArray(width * height * 4)
            return texPixels[texId]?.copyOf() ?: ByteArray(width * height * 4)
        }
        override fun finish() {}
        override fun flush() {}
    }

    private lateinit var hostFactory: TestHostNativeFactory

    @BeforeTest
    fun setUp() {
        AlienExtension.clearAlienExtensionImplsForTests()
        Canvas3DRegistry.resetForTesting()
        hostFactory = TestHostNativeFactory()
        GenericUI.initialize(hostFactory, "Canvas3DAlienTestApp")
    }

    @AfterTest
    fun tearDown() {
        Canvas3DAlienExtension.unregister()
        AlienExtension.clearAlienExtensionImplsForTests()
        Canvas3DRegistry.resetForTesting()
    }

    @Test
    fun testCanvas3DUnderAlienFactoryResolvesToCanvas3DAlienImplAndUnregistersCleanly() {
        Canvas3DAlienExtension.register()
        Canvas3DAlienExtension.register()

        val session = AlienRenderSession(
            hostFactory = hostFactory,
            initialWidth = 400,
            initialHeight = 300,
            offscreenOnly = true
        )

        val directCanvas = object : Canvas3D(
            backend = GpuBackendType.OPENGL_ES,
            acceleration = GpuAccelerationPolicy.SOFTWARE_ONLY,
            width = 160,
            height = 120
        ) {
            override fun resolveActiveFactory(): NativeFactory = session.alienFactory
        }
        directCanvas.ensureNativeImpl()
        val impl = assertIs<Canvas3DAlienImpl>(directCanvas.nativeImpl)
        assertEquals("canvas", impl.node.tagName)
        assertEquals(160, impl.getSurfaceWidth())
        assertEquals(120, impl.getSurfaceHeight())

        // Verify parent Container propagation under AlienFactory also resolves Canvas3D -> Canvas3DAlienImpl
        val parentContainer = object : Container("row") {
            override fun resolveActiveFactory(): NativeFactory = session.alienFactory
        }
        val nestedCanvas = Canvas3D(
            backend = GpuBackendType.OPENGL_ES,
            acceleration = GpuAccelerationPolicy.HARDWARE_PREFERRED,
            width = 96,
            height = 64
        )
        parentContainer.addChild(nestedCanvas)
        parentContainer.ensureNativeImpl()
        assertIs<Canvas3DAlienImpl>(nestedCanvas.nativeImpl)

        // Verify ScrollCanvas3D under AlienFactory builds an Alien container & child Canvas3DAlienImpl
        val scrollCanvas = object : ScrollCanvas3D(
            viewportWidth = 100,
            viewportHeight = 80,
            contentWidth = 300,
            contentHeight = 200,
            backend = GpuBackendType.OPENGL_ES,
            acceleration = GpuAccelerationPolicy.SOFTWARE_ONLY
        ) {
            override fun resolveActiveFactory(): NativeFactory = session.alienFactory
        }
        scrollCanvas.ensureNativeImpl()
        assertIs<ContainerAlienImpl>(scrollCanvas.nativeImpl)
        assertIs<Canvas3DAlienImpl>(scrollCanvas.viewportCanvas.nativeImpl)

        // Verify unregister removes the extension hook
        Canvas3DAlienExtension.unregister()
        Canvas3DRegistry.overrideAllSupportForTesting(hardwareAvailable = false, softwareAvailable = true)
        val afterUnregisterCanvas = Canvas3D(
            backend = GpuBackendType.OPENGL_ES,
            acceleration = GpuAccelerationPolicy.SOFTWARE_ONLY
        )
        assertNull(session.alienFactory.createExtensionNativeImpl(afterUnregisterCanvas))
    }

    @Test
    fun testOffscreen3DSceneShadersDepthTestFboReadPixelsAndAlienOffscreenBuffer() {
        Canvas3DAlienExtension.register()
        val width = 128
        val height = 128
        val session = AlienRenderSession(
            hostFactory = hostFactory,
            initialWidth = width,
            initialHeight = height,
            offscreenOnly = true
        )

        val canvas = object : Canvas3D(
            backend = GpuBackendType.OPENGL_ES,
            acceleration = GpuAccelerationPolicy.HARDWARE_PREFERRED,
            width = width,
            height = height
        ) {
            override fun resolveActiveFactory(): NativeFactory = session.alienFactory
        }
        canvas.ensureNativeImpl()
        val impl = assertIs<Canvas3DAlienImpl>(canvas.nativeImpl)
        session.rootNode.addChild(impl.node)

        val gl = assertIs<AlienOffscreenGlContext>(canvas.glContext)

        // 1. Pass 1: Render-to-texture FBO pass (blue background + solid green inner quad)
        val fboSize = 64
        val fboTex = gl.createTexture()
        gl.bindTexture(GLTextureTarget.TEXTURE_2D, fboTex)
        gl.texImage2D(GLTextureTarget.TEXTURE_2D, 0, GLTextureFormat.RGBA8, fboSize, fboSize, null)
        gl.texParameteri(
            GLTextureTarget.TEXTURE_2D,
            AlienOffscreenGlContext.GL_TEXTURE_MIN_FILTER,
            AlienOffscreenGlContext.GL_NEAREST
        )
        gl.texParameteri(
            GLTextureTarget.TEXTURE_2D,
            AlienOffscreenGlContext.GL_TEXTURE_MAG_FILTER,
            AlienOffscreenGlContext.GL_NEAREST
        )

        val fbo = gl.createFramebuffer()
        gl.bindFramebuffer(GLFramebufferTarget.FRAMEBUFFER, fbo)
        gl.framebufferTexture2D(
            GLFramebufferTarget.FRAMEBUFFER,
            GLFramebufferAttachment.COLOR_ATTACHMENT0,
            GLTextureTarget.TEXTURE_2D,
            fboTex,
            0
        )
        assertEquals(GLFramebufferStatus.COMPLETE, gl.checkFramebufferStatus(GLFramebufferTarget.FRAMEBUFFER))

        gl.viewport(0, 0, fboSize, fboSize)
        gl.clearColor(0.0f, 0.25f, 0.75f, 1.0f)
        gl.clear(setOf(GLClearBuffer.COLOR, GLClearBuffer.DEPTH))

        val solidVs = gl.createShader(
            ShaderType.VERTEX,
            """
            #version 300 es
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec4 aColor;
            uniform mat4 uTransform;
            out vec4 vColor;
            void main() {
                gl_Position = uTransform * vec4(aPosition, 1.0);
                vColor = aColor;
            }
            """.trimIndent()
        )
        val solidFs = gl.createShader(
            ShaderType.FRAGMENT,
            """
            #version 300 es
            precision mediump float;
            in vec4 vColor;
            out vec4 fragColor;
            void main() {
                fragColor = vColor;
            }
            """.trimIndent()
        )
        assertTrue(solidVs.compileStatus.success, solidVs.compileStatus.infoLog)
        assertTrue(solidFs.compileStatus.success, solidFs.compileStatus.infoLog)
        val solidProg = gl.createProgram(solidVs, solidFs)
        assertTrue(solidProg.linkStatus.success, solidProg.linkStatus.infoLog)

        val identityMat4 = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )

        val fboQuadVertices = floatArrayOf(
            // x, y, z, r, g, b, a
            -0.5f, -0.5f, 0.0f, 0f, 1f, 0f, 1f,
             0.5f, -0.5f, 0.0f, 0f, 1f, 0f, 1f,
             0.5f,  0.5f, 0.0f, 0f, 1f, 0f, 1f,
            -0.5f, -0.5f, 0.0f, 0f, 1f, 0f, 1f,
             0.5f,  0.5f, 0.0f, 0f, 1f, 0f, 1f,
            -0.5f,  0.5f, 0.0f, 0f, 1f, 0f, 1f
        )
        val fboVbo = gl.createBuffer()
        gl.bindBuffer(GLBufferTarget.ARRAY_BUFFER, fboVbo)
        gl.bufferData(GLBufferTarget.ARRAY_BUFFER, fboQuadVertices, GLBufferUsage.STATIC_DRAW)

        gl.useProgram(solidProg)
        gl.uniformMatrix4fv(assertNotNull(gl.getUniformLocation(solidProg, "uTransform")), false, identityMat4)
        gl.enableVertexAttribArray(0)
        gl.enableVertexAttribArray(1)
        gl.vertexAttribPointer(GLVertexAttribLayout(0, 3, GLDataType.FLOAT, false, 28, 0))
        gl.vertexAttribPointer(GLVertexAttribLayout(1, 4, GLDataType.FLOAT, false, 28, 12))
        gl.drawArrays(GLPrimitive.TRIANGLES, 0, 6)

        val fboCenter = gl.readPixels(32, 32, 1, 1, GLReadFormat.RGBA8)
        assertTrue((fboCenter[1].toInt() and 0xFF) >= 245, "Expected green FBO center, got g=${fboCenter[1].toInt() and 0xFF}")
        val fboCorner = gl.readPixels(2, 2, 1, 1, GLReadFormat.RGBA8)
        assertTrue((fboCorner[2].toInt() and 0xFF) >= 180, "Expected blue FBO corner, got b=${fboCorner[2].toInt() and 0xFF}")

        // 2. Pass 2: Bind default framebuffer, sample FBO texture onto back quad, then draw depth-tested 3D triangles
        gl.bindFramebuffer(GLFramebufferTarget.FRAMEBUFFER, null)
        gl.viewport(0, 0, width, height)
        gl.clearColor(0.8f, 0.4f, 0.0f, 1.0f)
        gl.clearDepth(1.0f)
        gl.clear(setOf(GLClearBuffer.COLOR, GLClearBuffer.DEPTH))
        gl.enable(GLCapability.DEPTH_TEST)
        gl.depthFunc(GLCompareFunction.LESS)

        val texVs = gl.createShader(
            ShaderType.VERTEX,
            """
            #version 300 es
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec2 aUv;
            out vec2 vUv;
            void main() {
                gl_Position = vec4(aPosition, 1.0);
                vUv = aUv;
            }
            """.trimIndent()
        )
        val texFs = gl.createShader(
            ShaderType.FRAGMENT,
            """
            #version 300 es
            precision mediump float;
            uniform sampler2D uTex;
            in vec2 vUv;
            out vec4 fragColor;
            void main() {
                fragColor = texture(uTex, vUv);
            }
            """.trimIndent()
        )
        val texProg = gl.createProgram(texVs, texFs)
        assertTrue(texProg.linkStatus.success, texProg.linkStatus.infoLog)

        val texturedQuad = floatArrayOf(
            // Covers [-0.9..+0.9] at z = +0.6 (far back layer)
            -0.9f, -0.9f, 0.6f, 0f, 0f,
             0.9f, -0.9f, 0.6f, 1f, 0f,
             0.9f,  0.9f, 0.6f, 1f, 1f,
            -0.9f, -0.9f, 0.6f, 0f, 0f,
             0.9f,  0.9f, 0.6f, 1f, 1f,
            -0.9f,  0.9f, 0.6f, 0f, 1f
        )
        val quadVbo = gl.createBuffer()
        gl.bindBuffer(GLBufferTarget.ARRAY_BUFFER, quadVbo)
        gl.bufferData(GLBufferTarget.ARRAY_BUFFER, texturedQuad, GLBufferUsage.STATIC_DRAW)

        gl.useProgram(texProg)
        gl.activeTexture(0)
        gl.bindTexture(GLTextureTarget.TEXTURE_2D, fboTex)
        gl.uniform1i(assertNotNull(gl.getUniformLocation(texProg, "uTex")), 0)
        gl.enableVertexAttribArray(0)
        gl.enableVertexAttribArray(1)
        gl.vertexAttribPointer(GLVertexAttribLayout(0, 3, GLDataType.FLOAT, false, 20, 0))
        gl.vertexAttribPointer(GLVertexAttribLayout(1, 2, GLDataType.FLOAT, false, 20, 12))
        gl.drawArrays(GLPrimitive.TRIANGLES, 0, 6)

        // Draw closer RED triangle (z = -0.5) first, then farther BLUE triangle (z = +0.2) second ->
        // depth test MUST preserve RED in overlap and draw BLUE below it over the FBO quad!
        val depthTriangles = floatArrayOf(
            // Triangle A: near RED at z = -0.5
            -0.4f, -0.4f, -0.5f, 1f, 0f, 0f, 1f,
             0.4f, -0.4f, -0.5f, 1f, 0f, 0f, 1f,
             0.0f,  0.5f, -0.5f, 1f, 0f, 0f, 1f,
            // Triangle B: far BLUE at z = +0.2
            -0.5f,  0.0f,  0.2f, 0f, 0f, 1f, 1f,
             0.5f,  0.0f,  0.2f, 0f, 0f, 1f, 1f,
             0.0f, -0.6f,  0.2f, 0f, 0f, 1f, 1f
        )
        val triVbo = gl.createBuffer()
        gl.bindBuffer(GLBufferTarget.ARRAY_BUFFER, triVbo)
        gl.bufferData(GLBufferTarget.ARRAY_BUFFER, depthTriangles, GLBufferUsage.STATIC_DRAW)

        gl.useProgram(solidProg)
        gl.uniformMatrix4fv(assertNotNull(gl.getUniformLocation(solidProg, "uTransform")), false, identityMat4)
        gl.enableVertexAttribArray(0)
        gl.enableVertexAttribArray(1)
        gl.vertexAttribPointer(GLVertexAttribLayout(0, 3, GLDataType.FLOAT, false, 28, 0))
        gl.vertexAttribPointer(GLVertexAttribLayout(1, 4, GLDataType.FLOAT, false, 28, 12))
        gl.drawArrays(GLPrimitive.TRIANGLES, 0, 6)

        val glRgba = gl.readPixels(0, 0, width, height, GLReadFormat.RGBA8)
        impl.swapBuffers()
        val offscreenBuf = session.renderFrameToBuffer(0f)

        fun assertPixel(
            x: Int,
            y: Int,
            desc: String,
            check: (r: Int, g: Int, b: Int, a: Int) -> Boolean
        ) {
            val idx = (y * width + x) * 4
            val gr = glRgba[idx].toInt() and 0xFF
            val gg = glRgba[idx + 1].toInt() and 0xFF
            val gb = glRgba[idx + 2].toInt() and 0xFF
            val ga = glRgba[idx + 3].toInt() and 0xFF
            assertTrue(check(gr, gg, gb, ga), "glReadPixels mismatch at ($x,$y) [$desc]: rgba=($gr,$gg,$gb,$ga)")

            val composedPixel = offscreenBuf.getPixel(x, y)
            assertTrue(
                check(composedPixel.r, composedPixel.g, composedPixel.b, composedPixel.a),
                "Alien offscreenBuffer mismatch at ($x,$y) [$desc]: rgba=(${composedPixel.r},${composedPixel.g},${composedPixel.b},${composedPixel.a})"
            )
        }

        // Border (2, 2): untouched clearColor orange (204, 102, 0, 255)
        assertPixel(2, 2, "clearColor border") { r, g, b, a -> r in 198..210 && g in 96..108 && b <= 5 && a == 255 }
        // FBO quad corner (14, 14): Pass 1 FBO clear color blue (0, 64, 191, 255)
        assertPixel(14, 14, "FBO outer clear blue") { r, g, b, a -> r <= 10 && g in 55..75 && b in 180..205 && a == 255 }
        // FBO quad green region (40, 54): Pass 1 FBO drawn green quad (0, 255, 0, 255)
        assertPixel(40, 54, "FBO rendered green quad") { r, g, b, a -> r <= 10 && g >= 245 && b <= 10 && a == 255 }
        // Depth test overlap (64, 70): Near RED triangle (z=-0.5) must beat later-drawn BLUE (z=+0.2)
        assertPixel(64, 70, "depth-tested near RED triangle") { r, g, b, a -> r >= 245 && g <= 10 && b <= 10 && a == 255 }
        // Non-overlapping lower part of BLUE triangle (64, 96): BLUE (z=+0.2) must beat FBO quad (z=+0.6)
        assertPixel(64, 96, "far BLUE triangle over FBO quad") { r, g, b, a -> r <= 10 && g <= 10 && b >= 245 && a == 255 }
    }

    @Test
    fun testHardwareDelegateFboReadbackAndWebGlPipelineFeatures() {
        Canvas3DAlienExtension.register()
        Canvas3DRegistry.overrideSupportForTesting(
            GpuBackendType.OPENGL_ES,
            GpuBackendSupport(GpuBackendType.OPENGL_ES, hardwareAvailable = true, softwareAvailable = true)
        )

        val hwCanvas = Canvas3D(
            backend = GpuBackendType.OPENGL_ES,
            acceleration = GpuAccelerationPolicy.HARDWARE_ONLY,
            width = 32,
            height = 32
        )
        val mockHwGl = MockHardwareGlContext(32, 32)
        val hwImpl = Canvas3DAlienImpl(
            component = hwCanvas,
            activeAcceleration = GpuAcceleration.HARDWARE,
            session = null,
            hardwareGlDelegate = mockHwGl
        )

        val alienGl = assertIs<AlienOffscreenGlContext>(hwImpl.glContext)
        alienGl.bindFramebuffer(GLFramebufferTarget.FRAMEBUFFER, null)
        alienGl.clearColor(0.2f, 0.8f, 0.4f, 1.0f)
        alienGl.clear(setOf(GLClearBuffer.COLOR))
        val hwBytes = hwImpl.captureFrameToRgba()

        assertTrue(mockHwGl.createdFbos >= 1)
        assertTrue(mockHwGl.attachedFboColor)
        assertNotNull(mockHwGl.lastReadFboId)
        assertEquals(204, hwBytes[1].toInt() and 0xFF)

        // Verify WebGL extras (VAO, bufferSubData, drawElements, CULL_FACE, BLEND, SCISSOR_TEST, measure & JPEG)
        val session = AlienRenderSession(hostFactory = hostFactory, initialWidth = 100, initialHeight = 80, offscreenOnly = true)
        val swCanvas = object : Canvas3D(
            backend = GpuBackendType.OPENGL_ES,
            acceleration = GpuAccelerationPolicy.SOFTWARE_ONLY,
            width = 64,
            height = 64
        ) {
            override fun resolveActiveFactory(): NativeFactory = session.alienFactory
        }
        swCanvas.ensureNativeImpl()
        val swImpl = assertIs<Canvas3DAlienImpl>(swCanvas.nativeImpl)

        // Default HTML canvas 300x150 fallback when widthPx / heightPx cleared to null
        swCanvas.widthPx = null
        swCanvas.heightPx = null
        assertEquals(Size(300, 150), swImpl.node.customMeasure!!(1000, 1000, swImpl.node.resolveVisualStyle()))
        swImpl.setPreferredSize(Size(64, 64))
        assertEquals(Size(64, 64), Size(swImpl.getSurfaceWidth(), swImpl.getSurfaceHeight()))

        swImpl.setPointerEventsPassThrough(true)
        assertFalse(swImpl.node.resolveVisualStyle().pointerEventsEnabled)
        swImpl.setPointerEventsPassThrough(false)
        assertTrue(swImpl.node.resolveVisualStyle().pointerEventsEnabled)

        val gl = assertIs<AlienOffscreenGlContext>(swCanvas.glContext)
        gl.viewport(0, 0, 64, 64)
        gl.clearColor(0f, 0f, 0f, 1f)
        gl.clear(setOf(GLClearBuffer.COLOR, GLClearBuffer.DEPTH))

        val vs = gl.createShader(ShaderType.VERTEX).also {
            gl.shaderSource(
                it,
                """
                #version 100
                attribute vec2 aPos;
                void main() { gl_Position = vec4(aPos, 0.0, 1.0); }
                """.trimIndent()
            )
            assertTrue(gl.compileShader(it).compileStatus.success)
        }
        val fs = gl.createShader(ShaderType.FRAGMENT).also {
            gl.shaderSource(
                it,
                """
                #version 100
                precision mediump float;
                uniform vec4 uColor;
                void main() { gl_FragColor = uColor; }
                """.trimIndent()
            )
            assertTrue(gl.compileShader(it).compileStatus.success)
        }
        val prog = gl.createProgram().also {
            gl.attachShader(it, vs)
            gl.attachShader(it, fs)
            assertTrue(gl.linkProgram(it).linkStatus.success)
        }
        gl.useProgram(prog)

        val vao = gl.createVertexArray()
        gl.bindVertexArray(vao)

        val vbo = gl.createBuffer()
        gl.bindBuffer(GLBufferTarget.ARRAY_BUFFER, vbo)
        // Start with zeroed buffer, then patch coordinates via bufferSubData
        gl.bufferData(GLBufferTarget.ARRAY_BUFFER, FloatArray(8), GLBufferUsage.DYNAMIC_DRAW)
        gl.bufferSubData(
            GLBufferTarget.ARRAY_BUFFER,
            0,
            floatArrayOf(
                -1f, -1f,
                 1f, -1f,
                 1f,  1f,
                -1f,  1f
            )
        )
        gl.enableVertexAttribArray(0)
        gl.vertexAttribPointer(GLVertexAttribLayout(0, 2, GLDataType.FLOAT, false, 8, 0))

        val ebo = gl.createBuffer()
        gl.bindBuffer(GLBufferTarget.ELEMENT_ARRAY_BUFFER, ebo)
        // CCW triangle (0, 1, 2) and CW triangle (0, 2, 1)
        gl.bufferData(GLBufferTarget.ELEMENT_ARRAY_BUFFER, shortArrayOf(0, 1, 2, 0, 2, 1), GLBufferUsage.STATIC_DRAW)

        // Enable CULL_FACE (BACK, CCW): CW triangle (indices offset=6 bytes) must be culled cleanly!
        gl.enable(GLCapability.CULL_FACE)
        gl.cullFace(GLFaceSide.BACK)
        gl.frontFace(GLWinding.CCW)
        gl.uniform4f(assertNotNull(gl.getUniformLocation(prog, "uColor")), 1f, 0f, 0f, 1f)
        gl.drawElements(GLPrimitive.TRIANGLES, 3, GLIndexType.UNSIGNED_SHORT, 6)
        val afterCulled = gl.readPixels(32, 32, 1, 1)
        assertEquals(0, afterCulled[0].toInt() and 0xFF, "Back-facing CW triangle should have been culled")

        // Draw CCW triangle (offset = 0) with BLEND 50% green over dark background under SCISSOR_TEST
        gl.enable(GLCapability.BLEND)
        gl.blendFuncSeparate(
            GLBlendFactor.SRC_ALPHA,
            GLBlendFactor.ONE_MINUS_SRC_ALPHA,
            GLBlendFactor.ONE,
            GLBlendFactor.ZERO
        )
        gl.enable(GLCapability.SCISSOR_TEST)
        gl.scissor(0, 0, 40, 64)
        gl.uniform4f(assertNotNull(gl.getUniformLocation(prog, "uColor")), 0f, 1f, 0f, 0.5f)
        gl.drawElements(GLPrimitive.TRIANGLES, 3, GLIndexType.UNSIGNED_SHORT, 0)

        val insideScissor = gl.readPixels(32, 32, 1, 1)
        assertTrue((insideScissor[1].toInt() and 0xFF) in 120..135, "Expected 50% blended green inside scissor")
        val outsideScissor = gl.readPixels(60, 10, 1, 1)
        assertEquals(0, outsideScissor[1].toInt() and 0xFF, "Pixel outside scissor must stay untouched")

        val jpeg = swImpl.captureFrameToJpeg(85)
        assertTrue(jpeg.size > 4 && (jpeg[0].toInt() and 0xFF) == 0xFF && (jpeg[1].toInt() and 0xFF) == 0xD8)
    }
}
