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
