import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    api(project(":desktop:swing"))
    api(project(":extensions:gpu:desktop:swing"))
    api(project(":extensions:imageprocessing:systemCommon"))
    api(project(":extensions:canvas2d:common"))
    api(project(":extensions:canvas2d:systemCommon"))
    implementation(libs.kotlinx.coroutinesSwing)

    testImplementation(libs.kotlin.test)
}
