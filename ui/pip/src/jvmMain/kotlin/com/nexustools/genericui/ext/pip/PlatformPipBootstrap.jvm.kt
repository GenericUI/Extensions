package com.nexustools.genericui.ext.pip

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.ext.pip.swing.SwingFloatingPipBackend

internal actual object PlatformPipBootstrap {
    actual fun ensureDefaultRegistered() {
        if (PipComponentClassifier.platformCompatibleDetector == null) {
            PipComponentClassifier.platformCompatibleDetector = ::hasJvmPipCompatibleAnnotation
        }
        if (!PipRegistry.hasRegisteredFactory()) {
            PipRegistry.registerBackendFactory { SwingFloatingPipBackend() }
        }
    }

    internal fun hasJvmPipCompatibleAnnotation(component: AbstractComponent): Boolean {
        var cls: Class<*>? = component::class.java
        while (cls != null) {
            if (cls.isAnnotationPresent(PiPCompatible::class.java)) {
                return true
            }
            cls = cls.superclass
        }
        return false
    }
}
