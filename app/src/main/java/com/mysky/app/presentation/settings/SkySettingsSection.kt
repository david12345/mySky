package com.mysky.app.presentation.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import com.mysky.app.domain.model.NotificationPolicy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.mysky.app.R
import com.mysky.app.domain.model.AltitudeUnit
import com.mysky.app.domain.model.DistanceUnit
import com.mysky.app.domain.model.SkySettings
import com.mysky.app.presentation.format.FlightFormatting
import com.mysky.app.presentation.format.UnitLabels
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Os controlos do que conta como "o meu céu".
 *
 * O detalhe que importa mais neste ficheiro é onde a escrita acontece: no `onValueChangeFinished`,
 * quando o utilizador **larga** o cursor, e nunca no `onValueChange`. Ligada a cada movimento, a
 * escrita funcionaria perfeitamente e gastaria o orçamento de um dia num único arrasto — sem erro,
 * sem aviso, e a app deixaria de atualizar a meio da tarde (AD-019).
 */
@Composable
fun SkySettingsSection(
    state: SettingsUiState,
    onRadiusChanged: (Double) -> Unit,
    onMinElevationChanged: (Double) -> Unit,
    onMinAltitudeChanged: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SettingSlider(
            label = stringResource(R.string.settings_radius),
            help = stringResource(R.string.settings_radius_help),
            value = settings.detectionRadiusMeters,
            range = SkySettings.RADIUS_RANGE,
            atDefault = state.isRadiusAtDefault,
            valueText = stringResource(
                UnitLabels.distance(settings.distanceUnit),
                FlightFormatting.distance(settings.detectionRadiusMeters, settings.distanceUnit),
            ),
            onValueSettled = onRadiusChanged,
        )

        // A única forma de o utilizador descobrir que as duas definições interagem: sem isto, põe o
        // raio no máximo, não vê aeronave nova nenhuma, e conclui que a app está estragada (FR-014).
        if (state.radiusExceedsUsefulRange) {
            Text(
                text = stringResource(
                    R.string.settings_range_warning,
                    stringResource(
                        UnitLabels.distance(settings.distanceUnit),
                        FlightFormatting.distance(state.usefulRangeMeters, settings.distanceUnit),
                    ),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        SettingSlider(
            label = stringResource(R.string.settings_min_elevation),
            help = stringResource(R.string.settings_min_elevation_help),
            value = settings.minElevationDegrees,
            range = SkySettings.MIN_ELEVATION_RANGE,
            atDefault = state.isMinElevationAtDefault,
            valueText = stringResource(
                R.string.settings_degrees,
                FlightFormatting.elevationDegrees(settings.minElevationDegrees),
            ),
            onValueSettled = onMinElevationChanged,
        )

        SettingSlider(
            label = stringResource(R.string.settings_min_altitude),
            help = stringResource(R.string.settings_min_altitude_help),
            value = settings.minAltitudeMeters,
            range = SkySettings.MIN_ALTITUDE_RANGE,
            atDefault = state.isMinAltitudeAtDefault,
            valueText = stringResource(
                UnitLabels.altitude(settings.altitudeUnit),
                FlightFormatting.altitude(settings.minAltitudeMeters, settings.altitudeUnit),
            ),
            onValueSettled = onMinAltitudeChanged,
        )
    }
}

/**
 * Um cursor, o valor que ele mostra, e a explicação do que muda.
 *
 * O intervalo vem sempre de `SkySettings`, que é o único ponto de verdade sobre os limites (AD-022).
 * E **não** se estreita em função de outro controlo: um limite que se mexe debaixo do dedo é hostil,
 * e tira sentido a dizer que um valor está "de origem" (AD-021).
 */
@Composable
private fun SettingSlider(
    label: String,
    help: String,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    atDefault: Boolean,
    valueText: String,
    onValueSettled: (Double) -> Unit,
) {
    // O valor em trânsito vive aqui, no controlo, e só sai daqui quando o dedo levanta.
    //
    // O `remember(value)` reinicia o cursor quando o valor gravado muda, e é isso que faz "repor
    // valores de origem" e a correção por `coerced()` aparecerem no cursor sem código extra. O preço:
    // se o valor gravado mudar **enquanto** o dedo ainda está no cursor, ele salta. Na prática exige
    // multitoque — arrastar aqui e tocar em "repor" ao mesmo tempo — e o resultado é o valor correto,
    // só com um salto visível. Anotado em vez de resolvido: guardar o valor em trânsito através de uma
    // reposição pedida pelo utilizador dava um cursor a discordar do que está gravado, que é pior.
    var inFlight by remember(value) { mutableFloatStateOf(value.toFloat()) }

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(text = valueText, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            text = help,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = inFlight,
            onValueChange = { inFlight = it },
            onValueChangeFinished = { onValueSettled(inFlight.toDouble()) },
            valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
        )
        if (atDefault) {
            Text(
                text = stringResource(R.string.settings_at_default),
                style = MaterialTheme.typography.labelSmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Os seletores de unidade. Sem eles, toda a canalização das unidades ficaria sem torneira. */
@Composable
fun UnitsSection(
    state: SettingsUiState,
    onDistanceUnitChanged: (DistanceUnit) -> Unit,
    onAltitudeUnitChanged: (AltitudeUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UnitRow(
            label = stringResource(R.string.settings_distance_unit),
            options = DistanceUnit.entries,
            selected = state.settings.distanceUnit,
            labelOf = { unit ->
                when (unit) {
                    DistanceUnit.KILOMETERS -> stringResource(R.string.settings_unit_kilometers)
                    DistanceUnit.MILES -> stringResource(R.string.settings_unit_miles)
                }
            },
            onSelected = onDistanceUnitChanged,
        )
        UnitRow(
            label = stringResource(R.string.settings_altitude_unit),
            options = AltitudeUnit.entries,
            selected = state.settings.altitudeUnit,
            labelOf = { unit ->
                when (unit) {
                    AltitudeUnit.METERS -> stringResource(R.string.settings_unit_meters)
                    AltitudeUnit.FEET -> stringResource(R.string.settings_unit_feet)
                }
            },
            onSelected = onAltitudeUnitChanged,
        )
    }
}

@Composable
private fun <T> UnitRow(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: @Composable (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    label = { Text(labelOf(option)) },
                )
            }
        }
    }
}

/**
 * A cadência do widget, com o preço ao lado.
 *
 * O preço não é decoração: o widget e o ecrã bebem do **mesmo** orçamento diário, e sem isto o
 * utilizador escolhe "de 15 em 15 minutos" e descobre semanas depois que a app deixa de atualizar
 * mais cedo à tarde, sem relação aparente com a escolha que fez (FR-027).
 */
@Composable
fun WidgetScheduleSection(
    state: SettingsUiState,
    onRefreshIntervalChanged: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val minutes = state.settings.refreshIntervalMinutes

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SettingSlider(
            label = stringResource(R.string.settings_refresh_interval),
            help = stringResource(R.string.settings_refresh_interval_help),
            value = minutes.toDouble(),
            range = SkySettings.REFRESH_INTERVAL_RANGE.first.toDouble()..SkySettings.REFRESH_INTERVAL_RANGE.last.toDouble(),
            atDefault = minutes == SkySettings.MIN_REFRESH_INTERVAL_MINUTES,
            valueText = stringResource(R.string.settings_minutes, minutes.toInt()),
            onValueSettled = { onRefreshIntervalChanged(it.roundToLong()) },
        )

        Text(
            text = stringResource(
                R.string.settings_budget_cost,
                state.widgetQueriesPerDay,
                (state.widgetBudgetShare * 100).roundToInt(),
                formatDuration(state.remainingScreenSeconds),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun formatDuration(seconds: Long): String =
    stringResource(R.string.settings_hours_minutes, seconds / 3600, (seconds % 3600) / 60)

/**
 * As notificações de passagem, e o número que impede o utilizador de se sentir enganado.
 *
 * A taxa de captura aparece **antes** de a opção ser ligada, e não como letra pequena depois. A razão
 * é concreta: esta feature avisa de uma pequena fração das passagens, por limites da plataforma que
 * nenhum desenho contorna. Quem ligue sem saber isso recebe dois avisos por semana e conclui que a app
 * está avariada — e conclui bem, porque ninguém lhe disse o que esperar.
 */
@Composable
fun NotificationsSection(
    state: SettingsUiState,
    onEnabledChanged: (Boolean) -> Unit,
    onThresholdChanged: (Double) -> Unit,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_notifications_enabled),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.settings_notifications_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // `notificationsActive` e **não** `settings.notificationsEnabled`: o interruptor mostra o
            // que a app consegue fazer, não o que o utilizador quis. Ligado à intenção, ele continuava
            // visualmente ligado depois de o utilizador revogar a permissão no Android — a mentir
            // sobre o estado, que é precisamente o que a FR-014 proíbe.
            //
            // A intenção guardada não se perde por isto (AD-032): voltar a conceder no sistema repõe o
            // funcionamento sem ser preciso tocar aqui outra vez.
            Switch(checked = state.notificationsActive, onCheckedChange = onEnabledChanged)
        }

        // A expectativa, sempre visível — ligada ou desligada a opção.
        Text(
            text = stringResource(
                R.string.settings_capture_rate,
                (state.expectedCaptureRate * 100).roundToInt(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(
                R.string.settings_capture_rate_why,
                state.settings.refreshIntervalMinutes.toInt(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Duas faltas com remédios diferentes, e ambas levam às definições do sistema — é a única via
        // que o Android oferece para a de segundo plano a partir da API 30.
        if (state.settings.notificationsEnabled && !state.hasNotificationPermission) {
            PermissionWarning(R.string.settings_notifications_permission_missing, onOpenSystemSettings)
        }
        if (state.settings.notificationsEnabled && !state.hasBackgroundLocationPermission) {
            // Usa o rationale que já existia e nunca era lido: explicar **porquê** antes de mandar o
            // utilizador às definições do sistema é o que a constituição exige, e mandá-lo lá sem
            // razão nenhuma seria pior do que não pedir de todo.
            PermissionWarning(R.string.permission_background_location_rationale, onOpenSystemSettings)
        }

        if (state.settings.notificationsEnabled) {
            SettingSlider(
                label = stringResource(R.string.settings_notification_threshold),
                help = stringResource(R.string.settings_notification_threshold_help),
                value = state.settings.notificationThresholdDegrees,
                // O piso é o ângulo mínimo de deteção: abaixo dele a faixa é inatingível, porque
                // essas aeronaves nem sequer chegam a ser detetadas (AD-033).
                range = state.settings.minElevationDegrees..90.0,
                atDefault = state.settings.notificationThresholdDegrees ==
                    NotificationPolicy.DEFAULT_THRESHOLD_DEGREES,
                valueText = stringResource(
                    R.string.settings_degrees,
                    FlightFormatting.elevationDegrees(state.settings.notificationThresholdDegrees),
                ),
                onValueSettled = onThresholdChanged,
            )
        }
    }
}

@Composable
private fun PermissionWarning(@StringRes message: Int, onOpen: () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = stringResource(message),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onOpen, contentPadding = PaddingValues(0.dp)) {
            Text(stringResource(R.string.settings_open_system_settings))
        }
    }
}
