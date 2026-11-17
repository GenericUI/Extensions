package com.nexustools.genericui.ext.alien.web

import com.nexustools.genericui.ext.alien.AlienExtension

/**
 * Bootstrapper for `:extensions:alien:web` on the Web target (`js` and `wasmJs`).
 *
 * Installs [WebAlienPlatformBridge] so `AlienContainer`, `AlienFragment`, and `UnalienContainer`
 * forward directly to the parent container and use native browser DOM implementations.
 */
object WebAlienBootstrap {
    init {
        install()
    }

    fun install() {
        AlienExtension.platformBridge = WebAlienPlatformBridge
    }
}
