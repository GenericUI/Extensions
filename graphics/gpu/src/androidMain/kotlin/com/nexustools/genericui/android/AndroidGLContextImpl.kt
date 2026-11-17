package com.nexustools.genericui.android

import android.opengl.GLES20
import android.opengl.GLES30
import com.nexustools.genericui.component.media.GLBackendKind
import com.nexustools.genericui.component.media.GLBlendEquation
import com.nexustools.genericui.component.media.GLBlendFactor
import com.nexustools.genericui.component.media.GLCapability
import com.nexustools.genericui.component.media.GLClearBuffer
import com.nexustools.genericui.component.media.GLCompareFunction
import com.nexustools.genericui.component.media.GLContextAttributes
import com.nexustools.genericui.component.media.GLContextInfo
import com.nexustools.genericui.component.media.GLContextVersion
import com.nexustools.genericui.component.media.GLFaceSide
import com.nexustools.genericui.component.media.GLShadingLanguage
import com.nexustools.genericui.component.media.GLWinding
import com.nexustools.genericui.desktop.canvas.DesktopGLContextImpl
import com.nexustools.genericui.ext.gpu.GpuAcceleration

/**
 * Android OpenGL ES 3.0 implementation of [com.nexustools.genericui.component.media.GLContext]
 * forwarding to [GLES30] / [GLES20] and maintaining software-accurate state and pixel readback.
 */
class AndroidGLContextImpl(
    config: GLContextAttributes = GLContextAttributes(),
    initialWidth: Int = 800,
    initialHeight: Int = 600,
    acceleration: GpuAcceleration = GpuAcceleration.HARDWARE
) : DesktopGLContextImpl(
    config = config,
    initialWidth = initialWidth,
    initialHeight = initialHeight,
    contextInfo = GLContextInfo(
        backend = GLBackendKind.GLES_ANDROID,
        version = config.version,
        shadingLanguage = when (config.version) {
            GLContextVersion.ES_3_0 -> GLShadingLanguage.GLSL_ES_300
            GLContextVersion.ES_3_1 -> GLShadingLanguage.GLSL_ES_310
            GLContextVersion.ES_3_2 -> GLShadingLanguage.GLSL_ES_320
        },
        acceleration = acceleration,
        renderer = "Android GLES 3.0",
        vendor = "Android"
    )
) {

    override fun viewport(x: Int, y: Int, width: Int, height: Int) {
        super.viewport(x, y, width, height)
        try {
            GLES30.glViewport(x, y, width, height)
        } catch (_: Throwable) {
        }
    }

    override fun scissor(x: Int, y: Int, width: Int, height: Int) {
        super.scissor(x, y, width, height)
        try {
            GLES30.glScissor(x, y, width, height)
        } catch (_: Throwable) {
        }
    }

    override fun clearColor(r: Float, g: Float, b: Float, a: Float) {
        super.clearColor(r, g, b, a)
        try {
            GLES30.glClearColor(r, g, b, a)
        } catch (_: Throwable) {
        }
    }

    override fun clearDepth(depth: Float) {
        super.clearDepth(depth)
        try {
            GLES30.glClearDepthf(depth)
        } catch (_: Throwable) {
        }
    }

    override fun clearStencil(stencil: Int) {
        super.clearStencil(stencil)
        try {
            GLES30.glClearStencil(stencil)
        } catch (_: Throwable) {
        }
    }

    override fun clear(buffers: Set<GLClearBuffer>) {
        super.clear(buffers)
        try {
            var mask = 0
            if (GLClearBuffer.COLOR in buffers) mask = mask or GLES30.GL_COLOR_BUFFER_BIT
            if (GLClearBuffer.DEPTH in buffers) mask = mask or GLES30.GL_DEPTH_BUFFER_BIT
            if (GLClearBuffer.STENCIL in buffers) mask = mask or GLES30.GL_STENCIL_BUFFER_BIT
            if (mask != 0) GLES30.glClear(mask)
        } catch (_: Throwable) {
        }
    }

    override fun enable(capability: GLCapability) {
        super.enable(capability)
        try {
            GLES30.glEnable(toGlCap(capability))
        } catch (_: Throwable) {
        }
    }

    override fun disable(capability: GLCapability) {
        super.disable(capability)
        try {
            GLES30.glDisable(toGlCap(capability))
        } catch (_: Throwable) {
        }
    }

    override fun blendFunc(sfactor: GLBlendFactor, dfactor: GLBlendFactor) {
        super.blendFunc(sfactor, dfactor)
        try {
            GLES30.glBlendFunc(toGlBlendFactor(sfactor), toGlBlendFactor(dfactor))
        } catch (_: Throwable) {
        }
    }

    override fun blendEquation(mode: GLBlendEquation) {
        super.blendEquation(mode)
        try {
            val glMode = when (mode) {
                GLBlendEquation.FUNC_ADD -> GLES30.GL_FUNC_ADD
                GLBlendEquation.FUNC_SUBTRACT -> GLES30.GL_FUNC_SUBTRACT
                GLBlendEquation.FUNC_REVERSE_SUBTRACT -> GLES30.GL_FUNC_REVERSE_SUBTRACT
                GLBlendEquation.MIN -> GLES30.GL_MIN
                GLBlendEquation.MAX -> GLES30.GL_MAX
            }
            GLES30.glBlendEquation(glMode)
        } catch (_: Throwable) {
        }
    }

    override fun depthFunc(func: GLCompareFunction) {
        super.depthFunc(func)
        try {
            val glFunc = when (func) {
                GLCompareFunction.NEVER -> GLES30.GL_NEVER
                GLCompareFunction.LESS -> GLES30.GL_LESS
                GLCompareFunction.EQUAL -> GLES30.GL_EQUAL
                GLCompareFunction.LEQUAL -> GLES30.GL_LEQUAL
                GLCompareFunction.GREATER -> GLES30.GL_GREATER
                GLCompareFunction.NOTEQUAL -> GLES30.GL_NOTEQUAL
                GLCompareFunction.GEQUAL -> GLES30.GL_GEQUAL
                GLCompareFunction.ALWAYS -> GLES30.GL_ALWAYS
            }
            GLES30.glDepthFunc(glFunc)
        } catch (_: Throwable) {
        }
    }

    override fun depthMask(flag: Boolean) {
        super.depthMask(flag)
        try {
            GLES30.glDepthMask(flag)
        } catch (_: Throwable) {
        }
    }

    override fun cullFace(mode: GLFaceSide) {
        super.cullFace(mode)
        try {
            val glMode = when (mode) {
                GLFaceSide.FRONT -> GLES30.GL_FRONT
                GLFaceSide.BACK -> GLES30.GL_BACK
                GLFaceSide.FRONT_AND_BACK -> GLES30.GL_FRONT_AND_BACK
            }
            GLES30.glCullFace(glMode)
        } catch (_: Throwable) {
        }
    }

    override fun frontFace(mode: GLWinding) {
        super.frontFace(mode)
        try {
            GLES30.glFrontFace(if (mode == GLWinding.CW) GLES30.GL_CW else GLES30.GL_CCW)
        } catch (_: Throwable) {
        }
    }

    private fun toGlCap(cap: GLCapability): Int = when (cap) {
        GLCapability.BLEND -> GLES30.GL_BLEND
        GLCapability.DEPTH_TEST -> GLES30.GL_DEPTH_TEST
        GLCapability.CULL_FACE -> GLES30.GL_CULL_FACE
        GLCapability.SCISSOR_TEST -> GLES30.GL_SCISSOR_TEST
        GLCapability.STENCIL_TEST -> GLES30.GL_STENCIL_TEST
        GLCapability.POLYGON_OFFSET_FILL -> GLES30.GL_POLYGON_OFFSET_FILL
        GLCapability.SAMPLE_ALPHA_TO_COVERAGE -> GLES30.GL_SAMPLE_ALPHA_TO_COVERAGE
    }

    private fun toGlBlendFactor(factor: GLBlendFactor): Int = when (factor) {
        GLBlendFactor.ZERO -> GLES30.GL_ZERO
        GLBlendFactor.ONE -> GLES30.GL_ONE
        GLBlendFactor.SRC_COLOR -> GLES30.GL_SRC_COLOR
        GLBlendFactor.ONE_MINUS_SRC_COLOR -> GLES30.GL_ONE_MINUS_SRC_COLOR
        GLBlendFactor.DST_COLOR -> GLES30.GL_DST_COLOR
        GLBlendFactor.ONE_MINUS_DST_COLOR -> GLES30.GL_ONE_MINUS_DST_COLOR
        GLBlendFactor.SRC_ALPHA -> GLES30.GL_SRC_ALPHA
        GLBlendFactor.ONE_MINUS_SRC_ALPHA -> GLES30.GL_ONE_MINUS_SRC_ALPHA
        GLBlendFactor.DST_ALPHA -> GLES30.GL_DST_ALPHA
        GLBlendFactor.ONE_MINUS_DST_ALPHA -> GLES30.GL_ONE_MINUS_DST_ALPHA
        GLBlendFactor.CONSTANT_COLOR -> GLES30.GL_CONSTANT_COLOR
        GLBlendFactor.ONE_MINUS_CONSTANT_COLOR -> GLES30.GL_ONE_MINUS_CONSTANT_COLOR
        GLBlendFactor.CONSTANT_ALPHA -> GLES30.GL_CONSTANT_ALPHA
        GLBlendFactor.ONE_MINUS_CONSTANT_ALPHA -> GLES30.GL_ONE_MINUS_CONSTANT_ALPHA
        GLBlendFactor.SRC_ALPHA_SATURATE -> GLES30.GL_SRC_ALPHA_SATURATE
    }
}
