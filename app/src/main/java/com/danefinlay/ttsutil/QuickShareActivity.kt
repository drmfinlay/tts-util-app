package com.danefinlay.ttsutil

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import org.jetbrains.anko.longToast

/**
 * Activity to quickly read text from an input source.
 * Reading text is done via SpeakerIntentService.
 */
abstract class QuickShareActivity : SpeakerActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (speaker.isReady()) {
            // Start the appropriate service action if the Speaker is ready.
            startServiceAction()

            // Finish the activity.
            finish()
        } else {
            // Check (and eventually setup) text-to-speech.
            checkTTS(CHECK_TTS_SPEAK_AFTERWARDS)
        }
    }

    abstract fun startServiceAction()

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Start the appropriate service action now that the Speaker is ready.
        if (requestCode == CHECK_TTS_SPEAK_AFTERWARDS &&
                resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
            startServiceAction()

            // Finish the activity.
            finish()
        }
    }
}

class ReadClipboardActivity : QuickShareActivity() {
    override fun startServiceAction() {
        val intent = intent ?: return
        if (intent.action == ACTION_READ_CLIPBOARD) {
            if (Build.VERSION.SDK_INT < 29) {
                SpeakerIntentService.startActionReadClipboard(this)
            } else {
                // Display a message about this action on Android 10.
                longToast(R.string.cannot_read_clipboard_android_10_msg)
            }
        }
    }
}
