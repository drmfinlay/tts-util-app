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

package com.danefinlay.ttsutil.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.LANG_MISSING_DATA
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
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
        ActivityInterface, TaskObserver {

    private val idleStatusEvent =
            ActivityEvent.StatusUpdateEvent(100, TASK_ID_IDLE, 0)

    protected var mLastStatusUpdate = idleStatusEvent
    private var mLastChosenDirEvent: ActivityEvent.ChosenFileEvent? = null
    private var mLastChosenFileEvent: ActivityEvent.ChosenFileEvent? = null

    private fun retrieveChosenFileData(prefs: SharedPreferences, uriKey: String,
                                       nameKey: String, localeKeyPrefix: String,
                                       requestCode: Int):
            ActivityEvent.ChosenFileEvent? {
        // Retrieve the saved chosen file data.
        val uriPrefString = prefs.getString(uriKey, "") ?: return null
        val displayNamePrefString = prefs.getString(nameKey, null)
                ?: return null

        // Note: The Uri and display names are delimited by null characters to
        // This is done to avoid mangling; filenames cannot typically include this
        // character.
        val uriList = uriPrefString.split(Char.MIN_VALUE).filter { it.length > 0 }
                .map { Uri.parse(it) }
        var displayNames = displayNamePrefString.split(Char.MIN_VALUE)
                .filter { it.length > 0 }

        // Retrieve the locale associated with the display names from shared
        // preferences.
        val localeLang = prefs.getString(localeKeyPrefix + "_LANG", "en")!!
        val localeCountry = prefs.getString(localeKeyPrefix + "_COUNTRY", "US")!!
        val localeVariant = prefs.getString(localeKeyPrefix + "_VARIANT", "")!!
        val locale = Locale(localeLang, localeCountry, localeVariant)

        // Re-retrieve the display names if the current system locale doesn't match
        // the associated locale, falling back on stored display names where new
        // ones could not be retrieved.
        val newLocale = currentSystemLocale ?: Locale.getDefault()
        if (locale != newLocale) {
            val newDisplayNames = mutableListOf<String>()
            for ((uri, name) in uriList.zip(displayNames)) {
                val newName: String?
                if (requestCode == FILE_SELECT_CODE) {
                    newName = uri.retrieveFileDisplayName(this, true)
                } else {
                    newName = uri.retrieveDirDisplayName(this)
                }
                newDisplayNames.add(newName ?: name)
            }
            displayNames = newDisplayNames
        }
        return ActivityEvent.ChosenFileEvent(uriList, displayNames, newLocale,
                requestCode)
    }

    private fun saveChosenFileData(prefs: SharedPreferences, uriKey: String,
                                   nameKey: String, localeKeyPrefix: String,
                                   event: ActivityEvent.ChosenFileEvent) {
        // Save chosen file data delimited by null characters.
        val uriString = event.uriList.fold("") { acc, uri ->
            acc + uri.toString() + Char.MIN_VALUE
        }
        val displayNamesString = event.displayNameList.fold("") { acc, s ->
            acc + s + Char.MIN_VALUE
        }

        // Retrieve the locale to be stored with the display names.
        // Note: This will always be the current system locale.
        val locale = event.locale

        // Save data to shared preferences.
        prefs.edit()
                .putString(uriKey, uriString)
                .putString(nameKey, displayNamesString)
                .putString(localeKeyPrefix + "_LANG", locale.language)
                .putString(localeKeyPrefix + "_COUNTRY", locale.country)
                .putString(localeKeyPrefix + "_VARIANT", locale.variant)
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

        // Register as a task observer.
        myApplication.addTaskObserver(this)

        // Restore instance data.
        savedInstanceState?.run {
            mLastStatusUpdate = restoreLastStatusUpdate(savedInstanceState)
            mLastChosenFileEvent = getParcelable("mLastChosenFileEvent")
            mLastChosenDirEvent = getParcelable("mLastChosenDirEvent")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save data.
        outState.run {
            putParcelable("mLastStatusUpdate", mLastStatusUpdate)
            putParcelable("mLastChosenFileEvent", mLastChosenFileEvent)
            putParcelable("mLastChosenDirEvent", mLastChosenDirEvent)
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
        // Initialize TTS, if necessary.
        if (myApplication.mTTS == null) {
            val wrappedListener = TextToSpeech.OnInitListener { status ->
                this.onInit(status)
                initListener?.onInit(status)
            }
            myApplication.setupTTS(wrappedListener, null)
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
        var languageUnavailable: Boolean =
                language == null ||
                tts.isLanguageAvailable(language) == LANG_MISSING_DATA
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            languageUnavailable =
                    (languageUnavailable || language == null ||
                    tts.countAvailableVoices(language) == 0) &&
                    tts.voicesEx.size > 0 // Engine reports one or more voices.
        }
        if (languageUnavailable) {
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
            val engineName = myApplication.ttsEngineName
            val engine = tts.engines.find { it.name == engineName }!!

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
        // Start the appropriate activity for requesting TTS sample text from the
        // engine, falling back on ours if this is not possible or if an exception
        // occurs.
        val ourSampleText = getString(R.string.sample_tts_sentence)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            val event = ActivityEvent.SampleTextReceivedEvent(ourSampleText)
            handleActivityEvent(event)
            return
        }

        // Initialize the start activity intent.
        // Note: This action may be used to retrieve sample text for specific
        // localities.
        val intent = Intent(TextToSpeech.Engine.ACTION_GET_SAMPLE_TEXT)

        // Retrieve the current engine package name, if possible, and set it as
        // the target package.  This tells the system to exclude other TTS
        // engine packages installed.
        val packageName = myApplication.ttsEngineName
        if (packageName != null) intent.setPackage(packageName)

        // Start the appropriate activity.
        try {
            startActivityForResult(intent, SAMPLE_TEXT_CODE)
        } catch (e: ActivityNotFoundException) {
            // Failure: Dispatch an event with the sample text.
            val event = ActivityEvent.SampleTextReceivedEvent(ourSampleText)
            handleActivityEvent(event)
        }
    }

    private fun startFileChooserActivity(intent: Intent, chooserTitle: String,
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
        // Determine which intent action to use.
        val intentAction: String
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            intentAction = Intent.ACTION_OPEN_DOCUMENT
        } else {
            intentAction = Intent.ACTION_GET_CONTENT
        }

        // Initialize the intent.
        val intent = Intent(intentAction).apply {
            type = "text/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        val title = getString(R.string.file_chooser_title)
        startFileChooserActivity(intent, title, FILE_SELECT_CODE)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun showDirChooser(requestCode: Int) {
        // Start a file chooser activity for choosing a directory with persistable
        // read/write permission.
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        val flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        intent.addFlags(flags)
        val title = getString(R.string.dir_chooser_title)
        startFileChooserActivity(intent, title, requestCode)
    }

    override fun getLastStatusUpdate() = mLastStatusUpdate

    override fun getLastFileChosenEvent(): ActivityEvent.ChosenFileEvent? {
        // Check if the last event's locale differs from the current system locale.
        // If it does, then the event should be re-retrieved.
        if (mLastChosenFileEvent?.locale != currentSystemLocale) {
            mLastChosenFileEvent = null
        }

        // Attempt to restore the data concerning the last chosen file(s) from
        // shared preferences, if necessary.
        if (mLastChosenFileEvent == null) {
            val prefs = getSharedPreferences(packageName, MODE_PRIVATE)
            val event = retrieveChosenFileData(prefs, CHOSEN_FILE_URI_KEY,
                    CHOSEN_FILE_NAME_KEY, CHOSEN_FILE_LOCALE_KEY, FILE_SELECT_CODE)
            if (event != null) mLastChosenFileEvent = event
        }
        return mLastChosenFileEvent
    }

    override fun getLastDirChosenEvent(): ActivityEvent.ChosenFileEvent? {
        // Check if the last event's locale differs from the current system locale.
        // If it does, then the event should be re-retrieved.
        if (mLastChosenDirEvent?.locale != currentSystemLocale) {
            mLastChosenDirEvent = null
        }

        // Attempt to restore the data concerning the last chosen directory from
        // shared preferences, if necessary.
        if (mLastChosenDirEvent == null) {
            val prefs = getSharedPreferences(packageName, MODE_PRIVATE)
            val event = retrieveChosenFileData(prefs, CHOSEN_DIR_URI_KEY,
                    CHOSEN_DIR_NAME_KEY, CHOSEN_DIR_LOCALE_KEY, DIR_SELECT_CODE)
            if (event != null) mLastChosenDirEvent = event
        }
        return mLastChosenDirEvent
    }

    override fun notifyProgress(progress: Int, taskId: Int, remainingTasks: Int) {
        // Inform each compatible fragment of the progress via a status update
        // event.  Ensure that this runs on the main thread.
        val event = ActivityEvent.StatusUpdateEvent(progress, taskId,
                remainingTasks)
        runOnUiThread { handleActivityEvent(event) }
    }

    override fun notifyInputSelection(start: Long, end: Long, taskId: Int) {
        // Log.e(TAG, "notifyInputSelection(): $start, $end, $taskId")

        // TODO
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int,
                                  data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Disable notifications on reentry.  This prevents notifications from being
        // posted while sample text is being read or after a file/directory is
        // chosen.
        myApplication.disableNotifications()

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 &&
            requestCode == SAMPLE_TEXT_CODE -> {
                // Note: Sample text may be available if resultCode is
                // RESULT_CANCELLED.  Therefore, we do not check resultCode.
                // This apparent error may be explained by the peculiar nature of
                // the activity, which, for each engine this programmer has tried,
                // behaves like a trampoline.

                // Retrieve the sample text, falling back on ours if the engine's
                // is unavailable or invalid.
                val key = TextToSpeech.Engine.EXTRA_SAMPLE_TEXT
                var sampleText: String? = null
                if (data != null && data.hasExtra(key)) {
                    sampleText = data.getStringExtra(key)
                }
                if (sampleText == null || sampleText.isBlank()){
                    sampleText = getString(R.string.sample_tts_sentence)
                }

                // Dispatch an event with the sample text.
                val event = ActivityEvent.SampleTextReceivedEvent(sampleText)
                handleActivityEvent(event)
            }
            requestCode == DIR_SELECT_CODE && resultCode == RESULT_OK ||
            requestCode == DIR_SELECT_CONT_CODE && resultCode == RESULT_OK -> {
                // Get the Uri of the selected directory, if possible.
                val uri = data?.data ?: return

                // Take the persistable URI grant that has been offered, if
                // necessary.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val flags =
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, flags)
                }

                // Retrieve the display name.
                val displayName = uri.retrieveDirDisplayName(this)

                // Retrieve the current system locale.
                val systemLocale = currentSystemLocale ?: return

                // Create a file chosen event.
                val event = ActivityEvent.ChosenFileEvent(listOf(uri),
                        listOf(displayName), systemLocale, requestCode)

                // Set shared preference and property values.
                val prefs = getSharedPreferences(packageName, MODE_PRIVATE)
                saveChosenFileData(prefs, CHOSEN_DIR_URI_KEY, CHOSEN_DIR_NAME_KEY,
                        CHOSEN_DIR_LOCALE_KEY, event)
                mLastChosenDirEvent = event

                // Dispatch the event.
                handleActivityEvent(event)
            }
            requestCode == FILE_SELECT_CODE && resultCode == RESULT_OK -> {
                // Get the Uri of the selected file(s), if possible.
                if (data == null) return
                val uriList = mutableListOf<Uri>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    // Iterate *clipData* on Android Jellybean (4.1) and above.
                    val clipData = data.clipData
                    if (clipData == null) {
                        val uri = data.data
                        if (uri != null) uriList.add(uri)
                    } else {
                        for (i in 0 until clipData.itemCount) {
                            uriList.add(clipData.getItemAt(i).uri)
                        }
                    }
                } else {
                    // Use *data* on older versions (SDK v15) that do not support
                    // selecting multiple documents.
                    uriList.add(data.data ?: return)
                }

                // Set display names for each Uri.
                val displayNames = mutableListOf<String>()
                val fallbackName = getString(R.string.fallback_filename)
                for (uri in uriList) {
                    val displayName = uri.retrieveFileDisplayName(this, true)
                    displayNames.add(displayName ?: fallbackName)
                }

                // Retrieve the current system locale.
                val systemLocale = currentSystemLocale ?: return

                // Create the file chosen event.
                val event = ActivityEvent.ChosenFileEvent(uriList, displayNames,
                        systemLocale, requestCode)

                // Set shared preference and property values.
                saveChosenFileData(getSharedPreferences(packageName, MODE_PRIVATE),
                        CHOSEN_FILE_URI_KEY, CHOSEN_FILE_NAME_KEY,
                        CHOSEN_FILE_LOCALE_KEY, event)
                mLastChosenFileEvent = event

                // Dispatch the event.
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
        myApplication.deleteTaskObserver(this)
    }
}
