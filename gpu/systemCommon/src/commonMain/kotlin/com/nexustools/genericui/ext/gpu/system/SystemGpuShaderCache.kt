package com.nexustools.genericui.ext.gpu.system

import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.ext.gpu.GpuShaderProgram

/**
 * Shared system-target (`:extensions:gpu:sharedSystem`) shader program cache for Vulkan (`#version 450`)
 * and OpenGL ES (`#version 300 es`) offscreen and surface GPU contexts across Desktop, Swing, and Mobile.
 *
 * Not included on `:web`, where WebGPU/WebGL2 shader pipelines are managed by the browser DOM context.
 */
object SystemGpuShaderCache {

    private val cachedPrograms = LinkedHashMap<String, GpuShaderProgram>()

    /**
     * Retrieves a cached [GpuShaderProgram] for [shaderKey] on [gpuContext], or compiles and caches
     * [vulkanGlsl450] + [gles300Shader] on first use.
     */
    fun getOrCompile(
        gpuContext: GpuOffscreenContext,
        shaderKey: String,
        vulkanGlsl450: String,
        gles300Shader: String
    ): GpuShaderProgram {
        val compositeKey = "${gpuContext.backendType}:${gpuContext.deviceName}:$shaderKey"
        cachedPrograms[compositeKey]?.let { return it }
        val compiled = gpuContext.compileComputeOrFragmentKernel(
            vulkanGlsl450 = vulkanGlsl450,
            gles300Shader = gles300Shader
        )
        cachedPrograms[compositeKey] = compiled
        return compiled
    }

    /**
     * Clears the compiled shader cache on [gpuContext].
     */
    fun clear(gpuContext: GpuOffscreenContext? = null) {
        if (gpuContext != null) {
            val prefix = "${gpuContext.backendType}:${gpuContext.deviceName}:"
            val toRemove = cachedPrograms.entries.filter { it.key.startsWith(prefix) }
            for ((k, prog) in toRemove) {
                runCatching { gpuContext.destroyShaderProgram(prog) }
                cachedPrograms.remove(k)
            }
        } else {
            cachedPrograms.clear()
        }
    }
}
