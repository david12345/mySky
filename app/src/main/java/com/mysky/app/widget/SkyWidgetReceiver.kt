package com.mysky.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import dagger.hilt.android.AndroidEntryPoint

/**
 * Receiver declarado no manifesto. Agenda/cancela o trabalho periódico quando o primeiro widget é
 * adicionado e o último é removido — não faz sentido manter o worker vivo sem widget nem
 * notificações ativas.
 *
 * TODO(feature/widget): sobrepor `onEnabled`/`onDisabled` para agendar e cancelar via
 *  [com.mysky.app.worker.SkyWorkScheduler].
 */
@AndroidEntryPoint
class SkyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SkyWidget()
}
