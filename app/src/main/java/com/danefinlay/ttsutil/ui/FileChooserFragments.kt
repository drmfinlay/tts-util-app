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

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import java.io.File

typealias PermissionBlock = (granted: Boolean) -> Unit

abstract class FileChooserFragment : MyFragment() {

    protected var fileChosenEvent: ActivityEvent.FileChosenEvent? = null
    private var tempStoragePermissionBlock: PermissionBlock = {}

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set OnClick listener for common buttons.
        find<ImageButton>(R.id.stop_button).onClick { myApplication.stopSpeech() }

        // Set text fields.
        val event1 = activityInterface?.getLastStatusUpdate()
        if (event1 != null) onStatusUpdate(event1)
        val event2 = activityInterface?.getLastFileChosenEvent()
        if (event2 != null) onFileChosen(event2)
    }

    fun withStoragePermission(block: PermissionBlock) {
        // Check if we have write permission.
        if (Build.VERSION.SDK_INT >= 23) {
            val permission = ctx.checkSelfPermission(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // We don't have permission, so prompt the user.
                requestPermissions(PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE)

                // Store the function so we can execute it later if the user
                // grants us storage permission.
                tempStoragePermissionBlock = block
            }
            else {
                // We have permission, so execute the function.
                block(true)
            }
        } else {
            // No need to check permission before Android 23, so execute the
            // function.
            block(true)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<out String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (permissions.contentEquals(PERMISSIONS_STORAGE)) {
            // Check that all permissions were granted.
            var allGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    // Permission wasn't granted.
                    allGranted = false
                    break
                }
            }

            // Execute the storage permission block and replace it.
            tempStoragePermissionBlock(allGranted)
            tempStoragePermissionBlock = {}
        }
    }

    protected fun onFileChosen(event: ActivityEvent.FileChosenEvent) {
        // Set the property and display name text field.
        fileChosenEvent = event
        var text = event.displayName
        if (text.isEmpty()) text = getString(R.string.no_file_chosen_dec)
        find<TextView>(R.id.chosen_filename).text = text
    }

    override fun handleActivityEvent(event: ActivityEvent) {
        super.handleActivityEvent(event)
        when (event) {
            is ActivityEvent.FileChosenEvent -> onFileChosen(event)
            else -> {}
        }
    }

    protected fun buildInvalidFileAlertDialog(uri: Uri?): AlertDialogBuilder {
        // Use a different title and message based on whether or not a file has been
        // chosen already.
        val title: Int
        val message: Int
        if (uri == null) {
            title = R.string.no_file_chosen_dialog_title
            message = R.string.no_file_chosen_dialog_message
        } else {
            title = R.string.invalid_file_dialog_title
            message = R.string.invalid_file_dialog_message
        }
        return AlertDialogBuilder(ctx).apply {
            title(title)
            message(message)
            positiveButton(R.string.alert_positive_message) {
                activityInterface?.showFileChooser()
            }
            negativeButton(R.string.alert_negative_message)
        }
    }

    protected fun buildNoPermissionAlertDialog(block: PermissionBlock):
            AlertDialogBuilder {
        return AlertDialogBuilder(ctx).apply {
            title(R.string.no_storage_permission_title)
            message(R.string.no_storage_permission_message)
            positiveButton(R.string.grant_permission_positive_message) {
                // Try asking for storage permission again.
                withStoragePermission { havePermission -> block(havePermission) }
            }
            negativeButton(R.string.alert_negative_message)
        }
    }

    companion object {
        // Storage Permissions
        private val PERMISSIONS_STORAGE = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
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
        find<ImageButton>(R.id.choose_file_button)
                .onClick { activityInterface?.showFileChooser() }
        find<ImageButton>(R.id.play_file_button).onClick {
            onClickPlay()
        }
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
}

class WriteFilesFragment : FileChooserFragment() {

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_write_files, container,
                false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        find<ImageButton>(R.id.save_button).onClick { onClickSave() }
        find<ImageButton>(R.id.choose_file_button)
                .onClick { activityInterface?.showFileChooser() }
    }

    override fun updateStatusField(text: String) {
        find<TextView>(R.id.status_text_field).text = text
    }

    private fun writeSpeechToFile(uri: Uri?, havePermission: Boolean,
                                  waveFilename: String) {
        if (!havePermission) {
            // Show a dialog if we don't have read/write storage permission.
            // and return here if permission is granted.
            val permissionBlock = { havePermission2: Boolean ->
                if (havePermission2) {
                    writeSpeechToFile(uri, havePermission2, waveFilename)
                }
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
            title(R.string.write_files_fragment_label)
            message(message)
            positiveButton(R.string.alert_positive_message) {
                // Ask the user for write permission if necessary.
                withStoragePermission { havePermission ->
                    writeSpeechToFile(uri, havePermission, waveFilename)
                }
            }
            negativeButton(R.string.alert_negative_message)

            // Show the dialog.
            show()
        }
    }
}
