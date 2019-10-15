package com.danefinlay.ttsutil

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID
import org.jetbrains.anko.runOnUiThread
import org.jetbrains.anko.toast
import java.io.File

class Speaker(private val context: Context,
              var speechAllowed: Boolean,
              onReady: Speaker.() -> Unit = {}) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)

    private val appCtx: ApplicationEx
        get() = context.applicationContext as ApplicationEx

    var onReady: Speaker.() -> Unit = onReady
        set(value) {
            field = value
            if ( ready ) value()
        }

    var ready = false
        private set
    private var lastUtteranceWasFileSynthesis: Boolean = false

    private var currentUtteranceId: Long = 0
    private fun getUtteranceId(): String {
        val id = currentUtteranceId
        currentUtteranceId += 1
        return "$id"
    }

    override fun onInit(status: Int) {
        when ( status ) {
            TextToSpeech.SUCCESS -> {
                ready = true
                onReady()
            }
        }
    }

    fun speak(string: String?) {
        // Split the text on any new lines to get a list. Utterance pauses will be
        // inserted this way.
        val lines = string?.split("\n") ?: return
        speak(lines)
    }

    fun speak(lines: List<String?>) {
        if (!(ready && speechAllowed)) {
            return
        }

        // Stop possible file synthesis before speaking.
        if (lastUtteranceWasFileSynthesis) {
            tts.stop()
            lastUtteranceWasFileSynthesis = false
        }

        // Set the listener.
        val listener = SpeakingEventListener(appCtx)
        tts.setOnUtteranceProgressListener(listener)

        // Get Android's TTS framework to speak each non-null line.
        // This is, quite typically, different in some versions of Android.
        val nonEmptyLines = lines.mapNotNull { it }.filter { !it.isBlank() }
        val streamKey = TextToSpeech.Engine.KEY_PARAM_STREAM
        var utteranceId: String? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val bundle = Bundle()
            bundle.putInt(streamKey, AudioManager.STREAM_MUSIC)
            nonEmptyLines.forEach {
                utteranceId = getUtteranceId()
                tts.speak(it, TextToSpeech.QUEUE_ADD, bundle, utteranceId)
                pause(100)
            }
        } else {
            val streamValue = AudioManager.STREAM_MUSIC.toString()
            nonEmptyLines.forEach {
                utteranceId = getUtteranceId()
                val map = hashMapOf(streamKey to streamValue,
                        KEY_PARAM_UTTERANCE_ID to utteranceId)

                @Suppress("deprecation")  // handled above.
                tts.speak(it, TextToSpeech.QUEUE_ADD, map)
                pause(100)
            }
        }

        // Set the listener's final utterance ID.
        listener.finalUtteranceId = utteranceId
    }

    fun pause(duration: Long, listener: SpeakerEventListener) {
        // Set the listener.
        tts.setOnUtteranceProgressListener(listener)
        pause(duration)
    }

    @Suppress("SameParameterValue")
    private fun pause(duration: Long) {
        val utteranceId = getUtteranceId()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.playSilentUtterance(duration, TextToSpeech.QUEUE_ADD,
                    utteranceId)
        } else {
            @Suppress("deprecation")
            tts.playSilence(duration, TextToSpeech.QUEUE_ADD,
                    hashMapOf(KEY_PARAM_UTTERANCE_ID to utteranceId))
        }

    }

    fun synthesizeToFile(text: String, outFile: File,
                         listener: SpeakerEventListener) {
        // Stop speech before synthesizing.
        if (tts.isSpeaking) {
            tts.stop()
            context.runOnUiThread {
                toast(getString(R.string.pre_file_synthesis_msg))
            }
        }

        // Set the listener.
        tts.setOnUtteranceProgressListener(listener)

        // Get an utterance ID.
        val utteranceId = getUtteranceId()

        if (Build.VERSION.SDK_INT >= 21) {
            tts.synthesizeToFile(text, null, outFile, utteranceId)
        } else {
            @Suppress("deprecation")
            tts.synthesizeToFile(
                    text, hashMapOf(KEY_PARAM_UTTERANCE_ID to utteranceId),
                    outFile.absolutePath)
        }

        // Set an internal variable for keeping track of file synthesis.
        lastUtteranceWasFileSynthesis = true
    }

    fun stopSpeech() {
        if ( tts.isSpeaking ) {
            tts.stop()
        }
    }

    fun free() {
        // Stop speech and free resources.
        stopSpeech()
        tts.shutdown()
    }
}

/**
 * Returns `true` if the Speaker is initialised and ready to speak.
 */
fun Speaker?.isReady(): Boolean {
    return this != null && this.ready && this.speechAllowed
}
