package com.nexustools.genericui.qt

import com.nexustools.genericui.desktop.canvas.DesktopVKCanvasNativeImpl

/**
 * Qt 6 `QVulkanWindow` + `QVulkanWindowRenderer` (`QWidget::createWindowContainer`) implementation
 * of [com.nexustools.genericui.nativeimpl.media.VKCanvasNativeImpl].
 */
class QtVKCanvasNativeImpl : DesktopVKCanvasNativeImpl<QtWidgetHandle>(
    nativeWidget = QtWidgetHandle("QVulkanWindow_Container"),
    deviceName = "Qt6 QVulkanWindow Vulkan 1.3 GPU",
    isMoltenVk = false,
    surfaceExtensionName = "QVulkanInstance_Surface"
)
