package com.danefinlay.ttsutil.ui

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.Engine.ACTION_GET_SAMPLE_TEXT
import android.support.design.widget.TextInputLayout
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import com.danefinlay.ttsutil.R
import com.danefinlay.ttsutil.isReady
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

                R.id.menu_tts_settings -> {
                    // Got this from: https://stackoverflow.com/a/8688354
                    val intent = Intent()
                    intent.action = "com.android.settings.TTS_SETTINGS"
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
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
            if (speaker.isReady()) {
                speakFromInputLayout()
            } else {
                // Speaker isn't set up.
                toast(R.string.speaker_not_ready_message)

                // Check (and eventually setup) text-to-speech.
                checkTTS(CHECK_TTS_SPEAK_AFTERWARDS)
            }
        }

        stopSpeakingButton.onClick {
            speaker?.stopSpeech()
        }

        clearBoxButton.onClick {
            ttsInputLayout.editText?.text?.clear()
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CHECK_TTS_SPEAK_AFTERWARDS &&
                resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
            // The Speaker should have been initialised by the super method.
            speakFromInputLayout()
        }
    }

    private fun speakFromInputLayout() {
        val content = ttsInputLayout.editText?.text?.toString()
        if (!content.isNullOrBlank()) {
            speaker?.speak(content)
        } else {
            // Get sample text from the TTS engine.
            // This is handled by SpeakerActivity.onActivityResult().
            val intent = Intent()
            intent.action = ACTION_GET_SAMPLE_TEXT
            startActivityForResult(intent, SPEAK_SAMPLE_TEXT)
        }
    }
}
