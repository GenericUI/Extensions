package com.nexustools.genericui.ext.pip.android

import android.graphics.Rect
import android.util.Rational
import android.view.View
import android.view.ViewTreeObserver
import com.nexustools.genericui.android.AndroidBaseNativeImpl
import com.nexustools.genericui.core.AbstractComponent

/**
 * Tracks a PiP target's Android [View] in window coordinates (`getLocationInWindow`), computes its
 * non-empty `sourceRectHint` [Rect] and aspect [Rational] clamped to Android's valid PiP bounds
 * `[100:239 .. 239:100]`, and deduplicates identical updates before notifying [onSourceRectChanged].
 */
class AndroidPipSourceRectTracker(
    private val onSourceRectChanged: (Rect, Rational) -> Unit
) {

    var currentSourceRect: Rect? = null
        private set

    var currentAspectRatio: Rational = Rational(16, 9)
        private set

    private var trackedComponent: AbstractComponent? = null
    private var preferredAspectRatio: Pair<Int, Int>? = null
    private var observedView: View? = null
    private var observedVto: ViewTreeObserver? = null
    private var fallbackView: View? = null
    private var fallbackVto: ViewTreeObserver? = null
    private var lastNotifiedRect: Rect? = null
    private var lastNotifiedAspect: Rational? = null

    private val layoutChangeListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        refresh()
    }

    private val scrollChangedListener = ViewTreeObserver.OnScrollChangedListener {
        refresh()
    }

    private val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        refresh()
    }

    private val fallbackGlobalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        val comp = trackedComponent ?: return@OnGlobalLayoutListener
        val resolved = resolveComponentView(comp)
        if (resolved != null && resolved !== observedView) {
            detachFallbackListeners()
            attachToView(resolved)
        }
        refresh()
    }

    private val attachStateListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            attachVtoListenersIfNeeded(v)
            refresh()
        }

        override fun onViewDetachedFromWindow(v: View) {
            detachVtoListeners()
        }
    }

    fun track(
        component: AbstractComponent?,
        fallbackHostView: View? = null,
        preferredAspectRatio: Pair<Int, Int>? = null
    ) {
        val resolvedView = resolveComponentView(component)
        if (trackedComponent === component &&
            observedView === resolvedView &&
            this.preferredAspectRatio == preferredAspectRatio &&
            (observedView != null || fallbackView === fallbackHostView)
        ) {
            refresh()
            return
        }

        detach()
        trackedComponent = component
        this.preferredAspectRatio = preferredAspectRatio

        if (resolvedView != null) {
            attachToView(resolvedView)
        } else if (component != null && fallbackHostView != null) {
            attachFallbackListeners(fallbackHostView)
        }
        refresh()
    }

    fun trackView(view: View?, preferredAspectRatio: Pair<Int, Int>? = null) {
        if (trackedComponent == null &&
            observedView === view &&
            this.preferredAspectRatio == preferredAspectRatio
        ) {
            refresh()
            return
        }

        detach()
        trackedComponent = null
        this.preferredAspectRatio = preferredAspectRatio
        if (view != null) {
            attachToView(view)
        }
        refresh()
    }

    fun refresh() {
        if (observedView == null) {
            val comp = trackedComponent
            if (comp != null) {
                val lateResolved = resolveComponentView(comp)
                if (lateResolved != null) {
                    detachFallbackListeners()
                    attachToView(lateResolved)
                }
            }
        }
        val view = observedView ?: return
        val w = view.width
        val h = view.height
        if (w <= 0 || h <= 0 || view.visibility != View.VISIBLE) {
            return
        }
        val coords = IntArray(2)
        view.getLocationInWindow(coords)
        val rect = Rect(coords[0], coords[1], coords[0] + w, coords[1] + h)
        if (rect.isEmpty) return

        val pref = preferredAspectRatio
        val rational = if (pref != null && pref.first > 0 && pref.second > 0) {
            clampAspectRatio(pref.first, pref.second)
        } else {
            clampAspectRatio(w, h)
        }

        if (rect == lastNotifiedRect && rational == lastNotifiedAspect) {
            return
        }

        lastNotifiedRect = Rect(rect)
        lastNotifiedAspect = rational
        currentSourceRect = Rect(rect)
        currentAspectRatio = rational
        onSourceRectChanged(Rect(rect), rational)
    }

    fun detach() {
        detachObservedListeners()
        detachFallbackListeners()
        trackedComponent = null
        preferredAspectRatio = null
        currentSourceRect = null
        lastNotifiedRect = null
        lastNotifiedAspect = null
        currentAspectRatio = Rational(16, 9)
    }

    private fun resolveComponentView(component: AbstractComponent?): View? {
        if (component == null) return null
        val impl = runCatching { component.ensureNativeImpl() }.getOrNull() as? AndroidBaseNativeImpl
        return impl?.nativeWidget?.androidView
    }

    private fun attachToView(view: View) {
        detachObservedListeners()
        observedView = view
        view.addOnLayoutChangeListener(layoutChangeListener)
        view.addOnAttachStateChangeListener(attachStateListener)
        attachVtoListenersIfNeeded(view)
    }

    private fun attachVtoListenersIfNeeded(view: View) {
        detachVtoListeners()
        val vto = view.viewTreeObserver
        if (vto != null && vto.isAlive) {
            vto.addOnScrollChangedListener(scrollChangedListener)
            vto.addOnGlobalLayoutListener(globalLayoutListener)
            observedVto = vto
        }
    }

    private fun detachVtoListeners() {
        val savedVto = observedVto
        if (savedVto != null && savedVto.isAlive) {
            runCatching { savedVto.removeOnScrollChangedListener(scrollChangedListener) }
            runCatching { savedVto.removeOnGlobalLayoutListener(globalLayoutListener) }
        }
        val currentVto = observedView?.viewTreeObserver
        if (currentVto != null && currentVto !== savedVto && currentVto.isAlive) {
            runCatching { currentVto.removeOnScrollChangedListener(scrollChangedListener) }
            runCatching { currentVto.removeOnGlobalLayoutListener(globalLayoutListener) }
        }
        observedVto = null
    }

    private fun detachObservedListeners() {
        detachVtoListeners()
        observedView?.let { v ->
            runCatching { v.removeOnLayoutChangeListener(layoutChangeListener) }
            runCatching { v.removeOnAttachStateChangeListener(attachStateListener) }
        }
        observedView = null
    }

    private fun attachFallbackListeners(hostView: View) {
        detachFallbackListeners()
        fallbackView = hostView
        val vto = hostView.viewTreeObserver
        if (vto != null && vto.isAlive) {
            vto.addOnGlobalLayoutListener(fallbackGlobalLayoutListener)
            fallbackVto = vto
        }
    }

    private fun detachFallbackListeners() {
        val savedVto = fallbackVto
        if (savedVto != null && savedVto.isAlive) {
            runCatching { savedVto.removeOnGlobalLayoutListener(fallbackGlobalLayoutListener) }
        }
        val currentVto = fallbackView?.viewTreeObserver
        if (currentVto != null && currentVto !== savedVto && currentVto.isAlive) {
            runCatching { currentVto.removeOnGlobalLayoutListener(fallbackGlobalLayoutListener) }
        }
        fallbackVto = null
        fallbackView = null
    }

    companion object {
        const val MIN_ASPECT_NUM: Int = 100
        const val MIN_ASPECT_DEN: Int = 239
        const val MAX_ASPECT_NUM: Int = 239
        const val MAX_ASPECT_DEN: Int = 100

        /**
         * Clamps [numerator]:[denominator] into Android's mandatory PiP aspect ratio range
         * `[100/239 .. 239/100]` (`~0.41841 .. 2.39`), preserving exact boundaries without
         * floating-point underflow below `1 / 2.39`.
         */
        fun clampAspectRatio(numerator: Int, denominator: Int): Rational {
            if (numerator <= 0 || denominator <= 0) {
                return Rational(16, 9)
            }
            val ratio = numerator.toDouble() / denominator.toDouble()
            val minRatio = MIN_ASPECT_NUM.toDouble() / MIN_ASPECT_DEN.toDouble()
            val maxRatio = MAX_ASPECT_NUM.toDouble() / MAX_ASPECT_DEN.toDouble()
            if (ratio < minRatio) {
                return Rational(MIN_ASPECT_NUM, MIN_ASPECT_DEN)
            }
            if (ratio > maxRatio) {
                return Rational(MAX_ASPECT_NUM, MAX_ASPECT_DEN)
            }
            val divisor = gcd(numerator, denominator).coerceAtLeast(1)
            return Rational(numerator / divisor, denominator / divisor)
        }

        private fun gcd(a: Int, b: Int): Int {
            var x = a
            var y = b
            while (y != 0) {
                val t = x % y
                x = y
                y = t
            }
            return x
        }
    }
}
