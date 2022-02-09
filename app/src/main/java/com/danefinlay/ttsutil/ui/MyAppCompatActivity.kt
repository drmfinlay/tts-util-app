package com.danefinlay.ttsutil.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import com.danefinlay.ttsutil.ApplicationEx
import com.danefinlay.ttsutil.R
import com.danefinlay.ttsutil.TASK_ID_IDLE
import com.danefinlay.ttsutil.TaskProgressObserver
import org.jetbrains.anko.longToast

abstract class MyAppCompatActivity : AppCompatActivity(),
        ActivityInterface, TaskProgressObserver {

    protected val myApplication: ApplicationEx
        get() = application as ApplicationEx

    @JvmField
    protected var mLastStatusUpdate =
            ActivityEvent.StatusUpdateEvent(100, TASK_ID_IDLE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register as a task progress observer.
        myApplication.addProgressObserver(this)
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

    override fun showFileChooser() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "text/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            // TODO Allow processing of multiple text files.
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        }

        try {
            val title = getString(R.string.file_chooser_title)
            startActivityForResult(
                    Intent.createChooser(intent, title),
                    FILE_SELECT_CODE)
        } catch (ex: ActivityNotFoundException) {
            // Potentially direct the user to the Market with a Dialog.
            longToast(getString(R.string.no_file_manager_msg))
        }
    }

    override fun getLastStatusUpdate(): ActivityEvent.StatusUpdateEvent {
        return mLastStatusUpdate
    }

    override fun notifyProgress(progress: Int, taskId: Int) {
        // Inform each compatible fragment of the progress via a status update
        // event.  Ensure that this runs on the main thread.
        val event = ActivityEvent.StatusUpdateEvent(progress, taskId)
        runOnUiThread { handleActivityEvent(event) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_SELECT_CODE && resultCode == RESULT_OK) {
            // Get the Uri of the selected file and pass it on.
            val uri = data?.data
            if (uri != null) {
                val event = ActivityEvent.FileChosenEvent(uri)
                handleActivityEvent(event)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        myApplication.deleteProgressObserver(this)
    }

    companion object {
        private const val FILE_SELECT_CODE = 5
    }
}
