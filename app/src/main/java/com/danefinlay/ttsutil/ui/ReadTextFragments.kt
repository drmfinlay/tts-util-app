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
import android.os.Bundle
import android.os.Environment
import android.speech.tts.TextToSpeech.QUEUE_ADD
import android.support.design.widget.TextInputLayout
import android.support.v7.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import com.danefinlay.ttsutil.*
import org.jetbrains.anko.*
import org.jetbrains.anko.support.v4.find
import java.io.File

abstract class ReadTextFragmentBase : MyFragment() {

    protected var mPlaybackOnStart: Boolean = false
    protected val inputLayout: TextInputLayout
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set OnClick listener for common buttons.
        find<ImageButton>(R.id.play_button).onClick { onClickPlay() }
        find<ImageButton>(R.id.save_button).onClick { onClickSave() }
        find<ImageButton>(R.id.stop_button).onClick { myApplication.stopSpeech() }

        // Set status field.
        val event = activityInterface?.getLastStatusUpdate() ?: return
        onStatusUpdate(event)

        // Handle playback on start.
        if (savedInstanceState == null) {
            val intent = activity?.intent
            if (intent?.getBooleanExtra("playbackOnStart", false) == true) {
                mPlaybackOnStart = true
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        // Restore fragment instance state here.
        val content = savedInstanceState?.getString("inputLayoutContent")
        if (content != null) inputLayoutContent = content
        val playbackOnStart = savedInstanceState?.getBoolean("playbackOnStart",
                false)
        if (playbackOnStart != null) mPlaybackOnStart = playbackOnStart
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // Save fragment instance state here.
        if (view != null) {
            outState.putString("inputLayoutContent", inputLayoutContent)
            outState.putBoolean("playbackOnStart", mPlaybackOnStart)
        }
    }

    override fun handleActivityEvent(event: ActivityEvent) {
        super.handleActivityEvent(event)
        when (event) {
            is ActivityEvent.TTSReadyEvent -> {
                // If playback on start was requested, begin playback, since TTS is
                // now ready.
                if (mPlaybackOnStart) {
                    onClickPlay()
                    mPlaybackOnStart = false
                }
            }
            else -> {}
        }
    }

    protected fun onClickPlay() {
        // Retrieve input field text.  If blank, use the hint text
        // and display an alert message.
        var text = inputLayoutContent ?: ""
        if (text.isBlank()) {
            ctx.toast(R.string.cannot_speak_empty_text_msg)
            text = inputLayout.hint?.toString() ?: return
        }

        // Speak *text* and handle the result.
        val result = myApplication.speak(text, QUEUE_ADD)
        myApplication.handleTTSOperationResult(result)
    }

    private fun synthesizeTextToFile(filename: String) {
        // Retrieve input field text.  If blank, display an alert message.
        val text = inputLayoutContent ?: ""
        if (text.isBlank()) {
            ctx.toast(R.string.cannot_speak_empty_text_msg)
            return
        }

        // TODO Handle an already existing wave file.
        // TODO Allow the user to select a custom directory.
        // Synthesize the text into a wave file and handle the result.
        val dir = Environment.getExternalStorageDirectory()
        val file = File(dir, filename)
        val result = myApplication.synthesizeToFile(text, file)
        myApplication.handleTTSOperationResult(result)
    }

    private fun buildSaveWaveFileAlertDialog(filename: String): AlertDialogBuilder {
        val message = getString(R.string.write_to_file_alert_message_2, filename)
        return AlertDialogBuilder(ctx).apply {
            title(R.string.write_to_file_alert_title)
            message(message)
            positiveButton(R.string.alert_positive_message) { synthesizeTextToFile(filename) }
            negativeButton(R.string.alert_negative_message)
        }
    }

    private fun onClickSave() {
        // Return early if TTS is busy or not ready.
        if (!myApplication.ttsReady) {
            myApplication.handleTTSOperationResult(TTS_NOT_READY)
            return
        }
        if (myApplication.readingTaskInProgress) {
            myApplication.handleTTSOperationResult(TTS_BUSY)
            return
        }

        // Show a confirmation dialog to the user.
        // TODO Allow the user to change the filename.
        val filename = getString(R.string.output_wave_filename) + ".wav"
        buildSaveWaveFileAlertDialog(filename).show()
    }

    override fun updateStatusField(text: String) {
        find<TextView>(R.id.status_text_field).text = text
    }

    protected fun attemptPlaybackOnStart() {
        if (!myApplication.ttsReady) {
            myApplication.handleTTSOperationResult(TTS_NOT_READY)
        } else {
            onClickPlay()
            mPlaybackOnStart = false
        }
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

        // Attempt to start playback, if requested.
        if (mPlaybackOnStart) attemptPlaybackOnStart()
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
                activity?.toast(R.string.clipboard_is_empty_msg)
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val ctx = context
        if (savedInstanceState != null || ctx == null) return

        // Set the (initial) content of the input layout.
        // Note: The safety check on *view* is necessary because of how the
        // useClipboardText() function works.
        ctx.useClipboardText(false) { text: String? ->
            if (view != null) {
                inputLayoutContent = text

                // Attempt to start playback, if requested.
                if (mPlaybackOnStart) attemptPlaybackOnStart()
            }
        }
    }
}
