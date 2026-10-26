plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    macosArm64()
    macosX64()

    sourceSets {
        commonMain.dependencies {
            api(project(":desktop:macos"))
            api(project(":extensions:media:desktop:nativeCommon"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}