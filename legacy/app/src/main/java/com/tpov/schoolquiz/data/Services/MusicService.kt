package com.tpov.schoolquiz.data.Services

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.IBinder
import com.tpov.schoolquiz.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MusicService : Service() {
    private var player: MediaPlayer? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
            //if (player == null) {
        //    player = MediaPlayer.create(this@MusicService, R.raw.music_wot)
                    //}

        coroutineScope.launch {
            player?.start()
        }


    }

    override fun onDestroy() {
        super.onDestroy()
        player?.stop()
        coroutineScope.cancel()
    }
}