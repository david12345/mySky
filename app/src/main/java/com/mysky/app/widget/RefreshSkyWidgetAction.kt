package com.mysky.app.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll

/**
 * O toque em "atualizar".
 *
 * Duas coisas que este ficheiro **não** faz, e ambas por razões concretas:
 *
 * - **Não vai à rede.** Um `ActionCallback` corre com orçamento de tempo curto; enfileira-se trabalho
 *   e devolve-se o controlo (FR-016). O `ExistingWorkPolicy.KEEP` do agendador é o que faz dois
 *   toques seguidos valerem por um (FR-017) — com `REPLACE`, o segundo cancelaria uma consulta já
 *   paga e começaria outra, gastando duas do orçamento para obter um resultado.
 * - **Não escreve no porto de domínio.** O "a atualizar" é estado de UI **desta** instância de
 *   widget, não um facto sobre o céu: pertence ao estado do Glance, que é por `GlanceId` (AD-023).
 *   Guardá-lo no porto partilhado faria o indicador aparecer em todos os widgets ao mesmo tempo e
 *   sobreviver à morte do processo, que é precisamente o que não deve acontecer.
 */
class RefreshSkyWidgetAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        updateAppWidgetState(context, glanceId) { preferences ->
            preferences[REFRESHING] = true
            // O desfecho anterior sai de cena assim que se tenta outra vez: manter "sem ligação"
            // visível durante uma nova tentativa seria mostrar uma resposta a uma pergunta antiga.
            preferences.remove(LAST_FEEDBACK)
        }
        // Redesenha já, para o utilizador ver que o toque foi registado antes de haver resposta.
        SkyWidget().updateAll(context)

        context.widgetEntryPoint().skyWorkScheduler().requestImmediateRefresh()
    }

    companion object {
        /** Efémero e por widget. Ver a KDoc acima para a razão de não viver no porto de domínio. */
        val REFRESHING = booleanPreferencesKey("refreshing")

        /** Como correu a última tentativa, por nome de [com.mysky.app.worker.RefreshFeedback]. */
        val LAST_FEEDBACK = stringPreferencesKey("last_feedback")
    }
}
