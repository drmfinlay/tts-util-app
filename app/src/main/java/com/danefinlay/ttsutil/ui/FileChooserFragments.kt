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
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.danefinlay.ttsutil.*
import com.danefinlay.ttsutil.ui.MainActivity.Companion.CHOSEN_FILE_NAME_KEY
import org.jetbrains.anko.AlertDialogBuilder
import org.jetbrains.anko.onClick
import org.jetbrains.anko.runOnUiThread
import org.jetbrains.anko.support.v4.find
import org.jetbrains.anko.toast
import java.io.File


typealias PermissionBlock = (granted: Boolean) -> Unit

abstract class FileChooserFragment : Fragment(), FileChooser {

    override var chooseFileAction = Intent.ACTION_OPEN_DOCUMENT
    override var chooseFileCategory = Intent.CATEGORY_OPENABLE
    override var chooseFileMimeType = "text/*"

    protected val myActivity: MainActivity
        get() = (activity as MainActivity)

    protected val fileToRead: Uri?
        get() = myActivity.fileToRead

    protected val ctx: Context
        get() = myActivity

    private var tempStoragePermissionBlock: PermissionBlock = {}

    protected val speaker: Speaker?
        get() = myActivity.speaker

    private val chosenFilename: TextView
        get() = find(R.id.chosen_filename)

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super<FileChooser>.onActivityResult(requestCode, resultCode, data)
        super<Fragment>.onActivityResult(requestCode, resultCode, data)
    }

    private fun setFileDisplayName() {
        // Set chosenFilename.text to the file's display name if possible.
        val uri = fileToRead
        val noFileChosen = getString(R.string.no_file_chosen)
        val displayName = if (uri == null || uri.toString() == "") {
            noFileChosen
        } else if (!uri.validFilePath(ctx)) {
            // Fallback on the last display name if the path isn't valid.
            val prefs = ctx.getSharedPreferences(ctx.packageName, MODE_PRIVATE)
            prefs.getString(CHOSEN_FILE_NAME_KEY, "") ?: noFileChosen
        } else {
            uri.getDisplayName(ctx) ?: noFileChosen
        }

        // Set display name.
        chosenFilename.text = displayName
    }

    override fun onResume() {
        super.onResume()

        // Set the file display name. onResume() can be called after
        // the user picks a file.
        setFileDisplayName()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        // Set/restore the file display name.
        setFileDisplayName()
    }

    override fun onFileChosen(uri: Uri?) { /* FIXME Not called for Fragments. */ }

    // Functions related to choosing files.
    protected fun buildInvalidFileAlertDialog(): AlertDialogBuilder {
        return AlertDialogBuilder(ctx).apply {
            title(R.string.invalid_file_dialog_title)
            message(R.string.invalid_file_dialog_message)
            positiveButton(R.string.alert_positive_message) {
                myActivity.showFileChooser()
            }
            negativeButton(R.string.alert_negative_message1)
        }
    }

    protected fun buildNoPermissionAlertDialog(): AlertDialogBuilder {
        return AlertDialogBuilder(ctx).apply {
            title(R.string.no_storage_permission_title)
            message(R.string.no_storage_permission_message)
            positiveButton(R.string.give_permission_message) {
                // Try asking for storage permission again.
                withStoragePermission {}
            }
            negativeButton(R.string.alert_negative_message1)
        }
    }

    fun withStoragePermission(block: PermissionBlock) {
        // Check if we have write permission.
        if (Build.VERSION.SDK_INT >= 23) {
            val permission = ctx.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // We don't have permission so prompt the user
                requestPermissions(
                        PERMISSIONS_STORAGE,
                        REQUEST_EXTERNAL_STORAGE
                )

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

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<out String>,
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

    companion object {
        // Storage Permissions
        private const val REQUEST_EXTERNAL_STORAGE = 6
        private val PERMISSIONS_STORAGE = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }
}

class ReadFilesFragment : FileChooserFragment() {
    private val chooseFileButton: Button
        get() = find(R.id.choose_file_button)

    private val readFileButton: Button
        get() = find(R.id.read_file_button)

    private val stopSpeakingButton: Button
        get() = find(R.id.stop_speaking_button)

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

        chooseFileButton.onClick { activity?.showFileChooser() }
        readFileButton.onClick { onClickReadFile() }
        stopSpeakingButton.onClick { speaker?.stopSpeech() }
    }

    private fun onClickReadFile() {
        // Show the speaker not ready message if appropriate.
        if (!speaker.isReady()) {
            myActivity.showSpeakerNotReadyMessage()
            return
        }

        val fileToRead = fileToRead
        when (fileToRead?.validFilePath(ctx)) {
            true -> fileToRead.getContent(ctx)?.reader()?.forEachLine {
                speaker?.speak(it)
            }
            false -> buildInvalidFileAlertDialog().show()
            else -> activity?.toast(R.string.no_file_chosen2)
        }

    }
}

class WriteFilesFragment : FileChooserFragment() {

    private val chooseFileButton: Button
        get() = find(R.id.choose_file_button)

    private val writeToFileButton: Button
        get() = find(R.id.write_to_file_button)

    private val stopSynthesisButton: Button
        get() = find(R.id.stop_synthesis_button)

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
        writeToFileButton.onClick { onClickWriteFile() }
        chooseFileButton.onClick { showFileChooser() }
        stopSynthesisButton.onClick { speaker?.stopSpeech() }
    }

    private fun writeSpeechToFile(uri: Uri?, havePermission: Boolean) {
        if (!havePermission) {
            // Show a dialog if we don't have read/write storage permission
            ctx.runOnUiThread { buildNoPermissionAlertDialog().show() }
            return
        }

        // Validate before continuing.
        if (uri == null || !uri.validFilePath(ctx)) {
            ctx.runOnUiThread {buildInvalidFileAlertDialog().show()}
            return
        }

        val content = uri.getContent(ctx)?.reader()?.readText()
        if (content.isNullOrBlank()) {
            // Nothing to read.
            return
        }

        // Save the file in the external storage directory using the filename
        // + '.wav'.
        val dir = Environment.getExternalStorageDirectory()
        val filename = "${uri.getDisplayName(ctx)}.wav"
        val file = File(dir, filename)
        val listener = SynthesisEventListener(myActivity.myApplication, filename,
                ctx, file)
        speaker?.synthesizeToFile(content, listener)
    }

    private fun onClickWriteFile() {
        val uri = fileToRead
        when (uri?.validFilePath(ctx)) {
            false -> {
                buildInvalidFileAlertDialog().show()
                return
            }
            null -> {
                activity?.toast(R.string.no_file_chosen2)
                return
            }
        }

        // Show the speaker not ready message if appropriate.
        if (!speaker.isReady()) {
            myActivity.showSpeakerNotReadyMessage()
            return
        }

        // Build and display an appropriate alert dialog.
        val msgPart1 = getString(R.string.write_to_file_alert_message_p1)
        val msgPart2 = getString(R.string.write_to_file_alert_message_p2)
        val filename = "${uri?.getDisplayName(ctx)}"
        val fullMsg = "$msgPart1 \"$filename\"\n" +
                "\n$msgPart2 \"$filename.wav\""
        AlertDialogBuilder(ctx).apply {
            title(R.string.write_files_fragment_label)
            message(fullMsg)
            positiveButton(R.string.alert_positive_message) {
                // Ask the user for write permission if necessary.
                withStoragePermission { havePermission ->
                    writeSpeechToFile(uri, havePermission)
                }
            }
            negativeButton(R.string.alert_negative_message2)

            // Show the dialog.
            show()
        }
    }
}
