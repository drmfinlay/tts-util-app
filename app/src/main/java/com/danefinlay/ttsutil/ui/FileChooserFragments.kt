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
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
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

abstract class FileChooserFragment : Fragment(), FragmentInterface {

    protected var chosenFileUri: Uri? = null
    protected var chosenFileDisplayName: String? = null
    private var tempStoragePermissionBlock: PermissionBlock = {}

    protected val speakerActivity: SpeakerActivity?
        get() = activity as? SpeakerActivity

    protected val ctx: Context
        get() = activity as Context

    protected val speaker: Speaker?
        get() = speakerActivity?.speaker

    protected val activityInterface: ActivityInterface?
        get() = context as? ActivityInterface

    fun withStoragePermission(block: PermissionBlock) {
        // Check if we have write permission.
        if (Build.VERSION.SDK_INT >= 23) {
            val permission = ctx.checkSelfPermission(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // We don't have permission, so prompt the user.
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

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        // Attach to the activity interface, if possible.
        val activityInterface = context as? ActivityInterface
        activityInterface?.attachFragment(this)
    }

    override fun onDetach() {
        super.onDetach()

        // Detach to the activity interface, if necessary.
        val activityInterface = context as? ActivityInterface
        activityInterface?.detachFragment(this)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        // Restore fragment instance state here.
        // Retrieve the previous chosen file URI and display name, if any.
        val uriKey = CHOSEN_FILE_URI_KEY
        val displayNameKey = CHOSEN_FILE_NAME_KEY
        val uri: Uri?
        if (savedInstanceState != null) {
            uri = savedInstanceState.getParcelable(uriKey) as? Uri
            chosenFileDisplayName = savedInstanceState.getString(displayNameKey)
        } else {
            val prefs = ctx.getSharedPreferences(ctx.packageName,
                    AppCompatActivity.MODE_PRIVATE)
            val uriString = prefs.getString(uriKey, null)
            uri = if (uriString != null) Uri.parse(uriString) else null
            chosenFileDisplayName = prefs.getString(displayNameKey, null)
        }

        // Set chosenFileUri if the Uri is acceptable.
        if (uri != null) {
            chosenFileUri = uri
        }

        // Set the chosen file name text field.
        setChosenFilenameText(chosenFileDisplayName)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // Save fragment instance state here.
        if (view != null) {
            outState.putParcelable(CHOSEN_FILE_URI_KEY, chosenFileUri)
            outState.putString(CHOSEN_FILE_NAME_KEY, chosenFileDisplayName)
        }
    }

    private fun setChosenFilenameText(displayName: String?) {
        val text = if (displayName.isNullOrEmpty()) {
            getString(R.string.no_file_chosen_dec)
        } else displayName
        find<TextView>(R.id.chosen_filename).text = text
    }

    override fun onActivityEvent(event: ActivityEvent) {
        when (event) {
            is ActivityEvent.FileChosenEvent -> onFileChosen(event.uri)
        }
    }

    private fun onFileChosen(uri: Uri) {
        // Retrieve the display name of the chosen file, if possible.
        val displayName = if (uri.isAccessibleFile(ctx)) {
            uri.retrieveFileDisplayName(ctx)
        } else null

        // Set the chosen filename text field.
        setChosenFilenameText(displayName)

        // Set property and shared preference values.
        chosenFileUri = uri
        chosenFileDisplayName = displayName
        val prefs = ctx.getSharedPreferences(ctx.packageName,
                AppCompatActivity.MODE_PRIVATE)
        prefs.edit()
                .putString(CHOSEN_FILE_URI_KEY, uri.toString())
                .putString(CHOSEN_FILE_NAME_KEY, displayName)
                .apply()  // apply() is asynchronous.
    }

    protected fun buildInvalidFileAlertDialog(): AlertDialogBuilder {
        // Use a different title and message based on whether or not a file has been
        // chosen already.
        val title =   if (chosenFileUri == null) R.string.no_file_chosen_dialog_title
                      else R.string.invalid_file_dialog_title
        val message = if (chosenFileUri == null) R.string.no_file_chosen_dialog_message
                      else R.string.invalid_file_dialog_message

        // Initialise and return a builder.
        return AlertDialogBuilder(ctx).apply {
            title(title)
            message(message)
            positiveButton(R.string.alert_positive_message) {
                activityInterface?.showFileChooser()
            }
            negativeButton(R.string.alert_negative_message1)
        }
    }

    protected fun buildNoPermissionAlertDialog(block: PermissionBlock):
            AlertDialogBuilder {
        return AlertDialogBuilder(ctx).apply {
            title(R.string.no_storage_permission_title)
            message(R.string.no_storage_permission_message)
            positiveButton(R.string.grant_permission_message) {
                // Try asking for storage permission again.
                withStoragePermission { havePermission -> block(havePermission) }
            }
            negativeButton(R.string.alert_negative_message1)
        }
    }

    companion object {
        // Shared pref keys.
        private const val CHOSEN_FILE_URI_KEY = "$APP_NAME.CHOSEN_FILE_URI_KEY"
        private const val CHOSEN_FILE_NAME_KEY = "$APP_NAME.CHOSEN_FILE_NAME_KEY"

        // Storage Permissions
        private const val REQUEST_EXTERNAL_STORAGE = 6
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
        find<ImageButton>(R.id.play_file_button).onClick { onClickReadFile() }
        find<ImageButton>(R.id.stop_button).onClick { speaker?.stopSpeech() }
    }

    private fun onClickReadFile() {
        // Show the speaker not ready message if appropriate.
        if (!speaker.isReady()) {
            speakerActivity?.showSpeakerNotReadyMessage()
            return
        }

        val uri = chosenFileUri
        when (uri?.isAccessibleFile(ctx)) {
            true -> {
                // TODO Make this more efficient.
                val lines = uri.openContentInputStream(ctx)?.reader()?.readLines()
                speaker?.speak(lines ?: return)
            }
            else -> buildInvalidFileAlertDialog().show()
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
        find<ImageButton>(R.id.stop_button).onClick { speaker?.stopSpeech() }
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

        // Validate before continuing.
        if (uri == null || !uri.isAccessibleFile(ctx)) {
            buildInvalidFileAlertDialog().show()
            return
        }

        val content = uri.openContentInputStream(ctx)?.reader()?.readText()
        if (content.isNullOrBlank()) {
            // Nothing to read.
            return
        }

        // Save the file in the external storage directory using the filename
        // + '.wav'.
        val dir = Environment.getExternalStorageDirectory()
        val file = File(dir, waveFilename)
        val listener = SynthesisEventListener(speakerActivity!!.myApplication,
                waveFilename, ctx, file)
        speaker?.synthesizeToFile(content, listener)
    }

    private fun onClickSave() {
        val uri = chosenFileUri
        if (uri?.isAccessibleFile(ctx) != true) {
            buildInvalidFileAlertDialog().show()
            return
        }

        // Show the speaker not ready message if appropriate.
        if (!speaker.isReady()) {
            speakerActivity?.showSpeakerNotReadyMessage()
            return
        }

        // Build and display an appropriate alert dialog.
        val msgPart1 = getString(R.string.write_to_file_alert_message_p1)
        val msgPart2 = getString(R.string.write_to_file_alert_message_p2)
        val filename = chosenFileDisplayName
        val waveFilename = "$filename.wav"
        val fullMsg = "$msgPart1 \"$filename\"\n" +
                "\n$msgPart2 \"$waveFilename\""
        AlertDialogBuilder(ctx).apply {
            title(R.string.write_files_fragment_label)
            message(fullMsg)
            positiveButton(R.string.alert_positive_message) {
                // Ask the user for write permission if necessary.
                withStoragePermission { havePermission ->
                    writeSpeechToFile(uri, havePermission, waveFilename)
                }
            }
            negativeButton(R.string.alert_negative_message2)

            // Show the dialog.
            show()
        }
    }
}
