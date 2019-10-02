package com.danefinlay.ttsutil

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.Engine.ACTION_GET_SAMPLE_TEXT
import android.speech.tts.TextToSpeech.Engine.EXTRA_SAMPLE_TEXT
import android.support.design.widget.TextInputLayout
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import org.jetbrains.anko.AlertDialogBuilder
import org.jetbrains.anko.find
import org.jetbrains.anko.onClick
import org.jetbrains.anko.toast

class MainActivity : SpeakerActivity() {

    private val fileActivityButton: Button
        get() = find(R.id.file_activity_button)

    private val speakButton: Button
        get() = find(R.id.speak_button)

    private val stopSpeakingButton: Button
        get() = find(R.id.stop_speaking_button)

    private val clearBoxButton: Button
        get() = find(R.id.clear_box_button)

    private val ttsInputLayout: TextInputLayout
        get() = find(R.id.tts_input_layout)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if ( savedInstanceState == null ) {
            // Check if there is a TTS engine is installed on the device.
            checkTTS(CHECK_TTS)
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        val content = ttsInputLayout.editText?.text?.toString()
        outState?.putString("inputBoxContent", content)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        val content = savedInstanceState?.getString("inputBoxContent")
        if (content != null) {
            ttsInputLayout.editText?.text?.apply {
                clear()
                append(content)
            }
        }
        super.onRestoreInstanceState(savedInstanceState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.app_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
            when (item.itemId) {

                R.id.menu_about -> {
                    val activity = AboutActivity::class.java
                    startActivity(Intent(this, activity))
                    true
                }

                else -> {
                    // Display a not implemented yet toast message
                    toast(R.string.not_yet_implemented)

                    // return the super class method's value for other menu cases
                    super.onOptionsItemSelected(item)
                }
            }

    override fun onStart() {
        super.onStart()

        fileActivityButton.onClick {
            // Start the file activity when the button is pressed.
            val activity = FileActivity::class.java
            val fileActivityIntent = Intent(this, activity)
            startActivity(fileActivityIntent)
        }

        speakButton.onClick {
            if (speaker == null) {
                // Check (and eventually setup) text-to-speech.
                checkTTS(CHECK_TTS_SPEAK_AFTERWARDS)
            } else {
                speakFromInputLayout()
            }
        }

        stopSpeakingButton.onClick {
            speaker?.stopSpeech()
        }

        clearBoxButton.onClick {
            ttsInputLayout.editText?.text?.clear()
        }

        // TODO Probably should be another activity
        if (intent?.action == Intent.ACTION_SEND) {
            val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
            if (text != null) {
                ttsInputLayout.editText?.text?.apply {
                    clear()
                    append(text)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Free the speaker only if the activity (and probably the application)
        // is finishing.
        if (isFinishing) {
            myApplication.freeSpeaker()
        }
    }

    private fun checkTTS(code: Int) {
        val check = Intent()
        check.action = TextToSpeech.Engine.ACTION_CHECK_TTS_DATA
        startActivityForResult(check, code)
    }

    private fun speakFromInputLayout() {
        val content = ttsInputLayout.editText?.text?.toString()
        if (!content.isNullOrBlank()) {
            speaker?.speak(listOf(content))
        } else {
            // Get sample text from the TTS engine.
            val intent = Intent()
            intent.action = ACTION_GET_SAMPLE_TEXT
            startActivityForResult(intent, SPEAK_SAMPLE_TEXT)
        }
    }

    private fun showNoTTSDataDialog() {
        AlertDialogBuilder(this).apply {
            title(R.string.no_tts_data_alert_title)
            message(R.string.no_tts_data_alert_message)
            positiveButton("Okay") {
                val install = Intent()
                install.action = TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
                startActivityForResult(install, INSTALL_TTS_DATA)
            }
            negativeButton("No thanks")
            show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            CHECK_TTS, CHECK_TTS_SPEAK_AFTERWARDS -> {
                if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                    // Start the speaker.
                    myApplication.startSpeaker()
                    if (requestCode == CHECK_TTS_SPEAK_AFTERWARDS) {
                        speakFromInputLayout()
                    }
                } else {
                    // Show a dialogue *and then* start an activity to install a
                    // text to speech engine if the user agrees.
                    showNoTTSDataDialog()
                }
            }

            INSTALL_TTS_DATA -> {
                if (resultCode == TextToSpeech.ERROR) {
                    // Show a dialog if installing TTS data failed.
                    AlertDialogBuilder(this).apply {
                        title(R.string.failed_to_get_tts_data_title)
                        message(R.string.failed_to_get_tts_data_msg)
                        positiveButton(R.string.alert_positive_message) {}
                        show()
                    }
                }
            }

            SPEAK_SAMPLE_TEXT -> {
                // Speak sample text
                val sampleText = data?.getStringExtra(EXTRA_SAMPLE_TEXT) ?:
                        ttsInputLayout.editText?.hint?.toString()
                if (sampleText != null) {
                    myApplication.speaker?.speak(sampleText)
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    companion object {
        private const val CHECK_TTS = 1
        private const val CHECK_TTS_SPEAK_AFTERWARDS = 2
        private const val INSTALL_TTS_DATA = 3
        private const val SPEAK_SAMPLE_TEXT = 4
    }
}
