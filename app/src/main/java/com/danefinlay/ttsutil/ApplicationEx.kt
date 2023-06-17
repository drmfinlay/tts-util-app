/*
 * TTS Util
 *
 * Authors: Dane Finlay <dane@danefinlay.net>
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
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.*
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import org.jetbrains.anko.*
import java.io.File
import java.util.*
import java.util.concurrent.Executors

class ApplicationEx : Application(), OnInitListener, TaskObserver {

    var mTTS: TextToSpeech? = null
        private set

    private var ttsInitialized: Boolean = false
    private var ttsInitErrorMessage: String? = null
    private val taskQueue = ArrayDeque<Task>()
    private val userTaskExecService by lazy { Executors.newSingleThreadExecutor() }
    private val notifyingExecService by lazy { Executors.newSingleThreadExecutor() }
    private val taskObservers = mutableSetOf<TaskObserver>()
    private var notificationBuilder: NotificationCompat.Builder? = null

    @Volatile
    private var currentTaskProgress: Int = 100

    @Volatile
    private var notificationsEnabled: Boolean = false

    private val asyncTaskObserver = object : TaskObserver {
        override fun notifyProgress(progress: Int, taskId: Int) {
            // Submit an executor task to call the application procedure.
            notifyingExecService.submit {
                this@ApplicationEx.notifyProgress(progress, taskId)
            }
        }

        override fun notifyTaskQueueChange(remainingTasks: Int) {
            // Submit an executor task to call the application procedure.
            notifyingExecService.submit {
                this@ApplicationEx.notifyTaskQueueChange(remainingTasks)
            }
        }

        override fun notifyInputSelection(start: Long, end: Long, taskId: Int) {
            // Submit an executor task to call the application procedure.
            notifyingExecService.submit {
                this@ApplicationEx.notifyInputSelection(start, end, taskId)
            }
        }
    }

    val readingTaskInProgress: Boolean
        get () {
            val readingTasks = listOf(TASK_ID_READ_TEXT)
            return taskInProgress && taskQueue.peek()?.id in readingTasks
        }

    val fileSynthesisTaskInProgress: Boolean
        get () {
            val fileSynthesisTasks = listOf(TASK_ID_WRITE_FILE,
                    TASK_ID_PROCESS_FILE)
            return taskInProgress && taskQueue.peek()?.id in fileSynthesisTasks
        }

    val taskInProgress: Boolean
        get () = currentTaskProgress in 0..99

    val taskCount: Int
        get() = taskQueue.size

    val ttsEngineName: String?
        get() {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            return prefs.getString("pref_tts_engine", mTTS?.defaultEngine)
        }

    private val nextTaskMessagesEnabled: Boolean
        get() {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            return prefs.getBoolean("pref_messages_next_task", true)
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
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> stopTask()
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

    fun enableNotifications() {
        if (notificationsEnabled) return

        // Post the current notification, if necessary.
        if (ttsInitialized && taskQueue.size > 0) {
            notifyingExecService.submit { postTaskNotification() }
        }

        // Set notifications as enabled.
        notificationsEnabled = true
    }

    fun disableNotifications() {
        if (!notificationsEnabled) return

        // Cancel the task progress notification, if necessary.
        if (ttsInitialized && taskQueue.size > 0) {
            val id = PROGRESS_NOTIFICATION_ID
            notifyingExecService.submit { cancelNotification(id) }
        }

        // Set notifications as disabled.
        notificationsEnabled = false
    }

    fun cleanupFiles() {
        // Note: Exclude persistent data files here if the application ever requires
        // them.

        // Clean up application files that are no longer needed.
        val filesDirFiles = filesDir.listFiles() ?: arrayOf()
        val cacheDirFiles = cacheDir.listFiles() ?: arrayOf()
        for (f in filesDirFiles + cacheDirFiles) {
            if (f.isFile && f.canWrite()) {
                f.delete()
            }
        }
    }

    fun setupTTS(initListener: OnInitListener, preferredEngine: String?) {
        // Set initialization variables.
        ttsInitialized = false
        ttsInitErrorMessage = null

        // Try to get the preferred engine package from shared preferences if
        // it is null.
        val engineName = preferredEngine ?:
        PreferenceManager.getDefaultSharedPreferences(this)
                .getString("pref_tts_engine", null)

        // Prepare the OnInitListener.
        val wrappedListener = OnInitListener { status ->
            this.onInit(status)
            initListener.onInit(status)
            if (!ttsInitialized) freeTTS()
        }

        // Display a message to the user.
        runOnUiThread { toast(R.string.tts_initializing_message) }

        // Begin text-to-speech initialization.
        mTTS = TextToSpeech(this, wrappedListener, engineName)
    }

    private fun setTTSLanguage(tts: TextToSpeech): Boolean {
        // Use the engine's language setting on SDK version 17 and below.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) return true

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
            message = getString(R.string.using_general_tts_language_msg,
                    locale.displayName)
        }
        if (message != null)  runOnUiThread { longToast(message) }

        // Save the message if failure is indicated.
        if (!success) ttsInitErrorMessage = message

        // Set the language if it is available.
        if (success) tts.language = locale
        return success
    }

    override fun onInit(status: Int) {
        // Handle errors.
        val tts = mTTS
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
            ttsInitErrorMessage = getString(messageId)
            freeTTS()
            return
        }

        // Handle setting the appropriate language.
        if (!setTTSLanguage(tts)) {
            freeTTS()
            return
        }

        // Set the preferred voice if one has been set in the preferences and if
        // this is SDK 21 or above.
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
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

        // Initialization complete.
        ttsInitialized = true

        // If there are one or more tasks in the queue, begin processing.
        val task = taskQueue.peek()
        if (task != null) {
            val result = beginTaskOrNotify(task, false)
            handleTaskResult(result)
        }
    }

    @Synchronized
    fun stopTask(): Boolean {
        val tts = mTTS ?: return true

        // Interrupt TTS synthesis.
        val success = tts.stop() == 0

        // Finalize the current task, if there is one, and clear the queue.
        // If the queue was empty, notify observers that we are idle.
        val idle = taskQueue.size == 0
        taskQueue.peek()?.finalize()
        if (idle) asyncTaskObserver.notifyProgress(100, TASK_ID_IDLE)
        else taskQueue.clear()
        asyncTaskObserver.notifyTaskQueueChange(0)
        return success
    }

    fun openSystemTTSSettings(ctx: Context) {
        // Got this from: https://stackoverflow.com/a/8688354
        val intent = Intent()
        intent.action = "com.android.settings.TTS_SETTINGS"
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        ctx.startActivity(intent)
    }

    fun handleTaskResult(result: Int) {
        // Handle task result codes by displaying a message.  Do nothing for tasks
        // that were begun successfully.
        val task = taskQueue.peek()
        val message: String = when (result) {
            TTS_NOT_READY -> {
                val defaultMessage = getString(R.string.tts_not_ready_message)
                val errorMessage = ttsInitErrorMessage
                errorMessage ?: defaultMessage
            }
            TTS_BUSY -> {
                // The application is currently busy performing another operation.
                val taskDescription = task?.getShortDescription(this) ?: return
                getString(R.string.tts_busy_message, taskDescription)
            }
            UNAVAILABLE_OUT_DIR -> getString(R.string.unavailable_out_dir_message)
            UNAVAILABLE_INPUT_SRC -> getString(R.string.unavailable_input_src_message)
            UNWRITABLE_OUT_DIR -> getString(R.string.unwritable_out_dir_message)
            ZERO_LENGTH_INPUT -> task?.getZeroLengthInputMessage(this) ?: return
            else -> ""
        }

        // Display the message, if appropriate.
        if (message.length > 0) {
            runOnUiThread { longToast(message) }
        }
    }

    fun freeTTS() {
        stopTask()
        mTTS?.shutdown()
        mTTS = null
        ttsInitialized = false
        taskQueue.clear()

        // Cancel the task progress notification, if necessary.
        cancelNotification(PROGRESS_NOTIFICATION_ID)

        // Release audio focus.
        releaseAudioFocus()
    }

    fun reinitialiseTTS(initListener: OnInitListener,
                        preferredEngine: String?) {
        freeTTS()
        setupTTS(initListener, preferredEngine)
    }

    fun addTaskObserver(observer: TaskObserver) {
        taskObservers.add(observer)
    }

    fun deleteTaskObserver(observer: TaskObserver) {
        taskObservers.remove(observer)
    }

    private fun postTaskNotification() {
        // Retrieve the current task, if any.
        val task = taskQueue.peek() ?: return

        // Do not continue if the task has failed.
        val progress = currentTaskProgress
        if (progress == -1) return

        // Initialize the notification builder, if necessary.
        // Note: All tasks share the same builder.
        var builder: NotificationCompat.Builder? = notificationBuilder
        if (builder == null) {
            builder = getNotificationBuilder(this)
            notificationBuilder = builder
        }

        // Build (or re-build) the notification with the specified progress.
        val title = task.getShortDescription(this)
        val text = task.getLongDescription(this, taskQueue.size)
        val notification = builder
                .setContentTitle(title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setProgress(100, progress, false)
                .build()
        notificationManager.notify(PROGRESS_NOTIFICATION_ID, notification)
    }

    fun cancelNotification(id: Int) {
        notificationManager.cancel(id)
    }

    override fun notifyProgress(progress: Int, taskId: Int) {
        // Note: It is acceptable for the task queue to be empty when this method is
        // called, e.g., when progress=100 and taskId=TASK_ID_IDLE.

        // Store the current progress.
        currentTaskProgress = progress

        // Notify registered observers.
        for (observer in taskObservers) {
            observer.notifyProgress(progress, taskId)
        }

        // If there is a finished task in the queue, remove it.
        // Notify registered observers of the change.
        val taskCount = taskQueue.size
        if (taskCount > 0 && progress !in 0..99) {
            taskQueue.pop()
            notifyTaskQueueChange(taskCount - 1)
        }

        // Post a progress notification, if necessary.
        if (notificationsEnabled) postTaskNotification()

        // Start the next task, if there is one.  Cancel the notification if the
        // task ID is different.
        val nextTask = taskQueue.peek()
        if (progress == 100) {
            if (nextTask != null) {
                val result = beginTaskOrNotify(nextTask, true)
                handleTaskResult(result)
            } else {
                cancelNotification(PROGRESS_NOTIFICATION_ID)
            }
        } else if (progress == -1) {
            cancelNotification(PROGRESS_NOTIFICATION_ID)
            taskQueue.clear()
        }
    }

    override fun notifyTaskQueueChange(remainingTasks: Int) {
        // Notify other observers.
        for (observer in taskObservers) {
            observer.notifyTaskQueueChange(remainingTasks)
        }
    }

    override fun notifyInputSelection(start: Long, end: Long, taskId: Int) {
        // Notify other observers.
        for (observer in taskObservers) {
            observer.notifyInputSelection(start, end, taskId)
        }
    }

    private fun beginTaskOrNotify(task: Task, postTaskMessage: Boolean): Int {
        // Return early if TTS is not yet initialized.
        if (!ttsInitialized) return SUCCESS

        // If the specified task is at the head of the queue, begin it and, if
        // appropriate, display an info message.
        var result: Int = SUCCESS
        val currentTask = taskQueue.peek()
        if (currentTask === task) {
            // Begin the task.
            // Note: Each task is expected to notify the observer of failure via
            // `notifyProgress'.
            result = task.begin()

            // If it is appropriate, display an info message.
            if (result == SUCCESS && postTaskMessage && nextTaskMessagesEnabled) {
                val message = task.getBeginTaskMessage(this)
                runOnUiThread { toast(message) }
            }
        }

        // Notify observers of a task queue change and return.
        asyncTaskObserver.notifyTaskQueueChange(taskCount)
        return result
    }

    fun enqueueReadInputTask(inputSource: InputSource, queueMode: Int): Int {
        // Do not continue unless TTS is ready.
        val tts = mTTS ?: return TTS_NOT_READY

        // Handle QUEUE_FLUSH and QUEUE_DESTROY by clearing the task queue and
        // finalizing the current task.
        // Note: There should be no need to call tts.stop().
        if (queueMode in listOf(QUEUE_FLUSH, -2) && taskQueue.size > 0) {
            val currentTask = taskQueue.pop()
            taskQueue.clear()
            currentTask?.finalize()
        }

        // Initialize the task and add it to the queue.
        val task = ReadInputTask(
                this, userTaskExecService, tts, inputSource, asyncTaskObserver,
                queueMode
        )
        taskQueue.add(task)

        // Begin this task, if appropriate.  Return the result code.
        return beginTaskOrNotify(task, taskQueue.size > 1)
    }

    fun enqueueFileSynthesisTasks(inputSource: InputSource, outDirectory: Directory,
                                  waveFilename: String): Int {
        // Do not continue unless TTS is ready.
        val tts = mTTS ?: return TTS_NOT_READY

        // Initialise the mutable list of files to be populated by the first task
        // and processed by the second.
        val inWaveFiles = mutableListOf<File>()

        // Initialise a FileSynthesisTask and add it to the queue.
        val task1 = FileSynthesisTask(
                this, userTaskExecService, tts, inputSource, asyncTaskObserver,
                outDirectory, waveFilename, inWaveFiles
        )
        taskQueue.add(task1)

        // Initialize a ProcessWaveFilesTask and add it to the queue.
        val task2 = ProcessWaveFilesTask(
                this, userTaskExecService, inputSource, asyncTaskObserver,
                outDirectory, waveFilename, inWaveFiles
        )
        taskQueue.add(task2)

        // Begin task one, if appropriate.  Return the result code.
        return beginTaskOrNotify(task1, taskQueue.size > 2)
    }

    override fun onLowMemory() {
        super.onLowMemory()

        // Stop and free the current text-to-speech engine.
        freeTTS()
    }
}
