package com.nexustools.genericui.macos

import com.nexustools.genericui.desktop.canvas.DesktopVKCanvasNativeImpl

/**
 * macOS Vulkan 1.2 / 1.3 canvas implementation powered by **MoltenVK** (`vkCreateMetalSurfaceEXT` /
 * `VK_EXT_metal_surface` over `CAMetalLayer`).
 */
class MacosVKCanvasNativeImpl : DesktopVKCanvasNativeImpl<MacosViewHandle>(
    nativeWidget = MacosViewHandle("GenericUIMoltenVKMetalView"),
    deviceName = "Apple Silicon / Metal GPU (MoltenVK)",
    isMoltenVk = true,
    surfaceExtensionName = "VK_EXT_metal_surface"
)
