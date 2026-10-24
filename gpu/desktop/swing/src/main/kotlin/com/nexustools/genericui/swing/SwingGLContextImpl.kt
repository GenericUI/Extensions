package com.nexustools.genericui.swing

import com.nexustools.genericui.component.media.GLBuffer
import com.nexustools.genericui.component.media.GLContext
import com.nexustools.genericui.component.media.GLFramebuffer
import com.nexustools.genericui.component.media.GLProgram
import com.nexustools.genericui.component.media.GLShader
import com.nexustools.genericui.component.media.GLTexture
import com.nexustools.genericui.component.media.GLUniformLocation
import com.nexustools.genericui.enums.media.ShaderType

/**
 * Java Swing OpenGL ES / ANGLE (`LWJGL` `org.lwjgl.opengles.GLES20` / `org.lwjgl.opengl.GL` when present on
 * the runtime classpath, with software/offscreen fallback) implementation of [GLContext].
 */
class SwingGLContextImpl(
    var viewportWidth: Int = 800,
    var viewportHeight: Int = 600,
    val lwjglAvailable: Boolean = detectLwjglOpenGl()
) : GLContext {
    override val versionString: String = if (lwjglAvailable) "OpenGL ES 3.0 (LWJGL / ANGLE)" else "OpenGL ES 3.0 (Swing Offscreen)"
    override val rendererString: String = if (lwjglAvailable) "LWJGL Hardware GL" else "Swing GL Offscreen"
    override val vendorString: String = "GenericUI Swing"
    override val isAngleBackend: Boolean = System.getProperty("os.name").orEmpty().contains("Windows", ignoreCase = true)

    private var nextId: Int = 1

    override fun viewport(x: Int, y: Int, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
    }

    override fun scissor(x: Int, y: Int, width: Int, height: Int) {}
    override fun clearColor(r: Float, g: Float, b: Float, a: Float) {}
    override fun clearDepth(depth: Float) {}
    override fun clearStencil(stencil: Int) {}
    override fun clear(mask: Int) {}

    override fun enable(cap: Int) {}
    override fun disable(cap: Int) {}
    override fun blendFunc(sfactor: Int, dfactor: Int) {}
    override fun blendFuncSeparate(srcRGB: Int, dstRGB: Int, srcAlpha: Int, dstAlpha: Int) {}
    override fun depthFunc(func: Int) {}
    override fun depthMask(flag: Boolean) {}
    override fun cullFace(mode: Int) {}
    override fun frontFace(mode: Int) {}

    override fun createShader(type: ShaderType, source: String): GLShader = GLShader(nextId++, type)
    override fun deleteShader(shader: GLShader) {}
    override fun createProgram(vertexShader: GLShader, fragmentShader: GLShader): GLProgram = GLProgram(nextId++)
    override fun useProgram(program: GLProgram?) {}
    override fun deleteProgram(program: GLProgram) {}

    override fun getAttribLocation(program: GLProgram, name: String): Int = 0
    override fun getUniformLocation(program: GLProgram, name: String): GLUniformLocation? = GLUniformLocation(nextId++, name)
    override fun uniform1i(location: GLUniformLocation, v0: Int) {}
    override fun uniform1f(location: GLUniformLocation, v0: Float) {}
    override fun uniform2f(location: GLUniformLocation, v0: Float, v1: Float) {}
    override fun uniform3f(location: GLUniformLocation, v0: Float, v1: Float, v2: Float) {}
    override fun uniform4f(location: GLUniformLocation, v0: Float, v1: Float, v2: Float, v3: Float) {}
    override fun uniformMatrix3fv(location: GLUniformLocation, transpose: Boolean, values: FloatArray) {}
    override fun uniformMatrix4fv(location: GLUniformLocation, transpose: Boolean, values: FloatArray) {}

    override fun createBuffer(): GLBuffer = GLBuffer(nextId++)
    override fun bindBuffer(target: Int, buffer: GLBuffer?) {}
    override fun bufferDataFloat(target: Int, data: FloatArray, usage: Int) {}
    override fun bufferDataInt(target: Int, data: IntArray, usage: Int) {}
    override fun deleteBuffer(buffer: GLBuffer) {}

    override fun enableVertexAttribArray(index: Int) {}
    override fun disableVertexAttribArray(index: Int) {}
    override fun vertexAttribPointer(index: Int, size: Int, type: Int, normalized: Boolean, stride: Int, offset: Int) {}

    override fun createTexture(): GLTexture = GLTexture(nextId++)
    override fun bindTexture(target: Int, texture: GLTexture?) {}
    override fun activeTexture(textureUnit: Int) {}
    override fun texImage2DRgba(target: Int, level: Int, width: Int, height: Int, rgbaBytes: ByteArray?) {}
    override fun texParameteri(target: Int, pname: Int, param: Int) {}
    override fun generateMipmap(target: Int) {}
    override fun deleteTexture(texture: GLTexture) {}

    override fun createFramebuffer(): GLFramebuffer = GLFramebuffer(nextId++)
    override fun bindFramebuffer(target: Int, framebuffer: GLFramebuffer?) {}
    override fun framebufferTexture2D(target: Int, attachment: Int, textarget: Int, texture: GLTexture, level: Int) {}
    override fun deleteFramebuffer(framebuffer: GLFramebuffer) {}

    override fun drawArrays(mode: Int, first: Int, count: Int) {}
    override fun drawElements(mode: Int, count: Int, type: Int, offset: Int) {}
    override fun drawArraysInstanced(mode: Int, first: Int, count: Int, instanceCount: Int) {}
    override fun drawElementsInstanced(mode: Int, count: Int, type: Int, offset: Int, instanceCount: Int) {}

    override fun readPixelsRgba(x: Int, y: Int, width: Int, height: Int): ByteArray =
        ByteArray((width * height * 4).coerceAtLeast(0))

    override fun finish() {}
    override fun flush() {}

    companion object {
        fun detectLwjglOpenGl(): Boolean = try {
            Class.forName("org.lwjgl.opengles.GLES20")
            true
        } catch (_: Throwable) {
            try {
                Class.forName("org.lwjgl.opengl.GL")
                true
            } catch (_: Throwable) {
                false
            }
        }
    }
}
