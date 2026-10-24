package com.nexustools.genericui.gtk

import com.nexustools.genericui.desktop.canvas.DesktopVKCanvasNativeImpl

/**
 * GTK 4 Vulkan 1.3 canvas implementation using `GdkVulkanContext` (`VK_KHR_wayland_surface` /
 * `VK_KHR_xlib_surface` on Linux and `VK_KHR_win32_surface` on Windows).
 */
class GtkVKCanvasNativeImpl : DesktopVKCanvasNativeImpl<GtkWidgetHandle>(
    nativeWidget = GtkWidgetHandle("GtkWidget_GdkVulkanSurface"),
    deviceName = "GTK4 Vulkan 1.3 GPU (VK_KHR_wayland_surface / VK_KHR_xlib_surface)",
    isMoltenVk = false,
    surfaceExtensionName = "VK_KHR_wayland_surface"
)
