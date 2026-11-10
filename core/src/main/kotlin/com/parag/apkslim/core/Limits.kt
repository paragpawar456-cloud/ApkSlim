package com.parag.apkslim.core

/** Hard limits for untrusted input (.slim archives and .apks bundles), so a broken or hostile file fails cleanly. */
object Limits {
    const val MAX_PARTS = 100
    const val MAX_ENTRIES = 200_000
    const val MAX_ENTRY_BYTES = 4L shl 30   // 4 GB per entry
    const val MAX_TOTAL_BYTES = 16L shl 30  // 16 GB restored in total
    const val SPACE_MARGIN = 64L shl 20

    /** Zip entry names: relative, no "..", no backslashes or control characters. */
    fun isSafeEntryName(name: String): Boolean =
        name.isNotEmpty() && name.length <= 1024 &&
            !name.startsWith("/") && '\\' !in name && name.none { it.code < 0x20 } &&
            name.split('/').none { it == ".." }
}
