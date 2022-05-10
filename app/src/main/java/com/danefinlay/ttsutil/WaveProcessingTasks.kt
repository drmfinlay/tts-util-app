/*
 * TTS Util
 *
 * Authors: Dane Finlay <Danesprite@posteo.net>
 *
 * Copyright (C) 2022 Dane Finlay
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

package com.danefinlay.ttsutil

import android.content.Context
import android.util.Log
import org.jetbrains.anko.longToast
import org.jetbrains.anko.runOnUiThread
import java.io.File
import java.io.OutputStream

class JoinWaveFilesTask(private val ctx: Context,
                        private val observer: TaskProgressObserver,
                        private val inWaveFiles: List<File>,
                        private val outputStream: OutputStream,
                        private val finalWaveFilename: String): Task {
    private val interruptEvent = InterruptEvent()

    override fun begin(): Boolean {
        // Notify the progress observer that post-processing has begun.
        observer.notifyProgress(0, TASK_ID, 0)

        // Verify that the initialization data is correct.
        var success: Boolean
        if (inWaveFiles.size == 0) return finish(false)

        // Join each utterance's wave file into the output file passed to this
        // listener.  Notify the progress observer as files are concatenated.
        // Note: progress=[-1, 100] is dispatched by the super method.
        try {
            success = joinWaveFiles(inWaveFiles, outputStream, true,
                    interruptEvent) { p: Int ->
                if (p in 0..99) observer.notifyProgress(p, TASK_ID, 0)
            }
        } catch (error: RuntimeException) {
            success = false
            Log.e(TAG, "Failed to join wave ${inWaveFiles.size} files.", error)
        }
        return finish(success)
    }

    private fun finish(success: Boolean): Boolean {
        // Display an appropriate message to the user.
        val messageId = if (success) R.string.write_to_file_message_success
                        else R.string.write_to_file_message_failure
        val message = ctx.getString(messageId, finalWaveFilename)
        ctx.runOnUiThread { longToast(message) }

        // Notify the progress observer that the task is finished.
        val progress = if (success) 100 else -1
        observer.notifyProgress(progress, TASK_ID, 0)

        // Delete all internal wave files and return.
        for (wf in inWaveFiles) {
            if (wf.isFile && wf.canWrite()) wf.delete()
        }
        return success
    }

    override fun finalize() {
        // Set the interrupt event.
        interruptEvent.interrupt = true
    }

    companion object {
        private const val TASK_ID = TASK_ID_PROCESS_FILE
    }
}
