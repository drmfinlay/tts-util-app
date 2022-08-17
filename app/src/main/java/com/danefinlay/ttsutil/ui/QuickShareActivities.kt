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

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.annotation.RequiresApi
import com.danefinlay.ttsutil.ACTION_READ_CLIPBOARD
import com.danefinlay.ttsutil.TTSIntentService
import com.danefinlay.ttsutil.TTS_NOT_READY

/**
 * Activity to quickly read text from an input source.
 * Reading text is done via TTSIntentService.
 */
abstract class QuickShareActivity : TTSActivity() {

    abstract fun startServiceAction()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If TTS is not yet initialized, attempt to initialize it.  Otherwise,
        // start the service action and finish the activity.
        if (myApplication.mTTS == null) initializeTTS(null)
        else {
            startServiceAction()
            finish()
        }
    }

    override fun onInit(status: Int) {
        super.onInit(status)

        // If TTS is ready, start the service action.
        // Otherwise, handle TTS_NOT_READY.
        if (status == TextToSpeech.SUCCESS) startServiceAction()
        else myApplication.handleTTSOperationResult(TTS_NOT_READY)

        // Finish the activity.
        finish()
    }

    override fun handleActivityEvent(event: ActivityEvent) {}
}

class ReadClipboardActivity : QuickShareActivity() {
    override fun startServiceAction() {
        val intent = intent ?: return
        if (intent.action == ACTION_READ_CLIPBOARD) {
            TTSIntentService.startActionReadClipboard(this)
        }
    }
}

class ReadTextActivity : QuickShareActivity() {
    override fun startServiceAction() {
        val intent = intent ?: return
        if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            TTSIntentService.startActionReadText(this, text)
        }
    }
}

class TextActionActivity : QuickShareActivity() {
    @RequiresApi(Build.VERSION_CODES.M)
    override fun startServiceAction() {
        val intent = intent ?: return
        if (intent.action == Intent.ACTION_PROCESS_TEXT) {
            val text = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
            TTSIntentService.startActionReadText(this, text)
        }
    }
}
