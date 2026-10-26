plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    mingwX64()

    sourceSets {
        commonMain.dependencies {
            api(project(":desktop:windows"))
            api(project(":extensions:media:desktop:nativeCommon"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}