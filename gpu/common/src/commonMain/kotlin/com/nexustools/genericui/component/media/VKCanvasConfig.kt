package com.nexustools.genericui.component.media

/**
 * Configuration options for initializing a [VKCanvas] (Vulkan 1.1 / 1.2 / 1.3).
 */
data class VKCanvasConfig(
    val apiMajorVersion: Int = 1,
    val apiMinorVersion: Int = 2,
    val enableValidationLayers: Boolean = false,
    val preferredSwapchainImages: Int = 3,
    val vsyncFifoPresentMode: Boolean = true,
    val preferDiscreteGpu: Boolean = true,
    val enableHdrSwapchainIfAvailable: Boolean = true
)
