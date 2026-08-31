package com.swift.browser.videoengine.extraction

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.swift.browser.videoengine.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

class VideoAudioExtractionManager(private val context: Context) {

    suspend fun extractAudio(item: VideoItem): String? = withContext(Dispatchers.IO) {
        try {
            val file = File(item.path)
            if (!file.exists()) return@withContext null
            val outputFile = File(file.parent, "${file.nameWithoutExtension}_audio.m4a")
            val outputPath = outputFile.absolutePath

            val extractor = MediaExtractor()
            extractor.setDataSource(item.path)

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }
            if (audioTrackIndex == -1 || audioFormat == null) return@withContext null

            extractor.selectTrack(audioTrackIndex)

            val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()

            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.flags = extractor.sampleFlags
                bufferInfo.presentationTimeUs = extractor.sampleTime

                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            extractor.release()

            return@withContext outputPath
        } catch (e: Exception) {
            Log.e("AudioExtractor", "Error extracting audio", e)
            return@withContext null
        }
    }
}
