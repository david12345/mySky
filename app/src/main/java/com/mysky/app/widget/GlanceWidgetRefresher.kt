package com.mysky.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.glance.appwidget.state.updateAppWidgetState
import com.mysky.app.worker.RefreshFeedback
import com.mysky.app.worker.WidgetPresenceCheck
import com.mysky.app.worker.WidgetRefresher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * O lado do Glance das duas pontes que o `worker/` declara (AD-025).
 *
 * As duas juntas na mesma classe porque são a mesma dependência — o `GlanceAppWidgetManager` e o
 * `SkyWidget` — vista de dois ângulos, e separá-las daria duas classes de três linhas com o mesmo
 * construtor.
 */
@Singleton
class GlanceWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) : WidgetRefresher, WidgetPresenceCheck {

    override suspend fun refreshAll(feedback: RefreshFeedback) {
        try {
            // Limpar o "a atualizar" e escrever o desfecho **antes** de repintar, senão o repinte
            // mostrava o estado antigo. É a mesma armadilha que na 003 deixou um ecrã preso em
            // "A atualizar…" para sempre.
            GlanceAppWidgetManager(context).getGlanceIds(SkyWidget::class.java).forEach { id ->
                updateAppWidgetState(context, id) { preferences ->
                    preferences[RefreshSkyWidgetAction.REFRESHING] = false
                    preferences[RefreshSkyWidgetAction.LAST_FEEDBACK] = feedback.name
                }
            }
            SkyWidget().updateAll(context)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            // Repintar é o último passo do ciclo e o menos importante: o snapshot já está gravado, e
            // o widget mostra-o na composição seguinte de qualquer forma. Deixar isto rebentar
            // transformaria um repinte falhado num ciclo falhado, com `retry` e consulta a mais.
        }
    }

    override suspend fun hasAnyWidget(): Boolean = try {
        GlanceAppWidgetManager(context).getGlanceIds(SkyWidget::class.java).isNotEmpty()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        // Na dúvida, dizer que **não** há widget: o pior caso é não agendar trabalho que ninguém
        // pediu. O simétrico — agendar por engano — gastaria bateria e orçamento a calcular para um
        // ecrã que não existe.
        false
    }
}
