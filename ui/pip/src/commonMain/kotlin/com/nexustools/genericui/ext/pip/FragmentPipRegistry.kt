package com.nexustools.genericui.ext.pip

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.util.WeakReference
import com.nexustools.genericui.window.IWindow

/**
 * Weak-keyed registry associating active [Fragment] and [IWindow] instances with
 * their [FragmentPipHelper] using object identity comparison.
 */
object FragmentPipRegistry {

    private data class FragmentEntry(
        val ref: WeakReference<Fragment>,
        val helper: FragmentPipHelper
    )

    private data class WindowEntry(
        val ref: WeakReference<IWindow>,
        val helper: FragmentPipHelper
    )

    private val fragmentEntries = mutableListOf<FragmentEntry>()
    private val windowEntries = mutableListOf<WindowEntry>()

    fun register(fragment: Fragment, helper: FragmentPipHelper) {
        pruneDeadEntries()
        fragmentEntries.removeAll { it.ref.get() === fragment }
        fragmentEntries.add(FragmentEntry(WeakReference(fragment), helper))
    }

    fun unregister(fragment: Fragment, helper: FragmentPipHelper? = null) {
        pruneDeadEntries()
        fragmentEntries.removeAll { entry ->
            entry.ref.get() === fragment && (helper == null || entry.helper === helper)
        }
    }

    fun findForFragment(fragment: Fragment?): FragmentPipHelper? {
        if (fragment == null) return null
        pruneDeadEntries()
        return fragmentEntries.lastOrNull { it.ref.get() === fragment }?.helper
    }

    fun registerWindow(window: IWindow, helper: FragmentPipHelper) {
        pruneDeadEntries()
        windowEntries.removeAll { it.ref.get() === window }
        windowEntries.add(WindowEntry(WeakReference(window), helper))
    }

    fun unregisterWindow(window: IWindow, helper: FragmentPipHelper? = null) {
        pruneDeadEntries()
        windowEntries.removeAll { entry ->
            entry.ref.get() === window && (helper == null || entry.helper === helper)
        }
    }

    fun findForWindow(window: IWindow?): FragmentPipHelper? {
        if (window == null) return null
        pruneDeadEntries()
        return windowEntries.lastOrNull { it.ref.get() === window }?.helper
    }

    fun clearForTest() {
        fragmentEntries.forEach { it.ref.clear() }
        windowEntries.forEach { it.ref.clear() }
        fragmentEntries.clear()
        windowEntries.clear()
    }

    private fun pruneDeadEntries() {
        fragmentEntries.removeAll { it.ref.get() == null || it.helper.isDisposed }
        windowEntries.removeAll { it.ref.get() == null || it.helper.isDisposed }
    }
}
