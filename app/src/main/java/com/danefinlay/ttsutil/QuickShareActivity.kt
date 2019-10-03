package com.danefinlay.ttsutil

import android.content.Intent
import android.os.Bundle

/**
 * Activity to quickly read text from an input source.
 */
class QuickShareActivity : SpeakerActivity() {

    private var waitForActivityResult = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && !speaker.isReady()) {
            checkTTS(CHECK_TTS)
            waitForActivityResult = true
        }

        // Finish the activity.
        if (!waitForActivityResult) finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Finish the activity.
        finish()
    }
}
