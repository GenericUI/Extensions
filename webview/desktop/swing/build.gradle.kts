import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":common"))
    api(project(":desktop:swing"))
    api(project(":extensions:webview:common"))
    api(project(":extensions:webview:systemCommon"))
    api(project(":extensions:webview:desktop:common"))
    // NJS :common & :jvm subprojects (symlinked at lib/NJS; Desktop builds ONLY)
    api("com.nexustools.njs:common:1.0")
    api("com.nexustools.njs:jvm:1.0")
}
