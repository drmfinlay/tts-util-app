package com.danefinlay.ttsutil

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.InputStream


fun Uri.setupUriForProcessing(ctx: Context): Uri {
    val contentResolver = ctx.contentResolver
    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    // Handle permissions properly.
    contentResolver.takePersistableUriPermission(this, takeFlags)
    return this
}


fun Uri.validFilePath(ctx: Context): Boolean {
    // Check that the returned Cursor from the content resolver query is not null
    // If it is then the file has been moved, deleted, or is inaccessible.
    return try {
        setupUriForProcessing(ctx)
        val cursor = ctx.contentResolver.query(this, null, null,
                null, null)
        cursor?.close()
        /* return */ cursor != null
    } catch (e: SecurityException) {
        // The file probably doesn't exist or is inaccessible.
        false
    }
}


fun Uri.getFileProperties(ctx: Context): Map<String, String>? {
    return ctx.contentResolver.query(this, null, null, null,
            null)?.use { cursor ->
        val result = mutableMapOf<String, String>()
        while ( cursor.moveToNext() ) {
            cursor.columnNames.forEach {
                result[it] = cursor.getString(cursor.getColumnIndex(it))
            }
        }
        result
    }
}


fun Uri.getDisplayName(ctx: Context): String? {
    // Return the display name property, falling back on the path if the
    // display name is not available.
    return getFileProperties(ctx)?.get("_display_name") ?: path
}


fun Uri.getContent(ctx: Context): InputStream? {
    setupUriForProcessing(ctx)
    return ctx.contentResolver.openInputStream(this)
}
