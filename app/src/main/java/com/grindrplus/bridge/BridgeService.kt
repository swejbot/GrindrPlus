package com.grindrplus.bridge

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.grindrplus.core.LogSource
import com.grindrplus.core.Logger
import com.grindrplus.manager.GPApp
import com.grindrplus.manager.fetchNotifs
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock

@SuppressLint("MissingPermission")
class BridgeService : Service() {

    private val configRepository by lazy { GPApp.configRepository }
    private val logRepository by lazy { GPApp.logRepository }
    private val blockLogRepository by lazy { GPApp.blockLogRepository }
    private val notificationSender by lazy { GPApp.notificationSender }
    private val periodicTasksExecutor = Executors.newSingleThreadScheduledExecutor()

    private var isForegroundStarted = false

    override fun onCreate() {
        super.onCreate()
        Logger.i("BridgeService created", LogSource.BRIDGE)

        Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND)
    }

    override fun onBind(intent: Intent?): IBinder {
        Logger.i("BridgeService bound", LogSource.BRIDGE)
        startForegroundSafely()
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.i("BridgeService started", LogSource.BRIDGE)

        startForegroundSafely()

        return START_STICKY
    }

    private fun startForegroundSafely() {
        if (isForegroundStarted) {
            return
        }

        try {
            val channelId = "bridge_service_channel"
            notificationSender.createNotificationChannel(channelId, "GrindrPlus Background Service", "Keeps GrindrPlus running in background")

            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("GrindrPlus")
                .setContentText("Background service active")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setOngoing(true)
                .setShowWhen(false)
                .build()

            try {
                periodicTasksExecutor.scheduleWithFixedDelay(
                    { runBlocking { fetchNotifs(this@BridgeService) } },
                    0,
                    15,
                    java.util.concurrent.TimeUnit.SECONDS
                )
            } catch (e: Exception) {
                Logger.e( "Failed to schedule periodic tasks: ${e.message}", LogSource.BRIDGE)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    1001,
                    notification,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    } else {
                        0
                    }
                )
            } else {
                startForeground(1001, notification)
            }

            isForegroundStarted = true
            Logger.i("Foreground service started successfully", LogSource.BRIDGE)

        } catch (e: Exception) {
            Logger.w("Failed to start foreground service: ${e.message}", LogSource.BRIDGE)
            Logger.writeRaw(e.stackTraceToString())

            // If we can't start as foreground, continue as normal service
            // The service will still work, just won't be protected from being killed
            isForegroundStarted = false
        }
    }

    private val binder = object : IBridgeService.Stub() {
        override fun getConfig(): String =
            configRepository.getConfig()


        override fun setConfig(config: String?) =
            configRepository.setConfig(config)

        override fun log(level: String, source: String, message: String, hookName: String?) =
            logRepository.log(level, source, message, hookName)

        override fun writeRawLog(content: String) =
            logRepository.writeRawLog(content)

        override fun clearLogs() =
            logRepository.clearLogs()


        override fun sendNotification(
            title: String,
            message: String,
            notificationId: Int,
            channelId: String,
            channelName: String,
            channelDescription: String
        ) =
            notificationSender.sendNotification(
                title,
                message,
                notificationId,
                channelId,
                channelName,
                channelDescription
            )

        override fun sendNotificationWithActions(
            title: String,
            message: String,
            notificationId: Int,
            channelId: String,
            channelName: String,
            channelDescription: String,
            actionLabels: Array<String>,
            actionIntents: Array<String>,
            actionData: Array<String>
        ) =
            notificationSender.sendNotificationWithActions(
                title,
                message,
                notificationId,
                channelId,
                channelName,
                channelDescription,
                actionLabels,
                actionIntents,
                actionData
            )

        override fun logBlockEvent(
            profileId: String,
            displayName: String,
            isBlock: Boolean,
            packageName: String
        ) =
            blockLogRepository.logBlockEvent(profileId, displayName, isBlock, packageName)

        override fun getBlockEvents(): String =
            blockLogRepository.getBlockEvents()

        override fun clearBlockEvents() =
            blockLogRepository.clearBlockEvents()

        override fun shouldRegenAndroidId(packageName: String): Boolean {
            val regenFile = File(getExternalFilesDir(null), "$packageName.android_id_regen")
            return regenFile.exists().also { exists ->
                if (exists) {
                    regenFile.delete()
                }
            }
        }

        override fun getForcedLocation(packageName: String): String {
            val coordinatesFile = File(getExternalFilesDir(null), "$packageName.location")
            return if (coordinatesFile.exists()) {
                coordinatesFile.readText().trim().ifBlank { "" }
            } else {
                ""
            }
        }

        override fun deleteForcedLocation(packageName: String) {
            val coordinatesFile = File(getExternalFilesDir(null), "$packageName.location")
            if (coordinatesFile.exists()) {
                coordinatesFile.delete()
            }
        }

        override fun isRooted(): Boolean {
            return com.grindrplus.manager.utils.isRooted(applicationContext)
        }

        override fun isLSPosed(): Boolean {
            return com.grindrplus.manager.utils.isLSPosed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.i("BridgeService destroyed", LogSource.BRIDGE)
        periodicTasksExecutor.shutdown()
    }

    companion object {
        private const val TAG = "BridgeService"
    }
}