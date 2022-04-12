package com.danefinlay.ttsutil.ui

import android.support.v7.app.AppCompatActivity
import com.danefinlay.ttsutil.ApplicationEx

abstract class MyAppCompatActivity : AppCompatActivity() {
    protected val myApplication: ApplicationEx
        get() = application as ApplicationEx

    override fun onPause() {
        super.onPause()

        // Enable notifications when our activities are inactive.
        myApplication.notificationsEnabled = true
    }

    override fun onResume() {
        super.onResume()

        // Disable notifications when one of our activities is active.
        myApplication.notificationsEnabled = false
    }
}
