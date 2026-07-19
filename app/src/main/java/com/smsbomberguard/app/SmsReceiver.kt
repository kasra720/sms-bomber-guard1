package com.smsbomberguard.app

/**
 * SmsReceiver.kt
 * -----------------------------------------------------------
 * Hooks into incoming SMS, applies the rate limiter, and either
 * shows a normal notification or silently quarantines the message.
 *
 * Fast path: if protection is turned OFF in settings, we skip all
 * detection logic entirely and just show the message normally -
 * zero overhead.
 *
 * Platform note: to fully suppress the notification shown by the
 * phone's default Messages app (not just this app's own
 * notification), this app must be set as the DEFAULT SMS APP on the
 * device. Without that, Android only lets this receiver see the
 * message; it cannot stop another app's notification.
 */

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // Fast path - protection disabled by the user.
        if (!AppSettings.current.protectionEnabled) {
            for (msg in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                NotificationHelper.showSmsNotification(
                    context,
                    msg.originatingAddress ?: "Unknown",
                    msg.messageBody ?: ""
                )
            }
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val limiter = RateLimiterProvider.get()

        for (msg in messages) {
            val sender = msg.originatingAddress ?: "Unknown"
            val body = msg.messageBody ?: ""
            val now = System.currentTimeMillis()

            when (limiter.onSmsReceived(now)) {
                RateLimiter.Decision.ALLOW -> {
                    NotificationHelper.showSmsNotification(context, sender, body)
                }
                RateLimiter.Decision.QUARANTINE -> {
                    // Stored silently. No notification is shown now, and none will be
                    // shown later either - see AutoReleaseWorker. From the user's
                    // perspective, it's as if nothing arrived during the attack.
                    QuarantineStore.save(context, sender, body, now)
                }
            }
        }

        scheduleAutoRelease(context)
    }

    private fun scheduleAutoRelease(context: Context) {
        val request = OneTimeWorkRequestBuilder<AutoReleaseWorker>()
            .setInitialDelay(AppSettings.current.blockDurationMinutes.toLong(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}

/**
 * Single shared RateLimiter instance for the process lifetime.
 * (Detection state itself is intentionally in-memory only - it only
 * needs to survive as long as an attack is actively happening.)
 */
object RateLimiterProvider {
    @Volatile private var instance: RateLimiter? = null

    fun get(): RateLimiter =
        instance ?: synchronized(this) {
            instance ?: RateLimiter().also { instance = it }
        }
}
