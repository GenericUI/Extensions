plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    applyDefaultHierarchyTemplate()

    // Desktop Native targets ONLY — uses Chromium + NJS (:common + :native AsmJit/SLJIT JIT)
    mingwX64()
    linuxX64()
    linuxArm64()
    macosArm64()
    macosX64()

    sourceSets {
        commonMain.dependencies {
            api(project(":common"))
            api(project(":desktop:shared"))
            api(project(":extensions:webview:common"))
            // NJS :common & :native subprojects (symlinked at lib/NJS; Desktop builds ONLY)
            api("com.nexustools.njs:common:1.0")
            api("com.nexustools.njs:native:1.0")
        }
    }
}
