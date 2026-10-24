plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    js {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":web"))
            api(project(":extensions:canvas3d:common"))
            implementation(libs.kotlinx.coroutines.core)
            api(project(":extensions:gpu:web"))
            api(project(":extensions:canvas2d:web"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
