/*
 * TTS Util
 *
 * Authors: Dane Finlay <dane@danefinlay.net>
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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.ExecutorService

class ProcessWaveFilesTask(private val ctx: Context,
                           private val execService: ExecutorService,
                           private val inputSource: InputSource,
                           private val observer: TaskObserver,
                           private val outDirectory: Directory,
                           private val waveFilename: String,
                           private val inWaveFiles: MutableList<File>):
        Task, JoinWaveFilesHandler {

    @Volatile
    private var finalize: Boolean = false

    @Volatile
    private var outStream: OutputStream? = null

    override val id: Int = TASK_ID_PROCESS_FILE

    override fun begin(): Int {
        // Notify the observer that post-processing has begun.
        observer.notifyProgress(0, id)

        // Run validation checks, finishing early if unsuccessful.
        val result = performValidationChecks()
        if (result != SUCCESS) {
            finish(false)
            return result
        }

        // Delegate the rest of the work to the executor service and return.
        execService.submit { beginProcessing() }
        return SUCCESS
    }

    private fun performValidationChecks(): Int {
        // Use the out directory to create and open an output stream on the
        // specified document.  Return early if this is not possible.
        if (!outDirectory.exists(ctx)) return UNAVAILABLE_OUT_DIR
        val outputStream = outDirectory.openDocumentOutputStream(
                ctx, waveFilename, "audio/x-wav"
        ) ?: return UNWRITABLE_OUT_DIR

        // Set variables.
        this.outStream = outputStream

        // Everything is OK.
        return SUCCESS
    }

    private fun beginProcessing() {
        // Find the first proper wave file and read its header.
        val minFileSize = WaveFileHeader.MIN_SIZE + 1
        val wf = inWaveFiles.find { it.length() >= minFileSize }
        val wfHeader = WaveFileHeader(FileInputStream(wf).buffered(60))

        // Generate silence for each distinct file in the list ending with
        // "ms_sil.wav".
        val suffix = SILENCE_FILE_SUFFIX
        for (f in inWaveFiles) {
            val filename = f.name
            if (filename.endsWith(suffix) && !f.exists()) {
                // Parse the duration from the filename.
                val durationInMs = filename.substringBefore(suffix).toInt()
                val durationInSeconds: Float = durationInMs / 1000f

                // Write the silent wave file.
                writeSilentWaveFile(f, wfHeader, durationInSeconds)
            }
        }

        // If the TTS engine has produced impossibly short wave files, filter
        // them out and delete them.
        val toRemoveAndDelete = inWaveFiles.filter { f ->
            f.length() < minFileSize && f.isFile && f.canWrite()
        }
        for (f in toRemoveAndDelete) { inWaveFiles.remove(f);  f.delete(); }

        // Verify that the initialization data is correct.
        if (inWaveFiles.size == 0) {
            finish(false)
            return
        }

        // TODO Optionally handle creation of MP3 files here instead.

        // Join each wave file into one large one and write it to the output stream.
        var success: Boolean
        try {
            success = joinWaveFiles(inWaveFiles, outStream!!, this)
        } catch (error: RuntimeException) {
            success = false
            Log.e(TAG, "Failed to join wave ${inWaveFiles.size} files.", error)
        }

        // Finish.
        finish(success)
    }

    override fun getBeginTaskMessage(ctx: Context): String {
        return ctx.getString(
                R.string.begin_processing_source_message,
                inputSource.description
        )
    }

    override fun getShortDescription(ctx: Context): String =
            ctx.getString(R.string.post_synthesis_notification_title)

    override fun getLongDescription(ctx: Context, remainingTasks: Int): String {
        // Example: "Processing synthesized abc.txt wave file…
        //           2 tasks remaining."
        val textId = R.string.progress_notification_text
        val beginTextId = R.string.begin_processing_source_message
        val srcDescription = inputSource.description
        val beginText = ctx.getString(beginTextId, srcDescription)
        return ctx.getString(textId, beginText, remainingTasks,
                ctx.resources.getQuantityString(R.plurals.tasks, remainingTasks))
    }

    override fun getZeroLengthInputMessage(ctx: Context): String {
        // This task does no direct text input processing and so has no zero length
        // input error message.
        return ""
    }

    override fun getInputSource(ctx: Context): InputSource = this.inputSource

    private fun writeSilentWaveFile(file: File, header: WaveFileHeader,
                                    durationInSeconds: Float) {
        // Determine the data sub-chunk size.  It should be an even integer.
        var dataSize = (header.fmtSubChunk.byteRate * durationInSeconds).toInt() + 1
        if (dataSize % 2 == 1) dataSize += 1

        // Construct a wave header using the duration and the header provided.
        val silHeader = header.copy(dataSize)

        // Write the new header's bytes to the file.
        val silHeaderBytes = silHeader.writeToArray()
        val outputStream = FileOutputStream(file).buffered()
        outputStream.write(silHeaderBytes)

        // Write the audio data as zeros.
        var count = 0
        do { outputStream.write(0); count++; }
        while (count < silHeader.dataSubChunk.ckSize)
        outputStream.flush()
        outputStream.close()
    }

    override fun jwfHandler(totalProgress: Int, currentFile: File?,
                            fileProgress: Int): Boolean {
        // Notify the progress observer of the procedure's total progress.
        // Note: progress=[-1, 100] are dispatched by finish().
        if (totalProgress in 0..99) {
            observer.notifyProgress(totalProgress, id)
        }

        // Delete finished files, except the special ones for silence, which may be
        // needed later on in the procedure.
        if (fileProgress == 100 && currentFile != null &&
                !currentFile.name.endsWith(SILENCE_FILE_SUFFIX)) {
            currentFile.delete()
        }

        // Return whether the join wave files procedure should continue.
        return !finalize
    }

    private fun finish(success: Boolean): Boolean {
        // Close the output stream.
        outStream?.close()

        // Display an appropriate message to the user.
        val messageId = if (success) R.string.write_to_file_message_success
                        else R.string.write_to_file_message_failure
        val message = ctx.getString(messageId, waveFilename)
        ctx.runOnUiThread { toast(message, 1) }

        // Notify the progress observer that the task is finished.
        val progress = if (success) 100 else -1
        observer.notifyProgress(progress, id)

        // Delete all internal wave files and return.
        for (wf in inWaveFiles) {
            if (wf.isFile && wf.canWrite()) wf.delete()
        }
        return success
    }

    override fun finalize() {
        finalize = true
    }

    companion object {
        private const val SILENCE_FILE_SUFFIX = "ms_sil.wav"
    }
}
