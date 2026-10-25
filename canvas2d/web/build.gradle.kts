plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    js {
        browser()
    }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":web"))
            api(project(":extensions:gpu:web"))
            api(project(":extensions:imageprocessing:common"))
            api(project(":extensions:imageprocessing:web"))
            api(project(":extensions:canvas2d:common"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
