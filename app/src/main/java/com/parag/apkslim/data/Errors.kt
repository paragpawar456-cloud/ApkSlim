package com.parag.apkslim.data

import android.util.Log
import java.io.EOFException
import java.io.FileNotFoundException
import java.io.IOException
import java.util.zip.ZipException

/**
 * One place that turns any failure into a short message a person can act on, and logs the
 * technical details for the crash/debug report instead of showing them on screen.
 */
object Errors {
    private const val TAG = "ApkSlim"

    fun friendly(t: Throwable, fallback: String = "Something went wrong. Please try again."): String {
        Log.w(TAG, fallback, t)
        CrashLog.note(t)
        val root = generateSequence(t) { it.cause }.last()
        val msg = (t.message ?: "") + " " + (root.message ?: "")
        return when {
            t is UserFacingException -> t.message ?: fallback
            t is OutOfMemoryError || root is OutOfMemoryError ->
                "Ran out of memory. Try turning image optimization off for this app."
            msg.contains("ENOSPC", true) || msg.contains("No space left", true) ->
                "Your phone ran out of storage. Free up some space and try again."
            root is EOFException || msg.contains("truncated", true) ->
                "The file is incomplete or damaged. Download or copy it again."
            root is ZipException || msg.contains("Not an ApkSlim archive") || msg.contains("not a valid APK", true) ->
                "This file is damaged or is not an APK, .apks or .slim file."
            root is FileNotFoundException -> "The file could not be found. It may have been moved or deleted."
            root is SecurityException -> "Android did not allow access to this file. Pick it again from the file picker."
            root is IllegalArgumentException || root is IllegalStateException ->
                t.message?.takeIf { it.length in 1..160 } ?: fallback
            root is IOException -> "Could not read or write the file. Please try again."
            else -> t.message?.takeIf { it.length in 1..160 } ?: fallback
        }
    }
}
