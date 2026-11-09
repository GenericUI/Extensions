plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    applyDefaultHierarchyTemplate()

    // Desktop Native targets ONLY — uses Chromium + NJS (:common + :native AsmJit/SLJIT JIT)
    val desktopNativeTargets = listOf(
        mingwX64(),
        linuxX64(),
        linuxArm64(),
        macosArm64()
    )

    desktopNativeTargets.forEach { target ->
        target.binaries {
            executable("webviewProcess") {
                baseName = "genericui-webview-process"
                entryPoint = "com.nexustools.genericui.ext.webview.desktop.process.main"
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":common"))
            api(project(":desktop:nativeCommon"))
            api(project(":extensions:webview:common"))
            api(project(":extensions:webview:systemCommon"))
            api(project(":extensions:webview:desktop:common"))
            // NJS :common & :native subprojects (symlinked at lib/NJS; Desktop builds ONLY)
            api("com.nexustools.njs:common:1.0")
            api("com.nexustools.njs:native:1.0")
        }
    }
}
