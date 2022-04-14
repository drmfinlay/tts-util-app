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
import android.util.Log
import org.jetbrains.anko.*
import java.io.*

abstract class MyUtteranceProgressListener(ctx: Context, val tts: TextToSpeech) :
        UtteranceProgressListener() {

    val app = ctx.applicationContext as ApplicationEx

    @Volatile
    protected var finalize: Boolean = false

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
    open fun begin() {
        tts.setOnUtteranceProgressListener(this)
    }

    override fun onError(utteranceId: String?) { // deprecated
        onError(utteranceId, -1)
    }

    @CallSuper
    open fun finish(success: Boolean) {
        tts.setOnUtteranceProgressListener(null)
    }

    open fun finalize() {
        finalize = true
    }
}


abstract class TTSEventListener(ctx: Context,
                                tts: TextToSpeech,
                                private val inputStream: InputStream,
                                private val inputSize: Long,
                                protected var taskId: Int,
                                protected val observer: TaskProgressObserver) :
        MyUtteranceProgressListener(ctx, tts) {

    protected lateinit var reader: BufferedReader
    protected val maxInputLength = getMaxSpeechInputLength()
    private var inputProcessed: Long = 0
    protected var streamHasFurtherInput: Boolean = true
    protected var utteranceBytesQueue = mutableListOf<Int>()

    abstract fun enqueueNextInput(): Boolean

    sealed class Filter(val include: Boolean,
                        val newUtteranceRequired: Boolean) {
        class SilentUtteranceFilter(val durationInMs: Long) : Filter(false, true)
        class NoFilter : Filter(true, false)
    }

    protected fun filterInputByte(byte: Int): Filter {
        return when (byte) {
            "\n"[0].toInt() -> Filter.SilentUtteranceFilter(100)
            else -> Filter.NoFilter()
        }
    }

    override fun begin() {
        super.begin()

        // Open a reader on the input stream and enqueue the first input bytes.
        // This jumpstarts the processing of the entire stream.
        try {
            reader = inputStream.bufferedReader()
            streamHasFurtherInput = enqueueNextInput()
        } catch (exception: IOException) {
            finish(false)
        }

        // Notify the progress listener that work has begun.
        observer.notifyProgress(0, taskId)
    }

    override fun finish(success: Boolean) {
        // Close the reader and input stream.
        reader.close()
        inputStream.close()

        // Notify the progress observer that the task is finished or if an error
        // occurred.
        val progress = if (success) 100 else -1
        observer.notifyProgress(progress, taskId)

        // Call the super method.
        super.finish(success)
    }

    override fun onStart(utteranceId: String?) {}

    override fun onError(utteranceId: String?, errorCode: Int) {
        // Schedule the finish(false) to be invoked in  roughly 300 ms.
        // This is done asynchronously because it is possible that neither the
        // notification has been started nor the audio focus has been acquired by
        // the time this is run.
        app.doAsync {
            Thread.sleep(300)
            finish(false)
        }

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
        val progress = inputProcessed.toFloat() / inputSize * 100
        observer.notifyProgress(progress.toInt(), taskId)

        // Enqueue the next input.
        try {
            streamHasFurtherInput = enqueueNextInput()
        } catch (exception: IOException) {
            finish(false)
        }
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


class SpeakingEventListener(ctx: Context,
                            tts: TextToSpeech,
                            inputStream: InputStream,
                            inputSize: Long,
                            private val queueMode: Int,
                            observer: TaskProgressObserver) :
        TTSEventListener(ctx, tts, inputStream, inputSize,
                TASK_ID_READ_TEXT, observer) {

    override fun begin() {
        // Request audio focus.  Finish early if our request was denied.
        if (!app.requestAudioFocus()) {
            finish(false)
            return
        }

        super.begin()
    }

    private fun enqueueSilentUtterance(durationInMs: Long) {
        // We ignore *queueMode* for silent utterances.  It does not make much sense
        // to use QUEUE_FLUSH here.
        tts.playSilentUtterance(durationInMs, QUEUE_ADD, nextUtteranceId())
    }

    private fun enqueueTextUtterance(text: String, bytesRead: Int) {
        if (bytesRead == 0) return

        // Add *bytesRead* to the queue.
        utteranceBytesQueue.add(bytesRead)

        // Add text to the queue as an utterance.
        val bundle = Bundle()
        bundle.putInt(Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        tts.speak(text, queueMode, bundle, nextUtteranceId())
    }

    override fun enqueueNextInput(): Boolean {
        // Have Android's TTS framework to speak some text from the input stream.
        var text = ""
        var bytesRead = 0
        var byte = reader.read()
        while (byte >= 0) {
            // Increment input counter.
            bytesRead++

            // Use the input byte, applying filters as necessary.
            val filter = filterInputByte(byte)
            if (filter.newUtteranceRequired) {
                // Enqueue text current text.
                enqueueTextUtterance(text, bytesRead)
                text = ""
                bytesRead = 0
            }
            if (filter.include) text += byte.toChar()
            when (filter) {
                is Filter.SilentUtteranceFilter -> {
                    enqueueSilentUtterance(filter.durationInMs)

                    // This is enough input.
                    break
                }
                is Filter.NoFilter -> {}
            }

            // Avoid hitting Android's input text limit.  We try to break nicely on
            // a whitespace character close to the limit.  If there is no word
            // boundary close to the limit, we break on the last possible character.
            if (text.length > maxInputLength - 100 && byte.toChar().isWhitespace())
                break
            else if (text.length == maxInputLength)
                break

            // Read the next byte.
            byte = reader.read()
        }

        // Flush text, if any.
        enqueueTextUtterance(text, bytesRead)

        // Return whether there is further input.
        return byte >= 0
    }

    override fun onDone(utteranceId: String?) {
        // Note: This function may be called before an utterance is completely
        // processed.  This appears to be related to our use of silent utterances.
        // With this in mind, wait until the utterance is *really* done before we
        // proceed.
        while (tts.isSpeaking) Thread.sleep(5)
        super.onDone(utteranceId)
    }

    override fun finish(success: Boolean) {
        app.releaseAudioFocus()
        super.finish(success)
    }
}

class FileSynthesisEventListener(ctx: Context, tts: TextToSpeech,
                                 inputStream: InputStream, inputSize: Long,
                                 private val outputStream: OutputStream,
                                 private val waveFilename: String,
                                 progressObserver: TaskProgressObserver) :
        TTSEventListener(ctx, tts, inputStream, inputSize,
                TASK_ID_WRITE_FILE, progressObserver) {

    private var inWaveFiles = mutableListOf<File>()
    private val interruptEvent = InterruptEvent()

    private fun enqueueFileSynthesis(text: String, bytesRead: Int) {
        if (bytesRead == 0) return

        // Create a wave file for this utterance.
        val file = File.createTempFile("utt", "dat", app.cacheDir)

        // Add *bytesRead* to the queue.
        utteranceBytesQueue.add(bytesRead)

        // Enqueue file synthesis.
        val success = tts.synthesizeToFile(text, null, file, file.name)
        if (success == SUCCESS) inWaveFiles.add(file)
    }

    override fun enqueueNextInput(): Boolean {
        // Have Android's TTS framework to synthesize the input into one or more
        // files.  These will be joined together at the end.
        var bytesRead = 0
        var text = ""
        var byte = reader.read()
        while (byte >= 0) {
            // Increment input counter.
            bytesRead++

            // Use the input byte.
            text += byte.toChar()

            /*
            NOTE ON SILENT UTTERANCES

            As far as this programmer can tell, it is currently impossible to add
            silent utterances to wave files with Android's TextToSpeech class.

            It is, however, possible to do manually.  One would need to interweave
            the wave files for each utterance (generated by the engine) with
            specially crafted wave files containing the desired silence in
            milliseconds.
             */

            // Avoid hitting Android's input text limit.  We try to break nicely on
            // a whitespace character close to the limit.  If there is no word
            // boundary close to the limit, we break on the last possible character.
            if (text.length > maxInputLength - 100 && byte.toChar().isWhitespace())
                break
            else if (text.length == maxInputLength)
                break

            // Read the next byte.
            byte = reader.read()
        }

        // Flush text, if any.
        enqueueFileSynthesis(text, bytesRead)

        // Return whether there is further input.
        return byte >= 0
    }

    override fun finalize() {
        super.finalize()

        // Set the interrupt event.
        interruptEvent.interrupt = true
    }

    override fun finish(success: Boolean) {
        // Handle success=true.
        var mSuccess = success
        if (mSuccess) {
            // If the TTS engine has produced impossibly short wave files, filter
            // them out.  These are typically empty files.
            val inWaveFiles = inWaveFiles
                    .filterNot { it.length() < WaveFileHeader.MIN_SIZE }

            // Notify the progress observer that the first task has finished.
            // This is purposefully done here because this listener has two tasks.
            observer.notifyProgress(100, taskId)

            // Notify the progress observer that post-processing has begun.
            taskId = TASK_ID_PROCESS_FILE
            observer.notifyProgress(0, taskId)

            // Join each utterance's wave file into the output file passed to this
            // listener.  Notify the progress observer as files are concatenated.
            try {
                mSuccess = joinWaveFiles(inWaveFiles, outputStream,true,
                        interruptEvent) {
                    p: Int -> observer.notifyProgress(p, taskId)
                }
            } catch (error: RuntimeException) {
                mSuccess = false
                Log.e(TAG, "Failed to join wave ${inWaveFiles.size} files.", error)
            }
        }

        // Ensure all internal wave files are deleted afterwards.
        if (!mSuccess) {
            inWaveFiles.forEach { f ->
                if (f.isFile && f.canWrite()) {
                    f.delete()
                }
            }
        }

        // Display a toast message.
        val messageId = if (mSuccess) R.string.write_to_file_message_success
                        else R.string.write_to_file_message_failure
        val message = app.getString(messageId, waveFilename)
        displayMessage(message, true)

        // Call the super method.
        super.finish(mSuccess)
    }
}
