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
import org.jetbrains.anko.longToast
import org.jetbrains.anko.runOnUiThread
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

class ProcessWaveFilesTask(private val ctx: Context,
                           private val observer: TaskObserver,
                           private val inWaveFiles: MutableList<File>,
                           private val outputStream: OutputStream,
                           private val finalWaveFilename: String):
        Task, JoinWaveFilesHandler {

    @Volatile
    private var finalize: Boolean = false

    override fun begin(): Boolean {
        // Notify the observer that post-processing has begun.
        observer.notifyProgress(0, TASK_ID, 0)

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
        var success: Boolean
        if (inWaveFiles.size == 0) return finish(false)

        // TODO Optionally handle creation of MP3 files here instead.

        // Join each wave file into one large file and write it to the output
        // stream.
        try {
            success = joinWaveFiles(inWaveFiles, outputStream, this)
        } catch (error: RuntimeException) {
            success = false
            Log.e(TAG, "Failed to join wave ${inWaveFiles.size} files.", error)
        }
        return finish(success)
    }

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
            observer.notifyProgress(totalProgress, TASK_ID, 0)
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
        outputStream.close()

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
        finalize = true
    }

    companion object {
        private const val TASK_ID = TASK_ID_PROCESS_FILE
        private const val SILENCE_FILE_SUFFIX = "ms_sil.wav"
    }
}
