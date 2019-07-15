package com.danefinlay.ttsutil

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.support.design.widget.TextInputLayout
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.danefinlay.androidutil.FileChooser
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.enabled
import org.jetbrains.anko.find
import org.jetbrains.anko.onClick

class MainActivity : AppCompatActivity(), FileChooser {
    private var serviceRunning: Boolean = false

    private val daemonButton: Button
        get() = find(R.id.daemon_button)

    private val testButton: Button
        get() = find(R.id.test_button)

    private val chooseButton: Button
        get() = find(R.id.choose_file_button)

    private val errorMessage: TextView
        get() = find(R.id.error_message)

    private val chosenFileText: TextView
        get() = find(R.id.file_to_monitor_text)

    private val chosenAbsFileEditTextLayout: TextInputLayout
        get() = find(R.id.abs_file_path_layout)

    private var myService: MyService? = null

    override val chooseFileAction: String = Intent.ACTION_OPEN_DOCUMENT
    override val chooseFileCategory: String = Intent.CATEGORY_OPENABLE
    override val chooseFileMimeType: String = "*/*"

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(component: ComponentName?, binder: IBinder?) {
            serviceRunning = true
            setButtonsUp()
            myService = (binder as? MyService.LocalBinder)?.service
        }

        override fun onServiceDisconnected(component: ComponentName?) {
            serviceRunning = false
            setButtonsUp()
            myService = null
        }
    }

    override fun getActivity() = this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if ( savedInstanceState == null ) {
            // Check if there is a TTS engine is installed on the device.
            checkTTS()
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        outState?.putBoolean("serviceRunning", serviceRunning)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        if ( savedInstanceState != null ) {

            if ( savedInstanceState.containsKey("serviceRunning") ) {
                serviceRunning = savedInstanceState.getBoolean("serviceRunning")
                bindMyServiceIfRunning()
            }
        }
        super.onRestoreInstanceState(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()

        daemonButton.onClick {
            if ( serviceRunning ) stopMyService()
            else startMyService()
        }

        testButton.onClick {
            myService?.speakFromFile()
        }

        chooseButton.onClick {
            showFileChooser()
        }

        setFileDisplayName()

        setButtonsUp()
    }

    override fun onResume() {
        super.onResume()

        setButtonsUp()
    }

    override fun onDestroy() {
        try {
            unbindService(serviceConnection)
        } catch(e: IllegalArgumentException) { } // le sigh...
        super.onDestroy()
    }

    private fun checkTTS() {
        val check = Intent()
        check.action = TextToSpeech.Engine.ACTION_CHECK_TTS_DATA
        startActivityForResult(check, CHECK_TTS_CODE)
    }

    private fun setButtonsUp() {
        doAsync {
            // Do this asynchronously because it is long running!
            val chosenPathIsValid = chosenPathIsValid
            runOnUiThread {
                daemonButton.enabled = chosenPathIsValid

                if ( !serviceRunning || !chosenPathIsValid ) {
                    daemonButton.text = getString(R.string.start_daemon_text)
                    testButton.enabled = false
                } else {
                    daemonButton.text = getString(R.string.stop_daemon_text)
                    testButton.enabled = true
                }

                setFileDisplayName()
            }
        }

    }

    override fun onFileChosen(uri: Uri) {
        chosenFileUri = uri

        // Also update CHOSEN_FILE_LAST_MODIFIED_PREF_KEY
        fileUriLastModifiedPrefValue = fileUriLastModified

        setFileDisplayName()
        setAbsFilePath()

        // If the service is running, then restart the service's file watching thread.
        if ( serviceRunning ) {
            myService?.restartFileWatcher()
        }
    }

    private fun setFileDisplayName() {
        val properties = fileUriProperties
        val displayNameKey = "_display_name"
        val displayText = if ( properties != null && properties.containsKey(displayNameKey) ) {
            properties[displayNameKey]
        } else getString(R.string.no_file_chosen)
        chosenFileText.text = displayText
    }

    private fun setAbsFilePath() {
        TODO()
    }

    private fun startMyService() {
        val serviceIntent = Intent(this, MyService::class.java)
                .putExtra(FILE_URI, chosenFileUri)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, 0)
    }

    private fun bindMyServiceIfRunning() {
        if ( serviceRunning ) {
            val serviceIntent = Intent(this, MyService::class.java)
            bindService(serviceIntent, serviceConnection, 0)
        }
    }

    private fun stopMyService() {
        val serviceIntent = Intent(this, MyService::class.java)
        try {
            unbindService(serviceConnection)
        } catch(e: IllegalArgumentException) { } // le sigh...
        serviceConnection.onServiceDisconnected(null)
        stopService(serviceIntent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            CHECK_TTS_CODE -> {
                if ( resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS ) {
                    // Start the daemon if we have an engine and if the service isn't running.
                    if ( !serviceRunning ) startMyService()

                    // Activate the daemonButton and hide the error message
                    setButtonsUp()
                    errorMessage.visibility = View.GONE
                } else {
                    // Show the error message
                    errorMessage.visibility = View.VISIBLE

                    // Start an activity that installs a text to speech engine
                    val install = Intent()
                    install.action = TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
                    startActivity(install)
                }
            }
        }
        super<FileChooser>.onActivityResult(requestCode, resultCode, data)
        super<AppCompatActivity>.onActivityResult(requestCode, resultCode, data)
    }

    companion object {
        private const val CHECK_TTS_CODE = 1

    }
}
