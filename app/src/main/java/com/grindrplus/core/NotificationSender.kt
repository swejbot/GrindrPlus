package com.grindrplus.core

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class NotificationSender(
    val context: Context
) {

    fun sendNotification(
        title: String,
        message: String,
        notificationId: Int,
        channelId: String,
        channelName: String,
        channelDescription: String
    ) {
        Logger.d("sendNotification() called")
        try {
            createNotificationChannel(channelId, channelName, channelDescription)

            val notificationBuilder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            with(NotificationManagerCompat.from(context)) {
                notify(notificationId, notificationBuilder.build())
            }
        } catch (e: Exception) {
            Logger.e("Error sending notification", LogSource.BRIDGE)
            Logger.writeRaw(e.stackTraceToString())
        }
    }

    fun sendNotificationWithActions(
        title: String,
        message: String,
        notificationId: Int,
        channelId: String,
        channelName: String,
        channelDescription: String,
        actionLabels: Array<String>,
        actionIntents: Array<String>,
        actionData: Array<String>
    ) {
        Logger.d("sendNotificationWithActions() called")
        try {
            createNotificationChannel(channelId, channelName, channelDescription)

            val notificationBuilder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            for (i in actionLabels.indices) {
                if (i >= actionIntents.size || i >= actionData.size) break

                val intent = createActionIntent(actionIntents[i], actionData[i])
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    notificationId + i,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                notificationBuilder.addAction(
                    R.drawable.ic_menu_send,
                    actionLabels[i],
                    pendingIntent
                )
            }

            with(NotificationManagerCompat.from(context)) {
                notify(notificationId, notificationBuilder.build())
            }
        } catch (e: Exception) {
            Logger.e("Error sending notification with actions", LogSource.BRIDGE)
            Logger.writeRaw(e.stackTraceToString())
        }
    }


    fun createNotificationChannel(
        channelId: String,
        channelName: String,
        channelDescription: String
    ) {
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (notificationManager.getNotificationChannel(channelId) == null) {
            val importance = if (channelId == "bridge_service_channel") {
                NotificationManager.IMPORTANCE_MIN
            } else {
                NotificationManager.IMPORTANCE_DEFAULT
            }

            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = channelDescription
                if (channelId == "bridge_service_channel") {
                    setShowBadge(false)
                    setSound(null, null)
                    enableLights(false)
                    enableVibration(false)
                }
            }
            notificationManager.createNotificationChannel(channel)
            Logger.d("Notification channel created: $channelId", LogSource.BRIDGE)
        }
    }

    private fun createActionIntent(actionType: String, actionData: String): Intent {
        val packageName = context.packageName

        val intent = when (actionType) {
            "COPY" -> Intent("com.grindrplus.COPY_ACTION").apply {
                putExtra("data", actionData)
                setPackage(packageName)
                setClassName(
                    packageName,
                    "$packageName.bridge.NotificationActionReceiver"
                )
            }

            "VIEW_PROFILE" -> Intent("com.grindrplus.VIEW_PROFILE_ACTION").apply {
                putExtra("profileId", actionData)
                setPackage(packageName)
                setClassName(
                    packageName,
                    "${packageName}.bridge.NotificationActionReceiver"
                )
            }

            "CUSTOM" -> Intent("com.grindrplus.CUSTOM_ACTION").apply {
                putExtra("data", actionData)
                setPackage(packageName)
                setClassName(
                    packageName,
                    "${packageName}.bridge.NotificationActionReceiver"
                )
            }

            else -> Intent("com.grindrplus.DEFAULT_ACTION").apply {
                putExtra("data", actionData)
                setPackage(packageName)
                setClassName(
                    packageName,
                    "${packageName}.bridge.NotificationActionReceiver"
                )
            }
        }

        Logger.d("Action intent created: $actionType with data: $actionData", LogSource.BRIDGE)
        return intent
    }

    companion object {
        const val CHANNEL_BLOCKS = "grindr_plus_blocks"
        const val CHANNEL_UNBLOCKS = "grindr_plus_unblocks"
        const val CHANNEL_GENERAL = "grindr_plus_general"
    }
}