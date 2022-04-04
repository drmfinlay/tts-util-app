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

import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.speech.tts.TextToSpeech.QUEUE_ADD
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import com.danefinlay.ttsutil.*
import org.jetbrains.anko.*
import org.jetbrains.anko.support.v4.find
import java.io.File


abstract class FileChooserFragment : MyFragment() {

    protected var dirChosenEvent: ActivityEvent.ChosenFileEvent? = null
    protected var fileChosenEvent: ActivityEvent.ChosenFileEvent? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set OnClick listener for common buttons.
        find<ImageButton>(R.id.stop_button).onClick { myApplication.stopSpeech() }

        // Re-process last updates.
        val event1 = activityInterface?.getLastStatusUpdate()
        if (event1 != null) onStatusUpdate(event1)
        val event2 = activityInterface?.getLastDirChosenEvent()
        if (event2 != null) onDirChosen(event2)
        val event3 = activityInterface?.getLastFileChosenEvent()
        if (event3 != null) onFileChosen(event3)
    }

    private fun onDirChosen(event: ActivityEvent.ChosenFileEvent) {
        dirChosenEvent = event
        Log.e(TAG, "${event.uri}")
    }

    protected fun onFileChosen(event: ActivityEvent.ChosenFileEvent) {
        // Set the property and display name text field.
        fileChosenEvent = event
        var text = event.displayName
        if (text.isEmpty()) text = getString(R.string.no_file_chosen_dec)
        find<TextView>(R.id.chosen_filename).text = text
    }

    override fun handleActivityEvent(event: ActivityEvent) {
        super.handleActivityEvent(event)
        when (event) {
            is ActivityEvent.ChosenFileEvent -> {
                when (event.fileType) {
                    0 -> onDirChosen(event)
                    1 -> onFileChosen(event)
                }
            }
            else -> {}
        }
    }

    protected fun buildInvalidFileAlertDialog(uri: Uri?): AlertDialogBuilder {
        // Use a different title and message based on whether or not a file has been
        // chosen already.
        val title: Int; val message: Int; val positive: Int; val negative: Int
        if (uri == null) {
            title = R.string.no_file_chosen_dialog_title
            message = R.string.no_file_chosen_dialog_message
            positive = R.string.alert_positive_message_2
            negative = R.string.alert_negative_message_2
        } else {
            title = R.string.invalid_file_dialog_title
            message = R.string.invalid_file_dialog_message
            positive = R.string.alert_positive_message_1
            negative = R.string.alert_negative_message_1
        }
        return AlertDialogBuilder(ctx).apply {
            title(title)
            message(message)
            positiveButton(positive) {
                activityInterface?.showFileChooser()
            }
            negativeButton(negative)
        }
    }
}

class ReadFilesFragment : FileChooserFragment() {

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_read_files, container,
                false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set OnClick listeners.
        find<ImageButton>(R.id.play_file_button).onClick { onClickPlay() }
        find<ImageButton>(R.id.save_button).onClick { onClickSave() }
        find<ImageButton>(R.id.choose_file_button)
                .onClick { activityInterface?.showFileChooser() }
        find<ImageButton>(R.id.choose_dir_button)
                .onClick { activityInterface?.showDirChooser() }
    }

    override fun updateStatusField(text: String) {
        find<TextView>(R.id.status_text_field).text = text
    }

    private fun onClickPlay() {
        // Speak from the chosen file's URI and handle the result.
        val uri = fileChosenEvent?.uri
        when (val result = myApplication.speak(uri, QUEUE_ADD)) {
            INVALID_FILE_URI -> buildInvalidFileAlertDialog(uri).show()
            else -> myApplication.handleTTSOperationResult(result)
        }
    }

    private fun synthesizeTextToFile(uri: Uri?, waveFilename: String,
                                     storageAccess: Boolean) {
        if (!storageAccess) {
            // Show a dialog if we don't have read/write storage permission.
            // and return here if permission is granted.
            val permissionBlock: (Boolean) -> Unit = { granted ->
                if (granted) synthesizeTextToFile(uri, waveFilename, granted)
            }
            buildNoPermissionAlertDialog(permissionBlock).show()
            return
        }

        // TODO Handle an already existing wave file.
        // TODO Allow the user to select a custom directory.
        // Synthesize the file's content into a wave file and handle the result.
        val dir = Environment.getExternalStorageDirectory()
        val file = File(dir, waveFilename)
        when (val result = myApplication.synthesizeToFile(uri, file)) {
            INVALID_FILE_URI -> buildInvalidFileAlertDialog(uri).show()
            else -> myApplication.handleTTSOperationResult(result)
        }
    }

    private fun onClickSave() {
        val event = fileChosenEvent
        val uri = event?.uri
        if (uri?.isAccessibleFile(ctx) != true) {
            buildInvalidFileAlertDialog(uri).show()
            return
        }

        // Return early if TTS is busy or not ready.
        if (!myApplication.ttsReady) {
            myApplication.handleTTSOperationResult(TTS_NOT_READY)
            return
        }
        if (myApplication.readingTaskInProgress) {
            myApplication.handleTTSOperationResult(TTS_BUSY)
            return
        }

        // Build and display an appropriate alert dialog.
        val filename = event.displayName
        val waveFilename = "$filename.wav"
        val message = getString(R.string.write_to_file_alert_message_1,
                filename, waveFilename)
        AlertDialogBuilder(ctx).apply {
            title(R.string.write_to_file_alert_title)
            message(message)
            positiveButton(R.string.alert_positive_message_2) {
                // Ask the user for write permission if necessary.
                withStoragePermission { granted ->
                    synthesizeTextToFile(uri, waveFilename, granted)
                }
            }
            negativeButton(R.string.alert_negative_message_2)

            // Show the dialog.
            show()
        }
    }
}
