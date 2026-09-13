package com.mysky.app.widget

import android.content.ComponentName
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.datastore.preferences.core.Preferences
import com.mysky.app.R
import com.mysky.app.domain.model.SkyWidgetState
import com.mysky.app.presentation.format.FlightFormatting
import com.mysky.app.presentation.format.Freshness
import com.mysky.app.worker.RefreshFeedback
import kotlinx.coroutines.flow.first

/**
 * Widget de ecrã inicial: a aeronave mais alta no céu, e **quando** foi vista.
 *
 * Duas coisas que este ficheiro tem de fazer bem, e ambas são silenciosas quando falham:
 *
 * 1. **Não vai à rede** (FR-009). Lê o último resultado já calculado, do porto de domínio (AD-023).
 *    Ler disco localmente é permitido; o que é proibido é ir à rede na composição.
 * 2. **Avalia o estado agora**, não quando o worker gravou (AD-024). A cadência por omissão é seis
 *    vezes a janela de frescura: se o texto viesse decidido de trás, o widget afirmaria presença
 *    durante cerca de 25 dos 30 minutos em que isso já era falso.
 */
class SkyWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = context.widgetEntryPoint()
        val snapshot = entryPoint.skyWidgetRepository().snapshot.first()
        val now = entryPoint.timeProvider().nowEpochSeconds()

        val state = SkyWidgetState.evaluate(snapshot, now)
        val freshnessText = context.freshnessTextFor(snapshot?.observedAtEpochSeconds, now)

        provideContent {
            GlanceTheme {
                // Lido aqui dentro porque `currentState` é `@Composable`. É o estado efémero **desta**
                // instância — não do céu, e não partilhado com os outros widgets (AD-023).
                val ephemeral = currentState<Preferences>()
                val isRefreshing = ephemeral[RefreshSkyWidgetAction.REFRESHING] == true
                val feedback = ephemeral[RefreshSkyWidgetAction.LAST_FEEDBACK]
                    ?.let { name -> runCatching { RefreshFeedback.valueOf(name) }.getOrNull() }
                    ?: RefreshFeedback.None

                SkyWidgetContent(
                    state = state,
                    freshnessText = freshnessText,
                    onOpenApp = actionStartActivity(mainActivityComponent(context)),
                    isRefreshing = isRefreshing,
                    feedback = feedback,
                )
            }
        }
    }
}

/**
 * O ecrã a abrir ao toque (FR-008).
 *
 * Por `ComponentName` e não por classe: o `widget/` não importa `presentation/`, o que mantém a
 * dependência num sentido só e evita arrastar o grafo da UI para dentro do widget.
 */
private fun mainActivityComponent(context: Context): ComponentName =
    ComponentName(context.packageName, "com.mysky.app.presentation.MainActivity")

/**
 * "há 3 min", pelo mesmo formatador que os ecrãs usam.
 *
 * Reaproveitado de propósito: uma segunda forma de dizer há quanto tempo foi, com outras palavras e
 * outros arredondamentos, seria a app a contradizer-se entre o widget e o ecrã de detalhe.
 */
private fun Context.freshnessTextFor(observedAtEpochSeconds: Long?, nowEpochSeconds: Long): String {
    if (observedAtEpochSeconds == null) return ""
    return when (val freshness = FlightFormatting.freshnessOf(nowEpochSeconds, observedAtEpochSeconds)) {
        Freshness.JustNow -> getString(R.string.sky_updated_just_now)
        is Freshness.Seconds -> getString(R.string.sky_updated_seconds_ago, freshness.value)
        is Freshness.Minutes -> getString(R.string.sky_updated_minutes_ago, freshness.value)
    }
}
