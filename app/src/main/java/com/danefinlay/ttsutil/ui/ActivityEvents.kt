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

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import java.util.*

sealed class ActivityEvent : Parcelable {
    class ChosenFileEvent(val uriList: List<Uri>,
                          val displayNameList: List<String>,
                          val locale: Locale,
                          var requestCode: Int) : ActivityEvent() {
        constructor(parcel: Parcel) : this(
                parcel.createTypedArrayList(Uri.CREATOR)!!,
                parcel.createStringArrayList()!!,
                parcel.readSerializable() as Locale,
                parcel.readInt())

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeTypedList(uriList)
            parcel.writeStringList(displayNameList)
            parcel.writeSerializable(locale)
            parcel.writeInt(requestCode)
        }

        val firstUri: Uri = uriList.first()
        val firstDisplayName: String = displayNameList.first()

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<ChosenFileEvent> {
            override fun createFromParcel(parcel: Parcel): ChosenFileEvent =
                    ChosenFileEvent(parcel)

            override fun newArray(size: Int): Array<ChosenFileEvent?> =
                    arrayOfNulls(size)
        }
    }

    class TTSReadyEvent : ActivityEvent(), Parcelable {
        override fun writeToParcel(p0: Parcel, flags: Int) {}
        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<TTSReadyEvent> {
            override fun createFromParcel(parcel: Parcel): TTSReadyEvent =
                    TTSReadyEvent()
            override fun newArray(size: Int): Array<TTSReadyEvent?> =
                    arrayOfNulls(size)
        }
    }

    class TaskQueueChangeEvent(val remainingTasks: Int) : ActivityEvent() {
        constructor(parcel: Parcel) : this(parcel.readInt())

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeInt(remainingTasks)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<TaskQueueChangeEvent> {
            override fun createFromParcel(parcel: Parcel): TaskQueueChangeEvent =
                    TaskQueueChangeEvent(parcel)
            override fun newArray(size: Int): Array<TaskQueueChangeEvent?> =
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
