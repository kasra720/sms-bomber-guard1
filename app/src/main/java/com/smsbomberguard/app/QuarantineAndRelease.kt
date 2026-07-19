package com.smsbomberguard.app

/**
 * QuarantineAndRelease.kt
 * -----------------------------------------------------------
 * Quarantined messages are NEVER surfaced as a notification burst -
 * not while blocked, and not afterwards either. They are written to
 * a silent, observable log that the UI can show whenever the user
 * opens the app. From the user's point of view, once the block
 * window ends, it's as if the bomber attack never happened: no
 * notification storm, before or after.
 */

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class QuarantinedSms(
    val sender: String,
    val body: String,
    val timestamp: Long
)

/**
 * Silent, in-app-only log of withheld messages. Exposed as a
 * StateFlow so the Compose UI updates reactively with zero polling.
 *
 * NOTE: backed by memory here for brevity. For production, persist
 * this list (e.g. Room) so it survives process death - the API
 * surface below would stay identical.
 */
object SilentSmsLog {
    private val _items = MutableStateFlow<List<QuarantinedSms>>(emptyList())
    val items: StateFlow<List<QuarantinedSms>> = _items.asStateFlow()

    @Synchronized
    fun save(sender: String, body: String, timestamp: Long) {
        _items.value = _items.value + QuarantinedSms(sender, body, timestamp)
    }

    @Synchronized
    fun clear() {
        _items.value = emptyList()
    }

    fun count(): Int = _items.value.size
}

// Kept as a thin alias so SmsReceiver.kt's existing call site keeps working.
object QuarantineStore {
    fun save(context: Context, sender: String, body: String, timestamp: Long) {
        SilentSmsLog.save(sender, body, timestamp)
    }
}

/**
 * Runs once the configured block duration has elapsed. It does not
 * show anything - its only job is to make sure the limiter's
 * internal state is cleanly reset even if no further SMS arrives to
 * trigger that check organically.
 */
class AutoReleaseWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val limiter = RateLimiterProvider.get()
        limiter.isCurrentlyBlocked() // lazily unblocks internally if the window has passed
        return Result.success()
    }
}

object NotificationHelper {
    private const val CHANNEL_ID = "sms_channel"

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.createNotificationChannel(
            android.app.NotificationChannel(CHANNEL_ID, "Messages", android.app.NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    /** The ONLY notification path in the whole app - used exclusively for ALLOWed messages. */
    fun showSmsNotification(context: Context, sender: String, body: String) {
        ensureChannel(context)
        val notification = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(sender)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        try {
            androidx.core.app.NotificationManagerCompat.from(context).notify(sender.hashCode(), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted yet - request it from the UI.
        }
    }
}
