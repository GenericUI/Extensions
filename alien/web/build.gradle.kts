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
            api(project(":extensions:canvas2d:common"))
            api(project(":extensions:canvas2d:web"))
            api(project(":extensions:alien:common"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
