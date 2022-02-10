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

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.net.Uri
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.support.v4.app.NotificationCompat
import android.support.v7.preference.PreferenceManager
import org.jetbrains.anko.*
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.*

class ApplicationEx : Application(), OnInitListener, TaskProgressObserver {

    var mTTS: TextToSpeech? = null
        private set

    private var utteranceProgressListener: MyUtteranceProgressListener? = null
    private var lastUtteranceWasFileSynthesis: Boolean = false
    private var errorMessage: String? = null
    private val progressObservers = mutableSetOf<TaskProgressObserver>()
    private var notificationBuilder: NotificationCompat.Builder? = null

    /**
     * Whether the TTS is ready to synthesize text into speech.
     *
     * This should be changed by the OnInitListener.
     * */
    var ttsReady = false
        private set

    val ttsEngineName: String?
        get() {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            return prefs.getString("pref_tts_engine", mTTS?.defaultEngine)
        }

    var notificationsEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) {
                // Cancel any TTS notifications present.
                notificationTasks.forEach {notificationManager.cancel(it) }
            }
        }

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

    private val onAudioFocusChangeListener = OnAudioFocusChangeListener {
        focusChange ->
        when ( focusChange ) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                stopSpeech()
            }
        }
    }

    fun requestAudioFocus(): Boolean {
        val audioManager = audioManager
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

    fun releaseAudioFocus() {
        // abandon the audio focus using the same context and AudioFocusChangeListener used
        // in requestAudioFocusResult to request it
        val audioManager = audioManager
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("deprecation")
            audioManager.abandonAudioFocus(onAudioFocusChangeListener)
        } else {
            audioManager.abandonAudioFocusRequest(audioFocusRequest)
        }
    }

    fun cleanupFiles() {
        // Note: Exclude persistent data files here if the application ever requires
        // them.

        // Clean up no longer needed internal files.
        (filesDir.listFiles() + cacheDir.listFiles()).forEach { f ->
            if (f.isFile && f.canWrite()) {
                f.delete()
            }
        }
    }

    fun setupTTS(initListener: OnInitListener, preferredEngine: String?) {
        if (mTTS == null) {
            // Try to get the preferred engine package from shared preferences if
            // it is null.
            val engineName = preferredEngine ?:
            PreferenceManager.getDefaultSharedPreferences(this)
                    .getString("pref_tts_engine", null)

            // Initialize the TTS object.  Wrap the given OnInitListener.
            val wrappedListener = OnInitListener { status ->
                this.onInit(status)
                initListener.onInit(status)
            }

            mTTS = TextToSpeech(this, wrappedListener, engineName)
        }
    }

    private fun setTTSLanguage(tts: TextToSpeech): Boolean {
        // Find an acceptable TTS language.
        val startLocale = tts.currentLocale
        var locale: Locale? = null
        if (startLocale != null) {
            locale = tts.findAcceptableTTSLanguage(startLocale)
        }

        // Attempt to fallback on the system language, if necessary.
        // If this is also unacceptable, try the JVM default.
        if (locale == null) {
            val sysLocale = currentSystemLocale
            if (sysLocale != null) {
                locale = tts.findAcceptableTTSLanguage(sysLocale)
            }
        }
        if (locale == null) {
            locale = tts.findAcceptableTTSLanguage(Locale.getDefault())
        }

        // Set and display a warning message, if necessary.
        val success = locale != null
        var message: String? = null
        if (locale == null) {
            // Neither the selected nor the default languages are
            // available.
            message = getString(R.string.no_tts_language_available_msg)
        } else if (startLocale == null || startLocale.language != locale.language ||
                startLocale.country != locale.country) {
            // A language is available, but it is one that the user might not be
            // expecting.
            message= getString(R.string.using_general_tts_language_msg,
                    locale.displayName)
        }
        if (message != null)  runOnUiThread { longToast(message) }

        // Save the message if failure is indicated.
        if (!success) errorMessage = message

        // Set the language if it is available.
        if (success) tts.language = locale
        return success
    }

    override fun onInit(status: Int) {
        // Handle errors.
        val tts = mTTS
        errorMessage = null
        if (status == TextToSpeech.ERROR || tts == null) {
            // Check the number of available TTS engines and set an appropriate
            // error message.
            val engines = tts?.engines ?: listOf()
            val messageId = if (engines.isEmpty()) {
                // No usable TTS engines.
                R.string.no_engine_available_message
            } else {
                // General TTS initialisation failure.
                R.string.tts_initialisation_failure_msg
            }
            runOnUiThread { longToast(messageId) }
            // Save the error message ID for later use, free TTS and return.
            errorMessage = getString(messageId)
            freeTTS()
            return
        }

        // Handle setting the appropriate language.
        if (!setTTSLanguage(tts)) return

        // Success: TTS is ready.
        ttsReady = true

        // Set the preferred voice if one has been set in the preferences.
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val voiceName = prefs.getString("pref_tts_voice", null)
        if (voiceName != null) {
            val voices = tts.voicesEx.toList().filterNotNull()
            if (voices.isNotEmpty()) {
                val voiceNames = voices.map { it.name }
                val voiceIndex = voiceNames.indexOf(voiceName)
                tts.voiceEx = if (voiceIndex == -1) {
                    tts.voiceEx ?: tts.defaultVoiceEx
                } else voices[voiceIndex]
            }
        }

        // Set the preferred pitch if one has been set in the preferences.
        val preferredPitch = prefs.getFloat("pref_tts_pitch", -1.0f)
        if (preferredPitch > 0) {
            tts.setPitch(preferredPitch)
        }

        // Set the preferred speech rate if one has been set in the preferences.
        val preferredSpeechRate = prefs.getFloat("pref_tts_speech_rate", -1.0f)
        if (preferredSpeechRate > 0) {
            tts.setSpeechRate(preferredSpeechRate)
        }
    }

    fun clearUtteranceProgressListener() {
        mTTS?.setOnUtteranceProgressListener(null)
        utteranceProgressListener = null
    }

    fun stopSpeech(): Boolean {
        // If the TTS engine is speaking, stop speech synthesis and return the
        // success.
        val tts = mTTS ?: return true
        if (tts.isSpeaking) return tts.stop() == 0

        // If utteranceProgressListener is set, it means that it did not finish
        // correctly, perhaps because the TTS engine application or the service was
        // restarted.  The listener's finish() method is called here as a remedy.
        utteranceProgressListener?.finish(false)

        // Notify progress observers.
        // Since this stops TTS operations, the following notifyProgress() call
        // will, if a TTS operation is in progress, be closely followed by another
        // call with parameters (-1, TASK_ID_X).
        notifyProgress(100, TASK_ID_IDLE)
        return true
    }

    fun openSystemTTSSettings(ctx: Context) {
        // Got this from: https://stackoverflow.com/a/8688354
        val intent = Intent()
        intent.action = "com.android.settings.TTS_SETTINGS"
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        ctx.startActivity(intent)
    }

    fun handleTTSOperationResult(result: Int) {
        when (result) {
            TTS_NOT_READY -> {
                val defaultMessage = getString(R.string.tts_not_ready_message)
                val errorMessage = errorMessage
                val notReadyMessage = errorMessage ?: defaultMessage
                runOnUiThread { longToast(notReadyMessage) }
            }
        }
    }

    fun freeTTS() {
        stopSpeech()
        mTTS?.shutdown()
        mTTS = null
        utteranceProgressListener = null

        // Cancel any TTS notifications present.
        notificationTasks.forEach {notificationManager.cancel(it) }
    }

    fun reinitialiseTTS(initListener: OnInitListener,
                        preferredEngine: String?) {
        freeTTS()
        setupTTS(initListener, preferredEngine)
    }

    fun addProgressObserver(observer: TaskProgressObserver) {
        progressObservers.add(observer)
    }

    fun deleteProgressObserver(observer: TaskProgressObserver) {
        progressObservers.remove(observer)
    }

    private fun postNotification(progress: Int, taskId: Int) {
        // Initialize the notification builder using the given task ID.
        var builder: NotificationCompat.Builder? = notificationBuilder
        if (builder == null) {
            builder = getNotificationBuilder(this, taskId)
            notificationBuilder = builder
        }

        // Build (or re-build) the notification with the specified progress if the
        // task has not yet been completed.
        if (progress in 0..99) {
            val notification = builder
                    .setProgress(100, progress, false)
                    .build()
            notificationManager.notify(taskId, notification)
        }

        // Clean up if the task is complete (progress=100) or if an error occurred
        // (progress<0).
        else {
            notificationManager.cancel(taskId)
            notificationBuilder = null
        }
    }

    override fun notifyProgress(progress: Int, taskId: Int) {
        // Post a notification, if necessary.
        // TODO Make use of activity lifecycle callbacks to disable notifications
        //  when the activity is active.
        if (notificationsEnabled && taskId in notificationTasks) {
            postNotification(progress, taskId)
        }

        // Notify other observers.
        progressObservers.forEach { it.notifyProgress(progress, taskId) }
    }

    fun speak(inStream: InputStream, size: Long, queueMode: Int): Int {
        val tts = mTTS

        // If TTS is not yet ready, return early.
        if (!ttsReady || tts == null) return TTS_NOT_READY

        // Stop possible file synthesis before speaking.
        if (lastUtteranceWasFileSynthesis && tts.isSpeaking) stopSpeech()

        // Initialize an event listener and tell it to begin reading.
        val listener = SpeakingEventListener(this, tts, inStream, size, queueMode,
                this)
        utteranceProgressListener = listener
        listener.begin()
        return SUCCESS
    }

    fun synthesizeToFile(inputStream: InputStream, size: Long, outFile: File): Int {
        val tts = mTTS
        // If TTS is not yet ready, return early.
        if (!ttsReady || tts == null) return TTS_NOT_READY

        // Initialize an event listener and tell it to begin synthesis.
        val listener = FileSynthesisEventListener(this, tts, inputStream, size, outFile,
                this)
        utteranceProgressListener = listener
        listener.begin()

        // Set an internal variable for keeping track of file synthesis.
        // This is because we allow user interactions to enqueue speech events, but
        // not file synthesis events since it would lead to awkward silence.
        lastUtteranceWasFileSynthesis = true
        return SUCCESS
    }

    fun speak(text: String, queueMode: Int): Int {
        // Get an input stream from the text.  This is done because
        val inStream = ByteArrayInputStream(text.toByteArray())
        val size = text.length.toLong()
        return speak(inStream, size, queueMode)
    }

    fun synthesizeToFile(text: String, outFile: File): Int {
        val inStream = ByteArrayInputStream(text.toByteArray())
        return synthesizeToFile(inStream, text.length.toLong(), outFile)
    }

    private inline fun useUri(
            uri: Uri?,
            block: (inStream: InputStream, size: Long) -> Int): Int {
        // Open an input stream on and retrieve the size of the URI's content,
        // if possible, and invoke the given function.
        if (uri?.isAccessibleFile(this) == true) {
            val inStream = uri.openContentInputStream(this)
            val fileSize = uri.getFileSize(this)
            if (inStream != null && fileSize != null) {
                return block(inStream, fileSize)
            }
        }

        // The given URI was invalid.
        return INVALID_FILE_URI
    }

    fun speak(uri: Uri?, queueMode: Int): Int {
        // Speak the URI content, if possible.
        return useUri(uri) {
            inStream, size -> speak(inStream, size, queueMode)
        }
    }

    fun synthesizeToFile(uri: Uri?, outFile: File): Int {
        // Synthesize speech from the URI content, if possible.
        return useUri(uri) { inStream, size ->
            synthesizeToFile(inStream, size, outFile)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()

        // Stop and free the current text-to-speech engine.
        freeTTS()
    }
}
