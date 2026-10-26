plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    mingwX64()

    sourceSets {
        commonMain.dependencies {
            api(project(":common"))
            api(project(":extensions:mediacontrols:desktop:nativeCommon"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
