@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.ipc

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value
import platform.posix.memset
import platform.windows.ACCESS_ALLOWED_ACE
import platform.windows.ACL
import platform.windows.ACL_REVISION
import platform.windows.AddAccessAllowedAce
import platform.windows.CloseHandle
import platform.windows.FILE_ALL_ACCESS
import platform.windows.GetCurrentProcess
import platform.windows.GetLengthSid
import platform.windows.GetTokenInformation
import platform.windows.HANDLEVar
import platform.windows.InitializeAcl
import platform.windows.InitializeSecurityDescriptor
import platform.windows.OpenProcessToken
import platform.windows.PSID
import platform.windows.SECURITY_ATTRIBUTES
import platform.windows.SECURITY_DESCRIPTOR_REVISION
import platform.windows.SetSecurityDescriptorDacl
import platform.windows.SetSecurityDescriptorOwner
import platform.windows.TOKEN_QUERY
import platform.windows.TOKEN_USER
import platform.windows.TokenUser

/**
 * Current-user SID DACL `SECURITY_ATTRIBUTES` builder for Windows Named Pipes (`mingwX64`).
 */
internal object WindowsNamedPipeSecurity {

    fun <T> withCurrentUserPipeSecurityAttributes(block: (CPointer<SECURITY_ATTRIBUTES>?) -> T): T =
        withCurrentUserSid(
            onFailure = { block(null) }
        ) { pSid, sidLen ->
            memScoped {
                val sdBuf = allocArray<ULongVar>(32)
                memset(sdBuf, 0, 256.convert())
                val sdPtr = sdBuf.reinterpret<kotlinx.cinterop.ByteVar>()
                if (InitializeSecurityDescriptor(sdPtr, SECURITY_DESCRIPTOR_REVISION.toUInt()) == 0) {
                    return@memScoped block(null)
                }

                val rawAclSize = sizeOf<ACL>().toInt() + sizeOf<ACCESS_ALLOWED_ACE>().toInt() + sidLen + 16
                val aclSize = (rawAclSize + 3) and 3.inv()
                val aclULongCount = (aclSize + 7) / 8
                val aclBuf = allocArray<ULongVar>(aclULongCount)
                memset(aclBuf, 0, (aclULongCount * 8).convert())
                val aclPtr = aclBuf.reinterpret<ACL>()

                if (InitializeAcl(aclPtr, aclSize.toUInt(), ACL_REVISION.toUInt()) == 0 ||
                    AddAccessAllowedAce(aclPtr, ACL_REVISION.toUInt(), FILE_ALL_ACCESS.toUInt(), pSid) == 0 ||
                    SetSecurityDescriptorOwner(sdPtr, pSid, 0) == 0 ||
                    SetSecurityDescriptorDacl(sdPtr, 1, aclPtr, 0) == 0
                ) {
                    return@memScoped block(null)
                }

                val sa = alloc<SECURITY_ATTRIBUTES>()
                memset(sa.ptr, 0, sizeOf<SECURITY_ATTRIBUTES>().convert())
                sa.nLength = sizeOf<SECURITY_ATTRIBUTES>().toUInt()
                sa.lpSecurityDescriptor = sdPtr
                sa.bInheritHandle = 0
                block(sa.ptr)
            }
        }

    private fun <T> withCurrentUserSid(
        onFailure: () -> T,
        block: (pSid: PSID, sidLen: Int) -> T
    ): T = memScoped {
        val tokenHandleVar = alloc<HANDLEVar>()
        if (OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY.toUInt(), tokenHandleVar.ptr) == 0) {
            return@memScoped onFailure()
        }
        val hToken = tokenHandleVar.value ?: return@memScoped onFailure()
        try {
            val neededVar = alloc<UIntVar>()
            GetTokenInformation(hToken, TokenUser, null, 0u, neededVar.ptr)
            val needed = neededVar.value.toInt()
            if (needed <= 0) {
                return@memScoped onFailure()
            }
            val uLongCount = (needed + 7) / 8
            val tokenBuf = allocArray<ULongVar>(uLongCount)
            memset(tokenBuf, 0, (uLongCount * 8).convert())
            if (GetTokenInformation(hToken, TokenUser, tokenBuf, needed.toUInt(), neededVar.ptr) == 0) {
                return@memScoped onFailure()
            }
            val tokenUser = tokenBuf.reinterpret<TOKEN_USER>()
            val pSid = tokenUser.pointed.User.Sid ?: return@memScoped onFailure()
            val sidLen = GetLengthSid(pSid).toInt()
            if (sidLen <= 0) {
                return@memScoped onFailure()
            }
            block(pSid, sidLen)
        } finally {
            CloseHandle(hToken)
        }
    }
}
