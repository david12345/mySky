package com.mysky.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/**
 * Ação de "tap-to-refresh" no widget.
 *
 * Enfileira um `OneTimeWorkRequest` expedito: o callback do Glance corre com orçamento de tempo
 * curto e não deve fazer rede diretamente.
 *
 * TODO(feature/widget): enfileirar via [com.mysky.app.worker.SkyWorkScheduler.requestImmediateRefresh].
 */
class RefreshSkyWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        TODO("Implementar durante a feature 'widget' (ver .specify/)")
    }
}
