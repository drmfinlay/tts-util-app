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
import android.net.MailTo
import android.net.ParseException
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.*
import android.speech.tts.UtteranceProgressListener
import android.support.annotation.CallSuper
import android.support.v7.preference.PreferenceManager
import org.jetbrains.anko.*
import java.io.*
import java.lang.StringBuilder
import java.net.MalformedURLException
import java.net.URL
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.min

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

    private val streamReader: Reader
    protected val utteranceBytesQueue: MutableList<Int> =
            Collections.synchronizedList(mutableListOf<Int>())

    private val scaleSilenceToRate: Boolean
    private val speechRate: Float
    private val delimitersToSilenceMap: Map<Int, Long>

    private val filterHashes: Boolean
    private val filterWebLinks: Boolean
    private val filterMailToLinks: Boolean
    private val filtersEnabled: Boolean

    // Note: Instance variables may be accessed by many (at least three) threads.
    // Hence, we use Java's "volatile" mechanism.
    @Volatile
    protected var finalize: Boolean = false

    @Volatile
    protected var inputProcessed: Long = 0

    @Volatile
    private var inputFiltered: Long = 0

    @Volatile
    private var streamHasFurtherInput: Boolean = true

    abstract fun enqueueText(text: CharSequence, bytesRead: Int)
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
        filterHashes = prefs.getBoolean("pref_filter_hash", false)
        filterWebLinks = prefs.getBoolean("pref_filter_web_links", false)
        filterMailToLinks = prefs.getBoolean("pref_filter_mailto_links", false)
        filtersEnabled = filterHashes || filterWebLinks || filterMailToLinks

        // Initialize an appropriate reader.
        val delimitersEnabled = delimitersToSilenceMap.values.count { it > 0 }
        streamReader = if (delimitersEnabled > 0) inputStream.reader()
                       else inputStream.bufferedReader()
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

    private fun filterChar(char: Char): Boolean {
        // Check for hash, if appropriate.
        return filterHashes && char.toInt() == 0x23
    }

    private fun filterWord(word: String): Boolean {
        var result = false

        // Check for web hyperlinks, if appropriate.
        if (filterWebLinks) {
            // Check prefixes first as an optimization.
            val lowerCaseWord = word.toLowerCase(Locale.ROOT)
            for (prefix in listOf("http://", "https://")) {
                result = lowerCaseWord.startsWith(prefix)
                if (result) break
            }

            // If there is a match, try to parse the string using Java's URL class,
            // which is imperfect, but good enough for this use case.
            if (result) {
                result = try { URL(lowerCaseWord); true }
                catch (e: MalformedURLException) { false }
            }
        }

        // Check for mailto hyperlinks, if appropriate.
        if (!result && filterMailToLinks) {
            val lowerCaseWord = word.toLowerCase(Locale.ROOT)
            result = try { MailTo.parse(lowerCaseWord); true }
            catch (e: ParseException) { false }
        }
        return result
    }

    private fun applyTextFilters(buffer: ArrayList<Char>) {
        val initialBufferSize = buffer.size
        val stringBuilder = StringBuilder()
        var markedIndices = mutableListOf<Int>()

        // Mark the index of each character to filter out.
        for ((index, char) in buffer.withIndex()) {
            // Read until we reach a word boundary.
            // Filter characters here, if appropriate.
            if (!char.isWhitespace()) {
                stringBuilder.append(char)
                if (filterChar(char)) markedIndices.add(index)
                continue
            }

            // We have reached a word boundary.  Filter out the word, if
            // appropriate.
            val word = stringBuilder.toString()
            if (filterWord(word)) {
                val wordIndexZero = index - word.length
                word.indices.forEach { i -> markedIndices.add(wordIndexZero + i) }
            }
            stringBuilder.clear()
        }

        // Remove repeated indices and sort the list.
        markedIndices = markedIndices.toMutableSet().toMutableList()
        markedIndices.sort()

        // Remove filtered characters from the buffer in reverse order.
        var index = markedIndices.size - 1
        while (index >= 0) {
            buffer.removeAt(markedIndices[index])
            index--
        }

        // Done.
        val filterCount = initialBufferSize - buffer.size
        inputProcessed += filterCount
        inputFiltered += filterCount
    }

    private fun processInputBuffer(buffer: ArrayList<Char>) {
        val stringBuilder = StringBuilder()

        // If text filters are enabled, apply them to the input buffer and take note
        // of the number of filtered characters.
        if (filtersEnabled) applyTextFilters(buffer)

        // Process buffer characters as strings delimited by characters with
        // positive silence duration entries.  Scale silence by the speech rate, if
        // appropriate.
        for (char in buffer) {
            var silenceDuration = delimitersToSilenceMap[char.toInt()]
            if (silenceDuration != null && silenceDuration > 0L) {
                val text = stringBuilder.toString()
                val bytesRead = text.length + 1 // Including char.
                enqueueText(text, bytesRead)
                if (scaleSilenceToRate) {
                    silenceDuration = (silenceDuration / speechRate).toLong()
                }
                enqueueSilence(silenceDuration)
                stringBuilder.clear()
                continue
            }
            stringBuilder.append(char)
        }

        // If there were no appropriate delimiters, then enqueue the text.
        if (stringBuilder.length > 0) {
            val text = stringBuilder.toString()
            enqueueText(text, text.length)
        }

        // Done.
        buffer.clear()
    }

    private fun enqueueNextInput(): Boolean {
        val buffer = ArrayList<Char>()
        var byte = streamReader.read()
        val lineFeedScanThreshold = (maxInputLength * 0.70).toInt()
        val whiteSpaceScanThreshold = (maxInputLength * 0.9).toInt()

        // Read characters until we hit a delimiter with a positive silence duration
        // or until an appropriate whitespace character is found near the maximum
        // input length -- whichever comes first.
        while (byte >= 0) {
            val char = byte.toChar()
            buffer.add(char)

            val silenceDuration = delimitersToSilenceMap[char.toInt()] ?: 0L
            if (silenceDuration > 0) break
            if (buffer.size >= lineFeedScanThreshold && byte == 0x0a) break
            if (buffer.size >= whiteSpaceScanThreshold && char.isWhitespace()) break

            byte = streamReader.read()
        }

        // Process the input buffer.
        processInputBuffer(buffer)

        // Return whether there is further input.
        return byte >= 0
    }

    override fun finish(success: Boolean): Boolean {
        // Close the reader and input stream.
        streamReader.close()
        inputStream.close()

        // Notify the progress observer that the task is finished.
        val progress = if (success) 100 else -1
        observer.notifyProgress(progress, taskId, 0)

        // If any input was filtered, display a toast message.
        if (inputFiltered > 0) {
            val message = app.getString(
                    R.string.filtered_characters_message,
                    inputFiltered, app.resources.getQuantityString(
                    R.plurals.characters, inputFiltered.toInt())
            )
            displayMessage(message, false)
        }

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
        // Note: Using the value returned by getMaxSpeechInputLength() can result in
        // fatal errors, i.e. application crashes.  Hence, we use shorter values.
        val maxInputLength = min(500, getMaxSpeechInputLength() - 1)

        private var currentUtteranceId: Long = 0

        fun nextUtteranceId(): String = "${currentUtteranceId++}"
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
        if (durationInMs == 0L) return

        // The queue mode initially specified for the task is ignored here; it only
        // makes sense to use QUEUE_ADD for silence.
        tts.playSilentUtterance(durationInMs, QUEUE_ADD, nextUtteranceId())
    }

    override fun enqueueText(text: CharSequence, bytesRead: Int) {
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
        // Delete silent wave files because they may be incompatible with current
        // user settings.
        for (file in app.filesDir.listFiles()) {
            if (file.name.endsWith("ms_sil.wav")) file.delete()
        }

        if (!super.begin()) return false
        return true
    }

    override fun enqueueSilence(durationInMs: Long) {
        if (durationInMs == 0L) return

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

    override fun enqueueText(text: CharSequence, bytesRead: Int) {
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
