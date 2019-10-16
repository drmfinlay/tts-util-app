package com.danefinlay.ttsutil.ui

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import org.jetbrains.anko.toast

interface FileChooser {
    fun getActivity(): Activity
    /** Default suggested value is Intent.ACTION_OPEN_DOCUMENT */
    var chooseFileAction: String
    /** Default suggested value is Intent.CATEGORY_OPENABLE */
    var chooseFileCategory: String
    /** Default suggested value is (astrix)/(astrix) for all types */
    var chooseFileMimeType: String

    fun Activity.showFileChooser() {
        val intent = Intent(chooseFileAction).apply {
            type = chooseFileMimeType
            addCategory(chooseFileCategory)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivityForResult(
                    Intent.createChooser(intent, "Select a File..."),
                    FILE_SELECT_CODE)
        } catch (ex: ActivityNotFoundException) {
            // Potentially direct the user to the Market with a Dialog
            toast("Please install a File Manager.")
        }
    }

    fun onFileChosen(uri: Uri?)

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if ( data == null ) return
        when (requestCode) {
            FILE_SELECT_CODE -> if (resultCode == RESULT_OK) {
                // Get the Uri of the selected file
                val uri = data.data
                onFileChosen(uri)
            }
        }
    }

    companion object {
        private const val FILE_SELECT_CODE = 5
    }
}
