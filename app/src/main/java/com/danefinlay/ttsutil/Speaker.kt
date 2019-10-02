package com.danefinlay.ttsutil

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.jetbrains.anko.audioManager
import java.io.File

typealias ProgressListener = (Speaker.UtteranceProgress) -> Unit

class Speaker(private val context: Context,
              var speechAllowed: Boolean,
              onReady: Speaker.() -> Unit = {}) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)
    var onReady: Speaker.() -> Unit = onReady
        set(value) {
            field = value
            if ( ready ) value()
        }

    var ready = false
        private set

    private val audioFocusGain = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
    private val audioFocusRequest: AudioFocusRequest by lazy {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .build()
            AudioFocusRequest.Builder(audioFocusGain)
                    .setAudioAttributes(audioAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(onAudioFocusChangeListener)
                    .build()
        } else
            throw RuntimeException("should not use AudioFocusRequest below SDK v26")
    }

    private var utteranceId: Long = 1
        get() {
            val current = field
            field++
            return current
        }

    override fun onInit(status: Int) {
        when ( status ) {
            TextToSpeech.SUCCESS -> {
                ready = true
                onReady()
            }
        }
    }

    private val onAudioFocusChangeListener = AudioManager.OnAudioFocusChangeListener {
        focusChange ->
        when ( focusChange ) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent loss of audio focus
                // Pause playback immediately
                stopSpeech()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Pause playback
                stopSpeech()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Lower the volume, keep playing
                stopSpeech()
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                // Your app has been granted audio focus again
                // Raise volume to normal, restart playback if necessary
            }

        }
    }

    private fun requestAudioFocus(): Boolean {
        val audioManager = context.audioManager
        val success = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("deprecation")
            audioManager.requestAudioFocus(
                    onAudioFocusChangeListener, AudioManager.STREAM_ALARM,
                    audioFocusGain)
        } else {
            audioManager.requestAudioFocus(audioFocusRequest)
        }

        // Check the success value.
        return success == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun releaseAudioFocus() {
        // abandon the audio focus using the same context and AudioFocusChangeListener used
        // in requestAudioFocusResult to request it
        val audioManager = context.audioManager
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("deprecation")
            audioManager.abandonAudioFocus(onAudioFocusChangeListener)
        } else {
            audioManager.abandonAudioFocusRequest(audioFocusRequest)
        }
    }

    sealed class UtteranceProgress(val utteranceId: String?) {
        class Start(utteranceId: String?) : UtteranceProgress(utteranceId)
        class Error(utteranceId: String?, errorCode: Int) :
                UtteranceProgress(utteranceId)
        class Done(utteranceId: String?) : UtteranceProgress(utteranceId)
    }

    private fun setOnUtteranceListener(progressListener: ProgressListener = {}) {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                progressListener(UtteranceProgress.Start(utteranceId))
            }

            override fun onError(utteranceId: String?) { // deprecated
                onError(utteranceId, -1)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                super.onError(utteranceId, errorCode)
                progressListener(UtteranceProgress.Error(utteranceId, errorCode))
            }

            override fun onDone(utteranceId: String?) {
                progressListener(UtteranceProgress.Done(utteranceId))
            }
        })
    }

    fun speak(string: String?, progressListener: ProgressListener = {}) {
        speak(listOf(string), progressListener)
    }

    fun speak(lines: List<String?>, progressListener: ProgressListener = {}) {
        if (!(ready && speechAllowed)) {
            return
        }

        // Set up an utterance listener.
        var utterancesMade = 0
        setOnUtteranceListener {
            when (it) {
                is UtteranceProgress.Start -> {
                    if (utterancesMade == 0) requestAudioFocus()
                    else pause(100)
                }

                is UtteranceProgress.Done -> utterancesMade++
            }

            if (utterancesMade == lines.size) releaseAudioFocus()

            // Run the external progressListener too.
            progressListener(it)
        }

        // Get Android's TTS framework to speak each non-null line.
        // This is, quite typically, different in some versions of Android.
        val streamKey = TextToSpeech.Engine.KEY_PARAM_STREAM
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val bundle = Bundle()
            bundle.putInt(streamKey, AudioManager.STREAM_MUSIC)
            lines.mapNotNull {
                val utteranceId = "$utteranceId"
                tts.speak(it, TextToSpeech.QUEUE_ADD, bundle, utteranceId)
            }
        } else {
            val streamValue = AudioManager.STREAM_MUSIC.toString()
            val map = hashMapOf(streamKey to streamValue)
            lines.mapNotNull {
                @Suppress("deprecation")  // handled above.
                tts.speak(it, TextToSpeech.QUEUE_ADD, map)
            }
        }
    }

    fun pause(duration: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.playSilentUtterance(duration, TextToSpeech.QUEUE_ADD, utteranceId.toString())
        } else {
            @Suppress("deprecation")
            tts.playSilence(duration, TextToSpeech.QUEUE_ADD, null)
        }
    }

    fun synthesizeToFile(text: String, outFile: File,
                         progressListener: ProgressListener = {}) {
        // TODO Allow sharing file paths to the app.
        setOnUtteranceListener(progressListener)
        if (Build.VERSION.SDK_INT >= 21) {
            tts.synthesizeToFile(text, null, outFile,
                    "$utteranceId")
        } else {
            @Suppress("deprecation")
            tts.synthesizeToFile(text, hashMapOf<String, String>(),
                    outFile.absolutePath)
        }
    }

    fun stopSpeech() {
        if ( tts.isSpeaking ) {
            tts.stop()
        }
        releaseAudioFocus()
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
