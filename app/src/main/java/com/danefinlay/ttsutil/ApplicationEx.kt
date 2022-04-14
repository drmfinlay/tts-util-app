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
import android.speech.tts.TextToSpeech.QUEUE_FLUSH
import android.support.v4.app.NotificationCompat
import android.support.v4.provider.DocumentFile
import android.support.v7.preference.PreferenceManager
import org.jetbrains.anko.*
import java.io.*
import java.util.*

class ApplicationEx : Application(), OnInitListener, TaskProgressObserver {

    private class TaskData(var taskId: Int,
                           var progress: Int,
                           val listener: MyUtteranceProgressListener)

    var mTTS: TextToSpeech? = null
        private set

    private var currentTaskData: TaskData? = null
    private val taskQueue = ArrayDeque<TaskData>()
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

    val readingTaskInProgress: Boolean
        get () {
            val readingTasks = listOf(TASK_ID_READ_TEXT)
            return taskInProgress && currentTaskData?.taskId in readingTasks
        }

    val fileSynthesisTaskInProgress: Boolean
        get () {
            val fileSynthesisTasks = listOf(TASK_ID_WRITE_FILE,
                    TASK_ID_PROCESS_FILE)
            return taskInProgress && currentTaskData?.taskId in fileSynthesisTasks
        }

    val taskInProgress: Boolean
        get () = currentTaskData?.progress in 0..99

    val ttsEngineName: String?
        get() {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            return prefs.getString("pref_tts_engine", mTTS?.defaultEngine)
        }

    var notificationsEnabled: Boolean = true
        set(value) {
            if (!field && value) {
                // If re-enabling, post the current notification, if any.
                val taskData = currentTaskData
                if (taskData != null) {
                    postNotification(taskData.progress, taskData.taskId)
                }
            } else if (!value) {
                // Cancel any TTS notifications present.
                notificationTasks.forEach {notificationManager.cancel(it) }
            }
            field = value
        }

    private val audioFocusGain = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
    private val audioFocusRequest: AudioFocusRequest by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
        val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(audioFocusRequest)
        } else {
            @Suppress("deprecation")
            audioManager.requestAudioFocus(
                    onAudioFocusChangeListener, AudioManager.STREAM_MUSIC,
                    audioFocusGain)
        }

        // Check the success value.
        return success == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun releaseAudioFocus() {
        // Abandon the audio focus using the same context and
        // AudioFocusChangeListener used to request it.
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
                R.string.tts_initialization_failure_msg
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

    @Synchronized
    fun stopSpeech(): Boolean {
        val tts = mTTS ?: return true

        // Clear the task queue.
        taskQueue.clear()

        // If the TTS engine is speaking, stop speech synthesis.
        var success = false
        if (tts.isSpeaking) success = tts.stop() == 0

        // If there is a task in progress, finalize it.
        // Otherwise, notify observers that we are idle.
        val taskData = currentTaskData
        if (taskData != null) {
            taskData.listener.finalize()
            currentTaskData = null
        } else {
            notifyProgress(100, TASK_ID_IDLE)
        }
        return success
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
            TTS_BUSY -> {
                // Inform the user that the application is currently busy performing
                // another operation.
                val currentTaskTextId = when (currentTaskData?.taskId) {
                    TASK_ID_READ_TEXT ->
                        R.string.speaking_notification_title
                    TASK_ID_WRITE_FILE ->
                        R.string.synthesis_notification_title
                    TASK_ID_PROCESS_FILE ->
                        R.string.post_synthesis_notification_title
                    else -> return
                }
                val message = getString(R.string.tts_busy_message,
                        getString(currentTaskTextId))
                runOnUiThread { longToast(message) }
            }
        }
    }

    fun freeTTS() {
        stopSpeech()
        mTTS?.shutdown()
        mTTS = null
        taskQueue.clear()
        currentTaskData = null

        // Cancel any TTS notifications present.
        notificationTasks.forEach { notificationManager.cancel(it) }

        // Release audio focus.
        releaseAudioFocus()
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

    @Synchronized
    override fun notifyProgress(progress: Int, taskId: Int) {
        // Post a notification, if necessary.
        if (notificationsEnabled && taskId in notificationTasks) {
            postNotification(progress, taskId)
        }

        // Save current task attributes, if necessary.
        var taskData = currentTaskData
        if (taskData != null) {
            taskData.progress = progress
            taskData.taskId = taskId
        }

        // Notify other observers.
        progressObservers.forEach { it.notifyProgress(progress, taskId) }

        // Do nothing further for intermediate tasks.
        if (taskId in listOf(TASK_ID_WRITE_FILE)) return

        // Handle a finished task.  If there is a queued task, begin it.
        // Otherwise, finalize the current one.
        val finished = progress == 100 || progress == -1
        if (finished) {
            if (taskQueue.size > 0) {
                taskData = taskQueue.pop()
                currentTaskData = taskData

                // Note: We begin from the main thread only because the work cannot,
                // at least in practice, be done on any binder thread calling this
                // function.  This does not result in dropped frames, but should
                // probably be done with a dedicated background thread any way.
                runOnUiThread { taskData.listener.begin() }
            } else {
                currentTaskData?.listener?.finalize()
                currentTaskData = null
            }
        }
    }

    private inline fun useContentUri(
            contentUri: Uri?,
            block: (inStream: InputStream, size: Long) -> Int): Int {
        // Open an input stream on and retrieve the size of the URI's content,
        // if possible, and invoke the given function.
        if (contentUri?.isAccessibleFile(this) == true) {
            val inStream = contentUri.openContentInputStream(this, true)
            val fileSize = contentUri.getFileSize(this)
            if (inStream != null && fileSize != null) {
                return block(inStream, fileSize)
            }
        }

        // The given URI was invalid.
        return INVALID_FILE_URI
    }

    fun speak(inStream: InputStream, size: Long, queueMode: Int): Int {
        val tts = mTTS

        // If TTS is not yet ready, return early.
        if (!ttsReady || tts == null) {
            inStream.close()
            return TTS_NOT_READY
        }

        // If file synthesis is in process, return early.
        // The user must stop the current operation manually.
        if (fileSynthesisTaskInProgress) {
            inStream.close()
            return TTS_BUSY
        }

        // Handle QUEUE_FLUSH and QUEUE_DESTROY by clearing the task queue and
        // finalizing the current task.
        // Note: There should be no need to call tts.stop().
        if (queueMode in listOf(QUEUE_FLUSH, -2)) {
            taskQueue.clear()
            currentTaskData?.listener?.finalize()
            currentTaskData = null
        }

        // Initialize the event listener and task data.
        val listener = SpeakingEventListener(this, tts, inStream, size, queueMode,
                this)
        val taskData = TaskData(TASK_ID_READ_TEXT, 0, listener)

        // If a task is currently in process, enqueue this one.
        // Otherwise, tell the listener to begin reading.
        if (currentTaskData != null) {
            taskQueue.add(taskData)
        } else {
            currentTaskData = taskData
            listener.begin()
        }
        return SUCCESS
    }

    fun synthesizeToFile(inStream: InputStream, size: Long,
                         outDirectory: Directory, waveFilename: String): Int {
        val tts = mTTS

        // Verify that *outDirectory* is valid.  If it is, then open an output
        // stream on the specified wave file.  If it is not, return early.
        // Note: Directory types are handled separately here because the
        // DocumentFile.fromFile() method, if used, causes trouble later on.
        var outStream: OutputStream? = null
        when (outDirectory) {
            is Directory.DocumentTree -> {
                val uri = outDirectory.uri

                // Open an output stream on the specified file and directory.
                // Return early if the Uri is invalid.
                val dir = DocumentFile.fromTreeUri(this, uri)
                if (dir == null || !dir.exists()) {
                    return INVALID_OUT_DIR
                }

                // TODO Handle already existing wave files by creating new files:
                //  '<filename>.wav (1)', <filename>.wav (2), etc.
                // Create a new wave file, if necessary.
                var file = dir.findFile(waveFilename)
                if (file == null) file = dir.createFile("audio/x-wav", waveFilename)

                // If successful, open an output stream on it and invoke the given
                // function.
                outStream = file?.uri?.openContentOutputStream(this, false)
            }
            is Directory.FileDir -> {
                val file = File(outDirectory.file, waveFilename)
                outStream = FileOutputStream(file)
            }
        }
        if (outStream == null) return INVALID_OUT_DIR

        // If TTS is not yet ready, return early.
        if (!ttsReady || tts == null) {
            inStream.close()
            outStream.close()
            return TTS_NOT_READY
        }

        // If text is currently being read, return early.
        // The user must stop the task manually.
        // This should allow for queueing of file synthesis events.
        if (readingTaskInProgress) {
            inStream.close()
            outStream.close()
            return TTS_BUSY
        }

        // Initialize the event listener and task data.
        val listener = FileSynthesisEventListener(this, tts, inStream, size,
                outStream, waveFilename, this)
        val taskData = TaskData(TASK_ID_WRITE_FILE, 0, listener)

        // If a task is currently in process, enqueue this one.
        // Otherwise, tell the listener to begin file synthesis.
        if (currentTaskData != null) {
            taskQueue.add(taskData)
        } else {
            currentTaskData = taskData
            listener.begin()
        }
        return SUCCESS
    }

    fun speak(text: String, queueMode: Int): Int {
        // Get an input stream from the text.  This is done because
        val inStream = ByteArrayInputStream(text.toByteArray())
        val size = text.length.toLong()
        return speak(inStream, size, queueMode)
    }

    fun synthesizeToFile(text: String, outDirectory: Directory,
                         waveFilename: String): Int {
        val inStream = ByteArrayInputStream(text.toByteArray())
        return synthesizeToFile(inStream, text.length.toLong(), outDirectory,
                waveFilename)
    }

    fun speak(contentUri: Uri?, queueMode: Int): Int {
        // Speak the URI content, if possible.
        return useContentUri(contentUri) {
            inStream, size -> speak(inStream, size, queueMode)
        }
    }

    fun synthesizeToFile(contentUri: Uri?, outDirectory: Directory,
                         waveFilename: String): Int {
        // Synthesize speech from the URI content into the specified wave file, if
        // possible.
        return useContentUri(contentUri) { inStream, size ->
            synthesizeToFile(inStream, size, outDirectory, waveFilename)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()

        // Stop and free the current text-to-speech engine.
        freeTTS()
    }
}
