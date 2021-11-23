/*
 * TTS Util
 *
 * Authors: Dane Finlay <Danesprite@posteo.net>
 *
 * Copyright (C) 2019 Dane Finlay
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.danefinlay.ttsutil.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.support.design.widget.TextInputLayout
import android.support.v4.app.Fragment
import android.support.v7.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import com.danefinlay.ttsutil.*
import org.jetbrains.anko.*
import org.jetbrains.anko.support.v4.find

abstract class ReadTextFragmentBase : Fragment() {

    private val speakButton: ImageButton
        get() = find(R.id.speak_button)

    private val stopSpeakingButton: ImageButton
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
                // Show the speaker not ready message.
                myActivity.showSpeakerNotReadyMessage()
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

    private val clearBoxButton: ImageButton
        get() = find(R.id.clear_box_button)

    private val mem1: ImageButton
        get() = find(R.id.Memory1)

    private val mem2: ImageButton
        get() = find(R.id.Memory2)

    private val mem3: ImageButton
        get() = find(R.id.Memory3)

    private val mem4: ImageButton
        get() = find(R.id.Memory4)

    /**
     * Event listener for the memory buttons.
     */
    private class MemoryButtonEventListener(val memoryKey: String,
                                            val prefs: SharedPreferences,
                                            val inputLayout: TextInputLayout,
                                            val ctx: Context?
    ) : View.OnClickListener, View.OnLongClickListener {

        override fun onClick(v: View?) {
            // Set the text content from memory.  If the memory slot is empty,
            // display a message.
            val text = prefs.getString(memoryKey, "")
            if (text.isNullOrEmpty()) {
                ctx?.runOnUiThread {
                    toast(R.string.mem_slot_empty_msg)
                }
            } else {
                inputLayout.editText?.text?.apply {
                    clear()
                    append(text)
                }
            }
        }

        override fun onLongClick(v: View?): Boolean {
            // Store the text field content in memory, displaying an appropriate
            // message.
            val content = inputLayout.editText?.text?.toString()
            val messageId = if (content.isNullOrEmpty()) R.string.mem_slot_cleared_msg
                            else R.string.mem_slot_set_msg
            val editor: SharedPreferences.Editor = prefs.edit()
            editor.putString(memoryKey, content)
            editor.apply()
            ctx?.runOnUiThread {
                toast(messageId)
            }
            return true
        }
    }

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

        // Set OnClick and OnLongClick event listeners for each memory button.
        val ctx = context /* Activity context */
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        listOf(mem1, mem2, mem3, mem4).forEachIndexed { i, button ->
            val memoryKey = "mem${i + 1}"  // mem1..mem4
            val listener = MemoryButtonEventListener(memoryKey, prefs, inputLayout,
                                                     ctx)
            button.setOnClickListener(listener)
            button.setOnLongClickListener(listener)
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

    private val updateTextFieldButton: ImageButton
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
