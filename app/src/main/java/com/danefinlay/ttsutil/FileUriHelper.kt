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
import android.os.Build
import android.provider.MediaStore
import android.support.annotation.RequiresApi
import org.jetbrains.anko.storageManager
import java.io.InputStream
import java.io.OutputStream


@RequiresApi(Build.VERSION_CODES.KITKAT)
fun Uri.takeUriPermission(ctx: Context, takeFlags: Int): Uri {
    val contentResolver = ctx.contentResolver
    contentResolver.takePersistableUriPermission(this, takeFlags)
    return this
}


@RequiresApi(Build.VERSION_CODES.KITKAT)
fun Uri.takeReadUriPermission(ctx: Context): Uri {
    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    return takeUriPermission(ctx, takeFlags)
}


@RequiresApi(Build.VERSION_CODES.KITKAT)
fun Uri.takeWriteUriPermission(ctx: Context): Uri {
    val takeFlags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    return takeUriPermission(ctx, takeFlags)
}


/**
 * Whether this Uri is for an accessible file.
 */
fun Uri.isAccessibleFile(ctx: Context): Boolean {
    return try {
        // Ensure we have permission to access the file, if necessary on this
        // version of Android.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            takeReadUriPermission(ctx)
        }

        // Test with a query.
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
 * Get the display name of the file, falling back on the last segment in the path if
 * the display name is not available.
 *
 * Read permission is taken prior to querying for the display name, if requested.
 */
fun Uri.retrieveFileDisplayName(ctx: Context, takePermission: Boolean): String? {
    // Ensure we have permission to access the display name, if necessary on this
    // version of Android.
    if (takePermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        takeReadUriPermission(ctx)
    }

    // Retrieve the display name, falling back on the URI's last path segment, if
    // there is one.
    val columnName = MediaStore.MediaColumns.DISPLAY_NAME
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
    return lastPathSegment
}


/**
 * Return a description of the storage volume containing the resource indicated by
 * the Uri, if possible.
 */
fun Uri.resolveStorageVolumeDescription(ctx: Context): String? {
    var result: String? = null
    if (authority?.startsWith("com.android.externalstorage") == true) {
        result = ctx.getString(R.string.default_output_dir)
    }

    // Find a matching storage volume using the storage manager, if possible.
    val pathSegmentParts = lastPathSegment?.split(":")
    if (pathSegmentParts != null && Build.VERSION.SDK_INT >= 24) {
        if (pathSegmentParts.first() == "primary") {
            result = ctx.storageManager.primaryStorageVolume.getDescription(ctx)
        } else for (volume in ctx.storageManager.storageVolumes) {
            if (volume.uuid == pathSegmentParts.first()) {
                result = volume.getDescription(ctx)
            }
        }
    }
    return result
}


fun Uri.getFileSize(ctx: Context): Long? {
    // Take read permission, if necessary.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        takeReadUriPermission(ctx)
    }

    ctx.contentResolver.openFileDescriptor(this, "r")?.use {
        return it.statSize
    }
    return null
}


/**
 * Open an input stream on to the content associated with the URI, assuming content
 * exists.
 *
 * Read permission is taken prior to opening the input stream, if requested.
 */
fun Uri.openContentInputStream(ctx: Context,
                               takePermission: Boolean): InputStream? {
    // Take read permission, if necessary.
    if (takePermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        takeReadUriPermission(ctx)
    }

    // Open an input stream.
    return ctx.contentResolver.openInputStream(this)
}


/**
 * Open an output stream on to the content associated with the URI, assuming content
 * exists.
 *
 * Write permission is taken prior to opening the output stream, if requested.
 */
fun Uri.openContentOutputStream(ctx: Context,
                                takePermission: Boolean): OutputStream? {
    // Take write permission, if necessary.
    if (takePermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        takeWriteUriPermission(ctx)
    }

    // Open an output stream.
    return ctx.contentResolver.openOutputStream(this)
}
