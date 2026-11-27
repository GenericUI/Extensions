package com.nexustools.genericui.ext.ipc

import java.lang.invoke.MethodHandle
import java.lang.reflect.Array as ReflectArray
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.Optional

/**
 * Reflection-backed JDK 22+ `java.lang.foreign` (FFM) bridge to Win32 Named Pipe (`kernel32.dll`)
 * and user-SID DACL (`advapi32.dll`) APIs while compiling cleanly under `JVM_11`.
 */
internal object JvmWindowsNamedPipeBridge {

    const val PIPE_ACCESS_DUPLEX: Int = 0x00000003
    const val FILE_FLAG_OVERLAPPED: Int = 0x40000000
    const val FILE_FLAG_FIRST_PIPE_INSTANCE: Int = 0x00080000
    const val PIPE_TYPE_BYTE: Int = 0x00000000
    const val PIPE_READMODE_BYTE: Int = 0x00000000
    const val PIPE_WAIT: Int = 0x00000000
    const val PIPE_REJECT_REMOTE_CLIENTS: Int = 0x00000008
    const val PIPE_UNLIMITED_INSTANCES: Int = 255

    const val GENERIC_READ: Int = 0x80000000.toInt()
    const val GENERIC_WRITE: Int = 0x40000000
    const val OPEN_EXISTING: Int = 3
    const val SECURITY_SQOS_PRESENT: Int = 0x00100000
    const val SECURITY_IDENTIFICATION: Int = 0x00010000

    const val ERROR_IO_PENDING: Int = 997
    const val ERROR_PIPE_BUSY: Int = 231
    const val ERROR_BROKEN_PIPE: Int = 109
    const val ERROR_PIPE_NOT_CONNECTED: Int = 233
    const val ERROR_PIPE_CONNECTED: Int = 535
    const val ERROR_INVALID_HANDLE: Int = 6

    const val WAIT_OBJECT_0: Int = 0
    const val WAIT_TIMEOUT: Int = 258
    const val INFINITE: Int = -1

    private const val TOKEN_QUERY: Int = 0x0008
    private const val TOKEN_USER_INFO_CLASS: Int = 1
    private const val SECURITY_DESCRIPTOR_REVISION: Int = 1
    private const val ACL_REVISION: Int = 2
    private const val FILE_ALL_ACCESS: Int = 0x001F01FF

    private class BridgeImpl(
        val ptrSize: Long,
        val overlappedSize: Long,
        val overlappedEventOffset: Long,
        val captureLayoutSize: Long,
        val getLastErrorOffset: Long,
        val nullSegment: Any,
        val arenaOfSharedMethod: Method,
        val arenaAllocateMethod: Method,
        val arenaCloseMethod: Method,
        val segAddressMethod: Method,
        val segFillMethod: Method,
        val segGetIntMethod: Method,
        val segSetIntMethod: Method,
        val segSetShortMethod: Method,
        val segGetAddrMethod: Method,
        val segSetAddrMethod: Method,
        val segCopyToNativeMethod: Method,
        val segCopyFromNativeMethod: Method,
        val javaByteLayout: Any,
        val javaShortLayout: Any,
        val javaIntLayout: Any,
        val addressLayout: Any,
        val createNamedPipeW: MethodHandle,
        val connectNamedPipe: MethodHandle,
        val disconnectNamedPipe: MethodHandle,
        val createFileW: MethodHandle,
        val waitNamedPipeW: MethodHandle,
        val readFile: MethodHandle,
        val writeFile: MethodHandle,
        val createEventW: MethodHandle,
        val setEvent: MethodHandle,
        val resetEvent: MethodHandle,
        val waitForMultipleObjects: MethodHandle,
        val waitForSingleObject: MethodHandle,
        val getOverlappedResult: MethodHandle,
        val cancelIoEx: MethodHandle,
        val closeHandle: MethodHandle,
        val getCurrentProcess: MethodHandle,
        val openProcessToken: MethodHandle,
        val getTokenInformation: MethodHandle,
        val getLengthSid: MethodHandle,
        val initializeSecurityDescriptor: MethodHandle,
        val initializeAcl: MethodHandle,
        val addAccessAllowedAce: MethodHandle,
        val setSecurityDescriptorOwner: MethodHandle,
        val setSecurityDescriptorDacl: MethodHandle
    )

    private val implOrNull: BridgeImpl? by lazy { initializeBridgeOrNull() }

    fun isSupported(): Boolean =
        isWindowsOs() && implOrNull != null

    fun openSharedArena(): Any = requireImpl().arenaOfSharedMethod.invoke(null)

    fun closeArena(arena: Any) {
        requireImpl().arenaCloseMethod.invoke(arena)
    }

    fun allocate(arena: Any, byteSize: Long, alignment: Long = 8L): Any =
        requireImpl().arenaAllocateMethod.invoke(arena, byteSize, alignment)

    fun allocateZeroed(arena: Any, byteSize: Long, alignment: Long = 8L): Any {
        val seg = allocate(arena, byteSize, alignment)
        requireImpl().segFillMethod.invoke(seg, 0.toByte())
        return seg
    }

    fun allocateCaptureState(arena: Any): Any =
        allocateZeroed(arena, requireImpl().captureLayoutSize, 8L)

    fun allocateOverlapped(arena: Any): Any =
        allocateZeroed(arena, requireImpl().overlappedSize, requireImpl().ptrSize)

    fun zeroOverlappedWithEvent(overlapped: Any, eventHandle: Any) {
        val b = requireImpl()
        b.segFillMethod.invoke(overlapped, 0.toByte())
        b.segSetAddrMethod.invoke(overlapped, b.addressLayout, b.overlappedEventOffset, eventHandle)
    }

    fun allocateHandlePair(arena: Any, first: Any, second: Any): Any {
        val b = requireImpl()
        val pair = allocate(arena, b.ptrSize * 2L, b.ptrSize)
        b.segSetAddrMethod.invoke(pair, b.addressLayout, 0L, first)
        b.segSetAddrMethod.invoke(pair, b.addressLayout, b.ptrSize, second)
        return pair
    }

    fun allocateUtf16String(arena: Any, text: String): Any {
        val b = requireImpl()
        val byteLen = (text.length + 1).toLong() * 2L
        val seg = allocate(arena, byteLen, 2L)
        for (i in text.indices) {
            b.segSetShortMethod.invoke(seg, b.javaShortLayout, i.toLong() * 2L, text[i].code.toShort())
        }
        b.segSetShortMethod.invoke(seg, b.javaShortLayout, text.length.toLong() * 2L, 0.toShort())
        return seg
    }

    fun copyToNative(src: ByteArray, srcOffset: Int, dstSegment: Any, length: Int) {
        val b = requireImpl()
        b.segCopyToNativeMethod.invoke(null, src, srcOffset, dstSegment, b.javaByteLayout, 0L, length)
    }

    fun copyFromNative(srcSegment: Any, dst: ByteArray, dstOffset: Int, length: Int) {
        val b = requireImpl()
        b.segCopyFromNativeMethod.invoke(null, srcSegment, b.javaByteLayout, 0L, dst, dstOffset, length)
    }

    fun readInt(segment: Any, offset: Long = 0L): Int {
        val b = requireImpl()
        return b.segGetIntMethod.invoke(segment, b.javaIntLayout, offset) as Int
    }

    fun writeInt(segment: Any, offset: Long = 0L, value: Int) {
        val b = requireImpl()
        b.segSetIntMethod.invoke(segment, b.javaIntLayout, offset, value)
    }

    fun readCapturedLastError(captureState: Any): Int {
        val b = requireImpl()
        return b.segGetIntMethod.invoke(captureState, b.javaIntLayout, b.getLastErrorOffset) as Int
    }

    fun isValidHandle(handle: Any?): Boolean {
        if (handle == null) return false
        val b = requireImpl()
        val addr = b.segAddressMethod.invoke(handle) as Long
        return addr != 0L && addr != -1L && !(b.ptrSize == 4L && addr == 0xFFFFFFFFL)
    }

    fun createCurrentUserSecurityAttributes(arena: Any): Any {
        val b = requireImpl()
        val capture = allocateCaptureState(arena)
        val tokenHandleOut = allocateZeroed(arena, b.ptrSize, b.ptrSize)
        val proc = b.getCurrentProcess.invokeWithArguments()
        val openOk = b.openProcessToken.invokeWithArguments(capture, proc, TOKEN_QUERY, tokenHandleOut) as Int
        if (openOk == 0) {
            return b.nullSegment
        }
        val hToken = b.segGetAddrMethod.invoke(tokenHandleOut, b.addressLayout, 0L)
        if (!isValidHandle(hToken)) {
            return b.nullSegment
        }
        try {
            val neededOut = allocateZeroed(arena, 4L, 4L)
            b.getTokenInformation.invokeWithArguments(
                capture,
                hToken,
                TOKEN_USER_INFO_CLASS,
                b.nullSegment,
                0,
                neededOut
            )
            val needed = readInt(neededOut)
            if (needed <= 0) {
                return b.nullSegment
            }
            val tokenBuf = allocateZeroed(arena, needed.toLong(), 8L)
            val infoOk = b.getTokenInformation.invokeWithArguments(
                capture,
                hToken,
                TOKEN_USER_INFO_CLASS,
                tokenBuf,
                needed,
                neededOut
            ) as Int
            if (infoOk == 0) {
                return b.nullSegment
            }
            val pSid = b.segGetAddrMethod.invoke(tokenBuf, b.addressLayout, 0L)
            val sidLen = b.getLengthSid.invokeWithArguments(pSid) as Int
            if (sidLen <= 0) {
                return b.nullSegment
            }
            val sdBuf = allocateZeroed(arena, 256L, 8L)
            val aclSize = ((8 + 12 + sidLen + 16) + 3) and 3.inv()
            val aclBuf = allocateZeroed(arena, aclSize.toLong(), 8L)

            if ((b.initializeSecurityDescriptor.invokeWithArguments(sdBuf, SECURITY_DESCRIPTOR_REVISION) as Int) == 0 ||
                (b.initializeAcl.invokeWithArguments(aclBuf, aclSize, ACL_REVISION) as Int) == 0 ||
                (b.addAccessAllowedAce.invokeWithArguments(aclBuf, ACL_REVISION, FILE_ALL_ACCESS, pSid) as Int) == 0 ||
                (b.setSecurityDescriptorOwner.invokeWithArguments(sdBuf, pSid, 0) as Int) == 0 ||
                (b.setSecurityDescriptorDacl.invokeWithArguments(sdBuf, 1, aclBuf, 0) as Int) == 0
            ) {
                return b.nullSegment
            }

            val saSize = if (b.ptrSize == 8L) 24L else 12L
            val sa = allocateZeroed(arena, saSize, b.ptrSize)
            writeInt(sa, 0L, saSize.toInt())
            b.segSetAddrMethod.invoke(sa, b.addressLayout, b.ptrSize, sdBuf)
            writeInt(sa, b.ptrSize * 2L, 0)
            return sa
        } finally {
            b.closeHandle.invokeWithArguments(hToken)
        }
    }

    fun createNamedPipeW(
        capture: Any,
        pipeNameSeg: Any,
        openMode: Int,
        pipeMode: Int,
        maxInstances: Int,
        outBufferSize: Int,
        inBufferSize: Int,
        defaultTimeout: Int,
        securityAttrs: Any
    ): Any = requireImpl().createNamedPipeW.invokeWithArguments(
        capture,
        pipeNameSeg,
        openMode,
        pipeMode,
        maxInstances,
        outBufferSize,
        inBufferSize,
        defaultTimeout,
        securityAttrs
    )

    fun connectNamedPipe(capture: Any, hPipe: Any, overlapped: Any): Int =
        requireImpl().connectNamedPipe.invokeWithArguments(capture, hPipe, overlapped) as Int

    fun disconnectNamedPipe(hPipe: Any): Int =
        requireImpl().disconnectNamedPipe.invokeWithArguments(hPipe) as Int

    fun createFileW(
        capture: Any,
        fileNameSeg: Any,
        desiredAccess: Int,
        shareMode: Int,
        securityAttrs: Any,
        creationDisposition: Int,
        flagsAndAttrs: Int,
        templateFile: Any = requireImpl().nullSegment
    ): Any = requireImpl().createFileW.invokeWithArguments(
        capture,
        fileNameSeg,
        desiredAccess,
        shareMode,
        securityAttrs,
        creationDisposition,
        flagsAndAttrs,
        templateFile
    )

    fun waitNamedPipeW(capture: Any, pipeNameSeg: Any, timeoutMs: Int): Int =
        requireImpl().waitNamedPipeW.invokeWithArguments(capture, pipeNameSeg, timeoutMs) as Int

    fun readFile(
        capture: Any,
        hFile: Any,
        bufferSeg: Any,
        bytesToRead: Int,
        bytesReadOut: Any,
        overlapped: Any
    ): Int = requireImpl().readFile.invokeWithArguments(
        capture,
        hFile,
        bufferSeg,
        bytesToRead,
        bytesReadOut,
        overlapped
    ) as Int

    fun writeFile(
        capture: Any,
        hFile: Any,
        bufferSeg: Any,
        bytesToWrite: Int,
        bytesWrittenOut: Any,
        overlapped: Any
    ): Int = requireImpl().writeFile.invokeWithArguments(
        capture,
        hFile,
        bufferSeg,
        bytesToWrite,
        bytesWrittenOut,
        overlapped
    ) as Int

    fun createManualResetEvent(): Any? {
        val b = requireImpl()
        val handle = b.createEventW.invokeWithArguments(b.nullSegment, 1, 0, b.nullSegment)
        return if (isValidHandle(handle)) handle else null
    }

    fun setEvent(hEvent: Any): Int =
        requireImpl().setEvent.invokeWithArguments(hEvent) as Int

    fun resetEvent(hEvent: Any): Int =
        requireImpl().resetEvent.invokeWithArguments(hEvent) as Int

    fun waitForMultipleObjects(capture: Any, count: Int, handlesSeg: Any, waitAll: Int, timeoutMs: Int): Int =
        requireImpl().waitForMultipleObjects.invokeWithArguments(capture, count, handlesSeg, waitAll, timeoutMs) as Int

    fun waitForSingleObject(hHandle: Any, timeoutMs: Int): Int =
        requireImpl().waitForSingleObject.invokeWithArguments(hHandle, timeoutMs) as Int

    fun getOverlappedResult(
        capture: Any,
        hFile: Any,
        overlapped: Any,
        bytesTransferredOut: Any,
        wait: Int
    ): Int = requireImpl().getOverlappedResult.invokeWithArguments(
        capture,
        hFile,
        overlapped,
        bytesTransferredOut,
        wait
    ) as Int

    fun cancelIoEx(hFile: Any, overlappedOrNull: Any? = null): Int {
        val b = requireImpl()
        return b.cancelIoEx.invokeWithArguments(hFile, overlappedOrNull ?: b.nullSegment) as Int
    }

    fun closeHandle(hObject: Any): Int =
        requireImpl().closeHandle.invokeWithArguments(hObject) as Int

    fun nullSegment(): Any = requireImpl().nullSegment

    fun connect(endpointAddress: String, timeoutMs: Int, config: IpcConfig): IpcConnection? {
        if (!isSupported() || endpointAddress.isBlank()) return null
        val arena = openSharedArena()
        try {
            val capture = allocateCaptureState(arena)
            val pipeNameSeg = allocateUtf16String(arena, endpointAddress)
            val effectiveTimeoutMs = timeoutMs.coerceAtLeast(1).toLong()
            val deadlineNanos = System.nanoTime() + effectiveTimeoutMs * 1_000_000L
            val access = GENERIC_READ or GENERIC_WRITE
            val flags = FILE_FLAG_OVERLAPPED or SECURITY_SQOS_PRESENT or SECURITY_IDENTIFICATION
            val nullSeg = nullSegment()

            while (true) {
                val handle = createFileW(
                    capture = capture,
                    fileNameSeg = pipeNameSeg,
                    desiredAccess = access,
                    shareMode = 0,
                    securityAttrs = nullSeg,
                    creationDisposition = OPEN_EXISTING,
                    flagsAndAttrs = flags
                )
                if (isValidHandle(handle)) {
                    return JvmWin32NamedPipeConnection(handle, config.maxPacketBytes)
                }
                val err = readCapturedLastError(capture)
                if (err != ERROR_PIPE_BUSY) {
                    return null
                }
                val remainingMs = (deadlineNanos - System.nanoTime()) / 1_000_000L
                if (remainingMs <= 0L) {
                    return null
                }
                waitNamedPipeW(
                    capture = capture,
                    pipeNameSeg = pipeNameSeg,
                    timeoutMs = remainingMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                )
            }
        } finally {
            closeArena(arena)
        }
    }

    private fun requireImpl(): BridgeImpl =
        implOrNull ?: throw UnsupportedOperationException(
            "Win32 Named Pipe FFM bridge is unavailable on this JVM runtime."
        )

    private fun isWindowsOs(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("win")

    private fun initializeBridgeOrNull(): BridgeImpl? {
        if (!isWindowsOs()) return null
        return try {
            val linkerClass = Class.forName("java.lang.foreign.Linker")
            val optionClass = Class.forName("java.lang.foreign.Linker\$Option")
            val arenaClass = Class.forName("java.lang.foreign.Arena")
            val lookupClass = Class.forName("java.lang.foreign.SymbolLookup")
            val fdClass = Class.forName("java.lang.foreign.FunctionDescriptor")
            val memLayoutClass = Class.forName("java.lang.foreign.MemoryLayout")
            val pathElementClass = Class.forName("java.lang.foreign.MemoryLayout\$PathElement")
            val valueLayoutClass = Class.forName("java.lang.foreign.ValueLayout")
            val ofByteClass = Class.forName("java.lang.foreign.ValueLayout\$OfByte")
            val ofShortClass = Class.forName("java.lang.foreign.ValueLayout\$OfShort")
            val ofIntClass = Class.forName("java.lang.foreign.ValueLayout\$OfInt")
            val addrLayoutClass = Class.forName("java.lang.foreign.AddressLayout")
            val segClass = Class.forName("java.lang.foreign.MemorySegment")

            val nativeLinker = linkerClass.getMethod("nativeLinker").invoke(null)
            val arenaOfAutoMethod = arenaClass.getMethod("ofAuto")
            val arenaOfSharedMethod = arenaClass.getMethod("ofShared")
            val arenaAllocateMethod = arenaClass.getMethod("allocate", Long::class.javaPrimitiveType, Long::class.javaPrimitiveType)
            val arenaCloseMethod = arenaClass.getMethod("close")

            val globalArena = arenaOfAutoMethod.invoke(null)
            val libraryLookupMethod = lookupClass.getMethod("libraryLookup", String::class.java, arenaClass)
            val findMethod = lookupClass.getMethod("find", String::class.java)

            val kernel32 = libraryLookupMethod.invoke(null, "kernel32", globalArena)
            val advapi32 = libraryLookupMethod.invoke(null, "advapi32", globalArena)

            val captureCallStateMethod = optionClass.getMethod("captureCallState", Array<String>::class.java)
            val captureStateLayoutMethod = optionClass.getMethod("captureStateLayout")
            val captureOpt = captureCallStateMethod.invoke(null, arrayOf("GetLastError"))
            val captureLayout = captureStateLayoutMethod.invoke(null)
            val captureLayoutSize = memLayoutClass.getMethod("byteSize").invoke(captureLayout) as Long

            val groupElementMethod = pathElementClass.getMethod("groupElement", String::class.java)
            val pathElemArray = ReflectArray.newInstance(pathElementClass, 1)
            ReflectArray.set(pathElemArray, 0, groupElementMethod.invoke(null, "GetLastError"))
            val getLastErrorOffset = memLayoutClass.getMethod("byteOffset", pathElemArray.javaClass)
                .invoke(captureLayout, pathElemArray) as Long

            val javaByte = valueLayoutClass.getField("JAVA_BYTE").get(null)
            val javaShort = valueLayoutClass.getField("JAVA_SHORT").get(null)
            val javaInt = valueLayoutClass.getField("JAVA_INT").get(null)
            val addressLayout = valueLayoutClass.getField("ADDRESS").get(null)
            val ptrSize = memLayoutClass.getMethod("byteSize").invoke(addressLayout) as Long

            val layoutArrayType = ReflectArray.newInstance(memLayoutClass, 0).javaClass
            val optionArrayType = ReflectArray.newInstance(optionClass, 0).javaClass
            val fdOfMethod = fdClass.getMethod("of", memLayoutClass, layoutArrayType)
            val downcallMethod = linkerClass.getMethod("downcallHandle", segClass, fdClass, optionArrayType)

            val noOptions = ReflectArray.newInstance(optionClass, 0)
            val captureOptions = ReflectArray.newInstance(optionClass, 1)
            ReflectArray.set(captureOptions, 0, captureOpt)

            fun downcall(lookup: Any, name: String, ret: Any, capture: Boolean, vararg args: Any): MethodHandle {
                @Suppress("UNCHECKED_CAST")
                val opt = findMethod.invoke(lookup, name) as Optional<Any>
                val symbol = opt.orElseThrow { NoSuchMethodException("Missing Win32 symbol: $name") }
                val argArray = ReflectArray.newInstance(memLayoutClass, args.size)
                for (i in args.indices) {
                    ReflectArray.set(argArray, i, args[i])
                }
                val descriptor = fdOfMethod.invoke(null, ret, argArray)
                val opts = if (capture) captureOptions else noOptions
                return downcallMethod.invoke(nativeLinker, symbol, descriptor, opts) as MethodHandle
            }

            val nullSeg = segClass.getField("NULL").get(null)
            val segAddressMethod = segClass.getMethod("address")
            val segFillMethod = segClass.getMethod("fill", Byte::class.javaPrimitiveType)
            val segGetIntMethod = segClass.getMethod("get", ofIntClass, Long::class.javaPrimitiveType)
            val segSetIntMethod = segClass.getMethod("set", ofIntClass, Long::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            val segSetShortMethod = segClass.getMethod("set", ofShortClass, Long::class.javaPrimitiveType, Short::class.javaPrimitiveType)
            val segGetAddrMethod = segClass.getMethod("get", addrLayoutClass, Long::class.javaPrimitiveType)
            val segSetAddrMethod = segClass.getMethod("set", addrLayoutClass, Long::class.javaPrimitiveType, segClass)
            val segCopyToNativeMethod = segClass.getMethod(
                "copy",
                Any::class.java,
                Int::class.javaPrimitiveType,
                segClass,
                valueLayoutClass,
                Long::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            val segCopyFromNativeMethod = segClass.getMethod(
                "copy",
                segClass,
                valueLayoutClass,
                Long::class.javaPrimitiveType,
                Any::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )

            BridgeImpl(
                ptrSize = ptrSize,
                overlappedSize = 3L * ptrSize + 8L,
                overlappedEventOffset = 2L * ptrSize + 8L,
                captureLayoutSize = captureLayoutSize,
                getLastErrorOffset = getLastErrorOffset,
                nullSegment = nullSeg,
                arenaOfSharedMethod = arenaOfSharedMethod,
                arenaAllocateMethod = arenaAllocateMethod,
                arenaCloseMethod = arenaCloseMethod,
                segAddressMethod = segAddressMethod,
                segFillMethod = segFillMethod,
                segGetIntMethod = segGetIntMethod,
                segSetIntMethod = segSetIntMethod,
                segSetShortMethod = segSetShortMethod,
                segGetAddrMethod = segGetAddrMethod,
                segSetAddrMethod = segSetAddrMethod,
                segCopyToNativeMethod = segCopyToNativeMethod,
                segCopyFromNativeMethod = segCopyFromNativeMethod,
                javaByteLayout = javaByte,
                javaShortLayout = javaShort,
                javaIntLayout = javaInt,
                addressLayout = addressLayout,
                createNamedPipeW = downcall(
                    kernel32, "CreateNamedPipeW", addressLayout, true,
                    addressLayout, javaInt, javaInt, javaInt, javaInt, javaInt, javaInt, addressLayout
                ),
                connectNamedPipe = downcall(
                    kernel32, "ConnectNamedPipe", javaInt, true,
                    addressLayout, addressLayout
                ),
                disconnectNamedPipe = downcall(
                    kernel32, "DisconnectNamedPipe", javaInt, false,
                    addressLayout
                ),
                createFileW = downcall(
                    kernel32, "CreateFileW", addressLayout, true,
                    addressLayout, javaInt, javaInt, addressLayout, javaInt, javaInt, addressLayout
                ),
                waitNamedPipeW = downcall(
                    kernel32, "WaitNamedPipeW", javaInt, true,
                    addressLayout, javaInt
                ),
                readFile = downcall(
                    kernel32, "ReadFile", javaInt, true,
                    addressLayout, addressLayout, javaInt, addressLayout, addressLayout
                ),
                writeFile = downcall(
                    kernel32, "WriteFile", javaInt, true,
                    addressLayout, addressLayout, javaInt, addressLayout, addressLayout
                ),
                createEventW = downcall(
                    kernel32, "CreateEventW", addressLayout, false,
                    addressLayout, javaInt, javaInt, addressLayout
                ),
                setEvent = downcall(
                    kernel32, "SetEvent", javaInt, false,
                    addressLayout
                ),
                resetEvent = downcall(
                    kernel32, "ResetEvent", javaInt, false,
                    addressLayout
                ),
                waitForMultipleObjects = downcall(
                    kernel32, "WaitForMultipleObjects", javaInt, true,
                    javaInt, addressLayout, javaInt, javaInt
                ),
                waitForSingleObject = downcall(
                    kernel32, "WaitForSingleObject", javaInt, false,
                    addressLayout, javaInt
                ),
                getOverlappedResult = downcall(
                    kernel32, "GetOverlappedResult", javaInt, true,
                    addressLayout, addressLayout, addressLayout, javaInt
                ),
                cancelIoEx = downcall(
                    kernel32, "CancelIoEx", javaInt, false,
                    addressLayout, addressLayout
                ),
                closeHandle = downcall(
                    kernel32, "CloseHandle", javaInt, false,
                    addressLayout
                ),
                getCurrentProcess = downcall(
                    kernel32, "GetCurrentProcess", addressLayout, false
                ),
                openProcessToken = downcall(
                    advapi32, "OpenProcessToken", javaInt, true,
                    addressLayout, javaInt, addressLayout
                ),
                getTokenInformation = downcall(
                    advapi32, "GetTokenInformation", javaInt, true,
                    addressLayout, javaInt, addressLayout, javaInt, addressLayout
                ),
                getLengthSid = downcall(
                    advapi32, "GetLengthSid", javaInt, false,
                    addressLayout
                ),
                initializeSecurityDescriptor = downcall(
                    advapi32, "InitializeSecurityDescriptor", javaInt, false,
                    addressLayout, javaInt
                ),
                initializeAcl = downcall(
                    advapi32, "InitializeAcl", javaInt, false,
                    addressLayout, javaInt, javaInt
                ),
                addAccessAllowedAce = downcall(
                    advapi32, "AddAccessAllowedAce", javaInt, false,
                    addressLayout, javaInt, javaInt, addressLayout
                ),
                setSecurityDescriptorOwner = downcall(
                    advapi32, "SetSecurityDescriptorOwner", javaInt, false,
                    addressLayout, addressLayout, javaInt
                ),
                setSecurityDescriptorDacl = downcall(
                    advapi32, "SetSecurityDescriptorDacl", javaInt, false,
                    addressLayout, javaInt, addressLayout, javaInt
                )
            )
        } catch (_: ClassNotFoundException) {
            null
        } catch (_: NoSuchMethodException) {
            null
        } catch (_: NoSuchFieldException) {
            null
        } catch (_: LinkageError) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: UnsupportedOperationException) {
            null
        } catch (e: InvocationTargetException) {
            val cause = e.cause
            if (cause is IllegalCallerException ||
                cause is SecurityException ||
                cause is UnsupportedOperationException ||
                cause is LinkageError
            ) {
                null
            } else {
                throw e
            }
        } catch (_: IllegalCallerException) {
            null
        }
    }
}
