package com.danefinlay.ttsutil.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.support.v4.app.Fragment
import com.danefinlay.ttsutil.R
import com.danefinlay.ttsutil.*
import org.jetbrains.anko.AlertDialogBuilder

abstract class MyFragment : Fragment(), FragmentInterface {

    protected val ctx: Context
        get() = this.requireContext()

    protected val myApplication: ApplicationEx
        get() = ctx.applicationContext as ApplicationEx

    protected val activityInterface: ActivityInterface?
        get() = context as? ActivityInterface

    private var tempStoragePermissionBlock: (granted: Boolean) -> Unit = {}

    abstract fun updateStatusField(text: String)
    abstract fun updateTaskCountField(count: Int)

    protected fun onStatusUpdate(event: ActivityEvent.StatusUpdateEvent) {
        val statusTextId = when (event.taskId) {
            TASK_ID_READ_TEXT -> R.string.status_text_reading_text
            TASK_ID_WRITE_FILE -> R.string.status_text_writing_file
            TASK_ID_PROCESS_FILE -> R.string.status_text_processing_file
            else -> R.string.status_text_idle
        }

        // Get the formatted status text.
        var statusText: String = if (event.taskId != TASK_ID_IDLE) {
            // Add the percentage for statuses other than IDLE.
            // Show "stopped" if progress<0.
            val parenthetical = "(" + if (event.progress < 0) {
                getString(R.string.status_text_task_stopped)
            } else {
                "${event.progress}%"
            } + ")"
            getString(statusTextId, parenthetical)
        } else {
            getString(statusTextId)
        }
        statusText = getString(R.string.status_text_field, statusText)

        // Update the status text field.
        updateStatusField(statusText)

        // Update the task count field.
        updateTaskCountField(event.remainingTasks)
    }

    override fun handleActivityEvent(event: ActivityEvent) {
        // Handle events common to all fragments.
        when (event) {
            is ActivityEvent.StatusUpdateEvent -> onStatusUpdate(event)
            else -> {}
        }
    }

    fun withStoragePermission(block: (granted: Boolean) -> Unit) {
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

    protected fun buildNoPermissionAlertDialog(block: (granted: Boolean) -> Unit):
            AlertDialogBuilder {
        return AlertDialogBuilder(ctx).apply {
            title(R.string.no_storage_permission_title)
            message(R.string.no_storage_permission_message)
            positiveButton(R.string.grant_permission_positive_message) {
                // Try asking for storage permission again.
                withStoragePermission { granted -> block(granted) }
            }
            negativeButton(R.string.alert_negative_message_1)
        }
    }

    protected fun buildUnavailableDirAlertDialog(): AlertDialogBuilder {
        val title = R.string.unavailable_dir_dialog_title
        val message = R.string.unavailable_dir_dialog_message
        return AlertDialogBuilder(ctx).apply {
            title(title)
            message(message)
            positiveButton(R.string.alert_positive_message_1) {
                activityInterface?.showDirChooser(DIR_SELECT_CONT_CODE)
            }
            negativeButton(R.string.alert_negative_message_1)
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
