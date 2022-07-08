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
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * These classes constitute an adapter for working with text-to-speech input
 * sources.
 */
sealed class InputSource(val description: kotlin.CharSequence) {
    abstract fun isSourceAvailable(ctx: Context): Boolean
    abstract fun getSize(ctx: Context): Long?
    abstract fun openInputStream(ctx: Context): InputStream?

    class CharSequence(val text: kotlin.CharSequence,
                       description: kotlin.CharSequence) :
            InputSource(description) {
        override fun isSourceAvailable(ctx: Context): Boolean = true
        override fun getSize(ctx: Context): Long = text.length.toLong()
        override fun openInputStream(ctx: Context): InputStream =
                ByteArrayInputStream(text.toString().toByteArray())
    }

    class DocumentUri(val uri: Uri?, description: kotlin.CharSequence) :
            InputSource(description) {
        private val file: File?

        init {
            val uriPath = uri?.path
            if (uriPath != null && uri?.scheme == "file") {
                file = File(uriPath)
            } else {
                file = null
            }
        }

        override fun isSourceAvailable(ctx: Context): Boolean {
            return file != null && file.exists() ||
                    uri != null && uri.isAccessibleFile(ctx)
        }

        override fun getSize(ctx: Context): Long? {
            if (file != null) return file.length()
            else if (uri != null) return uri.getFileSize(ctx)
            return null
        }

        override fun openInputStream(ctx: Context): InputStream? {
            if (file != null) return FileInputStream(file)
            else if (uri != null) return uri.openContentInputStream(ctx, true)
            return null
        }
    }
}
