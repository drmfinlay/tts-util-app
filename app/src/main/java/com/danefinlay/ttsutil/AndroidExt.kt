/*
 * TTS Util
 *
 * Authors: Dane Finlay <dane@danefinlay.net>
 *
 * Copyright (C) 2025 Dane Finlay
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

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.storage.StorageManager
import android.view.View
import android.view.View.OnClickListener
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment

/**
 * Context extension function for posting a block of code to be run on the UI
 * thread.
 */
fun Context.runOnUiThread(b: Context.() -> Unit) {
    Handler(Looper.getMainLooper()).post { b() }
}

/**
 * Context extension function for showing a toast message by string ID.
 *
 * The duration is LENGTH_SHORT by default.
 */
fun Context.toast(@StringRes resId: Int, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, resId, duration).show()
}

/**
 * Context extension function for showing a toast message by character sequence.
 *
 * The duration is LENGTH_SHORT by default.
 */
fun Context.toast(text: CharSequence, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, text, duration).show()
}

/**
 * Context extension for getting the system AudioManager service instance.
 */
val Context.audioManager: AudioManager
    get() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

/**
 * Context extension for getting the system NotificationManager service instance.
 */
val Context.notificationManager: NotificationManager
    get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

/**
 * Context extension for getting the system StorageManager service instance.
 */
val Context.storageManager: StorageManager
    get () = getSystemService(Context.STORAGE_SERVICE) as StorageManager

/**
 * Context extension function for showing a toast message by string ID.
 *
 * The duration is LENGTH_SHORT by default.
 */
fun Fragment.toast(@StringRes id: Int, duration: Int = Toast.LENGTH_SHORT)
        = context?.toast(id, duration)

/**
 * Fragment extension function for showing a toast message by character sequence.
 *
 * The duration is LENGTH_SHORT by default.
 */
fun Fragment.toast(text: CharSequence, duration: Int = Toast.LENGTH_SHORT)
    = context?.toast(text, duration)

/**
 * View extension for getContext().
 */
val View.ctx: Context
    get() = this.context

/**
 * View extension function for setting an OnClickListener.
 */
fun View.onClick(l: OnClickListener?): View {
    setOnClickListener(l)
    return this
}
