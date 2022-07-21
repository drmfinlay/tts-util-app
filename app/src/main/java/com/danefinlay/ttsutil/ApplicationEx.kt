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
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.*
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import org.jetbrains.anko.*
import java.io.InputStream
import java.util.*
import java.util.concurrent.Executors

class ApplicationEx : Application(), OnInitListener {

    var mTTS: TextToSpeech? = null
        private set

    private var ttsInitialized: Boolean = false
    private var ttsInitErrorMessage: String? = null
    private var lastAttemptedTaskId: Int? = null
    private var currentTask: Task? = null
    private val taskQueue = ArrayDeque<TaskData>()
    private val userTaskExecService by lazy { Executors.newSingleThreadExecutor() }
    private val notifyingExecService by lazy { Executors.newSingleThreadExecutor() }
    private val progressObservers = mutableSetOf<TaskProgressObserver>()
    private var notificationBuilder: NotificationCompat.Builder? = null

    @Volatile
    private var notificationsEnabled: Boolean = false

    private val asyncProgressObserver = object : TaskProgressObserver {
        override fun notifyProgress(progress: Int, taskId: Int,
                                    remainingTasks: Int) {
            // Submit a executor task to call the application's notifyProgress()
            // procedure.
            notifyingExecService.submit {
                this@ApplicationEx.notifyProgress(progress, taskId, remainingTasks)
            }
        }
    }

    val readingTaskInProgress: Boolean
        get () {
            val readingTasks = listOf(TASK_ID_READ_TEXT)
            return taskInProgress && taskQueue.peek()?.taskId in readingTasks
        }

    val fileSynthesisTaskInProgress: Boolean
        get () {
            val fileSynthesisTasks = listOf(TASK_ID_WRITE_FILE,
                    TASK_ID_PROCESS_FILE)
            return taskInProgress && taskQueue.peek()?.taskId in fileSynthesisTasks
        }

    val taskInProgress: Boolean
        get () = taskQueue.peek()?.progress in 0..99

    private val unfinishedTaskCount: Int
        get() {
            // Determine the total number of unfinished tasks.
            val queueSize = taskQueue.size
            var result = queueSize
            val taskData = taskQueue.peek()
            if (taskData?.progress == 100) result -= 1
            else if (taskData?.progress == -1) result = 0
            return result
        }

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

    fun enableNotifications() {
        if (notificationsEnabled) return

        // Post the current notification, if necessary.
        if (ttsInitialized && taskQueue.size > 0) notifyingExecService.submit {
            val taskData = taskQueue.peek()
            if (taskData != null) postNotification(taskData, unfinishedTaskCount)
        }

        // Set notifications as enabled.
        notificationsEnabled = true
    }

    fun disableNotifications() {
        if (!notificationsEnabled) return

        // Cancel any TTS notifications present, if necessary.
        if (ttsInitialized && taskQueue.size > 0) notifyingExecService.submit {
            notificationTasks.forEach { notificationManager.cancel(it) }
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
        val taskData = taskQueue.peek()
        if (taskData != null) {
            val ttsOpRes = beginTaskOrNotify(taskData, false)
            handleTTSOperationResult(ttsOpRes)
        }
    }

    @Synchronized
    fun stopSpeech(): Boolean {
        val tts = mTTS ?: return true

        // Interrupt TTS synthesis.
        val success = tts.stop() == 0

        // Stop the current task, if there is one, and clear the queue.
        // If the queue was empty, notify observers that we are idle.
        val idle = taskQueue.size == 0
        taskQueue.clear()
        currentTask?.finalize()
        currentTask = null
        if (idle) {
            asyncProgressObserver.notifyProgress(100, TASK_ID_IDLE, 0)
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
        // FIXME Cleanup this repetitive function.
        when (result) {
            TTS_NOT_READY -> {
                val defaultMessage = getString(R.string.tts_not_ready_message)
                val errorMessage = ttsInitErrorMessage
                val notReadyMessage = errorMessage ?: defaultMessage
                runOnUiThread { longToast(notReadyMessage) }
            }
            TTS_BUSY -> {
                // Inform the user that the application is currently busy performing
                // another operation.
                val currentTaskTextId = when (taskQueue.peek()?.taskId) {
                    TASK_ID_READ_TEXT ->
                        R.string.reading_notification_title
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
            UNAVAILABLE_OUT_DIR -> {
                val message = getString(R.string.unavailable_out_dir_message)
                runOnUiThread { longToast(message) }
            }
            UNAVAILABLE_INPUT_SRC -> {
                val message = getString(R.string.unavailable_input_src_message)
                runOnUiThread { longToast(message) }
            }
            UNWRITABLE_OUT_DIR -> {
                val message = getString(R.string.unwritable_out_dir_message)
                runOnUiThread { longToast(message) }
            }
            ZERO_LENGTH_INPUT -> {
                val messageId = when (lastAttemptedTaskId) {
                    TASK_ID_READ_TEXT -> R.string.cannot_read_empty_input_message
                    TASK_ID_WRITE_FILE -> R.string.cannot_synthesize_empty_input_message
                    else -> return
                }
                val message = getString(messageId)
                runOnUiThread { longToast(message) }
            }
        }
    }

    fun freeTTS() {
        stopSpeech()
        mTTS?.shutdown()
        mTTS = null
        ttsInitialized = false
        taskQueue.clear()
        currentTask = null

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

    private fun postNotification(taskData: TaskData, remainingTasks: Int) {
        // Do nothing if the given task ID is, e.g., TASK_ID_IDLE.
        val taskId = taskData.taskId
        val progress = taskData.progress
        if (taskId !in notificationTasks) return

        // Initialize the notification builder using the given task ID.
        var builder: NotificationCompat.Builder? = notificationBuilder
        if (builder == null) {
            builder = getNotificationBuilder(this, taskId)
            notificationBuilder = builder
        }

        // Build (or re-build) the notification with the specified progress if the
        // task has not yet been completed.
        if (progress in 0..99) {
            val title = getNotificationTitle(this, taskData)
            val text = getNotificationText(this, taskData, remainingTasks)
            val notification = builder
                    .setContentTitle(title)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setProgress(100, progress, false)
                    .build()
            notificationManager.notify(taskId, notification)
        }

        // Cancel the notification only if the task is finished and there are no
        // remaining tasks.
        // Note: This is to prevent the scenario where our notification is canceled
        // once one task is finished only for another, very similar notification to
        // be posted a fraction of a second later!
        else if (remainingTasks == 0) {
            notificationManager.cancel(taskId)
            notificationBuilder = null
        }
    }

    private fun notifyProgress(progress: Int, taskId: Int, remainingTasks: Int) {
        // Note: The size of the taskQueue should not be assumed here.
        // Retrieve the current task data and update its progress attribute, if
        // necessary.
        val taskData = taskQueue.peek()
        if (taskData != null && taskData.taskId == taskId) {
            taskData.progress = progress
        }

        // Determine the total number of unfinished tasks.
        val totalUnfinishedTasks = remainingTasks + unfinishedTaskCount

        // Post a progress notification, if necessary.
        if (notificationsEnabled && taskData != null) {
            postNotification(taskData, totalUnfinishedTasks)
        }

        // Notify other observers.
        for (observer in progressObservers) {
            observer.notifyProgress(progress, taskId, totalUnfinishedTasks)
        }

        // If the task has finished, finalize it and remove it from the queue.
        // Clear the current task if this is the last one.
        if (progress == 100 || progress == -1) {
            currentTask?.finalize()
            if (taskQueue.size > 0) taskQueue.pop()
            if (taskQueue.size == 0) currentTask = null
        }

        // If the task finished successfully and there are more tasks in the queue,
        // then start the next one.  If the task finished unsuccessfully, clear the
        // queue.
        val nextTaskData = taskQueue.peek()
        if (progress == 100 && nextTaskData != null) {
            val result = beginTaskOrNotify(nextTaskData, true)
            handleTTSOperationResult(result)
        } else if (progress == -1) {
            taskQueue.clear()
            currentTask = null
        }
    }

    @Synchronized
    private fun speak(taskData: TaskData.ReadInputTaskData): Int {
        val tts = mTTS

        // Set this task as under consideration.
        lastAttemptedTaskId = taskData.taskId

        // Return early if it is not possible or not appropriate to proceed.
        if (tts == null) return TTS_NOT_READY

        // Use the input source to open an input stream and retrieve the content
        // size in bytes.  Return early if this is not possible.
        val inputSource = taskData.inputSource
        var inputStream: InputStream? = null
        var inputSize: Long? = null
        if (inputSource.isSourceAvailable(this)) {
            inputStream = inputSource.openInputStream(this)
            inputSize = inputSource.getSize(this)
        }
        if (inputStream == null || inputSize == null) return UNAVAILABLE_INPUT_SRC

        // Verify that the input stream is at least one byte long.
        if (inputSize == 0L) return ZERO_LENGTH_INPUT

        // Initialize the task, begin it asynchronously and return.
        val task = ReadInputTask(this, tts, inputStream, inputSize,
                taskData.queueMode, asyncProgressObserver)
        currentTask = task
        userTaskExecService.submit { task.begin() }
        return SUCCESS
    }

    @Synchronized
    private fun synthesizeToFile(taskData: TaskData.FileSynthesisTaskData): Int {
        val tts = mTTS

        // Set this task as under consideration.
        lastAttemptedTaskId = taskData.taskId

        // Return early if it is not possible or not appropriate to proceed.
        if (tts == null) return TTS_NOT_READY

        // Use the input source to open an input stream and retrieve the content
        // size in bytes.  Return early if this is not possible.
        val inputSource = taskData.inputSource
        var inputStream: InputStream? = null
        var inputSize: Long? = null
        if (inputSource.isSourceAvailable(this)) {
            inputStream = inputSource.openInputStream(this)
            inputSize = inputSource.getSize(this)
        }
        if (inputStream == null || inputSize == null) return UNAVAILABLE_INPUT_SRC

        // Verify that the input stream is at least one byte long.
        if (inputSize == 0L) return ZERO_LENGTH_INPUT

        // Verify that the out directory exists and that we have permission to
        // create files in it.
        val outDirectory = taskData.outDirectory
        val waveFilename = taskData.waveFilename
        if (!outDirectory.exists(this)) return UNAVAILABLE_OUT_DIR
        if (!outDirectory.canWrite(this)) return UNWRITABLE_OUT_DIR

        // Retrieve the mutable file list initialized earlier on.  This list will
        // be used by the next task if this one is successful.
        val inWaveFiles = taskData.inWaveFiles

        // Initialize the task, begin it asynchronously and return.
        val task = FileSynthesisTask(this, tts, inputStream, inputSize,
                waveFilename, inWaveFiles, asyncProgressObserver)
        currentTask = task
        userTaskExecService.submit { task.begin() }
        return SUCCESS
    }

    @Synchronized
    private fun processWaveFiles(taskData: TaskData.ProcessWaveFilesTaskData): Int {
        // Set this task as under consideration.
        lastAttemptedTaskId = taskData.taskId

        // Retrieve the previous task's data.
        val prevTaskData = taskData.prevTaskData

        // Use the out directory to create and open an output stream on the
        // specified document.  Return early if this is not possible.
        val outDirectory = prevTaskData.outDirectory
        val waveFilename = prevTaskData.waveFilename
        if (!outDirectory.exists(this)) return UNAVAILABLE_OUT_DIR
        val outputStream = outDirectory.openDocumentOutputStream(this,
                waveFilename, "audio/x-wav") ?: return UNWRITABLE_OUT_DIR

        // Initialize the task, begin it asynchronously and return.
        val task = ProcessWaveFilesTask(this, asyncProgressObserver,
                prevTaskData.inWaveFiles, outputStream, waveFilename)
        currentTask = task
        userTaskExecService.submit { task.begin() }
        return SUCCESS
    }

    private fun beginTaskOrNotify(taskData: TaskData, priorTask: Boolean): Int {
        // Return early if TTS is not yet initialized.
        if (!ttsInitialized) return SUCCESS

        // If the specified task is at the head of the queue, begin it.
        // Otherwise, notify the observer.
        val observer = asyncProgressObserver
        val result: Int
        if (taskQueue.peek() === taskData && ttsInitialized) {
            // Begin the task, taking note of information that might be used later.
            val infoMessageId: Int
            val srcDescription: CharSequence
            when (taskData) {
                is TaskData.ReadInputTaskData -> {
                    result = speak(taskData)
                    infoMessageId = R.string.begin_reading_source_message
                    srcDescription = taskData.inputSource.description
                }
                is TaskData.FileSynthesisTaskData -> {
                    result = synthesizeToFile(taskData)
                    infoMessageId = R.string.begin_synthesizing_source_message
                    srcDescription = taskData.inputSource.description
                }
                is TaskData.ProcessWaveFilesTaskData -> {
                    result = processWaveFiles(taskData)
                    infoMessageId = R.string.begin_processing_source_message
                    srcDescription = taskData.prevTaskData.inputSource.description
                }
            }

            // If it is appropriate, display an info message.
            if (result == SUCCESS && priorTask && nextTaskMessagesEnabled) {
                val message = getString(infoMessageId, srcDescription)
                runOnUiThread { toast(message) }
            }

            // If the task failed to start, remove the appropriate number of tasks
            // from the queue and notify the observer.
            else if (result != SUCCESS) {
                val taskCount = when (taskData) {
                    is TaskData.ReadInputTaskData -> 1
                    is TaskData.FileSynthesisTaskData -> 2
                    is TaskData.ProcessWaveFilesTaskData -> 1
                }
                for (i in 0 until taskCount) taskQueue.pop()
                observer.notifyProgress(-1, taskData.taskId, 0)
            }
        } else {
            val item1: TaskData = taskQueue.peek()!!
            observer.notifyProgress(item1.progress, item1.taskId, 0)
            result = SUCCESS
        }
        return result
    }

    fun enqueueReadInputTask(inputSource: InputSource, queueMode: Int): Int {
        // Do not continue unless TTS is ready.
        if (mTTS == null) return TTS_NOT_READY

        // Handle QUEUE_FLUSH and QUEUE_DESTROY by clearing the task queue and
        // finalizing the current task.
        // Note: There should be no need to call tts.stop().
        if (queueMode in listOf(QUEUE_FLUSH, -2)) {
            taskQueue.clear()
            currentTask?.finalize()
            currentTask = null
        }

        // Encapsulate task data and add it to the queue.
        val taskData = TaskData.ReadInputTaskData(TASK_ID_READ_TEXT, 0,
                inputSource, queueMode)
        taskQueue.add(taskData)

        // Process the task if it is at the head of the queue.  Otherwise, notify
        // progress observers.
        return beginTaskOrNotify(taskData, currentTask != null)
    }

    fun enqueueFileSynthesisTasks(inputSource: InputSource, outDirectory: Directory,
                                  waveFilename: String): Int {
        // Do not continue unless TTS is ready.
        if (mTTS == null) return TTS_NOT_READY

        // Encapsulate the file synthesis task data and add it to the queue.
        val taskData1 = TaskData.FileSynthesisTaskData(TASK_ID_WRITE_FILE, 0,
                inputSource, outDirectory, waveFilename, mutableListOf())
        taskQueue.add(taskData1)

        // Encapsulate the process wave files task data and add it to the queue.
        val taskData2 = TaskData.ProcessWaveFilesTaskData(TASK_ID_PROCESS_FILE, 0,
                taskData1)
        taskQueue.add(taskData2)

        // Process the task if it is at the head of the queue.  Otherwise, notify
        // progress observers.
        return beginTaskOrNotify(taskData1, currentTask != null)
    }

    override fun onLowMemory() {
        super.onLowMemory()

        // Stop and free the current text-to-speech engine.
        freeTTS()
    }
}
