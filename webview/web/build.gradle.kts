plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    // Web targets — uses native HTML5 <iframe>.
    // DO NOT USE NJS ON WEB.
    js(IR) {
        browser()
    }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":common"))
            api(project(":web"))
            api(project(":extensions:webview:common"))
        }
    }
}
