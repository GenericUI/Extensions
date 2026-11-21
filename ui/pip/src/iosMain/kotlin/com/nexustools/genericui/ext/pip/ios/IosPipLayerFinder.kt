@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.pip.ios

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.ios.UikitViewHandle
import platform.AVFoundation.AVPlayerLayer
import platform.QuartzCore.CALayer
import platform.UIKit.UIView

/**
 * Discovers an [AVPlayerLayer] inside a GenericUI component's UIKit [UIView] / [CALayer]
 * hierarchy without requiring a compile dependency on `:extensions:media:media`.
 */
object IosPipLayerFinder {

    private const val MAX_TRAVERSAL_DEPTH: Int = 64

    fun findPlayerLayer(component: AbstractComponent?): AVPlayerLayer? {
        if (component == null) return null
        val impl = runCatching { component.ensureNativeImpl() }.getOrNull() ?: return null
        val handle = impl.nativeWidget as? UikitViewHandle ?: return null
        return findPlayerLayer(handle.uiView)
    }

    fun findPlayerLayer(rootView: UIView?): AVPlayerLayer? {
        if (rootView == null) return null
        val visitedViews = mutableListOf<Any>()
        val visitedLayers = mutableListOf<Any>()
        return searchView(rootView, visitedViews, visitedLayers, depth = 0)
    }

    fun findPlayerLayer(rootLayer: CALayer?): AVPlayerLayer? {
        if (rootLayer == null) return null
        val visitedLayers = mutableListOf<Any>()
        return searchLayer(rootLayer, visitedLayers, depth = 0)
    }

    private fun searchView(
        view: UIView,
        visitedViews: MutableList<Any>,
        visitedLayers: MutableList<Any>,
        depth: Int
    ): AVPlayerLayer? {
        if (depth > MAX_TRAVERSAL_DEPTH || containsIdentity(visitedViews, view)) {
            return null
        }
        visitedViews.add(view)

        searchLayer(view.layer, visitedLayers, depth = 0)?.let { return it }

        val subviews = view.subviews
        for (sub in subviews) {
            val subView = sub as? UIView ?: continue
            searchView(subView, visitedViews, visitedLayers, depth + 1)?.let { return it }
        }
        return null
    }

    private fun searchLayer(
        layer: CALayer,
        visitedLayers: MutableList<Any>,
        depth: Int
    ): AVPlayerLayer? {
        if (depth > MAX_TRAVERSAL_DEPTH || containsIdentity(visitedLayers, layer)) {
            return null
        }
        visitedLayers.add(layer)

        if (layer is AVPlayerLayer) {
            return layer
        }
        val sublayers = layer.sublayers ?: return null
        for (sub in sublayers) {
            val subLayer = sub as? CALayer ?: continue
            searchLayer(subLayer, visitedLayers, depth + 1)?.let { return it }
        }
        return null
    }

    private fun containsIdentity(visited: List<Any>, candidate: Any): Boolean {
        for (item in visited) {
            if (item === candidate) return true
        }
        return false
    }
}
