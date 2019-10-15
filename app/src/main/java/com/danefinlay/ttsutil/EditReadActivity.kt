package com.danefinlay.ttsutil

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.support.design.widget.TextInputLayout
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import org.jetbrains.anko.find
import org.jetbrains.anko.onClick
import org.jetbrains.anko.toast

class EditReadActivity : SpeakerActivity() {

    private val speakButton: Button
        get() = find(R.id.speak_button)

    private val stopSpeakingButton: Button
        get() = find(R.id.stop_speaking_button)

    private val ttsInputLayout: TextInputLayout
        get() = find(R.id.tts_input_layout)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_read)
        when (intent.action) {
            Intent.ACTION_SEND -> {
                // Set the contents of the input layout to the shared text.
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (text != null) {
                    ttsInputLayout.editText?.text?.apply {
                        clear()
                        append(text)
                    }
                }
            }
        }

        speakButton.onClick {onSpeakButtonClick()}
        stopSpeakingButton.onClick {speaker?.stopSpeech()}
    }

    private fun onSpeakButtonClick() {
        if (speaker.isReady()) {
            speakFromInputLayout()
        } else {
            // Speaker isn't set up.
            toast(R.string.speaker_not_ready_message)

            // Check (and eventually setup) text-to-speech.
            checkTTS(CHECK_TTS_SPEAK_AFTERWARDS)
        }
    }

    private fun speakFromInputLayout() {
        // Speaking the contents of the input layout via SpeakerIntentService.
        val text = ttsInputLayout.editText?.text?.toString()
        SpeakerIntentService.startActionReadText(this, text)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.edit_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?) = when ( item?.itemId ) {
        R.id.menu_speak_in_bg -> {
            if (speaker.isReady()) {
                speakFromInputLayout()
                finish()
            } else {
                // Check (and eventually setup) text-to-speech.
                checkTTS(CHECK_TTS_SPEAK_AFTERWARDS_FINISH)
            }
            true
        }

        else -> false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
            // The Speaker should have been initialised by the super method.
            when (requestCode) {
                CHECK_TTS_SPEAK_AFTERWARDS -> speakFromInputLayout()
                CHECK_TTS_SPEAK_AFTERWARDS_FINISH -> {
                    speakFromInputLayout()
                    finish()
                }
            }
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

    companion object {
        const val CHECK_TTS_SPEAK_AFTERWARDS_FINISH = 5
    }
}
