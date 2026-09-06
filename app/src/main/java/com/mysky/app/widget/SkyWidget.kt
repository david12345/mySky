package com.mysky.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.Text

/**
 * Widget de ecrã inicial: mostra o avião mais relevante no céu (maior elevação) ou
 * "sem aviões no momento".
 *
 * O widget **não** vai à rede: lê o último resultado calculado pelo worker periódico
 * ([com.mysky.app.worker.SkyRefreshWorker]) a partir do estado do Glance. O toque em "atualizar"
 * enfileira um trabalho único ([RefreshSkyWidgetAction]) em vez de bloquear a composição.
 *
 * TODO(feature/widget): definir o `GlanceStateDefinition` (DataStore de preferências do widget),
 *  desenhar o conteúdo real e tratar os estados: sem permissão de localização, sem dados ainda,
 *  dados obsoletos (mostrar a hora da última atualização).
 */
class SkyWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            ) {
                Text(text = "mySky")
            }
        }
    }
}
