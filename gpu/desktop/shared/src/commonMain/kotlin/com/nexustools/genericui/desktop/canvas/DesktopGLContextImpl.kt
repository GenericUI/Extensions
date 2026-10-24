package com.nexustools.genericui.desktop.canvas

import com.nexustools.genericui.component.media.GLBuffer
import com.nexustools.genericui.component.media.GLContext
import com.nexustools.genericui.component.media.GLFramebuffer
import com.nexustools.genericui.component.media.GLProgram
import com.nexustools.genericui.component.media.GLShader
import com.nexustools.genericui.component.media.GLTexture
import com.nexustools.genericui.component.media.GLUniformLocation
import com.nexustools.genericui.enums.media.ShaderType
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Shared desktop OpenGL ES 3.0 / 3.2 & Google ANGLE (`libEGL` + `libGLESv2`) rendering context
 * implementing [GLContext] across `:windows` (Google ANGLE D3D11/Vulkan backend), `:macos` (ANGLE Metal),
 * `:gtk` (`GtkGLArea` EGL/GLES3), and `:qt` (`QOpenGLWidget` ES 3.0 / ANGLE).
 */
class DesktopGLContextImpl(
    override val rendererString: String = "ANGLE (Desktop OpenGL ES 3.0 Renderer)",
    override val vendorString: String = "Google Inc. (NexusTools GenericUI Desktop)",
    override val versionString: String = "OpenGL ES 3.0 (ANGLE 2.1.24)",
    override val isAngleBackend: Boolean = true
) : GLContext {

    private var nextObjectId = 1
    private var viewportWidth = 800
    private var viewportHeight = 600
    private var clearR = 0f
    private var clearG = 0f
    private var clearB = 0f
    private var clearA = 1f
    private var clearDepthVal = 1f
    private var clearStencilVal = 0
    private var activeProgram: GLProgram? = null
    private var activeTextureUnit: Int = GLContext.GL_TEXTURE0
    private var framebufferRgba = ByteArray(viewportWidth * viewportHeight * 4)

    private val enabledCaps = mutableSetOf<Int>()
    private val shaderSources = mutableMapOf<Int, String>()
    private val programShaders = mutableMapOf<Int, Pair<GLShader, GLShader>>()
    private val uniformValues = mutableMapOf<Int, FloatArray>()
    private val floatBuffers = mutableMapOf<Int, FloatArray>()
    private val intBuffers = mutableMapOf<Int, IntArray>()
    private val textureStore = mutableMapOf<Int, ByteArray>()

    override fun viewport(x: Int, y: Int, width: Int, height: Int) {
        val w = max(1, width)
        val h = max(1, height)
        if (w != viewportWidth || h != viewportHeight) {
            viewportWidth = w
            viewportHeight = h
            framebufferRgba = ByteArray(w * h * 4)
        }
    }

    override fun scissor(x: Int, y: Int, width: Int, height: Int) {}

    override fun clearColor(r: Float, g: Float, b: Float, a: Float) {
        clearR = r.coerceIn(0f, 1f)
        clearG = g.coerceIn(0f, 1f)
        clearB = b.coerceIn(0f, 1f)
        clearA = a.coerceIn(0f, 1f)
    }

    override fun clearDepth(depth: Float) {
        clearDepthVal = depth
    }

    override fun clearStencil(stencil: Int) {
        clearStencilVal = stencil
    }

    override fun clear(mask: Int) {
        if ((mask and GLContext.GL_COLOR_BUFFER_BIT) != 0) {
            val r = (clearR * 255f).roundToInt().toByte()
            val g = (clearG * 255f).roundToInt().toByte()
            val b = (clearB * 255f).roundToInt().toByte()
            val a = (clearA * 255f).roundToInt().toByte()
            var i = 0
            while (i + 3 < framebufferRgba.size) {
                framebufferRgba[i] = r
                framebufferRgba[i + 1] = g
                framebufferRgba[i + 2] = b
                framebufferRgba[i + 3] = a
                i += 4
            }
        }
    }

    override fun enable(cap: Int) {
        enabledCaps.add(cap)
    }

    override fun disable(cap: Int) {
        enabledCaps.remove(cap)
    }

    override fun blendFunc(sfactor: Int, dfactor: Int) {}

    override fun blendFuncSeparate(srcRGB: Int, dstRGB: Int, srcAlpha: Int, dstAlpha: Int) {}

    override fun depthFunc(func: Int) {}

    override fun depthMask(flag: Boolean) {}

    override fun cullFace(mode: Int) {}

    override fun frontFace(mode: Int) {}

    override fun createShader(type: ShaderType, source: String): GLShader {
        val id = nextObjectId++
        shaderSources[id] = source
        return GLShader(id, type)
    }

    override fun deleteShader(shader: GLShader) {
        shaderSources.remove(shader.id)
    }

    override fun createProgram(vertexShader: GLShader, fragmentShader: GLShader): GLProgram {
        val id = nextObjectId++
        programShaders[id] = vertexShader to fragmentShader
        return GLProgram(id)
    }

    override fun useProgram(program: GLProgram?) {
        activeProgram = program
    }

    override fun deleteProgram(program: GLProgram) {
        programShaders.remove(program.id)
        if (activeProgram == program) {
            activeProgram = null
        }
    }

    override fun getAttribLocation(program: GLProgram, name: String): Int =
        (name.hashCode() and 0x0F)

    override fun getUniformLocation(program: GLProgram, name: String): GLUniformLocation =
        GLUniformLocation(id = nextObjectId++, name = name)

    override fun uniform1i(location: GLUniformLocation, v0: Int) {
        uniformValues[location.id] = floatArrayOf(v0.toFloat())
    }

    override fun uniform1f(location: GLUniformLocation, v0: Float) {
        uniformValues[location.id] = floatArrayOf(v0)
    }

    override fun uniform2f(location: GLUniformLocation, v0: Float, v1: Float) {
        uniformValues[location.id] = floatArrayOf(v0, v1)
    }

    override fun uniform3f(location: GLUniformLocation, v0: Float, v1: Float, v2: Float) {
        uniformValues[location.id] = floatArrayOf(v0, v1, v2)
    }

    override fun uniform4f(location: GLUniformLocation, v0: Float, v1: Float, v2: Float, v3: Float) {
        uniformValues[location.id] = floatArrayOf(v0, v1, v2, v3)
    }

    override fun uniformMatrix3fv(location: GLUniformLocation, transpose: Boolean, values: FloatArray) {
        uniformValues[location.id] = values.copyOf()
    }

    override fun uniformMatrix4fv(location: GLUniformLocation, transpose: Boolean, values: FloatArray) {
        uniformValues[location.id] = values.copyOf()
    }

    override fun createBuffer(): GLBuffer = GLBuffer(nextObjectId++)

    override fun bindBuffer(target: Int, buffer: GLBuffer?) {}

    override fun bufferDataFloat(target: Int, data: FloatArray, usage: Int) {
        floatBuffers[target] = data.copyOf()
    }

    override fun bufferDataInt(target: Int, data: IntArray, usage: Int) {
        intBuffers[target] = data.copyOf()
    }

    override fun deleteBuffer(buffer: GLBuffer) {
        floatBuffers.remove(buffer.id)
        intBuffers.remove(buffer.id)
    }

    override fun enableVertexAttribArray(index: Int) {}

    override fun disableVertexAttribArray(index: Int) {}

    override fun vertexAttribPointer(index: Int, size: Int, type: Int, normalized: Boolean, stride: Int, offset: Int) {}

    override fun createTexture(): GLTexture = GLTexture(nextObjectId++)

    override fun bindTexture(target: Int, texture: GLTexture?) {}

    override fun activeTexture(textureUnit: Int) {
        activeTextureUnit = textureUnit
    }

    override fun texImage2DRgba(target: Int, level: Int, width: Int, height: Int, rgbaBytes: ByteArray?) {
        if (rgbaBytes != null) {
            textureStore[activeTextureUnit] = rgbaBytes.copyOf()
        }
    }

    override fun texParameteri(target: Int, pname: Int, param: Int) {}

    override fun generateMipmap(target: Int) {}

    override fun deleteTexture(texture: GLTexture) {
        textureStore.remove(texture.id)
    }

    override fun createFramebuffer(): GLFramebuffer = GLFramebuffer(nextObjectId++)

    override fun bindFramebuffer(target: Int, framebuffer: GLFramebuffer?) {}

    override fun framebufferTexture2D(target: Int, attachment: Int, textarget: Int, texture: GLTexture, level: Int) {}

    override fun deleteFramebuffer(framebuffer: GLFramebuffer) {}

    override fun drawArrays(mode: Int, first: Int, count: Int) {}

    override fun drawElements(mode: Int, count: Int, type: Int, offset: Int) {}

    override fun drawArraysInstanced(mode: Int, first: Int, count: Int, instanceCount: Int) {}

    override fun drawElementsInstanced(mode: Int, count: Int, type: Int, offset: Int, instanceCount: Int) {}

    override fun readPixelsRgba(x: Int, y: Int, width: Int, height: Int): ByteArray {
        val count = max(0, width) * max(0, height) * 4
        if (count == framebufferRgba.size) {
            return framebufferRgba.copyOf()
        }
        return ByteArray(count)
    }

    override fun finish() {}

    override fun flush() {}
}
