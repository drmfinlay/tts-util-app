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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import com.danefinlay.ttsutil.*
import org.jetbrains.anko.*
import org.jetbrains.anko.support.v4.find


abstract class FileChooserFragment : MyFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set OnClick listener for common buttons.
        find<ImageButton>(R.id.stop_button).onClick { myApplication.stopSpeech() }

        // Re-process last updates.
        val event1 = activityInterface?.getLastStatusUpdate()
        if (event1 != null) onStatusUpdate(event1)
        val event2 = activityInterface?.getLastFileChosenEvent()
        if (event2 != null) onFileChosen(event2)
    }

    protected fun onFileChosen(event: ActivityEvent.ChosenFileEvent) {
        // Set the property and display name text field.
        var text = event.firstDisplayName
        if (text.isEmpty()) text = getString(R.string.no_file_chosen_dec)
        else if (event.displayNameList.size > 1) {
            val fileCount = event.displayNameList.size - 1
            val fileWord = resources.getQuantityString(R.plurals.files, fileCount)
            text = getString(R.string.multiple_chosen_files, text, fileCount,
                    fileWord)
        }
        find<TextView>(R.id.chosen_filename).text = text
    }

    override fun handleActivityEvent(event: ActivityEvent) {
        super.handleActivityEvent(event)
        when (event) {
            is ActivityEvent.ChosenFileEvent -> {
                if (event.requestCode == FILE_SELECT_CODE) onFileChosen(event)
            }
            else -> {}
        }
    }

    protected fun buildUnavailableFileAlertDialog(uriList: List<Uri>?):
            AlertDialogBuilder {
        // Use a different title and message based on whether or not a file has been
        // chosen already.
        val title: Int; val message: Int; val positive: Int; val negative: Int
        if (uriList == null || uriList.size == 0) {
            title = R.string.no_file_chosen_dialog_title
            message = R.string.no_file_chosen_dialog_message
            positive = R.string.alert_positive_message_2
            negative = R.string.alert_negative_message_2
        } else {
            title = R.string.unavailable_file_dialog_title
            if (uriList.size == 1) message = R.string.unavailable_file_dialog_message_1
            else message = R.string.unavailable_file_dialog_message_2
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
                .onClick { activityInterface?.showDirChooser(DIR_SELECT_CODE) }
    }

    override fun updateStatusField(text: String) {
        find<TextView>(R.id.status_text_field).text = text
    }

    override fun updateTaskCountField(count: Int) {
        val text = getString(R.string.remaining_tasks_field, count)
        find<TextView>(R.id.remaining_tasks_field).text = text
    }

    private fun onDirChosen(event: ActivityEvent.ChosenFileEvent) {
        // Return if this callback is not for continuing a previous request after a
        // valid directory has been chosen.
        if (event.requestCode != DIR_SELECT_CONT_CODE) return

        // Ensure this is only done once.
        event.requestCode = DIR_SELECT_CODE

        // Attempt to start file synthesis, asking the user for write permission if
        // necessary.
        val chosenFileEvent = activityInterface?.getLastFileChosenEvent() ?: return
        val directory = Directory.DocumentFile(event.firstUri)
        withStoragePermission { granted ->
            synthesizeTextToFile(chosenFileEvent, directory, granted)
        }
    }

    override fun handleActivityEvent(event: ActivityEvent) {
        super.handleActivityEvent(event)
        if (event is ActivityEvent.ChosenFileEvent) {
            val code = event.requestCode
            if (code == DIR_SELECT_CODE || code == DIR_SELECT_CONT_CODE) {
                onDirChosen(event)
            }
        }
    }

    override fun onClickPlay() {
        super.onClickPlay()

        // Start reading from the chosen files in order, stopping on failure.
        val event = activityInterface?.getLastFileChosenEvent()
        val uriList = event?.uriList
        if (uriList == null || uriList.size == 0) {
            buildUnavailableFileAlertDialog(listOf()).show()
        } else for ((uri, displayName) in uriList.zip(event.displayNameList)) {
            val inputSource = InputSource.ContentUri(uri, displayName)
            val result = myApplication.enqueueReadInputTask(inputSource, QUEUE_ADD)
            when (result) {
                UNAVAILABLE_INPUT_SRC ->
                    buildUnavailableFileAlertDialog(uriList).show()
                else -> myApplication.handleTTSOperationResult(result)
            }
            if (result != SUCCESS) break
        }
    }

    private fun synthesizeTextToFile(event: ActivityEvent.ChosenFileEvent,
                                     directory: Directory, storageAccess: Boolean) {
        if (!storageAccess) {
            // Show a dialog if we don't have read/write storage permission.
            // and return here if permission is granted.
            val permissionBlock: (Boolean) -> Unit = { granted ->
                if (granted) {
                    synthesizeTextToFile(event, directory, granted)
                }
            }
            buildNoPermissionAlertDialog(permissionBlock).show()
            return
        }

        // Start synthesizing from the chosen files in order, stopping on failure.
        val fileData = event.uriList.zip(event.displayNameList)
        if (fileData.size == 0) {
            buildUnavailableFileAlertDialog(event.uriList).show()
        } else for ((uri, displayName) in fileData) {
            val waveFilename = "$displayName.wav"
            val inputSource = InputSource.ContentUri(uri, displayName)
            val result = myApplication.enqueueFileSynthesisTasks(inputSource,
                    directory, waveFilename)
            when (result) {
                UNAVAILABLE_INPUT_SRC ->
                    buildUnavailableFileAlertDialog(event.uriList).show()
                UNAVAILABLE_OUT_DIR -> buildUnavailableDirAlertDialog().show()
                else -> myApplication.handleTTSOperationResult(result)
            }
            if (result != SUCCESS) break
        }
    }

    override fun onClickSave() {
        super.onClickSave()

        // Verify that the chosen file can be read.  If it cannot, inform the user
        // by showing an appropriate dialog.
        val event1 = activityInterface?.getLastFileChosenEvent()
        val fileUri = event1?.firstUri
        if (fileUri?.isAccessibleFile(ctx) != true) {
            val uriList = if (fileUri == null) null else listOf(fileUri)
            buildUnavailableFileAlertDialog(uriList).show()
            return
        }

        // Determine the output directory.  If the user has not chosen one, the
        // "external" storage is used.
        val event2 = activityInterface?.getLastDirChosenEvent()
        val directory: Directory = if (event2 != null) {
            Directory.DocumentFile(event2.firstUri)
        } else {
            Directory.File(Environment.getExternalStorageDirectory())
        }

        // Determine the names of the wave file and directory.
        val filename = event1.firstDisplayName
        val dirDisplayName: String = event2?.firstDisplayName
                ?: getString(R.string.default_output_dir)

        // Build and display an appropriate alert dialog.
        AlertDialogBuilder(ctx).apply {
            title(R.string.write_to_file_alert_title)
            if (event1.displayNameList.size == 1) {
                val waveFilename = "$filename.wav"
                message(getString(R.string.write_to_file_alert_message_1,
                        filename, waveFilename, dirDisplayName))
            } else {
                val fileCount = event1.displayNameList.size - 1
                val fileWord = resources.getQuantityString(R.plurals.files,
                        fileCount)
                message(getString(R.string.write_to_file_alert_message_2,
                        filename, fileCount, fileWord, dirDisplayName))
            }
            positiveButton(R.string.alert_positive_message_2) {
                // Ask the user for write permission if necessary.
                withStoragePermission { granted ->
                    synthesizeTextToFile(event1, directory, granted)
                }
            }
            negativeButton(R.string.alert_negative_message_2)

            // Show the dialog.
            show()
        }
    }
}
