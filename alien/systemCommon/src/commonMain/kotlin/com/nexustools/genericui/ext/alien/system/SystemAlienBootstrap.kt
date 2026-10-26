package com.nexustools.genericui.ext.alien.system

import com.nexustools.genericui.ext.alien.AlienExtension

/**
 * Bootstrapper for `:extensions:alien:sharedSystem` on System targets (`:desktop`, `:mobile`, `:swing`).
 */
object SystemAlienBootstrap {
    fun install() {
        AlienExtension.platformBridge = SystemAlienPlatformBridge
    }
}
