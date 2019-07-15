package com.danefinlay.ttsutil

import android.content.Context
import android.content.Intent
import android.net.Uri

private const val CHOSEN_FILE_PREF_KEY = "$APP_NAME.CHOSEN_FILE_PREF_KEY"
const val CHOSEN_FILE_LAST_MODIFIED_PREF_KEY =
        "$APP_NAME.CHOSEN_FILE_LAST_MODIFIED_PREF_KEY"

var Context.chosenFileUri: Uri?
    get() {
        // Get chosenFilePath using shared preferences if possible
        val prefs = getSharedPreferences(packageName, Context.MODE_PRIVATE)
        val result = if ( prefs.contains(CHOSEN_FILE_PREF_KEY) ) {
            Uri.parse(prefs.getString(CHOSEN_FILE_PREF_KEY, ""))
        } else {
            null
        }

        return result
    }
    set(value) {
        // Update shared preferences asynchronously every time this is updated
        val prefs = getSharedPreferences(packageName, Context.MODE_PRIVATE)
        prefs.edit().putString(CHOSEN_FILE_PREF_KEY, value.toString()).apply()
    }

val Context.fileUriProperties: Map<String, String>?
    get() {
        val fileUri = setupFileUriForProcessing() ?: return null
        return contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
            val result = mutableMapOf<String, String>()
            while ( cursor.moveToNext() ) {
                cursor.columnNames.forEach {
                    result[it] = cursor.getString(cursor.getColumnIndex(it))
                }
            }
            result
        }
    }

val Context.fileUriLastModified: Long?
    get() {
        val properties = fileUriProperties ?: return null
        val lastModifiedKey = "last_modified"
        return try {
            properties[lastModifiedKey]?.toLong()
        } catch (e: NumberFormatException) { null }
    }

var Context.fileUriLastModifiedPrefValue: Long?
    get() {
        val prefs = getSharedPreferences(packageName, Context.MODE_PRIVATE)
        val result = if ( prefs.contains(CHOSEN_FILE_LAST_MODIFIED_PREF_KEY) ) {
            try {
            prefs.getString(CHOSEN_FILE_LAST_MODIFIED_PREF_KEY, null)?.toLong()
            } catch (e: NumberFormatException) { null }
        } else null
        return result
    }
    set(value) {
        val prefs = getSharedPreferences(packageName, Context.MODE_PRIVATE)
        prefs.edit().putString(
                CHOSEN_FILE_LAST_MODIFIED_PREF_KEY,
                value.toString()
        ).apply()
    }

/**
 * Handles FileUri exceptions and returns whether the block threw an exception as well as
 * the result from the `block` lambda.
 */
inline fun <R> Context.handleFileUriExceptions(block: () -> R): Pair<Boolean, R?> {
    return try {
        val result = block()
        Pair(false, result)
    } catch (e: Exception) {
        when (e) {
            is NullPointerException, is SecurityException -> {
                // SecurityException: the file probably doesn't exist or is inaccessible.
                // NullPointerException: Cursor was null (probably)
                chosenFileUri = null

                Pair(true, null)
            }

            else -> throw e // Re-throw others; we shouldn't catch everything...
        }
    }
}

val Context.chosenPathIsValid: Boolean
    get() {
        val (exceptional, result) = handleFileUriExceptions {
            val fileUri = setupFileUriForProcessing() ?: return false

            // Check that the returned Cursor from the content resolver query is not null
            // If it is then the file has been moved, deleted, or is inaccessible.
            val cursor = contentResolver.query(fileUri, null, null, null, null) ?: return false

            cursor.close()

            true
        }

        return !exceptional && result == true
    }

fun Context.setupFileUriForProcessing(): Uri? {
    val fileUri = chosenFileUri ?: return null
    val contentResolver = contentResolver
    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    // Handle permissions properly.
    contentResolver.takePersistableUriPermission(fileUri, takeFlags)

    return fileUri
}

val Context.uriContent: List<String>?
    get() {
        val fileUri = setupFileUriForProcessing() ?: return null
        val inputStream = contentResolver.openInputStream(fileUri)
        val reader = inputStream.bufferedReader()
        val lines = reader.lineSequence().toList()

        reader.close()
        inputStream.close()

        return lines
    }

val Context.uriAbsFilePath: String?
    get() {
        TODO()
    }
