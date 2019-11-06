package com.danefinlay.ttsutil.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.design.widget.TextInputLayout
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.danefinlay.ttsutil.R
import com.danefinlay.ttsutil.Speaker
import com.danefinlay.ttsutil.getClipboardText
import com.danefinlay.ttsutil.isReady
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.onClick
import org.jetbrains.anko.support.v4.find
import org.jetbrains.anko.toast

abstract class ReadTextFragmentBase : Fragment() {

    private val speakButton: Button
        get() = find(R.id.speak_button)

    private val stopSpeakingButton: Button
        get() = find(R.id.stop_speaking_button)

    protected val inputLayout: TextInputLayout
        get() = find(R.id.input_layout)

    protected val myActivity: SpeakerActivity
        get() = (activity as SpeakerActivity)

    private val speaker: Speaker?
        get() = myActivity.speaker

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        speakButton.onClick {
            if (speaker.isReady()) {
                speakFromInputLayout()
            } else {
                // Speaker isn't set up.
                myActivity.toast(R.string.speaker_not_ready_message)

                // Check (and eventually setup) text-to-speech.
                myActivity.checkTTS(SpeakerActivity.CHECK_TTS_SPEAK_AFTERWARDS)
            }
        }

        stopSpeakingButton.onClick {
            speaker?.stopSpeech()
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        // Restore fragment instance state here.
        val content = savedInstanceState?.getString("inputLayoutContent")
        if (content != null) {
            inputLayout.editText?.text?.apply {
                clear()
                append(content)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // Save fragment instance state here.
        if (view != null) {
            val content = inputLayout.editText?.text?.toString()
            outState.putString("inputLayoutContent", content)
        }
    }

    private fun speakFromInputLayout() {
        val content = inputLayout.editText?.text?.toString()
        if (content.isNullOrBlank()) {
            myActivity.toast(R.string.cannot_speak_empty_text_msg)
            speaker?.speak(inputLayout.hint?.toString())
            return
        }

        // Speak text.
        speaker?.speak(content)
    }
}

class ReadTextFragment : ReadTextFragmentBase() {

    val intent: Intent?
        get() = myActivity.intent

    private val clearBoxButton: Button
        get() = find(R.id.clear_box_button)

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_read_text, container,
                false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        clearBoxButton.onClick {
            inputLayout.editText?.text?.clear()
        }

        // Handle ACTION_SEND.
        if (savedInstanceState == null && intent?.action == Intent.ACTION_SEND) {
            val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
            if (text != null) {
                inputLayout.editText?.text?.apply {
                    clear()
                    append(text)
                }
            }
        }
    }
}

class ReadClipboardFragment : ReadTextFragmentBase() {

    private val updateTextFieldButton: Button
        get() = find(R.id.update_text_field_button)

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_read_clipboard, container,
                false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateTextFieldButton.onClick {
            onClickUpdateTextFieldButton()
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (savedInstanceState == null) {
            val text = myActivity.getClipboardText()

            // Android 10 restricts access to the clipboard for privacy reasons.
            // Accessing it from the foreground activity is permitted, but it seems
            // to require a short delay (async).
            if (text == null && Build.VERSION.SDK_INT >= 29) {
                doAsync {
                    Thread.sleep(100)
                    myActivity.runOnUiThread {
                        if (this@ReadClipboardFragment.view != null)
                            updateTextField(myActivity.getClipboardText())
                    }
                }
            } else {
                // Otherwise just update the text field.
                updateTextField(text)
            }
        }
    }

    private fun updateTextField(text: String?) {
        inputLayout.editText?.text?.clear()
        if (text != null) {
            inputLayout.editText?.text?.append(text)
        }
    }

    private fun onClickUpdateTextFieldButton() {
        // Get the current clipboard text.
        val text = myActivity.getClipboardText()

        // Update the text field.
        updateTextField(text)

        // Display a message if the clipboard was empty.
        if (text.isNullOrEmpty()) {
            myActivity.toast(R.string.clipboard_is_empty_blank_msg)
        }
    }
}
