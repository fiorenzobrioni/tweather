package com.callbackdev.tweather.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The sweep of 12 set 2026: the only retention in the app that asks WHOSE rows these
 * are. Everything here is about the line between "old" (which is fine, and which the
 * count-based prunes deliberately tolerate) and "orphaned" (which is not).
 */
@RunWith(RobolectricTestRunner::class)
class StoredDataSweepTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now: Instant = Instant.parse("2026-09-12T10:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private lateinit var database: TweatherDatabase
    private lateinit var cacheDir: File

    /** Well inside the grace period. */
    private val recent = now.minus(Duration.ofDays(1))

    /** Well outside it. */
    private val ancient = now.minus(Duration.ofDays(30))

    private val milan = "4546:919"
    private val turin = "4507:769"
    private val cell = "4530:900"

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, TweatherDatabase::class.java)
            .allowMainThreadQueries().build()
        cacheDir = tmp.newFolder("report_cache")
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun sweep(diskCache: ReportDiskCache? = null) = StoredDataSweep(
        historyDao = database.weatherHistoryDao(),
        diskCache = diskCache,
        clock = clock
    )

    private fun commit(cityKey: String, at: Instant) = runBlocking {
        database.weatherHistoryDao().insert(
            WeatherHistoryEntry(
                cityKey = cityKey,
                cityLabel = cityKey,
                hash = "abcdef0",
                author = "sys@tweather.app",
                timestampEpochSeconds = at.epochSecond,
                snapshotJson = "{}"
            )
        )
    }

    private fun historyKeys(): List<String> = runBlocking {
        listOf(milan, turin, cell).flatMap { key ->
            database.weatherHistoryDao().historyFor(key, 100).map { it.cityKey }
        }
    }

    @Test
    fun `a place still followed keeps its commits however old they are`() = runBlocking {
        commit(milan, ancient)
        commit(milan, ancient)

        sweep().run(liveKeys = setOf(milan))

        assertEquals(listOf(milan, milan), historyKeys())
    }

    @Test
    fun `a place no longer followed leaves once it has been quiet for the grace`() = runBlocking {
        commit(milan, recent)
        commit(cell, ancient)

        sweep().run(liveKeys = setOf(milan))

        assertEquals(listOf(milan), historyKeys())
    }

    @Test
    fun `a place removed a moment ago keeps everything, so a re-add still has a history`() =
        runBlocking {
            commit(turin, ancient)
            // The newest row is what the grace is measured from: a place followed until
            // yesterday is inside it even when most of its history is a month old.
            commit(turin, recent)

            sweep().run(liveKeys = setOf(milan))

            assertEquals(listOf(turin, turin), historyKeys())
        }

    @Test
    fun `it is all or nothing per place, never the old half of one history`() = runBlocking {
        commit(cell, ancient)
        commit(cell, recent)

        sweep().run(liveKeys = setOf(milan))

        // `cell` is foreign and half of it is past the cutoff, but its newest row is not:
        // the whole key stays. A `history.diff` with its middle pages torn out is the one
        // thing this must never produce.
        assertEquals(listOf(cell, cell), historyKeys())
    }

    @Test
    fun `an empty live set means everything is foreign, not that the sweep stands down`() =
        runBlocking {
            // The reader removed their last place and left GPS off: Room would choke on
            // `NOT IN ()`, so the sentinel has to carry this case.
            commit(milan, ancient)

            sweep().run(liveKeys = emptySet())

            assertTrue(historyKeys().isEmpty())
        }

    @Test
    fun `the disk cache loses the foreign responses and keeps the live one`() = runBlocking {
        val live = File(cacheDir, "4546_919.json").apply { writeText("{}") }
        val orphan = File(cacheDir, "4530_900.json").apply { writeText("{}") }
        val fresh = File(cacheDir, "4507_769.json").apply { writeText("{}") }
        live.setLastModified(ancient.toEpochMilli())
        orphan.setLastModified(ancient.toEpochMilli())
        // Foreign but still recent: the offline fallback could want it, so it stays.
        fresh.setLastModified(recent.toEpochMilli())

        sweep(ReportDiskCache(cacheDir, Json)).run(liveKeys = setOf(milan))

        assertTrue(live.exists())
        assertTrue(fresh.exists())
        assertTrue(!orphan.exists())
    }
}
