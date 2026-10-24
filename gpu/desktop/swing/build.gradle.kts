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
    api(project(":extensions:gpu:common"))
    implementation(libs.kotlinx.coroutinesSwing)
    compileOnly(libs.lwjgl.core)
    compileOnly(libs.lwjgl.jawt)
    compileOnly(libs.lwjgl.egl)
    compileOnly(libs.lwjgl.opengles)
    compileOnly(libs.lwjgl.opengl)
    compileOnly(libs.lwjgl.vulkan)
    compileOnly(libs.lwjgl.shaderc)
    compileOnly(libs.lwjgl.vma)
    testImplementation(libs.kotlin.test)
}
