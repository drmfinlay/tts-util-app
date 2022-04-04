package com.danefinlay.ttsutil.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.danefinlay.ttsutil.*
import com.danefinlay.ttsutil.ui.ActivityEvent.ChosenFileEvent
import org.jetbrains.anko.longToast

abstract class MyAppCompatActivity : AppCompatActivity(),
        ActivityInterface, TaskProgressObserver {

    protected val myApplication: ApplicationEx
        get() = application as ApplicationEx

    private val idleStatusEvent =
            ActivityEvent.StatusUpdateEvent(100, TASK_ID_IDLE)

    protected var mLastStatusUpdate = idleStatusEvent
    protected var mLastChosenDirEvent: ChosenFileEvent? = null
    protected var mLastChosenFileEvent: ChosenFileEvent? = null

    private fun retrieveChosenFileData(prefs: SharedPreferences,
                                       uriKey: String, nameKey: String,
                                       fileType: Int): ChosenFileEvent? {
        val uriString = prefs.getString(uriKey, null)
        val displayName = prefs.getString(nameKey, null)
        if (uriString == null || displayName == null) return null
        val uri = Uri.parse(uriString)
        return ChosenFileEvent(uri, displayName, fileType)
    }

    private fun saveChosenFileData(prefs: SharedPreferences, uriKey: String,
                                   nameKey: String, event: ChosenFileEvent) {
        prefs.edit()
                .putString(uriKey, event.uri.toString())
                .putString(nameKey, event.displayName)
                .apply()  // apply() is asynchronous.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register as a task progress observer.
        myApplication.addProgressObserver(this)

        // Restore persistent data, if necessary.
        if (savedInstanceState == null) {
            val prefs = getSharedPreferences(packageName, MODE_PRIVATE)
            val event1 = retrieveChosenFileData(prefs, CHOSEN_DIR_URI_KEY,
                    CHOSEN_DIR_NAME_KEY, 0)
            if (event1 != null) mLastChosenDirEvent = event1
            val event2 = retrieveChosenFileData(prefs, CHOSEN_FILE_URI_KEY,
                    CHOSEN_FILE_NAME_KEY, 1)
            if (event2 != null) mLastChosenFileEvent = event2
            return
        }

        // Restore instance data.
        savedInstanceState.run {
            mLastStatusUpdate = getParcelable("mLastStatusUpdate")
                    ?: idleStatusEvent
            mLastChosenFileEvent = getParcelable("mLastChosenFileEvent")
            mLastChosenDirEvent = getParcelable("mLastChosenDirEvent")
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        // Save data.
        outState?.run {
            putParcelable("mLastStatusUpdate", mLastStatusUpdate)
            putParcelable("mLastChosenFileEvent", mLastChosenFileEvent)
        }
    }

    protected fun handleActivityEvent(event: ActivityEvent,
                                      fragments: List<Fragment>) {
        // Iterate each attached fragment and, if it implements the right interface,
        // use it to handle this event.
        for (fragment in fragments) {
            if (fragment !is FragmentInterface) continue
            fragment.handleActivityEvent(event)
        }
    }

    private fun startFileChooserActivity(intent: Intent, chooserTitle: String,
                                         requestCode: Int) {
        try {
            val chooserIntent = Intent.createChooser(intent, chooserTitle)
            startActivityForResult(chooserIntent, requestCode)
        } catch (ex: ActivityNotFoundException) {
            // Potentially direct the user to the Market with a Dialog.
            longToast(getString(R.string.no_file_manager_msg))
        }
    }

    override fun showFileChooser() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "text/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            // TODO Allow processing of multiple text files.
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        }
        val title = getString(R.string.file_chooser_title)
        startFileChooserActivity(intent, title, FILE_SELECT_CODE)
    }

    override fun showDirChooser() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        val title = getString(R.string.dir_chooser_title)
        startFileChooserActivity(intent, title, DIR_SELECT_CODE)
    }

    override fun getLastStatusUpdate(): ActivityEvent.StatusUpdateEvent {
        return mLastStatusUpdate
    }

    override fun getLastFileChosenEvent(): ChosenFileEvent? {
        return mLastChosenFileEvent
    }

    override fun getLastDirChosenEvent(): ChosenFileEvent? {
        return mLastChosenDirEvent
    }

    override fun notifyProgress(progress: Int, taskId: Int) {
        // Inform each compatible fragment of the progress via a status update
        // event.  Ensure that this runs on the main thread.
        val event = ActivityEvent.StatusUpdateEvent(progress, taskId)
        runOnUiThread { handleActivityEvent(event) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when {
            requestCode == DIR_SELECT_CODE && resultCode == RESULT_OK -> {
                // Get the Uri of the selected directory, returning early if it is
                // invalid.
                val uri = data?.data ?: return
                val displayName = "<placeholder>"  // FIXME
                Log.e(TAG, "$uri")
                // DocumentsContract.

                // Set shared preference and property values.
                val event = ChosenFileEvent(uri, displayName, 0)
                saveChosenFileData(getSharedPreferences(packageName, MODE_PRIVATE),
                        CHOSEN_DIR_URI_KEY, CHOSEN_DIR_NAME_KEY, event)
                mLastChosenDirEvent = event

                // Send a file chosen event.
                handleActivityEvent(event)
            }
            requestCode == FILE_SELECT_CODE && resultCode == RESULT_OK -> {
                // Get the Uri of the selected file, returning early if it is
                // invalid.
                val uri = data?.data ?: return
                if (!uri.isAccessibleFile(this)) return
                val displayName = uri.retrieveFileDisplayName(this) ?: return

                // Set shared preference and property values.
                val event = ChosenFileEvent(uri, displayName, 1)
                saveChosenFileData(getSharedPreferences(packageName, MODE_PRIVATE),
                        CHOSEN_FILE_URI_KEY, CHOSEN_FILE_NAME_KEY, event)
                mLastChosenFileEvent = event

                // Send a file chosen event.
                handleActivityEvent(event)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        myApplication.deleteProgressObserver(this)
    }
}
