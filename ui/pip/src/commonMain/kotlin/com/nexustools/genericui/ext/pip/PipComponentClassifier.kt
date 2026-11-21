package com.nexustools.genericui.ext.pip

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.ext.overlay.OverlayHost

/**
 * Classifies [AbstractComponent] instances into [PipContentType] without requiring
 * compile-time dependencies on `:extensions:media:media` or `:extensions:graphics:canvas3d`.
 */
internal object PipComponentClassifier {

    internal var platformCompatibleDetector: ((AbstractComponent) -> Boolean)? = null

    fun classify(component: AbstractComponent): PipContentType {
        val chain = collectInspectionChain(component)
        for (node in chain) {
            if (isVideoNode(node)) return PipContentType.VIDEO
        }
        for (node in chain) {
            if (isCanvasNode(node)) return PipContentType.CANVAS
        }
        return PipContentType.ANY_COMPONENT
    }

    fun hasExplicitPipCompatibleMarker(component: AbstractComponent): Boolean {
        val chain = collectInspectionChain(component)
        for (node in chain) {
            val attrPip = node.getAttribute("data-pip-compatible") ?: node.getAttribute("pipCompatible")
            if (attrPip.equals("true", ignoreCase = true)) {
                return true
            }
            if (platformCompatibleDetector?.invoke(node) == true) {
                return true
            }
        }
        return false
    }

    fun isSupportedForCapabilities(
        component: AbstractComponent,
        capabilities: PipCapabilities
    ): Boolean {
        if (!capabilities.isSupported) return false
        if (PipContentType.ANY_COMPONENT in capabilities.supportedContentTypes) return true
        if (hasExplicitPipCompatibleMarker(component)) return true
        return classify(component) in capabilities.supportedContentTypes
    }

    private fun collectInspectionChain(root: AbstractComponent): List<AbstractComponent> {
        val result = mutableListOf<AbstractComponent>()
        var current: AbstractComponent? = root
        while (current != null && result.none { it === current }) {
            result.add(current)
            current = (current as? OverlayHost)?.targetComponent
        }
        return result
    }

    private fun isVideoNode(node: AbstractComponent): Boolean {
        val simpleName = node::class.simpleName?.lowercase().orEmpty()
        val tag = node.htmlTag.lowercase()
        val prefix = node.componentClassPrefix.lowercase()
        val realizedPrefix = if (node.parent != null && node.isVisible && node.resolveActiveFactory() != null) {
            node.ensureNativeImpl()?.componentClassPrefix?.lowercase().orEmpty()
        } else {
            ""
        }
        val keepScreenOn = node.getAttribute("data-keep-screen-on").equals("true", ignoreCase = true)
        return simpleName.contains("video") ||
            tag == "video" ||
            tag.contains(":video") ||
            prefix.contains("video") ||
            prefix.contains("vlc") ||
            realizedPrefix.contains("video") ||
            realizedPrefix.contains("vlc") ||
            keepScreenOn
    }

    private fun isCanvasNode(node: AbstractComponent): Boolean {
        val simpleName = node::class.simpleName?.lowercase().orEmpty()
        val tag = node.htmlTag.lowercase()
        val prefix = node.componentClassPrefix.lowercase()
        val realizedPrefix = if (node.parent != null && node.isVisible && node.resolveActiveFactory() != null) {
            node.ensureNativeImpl()?.componentClassPrefix?.lowercase().orEmpty()
        } else {
            ""
        }
        return simpleName.contains("canvas") ||
            tag == "canvas" ||
            tag.contains(":canvas") ||
            prefix.contains("canvas") ||
            realizedPrefix.contains("canvas")
    }
}
