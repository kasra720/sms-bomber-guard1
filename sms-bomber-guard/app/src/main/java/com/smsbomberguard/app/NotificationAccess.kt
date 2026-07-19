package com.smsbomberguard.app

/**
 * NotificationAccess.kt
 * -----------------------------------------------------------
 * "Notification access" is a special permission (not a normal
 * runtime permission dialog) - the user must grant it once from a
 * system settings screen. This is what lets NotificationBlockerService
 * see and cancel notifications posted by other apps.
 */

import android.content.Context
import android.content.Intent
import android.provider.Settings

object NotificationAccess {

    fun isGranted(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return flat.contains(context.packageName)
    }

    /** Opens the system screen where the user can grant/revoke access for this app. */
    fun openSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
}
