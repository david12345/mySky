package com.mysky.app.presentation.settings

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
