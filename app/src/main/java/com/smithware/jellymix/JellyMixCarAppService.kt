package com.smithware.jellymix

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator

class JellyMixCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(sessionInfo: SessionInfo): Session =
        JellyMixCarSession()
}

private class JellyMixCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen =
        JellyMixCarScreen(carContext)
}

private class JellyMixCarScreen(carContext: androidx.car.app.CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template =
        MessageTemplate.Builder(
            "Use JellyMix from the Android Auto media apps list. Your curated mixes, Vibes, Jarvis DJ, and Library are served through JellyMix media."
        )
            .setTitle("JellyMix")
            .setHeaderAction(Action.APP_ICON)
            .build()
}
