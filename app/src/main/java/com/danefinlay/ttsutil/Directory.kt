/*
 * TTS Util
 *
 * Authors: Dane Finlay <dane@danefinlay.net>
 *
 * Copyright (C) 2022 Dane Finlay
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
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * These classes constitute an adapter for working with File and DocumentFile
 * objects.
 */

sealed class Directory {
    abstract fun exists(ctx: Context): Boolean
    abstract fun canRead(ctx: Context): Boolean
    abstract fun canWrite(ctx: Context): Boolean
    abstract fun openDocumentOutputStream(ctx: Context,
                                          documentName: String,
                                          mimeType: String): OutputStream?

    class File(private val file: java.io.File) : Directory() {
        override fun exists(ctx: Context): Boolean = file.exists()
        override fun canRead(ctx: Context): Boolean = file.canRead()
        override fun canWrite(ctx: Context): Boolean = file.canWrite()

        override fun openDocumentOutputStream(ctx: Context,
                                              documentName: String,
                                              mimeType: String): OutputStream? {
            // Open an output stream on the specified file, if possible.
            val dir = this.file
            if (dir.canWrite()) {
                val file = File(dir, documentName)
                return FileOutputStream(file)
            } else {
                return null
            }
        }
    }

    class DocumentFile(private val uri: Uri) : Directory() {
        private fun getDocumentFileDir(ctx: Context):
                androidx.documentfile.provider.DocumentFile? {
            return androidx.documentfile.provider.DocumentFile
                    .fromTreeUri(ctx, uri)
        }

        override fun canRead(ctx: Context): Boolean {
            return getDocumentFileDir(ctx)?.canRead() ?: false
        }

        override fun canWrite(ctx: Context): Boolean {
            return getDocumentFileDir(ctx)?.canWrite() ?: false
        }

        private fun isValidDirectory(
                dir: androidx.documentfile.provider.DocumentFile): Boolean {
            return dir.isDirectory && dir.exists()
        }

        override fun exists(ctx: Context): Boolean {
            val dir = getDocumentFileDir(ctx)
            return if (dir != null) isValidDirectory(dir) else false
        }

        override fun openDocumentOutputStream(ctx: Context,
                                              documentName: String,
                                              mimeType: String): OutputStream? {
            // Get a DocumentFile object using the specified URI.
            val dir = androidx.documentfile.provider.DocumentFile.fromTreeUri(ctx,
                    uri)

            // Return early if the directory is invalid or if we do not have read/
            // write permission.
            if (dir == null || !isValidDirectory(dir) || !dir.canRead() ||
                    !dir.canWrite()) return null

            // Create a new file, if necessary.
            var file = dir.findFile(documentName)
            if (file == null) file = dir.createFile(mimeType, documentName)

            // Open an output stream on the specified document, if possible.
            return file?.uri?.openContentOutputStream(ctx, false)
        }
    }
}
