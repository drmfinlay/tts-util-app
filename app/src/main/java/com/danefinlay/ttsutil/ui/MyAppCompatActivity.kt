package com.danefinlay.ttsutil.ui

import androidx.appcompat.app.AppCompatActivity
import com.danefinlay.ttsutil.ApplicationEx

abstract class MyAppCompatActivity : AppCompatActivity() {
    protected val myApplication: ApplicationEx
        get() = application as ApplicationEx

    override fun onPause() {
        super.onPause()

        // Enable notifications when our activities are inactive.
        myApplication.enableNotifications()
    }

    override fun onResume() {
        super.onResume()

        // Disable notifications when one of our activities is active.
        myApplication.disableNotifications()
    }
}
