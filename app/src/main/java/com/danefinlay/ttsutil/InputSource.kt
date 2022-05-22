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
import java.io.InputStream

/**
 * These classes constitute an adapter for working with text-to-speech input
 * sources.
 */
sealed class InputSource(val description: kotlin.String) {
    abstract fun isSourceAvailable(ctx: Context): Boolean
    abstract fun getSize(ctx: Context): Long?
    abstract fun openInputStream(ctx: Context): InputStream?

    class String(val text: kotlin.String, description: kotlin.String) :
            InputSource(description) {
        override fun isSourceAvailable(ctx: Context): Boolean = true
        override fun getSize(ctx: Context): Long = text.length.toLong()
        override fun openInputStream(ctx: Context): InputStream =
                ByteArrayInputStream(text.toByteArray())
    }

    class ContentUri(val uri: Uri?, description: kotlin.String) :
            InputSource(description) {
        override fun isSourceAvailable(ctx: Context) =
                uri?.isAccessibleFile(ctx) == true

        override fun getSize(ctx: Context): Long? =
                uri?.getFileSize(ctx)

        override fun openInputStream(ctx: Context): InputStream? =
                uri?.openContentInputStream(ctx, true)
    }
}
