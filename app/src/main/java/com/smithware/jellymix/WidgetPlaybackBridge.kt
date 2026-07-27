package com.smithware.jellymix

import android.os.Handler
import android.os.Looper

interface WidgetPlaybackController {
    fun togglePlayPause()
    fun play()
    fun pause()
    fun skip()
    fun previous()
    fun stopPlayback()
}

object WidgetPlaybackBridge {
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var controller: WidgetPlaybackController? = null

    fun register(controller: WidgetPlaybackController) {
        this.controller = controller
    }

    fun unregister(controller: WidgetPlaybackController) {
        if (this.controller === controller) {
            this.controller = null
        }
    }

    fun dispatch(action: String?): Boolean {
        val activeController = controller ?: return false
        mainHandler.post {
            when (action) {
                WIDGET_ACTION_PLAY_PAUSE -> activeController.togglePlayPause()
                WIDGET_ACTION_PLAY -> activeController.play()
                WIDGET_ACTION_PAUSE -> activeController.pause()
                WIDGET_ACTION_SKIP -> activeController.skip()
                WIDGET_ACTION_PREVIOUS -> activeController.previous()
                WIDGET_ACTION_STOP -> activeController.stopPlayback()
            }
        }
        return action in setOf(
            WIDGET_ACTION_PLAY_PAUSE,
            WIDGET_ACTION_PLAY,
            WIDGET_ACTION_PAUSE,
            WIDGET_ACTION_SKIP,
            WIDGET_ACTION_PREVIOUS,
            WIDGET_ACTION_STOP
        )
    }
}
