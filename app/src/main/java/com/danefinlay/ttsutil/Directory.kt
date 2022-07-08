/*
 * TTS Util
 *
 * Authors: Dane Finlay <Danesprite@posteo.net>
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
    abstract fun openDocumentOutputStream(ctx: Context,
                                          documentName: String,
                                          mimeType: String): OutputStream?

    class File(private val file: java.io.File) : Directory() {
        override fun exists(ctx: Context): Boolean = file.exists()

        override fun openDocumentOutputStream(ctx: Context,
                                              documentName: String,
                                              mimeType: String): OutputStream {
            val file = File(file, documentName)
            return FileOutputStream(file)
        }
    }

    class DocumentFile(private val uri: Uri) : Directory() {
        private fun isValidDirectory(
                dir: android.support.v4.provider.DocumentFile): Boolean {
            return dir.isDirectory && dir.exists()
        }

        override fun exists(ctx: Context): Boolean {
            val dir = android.support.v4.provider.DocumentFile
                    .fromTreeUri(ctx, uri)
            return if (dir != null) isValidDirectory(dir) else false
        }

        override fun openDocumentOutputStream(ctx: Context,
                                              documentName: String,
                                              mimeType: String): OutputStream? {
            // Open an output stream on the specified document.
            val dir = android.support.v4.provider.DocumentFile.fromTreeUri(ctx, uri)
            if (dir == null || !isValidDirectory(dir)) return null

            // TODO Handle already existing wave files by creating new files:
            //  '<filename>.wav (1)', <filename>.wav (2), etc.
            // Create a new file, if necessary.
            var file = dir.findFile(documentName)
            if (file == null) file = dir.createFile(mimeType, documentName)

            // If successful, open and return an output stream.
            return file?.uri?.openContentOutputStream(ctx, false)
        }
    }
}
