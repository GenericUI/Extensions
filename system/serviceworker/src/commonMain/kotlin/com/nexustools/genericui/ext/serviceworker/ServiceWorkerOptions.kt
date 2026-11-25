package com.nexustools.genericui.ext.serviceworker

/**
 * Runtime options propagated from Gradle `genericUi { extensions { serviceWorker { ... } } }`
 * and application metadata to the platform Service Worker host and client channel.
 */
data class ServiceWorkerOptions(
    val appId: String = "com.nexustools.genericui.app",
    val appName: String = "GenericUI",
    val baseName: String = "app",
    val autostart: Boolean = false,
    val trayIcon: String? = null,
    val openWindowOnLaunch: Boolean = true,
    val androidForegroundServiceType: String = "specialUse",
    val androidSpecialUseFgsSubtype: String = "GenericUI background ServiceWorker execution",
    val hasWorker: Boolean = false,
    val swingWorkerMainClass: String = "",
    val macosTrayIcon: String? = null
)
