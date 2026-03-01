package com.grindrplus.manager

import android.app.Application
import com.grindrplus.core.Config
import com.grindrplus.core.Logger
import com.grindrplus.core.NotificationSender
import com.grindrplus.core.repository.BlockLogRepository
import com.grindrplus.core.repository.ConfigRepository
import com.grindrplus.core.repository.LogRepository

// This should hold all shared state between MainActiviry (manager activity) and BridgeService
class GPApp : Application() {
    companion object {
        lateinit var configRepository: ConfigRepository
            private set
        lateinit var logRepository: LogRepository
            private set
        lateinit var blockLogRepository: BlockLogRepository
            private set
        lateinit var notificationSender: NotificationSender
            private set

        lateinit var config: Config
            private set
    }



    override fun onCreate() {
        super.onCreate()

        configRepository = ConfigRepository(this)
        logRepository = LogRepository(this)
        blockLogRepository = BlockLogRepository(this)
        notificationSender = NotificationSender(this)

        config = Config(
            getConfig = { configRepository.getConfig() },
            onChange = { configRepository.setConfig(it) }
        )

        Logger.initialize(false) { message ->
            logRepository.writeRawLog(message)
        }
    }
}
