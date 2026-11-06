package com.nexustools.genericui.component.media

/**
 * Backend-specific configuration attributes for [Canvas3D].
 */
data class Canvas3DConfig(
    val gl: GLContextAttributes = GLContextAttributes(),
    val vulkan: VKDeviceAttributes = VKDeviceAttributes()
)
