/*
 * TTS Util
 *
 * Authors: Dane Finlay <Danesprite@posteo.net>
 *
 * Copyright (C) 2019 Dane Finlay
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
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.*
import android.speech.tts.UtteranceProgressListener
import android.support.annotation.CallSuper
import android.support.v7.preference.PreferenceManager
import org.jetbrains.anko.*
import java.io.*
import java.lang.StringBuilder
import java.util.*

abstract class MyUtteranceProgressListener(ctx: Context, val tts: TextToSpeech) :
        UtteranceProgressListener() {

    val app = ctx.applicationContext as ApplicationEx

    fun displayMessage(string: String, long: Boolean) {
        app.runOnUiThread {
            if (long) longToast(string)
            else toast(string)
        }
    }

    fun displayMessage(id: Int, long: Boolean) {
        app.runOnUiThread {
            if (long) longToast(id)
            else toast(id)
        }
    }

    @CallSuper
    open fun begin(): Boolean {
        tts.setOnUtteranceProgressListener(this)
        return true
    }

    override fun onError(utteranceId: String?) { // deprecated
        onError(utteranceId, -1)
    }

    @CallSuper
    open fun finish(success: Boolean): Boolean {
        tts.setOnUtteranceProgressListener(null)
        return success
    }
}


abstract class TTSTask(ctx: Context, tts: TextToSpeech,
                       private val inputStream: InputStream,
                       private val inputSize: Long,
                       private val taskId: Int,
                       private val observer: TaskProgressObserver) :
        MyUtteranceProgressListener(ctx, tts), Task {

    private val reader by lazy { inputStream.bufferedReader() }
    private val maxInputLength = getMaxSpeechInputLength()
    protected val utteranceBytesQueue: MutableList<Int> =
            Collections.synchronizedList(mutableListOf())
    private val scaleSilenceToRate: Boolean
    private val speechRate: Float
    private val delimitersToSilenceMap: Map<Int, Long>

    // Note: Instance variables may be accessed by many (at least three) threads.
    // Hence, we use Java's "volatile" mechanism.
    @Volatile
    protected var finalize: Boolean = false

    @Volatile
    private var inputProcessed: Long = 0

    @Volatile
    private var streamHasFurtherInput: Boolean = true

    abstract fun enqueueText(text: String, bytesRead: Int)
    abstract fun enqueueSilence(durationInMs: Long)

    init {
        // Retrieve values from shared preferences.
        // Note: This cannot be done from the binder threads which invoke utterance
        // progress listener callbacks.
        val prefs = PreferenceManager.getDefaultSharedPreferences(app)
        scaleSilenceToRate = prefs.getBoolean("pref_silence_scale_to_rate", false)
        speechRate = prefs.getFloat("pref_tts_speech_rate", 1.0f)
        delimitersToSilenceMap = mapOf(
                // Line Feed (\n).
                0x0a to prefs.getString("pref_silence_line_endings", "100")
                    !!.toLong()
        )
    }

    override fun begin(): Boolean {
        if (!super.begin()) return false

        // Notify the progress listener that work has begun.
        observer.notifyProgress(0, taskId, 0)

        // Enqueue the first input bytes, catching any IO exceptions.  This
        // jumpstarts the processing of the entire stream.
        try {
            streamHasFurtherInput = enqueueNextInput()
            if (!streamHasFurtherInput && utteranceBytesQueue.size == 0) {
                finish(true)
            }
        } catch (exception: IOException) {
            return finish(false)
        }
        return true
    }

    private fun processInputBuffer(buffer: ArrayList<Char>, flush: Boolean) {
        var byteCount = 0
        val stringBuilder = StringBuilder()

        // TODO Insert filter logic here.

        // Process each character in the buffer.
        while (byteCount < buffer.size) {
            // Retrieve the next character.
            val char = buffer[byteCount++]

            // Get the duration of the silent utterance, if any, to be inserted
            // in place of the current character.
            var silenceDuration: Long = delimitersToSilenceMap[char.toInt()] ?: 0L
            val insertSilence = silenceDuration > 0L

            // Include the input byte if no silence is to be inserted.
            if (!insertSilence) stringBuilder.append(char)

            // Enqueue TTS task(s), if necessary.
            if (insertSilence || char.toInt() in delimitersToSilenceMap.keys) {
                // Enqueue the current characters for synthesis.
                enqueueText(stringBuilder.toString(), byteCount)

                // If silence is to be inserted, then enqueue it.  Scale the silence
                // by the speech rate, if necessary.
                if (insertSilence) {
                    if (scaleSilenceToRate) {
                        silenceDuration = (silenceDuration / speechRate).toLong()
                    }
                    enqueueSilence(silenceDuration)
                }

                // Remove processed characters from the buffer and reset locals.
                for (x in 0 until byteCount) { buffer.removeAt(0) }
                byteCount = 0
                stringBuilder.clear()
            }
        }

        // If in flush mode and the buffer is not yet empty, enqueue all characters
        // and clear it.
        if (flush && buffer.size > 0) {
            enqueueText(stringBuilder.toString(), byteCount)
            buffer.clear()
        }
    }

    private fun enqueueNextInput(): Boolean {
        // Have Android's TTS framework to process text from the input stream.
        val buffer = ArrayList<Char>()
        var byte = reader.read()
        while (byte >= 0) {
            // Convert the input byte to a character and add it to the buffer.
            val char = byte.toChar()
            buffer.add(char)

            // Read until we reach a line feed or the maximum input length,
            // (whichever comes first,) then process the input buffer.  Processing
            // may or may not enqueue TTS tasks at this point, so continue until it
            // does.
            val isMaxLength = buffer.size == maxInputLength
            if (byte == 0x0a || isMaxLength) {
                processInputBuffer(buffer, isMaxLength)

                // Finish if the buffer was flushed.
                if (buffer.size == 0) break
            }

            // Read the next byte.
            byte = reader.read()
        }

        // Flush any characters still in the buffer.
        processInputBuffer(buffer, true)

        // Return whether there is further input.
        return byte >= 0
    }

    override fun finish(success: Boolean): Boolean {
        // Close the reader and input stream.
        reader.close()
        inputStream.close()

        // Notify the progress observer that the task is finished.
        val progress = if (success) 100 else -1
        observer.notifyProgress(progress, taskId, 0)

        // Call the super method.
        return super.finish(success)
    }

    override fun onStart(utteranceId: String?) {}

    override fun onError(utteranceId: String?, errorCode: Int) {
        // Get the matching error message string for errorCode.
        val errorMsg =  when (errorCode) {
            ERROR_SYNTHESIS -> R.string.synthesis_error_msg_synthesis
            ERROR_SERVICE -> R.string.synthesis_error_msg_service
            ERROR_OUTPUT -> R.string.synthesis_error_msg_output
            ERROR_NETWORK -> R.string.synthesis_error_msg_network
            ERROR_NETWORK_TIMEOUT -> R.string.synthesis_error_msg_net_timeout
            ERROR_INVALID_REQUEST -> R.string.synthesis_error_msg_invalid_req
            ERROR_NOT_INSTALLED_YET -> R.string.synthesis_error_msg_voice_data
            else -> R.string.synthesis_error_msg_generic
        }

        // Display the error message.
        displayMessage(errorMsg, true)

        // Finish.
        finalize()
        finish(false)
    }

    override fun onStop(utteranceId: String?, interrupted: Boolean) {
        super.onStop(utteranceId, interrupted)
        finish(false)
    }

    override fun onDone(utteranceId: String?) {
        // Add processed bytes to *inputProcessed*.
        if (utteranceBytesQueue.size > 0) {
            inputProcessed += utteranceBytesQueue.removeAt(0)
        }

        // Call finish() if we have reached the end of the stream or have been
        // requested to finalize.
        val success = !streamHasFurtherInput
        if (success || finalize) {
            finish(success)
            return
        }

        // Calculate progress and notify the progress listener.
        // Note: progress=[-1, 100] is dispatched by the finish() method.
        val progress = (inputProcessed.toFloat() / inputSize * 100).toInt()
        if (progress in 0..99) observer.notifyProgress(progress, taskId, 0)

        // Enqueue the next input.
        try {
            streamHasFurtherInput = enqueueNextInput()
            if (!streamHasFurtherInput && utteranceBytesQueue.size == 0) {
                finish(true)
            }
        } catch (exception: IOException) {
            finish(false)
        }
    }

    override fun finalize() {
        finalize = true
    }

    companion object {
        private var currentUtteranceId: Long = 0

        fun nextUtteranceId(): String {
            val id = currentUtteranceId
            currentUtteranceId += 1
            return "$id"
        }
    }
}


class ReadInputTask(ctx: Context, tts: TextToSpeech, inputStream: InputStream,
                    inputSize: Long, private val queueMode: Int,
                    observer: TaskProgressObserver) :
        TTSTask(ctx, tts, inputStream, inputSize, TASK_ID_READ_TEXT,
                observer) {

    override fun begin(): Boolean {
        // Request audio focus.  Finish early if our request was denied.
        // Otherwise, call the super method.
        if (!app.requestAudioFocus()) return finish(false)
        return super.begin()
    }

    override fun enqueueSilence(durationInMs: Long) {
        // The queue mode initially specified for the task is ignored here; it only
        // makes sense to use QUEUE_ADD for silence.
        tts.playSilentUtterance(durationInMs, QUEUE_ADD, nextUtteranceId())
    }

    override fun enqueueText(text: String, bytesRead: Int) {
        if (bytesRead == 0) return

        // Enqueue text using speak().
        // Add *bytesRead* to the queue.
        utteranceBytesQueue.add(bytesRead)

        // Add text to the queue as an utterance.
        val bundle = Bundle()
        bundle.putInt(Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        tts.speak(text, queueMode, bundle, nextUtteranceId())
    }

    override fun onDone(utteranceId: String?) {
        // Note: This function may be called before an utterance is completely
        // processed.  This appears to be related to our use of silent utterances.
        // With this in mind, wait until the utterance is *really* done before we
        // proceed.
        while (tts.isSpeaking) Thread.sleep(5)
        super.onDone(utteranceId)
    }

    override fun finish(success: Boolean): Boolean {
        app.releaseAudioFocus()
        return super.finish(success)
    }
}

class FileSynthesisTask(ctx: Context, tts: TextToSpeech,
                        inputStream: InputStream, inputSize: Long,
                        private val waveFilename: String,
                        private val inWaveFiles: MutableList<File>,
                        progressObserver: TaskProgressObserver) :
        TTSTask(ctx, tts, inputStream, inputSize,
                TASK_ID_WRITE_FILE, progressObserver) {

    override fun begin(): Boolean {
        if (!super.begin()) return false

        // Delete silent wave files because they may be incompatible with current
        // user settings.
        for (file in app.filesDir.listFiles()) {
            if (file.name.endsWith("ms_sil.wav")) file.delete()
        }
        return true
    }

    override fun enqueueSilence(durationInMs: Long) {
        // Android's text-to-speech framework does not allow adding silence to wave
        // files, so we add a reference to a special wave file that will contain Xms
        // of silence.  These files are created in finish(), if necessary.
        val file = File(app.filesDir, "${durationInMs}ms_sil.wav")
        inWaveFiles.add(file)
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
        do {
            outputStream.write(0)
            count++
        } while (count < silHeader.dataSubChunk.ckSize)
        outputStream.flush()
        outputStream.close()
    }

    override fun enqueueText(text: String, bytesRead: Int) {
        if (bytesRead == 0) return

        // Enqueue text with synthesizeToFile().
        // Add *bytesRead* to the queue.
        utteranceBytesQueue.add(bytesRead)

        // Create a wave file for this utterance and enqueue file synthesis.
        val file = File.createTempFile("utt", "dat", app.filesDir)
        val success = tts.synthesizeToFile(text, null, file, nextUtteranceId())

        // If successful, add the file to the list.
        if (success == SUCCESS) inWaveFiles.add(file)
    }

    override fun finish(success: Boolean): Boolean {
        // Generate silent wave files, if successful and if necessary.
        val minFileSize = WaveFileHeader.MIN_SIZE + 1
        if (success) {
            // Find the first proper wave file and read its header.
            val wf = inWaveFiles.find { it.length() >= minFileSize }
            val wfHeader = WaveFileHeader(FileInputStream(wf).buffered())

            // Generate silence for each distinct file in the list ending with
            // "ms_sil.wav".
            val suffix = "ms_sil.wav"
            for (f in inWaveFiles) {
                val filename = f.name
                if (filename.endsWith(suffix) && !f.exists()) {
                    // Parse the duration in seconds from the filename.
                    val durationInMs = filename.substringBefore(suffix).toInt()
                    val durationInSeconds = durationInMs / 1000f

                    // Write the silent wave file.
                    writeSilentWaveFile(f, wfHeader, durationInSeconds)
                }
            }
        }

        // If the TTS engine has produced impossibly short wave files, filter
        // them out and delete them.  If file synthesis failed, delete all files
        // instead.
        val toRemoveAndDelete = inWaveFiles.filter { f ->
            f.length() < minFileSize || !success && f.isFile && f.canWrite()
        }
        for (f in toRemoveAndDelete) {
            inWaveFiles.remove(f)
            f.delete()
        }

        // Display a toast message on failure.
        if (!success) {
            val messageId = R.string.write_to_file_message_failure
            val message = app.getString(messageId, waveFilename)
            displayMessage(message, true)
        }

        // Call the super method.
        return super.finish(success)
    }
}
