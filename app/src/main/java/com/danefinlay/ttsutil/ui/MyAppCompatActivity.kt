package com.danefinlay.ttsutil.ui

import android.support.v7.app.AppCompatActivity
import com.danefinlay.ttsutil.ApplicationEx

abstract class MyAppCompatActivity : AppCompatActivity() {
    protected val myApplication: ApplicationEx
        get() = application as ApplicationEx
}
