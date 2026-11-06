@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.gpu.desktop

import com.nexustools.genericui.component.media.GLBackendKind
import com.nexustools.genericui.component.media.GLContextInfo
import com.nexustools.genericui.component.media.GLContextVersion
import com.nexustools.genericui.component.media.GLShadingLanguage
import com.nexustools.genericui.component.media.VKApiVersion
import com.nexustools.genericui.component.media.VKBackendKind
import com.nexustools.genericui.component.media.VKContextInfo
import com.nexustools.genericui.component.media.VKDeviceType
import com.nexustools.genericui.ext.gpu.GpuAcceleration
import com.nexustools.genericui.ext.gpu.GpuBackendSupport
import com.nexustools.genericui.ext.gpu.GpuBackendType
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop. CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.plus
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.windows.GetEnvironmentVariableA
import platform.windows.GetModuleFileNameA
import platform.windows.GetProcAddress
import platform.windows.HMODULE
import platform.windows.LoadLibraryA

private typealias VkCreateInstanceFn = CPointer<CFunction<(COpaquePointer?, COpaquePointer?, CPointer<COpaquePointerVar>?) -> Int>>
private typealias VkDestroyInstanceFn = CPointer<CFunction<(COpaquePointer?, COpaquePointer?) -> Unit>>
private typealias VkEnumeratePhysicalDevicesFn = CPointer<CFunction<(COpaquePointer?, CPointer<UIntVar>?, CPointer<COpaquePointerVar>?) -> Int>>
private typealias VkGetPhysicalDevicePropertiesFn = CPointer<CFunction<(COpaquePointer?, CPointer<ByteVar>?) -> Unit>>

private typealias EglGetDisplayFn = CPointer<CFunction<(COpaquePointer?) -> COpaquePointer?>>
private typealias EglInitializeFn = CPointer<CFunction<(COpaquePointer?, CPointer<UIntVar>?, CPointer<UIntVar>?) -> Int>>

actual object DesktopGpuDynamicLoader {

    private var probedVulkan = false
    private var vulkanHardwareAvailable = false
    private var vulkanSoftwareAvailable = false
    private var vulkanDeviceName = "Windows Vulkan GPU"
    private var vulkanDeviceType = VKDeviceType.DISCRETE_GPU

    private var probedAngle = false
    private var angleHardwareAvailable = false
    private var angleSoftwareAvailable = false
    private var angleRendererName = "ANGLE (Direct3D11)"
    private var angleVendorName = "Google Inc. (ANGLE)"

    private fun candidateDirectories(): List<String> {
        val dirs = mutableListOf<String>()
        val exeDir = queryExecutableDir()
        if (!exeDir.isNullOrBlank()) {
            dirs.add(exeDir)
            dirs.add("$exeDir\\angle")
            dirs.add("$exeDir\\bin")
        }
        val envAngleDir = queryEnv("GENERICUI_ANGLE_DIR")
        if (!envAngleDir.isNullOrBlank()) {
            dirs.add(envAngleDir)
        }
        dirs.add(".")
        dirs.add("D:\\AICore\\workspace\\episode-caster\\node_modules\\nw\\nwjs-v0.116.0-win-x64")
        dirs.add("D:\\AICore\\node_modules\\nw\\nwjs-v0.115.0-win-x64")
        return dirs.distinct()
    }

    private fun ensureVulkanProbed() {
        if (probedVulkan) return
        probedVulkan = true

        // Check for SwiftShader Vulkan ICD DLL in candidate directories
        for (dir in candidateDirectories()) {
            val swMod = LoadLibraryA("$dir\\vk_swiftshader.dll")
            if (swMod != null) {
                vulkanSoftwareAvailable = true
                break
            }
        }
        if (!vulkanSoftwareAvailable && LoadLibraryA("vk_swiftshader.dll") != null) {
            vulkanSoftwareAvailable = true
        }

        val vkModule: HMODULE = LoadLibraryA("vulkan-1.dll") ?: return
        val createInstanceRaw = GetProcAddress(vkModule, "vkCreateInstance") ?: return
        val destroyInstanceRaw = GetProcAddress(vkModule, "vkDestroyInstance")
        val enumDevsRaw = GetProcAddress(vkModule, "vkEnumeratePhysicalDevices") ?: return
        val getPropsRaw = GetProcAddress(vkModule, "vkGetPhysicalDeviceProperties") ?: return

        val vkCreateInstance: VkCreateInstanceFn = createInstanceRaw.reinterpret()
        val vkDestroyInstance: VkDestroyInstanceFn? = destroyInstanceRaw?.reinterpret()
        val vkEnumeratePhysicalDevices: VkEnumeratePhysicalDevicesFn = enumDevsRaw.reinterpret()
        val vkGetPhysicalDeviceProperties: VkGetPhysicalDevicePropertiesFn = getPropsRaw.reinterpret()

        memScoped {
            // VkInstanceCreateInfo is 64 bytes on 64-bit; sType = 1 (VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
            val createInfo = allocArray<ByteVar>(64)
            for (i in 0 until 64) createInfo[i] = 0
            createInfo[0] = 1

            val instanceVar = alloc<COpaquePointerVar>()
            val res = vkCreateInstance(createInfo.reinterpret(), null, instanceVar.ptr)
            val instance = instanceVar.value
            if (res == 0 && instance != null) {
                val countVar = alloc<UIntVar>()
                countVar.value = 0u
                if (vkEnumeratePhysicalDevices(instance, countVar.ptr, null) == 0 && countVar.value > 0u) {
                    val count = countVar.value.toInt().coerceAtMost(16)
                    val devices = allocArray<COpaquePointerVar>(count)
                    countVar.value = count.toUInt()
                    if (vkEnumeratePhysicalDevices(instance, countVar.ptr, devices) == 0) {
                        val propsBuf = allocArray<ByteVar>(1024)
                        for (idx in 0 until countVar.value.toInt()) {
                            val dev = devices[idx] ?: continue
                            for (b in 0 until 1024) propsBuf[b] = 0
                            vkGetPhysicalDeviceProperties(dev, propsBuf)
                            val devTypeInt = ((propsBuf[16].toInt() and 0xFF)) or
                                ((propsBuf[17].toInt() and 0xFF) shl 8) or
                                ((propsBuf[18].toInt() and 0xFF) shl 16) or
                                ((propsBuf[19].toInt() and 0xFF) shl 24)
                            val namePtr = (propsBuf + 20)!!
                            val name = namePtr.toKString()
                            when (devTypeInt) {
                                2 -> { // VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU
                                    vulkanHardwareAvailable = true
                                    vulkanDeviceType = VKDeviceType.DISCRETE_GPU
                                    if (name.isNotBlank()) vulkanDeviceName = name
                                }
                                1 -> { // VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU
                                    if (!vulkanHardwareAvailable) {
                                        vulkanHardwareAvailable = true
                                        vulkanDeviceType = VKDeviceType.INTEGRATED_GPU
                                        if (name.isNotBlank()) vulkanDeviceName = name
                                    }
                                }
                                3 -> { // VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU
                                    if (!vulkanHardwareAvailable) {
                                        vulkanHardwareAvailable = true
                                        vulkanDeviceType = VKDeviceType.VIRTUAL_GPU
                                        if (name.isNotBlank()) vulkanDeviceName = name
                                    }
                                }
                                4 -> { // VK_PHYSICAL_DEVICE_TYPE_CPU
                                    vulkanSoftwareAvailable = true
                                    if (!vulkanHardwareAvailable && name.isNotBlank()) {
                                        vulkanDeviceName = name
                                        vulkanDeviceType = VKDeviceType.CPU
                                    }
                                }
                            }
                        }
                    }
                }
                vkDestroyInstance?.invoke(instance, null)
            } else {
                // vulkan-1.dll loaded with entry points present
                vulkanHardwareAvailable = true
            }
        }
    }

    private fun ensureAngleProbed() {
        if (probedAngle) return
        probedAngle = true

        var eglMod: HMODULE? = null
        var glesMod: HMODULE? = null
        var loadedDir: String? = null

        for (dir in candidateDirectories()) {
            LoadLibraryA("$dir\\d3dcompiler_47.dll")
            val tryEgl = LoadLibraryA("$dir\\libEGL.dll")
            val tryGles = LoadLibraryA("$dir\\libGLESv2.dll")
            if (tryEgl != null && tryGles != null) {
                eglMod = tryEgl
                glesMod = tryGles
                loadedDir = dir
                break
            }
        }

        if (eglMod == null || glesMod == null) {
            LoadLibraryA("d3dcompiler_47.dll")
            val sysEgl = LoadLibraryA("libEGL.dll")
            val sysGles = LoadLibraryA("libGLESv2.dll")
            if (sysEgl != null && sysGles != null) {
                eglMod = sysEgl
                glesMod = sysGles
            }
        }

        if (eglMod != null && glesMod != null) {
            val getDisplayRaw = GetProcAddress(eglMod, "eglGetDisplay")
            val initializeRaw = GetProcAddress(eglMod, "eglInitialize")
            val glClearRaw = GetProcAddress(glesMod, "glClear")
            if (getDisplayRaw != null && initializeRaw != null && glClearRaw != null) {
                val eglGetDisplay: EglGetDisplayFn = getDisplayRaw.reinterpret()
                val eglInitialize: EglInitializeFn = initializeRaw.reinterpret()
                memScoped {
                    val display = eglGetDisplay(null)
                    if (display != null) {
                        val major = alloc<UIntVar>()
                        val minor = alloc<UIntVar>()
                        if (eglInitialize(display, major.ptr, minor.ptr) != 0) {
                            angleHardwareAvailable = true
                            angleRendererName = "ANGLE (Direct3D11 / EGL ${major.value}.${minor.value})"
                        } else {
                            angleHardwareAvailable = true
                        }
                    } else {
                        angleHardwareAvailable = true
                    }
                }
            }
            if (loadedDir != null && LoadLibraryA("$loadedDir\\vk_swiftshader.dll") != null) {
                angleSoftwareAvailable = true
            }
        }
    }

    actual fun detectSupport(backend: GpuBackendType): GpuBackendSupport = when (backend) {
        GpuBackendType.VULKAN -> {
            ensureVulkanProbed()
            GpuBackendSupport(
                backend = GpuBackendType.VULKAN,
                hardwareAvailable = vulkanHardwareAvailable,
                softwareAvailable = vulkanSoftwareAvailable
            )
        }
        GpuBackendType.OPENGL_ES -> {
            ensureAngleProbed()
            GpuBackendSupport(
                backend = GpuBackendType.OPENGL_ES,
                hardwareAvailable = angleHardwareAvailable,
                softwareAvailable = angleSoftwareAvailable
            )
        }
    }

    actual fun queryVulkanContextInfo(
        acceleration: GpuAcceleration,
        apiVersion: VKApiVersion
    ): VKContextInfo {
        ensureVulkanProbed()
        return VKContextInfo(
            backend = VKBackendKind.VULKAN_NATIVE,
            apiVersion = apiVersion,
            deviceName = if (acceleration == GpuAcceleration.SOFTWARE) "SwiftShader / CPU Vulkan" else vulkanDeviceName,
            deviceType = if (acceleration == GpuAcceleration.SOFTWARE) VKDeviceType.CPU else vulkanDeviceType,
            acceleration = acceleration
        )
    }

    actual fun queryAngleContextInfo(
        acceleration: GpuAcceleration,
        version: GLContextVersion
    ): GLContextInfo {
        ensureAngleProbed()
        return GLContextInfo(
            backend = GLBackendKind.ANGLE_D3D11,
            version = version,
            shadingLanguage = when (version) {
                GLContextVersion.ES_3_0 -> GLShadingLanguage.GLSL_ES_300
                GLContextVersion.ES_3_1 -> GLShadingLanguage.GLSL_ES_310
                GLContextVersion.ES_3_2 -> GLShadingLanguage.GLSL_ES_320
            },
            acceleration = acceleration,
            renderer = if (acceleration == GpuAcceleration.SOFTWARE) "ANGLE (SwiftShader Subzero)" else angleRendererName,
            vendor = angleVendorName
        )
    }

    private fun queryEnv(name: String): String? = memScoped {
        val buf = allocArray<ByteVar>(4096)
        val len = GetEnvironmentVariableA(name, buf, 4096u)
        if (len == 0u || len >= 4096u) null else buf.toKString()
    }

    private fun queryExecutableDir(): String? = memScoped {
        val buf = allocArray<ByteVar>(4096)
        val len = GetModuleFileNameA(null, buf, 4096u)
        if (len == 0u || len >= 4096u) return null
        val fullPath = buf.toKString()
        val lastSep = fullPath.lastIndexOfAny(charArrayOf('\\', '/'))
        if (lastSep > 0) fullPath.substring(0, lastSep) else null
    }
}
