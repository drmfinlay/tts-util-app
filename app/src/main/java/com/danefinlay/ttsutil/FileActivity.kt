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

class FileActivity : SpeakerActivity(), FileChooser {

    override var chooseFileAction = Intent.ACTION_OPEN_DOCUMENT
    override var chooseFileCategory = Intent.CATEGORY_OPENABLE
    override var chooseFileMimeType = "text/*"
    override fun getActivity() = this

    private var fileToRead: Uri? = null

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

    private fun onClickReadFile() {
        val validFile: Boolean? = fileToRead?.validFilePath(ctx)
        if (validFile == true) {
            fileToRead?.getContent(ctx)?.reader()?.forEachLine {
                speaker?.speak(it)
            }
        } else if (validFile == false) buildInvalidFileAlertDialog().show()
    }

    private fun onClickWriteFile() {
        val uri = fileToRead

        // Build and display an appropriate alert dialog.
        val uriIsValid = uri != null && uri.validFilePath(this)
        val msg = getString(R.string.write_to_file_alert_message)
        if (!uriIsValid) {
            buildInvalidFileAlertDialog().show()
            return
        }

        AlertDialogBuilder(this).apply {
            title(getString(R.string.file_activity_description2))
            message("$msg \"${uri?.getDisplayName(ctx)}\"")
            positiveButton(getString(R.string.alert_positive_message)) {
                // Ask the user for write permission if necessary.
                verifyStoragePermissions()

                // Check again before continuing.
                if (uri == null || !uri.validFilePath(ctx)) {
                    runOnUiThread {buildInvalidFileAlertDialog().show()}
                    return@positiveButton
                }

                val content = uri.getContent(ctx)?.reader()?.readText()
                if (content.isNullOrBlank()) {
                    // Nothing to read.
                    return@positiveButton
                }

                val dir = Environment.getExternalStorageDirectory()
                val file = File(dir, "speech.mp4")
                val successDialogBuilder = AlertDialogBuilder(ctx).apply {
                    title(R.string.file_activity_description2)
                    message(R.string.write_to_file_alert_message_success)
                    positiveButton(R.string.alert_positive_message) {}
                    neutralButton(R.string.open_file_message) {
                        // Open the wave file.
                        // val intent = Intent()
                        // intent.action = ACTION_OPEN_DOCUMENT
                        // intent.putExtra()
                        // startActivity()
                    }
                }
                speaker?.synthesizeToFile(content, file) {
                    if (it is Speaker.UtteranceProgress.Done)
                        runOnUiThread {successDialogBuilder.show()}
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

    private fun verifyStoragePermissions() {
        // Check if we have write permission.
        if (Build.VERSION.SDK_INT >= 23) {
            val permission = this.checkSelfPermission(WRITE_EXTERNAL_STORAGE)
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // We don't have permission so prompt the user
                requestPermissions(
                        PERMISSIONS_STORAGE,
                        REQUEST_EXTERNAL_STORAGE
                )
            }
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super<FileChooser>.onActivityResult(requestCode, resultCode, data)
        super<SpeakerActivity>.onActivityResult(requestCode, resultCode, data)
    }

    companion object {
        // Storage Permissions
        private const val REQUEST_EXTERNAL_STORAGE = 1
        private val PERMISSIONS_STORAGE = arrayOf(
                READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE
        )
    }
}
