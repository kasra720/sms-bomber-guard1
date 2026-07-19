package com.smsbomberguard.app

/**
 * NotificationBlockerService.kt
 * -----------------------------------------------------------
 * Lets the app suppress the notification shown by the PHONE'S OWN
 * default messaging app (e.g. Google Messages / Samsung Messages)
 * WITHOUT becoming the default SMS app itself.
 *
 * How it works:
 *   - This app still reads incoming SMS content via SmsReceiver
 *     (RECEIVE_SMS / READ_SMS) - that part needs no special role.
 *   - Separately, this NotificationListenerService is notified of
 *     every notification posted by ANY app on the device (after the
 *     user grants "Notification access" - a special permission, not
 *     a runtime dialog, granted once in Settings).
 *   - While the RateLimiter is in "blocked" state, any notification
 *     categorized as a message (NotificationCompat.CATEGORY_MESSAGE
 *     - which virtually every SMS app uses) from a package other
 *     than our own is cancelled immediately, so it never reaches the
 *     status bar.
 *
 * This requires no default-app role change and works alongside
 * whatever messaging app the user already has as default.
 */

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationBlockerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == applicationContext.packageName) return // never touch our own
        if (!AppSettings.current.protectionEnabled) return
        if (!RateLimiterProvider.get().isCurrentlyBlocked()) return

        val category = sbn.notification.category
        if (category == Notification.CATEGORY_MESSAGE) {
            // Withheld silently, same as messages we intercept via SmsReceiver -
            // no notification appears, before or after the block window.
            cancelNotification(sbn.key)
        }
    }
}
