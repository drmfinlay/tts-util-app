package com.danefinlay.ttsutil

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import org.jetbrains.anko.*
import java.io.File


private const val CHOSEN_FILE_URI_KEY = "$APP_NAME.CHOSEN_FILE_URI_KEY"
private const val CHOSEN_FILE_NAME_KEY = "$APP_NAME.CHOSEN_FILE_NAME_KEY"
typealias PermissionBlock = (granted: Boolean) -> Unit

class FileActivity : SpeakerActivity(), FileChooser {

    override var chooseFileAction = Intent.ACTION_OPEN_DOCUMENT
    override var chooseFileCategory = Intent.CATEGORY_OPENABLE
    override var chooseFileMimeType = "text/*"
    override fun getActivity() = this

    private var fileToRead: Uri? = null
    private var tempStoragePermissionBlock: PermissionBlock = {}

    private val chooseFileButton: Button
        get() = find(R.id.choose_file_button)

    private val chosenFilename: TextView
        get() = find(R.id.chosen_filename)

    private val readFileButton: Button
        get() = find(R.id.read_file_button)

    private val stopSpeakingButton: Button
        get() = find(R.id.stop_speaking_button)

    private val writeToFileButton: Button
        get() = find(R.id.write_to_file_button)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (savedInstanceState == null) {
            // Get the last chosen file's URI and display name from shared
            // preferences.
            val prefs = getSharedPreferences(packageName, MODE_PRIVATE)
            val uri = Uri.parse(prefs.getString(CHOSEN_FILE_URI_KEY, ""))
            setFileUriAndDisplayName(uri)
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        outState?.putParcelable(CHOSEN_FILE_URI_KEY, fileToRead)
        outState?.putCharSequence(CHOSEN_FILE_NAME_KEY, chosenFilename.text)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        savedInstanceState?.apply {
            fileToRead = getParcelable(CHOSEN_FILE_URI_KEY)
            chosenFilename.text = getCharSequence(CHOSEN_FILE_NAME_KEY)
        }
        super.onRestoreInstanceState(savedInstanceState)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when ( item.itemId ) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun buildInvalidFileAlertDialog(): AlertDialogBuilder {
        return AlertDialogBuilder(ctx).apply {
            title(R.string.invalid_file_dialog_title)
            message(R.string.invalid_file_dialog_message)
            positiveButton(R.string.alert_positive_message) {
                showFileChooser()
            }
            negativeButton(R.string.alert_negative_message1)
        }
    }

    private fun buildNoPermissionAlertDialog(): AlertDialogBuilder {
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

    private fun onClickReadFile() {
        val validFile: Boolean? = fileToRead?.validFilePath(ctx)
        if (validFile == true) {
            fileToRead?.getContent(ctx)?.reader()?.forEachLine {
                speaker?.speak(it)
            }
        } else if (validFile == false) buildInvalidFileAlertDialog().show()
    }

    private fun writeSpeechToFile(uri: Uri?, havePermission: Boolean) {
        if (!havePermission) {
            // Show a dialog if we don't have read/write storage permission
            runOnUiThread { buildNoPermissionAlertDialog().show() }
            return
        }

        // Validate before continuing.
        if (uri == null || !uri.validFilePath(ctx)) {
            runOnUiThread {buildInvalidFileAlertDialog().show()}
            return
        }

        val content = uri.getContent(ctx)?.reader()?.readText()
        if (content.isNullOrBlank()) {
            // Nothing to read.
            return
        }

        // Save the file in the external storage directory using the filename
        // + '.mp4'.
        val dir = Environment.getExternalStorageDirectory()
        val filename = "${uri.getDisplayName(ctx)}.mp4"
        val file = File(dir, filename)
        speaker?.synthesizeToFile(content, file) {
            if (it is Speaker.UtteranceProgress.Done) runOnUiThread {
                AlertDialogBuilder(ctx).apply {
                    title(R.string.file_activity_description2)
                    val msgPart1 = getString(
                            R.string.write_to_file_alert_message_success)
                    val fullMsg = "$msgPart1 '$filename'"
                    message(fullMsg)
                    positiveButton(R.string.alert_positive_message) {}
                    show()
                }
            }
        }
    }

    private fun onClickWriteFile() {
        val uri = fileToRead

        // Build and display an appropriate alert dialog.
        val uriIsValid = uri != null && uri.validFilePath(this)
        if (!uriIsValid) {
            buildInvalidFileAlertDialog().show()
            return
        }

        val msgPart1 = getString(R.string.write_to_file_alert_message_p1)
        val msgPart2 = getString(R.string.write_to_file_alert_message_p2)
        val filename = "${uri?.getDisplayName(ctx)}"
        val fullMsg = "$msgPart1 \"$filename\"\n" +
                "\n$msgPart2 \"$filename.mp4\""
        AlertDialogBuilder(this).apply {
            title(R.string.file_activity_description2)
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

    override fun onStart() {
        super.onStart()
        chooseFileButton.onClick { showFileChooser() }
        readFileButton.onClick { onClickReadFile() }
        writeToFileButton.onClick { onClickWriteFile() }
        stopSpeakingButton.onClick { speaker?.stopSpeech() }
    }

    override fun onFileChosen(uri: Uri?) {
        setFileUriAndDisplayName(uri)

        // Set the shared preference values asynchronously.
        doAsync {
            val prefs = getSharedPreferences(packageName, MODE_PRIVATE)
            prefs.edit()
                    .putString(CHOSEN_FILE_URI_KEY, uri?.toString() ?: "")
                    .putString(CHOSEN_FILE_NAME_KEY,
                            uri?.getDisplayName(this@FileActivity) ?: "")
                    .apply()
        }
    }

    private fun setFileUriAndDisplayName(uri: Uri?) {
        // Set chosenFilename.text to the file's display name if possible.
        val noFileChosen = getString(R.string.no_file_chosen)
        chosenFilename.text = if (uri == null || uri.toString() == "") {
            noFileChosen
        } else if (!uri.validFilePath(this)) {
            // Fallback on the last display name if the path isn't valid.
            val prefs = getSharedPreferences(packageName, MODE_PRIVATE)
            prefs.getString(CHOSEN_FILE_NAME_KEY, "") ?: noFileChosen
        } else {
            uri.getDisplayName(this) ?: noFileChosen
        }

        // Save the file uri.
        fileToRead = uri
    }

    private fun withStoragePermission(block: PermissionBlock) {
        // Check if we have write permission.
        if (Build.VERSION.SDK_INT >= 23) {
            val permission = this.checkSelfPermission(WRITE_EXTERNAL_STORAGE)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super<FileChooser>.onActivityResult(requestCode, resultCode, data)
        super<SpeakerActivity>.onActivityResult(requestCode, resultCode, data)
    }

    companion object {
        // Storage Permissions
        private const val REQUEST_EXTERNAL_STORAGE = 6
        private val PERMISSIONS_STORAGE = arrayOf(
                READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE
        )
    }
}
