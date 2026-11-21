package com.nexustools.genericui.ext.pip.swing

import com.nexustools.genericui.ext.pip.PipComponentClassifier
import com.nexustools.genericui.ext.pip.PipRegistry
import com.nexustools.genericui.ext.pip.PlatformPipBootstrap

/**
 * Registers the JVM Swing floating-window [SwingFloatingPipBackend].
 */
object SwingPipProvider {
    fun register() {
        if (PipComponentClassifier.platformCompatibleDetector == null) {
            PipComponentClassifier.platformCompatibleDetector = PlatformPipBootstrap::hasJvmPipCompatibleAnnotation
        }
        PipRegistry.registerBackendFactory { SwingFloatingPipBackend() }
    }
}
