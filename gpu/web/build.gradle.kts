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
            api(project(":extensions:gpu:common"))
            implementation(libs.kotlinx.coroutines.core)

        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
