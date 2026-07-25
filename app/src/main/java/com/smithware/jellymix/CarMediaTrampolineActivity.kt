package com.smithware.jellymix

import android.app.Activity
import android.content.Intent
import android.os.Bundle

private const val SHOW_MEDIA_PLAYBACK_ACTION = "androidx.car.app.media.action.SHOW_MEDIA_PLAYBACK"

class CarMediaTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = if (intent?.action == SHOW_MEDIA_PLAYBACK_ACTION) {
            SHOW_MEDIA_PLAYBACK_ACTION
        } else {
            Intent.ACTION_MAIN
        }
        startActivity(
            Intent(action).setClassName(
                packageName,
                "androidx.car.app.activity.CarAppActivity"
            )
        )
        finish()
    }
}
