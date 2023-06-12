/*
 * TTS Util
 *
 * Authors: Dane Finlay <dane@danefinlay.net>
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

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.QUEUE_ADD
import android.speech.tts.TextToSpeech.QUEUE_FLUSH
import com.google.android.material.textfield.TextInputLayout
import androidx.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import com.danefinlay.ttsutil.*
import org.jetbrains.anko.*

abstract class ReadTextFragmentBase : MyFragment() {

    val inputLayout: TextInputLayout
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

    protected val inputTextReader: InputTextReader = object : InputTextReader() {
        override fun readChangedText(text: CharSequence) {
            // Refuse to read text if a file synthesis task is in progress.
            val application = myApplication
            if (application.fileSynthesisTaskInProgress) return

            // Enqueue the specified text with QUEUE_FLUSH so it is read
            // immediately.
            val inputSource = InputSource.CharSequence(text, textSourceDescription)
            val result = application.enqueueReadInputTask(inputSource, QUEUE_FLUSH)

            // Initialize TTS, if necessary.
            if (result != TTS_NOT_READY) return
            activityInterface?.initializeTTS { status ->
                if (status == TextToSpeech.SUCCESS) {
                    application.enqueueReadInputTask(inputSource, QUEUE_FLUSH)
                }
            }
        }
    }

    protected class ScrollingEditTextOnTouchListener(val editText: EditText) :
            View.OnTouchListener {
        // Note:
        //
        //  The following accessibility warning is suppressed, here and later in
        //  this file.  I have done this because the purported "solution" requires
        //  sub-classing, (and performing some arcane magic with,) every EditView
        //  class used with this listener.  I find this "solution" unacceptable.
        //  It is unreasonable to expect developers using the View.OnTouchListener
        //  interface to go out of their way to handle something that should be
        //  handled by the system itself.
        //
        //  This may be revisited down the line if there is significant demand.
        //  In that event, I'd rather just add an option for disabling use of this
        //  listener.
        //
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            // If *view* is *editText*, allow it to intercept and process events,
            // rather than the parent (or other ancestors).  This allows the user
            // to scroll the EditText view independently, without scrolling the
            // whole screen.
            if (view.id == editText.id) {
                val parent = view.parent
                parent.requestDisallowInterceptTouchEvent(true)
                val action = event.action and MotionEvent.ACTION_MASK
                if (action == MotionEvent.ACTION_UP) {
                    parent.requestDisallowInterceptTouchEvent(false)
                }
            }
            return false
        }
    }

    protected var playbackOnStart: Boolean = false
    protected var playbackOnInput: Boolean = false
    protected abstract val textSourceDescription: String

    protected abstract fun initializeInputField()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activityInterface = activityInterface

        // Set OnClick listener for common buttons.
        find<ImageButton>(R.id.play_button).onClick { onClickPlay() }
        find<ImageButton>(R.id.save_button).onClick { onClickSave() }
        find<ImageButton>(R.id.stop_button).onClick { myApplication.stopSpeech() }

        // Set the choose directory button's OnClick listener.  Choosing the output
        // directory is not possible on versions older than Android Lollipop (21).
        find<ImageButton>(R.id.choose_dir_button).onClick {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                activityInterface?.showDirChooser(DIR_SELECT_CODE)
            } else {
                ctx.longToast(R.string.sdk_18_choose_dir_message)
            }
        }

        // Re-process last updates.
        val event = activityInterface?.getLastStatusUpdate()
        if (event != null) onStatusUpdate(event)

        // Set the task count field.
        updateTaskCountField(myApplication.taskCount)

        // Read and set common values.
        if (savedInstanceState == null) {
            val intent = activity?.intent
            if (intent != null) {
                playbackOnStart = intent.getBooleanExtra("playbackOnStart", false)
            }
            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
            playbackOnInput = prefs.getBoolean("pref_playback_on_input", false)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        // Initialize the input field here.
        if (savedInstanceState == null) { initializeInputField(); return; }

        // Restore fragment instance state here.
        inputLayoutContent = savedInstanceState.getString("inputLayoutContent")
        playbackOnStart = savedInstanceState.getBoolean("playbackOnStart", false)
        playbackOnInput = savedInstanceState.getBoolean("playbackOnInput", false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // Save fragment instance state here.
        if (view != null) {
            outState.putString("inputLayoutContent", inputLayoutContent)
            outState.putBoolean("playbackOnStart", playbackOnStart)
            outState.putBoolean("playbackOnInput", playbackOnInput)
        }
    }

    private fun onDirChosen(event: ActivityEvent.ChosenFileEvent) {
        // Return if this callback is not for continuing a previous request after a
        // valid directory has been chosen.
        if (event.requestCode != DIR_SELECT_CONT_CODE) return

        // Ensure this is only done once.
        event.requestCode = DIR_SELECT_CODE

        // Set the output wave filename.
        // TODO Allow the user to change the filename.
        val waveFilename = getString(R.string.output_wave_filename) + ".wav"

        // Attempt to start file synthesis, asking the user for write permission if
        // necessary.
        val directory = Directory.DocumentFile(event.firstUri)
        withStoragePermission { granted ->
            synthesizeTextToFile(waveFilename, directory, granted)
        }
    }

    override fun handleActivityEvent(event: ActivityEvent) {
        super.handleActivityEvent(event)
        when (event) {
            is ActivityEvent.TTSReadyEvent -> {
                // If playback on start was requested, begin playback, since TTS is
                // now ready.
                if (playbackOnStart) attemptPlaybackOnStart()
            }
            is ActivityEvent.ChosenFileEvent -> {
                val code = event.requestCode
                if (code == DIR_SELECT_CODE || code == DIR_SELECT_CONT_CODE) {
                    onDirChosen(event)
                }
            }
            else -> {}
        }
    }

    override fun onClickPlay() {
        super.onClickPlay()

        // Retrieve the input field text.  If the field is empty, use the hint
        // text instead.
        var text: String? = inputLayoutContent
        if (text == null || text.length == 0) {
            text = inputLayout.hint?.toString() ?: ""
        }

        // Read text and handle the result.
        val inputSource = InputSource.CharSequence(text, textSourceDescription)
        val result = myApplication.enqueueReadInputTask(inputSource, QUEUE_ADD)
        myApplication.handleTaskResult(result)
    }

    private fun synthesizeTextToFile(waveFilename: String, directory: Directory,
                                     storageAccess: Boolean) {
        if (!storageAccess) {
            // Show a dialog if we don't have read/write storage permission.
            // and return here if permission is granted.
            val permissionBlock: (Boolean) -> Unit = { granted ->
                if (granted) synthesizeTextToFile(waveFilename, directory, granted)
            }
            buildNoPermissionAlertDialog(permissionBlock).show()
            return
        }

        // Retrieve input field text, begin synthesizing it into a wave file and
        // handle the result.
        val text = inputLayoutContent ?: ""
        val inputSource = InputSource.CharSequence(text, textSourceDescription)
        val result = myApplication.enqueueFileSynthesisTasks(
                inputSource, directory, waveFilename
        )
        when (result) {
            UNAVAILABLE_OUT_DIR -> buildUnavailableDirAlertDialog().show()
            UNWRITABLE_OUT_DIR -> buildUnwritableOutDirAlertDialog().show()
            else -> myApplication.handleTaskResult(result)
        }
    }

    override fun onClickSave() {
        super.onClickSave()

        // Determine the output directory.  If the user has not chosen one, the
        // "external" storage is used.
        val event = activityInterface?.getLastDirChosenEvent()
        val directory: Directory = if (event != null) {
            Directory.DocumentFile(event.firstUri)
        } else {
            @Suppress("deprecation")
            Directory.File(Environment.getExternalStorageDirectory())
        }

        // Determine the names of the wave file and directory.
        // TODO Allow the user to change the filename.
        val waveFilename = getString(R.string.output_wave_filename) + ".wav"
        val dirDisplayName: String = event?.firstDisplayName
                ?: getString(R.string.default_output_dir)

        // Build and display an appropriate alert dialog.
        AlertDialogBuilder(ctx).apply {
            title(R.string.write_to_file_alert_title)
            message(getString(R.string.write_to_file_alert_message_3,
                    waveFilename, dirDisplayName))
            positiveButton(R.string.alert_positive_message_2) {
                // Ask the user for write permission if necessary.
                withStoragePermission { granted ->
                    synthesizeTextToFile(waveFilename, directory, granted)
                }
            }
            negativeButton(R.string.alert_negative_message_2)

            // Show the dialog.
            show()
        }
    }

    override fun updateStatusField(text: String) {
        find<TextView>(R.id.status_text_field).text = text
    }

    override fun updateTaskCountField(count: Int) {
        val text = getString(R.string.remaining_tasks_field, count)
        find<TextView>(R.id.remaining_tasks_field).text = text
    }

    protected fun attemptPlaybackOnStart() {
        if (myApplication.mTTS == null) {
            myApplication.handleTaskResult(TTS_NOT_READY)
        } else {
            onClickPlay()
            playbackOnStart = false
        }
    }
}

class ReadTextFragment : ReadTextFragmentBase() {

    /**
     * Event listener for the memory buttons.
     */
    private class MemoryButtonEventListener(val ctx: Context,
                                            val memoryKey: String,
                                            val fragment: ReadTextFragmentBase
    ) : View.OnClickListener, View.OnLongClickListener {

        override fun onClick(v: View?) {
            // Set the text content from memory.  If the memory slot is empty,
            // display a message.
            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
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
            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
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

    private var persistentContent: Boolean = false

    override val textSourceDescription: String
        get() = getString(R.string.read_text_source_description)

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
        val memoryButtons = listOf(R.id.Memory1, R.id.Memory2, R.id.Memory3,
                R.id.Memory4, R.id.Memory5)
        memoryButtons.forEachIndexed { i, id ->
            val button = find<ImageButton>(id)
            val memoryKey = "mem${i + 1}"  // mem1..mem4
            val listener = MemoryButtonEventListener(ctx!!, memoryKey, this)
            button.setOnClickListener(listener)
            button.setOnLongClickListener(listener)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun initializeInputField() {
        // Restore persistent data as necessary.
        // The content of the input field is set to persist unless ACTION_SEND is
        // specified.
        val intent = activity?.intent
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        if (intent?.action == Intent.ACTION_SEND) {
            persistentContent = false
            inputLayoutContent = intent.getStringExtra(Intent.EXTRA_TEXT)
        } else {
            persistentContent = true
            inputLayoutContent = prefs.getString(CONTENT_PREF_KEY, "")
        }

        // Set the OnTouchListener that enables scrolling the EditText view.
        val editText = inputLayout.editText
        editText?.setOnTouchListener(ScrollingEditTextOnTouchListener(editText))

        // Enable the playback-on-input feature, if appropriate.
        // Note: Since this feature uses a TextWatcher it should only be enabled
        // after restoration of the previous input field content.
        if (playbackOnInput) {
            inputLayout.editText?.addTextChangedListener(inputTextReader)
        }

        // Attempt to start playback, if requested.
        if (playbackOnStart) attemptPlaybackOnStart()

    }

    override fun onPause() {
        super.onPause()
        if (view == null) return

        // If the content of the fragment's input layout should persist, save it to
        // shared preferences.
        val ctx = context
        if (persistentContent && ctx != null) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
            val editor: SharedPreferences.Editor = prefs.edit()
            editor.putString(CONTENT_PREF_KEY, inputLayoutContent)
            editor.apply()
        }
    }

    companion object {
        private const val CONTENT_PREF_KEY = "$APP_NAME.READ_TEXT_CONTENT"
    }
}

class ReadClipboardFragment : ReadTextFragmentBase() {

    override val textSourceDescription: String
        get() = getString(R.string.read_clipboard_source_description)

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
        find<ImageButton>(R.id.paste_button).onClick { onClickPaste() }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun initializeInputField() {
        // Set the (initial) content of the input layout.
        // Note: The safety check on *view* is necessary because of how the
        // useClipboardText() function works.
        ctx.useClipboardText(true) { text: String? ->
            if (view != null) onClipboardTextReceived(text)
        }

        // Set the OnTouchListener that enables scrolling the EditText view.
        val editText = inputLayout.editText
        editText?.setOnTouchListener(ScrollingEditTextOnTouchListener(editText))

        // Enable the playback-on-input feature, if appropriate.
        // Note: Since this feature uses a TextWatcher it should only be
        // enabled after the input field content is initialized.
        if (playbackOnInput) {
            inputLayout.editText?.addTextChangedListener(inputTextReader)
        }
    }

    private fun onClipboardTextReceived(text: String?) {
        // Set the input field content.
        inputLayoutContent = text

        // Attempt to start playback, if requested.
        if (playbackOnStart) attemptPlaybackOnStart()
    }

    private fun onClickPaste() {
        // Get the current clipboard text.
        val text = context?.getClipboardText() ?: ""

        // Update the text field.
        inputLayoutContent = text

        // Display a message if the clipboard was empty.
        if (text.length == 0) activity?.toast(R.string.clipboard_is_empty_msg)
    }
}
