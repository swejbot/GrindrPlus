package com.grindrplus.manager

import android.app.Application
import com.grindrplus.GrindrPlus
import com.grindrplus.bridge.BridgeClient

// Application has a different, longer lifecycle than Activity,
// allowing to keep the bridge client connected/in memory while the activity is closed
class ManagerApp : Application() {
    companion object {
        lateinit var bridgeClient: BridgeClient
            private set
    }

    override fun onCreate() {
        super.onCreate()
        bridgeClient = BridgeClient(this)
        GrindrPlus.bridgeClient = bridgeClient
    }
}
