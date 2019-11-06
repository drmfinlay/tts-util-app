package com.danefinlay.ttsutil.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import com.danefinlay.ttsutil.ACTION_EDIT_READ_CLIPBOARD
import com.danefinlay.ttsutil.R
import com.danefinlay.ttsutil.isReady

class EditReadActivity : SpeakerActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_read)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (savedInstanceState == null) {
            val fragment: ReadTextFragmentBase = when (intent?.action) {
                Intent.ACTION_SEND -> ReadTextFragment()
                ACTION_EDIT_READ_CLIPBOARD -> ReadClipboardFragment()
                else -> return
            }
            supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit()

            // Set up text-to-speech if necessary.
            if (!speaker.isReady()) {
                checkTTS(CHECK_TTS)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.itemId == android.R.id.home) {
            onBackPressed()
            finish()
            return true
        }

        return super.onOptionsItemSelected(item)
    }
}
