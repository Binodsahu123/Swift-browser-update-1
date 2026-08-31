package com.swift.browser.audioengine

import com.swift.browser.audioengine.manager.AudioQueueManager
import com.swift.browser.audioengine.model.AudioTrackItem
import com.swift.browser.audioengine.model.PlaybackSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioEngineUnitTest {

    private fun createSampleTrack(id: String, title: String): AudioTrackItem {
        return AudioTrackItem(
            id = id,
            title = title,
            artist = "Artist $id",
            album = "Album $id",
            filePath = "content://media/external/audio/media/$id",
            durationMs = 180000L
        )
    }

    @Test
    fun testAudioTrackItemCreation() {
        val track = createSampleTrack("1", "Song A")
        assertEquals("1", track.id)
        assertEquals("Song A", track.title)
        assertEquals("Artist 1", track.artist)
        assertEquals(180000L, track.durationMs)
    }

    @Test
    fun testAudioQueueManagerNavigation() {
        val queueManager = AudioQueueManager()
        val track1 = createSampleTrack("1", "Song 1")
        val track2 = createSampleTrack("2", "Song 2")
        val track3 = createSampleTrack("3", "Song 3")
        val tracks = listOf(track1, track2, track3)

        queueManager.setQueue(tracks, 0)
        assertEquals(3, queueManager.queueState.value.tracks.size)
        assertEquals(0, queueManager.queueState.value.currentIndex)
        assertEquals(track1, queueManager.getCurrentTrack())

        val next = queueManager.next()
        assertEquals(track2, next)
        assertEquals(1, queueManager.queueState.value.currentIndex)

        val next2 = queueManager.next()
        assertEquals(track3, next2)
        assertEquals(2, queueManager.queueState.value.currentIndex)

        // At end of queue without repeat, next is null
        val next3 = queueManager.next()
        assertNull(next3)

        // Previous goes back
        val prev = queueManager.previous()
        assertEquals(track2, prev)
    }

    @Test
    fun testAudioQueueShuffleAndRepeat() {
        val queueManager = AudioQueueManager()
        val tracks = (1..5).map { createSampleTrack("$it", "Song $it") }
        queueManager.setQueue(tracks, 0)

        // Toggle shuffle
        queueManager.toggleShuffle()
        assertTrue(queueManager.queueState.value.isShuffle)

        // Toggle repeat: 0 (None) -> 1 (All) -> 2 (One) -> 0 (None)
        assertEquals(0, queueManager.queueState.value.repeatMode)
        queueManager.toggleRepeat()
        assertEquals(1, queueManager.queueState.value.repeatMode)
        queueManager.toggleRepeat()
        assertEquals(2, queueManager.queueState.value.repeatMode)
        queueManager.toggleRepeat()
        assertEquals(0, queueManager.queueState.value.repeatMode)
    }

    @Test
    fun testPlaybackSourceEnum() {
        assertEquals(PlaybackSource.NONE, PlaybackSource.valueOf("NONE"))
        assertEquals(PlaybackSource.LOCAL, PlaybackSource.valueOf("LOCAL"))
        assertEquals(PlaybackSource.ONLINE, PlaybackSource.valueOf("ONLINE"))
    }
}

