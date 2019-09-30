package com.danefinlay.ttsutil

import android.app.Application

class ApplicationEx : Application() {

    var speaker: Speaker? = null
        private set

    fun startSpeaker() {
        speaker = Speaker(this, true)
    }

    fun freeSpeaker() {
        speaker?.free()
        speaker = null
    }

    override fun onLowMemory() {
        super.onLowMemory()

        // Stop and free the current text-to-speech engine.
        speaker?.free()
    }
}
