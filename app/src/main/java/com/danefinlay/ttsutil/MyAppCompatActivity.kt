package com.danefinlay.ttsutil

import android.annotation.SuppressLint
import android.support.v7.app.AppCompatActivity

/**
 * Custom activity class so things like 'myApplication' and 'speaker' don't need to
 * be redefined for each activity.
 */
@SuppressLint("Registered")
open class MyAppCompatActivity: AppCompatActivity() {
    val myApplication: ApplicationEx
        get() = application as ApplicationEx

    val speaker: Speaker?
        get() = myApplication.speaker
}
