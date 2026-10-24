package com.nexustools.genericui.windows

import com.nexustools.genericui.desktop.canvas.DesktopVKCanvasNativeImpl

/**
 * Windows Vulkan 1.3 canvas implementation using `vkCreateWin32SurfaceKHR` (`VK_KHR_win32_surface`)
 * and `VK_PRESENT_MODE_FIFO_KHR` VBlank-synchronized swapchains.
 */
class WindowsVKCanvasNativeImpl : DesktopVKCanvasNativeImpl<Win32HwndHandle>(
    nativeWidget = Win32HwndHandle(
        win32ClassName = "GenericUIVulkanSurfaceW",
        dwStyle = Win32HwndHandle.WS_CHILD or Win32HwndHandle.WS_VISIBLE,
        dwExStyle = Win32HwndHandle.WS_EX_NOREDIRECTIONBITMAP
    ),
    deviceName = "Windows Vulkan 1.3 GPU (VK_KHR_win32_surface)",
    isMoltenVk = false,
    surfaceExtensionName = "VK_KHR_win32_surface"
)
