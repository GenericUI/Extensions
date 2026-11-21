package com.nexustools.genericui.ext.pip.android

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import com.nexustools.genericui.android.AndroidFlexContainerView
import com.nexustools.genericui.android.GenericUiFragmentActivity
import com.nexustools.genericui.component.layout.LayerAlignment

/**
 * Isolates a PiP target [View] **in place** without ever detaching (`removeView`) it or any of its
 * ancestors, preserving hardware `SurfaceView` / `GLSurfaceView` / `TextureView` surfaces during PiP.
 */
class AndroidPipTargetIsolator {

    private data class SavedVisibility(
        val view: View,
        val visibility: Int
    )

    private data class SavedGroupGeometry(
        val viewGroup: ViewGroup,
        val paddingLeft: Int,
        val paddingTop: Int,
        val paddingRight: Int,
        val paddingBottom: Int,
        val scrollX: Int,
        val scrollY: Int
    )

    private data class SavedLayoutState(
        val view: View,
        val layoutParams: ViewGroup.LayoutParams,
        val width: Int,
        val height: Int,
        val leftMargin: Int,
        val topMargin: Int,
        val rightMargin: Int,
        val bottomMargin: Int,
        val linearWeight: Float,
        val flexFillWidth: Boolean,
        val flexFillHeight: Boolean,
        val flexWidthPercent: Float?,
        val flexHeightPercent: Float?,
        val flexIsLayerItem: Boolean,
        val flexLayerFill: Boolean,
        val flexLayerFloat: Boolean,
        val flexLayerAlignment: LayerAlignment,
        val flexLayerOffsetXPx: Int,
        val flexLayerOffsetYPx: Int,
        val flexLayerLeftPercent: Float?,
        val flexLayerTopPercent: Float?,
        val flexLayerRightPx: Int?,
        val flexLayerBottomPx: Int?
    )

    var isIsolated: Boolean = false
        private set

    var isolatedTargetView: View? = null
        private set

    private val savedVisibilities = mutableListOf<SavedVisibility>()
    private val recordedVisibilityViews = HashSet<Int>()
    private val savedGroupGeometries = mutableListOf<SavedGroupGeometry>()
    private val recordedGeometryGroups = HashSet<Int>()
    private val savedLayoutStates = mutableListOf<SavedLayoutState>()

    fun isolateTarget(activity: GenericUiFragmentActivity, targetView: View) {
        if (isIsolated) {
            if (isolatedTargetView === targetView) {
                return
            }
            restoreInternal(activity, syncChrome = false)
        }

        val rootLayout = activity.rootLayout
        val contentContainer = activity.contentContainer

        activity.toolbarView?.let { tb ->
            recordAndSetVisibility(tb, View.GONE)
        }
        activity.bottomNavigationView?.let { bn ->
            recordAndSetVisibility(bn, View.GONE)
        }

        // Clear rootLayout padding (system bar insets) while isolated so PiP content fills edge-to-edge.
        if (rootLayout != null && rootLayout !== targetView) {
            recordAndClearGroupGeometry(rootLayout, resetScroll = false)
        }

        recordAndSetVisibility(targetView, View.VISIBLE)

        var current: View = targetView
        var depth = 0
        while (depth < 64) {
            val parent = current.parent as? ViewGroup ?: break
            recordAndSetVisibility(parent, View.VISIBLE)
            recordAndClearGroupGeometry(parent, resetScroll = true)

            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i) ?: continue
                if (child !== current) {
                    recordAndSetVisibility(child, View.GONE)
                }
            }

            expandLayoutParamsToFillParent(current)
            parent.requestLayout()

            if (parent === contentContainer || parent === rootLayout) {
                if (parent === contentContainer && rootLayout != null && contentContainer.parent === rootLayout) {
                    expandLayoutParamsToFillParent(contentContainer)
                    rootLayout.requestLayout()
                }
                break
            }
            current = parent
            depth++
        }

        targetView.requestLayout()
        isIsolated = true
        isolatedTargetView = targetView
    }

    fun restore(activity: GenericUiFragmentActivity) {
        if (!isIsolated &&
            savedVisibilities.isEmpty() &&
            savedGroupGeometries.isEmpty() &&
            savedLayoutStates.isEmpty()
        ) {
            return
        }
        restoreInternal(activity, syncChrome = true)
    }

    private fun restoreInternal(activity: GenericUiFragmentActivity, syncChrome: Boolean) {
        for (saved in savedLayoutStates.asReversed()) {
            val lp = saved.layoutParams
            lp.width = saved.width
            lp.height = saved.height
            if (lp is ViewGroup.MarginLayoutParams) {
                lp.setMargins(saved.leftMargin, saved.topMargin, saved.rightMargin, saved.bottomMargin)
            }
            if (lp is LinearLayout.LayoutParams) {
                lp.weight = saved.linearWeight
            }
            if (lp is AndroidFlexContainerView.LayoutParams) {
                lp.weight = saved.linearWeight
                lp.fillWidth = saved.flexFillWidth
                lp.fillHeight = saved.flexFillHeight
                lp.widthPercent = saved.flexWidthPercent
                lp.heightPercent = saved.flexHeightPercent
                lp.isLayerItem = saved.flexIsLayerItem
                lp.layerFill = saved.flexLayerFill
                lp.layerFloat = saved.flexLayerFloat
                lp.layerAlignment = saved.flexLayerAlignment
                lp.layerOffsetXPx = saved.flexLayerOffsetXPx
                lp.layerOffsetYPx = saved.flexLayerOffsetYPx
                lp.layerLeftPercent = saved.flexLayerLeftPercent
                lp.layerTopPercent = saved.flexLayerTopPercent
                lp.layerRightPx = saved.flexLayerRightPx
                lp.layerBottomPx = saved.flexLayerBottomPx
            }
            saved.view.layoutParams = lp
            saved.view.requestLayout()
        }

        for (saved in savedGroupGeometries.asReversed()) {
            val vg = saved.viewGroup
            vg.setPadding(saved.paddingLeft, saved.paddingTop, saved.paddingRight, saved.paddingBottom)
            vg.scrollTo(saved.scrollX, saved.scrollY)
            vg.requestLayout()
        }

        for (saved in savedVisibilities.asReversed()) {
            saved.view.visibility = saved.visibility
            saved.view.requestLayout()
        }

        savedLayoutStates.clear()
        savedGroupGeometries.clear()
        recordedGeometryGroups.clear()
        savedVisibilities.clear()
        recordedVisibilityViews.clear()
        isIsolated = false
        isolatedTargetView = null

        if (syncChrome) {
            activity.hostedFragment?.let { frag ->
                activity.syncFragmentChrome(frag)
            }
            activity.rootLayout?.let { root ->
                ViewCompat.requestApplyInsets(root)
                root.requestLayout()
            }
        }
    }

    private fun recordAndSetVisibility(view: View, newVisibility: Int) {
        val key = System.identityHashCode(view)
        if (recordedVisibilityViews.add(key)) {
            savedVisibilities.add(SavedVisibility(view = view, visibility = view.visibility))
        }
        if (view.visibility != newVisibility) {
            view.visibility = newVisibility
        }
    }

    private fun recordAndClearGroupGeometry(vg: ViewGroup, resetScroll: Boolean) {
        val key = System.identityHashCode(vg)
        if (recordedGeometryGroups.add(key)) {
            savedGroupGeometries.add(
                SavedGroupGeometry(
                    viewGroup = vg,
                    paddingLeft = vg.paddingLeft,
                    paddingTop = vg.paddingTop,
                    paddingRight = vg.paddingRight,
                    paddingBottom = vg.paddingBottom,
                    scrollX = vg.scrollX,
                    scrollY = vg.scrollY
                )
            )
        }
        if (vg.paddingLeft != 0 || vg.paddingTop != 0 || vg.paddingRight != 0 || vg.paddingBottom != 0) {
            vg.setPadding(0, 0, 0, 0)
        }
        if (resetScroll && (vg.scrollX != 0 || vg.scrollY != 0)) {
            vg.scrollTo(0, 0)
        }
    }

    private fun expandLayoutParamsToFillParent(view: View) {
        val lp = view.layoutParams ?: ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        val marginLp = lp as? ViewGroup.MarginLayoutParams
        val linearLp = lp as? LinearLayout.LayoutParams
        val flexLp = lp as? AndroidFlexContainerView.LayoutParams

        savedLayoutStates.add(
            SavedLayoutState(
                view = view,
                layoutParams = lp,
                width = lp.width,
                height = lp.height,
                leftMargin = marginLp?.leftMargin ?: 0,
                topMargin = marginLp?.topMargin ?: 0,
                rightMargin = marginLp?.rightMargin ?: 0,
                bottomMargin = marginLp?.bottomMargin ?: 0,
                linearWeight = linearLp?.weight ?: (flexLp?.weight ?: 0f),
                flexFillWidth = flexLp?.fillWidth ?: false,
                flexFillHeight = flexLp?.fillHeight ?: false,
                flexWidthPercent = flexLp?.widthPercent,
                flexHeightPercent = flexLp?.heightPercent,
                flexIsLayerItem = flexLp?.isLayerItem ?: false,
                flexLayerFill = flexLp?.layerFill ?: false,
                flexLayerFloat = flexLp?.layerFloat ?: false,
                flexLayerAlignment = flexLp?.layerAlignment ?: LayerAlignment.TOP_START,
                flexLayerOffsetXPx = flexLp?.layerOffsetXPx ?: 0,
                flexLayerOffsetYPx = flexLp?.layerOffsetYPx ?: 0,
                flexLayerLeftPercent = flexLp?.layerLeftPercent,
                flexLayerTopPercent = flexLp?.layerTopPercent,
                flexLayerRightPx = flexLp?.layerRightPx,
                flexLayerBottomPx = flexLp?.layerBottomPx
            )
        )

        lp.width = ViewGroup.LayoutParams.MATCH_PARENT
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT
        marginLp?.setMargins(0, 0, 0, 0)
        linearLp?.weight = 0f
        if (flexLp != null) {
            flexLp.weight = 0f
            flexLp.fillWidth = true
            flexLp.fillHeight = true
            flexLp.widthPercent = null
            flexLp.heightPercent = null
            flexLp.layerFill = true
            flexLp.layerFloat = false
            flexLp.layerOffsetXPx = 0
            flexLp.layerOffsetYPx = 0
            flexLp.layerLeftPercent = null
            flexLp.layerTopPercent = null
            flexLp.layerRightPx = null
            flexLp.layerBottomPx = null
        }
        view.layoutParams = lp
    }
}
