package com.mysky.app.widget

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.mysky.app.R
import com.mysky.app.domain.model.SkyWidgetState

/**
 * O que se desenha para cada estado.
 *
 * A regra que atravessa este ficheiro: **nunca uma caixa sem texto** (FR-007), e o instante à vista
 * sempre que há dados (FR-002). O `when` sobre um tipo selado é exaustivo, o que torna
 * estruturalmente impossível esquecer um estado — se um estado novo aparecer, isto deixa de compilar.
 *
 * A separação entre presente e passado está em **strings diferentes**, não numa interpolação que
 * troque o verbo. Uma frase montada em pedaços é uma frase que alguém pode montar mal.
 */
@Composable
internal fun SkyWidgetContent(
    state: SkyWidgetState,
    freshnessText: String,
    onOpenApp: Action,
) {
    val context = LocalContext.current

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(onOpenApp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        when (state) {
            SkyWidgetState.NoDataYet -> Message(
                title = context.getString(R.string.widget_no_data_title),
                detail = context.getString(R.string.widget_no_data_detail),
            )

            SkyWidgetState.PermissionMissing -> Message(
                title = context.getString(R.string.widget_permission_title),
                detail = context.getString(R.string.widget_permission_detail),
            )

            is SkyWidgetState.EmptySky -> Message(
                title = context.getString(
                    if (state.isFresh) R.string.widget_empty_fresh_title else R.string.widget_empty_stale_title,
                ),
                detail = context.getString(
                    if (state.isFresh) R.string.widget_empty_fresh_detail else R.string.widget_empty_stale_detail,
                    freshnessText,
                ),
            )

            is SkyWidgetState.Fresh -> FlightRow(
                headline = state.flight.callsign ?: context.getString(R.string.widget_no_callsign),
                subtitle = state.flight.airlineName,
                // Presente, e só aqui: este é o único ramo em que a observação é recente o bastante
                // para a afirmação ser plausível.
                detail = context.getString(
                    R.string.widget_fresh_detail, state.flight.elevationDegrees.toInt(), freshnessText,
                ),
                extra = state.count,
            )

            is SkyWidgetState.Stale -> FlightRow(
                headline = state.flight.callsign ?: context.getString(R.string.widget_no_callsign),
                subtitle = state.flight.airlineName,
                // Passado. O avião atravessa o céu em 4 a 5 minutos; dizer que ainda lá está seria
                // afirmar o que quase de certeza já é falso.
                detail = context.getString(
                    R.string.widget_stale_detail, state.flight.elevationDegrees.toInt(), freshnessText,
                ),
                extra = state.count,
            )
        }
    }
}

@Composable
private fun Message(title: String, detail: String) {
    Column {
        Text(text = title, style = TextStyle(fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface))
        Text(text = detail, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
    }
}

@Composable
private fun FlightRow(headline: String, subtitle: String?, detail: String, extra: Int) {
    Column {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(
                text = headline,
                style = TextStyle(fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface),
            )
            // Só aparece quando acrescenta informação: "e mais 1" a dizer que há 2 é ruído.
            if (extra > 1) {
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = LocalContext.current.getString(R.string.widget_more_flights, extra - 1),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                )
            }
        }
        // A companhia desaparece quando é desconhecida, em vez de deixar uma linha vazia — mas o
        // avião continua lá (FR-005).
        subtitle?.let {
            Text(text = it, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
        }
        Text(text = detail, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
    }
}
