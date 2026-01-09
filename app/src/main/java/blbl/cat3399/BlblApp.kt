package blbl.cat3399

import android.app.Application
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.net.BiliClient

class BlblApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        AppLog.i("BlblApp", "onCreate")
        BiliClient.init(this)
    }

    companion object {
        @JvmStatic
        lateinit var instance: BlblApp
            private set
    }
}

