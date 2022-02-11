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
import java.io.BufferedReader
import java.io.File
import java.io.InputStream

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
    open fun begin() {
        tts.setOnUtteranceProgressListener(this)
    }

    override fun onError(utteranceId: String?) { // deprecated
        onError(utteranceId, -1)
    }

    @CallSuper
    open fun finish(success: Boolean) {
        tts.setOnUtteranceProgressListener(null)
        app.clearUtteranceProgressListener()
    }
}


abstract class TTSEventListener(ctx: Context,
                                tts: TextToSpeech,
                                private val inputStream: InputStream,
                                private val inputSize: Long,
                                protected var taskId: Int,
                                val observer: TaskProgressObserver) :
        MyUtteranceProgressListener(ctx, tts) {

    protected val reader: BufferedReader = inputStream.bufferedReader()
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

        // Enqueue the next (first) input.
        // This starts the process of processing the stream.
        streamHasFurtherInput = enqueueNextInput()

        // Notify the progress listener that work has begun.
        observer.notifyProgress(0, taskId)
    }

    override fun finish(success: Boolean) {
        super.finish(success)

        // Notify the progress observer that the task is finished or if an error
        // occurred.
        val progress = if (success) 100 else -1
        observer.notifyProgress(progress, taskId)

        // Close the reader and input stream.
        reader.close()
        inputStream.close()
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
        // Note: This function may be called before an utterance is completely
        // processed.  That is why the indicated progress sometimes jumps ahead and
        // hits 100% before the voice stops.

        // Add processed bytes to *inputProcessed*.
        inputProcessed += utteranceBytesQueue.removeAt(0)

        // Calculate and notify the progress listener.
        val progress = inputProcessed.toFloat() / inputSize * 100
        observer.notifyProgress(progress.toInt(), taskId)

        // Enqueue the next input.  If we have reached the end of the stream, then
        // finish.
        if (streamHasFurtherInput) streamHasFurtherInput = enqueueNextInput()
        else finish(true)
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

    private var audioFocusRequestGranted = false

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

    override fun onStart(utteranceId: String?) {
        // Only request audio focus if we don't already have it.
        if (!audioFocusRequestGranted) {
            audioFocusRequestGranted = app.requestAudioFocus()
        }
    }

    override fun finish(success: Boolean) {
        super.finish(success)
        app.releaseAudioFocus()
    }
}

class FileSynthesisEventListener(ctx: Context, tts: TextToSpeech,
                                 inputStream: InputStream, inputSize: Long,
                                 private val outFile: File,
                                 progressObserver: TaskProgressObserver) :
        TTSEventListener(ctx, tts, inputStream, inputSize,
                TASK_ID_WRITE_FILE, progressObserver) {

    private var inWaveFiles = mutableListOf<File>()

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

    override fun finish(success: Boolean) {
        super.finish(success)

        // Handle success=true.
        var mSuccess = success
        if (mSuccess) {
            // If the TTS engine has produced impossibly short wave files, filter
            // them out.  These are typically empty files.
            val inWaveFiles = inWaveFiles
                    .filterNot { it.length() < WaveFileHeader.MIN_SIZE }

            // Notify the progress observer that post-processing has begun.
            taskId = TASK_ID_PROCESS_FILE
            observer.notifyProgress(0, taskId)

            // Join each utterance's wave file into the output file passed to this
            // listener.  Notify the progress observer as files are concatenated.
            // TODO This method can be long-running and should be stopped if the
            //  user requests it (via the stop button).
            try {
                joinWaveFiles(inWaveFiles, outFile, deleteFiles = true) {
                    p: Int -> observer.notifyProgress(p, taskId)
                }
                observer.notifyProgress(100, taskId)
            } catch (error: RuntimeException) {
                Log.e(TAG, "Failed to join wave ${inWaveFiles.size} files.", error)
                observer.notifyProgress(-1, taskId)
                mSuccess = false
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
        val msg = if (mSuccess) {
            app.getString(R.string.write_to_file_message_success,
                    outFile.name)
        } else {
            app.getString(R.string.wave_file_error_msg)
        }
        displayMessage(msg, true)
    }
}
