package com.smsbomberguard.app

/**
 * RateLimiter.kt
 * -----------------------------------------------------------
 * Core detection algorithm for SMS-bomber attacks.
 *
 * Logic:
 *  - Every incoming SMS timestamp is recorded.
 *  - If more than [thresholdCount] messages arrive inside a short
 *    [windowMillis] window (default: 2 messages / 5 seconds), the
 *    limiter enters "blocked" mode.
 *  - While blocked, every new message is quarantined instead of
 *    being shown.
 *  - The block duration is READ LIVE from AppSettings on every call,
 *    so a user change (e.g. 5 -> 20 minutes) takes effect immediately,
 *    even for a block that is already in progress.
 *  - All operations are O(1) amortized (deque push/pop), so the
 *    decision for each SMS is made in well under a millisecond.
 */

import java.util.concurrent.ConcurrentLinkedDeque

class RateLimiter(
    private val thresholdCount: Int = 2,
    private val windowMillis: Long = 5_000L
) {
    private val timestamps = ConcurrentLinkedDeque<Long>()

    @Volatile
    private var blockedSince: Long? = null

    /** Called once per incoming SMS. Fast: no I/O, just in-memory arithmetic. */
    @Synchronized
    fun onSmsReceived(now: Long = System.currentTimeMillis()): Decision {
        val durationMillis = AppSettings.current.blockDurationMillis

        blockedSince?.let { since ->
            if (now - since >= durationMillis) {
                unblock()
            } else {
                timestamps.addLast(now)
                return Decision.QUARANTINE
            }
        }

        timestamps.addLast(now)
        while (timestamps.isNotEmpty() && now - timestamps.first() > windowMillis) {
            timestamps.pollFirst()
        }

        if (timestamps.size > thresholdCount) {
            blockedSince = now
            return Decision.QUARANTINE
        }

        return Decision.ALLOW
    }

    /** Current block state, without recording a new message (used by UI / worker). */
    @Synchronized
    fun isCurrentlyBlocked(now: Long = System.currentTimeMillis()): Boolean {
        val since = blockedSince ?: return false
        val durationMillis = AppSettings.current.blockDurationMillis
        if (now - since >= durationMillis) {
            unblock()
            return false
        }
        return true
    }

    /** Remaining time in the current block window, or 0 if not blocked. */
    @Synchronized
    fun remainingMillis(now: Long = System.currentTimeMillis()): Long {
        val since = blockedSince ?: return 0L
        val durationMillis = AppSettings.current.blockDurationMillis
        return (durationMillis - (now - since)).coerceAtLeast(0L)
    }

    /** Manual unlock (e.g. "Unblock now" button), or automatic reset after the window elapses. */
    @Synchronized
    fun unblock() {
        blockedSince = null
        timestamps.clear()
    }

    enum class Decision { ALLOW, QUARANTINE }
}
