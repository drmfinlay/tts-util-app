package com.danefinlay.ttsutil.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.support.v7.app.AppCompatActivity
import com.danefinlay.ttsutil.ApplicationEx
import com.danefinlay.ttsutil.R
import com.danefinlay.ttsutil.Speaker
import org.jetbrains.anko.AlertDialogBuilder

/**
 * Custom activity class so things like 'myApplication' and 'speaker' don't need to
 * be redefined for each activity.
 */
@SuppressLint("Registered")
open class SpeakerActivity: AppCompatActivity() {
    val myApplication: ApplicationEx
        get() = application as ApplicationEx

    val speaker: Speaker?
        get() = myApplication.speaker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if ( savedInstanceState == null ) {
            // Check if there is a TTS engine is installed on the device.
            // Only necessary if the Speaker is null.
            if (speaker == null) {
                checkTTS(CHECK_TTS)
            }
        }
    }

    fun checkTTS(code: Int) {
        val check = Intent()
        check.action = TextToSpeech.Engine.ACTION_CHECK_TTS_DATA
        startActivityForResult(check, code)
    }

    private fun showNoTTSDataDialog() {
        AlertDialogBuilder(this).apply {
            title(R.string.no_tts_data_alert_title)
            message(R.string.no_tts_data_alert_message)
            positiveButton(R.string.alert_positive_message) {
                val install = Intent()
                install.action = TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
                startActivityForResult(install, INSTALL_TTS_DATA)
            }
            negativeButton(R.string.alert_negative_message1)
            show()
        }
    }

    private fun showTTSInstallFailureDialog() {
        // Show a dialog if installing TTS data failed.
        AlertDialogBuilder(this).apply {
            title(R.string.failed_to_get_tts_data_title)
            message(R.string.failed_to_get_tts_data_msg)
            positiveButton(R.string.alert_positive_message) {}
            show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            CHECK_TTS, CHECK_TTS_SPEAK_AFTERWARDS -> {
                if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                    // Start the speaker.
                    myApplication.startSpeaker()
                } else {
                    // Show a dialogue *and then* start an activity to install a
                    // text to speech engine if the user agrees.
                    showNoTTSDataDialog()
                }
            }

            INSTALL_TTS_DATA -> {
                if (resultCode == TextToSpeech.ERROR) {
                    showTTSInstallFailureDialog()
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    companion object {
        const val CHECK_TTS = 1
        const val CHECK_TTS_SPEAK_AFTERWARDS = 2
        const val INSTALL_TTS_DATA = 3
    }
}
