package com.rady.reelio

import android.app.Application
import com.rady.reelio.data.MediaLibrary
import com.rady.reelio.data.PlaylistStore
import com.rady.reelio.data.PositionStore
import com.rady.reelio.data.Settings
import com.rady.reelio.data.ShuffleMemory
import com.rady.reelio.player.PlayerHub
import kotlinx.coroutines.MainScope

class ReelioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}

/** App-wide singletons. */
object AppGraph {
    val scope = MainScope()
    lateinit var settings: Settings; private set
    lateinit var library: MediaLibrary; private set
    lateinit var playlists: PlaylistStore; private set
    lateinit var positions: PositionStore; private set
    lateinit var shuffleMemory: ShuffleMemory; private set

    fun init(app: Application) {
        settings = Settings(app)
        library = MediaLibrary(app, settings, scope)
        playlists = PlaylistStore(app, scope)
        positions = PositionStore(app)
        shuffleMemory = ShuffleMemory(app)
        PlayerHub.init(app)
    }
}
