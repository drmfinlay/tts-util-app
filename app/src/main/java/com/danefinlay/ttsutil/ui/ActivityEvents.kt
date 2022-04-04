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

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable

sealed class ActivityEvent : Parcelable {
    class ChosenFileEvent(val uri: Uri,
                          val displayName: String,
                          val fileType: Int) : ActivityEvent() {
        constructor(parcel: Parcel) : this(
                parcel.readParcelable(Uri::class.java.classLoader)!!,
                parcel.readString()!!,
                parcel.readInt()
        )

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeParcelable(uri, flags)
            parcel.writeString(displayName)
            parcel.writeInt(fileType)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<ChosenFileEvent> {
            override fun createFromParcel(parcel: Parcel): ChosenFileEvent =
                    ChosenFileEvent(parcel)
            override fun newArray(size: Int): Array<ChosenFileEvent?> =
                    arrayOfNulls(size)
        }
    }

    class TTSReadyEvent : ActivityEvent(), Parcelable {
        override fun writeToParcel(dest: Parcel?, flags: Int) {}
        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<TTSReadyEvent> {
            override fun createFromParcel(parcel: Parcel): TTSReadyEvent =
                    TTSReadyEvent()
            override fun newArray(size: Int): Array<TTSReadyEvent?> =
                    arrayOfNulls(size)
        }
    }

    class StatusUpdateEvent(val progress: Int,
                            val taskId: Int) : ActivityEvent() {
        constructor(parcel: Parcel) : this(parcel.readInt(), parcel.readInt())

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeInt(progress)
            parcel.writeInt(taskId)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<StatusUpdateEvent> {
            override fun createFromParcel(parcel: Parcel): StatusUpdateEvent =
                    StatusUpdateEvent(parcel)
            override fun newArray(size: Int): Array<StatusUpdateEvent?> =
                    arrayOfNulls(size)
        }
    }

    class SampleTextReceivedEvent(val sampleText: String) : ActivityEvent() {
        constructor(parcel: Parcel) : this(parcel.readString()!!)

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(sampleText)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<SampleTextReceivedEvent> {
            override fun createFromParcel(parcel: Parcel): SampleTextReceivedEvent =
                    SampleTextReceivedEvent(parcel)
            override fun newArray(size: Int): Array<SampleTextReceivedEvent?> =
                    arrayOfNulls(size)
        }
    }
}
