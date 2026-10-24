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
            api(project(":extensions:media:common"))
            implementation(libs.kotlinx.coroutines.core)
            api(project(":extensions:gpu:web"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
