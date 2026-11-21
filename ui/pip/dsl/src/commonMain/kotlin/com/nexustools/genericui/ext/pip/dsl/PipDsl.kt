package com.nexustools.genericui.ext.pip.dsl

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.ext.pip.FragmentPipHelper
import com.nexustools.genericui.ext.pip.FragmentPipRegistry

/**
 * Installs (or updates the existing) [FragmentPipHelper] for this [Fragment], configures it
 * via [block], and binds it to the hosting window when already available.
 */
fun Fragment.installPipHelper(block: FragmentPipHelper.() -> Unit = {}): FragmentPipHelper {
    val existing = FragmentPipRegistry.findForFragment(this)
    val helper = if (existing != null && !existing.isDisposed) {
        existing
    } else {
        FragmentPipHelper(this)
    }
    helper.block()
    val window = hostingWindow ?: (application ?: Application.current)?.mainWindow
    if (window != null) {
        helper.bindWindow(window)
    } else {
        helper.notifyStateChanged()
    }
    return helper
}

/**
 * Returns the [FragmentPipHelper] currently registered for this [Fragment], if any.
 */
val Fragment.pipHelper: FragmentPipHelper?
    get() = FragmentPipRegistry.findForFragment(this)
