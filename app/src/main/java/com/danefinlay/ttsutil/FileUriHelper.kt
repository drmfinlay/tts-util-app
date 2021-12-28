/*
 * TTS Util
 *
 * Authors: Dane Finlay <Danesprite@posteo.net>
 *
 * Copyright (C) 2019 Dane Finlay
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.danefinlay.ttsutil

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.InputStream
import java.io.OutputStream


fun Uri.takeUriPermission(ctx: Context, takeFlags: Int): Uri {
    val contentResolver = ctx.contentResolver
    contentResolver.takePersistableUriPermission(this, takeFlags)
    return this
}


fun Uri.takeReadUriPermission(ctx: Context): Uri {
    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    return takeUriPermission(ctx, takeFlags)
}


fun Uri.takeWriteUriPermission(ctx: Context): Uri {
    val takeFlags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    return takeUriPermission(ctx, takeFlags)
}


/**
 * Whether this Uri is for an accessible file.
 */
fun Uri.isAccessibleFile(ctx: Context): Boolean {
    return try {
        // Ensure we have permission to access the file.
        takeReadUriPermission(ctx)
        val cursor = ctx.contentResolver.query(this, arrayOf(), null,
                null, null)
        cursor?.close()

        // The file is accessible.
        cursor != null
    } catch (e: SecurityException) {
        // The file either doesn't exist or is inaccessible.
        false
    }
}


/**
 * Get the display name of the file, falling back on the path if the
 * display name is not available.
 */
fun Uri.retrieveFileDisplayName(ctx: Context): String? {
    // Ensure we have permission to access the display name.
    takeReadUriPermission(ctx)

    // Retrieve the display name, falling back on the URI path, if there is one.
    val columnName = "_display_name"
    val cursor = ctx.contentResolver.query(this, arrayOf(columnName),
            null, null, null)
    cursor?.use {
        while (it.moveToNext()) {
            val index =  cursor.getColumnIndex(columnName)
            if (index != -1) {
                return cursor.getString(index)
            }
        }
    }
    return path
}


/**
 * Open an input stream on to the content associated with the URI, assuming content
 * exists.
 *
 * Read permission is taken prior to opening the input stream.
 */
fun Uri.openContentInputStream(ctx: Context): InputStream? {
    // Ensure we have permission to read the content.
    takeReadUriPermission(ctx)

    // Open an input stream.
    return ctx.contentResolver.openInputStream(this)
}


/**
 * Open an output stream on to the content associated with the URI, assuming content
 * exists.
 *
 * Write permission is taken prior to opening the output stream.
 */
fun Uri.openContentOutputStream(ctx: Context): OutputStream? {
    // Ensure we have permission to write content.
    takeWriteUriPermission(ctx)

    // Open an output stream.
    return ctx.contentResolver.openOutputStream(this)
}
