package com.nexustools.genericui.web

import com.nexustools.genericui.component.media.GLIndexType
import com.nexustools.genericui.component.media.VKBackendKind
import com.nexustools.genericui.component.media.VKContextInfo
import com.nexustools.genericui.component.media.VKDeviceAttributes
import com.nexustools.genericui.component.media.VKDeviceType
import com.nexustools.genericui.component.media.VKPipeline
import com.nexustools.genericui.component.media.VKRenderPassDescriptor
import com.nexustools.genericui.desktop.canvas.DesktopVKContextImpl
import com.nexustools.genericui.ext.gpu.GpuAcceleration

/**
 * Web (`WebGPU` `GPUCanvasContext` / `GPUDevice`) implementation of [com.nexustools.genericui.component.media.VKContext].
 */
class WebVKContextImpl(
    val canvasElement: WebDomElement = WebDomElement("canvas"),
    config: VKDeviceAttributes = VKDeviceAttributes(),
    initialWidth: Int = 800,
    initialHeight: Int = 600,
    acceleration: GpuAcceleration = GpuAcceleration.HARDWARE
) : DesktopVKContextImpl(
    config = config,
    initialWidth = initialWidth,
    initialHeight = initialHeight,
    contextInfo = VKContextInfo(
        backend = VKBackendKind.WEBGPU,
        apiVersion = config.apiVersion,
        deviceName = "WebGPU Adapter",
        deviceType = VKDeviceType.DISCRETE_GPU,
        acceleration = acceleration
    )
) {

    init {
        canvasElement.setAttribute("data-webgpu", "true")
        canvasElement.setAttribute("data-vk-validation", config.enableValidation.toString())
        canvasElement.setAttribute("data-vk-present-mode", config.presentMode.name)
    }

    override fun beginRenderPass(descriptor: VKRenderPassDescriptor) {
        super.beginRenderPass(descriptor)
        val c = descriptor.clearColor
        canvasElement.setAttribute("data-vk-render-pass", "active")
        canvasElement.setAttribute("data-vk-clear-color", "${c.r},${c.g},${c.b},${c.a}")
    }

    override fun endRenderPass() {
        super.endRenderPass()
        canvasElement.setAttribute("data-vk-render-pass", "ended")
        canvasElement.setAttribute("data-vk-image-index", currentImageIndex.toString())
    }

    override fun bindPipeline(pipeline: VKPipeline) {
        super.bindPipeline(pipeline)
        canvasElement.setAttribute("data-vk-pipeline", pipeline.id.toString())
    }

    override fun draw(vertexCount: Int, instanceCount: Int, firstVertex: Int, firstInstance: Int) {
        super.draw(vertexCount, instanceCount, firstVertex, firstInstance)
        canvasElement.setAttribute("data-vk-last-draw", "draw:$vertexCount:$instanceCount:$firstVertex:$firstInstance")
    }

    override fun drawIndexed(
        indexCount: Int,
        instanceCount: Int,
        firstIndex: Int,
        vertexOffset: Int,
        firstInstance: Int
    ) {
        super.drawIndexed(indexCount, instanceCount, firstIndex, vertexOffset, firstInstance)
        canvasElement.setAttribute(
            "data-vk-last-draw",
            "drawIndexed:$indexCount:$instanceCount:$firstIndex:$vertexOffset:$firstInstance"
        )
    }
}
