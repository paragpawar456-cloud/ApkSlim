package com.parag.apkslim.data

import java.io.File

/** An error whose message is already written for the user. */
class UserFacingException(message: String) : Exception(message)

/** Limits and checks applied to every file the user opens. */
object InputLimits {
    const val MAX_INPUT_BYTES = 4L shl 30          // 4 GB
    const val SPACE_MARGIN = 64L shl 20            // keep 64 MB free

    enum class Kind { ZIP, SLIM, UNKNOWN }

    /** Reads the first bytes: APK and .apks files are zips ("PK\u0003\u0004"), .slim starts with "APKSLIM". */
    fun sniff(file: File): Kind {
        val head = ByteArray(7)
        val n = runCatching { file.inputStream().use { it.read(head) } }.getOrDefault(-1)
        if (n >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() && head[2] == 3.toByte() && head[3] == 4.toByte()) return Kind.ZIP
        if (n == 7 && String(head, Charsets.US_ASCII) == "APKSLIM") return Kind.SLIM
        return Kind.UNKNOWN
    }
}
