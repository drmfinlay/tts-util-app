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

    private val inputLayout: TextInputLayout
        get() = find(R.id.input_layout)

    var inputLayoutContent: String?
        set(value) {
            val text = value ?: ""
            inputLayout.editText?.text?.apply {
                clear()
                append(text)
            }
        }
        get() {
            return inputLayout.editText?.text?.toString()
        }

    private val myActivity: SpeakerActivity
        get() = activity as SpeakerActivity

    private val speaker: Speaker?
        get() = myActivity.speaker

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set OnClick listener for the start/stop speaking buttons.
        find<ImageButton>(R.id.play_button).onClick {
            if (speaker.isReady()) {
                speakFromInputLayout()
            } else {
                // Show the speaker not ready message.
                myActivity.showSpeakerNotReadyMessage()
            }
        }
        find<ImageButton>(R.id.stop_button).onClick {
            speaker?.stopSpeech()
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        // Restore fragment instance state here.
        val content = savedInstanceState?.getString("inputLayoutContent")
        if (content != null) inputLayoutContent = content
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // Save fragment instance state here.
        if (view != null) {
            outState.putString("inputLayoutContent", inputLayoutContent)
        }
    }

    private fun speakFromInputLayout() {
        val content = inputLayoutContent ?: ""
        if (content.isBlank()) {
            context?.toast(R.string.cannot_speak_empty_text_msg)
            speaker?.speak(inputLayout.hint?.toString())
            return
        }

        // Speak text.
        speaker?.speak(content)
    }
}

class ReadTextFragment : ReadTextFragmentBase() {

    /**
     * Event listener for the memory buttons.
     */
    private class MemoryButtonEventListener(val memoryKey: String,
                                            val prefs: SharedPreferences,
                                            val fragment: ReadTextFragmentBase
    ) : View.OnClickListener, View.OnLongClickListener {

        override fun onClick(v: View?) {
            // Set the text content from memory.  If the memory slot is empty,
            // display a message.
            val text = prefs.getString(memoryKey, "")
            if (text.isNullOrEmpty()) {
                fragment.context?.toast(R.string.mem_slot_empty_msg)
            } else {
                fragment.inputLayoutContent = text
            }
        }

        override fun onLongClick(v: View?): Boolean {
            // Store the text field content in memory, displaying an appropriate
            // message.
            val content = fragment.inputLayoutContent ?: ""
            val messageId = if (content.isEmpty()) R.string.mem_slot_cleared_msg
                            else R.string.mem_slot_set_msg
            val editor: SharedPreferences.Editor = prefs.edit()
            editor.putString(memoryKey, content)
            editor.apply()
            fragment.context?.toast(messageId)
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

        // Set OnClick listener for the clear box button.
        find<ImageButton>(R.id.clear_box_button).onClick {
            inputLayoutContent = ""
        }

        // Set OnClick and OnLongClick event listeners for each memory button.
        val ctx = context /* Activity context */
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        listOf(R.id.Memory1, R.id.Memory2, R.id.Memory3, R.id.Memory4)
                .forEachIndexed { i, id ->
                    val button = find<ImageButton>(id)
                    val memoryKey = "mem${i + 1}"  // mem1..mem4
                    val listener = MemoryButtonEventListener(memoryKey, prefs,
                            this)
                    button.setOnClickListener(listener)
                    button.setOnLongClickListener(listener)
        }

        // Handle ACTION_SEND.
        val intent = activity?.intent
        if (savedInstanceState == null && intent?.action == Intent.ACTION_SEND) {
            inputLayoutContent = intent.getStringExtra(Intent.EXTRA_TEXT)
        }
    }
}

class ReadClipboardFragment : ReadTextFragmentBase() {

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

        // Set OnClick listener for the paste button.
        find<ImageButton>(R.id.paste_button).onClick {
            // Get the current clipboard text.
            val text = context?.getClipboardText() ?: ""

            // Update the text field.
            inputLayoutContent = text

            // Display a message if the clipboard was empty.
            if (text.isEmpty()) {
                activity?.toast(R.string.clipboard_is_empty_blank_msg)
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val ctx = context
        if (savedInstanceState == null && ctx != null) {
            val text = ctx.getClipboardText()

            // Android 10 restricts access to the clipboard for privacy reasons.
            // Accessing it from the foreground activity is permitted, but it seems
            // to require a short delay (async).
            if (text == null && Build.VERSION.SDK_INT >= 29) {
                doAsync {
                    Thread.sleep(100)
                    ctx.runOnUiThread {
                        if (this@ReadClipboardFragment.view != null)
                            inputLayoutContent = ctx.getClipboardText()
                    }
                }
            } else {
                // Otherwise just update the text field.
                inputLayoutContent = text
            }
        }
    }
}
