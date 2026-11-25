# `:extensions:system:serviceworker` & `:extensions:system:serviceworker:dsl` — Cross-Platform Background Service Worker, Tray Daemon, Android Foreground Service, & Typed IPC

> **Navigation**: [**GenericUI Root (`../../README.md`)**](../../README.md) · [**Extensions Index (`../README.md`)**](../README.md) · [**Root Extensions Guide (`../../docs/extensions.md`)**](../../docs/extensions.md) · [**Application & Targets (`../../docs/application.md`)**](../../docs/application.md) · [**Source Sets (`../../docs/sourcesets.md`)**](../../docs/sourcesets.md) · [**Backends (`../../docs/backends.md`)**](../../docs/backends.md) · [**Gradle Plugin (`../../docs/gradle-plugin.md`)**](../../docs/gradle-plugin.md)

`:extensions:system:serviceworker` (`com.nexustools.genericui.ext.serviceworker`) is a **Single Consolidated KMP Project** providing persistent background execution (`ServiceWorker`, `@MainServiceWorker`), reflection-free typed IPC (`ServiceWorkerChannel`, `@ServiceWorkerMessage`, `ServiceWorkerCodecRegistry`), Desktop system tray / login autostart integration, Android ongoing foreground notifications (`ServiceCompat.startForeground(...)`), and Web progressive Service Worker execution (`./serviceworker.js` + `${baseName}.sw.js` / `${baseName}.sw.wasm`).

Its companion module `:extensions:system:serviceworker:dsl` (`com.nexustools.genericui.ext.serviceworker.dsl`) provides `kotlinx.html`-style DSL helpers (`onMessage`, `onRequest`, `trayMenu { ... }`, `notificationActions { ... }`).

> [!IMPORTANT]
> **Honest Platform Support — `ServiceWorkerExtension.isSupported == false` on iOS & iPadOS**:
> Because iOS and iPadOS do not permit unconstrained persistent background daemons equivalent to Android Foreground Services, Desktop tray daemons, or Web Service Workers, [`AppleMobileServiceWorkerRuntime`](../system/serviceworker/src/iosMain/kotlin/com/nexustools/genericui/ext/serviceworker/AppleMobileServiceWorkerRuntime.kt) sets `ServiceWorkerExtension.isSupported = false` and `ServiceWorkerExtension.isAutostartSupported = false` with **zero simulation or fallback faking**. On `IOS` and `IPADOS`:
> - [`ServiceWorkerExtensionContributor`](../../gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/spi/ServiceWorkerExtensionContributor.kt) emits no worker factory registration (`contributeAppleMobile` returns `null`, `hasWorker = false`), so `@MainServiceWorker` classes are never instantiated.
> - `ServiceWorkerChannel.connect()` returns `false`, `send()` and `request()` fail fast with `UnsupportedOperationException`, and `tryRequest()` returns `Result.failure(...)`.

---

## 1. Cross-Platform Execution Architecture Matrix

| Target Platform | `isSupported` | `isAutostartSupported` | Runtime Process / Host Architecture & Codegen |
| :--- | :--- | :--- | :--- |
| **Desktop Native** (`:windows`, `:macos`, `:gtk`, `:kde`) | **`true`** | **`true`** | Secondary singleton executable (`BinaryBaseName = "serviceworker"`): `serviceworker.exe` (Windows), `Contents/Helpers/<AppName> Service Worker.app/Contents/MacOS/serviceworker` inside macOS `.app`, and `serviceworker` alias on Linux, launched headless alongside the main UI app via [`DesktopServiceWorkerHost`](../system/serviceworker/src/desktopMain/kotlin/com/nexustools/genericui/ext/serviceworker/DesktopServiceWorkerHost.kt) using OS-native IPC authenticated via `serviceworker-endpoint.json` (`transport`, `address`, 32-byte hex `token`): **macOS** uses `libxpc` Mach services (`<appId>.serviceworker`) under `launchd` (`MachServices`) with POSIX `AF_UNIX` `SOCK_STREAM` (`getpeereid` UID verification) for direct non-`launchd` launches; **Linux** (`:gtk`, `:kde`) uses pure kernel-native filesystem `AF_UNIX` `SOCK_STREAM` (`$XDG_RUNTIME_DIR/<appId>/sw.sock` or `/tmp/genericui-<euid>/`, `0700`/`0600` permissions, `SO_PEERCRED` UID verification, `MSG_NOSIGNAL`, stale socket recovery, zero `GDBus`/D-Bus IPC); **Windows** uses overlapped Win32 Named Pipes (`\\.\pipe\genericui.<userHash>.<scopeHash>.sw`) with `PIPE_REJECT_REMOTE_CLIENTS` and an explicit current-user SID DACL. Hosts OS `SystemTrayIcon`. |
| **Desktop JVM (`:swing`)** | **`true`** | **`true`** | Launches child JVM process running generated `GeneratedSwingServiceWorkerMain` using JDK 16+ JEP 380 native `AF_UNIX` (`StandardProtocolFamily.UNIX` / `UnixDomainSocketAddress`) across macOS, Linux, and Windows 10/11 + `serviceworker-endpoint.json` (`transport`, `address`, 32-byte hex `token`) and desktop `SystemTrayIcon`. |
| **Android (`:android`)** | **`true`** | **`true`** *(when `autostart = true`)* | Generated `GeneratedAndroidServiceWorkerService` backed by [`AndroidServiceWorkerController`](../system/serviceworker/src/androidMain/kotlin/com/nexustools/genericui/ext/serviceworker/AndroidServiceWorkerController.kt) and promoted automatically via `AndroidExtensionRuntimeBridge.promoteToForegroundService` (`ServiceCompat.startForeground`) with an ongoing foreground notification and default `"Stop"` button (`ServiceWorkerNotificationAction.createDefaultStopAction()`). When `autostart = true`, emits `GeneratedAndroidServiceWorkerBootReceiver` (`BOOT_COMPLETED` / `MY_PACKAGE_REPLACED`) + `RECEIVE_BOOT_COMPLETED`. |
| **Web (`:js`, `:wasmJs`)** | **`true`** | **`false`** | Registers unified root `./serviceworker.js` via [`WebServiceWorkerRuntime`](../system/serviceworker/src/webMain/kotlin/com/nexustools/genericui/ext/serviceworker/WebServiceWorkerRuntime.kt), loading `${appBaseName}.sw.js` and `${appBaseName}.sw.wasm` (`WebWorkerBinarySpec`) with bidirectional `MessageChannel` / `postMessage` IPC. |
| **iOS & iPadOS** (`:ios`) | **`false`** | **`false`** | Explicitly unsupported (`AppleMobileServiceWorkerRuntime`); zero simulation and zero `@MainServiceWorker` instantiation. |

---

## 2. Gradle Plugin Configuration (`serviceWorker { ... }`)

Enabling `serviceWorker()` in `build.gradle.kts` adds `:extensions:system:serviceworker` (and `:extensions:system:serviceworker:dsl` when `dsl()` is enabled). If a project defines **zero** `@MainServiceWorker` classes, the contributor produces zero-default footprint (no secondary binaries, no Android service/manifest entries, and no `.sw.wasm` web worker bundle):

```kotlin
plugins {
    id("com.nexustools.genericui")
}

genericui {
    app {
        id = "com.example.syncdaemon"
        name = "Sync Daemon"
        baseName = "syncdaemon"
    }
    backends {
        allExceptSwing()
    }
    extensions {
        dsl() // Auto-wires :extensions:system:serviceworker:dsl
        serviceWorker {
            autostart = true
            trayIcon = "src/commonMain/resources/tray.png"
            openWindowOnLaunch = false
            androidForegroundServiceType = "dataSync"
            androidSpecialUseFgsSubtype = "Continuous background file & state synchronization"
        }
    }
}
```

### `GenericUiServiceWorkerExtensionSpec` Options

| Option | Default | Effect |
| :--- | :--- | :--- |
| `autostart: Boolean` | `false` | Registers desktop OS login autostart (`DesktopAutostartContribution` pointing to `serviceworker`) and emits Android `RECEIVE_BOOT_COMPLETED` + `GeneratedAndroidServiceWorkerBootReceiver`. |
| `trayIcon: String?` | `null` | Overrides desktop tray / status bar icon (`tray_icon.png` / `tray_icon.svg` on macOS unwrapped without Chrome tile framing, preserving the Dock app icon). |
| `openWindowOnLaunch: Boolean` | `true` | When `false`, launching the desktop main binary without `--open-window` or `--settings-only` ensures the singleton `serviceworker` daemon is started and exits `0` immediately without opening a GUI window. |
| `androidForegroundServiceType: String` | `"specialUse"` | Sets `android:foregroundServiceType` on `GeneratedAndroidServiceWorkerService` and adds required foreground-service type permissions. |
| `androidSpecialUseFgsSubtype: String` | `"GenericUI background ServiceWorker execution"` | Populates `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` whenever `"specialUse"` is included in `androidForegroundServiceType`. |

---

## 3. `@MainServiceWorker`, `@ServiceWorkerMessage`, & Core API

### 3.1 `@MainServiceWorker` Compile-Time Rules
- Annotate a subclass of [`ServiceWorker`](../system/serviceworker/src/commonMain/kotlin/com/nexustools/genericui/ext/serviceworker/ServiceWorker.kt) with `@MainServiceWorker(vararg targets: AppTarget = [AppTarget.COMMON])`.
- Must provide a **public zero-argument constructor**.
- At most **one** `@MainServiceWorker` may apply per enabled target (platform-specific targets override `AppTarget.COMMON` via specificity ranking; conflicting definitions at the same specificity fail the build in [`ServiceWorkerExtensionContributor`](../../gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/spi/ServiceWorkerExtensionContributor.kt)).

### 3.2 Core Classes & Methods

| Symbol | Key Members |
| :--- | :--- |
| [`ServiceWorkerExtension`](../system/serviceworker/src/commonMain/kotlin/com/nexustools/genericui/ext/serviceworker/ServiceWorkerExtension.kt) | `isSupported`, `isAutostartSupported`, `options: ServiceWorkerOptions`, `channel: ServiceWorkerChannel`, `ensureStarted()`, `activeWorker()`, `isAutostartEnabled()`, `setAutostartEnabled(Boolean)`, `prepareDesktopMainLaunch(...)`, `runDesktopWorkerMain(...)`. |
| [`ServiceWorker`](../system/serviceworker/src/commonMain/kotlin/com/nexustools/genericui/ext/serviceworker/ServiceWorker.kt) | Lifecycle hooks `onStart()`, `onStop()`, `onClientConnected(client)`, `onClientDisconnected(client)`; coroutine launcher `launch { ... }`; messaging `onMessage<T>`, `onRequest<T>`, `broadcast(msg)`; tray APIs `setTrayIcon(...)`, `setTrayTooltip(...)`, `setTrayBadgeText(...)`, `setTrayMenu(ContextMenu?)`, `setTrayClickListener(...)`; window control `openMainWindow(route)`, `closeMainWindow()`, `stopSelf()`, `exitApplication()`; runtime autostart `isAutostartEnabled()`, `setAutostartEnabled(Boolean)`; Android notification APIs below. |
| **Android Foreground Notification APIs** | Automatic ongoing foreground notification started via `ServiceCompat.startForeground(...)` with default `"Stop"` button (`ServiceWorkerNotificationAction.createDefaultStopAction()`). Customize via `notificationTitle`, `notificationText`, `notificationSubText`, `notificationIcon`, `setNotificationTitle(...)`, `setNotificationText(...)`, `setNotificationSubText(...)`, `setNotificationIcon(...)`, `extendNotificationActions(...)`, `replaceNotificationActions(...)`, `getEffectiveNotificationActions()`. |
| [`ServiceWorkerChannel`](../system/serviceworker/src/commonMain/kotlin/com/nexustools/genericui/ext/serviceworker/ServiceWorkerChannel.kt) | Client-side IPC handle (`ServiceWorkerExtension.channel`): `isSupported`, `isConnected`, `suspend connect(): Boolean`, `suspend disconnect()`, `suspend send(message)`, `suspend request<R>(payload, timeoutMs): R`, `suspend tryRequest<R>(payload, timeoutMs): Result<R>`, `onMessage<T> { ... }`, `onRequest<T> { req, reply -> ... }`, `onConnectionStateChanged { ... }`. |
| [`ServiceWorkerClient`](../system/serviceworker/src/commonMain/kotlin/com/nexustools/genericui/ext/serviceworker/ServiceWorkerClient.kt) & [`ServiceWorkerReply`](../system/serviceworker/src/commonMain/kotlin/com/nexustools/genericui/ext/serviceworker/ServiceWorkerReply.kt) | Per-connected UI window/Activity/tab handle inside `ServiceWorker` (`clientId`, `isConnected`, `send(msg)`, `request<R>(payload)`) and typed reply handle (`reply.send(response)`, `reply.fail(error)`). |
| `@ServiceWorkerMessage` & [`ServiceWorkerCodecRegistry`](../system/serviceworker/src/commonMain/kotlin/com/nexustools/genericui/ext/serviceworker/ServiceWorkerCodecRegistry.kt) | Annotate any Kotlin `data class` with `@ServiceWorkerMessage`; [`ServiceWorkerMessageCodegen`](../../gradle-plugin/src/main/kotlin/com/nexustools/genericui/plugin/spi/ServiceWorkerMessageCodegen.kt) generates reflection-free `PreferenceElement` codecs (`<Msg>ServiceWorkerCodec.kt`) and `GeneratedServiceWorkerCodecRegistrar.registerAll()` supporting primitives, enums, nullable types, `ByteArray`, nested `@ServiceWorkerMessage` types, `List`, and `Map`. |

---

## 4. End-to-End Usage Example (`@MainServiceWorker`, `@ServiceWorkerMessage`, & `:dsl`)

```kotlin
import com.nexustools.genericui.component.fragment.AppFragment
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.enums.system.AppTarget
import com.nexustools.genericui.ext.dsl.build
import com.nexustools.genericui.ext.dsl.button
import com.nexustools.genericui.ext.dsl.p
import com.nexustools.genericui.ext.dsl.vstack
import com.nexustools.genericui.ext.serviceworker.MainServiceWorker
import com.nexustools.genericui.ext.serviceworker.ServiceWorker
import com.nexustools.genericui.ext.serviceworker.ServiceWorkerClient
import com.nexustools.genericui.ext.serviceworker.ServiceWorkerExtension
import com.nexustools.genericui.ext.serviceworker.ServiceWorkerMessage
import com.nexustools.genericui.ext.serviceworker.dsl.action
import com.nexustools.genericui.ext.serviceworker.dsl.item
import com.nexustools.genericui.ext.serviceworker.dsl.notificationActions
import com.nexustools.genericui.ext.serviceworker.dsl.onMessage
import com.nexustools.genericui.ext.serviceworker.dsl.onRequest
import com.nexustools.genericui.ext.serviceworker.dsl.separator
import com.nexustools.genericui.ext.serviceworker.dsl.trayMenu
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@ServiceWorkerMessage
data class SyncTriggerCommand(val forceFullSync: Boolean = false)

@ServiceWorkerMessage
data class SyncStatusQuery(val includePendingCount: Boolean = true)

@ServiceWorkerMessage
data class SyncStatusReply(val isSyncing: Boolean, val pendingItems: Int)

@MainServiceWorker(
    AppTarget.WINDOWS,
    AppTarget.MACOS,
    AppTarget.GTK,
    AppTarget.KDE,
    AppTarget.SWING,
    AppTarget.ANDROID,
    AppTarget.WEB
)
class BackgroundSyncWorker : ServiceWorker() {
    private var pendingItems = 0

    override fun onStart() {
        setNotificationTitle("Sync Daemon")
        setNotificationText("Monitoring workspace changes")
        setTrayTooltip("Sync Daemon — Idle")

        // Extends Android foreground notification buttons while keeping default "Stop"
        notificationActions(replaceDefaultStop = false) {
            action(id = "sync-now", label = "Sync Now") {
                setNotificationText("Manual sync requested")
            }
        }

        // Builds desktop system tray context menu
        trayMenu {
            item("Open Dashboard") { openMainWindow(route = "dashboard") }
            separator()
            item("Exit Sync Daemon") { exitApplication() }
        }

        onMessage<SyncTriggerCommand> { _, cmd ->
            pendingItems = if (cmd.forceFullSync) 12 else pendingItems + 1
            setNotificationText("Queued $pendingItems item(s)")
            delay(250)
            pendingItems = 0
            broadcast(SyncStatusReply(isSyncing = false, pendingItems = 0))
        }

        onRequest<SyncStatusQuery> { _, _, reply ->
            reply.send(SyncStatusReply(isSyncing = pendingItems > 0, pendingItems = pendingItems))
        }
    }

    override fun onClientConnected(client: ServiceWorkerClient) {
        launch {
            client.send(SyncStatusReply(isSyncing = pendingItems > 0, pendingItems = pendingItems))
        }
    }
}

@AppFragment(label = "Sync Dashboard", route = "dashboard")
class SyncDashboardFragment : Fragment() {
    override fun onCreate(savedState: FragmentBundle?) {
        super.onCreate(savedState)

        rootContainer.build {
            vstack(classes = "p-3 gap-2") {
                val statusLabel = p(
                    if (ServiceWorkerExtension.isSupported) "Connecting to background worker..."
                    else "Background Service Worker is unavailable on iOS / iPadOS."
                )

                if (ServiceWorkerExtension.isSupported) {
                    val channel = ServiceWorkerExtension.channel
                    channel.onMessage<SyncStatusReply> { status ->
                        statusLabel.text = "Syncing=${status.isSyncing} • Pending=${status.pendingItems}"
                    }
                    button("Query & Sync") {
                        onClick = {
                            lifecycleScope.launch {
                                val res = channel.tryRequest<SyncStatusReply>(SyncStatusQuery())
                                res.onSuccess { status ->
                                    statusLabel.text = "Pending=${status.pendingItems}"
                                }
                                channel.send(SyncTriggerCommand(forceFullSync = true))
                            }
                        }
                    }
                }
            }
        }
    }
}
```

---

## 5. Related Documentation

- [`:extensions:ui:dsl` (`dsl.md`)](dsl.md) — Base declarative Kotlin UI & Style DSL.
- [GenericUI Root (`../../README.md`)](../../README.md) · [Extensions Architecture (`../../docs/extensions.md`)](../../docs/extensions.md) · [Application & Targets (`../../docs/application.md`)](../../docs/application.md) · [Gradle Plugin (`../../docs/gradle-plugin.md`)](../../docs/gradle-plugin.md)
- [Desktop Backends (`../../desktop/README.md`)](../../desktop/README.md) · [Mobile Backends (`../../mobile/README.md`)](../../mobile/README.md) · [Web Backend (`../../web/README.md`)](../../web/README.md)
