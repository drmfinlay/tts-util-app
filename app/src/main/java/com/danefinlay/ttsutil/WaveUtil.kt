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

import android.support.annotation.CallSuper
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder.LITTLE_ENDIAN

class IncompatibleWaveFileException(message: String): RuntimeException(message)


// Define a few convenient byte-related extension functions.
fun InputStream.read(n: Int): ByteArray {
    return (1..n).map { read().toByte() }.toByteArray()
}

fun ByteArray.toLEByteBuffer(): ByteBuffer {
    return ByteBuffer.wrap(this).order(LITTLE_ENDIAN)
}

fun ByteArray.toAsciiString(): String {
    return fold("") { acc, i -> acc + i.toChar() }
}

fun String.toByteArray(): ByteArray {
    val buffer = ByteBuffer.allocate(this.length)
    for (i in 0 until this.length) {
        val asciiChar = this[i].toByte()
        buffer.put(asciiChar)
    }
    return buffer.array()
}

fun ByteArray.toInt() = toLEByteBuffer().int
fun ByteArray.toShort() = toLEByteBuffer().short

fun Int.toLEByteArray(): ByteArray {
    val buffer = ByteBuffer.allocate(4).order(LITTLE_ENDIAN)
    buffer.putInt(this)
    return buffer.array()
}

fun Short.toLEByteArray(): ByteArray {
    val buffer = ByteBuffer.allocate(2).order(LITTLE_ENDIAN)
    buffer.putShort(this)
    return buffer.array()
}

@Suppress("WeakerAccess")
class WaveFileHeader {
    class ChunkHeader(stream: InputStream) {
        val bCkId: ByteArray = stream.read(4)
        val bCkSize: ByteArray = stream.read(4)
        val ckId: String = bCkId.toAsciiString()
        val ckSize: Int = bCkSize.toInt()
    }

    open class Chunk {
        val bCkId: ByteArray
        val bCkSize: ByteArray
        val ckId: String
        val ckSize: Int

        constructor(chunkHeader: ChunkHeader) {
            this.bCkId = chunkHeader.bCkId
            this.bCkSize = chunkHeader.bCkSize
            this.ckId = chunkHeader.ckId
            this.ckSize = chunkHeader.ckSize
        }

        constructor(ckId: String, ckSize: Int) {
            this.bCkId = ckId.toByteArray()
            this.bCkSize = ckSize.toLEByteArray()
            this.ckId = ckId
            this.ckSize = ckSize

        }

        override fun toString(): String {
            return "ckId=\"$ckId\", ckSize=$ckSize"
        }

        fun compatibleWith(other: Chunk): Boolean {
            // Do not compare chunk size in general.  Sub-chunks should only
            // this if necessary.
            return ckId == other.ckId
        }

        @CallSuper
        open fun validateFields() {}

        @CallSuper
        open fun writeToArray(array: ByteArray, startIndex: Int): Int {
            // Write sub-chunk bytes to the given array and return the index of the
            // next byte.
            var count = startIndex
            for (i in 0..3) array[count++] = bCkId[i]
            for (i in 0..3) array[count++] = bCkSize[i]
            return count
        }
    }

    class RIFFChunk : Chunk {
        // RIFF chunk descriptor fields.
        val bFormat: ByteArray
        val format: String

        constructor(chunkHeader: ChunkHeader,
                    stream: InputStream) : super(chunkHeader) {
            this.bFormat = stream.read(4)
            this.format = bFormat.toAsciiString()
            if (format != "WAVE") {
                val message = "Input is \"$format\", not WAVE format."
                throw IncompatibleWaveFileException(message)
            }
        }

        constructor(ckId: String, ckSize: Int,
                    format: String) : super(ckId, ckSize) {
            this.format = format
            this.bFormat = format.toByteArray()
        }

        fun compatibleWith(other: RIFFChunk): Boolean {
            return super.compatibleWith(other) && format == other.format
        }

        fun copy(ckSize: Int): RIFFChunk {
            return RIFFChunk(ckId, ckSize, format)
        }

        override fun writeToArray(array: ByteArray, startIndex: Int): Int {
            // Write sub-chunk bytes to the given array and return the index of the
            // next byte.
            var count = super.writeToArray(array, startIndex)
            for (i in 0..3) array[count++] = bFormat[i]
            return count
        }

        override fun toString(): String {
            return "${javaClass.simpleName}(${super.toString()}, " +
                    "format=\"$format\")"
        }
    }

    class FmtSubChunk : Chunk {
        // "fmt " sub-chunk fields.
        val bAudioFormat: ByteArray
        val bNumChannels: ByteArray
        val bSampleRate: ByteArray
        val bByteRate: ByteArray
        val bBlockAlign: ByteArray
        val bBitsPerSample: ByteArray
        val audioFormat: Short
        val numChannels: Short
        val sampleRate: Int
        val byteRate: Int
        val blockAlign: Short
        val bitsPerSample: Short
        val bExtraParamsSize: ByteArray?
        val extraParamsSize: Short?
        val bExtraParams: ByteArray?

        constructor(chunkHeader: ChunkHeader,
                    stream: InputStream) : super(chunkHeader) {
            // Read the "fmt " sub-chunk.
            bAudioFormat = stream.read(2)
            bNumChannels = stream.read(2)
            bSampleRate = stream.read(4)
            bByteRate = stream.read(4)
            bBlockAlign = stream.read(2)
            bBitsPerSample = stream.read(2)
            audioFormat = bAudioFormat.toShort()
            numChannels = bNumChannels.toShort()
            sampleRate = bSampleRate.toInt()
            byteRate = bByteRate.toInt()
            blockAlign = bBlockAlign.toShort()
            bitsPerSample = bBitsPerSample.toShort()

            // Handle non-PCM params.
            // The following webpage has more information on non-PCM wave files:
            // http://www-mmsp.ece.mcgill.ca/Documents/AudioFormats/WAVE/WAVE.html
            if (ckSize > 16) {
                // Here we are not interested in non-PCM extensions; any extra
                // params are simply read into a byte array.
                bExtraParamsSize = stream.read(2)
                extraParamsSize = bExtraParamsSize.toShort()
                bExtraParams = stream.read(extraParamsSize.toInt())
            } else {
                // This wave file is PCM.
                bExtraParamsSize = null
                extraParamsSize = null
                bExtraParams = null
            }

            // Validate sub-chunk fields.
            validateFields()
        }

        constructor(ckId: String, ckSize: Int, audioFormat: Short,
                    numChannels: Short, sampleRate: Int, byteRate: Int,
                    blockAlign: Short, bitsPerSample: Short,
                    extraParamsSize: Short?,
                    bExtraParams: ByteArray?) : super(ckId, ckSize) {
            this.audioFormat = audioFormat
            this.bAudioFormat = audioFormat.toLEByteArray()
            this.numChannels = numChannels
            this.bNumChannels = numChannels.toLEByteArray()
            this.sampleRate = sampleRate
            this.bSampleRate = sampleRate.toLEByteArray()
            this.byteRate = byteRate
            this.bByteRate = byteRate.toLEByteArray()
            this.blockAlign = blockAlign
            this.bBlockAlign = blockAlign.toLEByteArray()
            this.bitsPerSample = bitsPerSample
            this.bBitsPerSample = bitsPerSample.toLEByteArray()
            this.extraParamsSize = extraParamsSize
            this.bExtraParamsSize = extraParamsSize?.toLEByteArray()
            this.bExtraParams = bExtraParams
            validateFields()
        }

        fun compatibleWith(other: FmtSubChunk): Boolean {
            return super.compatibleWith(other) &&
                    ckSize == other.ckSize &&
                    audioFormat == other.audioFormat &&
                    numChannels == other.numChannels &&
                    sampleRate == other.sampleRate &&
                    byteRate == other.byteRate &&
                    blockAlign == other.blockAlign &&
                    bitsPerSample == other.bitsPerSample &&
                    bExtraParams?.contentHashCode() ==
                    other.bExtraParams?.contentHashCode()
        }

        fun copy(): FmtSubChunk {
            return FmtSubChunk(ckId, ckSize, audioFormat, numChannels, sampleRate,
                    byteRate, blockAlign, bitsPerSample, extraParamsSize,
                    bExtraParams)
        }

        override fun toString(): String {
            var string = "${javaClass.simpleName}(${super.toString()}, " +
                    "audioFormat=${audioFormat}, numChannels=${numChannels}, " +
                    "sampleRate=${sampleRate}, byteRate=${byteRate}, " +
                    "blockAlign=${blockAlign}, bitsPerSample=${bitsPerSample}"
            if (ckSize > 16) string += ", extraParamSize=${extraParamsSize}"
            string += ")"
            return string
        }

        override fun validateFields() {
            super.validateFields()
            if (ckId != "fmt ") {
                val message = "Unexpected RIFF sub-chunk $ckId"
                throw IncompatibleWaveFileException(message)
            }
        }

        override fun writeToArray(array: ByteArray, startIndex: Int): Int {
            // Write sub-chunk bytes to the given array and return the index of the
            // next byte.
            var count = super.writeToArray(array, startIndex)
            for (i in 0..1) array[count++] = bAudioFormat[i]
            for (i in 0..1) array[count++] = bNumChannels[i]
            for (i in 0..3) array[count++] = bSampleRate[i]
            for (i in 0..3) array[count++] = bByteRate[i]
            for (i in 0..1) array[count++] = bBlockAlign[i]
            for (i in 0..1) array[count++] = bBitsPerSample[i]
            if (bExtraParamsSize != null && bExtraParams != null) {
                for (i in 0..1) array[count++] = bExtraParamsSize[i]
                for (i in 0 until extraParamsSize!!) {
                    array[count++] = bExtraParams[i]
                }
            }
            return count
        }
    }

    class FactSubChunk : Chunk {
        // "fact"  sub-chunk fields.
        val sampleLength: Int?
        val bSampleLength: ByteArray?

        constructor(chunkHeader: ChunkHeader,
                    stream: InputStream) : super(chunkHeader) {
            // Read the "fact" sub-chunk.
            if (ckSize >= 4) {
                bSampleLength = stream.read(4)
                sampleLength = bSampleLength.toInt()
            } else {
                bSampleLength = null
                sampleLength = null
            }

            // Validate sub-chunk fields.
            validateFields()
        }

        constructor(ckId: String, ckSize: Int,
                    sampleLength: Int?) : super(ckId, ckSize) {
            this.sampleLength = sampleLength
            this.bSampleLength = sampleLength?.toLEByteArray()
            validateFields()
        }

        fun compatibleWith(other: FactSubChunk): Boolean {
            // Note: Do not compare sample length, which is derived from the data
            // chunk size.
            return super.compatibleWith(other)
        }

        fun copy(sampleLength: Int?): FactSubChunk {
            return FactSubChunk(ckId, ckSize, sampleLength)
        }

        override fun toString(): String {
            var string = "${javaClass.simpleName}(${super.toString()}"
            if (ckSize >= 4) string += ", sampleLength=$sampleLength"
            string += ")"
            return string
        }

        override fun validateFields() {
            super.validateFields()
            if (ckId != "fact") {
                val message = "Unexpected RIFF sub-chunk $ckId"
                throw IncompatibleWaveFileException(message)
            }
        }

        override fun writeToArray(array: ByteArray, startIndex: Int): Int {
            // Write sub-chunk bytes to the given array and return the index of the
            // next byte.
            var count = super.writeToArray(array, startIndex)
            if (ckSize >= 4) {
                for (i in 0..3) array[count++] = bSampleLength!![i]
            }
            return count
        }
    }

    class DataSubChunk : Chunk {
        constructor(ckId: String, ckSize: Int) : super(ckId, ckSize) {
            // Validate sub-chunk fields.
            validateFields()
        }

        constructor(chunkHeader: ChunkHeader) : super(chunkHeader) {
            validateFields()
        }

        fun compatibleWith(other: DataSubChunk): Boolean {
            return super.compatibleWith(other)
        }

        fun copy(ckSize: Int): DataSubChunk {
            return DataSubChunk(ckId, ckSize)
        }

        override fun toString(): String {
            return "${javaClass.simpleName}(${super.toString()})"
        }

        override fun validateFields() {
            super.validateFields()
            if (ckId != "data") {
                val message = "Unexpected RIFF sub-chunk $ckId"
                throw IncompatibleWaveFileException(message)
            }
        }
    }

    // Properties.
    val riffChunk: RIFFChunk
    val fmtSubChunk: FmtSubChunk
    val factSubChunk: FactSubChunk?
    val dataSubChunk: DataSubChunk

    constructor(stream: InputStream) {
        // Read the RIFF header chunk.
        val riffHeader = ChunkHeader(stream)
        riffChunk = RIFFChunk(riffHeader, stream)

        // Read the "fmt " sub-chunk.
        val fmtHeader = ChunkHeader(stream)
        fmtSubChunk = FmtSubChunk(fmtHeader, stream)

        // Read the next sub-chunk header.
        val sCk2Header = ChunkHeader(stream)

        // If it is a (non-PCM) "fact" sub-chunk, read it.
        // Otherwise, it must be the "data" sub-chunk.
        val dataSCkHeader: ChunkHeader
        if (sCk2Header.ckId == "fact") {
            factSubChunk = FactSubChunk(sCk2Header, stream)
            dataSCkHeader = ChunkHeader(stream)  // sub-chunk 3
        } else {
            factSubChunk = null // no "fact" sub-chunk
            dataSCkHeader = sCk2Header
        }

        // Read the "data" sub-chunk.
        // Note: The wave data is not read here.
        dataSubChunk = DataSubChunk(dataSCkHeader)
    }

    constructor(riffChunk: RIFFChunk, fmtSubChunk: FmtSubChunk,
                factSubChunk: FactSubChunk?, dataSubChunk: DataSubChunk) {
        this.riffChunk = riffChunk
        this.fmtSubChunk = fmtSubChunk
        this.factSubChunk = factSubChunk
        this.dataSubChunk = dataSubChunk
    }

    /**
     * Whether the wave file this header represents is a Pulse-code modulation (PCM)
     * wave file.
     */
    val isPCM: Boolean
        get() {
            // The following is based on definitions on this webpage:
            // http://www-mmsp.ece.mcgill.ca/Documents/AudioFormats/WAVE/WAVE.html
            return fmtSubChunk.ckSize == 16 && factSubChunk == null
        }

    /**
     * The total size of this header.
     *
     * This is the total number of bytes up to and including the "data" chunk size
     * field.
     *
     */
    val size: Int
        get() = 8 + (riffChunk.ckSize - dataSubChunk.ckSize)

    fun compatibleWith(other: WaveFileHeader): Boolean {
        // Do not compare the chunkSize fields because they include the size
        // of sub-chunk 2, the data chunk.
        return riffChunk.compatibleWith(other.riffChunk) &&
                fmtSubChunk.compatibleWith(other.fmtSubChunk) &&
                dataSubChunk.compatibleWith(other.dataSubChunk) &&
                (factSubChunk == null && other.factSubChunk == null ||
                        factSubChunk != null && other.factSubChunk != null &&
                        factSubChunk.compatibleWith(other.factSubChunk))
    }

    fun copy(dataSubChunkSize: Int): WaveFileHeader {
        // Determine the final size based on the specified data sub-chunk size.
        val fmtSubChunkSize = fmtSubChunk.ckSize
        val factSubChunkSize = factSubChunk?.ckSize
        val totalSize = 4 + (8 + fmtSubChunkSize) +
                (if (factSubChunkSize != null) 8 + factSubChunkSize else 0) +
                (8 + dataSubChunkSize)

        // Determine the new sample length.
        val newSampleLength = totalSize / fmtSubChunk.numChannels

        // Create copies of each sub-chunk with fields adjusted appropriately.
        val riffChunk = riffChunk.copy(totalSize)
        val fmtSubChunk = fmtSubChunk.copy()
        var factSubChunk: FactSubChunk? = null
        if (factSubChunk != null) {
            factSubChunk = factSubChunk.copy(newSampleLength)
        }
        val dataSubChunk = dataSubChunk.copy(dataSubChunkSize)

        // Return a new wave file header.
        return WaveFileHeader(riffChunk, fmtSubChunk, factSubChunk, dataSubChunk)
    }

    override fun equals(other: Any?): Boolean {
        var result = false
        if (other is WaveFileHeader) {
            result = this.compatibleWith(other) &&
                    this.riffChunk.ckSize == other.riffChunk.ckSize
        }
        return result
    }

    override fun hashCode(): Int {
        var result = riffChunk.hashCode()
        result = 31 * result + fmtSubChunk.hashCode()
        result = 31 * result + (factSubChunk?.hashCode() ?: 0)
        result = 31 * result + dataSubChunk.hashCode()
        return result
    }

    override fun toString(): String {
        var string = "${javaClass.simpleName}(\n" +
                "\t$riffChunk\n" +
                "\t$fmtSubChunk\n"
        if (factSubChunk != null) string += "\t$factSubChunk\n"
        string += "\t$dataSubChunk\n)"
        return string
    }

    fun writeToArray(): ByteArray {
        // Allocate a byte array for the header bytes.
        val array = ByteArray(this.size)

        // Write the bytes of each sub-chunk in order.
        var count = 0
        count = riffChunk.writeToArray(array, count)
        count = fmtSubChunk.writeToArray(array, count)
        if (factSubChunk != null) {
            count = factSubChunk.writeToArray(array, count)
        }
        dataSubChunk.writeToArray(array, count)

        // Return the array.
        return array
    }

    companion object {
        const val MIN_SIZE = 44
    }
}

class WaveFile(val stream: InputStream) {
    // Read the file header from the stream.
    val header: WaveFileHeader = WaveFileHeader(stream)

    // The rest of the input stream is assumed to be the actual sound data.
    inline fun readWaveData(block: (int: Int) -> Unit) {
        var byte = stream.read()
        while (byte >= 0) {
            block(byte)
            byte = stream.read()
        }
        stream.close()
    }

    fun compatibleWith(other: WaveFile): Boolean =
            header.compatibleWith(other.header)

    override fun toString(): String {
        return "${javaClass.simpleName}($header)"
    }
}

class InterruptEvent {
    @Volatile
    var interrupt: Boolean = false
}

/**
 * Function for taking wave files and writing a joined wave file.
 *
 * I note here that although all TTS engines I've tested have used wave files,
 * Android's TextToSpeech documentation makes no specific reference to them:
 * https://developer.android.com/reference/android/speech/tts/TextToSpeech
 *
 * This, of course, has no bearing if the reader wishes to use this code on other
 * platforms.
 *
 * @param   inFiles             List of wave files.
 * @param   outStream           Output stream to which the joined wave file will be written.
 * @param   deleteFiles         Delete each inFile after processing.
 * @param   interruptEvent      Event to check for early returns, i.e., interrupts.
 * @param   progressCallback    Callback function for observing progress out of 100%.
 * @return  success
 * @exception   IncompatibleWaveFileException   Raised for invalid/incompatible Wave
 * files.
 */
fun joinWaveFiles(inFiles: List<File>, outStream: OutputStream,
                  deleteFiles: Boolean = false,
                  interruptEvent: InterruptEvent? = null,
                  progressCallback: ((progress: Int) -> Unit)? = null): Boolean {
    // Return early if appropriate.
    if (interruptEvent?.interrupt == true) return false

    // Notify the observer that work has begun.
    progressCallback?.invoke(0)

    // Handle special case: empty list.
    if (inFiles.isEmpty()) {
        progressCallback?.invoke(100)
        return true
    }

    // Read each file, verifying that all files are compatible.
    // Data chunks are not read in yet.
    val wf1 = WaveFile(FileInputStream(inFiles.first()).buffered())
    val waveFiles = listOf(wf1) + inFiles.subList(1, inFiles.size)
            .map { file ->
                val wf = WaveFile(FileInputStream(file).buffered())
                if (!wf.compatibleWith(wf1)) {
                    throw IncompatibleWaveFileException("Wave files with " +
                            "incompatible headers are not supported: " +
                            "$${wf.header} ~ ${wf1.header}")
                }
                // This wave file is acceptable.
                wf
            }

    // Calculate the data chunk size.
    val dataSubChunkSize = waveFiles.fold(0) { acc, wf ->
        acc + wf.header.dataSubChunk.ckSize
    }

    // Create a new wave file header based on the first one.
    val header = wf1.header.copy(dataSubChunkSize)

    // Get the total file size from the new header.
    val totalSize = 8 + header.riffChunk.ckSize

    // Open the output file for writing.
    outStream.buffered().use { bOutStream ->
        // Write the header to the output stream.
        bOutStream.write(header.writeToArray())

        // Return early if appropriate.
        if (interruptEvent?.interrupt == true) return false

        // Notify the observer of the progress so far.
        var count = header.size.toFloat()
        progressCallback?.invoke((count / totalSize * 100).toInt())

        // Stream data from each file into the output file.
        inFiles.zip(waveFiles).forEach { (f, wf) ->
            wf.readWaveData { byte ->
                bOutStream.write(byte)
            }

            // Delete the input file, if necessary.
            if (deleteFiles) f.delete()

            // Return early if appropriate.
            if (interruptEvent?.interrupt == true) return false

            // Notify the observer after each file is written, if necessary.
            count += wf.header.dataSubChunk.ckSize
            progressCallback?.invoke((count / totalSize * 100).toInt())
        }
    }
    return true
}
