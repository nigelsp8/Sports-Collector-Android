package com.nigelspeight.sportscollector.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nigelspeight.sportscollector.MainActivity
import com.nigelspeight.sportscollector.R

/// Firebase Cloud Messaging hookup - the Android side of the iOS
/// `AppDelegate`'s `MessagingDelegate`/`UNUserNotificationCenterDelegate`.
///
/// While the app is in the background, FCM posts notification messages itself
/// (using the default icon/channel from the manifest). While it's in the
/// foreground they arrive here instead, and are posted manually so they still
/// show as a banner - matching iOS's `willPresent` returning `.banner`.
class PushMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Log.e(TAG, "FCM registration token: $token")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.e(TAG, "Received remote notification: ${message.data}")
        val notification = message.notification ?: return
        showNotification(this, notification.title, notification.body)
    }

    companion object {
        private const val TAG = "PushMessaging"

        /// Creates the notification channel and logs the current FCM token -
        /// call once, from `AppServices.init`.
        fun start(context: Context) {
            val channel = NotificationChannel(
                context.getString(R.string.default_notification_channel_id),
                context.getString(R.string.default_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.e(TAG, "FCM registration token: ${task.result}")
                } else {
                    Log.e(TAG, "Failed to fetch FCM registration token", task.exception)
                }
            }
        }

        private fun showNotification(context: Context, title: String?, body: String?) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return

            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, context.getString(R.string.default_notification_channel_id))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(openApp)
                .build()
            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
        }
    }
}
