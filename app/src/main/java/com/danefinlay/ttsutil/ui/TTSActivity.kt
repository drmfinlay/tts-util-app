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

package com.danefinlay.ttsutil.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.LANG_MISSING_DATA
import android.support.v4.app.Fragment
import android.support.v4.provider.DocumentFile
import com.danefinlay.ttsutil.*
import org.jetbrains.anko.AlertDialogBuilder
import org.jetbrains.anko.longToast
import org.jetbrains.anko.toast
import java.util.*

/**
 * Abstract activity class inherited from classes that use text-to-speech in some
 * way.
 */
abstract class TTSActivity: MyAppCompatActivity(), TextToSpeech.OnInitListener,
        ActivityInterface, TaskProgressObserver {

    private val idleStatusEvent =
            ActivityEvent.StatusUpdateEvent(100, TASK_ID_IDLE, 0)

    protected var mLastStatusUpdate = idleStatusEvent
    protected var mLastChosenDirEvent: ActivityEvent.ChosenFileEvent? = null
    protected var mLastChosenFileEvent: ActivityEvent.ChosenFileEvent? = null

    protected var ttsInitAttempted: Boolean = false

    private fun retrieveChosenFileData(prefs: SharedPreferences, uriKey: String,
                                       nameKey: String, fileType: Int):
            ActivityEvent.ChosenFileEvent? {
        // Retrieve the saved chosen file data.
        val uriPrefString = prefs.getString(uriKey, "")
        val displayNamePrefString = prefs.getString(nameKey, null)
        if (uriPrefString == null || displayNamePrefString == null) return null

        // Note: The Uri and display names are delimited by null characters to
        // This is done to avoid mangling; filenames cannot typically include this
        // character.
        val uri = uriPrefString.split(Char.MIN_VALUE).filter { it.length > 0 }
                .map { Uri.parse(it) }
        val displayNames = displayNamePrefString.split(Char.MIN_VALUE)
                .filter { it.length > 0 }
        return ActivityEvent.ChosenFileEvent(uri, displayNames, fileType)
    }

    private fun saveChosenFileData(prefs: SharedPreferences, uriKey: String,
                                   nameKey: String,
                                   event: ActivityEvent.ChosenFileEvent) {
        // Save chosen file data delimited by null characters.
        val uriString = event.uriList.fold("") { acc, uri ->
            acc + uri.toString() + Char.MIN_VALUE
        }
        val displayNamesString = event.displayNameList.fold("") { acc, s ->
            acc + s + Char.MIN_VALUE
        }
        prefs.edit()
                .putString(uriKey, uriString)
                .putString(nameKey, displayNamesString)
                .apply()  // apply() is asynchronous.
    }

    private fun restoreLastStatusUpdate(savedInstanceState: Bundle):
            ActivityEvent.StatusUpdateEvent {
        // Restore the last status update event, falling back on the idle status
        // if it is out-of-date or unavailable.
        // Note: This preserves task success/failure statuses.
        var result: ActivityEvent.StatusUpdateEvent? =
                savedInstanceState.getParcelable("mLastStatusUpdate")
        if (result == null || result.progress in 0..99 &&
                        !myApplication.taskInProgress) result = idleStatusEvent
        return result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register as a task progress observer.
        myApplication.addProgressObserver(this)

        // Restore persistent data, if necessary.
        if (savedInstanceState == null) {
            val prefs = getSharedPreferences(packageName, MODE_PRIVATE)
            val event1 = retrieveChosenFileData(prefs, CHOSEN_DIR_URI_KEY,
                    CHOSEN_DIR_NAME_KEY, DIR_SELECT_CODE)
            if (event1 != null) mLastChosenDirEvent = event1
            val event2 = retrieveChosenFileData(prefs, CHOSEN_FILE_URI_KEY,
                    CHOSEN_FILE_NAME_KEY, FILE_SELECT_CODE)
            if (event2 != null) mLastChosenFileEvent = event2
            return
        }

        // Restore instance data.
        savedInstanceState.run {
            mLastStatusUpdate = restoreLastStatusUpdate(savedInstanceState)
            mLastChosenFileEvent = getParcelable("mLastChosenFileEvent")
            mLastChosenDirEvent = getParcelable("mLastChosenDirEvent")
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        // Save data.
        outState?.run {
            putParcelable("mLastStatusUpdate", mLastStatusUpdate)
            putParcelable("mLastChosenFileEvent", mLastChosenFileEvent)
        }
    }

    protected fun handleActivityEvent(event: ActivityEvent,
                                      fragments: List<Fragment>) {
        // Iterate each attached fragment and, if it implements the right interface,
        // use it to handle this event.
        for (fragment in fragments) {
            if (fragment !is FragmentInterface) continue
            fragment.handleActivityEvent(event)
        }
    }

    override fun initializeTTS(initListener: TextToSpeech.OnInitListener?) {
        // Initialize TTS, if necessary.  Since this may take some time, inform the
        // user with a short message.
        if (!ttsInitAttempted && myApplication.mTTS == null) {
            runOnUiThread { toast(R.string.tts_initializing_message) }
            val wrappedListener = TextToSpeech.OnInitListener { status ->
                this.onInit(status)
                initListener?.onInit(status)
            }
            myApplication.setupTTS(wrappedListener, null)
            ttsInitAttempted = true
        }
    }

    override fun onInit(status: Int) {
        // Note: This onInit() handles only what an activity needs to.
        // ApplicationEx does most of the setup originally done here.
        val tts = myApplication.mTTS ?: return

        // Ask the user to install missing voice data, if necessary for the selected
        // language and if the TTS engine reports one or more available voices.
        // Note: countAvailableVoices() is more reliable when voice data is not yet
        // downloaded, at least with Google's text-to-speech engine, which, in this
        // case, defaults to an available voice.
        val language = tts.currentLocale
        val languageUnavailable = (
                language == null ||
                tts.isLanguageAvailable(language) == LANG_MISSING_DATA ||
                tts.countAvailableVoices(language) == 0
        )
        if (tts.voicesEx.size > 0 && languageUnavailable) {
            runOnUiThread { showNoTTSDataDialog(tts, language) }
        }

        // Emit a TTS ready event.
        if (status == TextToSpeech.SUCCESS) {
            handleActivityEvent(ActivityEvent.TTSReadyEvent())
        }
    }

    private fun showNoTTSDataDialog(tts: TextToSpeech, language: Locale?) {
        AlertDialogBuilder(this).apply {
            // Set the title.
            title(R.string.no_tts_data_alert_title)

            // Get the engine info.
            // Note: An engine should be available at this point.
            val engine = tts.engines.find { it.name == tts.defaultEngine }!!

            // Set the message text.
            val messageText = getString(R.string.no_tts_data_alert_message,
                    language?.displayName, engine.label
            )
            message(messageText)

            // Set buttons and show the dialog.
            positiveButton(R.string.alert_positive_message_2) {
                startInstallTTSDataActivity()
            }
            negativeButton(R.string.alert_negative_message_2)
            show()
        }
    }

    override fun requestSampleTTSText() {
        // Initialize the start activity intent.
        // Note: This action may be used to retrieve sample text for specific
        // localities.
        val intent = Intent(TextToSpeech.Engine.ACTION_GET_SAMPLE_TEXT)

        // Retrieve the current engine package name, if possible, and set it as
        // the target package.  This tells the system to exclude other TTS
        // engine packages installed.
        val packageName = myApplication.ttsEngineName
        if (packageName != null) intent.setPackage(packageName)

        // Start the appropriate activity for requesting TTS sample text from the
        // engine, falling back on ours if an exception occurs.
        try {
            startActivityForResult(intent, SAMPLE_TEXT_CODE)
        } catch (e: ActivityNotFoundException) {
            // Dispatch an event with the sample text.
            val sampleText = getString(R.string.sample_tts_sentence)
            val event = ActivityEvent.SampleTextReceivedEvent(sampleText)
            handleActivityEvent(event)
        }
    }

    protected fun startFileChooserActivity(intent: Intent, chooserTitle: String,
                                           requestCode: Int) {
        try {
            val chooserIntent = Intent.createChooser(intent, chooserTitle)
            startActivityForResult(chooserIntent, requestCode)
        } catch (ex: ActivityNotFoundException) {
            // Potentially direct the user to the Market with a Dialog.
            longToast(getString(R.string.no_file_manager_msg))
        }
    }

    override fun showFileChooser() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "text/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        val title = getString(R.string.file_chooser_title)
        startFileChooserActivity(intent, title, FILE_SELECT_CODE)
    }

    override fun showDirChooser(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        val title = getString(R.string.dir_chooser_title)
        startFileChooserActivity(intent, title, requestCode)
    }

    override fun getLastStatusUpdate() = mLastStatusUpdate
    override fun getLastFileChosenEvent() = mLastChosenFileEvent
    override fun getLastDirChosenEvent() = mLastChosenDirEvent

    override fun notifyProgress(progress: Int, taskId: Int, remainingTasks: Int) {
        // Inform each compatible fragment of the progress via a status update
        // event.  Ensure that this runs on the main thread.
        val event = ActivityEvent.StatusUpdateEvent(progress, taskId,
                remainingTasks)
        runOnUiThread { handleActivityEvent(event) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int,
                                  data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Disable notifications on reentry.  This prevents notifications from being
        // posted while sample text is being read or after a file/directory is
        // chosen.
        myApplication.disableNotifications()

        when {
            requestCode == SAMPLE_TEXT_CODE -> {
                // Note: Sample text may be available if resultCode is
                // RESULT_CANCELLED.  Therefore, we do not check resultCode.
                // This apparent error may be explained by the peculiar nature of
                // the activity, which, for each engine this programmer has tried,
                // behaves like a trampoline.
                val key = TextToSpeech.Engine.EXTRA_SAMPLE_TEXT
                val sampleText = if (data != null && data.hasExtra(key)) {
                    // Retrieve the sample text.
                    data.getStringExtra(key)
                } else {
                    // Engine sample text unavailable.  Falling back on ours.
                    getString(R.string.sample_tts_sentence)
                }

                // Dispatch an event with the sample text.
                val event = ActivityEvent.SampleTextReceivedEvent(sampleText)
                handleActivityEvent(event)
            }
            requestCode == DIR_SELECT_CODE && resultCode == RESULT_OK ||
            requestCode == DIR_SELECT_CONT_CODE && resultCode == RESULT_OK -> {
                // Get the Uri of the selected directory, if possible.
                val uri = data?.data ?: return

                // Determine the display name.
                val documentFile = DocumentFile.fromTreeUri(this, uri)
                val dirName = documentFile?.name
                var displayName: String
                if (dirName == null) {
                    displayName = getString(R.string.generic_output_dir)
                } else {
                    displayName = """"$dirName""""
                }

                // Use a description of the storage volume instead, if appropriate.
                if (uri.path?.endsWith(":") == true) {
                    val volumeDesc = documentFile?.uri
                            ?.resolveStorageVolumeDescription(this)
                    if (volumeDesc != null) displayName = volumeDesc
                }

                // Set shared preference and property values.
                val event = ActivityEvent.ChosenFileEvent(listOf(uri),
                        listOf(displayName), requestCode)
                saveChosenFileData(getSharedPreferences(packageName, MODE_PRIVATE),
                        CHOSEN_DIR_URI_KEY, CHOSEN_DIR_NAME_KEY, event)
                mLastChosenDirEvent = event

                // Send a file chosen event.
                handleActivityEvent(event)
            }
            requestCode == FILE_SELECT_CODE && resultCode == RESULT_OK -> {
                // Get the Uri of the selected file(s), if possible.
                if (data == null) return
                val uriList = mutableListOf<Uri>()
                val clipData = data.clipData
                if (clipData == null) {
                    val uri = data.data
                    if (uri != null) uriList.add(uri)
                } else {
                    for (i in 0 until clipData.itemCount) {
                        uriList.add(clipData.getItemAt(i).uri)
                    }
                }

                // Set display names for each Uri.
                val displayNames = mutableListOf<String>()
                val fallbackName = getString(R.string.fallback_filename)
                for (uri in uriList) {
                    val displayName = uri.retrieveFileDisplayName(this, true)
                    displayNames.add(displayName ?: fallbackName)
                }

                val event = ActivityEvent.ChosenFileEvent(uriList, displayNames,
                        requestCode)

                // Set shared preference and property values.
                saveChosenFileData(getSharedPreferences(packageName, MODE_PRIVATE),
                        CHOSEN_FILE_URI_KEY, CHOSEN_FILE_NAME_KEY, event)
                mLastChosenFileEvent = event

                // Send a file chosen event.
                handleActivityEvent(event)
            }
        }
    }

    fun startInstallTTSDataActivity() {
        // Initialize the start activity intent.
        val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)

        // Retrieve the current engine package name, if possible, and set it as
        // the target package.  This tells the system to exclude other TTS
        // engine packages installed.
        val packageName = myApplication.ttsEngineName
        if (packageName != null) intent.setPackage(packageName)

        // Start the appropriate activity, displaying a warning message if no TTS
        // engine is available.
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast(R.string.no_engine_available_message)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        myApplication.deleteProgressObserver(this)
    }
}
