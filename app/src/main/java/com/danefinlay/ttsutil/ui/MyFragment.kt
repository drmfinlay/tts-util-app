package com.danefinlay.ttsutil.ui

import android.content.Context
import android.support.v4.app.Fragment
import com.danefinlay.ttsutil.R
import com.danefinlay.ttsutil.*

abstract class MyFragment : Fragment(), FragmentInterface {

    // TODO This should be 'Context?'
    protected val Fragment.ctx: Context
        get() = this.requireContext()

    protected val Fragment.myApplication: ApplicationEx
        get() = ctx.applicationContext as ApplicationEx

    protected val activityInterface: ActivityInterface?
        get() = context as? ActivityInterface

    abstract fun updateStatusField(text: String)

    protected fun onStatusUpdate(event: ActivityEvent.StatusUpdateEvent) {
        val statusTextId = when (event.taskId) {
            TASK_ID_READ_TEXT -> R.string.status_text_reading_text
            TASK_ID_WRITE_FILE -> R.string.status_text_writing_file
            TASK_ID_PROCESS_FILE -> R.string.status_text_processing_file
            else -> R.string.status_text_idle
        }

        // Get the formatted text to set.
        var statusText = getString(R.string.status_text_field,
                getString(statusTextId))

        // Add the percentage for statuses other than IDLE.
        // Show "error" if progress<0.
        if (statusTextId != R.string.status_text_idle) {
            statusText += " ("
            statusText += if (event.progress < 0) {
                getString(R.string.status_text_task_stopped)
            } else {
                "${event.progress}%"
            }
            statusText += ")"
        }

        // Update the text field.
        updateStatusField(statusText)
    }

    override fun handleActivityEvent(event: ActivityEvent) {
        // Handle events common to all fragments.
        when (event) {
            is ActivityEvent.StatusUpdateEvent -> onStatusUpdate(event)
            else -> {}
        }
    }
}
