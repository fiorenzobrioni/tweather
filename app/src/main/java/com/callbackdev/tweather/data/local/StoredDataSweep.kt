package com.callbackdev.tweather.data.local

import java.time.Clock
import java.time.Duration

/**
 * The one thing the app's storage had no answer for (12 set 2026): **rows nobody will
 * ever read again**.
 *
 * Everything else on disk is bounded, and each bound is count-based on purpose — the
 * history is as deep as its last hundred commits, not as deep as the last fortnight,
 * because a phone that was off for a week must not come back to an empty
 * `history.diff`. But a count says nothing about places that stopped existing, and this
 * app mints them constantly: a removed city keeps its commits, and the GPS pseudo-city
 * mints a fresh `cacheKey` for every ~1.1 km cell it has ever adopted. None of it is
 * stale data; all of it is data about a subject the app no longer has.
 *
 * So the rule is not an expiry date, which would contradict every retention above it.
 * The rule is: **a key the app no longer follows, quiet for [Grace], leaves entirely**.
 *
 * - *No longer follows* is the saved list plus the current GPS fix, and nothing else.
 *   Widget pins resolve against that same list, so they need no separate say.
 * - *Quiet for [Grace]* is measured from the key's NEWEST row, and the deletion is all
 *   or nothing per key: re-adding a place a reader missed is one tap, and a history
 *   that came back with its middle pages gone would be worse than one that came back
 *   empty.
 */
class StoredDataSweep(
    private val historyDao: WeatherHistoryDao,
    private val diskCache: ReportDiskCache?,
    private val clock: Clock = Clock.systemUTC(),
    private val grace: Duration = Grace
) {

    /**
     * @param liveKeys `City.cacheKey` of every place the app still follows. An EMPTY
     * set is a real state — a reader who removed their last place and left GPS off —
     * and means every key on disk is foreign, not that the sweep should stand down.
     */
    suspend fun run(liveKeys: Set<String>) {
        val cutoff = clock.instant().minus(grace)
        // Room expands an empty list to `NOT IN ()`, which SQLite will not parse. The
        // sentinel is a key no city can mint (cacheKey is always `<int>:<int>`), so an
        // empty live set reads as "nothing here is live" — which is what it means.
        val keys = liveKeys.ifEmpty { setOf(NoKey) }.toList()
        historyDao.pruneForeign(keys, cutoff.epochSecond)
        diskCache?.forgetForeign(liveKeys, cutoff.toEpochMilli())
    }

    companion object {
        /**
         * How long a place stays on disk after the app stops following it. A week, and
         * the same week the forecast itself reaches: long enough that a re-add or a
         * phone left in a drawer costs nobody their history, short enough that a
         * fortnight of commuting does not leave a fortnight of dead cells.
         */
        val Grace: Duration = Duration.ofDays(7)

        /** Matches no `City.cacheKey`, which is always `<int>:<int>`. */
        private const val NoKey = ""
    }
}
