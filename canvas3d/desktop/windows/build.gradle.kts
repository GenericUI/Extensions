plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    mingwX64()

    sourceSets {
        commonMain.dependencies {
            api(project(":desktop:windows"))
            api(project(":extensions:canvas3d:desktop:nativeCommon"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}