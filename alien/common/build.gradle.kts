import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    android {
        namespace = "com.nexustools.genericui.ext.alien"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    js { browser() }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    mingwX64()
    linuxX64()
    linuxArm64()
    macosArm64()
    macosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":common"))
            api(project(":extensions:imageprocessing:common"))
            api(project(":extensions:canvas2d:common"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

val syncPrecompileAlienBootstrapScss by tasks.registering {
    val scssFile = layout.projectDirectory.file("src/commonMain/resources/styles/bootstrap.scss")
    val targetKtFile = layout.projectDirectory.file(
        "src/commonMain/kotlin/com/nexustools/genericui/ext/alien/AlienPrecompiledBootstrapStyleSheet.kt"
    )
    inputs.file(scssFile)
    outputs.file(targetKtFile)
    doLast {
        val scssSource = scssFile.asFile.readText(Charsets.UTF_8).replace("\r\n", "\n").trim()
        val escapedScss = scssSource.replace("$", "\${'$'}")
        val currentKt = targetKtFile.asFile.readText(Charsets.UTF_8).replace("\r\n", "\n")
        val beginMarker = "// BEGIN_PRECOMPILED_BOOTSTRAP_SCSS"
        val endMarker = "// END_PRECOMPILED_BOOTSTRAP_SCSS"
        val beginIdx = currentKt.indexOf(beginMarker)
        val endIdx = currentKt.indexOf(endMarker)
        if (beginIdx >= 0 && endIdx > beginIdx) {
            val replacementBlock = buildString {
                append(beginMarker).append("\n")
                append("    const val BOOTSTRAP_SCSS_SOURCE: String = \"\"\"\n")
                append(escapedScss).append("\n")
                append("\"\"\"\n")
                append("    ").append(endMarker)
            }
            val updatedKt = currentKt.substring(0, beginIdx) +
                replacementBlock +
                currentKt.substring(endIdx + endMarker.length)
            if (updatedKt != currentKt) {
                targetKtFile.asFile.writeText(updatedKt, Charsets.UTF_8)
            }
        }
    }
}

tasks.matching { it.name.startsWith("compile") && it.name.contains("Kotlin") }.configureEach {
    dependsOn(syncPrecompileAlienBootstrapScss)
}
