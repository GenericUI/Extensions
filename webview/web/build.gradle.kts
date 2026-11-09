import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    // Web targets — uses native HTML5 <iframe>.
    // DO NOT USE NJS ON WEB.
    js {
        browser()
        nodejs()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":common"))
            api(project(":web"))
            api(project(":extensions:webview:common"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
