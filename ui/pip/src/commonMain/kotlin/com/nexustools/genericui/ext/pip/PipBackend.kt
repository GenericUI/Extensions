package com.nexustools.genericui.ext.pip

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.window.IWindow

interface PipBackend {
    val capabilities: PipCapabilities
    val isInPipMode: Boolean
    fun supportsComponent(component: AbstractComponent): Boolean
    fun attachHost(window: IWindow?, fragment: Fragment?, helper: FragmentPipHelper)
    fun detachHost()
    fun syncSessionState()
    fun enterPip(): Boolean
    fun exitPip(): Boolean
}
