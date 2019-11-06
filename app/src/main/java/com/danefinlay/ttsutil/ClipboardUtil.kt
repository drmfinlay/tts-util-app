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

import android.content.ClipboardManager
import android.content.Context

/**
 * Get the clipboard text, if any.
 *
 * This function returns null if Android's clipboard manager reports the clipboard
 * as empty.
 *
 */
fun Context.getClipboardText(): String? {
    // Get the primary ClipData object from the manager.
    // Return early if there is no clipboard data.
    val clipboardManager = (getSystemService(Context.CLIPBOARD_SERVICE) as
            ClipboardManager)
    val clipData = clipboardManager.primaryClip
    if (clipData == null || !clipboardManager.hasPrimaryClip()) {
        // Note: this can also occur on Android 10 and above if this app isn't
        // the foreground app. This is for privacy reasons. It just means we
        // need to have an activity running while the clipboard is read.
        return null
    }

    // Find the first text clipboard Item.
    var text = ""
    for (i in 0 until clipData.itemCount) {
        val item = clipData.getItemAt(i)
        val itemText = item?.text
        if (!itemText.isNullOrBlank()) {
            text = itemText.toString()
            break
        }
    }
    return text
}
