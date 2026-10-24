package app.groove

import android.app.Application
import app.groove.data.UserStore
import app.groove.playback.AudioFx

class GrooveApp : Application() {
    override fun onCreate() {
        super.onCreate()
        UserStore.init(this)
        AudioFx.init(this)
    }
}
