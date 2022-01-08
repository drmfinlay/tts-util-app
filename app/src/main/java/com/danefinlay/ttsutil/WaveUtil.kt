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

fun ByteArray.toInt() = toLEByteBuffer().int
fun ByteArray.toShort() = toLEByteBuffer().short

fun ByteBuffer.putArrays(vararg arrays: ByteArray): ByteBuffer {
    arrays.forEach { put(it) }
    return this
}

@Suppress("WeakerAccess")
class WaveFileHeader(stream: InputStream) {
    open class ChunkHeader(stream: InputStream) {
        val bCkId: ByteArray = stream.read(4)
        val bCkSize: ByteArray = stream.read(4)
        val ckId: String = bCkId.toAsciiString()
        val ckSize: Int = bCkSize.toInt()

        fun compatibleWith(other: ChunkHeader): Boolean {
            // Do not compare chunk size in general.  Sub-chunks should only
            // this if necessary.
            return ckId == other.ckId
        }

        override fun toString(): String {
            return "ckId=\"$ckId\", ckSize=$ckSize"
        }
    }

    open class Chunk(val ckHeader: ChunkHeader) {
        val bCkId = ckHeader.bCkId
        val bCkSize = ckHeader.bCkSize
        val ckId = ckHeader.ckId
        val ckSize = ckHeader.ckSize
    }

    class RIFFChunk(ckHeader: ChunkHeader, stream: InputStream) : Chunk(ckHeader) {
        // RIFF chunk descriptor fields.
        val bFormat: ByteArray = stream.read(4)
        val format: String = bFormat.toAsciiString()

        init {
            // Verify that the WAVE identifier is present.
            if (format != "WAVE") {
                val message = "Input is \"$format\", not WAVE format."
                throw IncompatibleWaveFileException(message)
            }
        }

        fun compatibleWith(other: RIFFChunk): Boolean {
            return ckHeader.compatibleWith(other.ckHeader) && format == other.format
        }

        fun writeToArray(newChunkSize: Int): ByteArray {
            return ByteBuffer.allocate(12)
                    .put(bCkId).putInt(newChunkSize).put(bFormat)
                    .array()
        }

        override fun toString(): String {
            return "${javaClass.simpleName}($ckHeader, format=\"$format\")"
        }
    }

    class FmtSubChunk(ckHeader: ChunkHeader, stream: InputStream) : Chunk(ckHeader) {
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

        init {
            if (ckId != "fmt ") {
                val message = "Unexpected RIFF sub-chunk $ckId"
                throw IncompatibleWaveFileException(message)
            }

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
        }

        fun compatibleWith(other: FmtSubChunk): Boolean {
            return ckHeader.compatibleWith(other.ckHeader) &&
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

        fun writeToArray(): ByteArray {
            val buffer = ByteBuffer.allocate(8 + ckSize)
            buffer.putArrays(bCkId, bCkSize, bAudioFormat, bNumChannels,
                    bSampleRate, bByteRate, bBlockAlign, bBitsPerSample)
            if (bExtraParamsSize != null) buffer.put(bExtraParamsSize)
            if (bExtraParams != null) buffer.put(bExtraParams)
            return buffer.array()
        }

        override fun toString(): String {
            var string = "${javaClass.simpleName}($ckHeader, " +
                    "audioFormat=${audioFormat}, numChannels=${numChannels}, " +
                    "sampleRate=${sampleRate}, byteRate=${byteRate}, " +
                    "blockAlign=${blockAlign}, bitsPerSample=${bitsPerSample}"
            if (ckSize > 16) string += ", extraParamSize=${extraParamsSize}"
            string += ")"
            return string
        }
    }

    class FactSubChunk(ckHeader: ChunkHeader,
                       stream: InputStream) : Chunk(ckHeader) {
        // "fact"  sub-chunk fields.
        val sampleLength: Int?
        val bSampleLength: ByteArray?

        init {
            if (ckId != "fact") {
                val message = "Unexpected RIFF sub-chunk $ckId"
                throw IncompatibleWaveFileException(message)
            }

            // Read the "fact" sub-chunk.
            if (ckSize >= 4) {
                bSampleLength = stream.read(4)
                sampleLength = bSampleLength.toInt()
            } else {
                bSampleLength = null
                sampleLength = null
            }
        }

        fun compatibleWith(other: FactSubChunk): Boolean {
            // Do not compare sample length, which is derived from the data chunk
            // size.
            return ckHeader.compatibleWith(other.ckHeader)
        }

        fun writeToArray(newSampleLength: Int): ByteArray {
            val buffer =  ByteBuffer.allocate(8 + ckSize)
                    .putArrays(bCkId, bCkSize)
            if (ckSize >= 4) buffer.putInt(newSampleLength)
            return buffer.array()
        }

        override fun toString(): String {
            var string = "${javaClass.simpleName}($ckHeader"
            if (ckSize >= 4) string += ", sampleLength=$sampleLength"
            string += ")"
            return string
        }
    }

    class DataSubChunk(ckHeader: ChunkHeader) : Chunk(ckHeader) {
        init {
            if (ckId != "data") {
                val message = "Unexpected RIFF sub-chunk $ckId"
                throw IncompatibleWaveFileException(message)
            }

            // Note: this is the end; this class does not read the wave data.
        }

        fun compatibleWith(other: DataSubChunk): Boolean {
            return ckHeader.compatibleWith(other.ckHeader)
        }

        fun writeToArray(newChunkSize: Int): ByteArray {
            // This is only the first 8 bytes of the data chunk.
            return ByteBuffer.allocate(8).put(bCkId).putInt(newChunkSize).array()
        }

        override fun toString(): String {
            return "${javaClass.simpleName}($ckHeader)"
        }
    }

    // Properties.
    val riffChunk: RIFFChunk
    val fmtSubChunk: FmtSubChunk
    val factSubChunk: FactSubChunk?
    val dataSubChunk: DataSubChunk

    init {
        // Read the RIFF header chunk.
        val riffHeader = ChunkHeader(stream)
        if (riffHeader.ckId != "RIFF") {
            val message = "RIFF header chunk not found"
            throw IncompatibleWaveFileException(message)
        }
        riffChunk = RIFFChunk(riffHeader, stream)

        // Read the "fmt " sub-chunk.
        val fmtHeader = ChunkHeader(stream)
        if (fmtHeader.ckId != "fmt ") {
            val message = "\"fmt \" sub-chunk not found."
            throw IncompatibleWaveFileException(message)
        }
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
        // Note: this is the end; this class does not read the wave data.
        dataSubChunk = DataSubChunk(dataSCkHeader)
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

    override fun toString(): String {
        var string = "${javaClass.simpleName}(\n" +
                "\t$riffChunk\n" +
                "\t$fmtSubChunk\n"
        if (factSubChunk != null) string += "\t$factSubChunk\n"
        string += "\t$dataSubChunk\n)"
        return string
    }

    companion object {
        const val MIN_SIZE = 44
    }
}

class WaveFile(val stream: InputStream) {
    // Read the file header.
    val header = WaveFileHeader(stream)

    // The rest of the file is the actual sound data.
    inline fun readDataChunk(block: (int: Int) -> Unit) {
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
 * @param   outFile             Output file where the joined wave file will be written.
 * @exception   IncompatibleWaveFileException   Raised for invalid/incompatible Wave
 * files.
 */
fun joinWaveFiles(inFiles: List<File>, outFile: File) {
    // Handle special case: empty list.
    if (inFiles.isEmpty()) return

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

    // Construct a new wave file.  Use the first wave file for fields with the same
    // values.
    // Calculate the SubChunk2Size and ChunkSize.
    val dataSubChunkSize = waveFiles.fold(0) { acc, wf ->
        acc + wf.header.dataSubChunk.ckSize
    }
    val header = wf1.header
    val totalChunkSize = 12 + header.fmtSubChunk.ckSize + 8 + dataSubChunkSize

    // Open the output file for writing.
    FileOutputStream(outFile).buffered().use { outStream ->

        // Write the RIFF header.
        outStream.write(header.riffChunk.writeToArray(totalChunkSize))

        // Write the "fmt " sub-chunk.
        outStream.write(header.fmtSubChunk.writeToArray())

        // Write the "fact" sub-chunk, if necessary.
        if (header.factSubChunk != null) {
            val newSampleLength = totalChunkSize / header.fmtSubChunk.numChannels
            outStream.write(header.factSubChunk.writeToArray(newSampleLength))
        }

        // Write the data chunk header.
        outStream.write(header.dataSubChunk.writeToArray(dataSubChunkSize))

        // Stream data from each file into the output file.
        // var count = 0f
        waveFiles.forEach { wf ->
            // val progress = (count / totalChunkSize.toFloat() * 100).toInt()
            // print("Progress: $progress%\r")
            wf.readDataChunk { byte ->
                outStream.write(byte)
            }
            // count+=wf.header.dataSubChunk.ckSize.toFloat()
        }
    }
}
