package com.mysky.app.presentation.format

import androidx.annotation.StringRes
import com.mysky.app.R
import com.mysky.app.domain.model.AltitudeUnit
import com.mysky.app.domain.model.DistanceUnit

/**
 * Qual o recurso de texto que corresponde a cada unidade.
 *
 * Um sítio só a fazer esta escolha, partilhado pelos três ecrãs onde as mesmas grandezas aparecem.
 * Espalhá-la por cada ponto de apresentação seria a forma mais fácil de acabar com quilómetros ao
 * lado de pés — a mistura que o SC-006 proíbe.
 *
 * A velocidade segue a unidade de **distância**: quem lê milhas espera milhas por hora, e não existe
 * preferência própria para ela.
 */
object UnitLabels {

    @StringRes
    fun distance(unit: DistanceUnit): Int = when (unit) {
        DistanceUnit.KILOMETERS -> R.string.flight_distance_km
        DistanceUnit.MILES -> R.string.flight_distance_mi
    }

    @StringRes
    fun altitude(unit: AltitudeUnit): Int = when (unit) {
        AltitudeUnit.METERS -> R.string.flight_altitude_meters
        AltitudeUnit.FEET -> R.string.flight_altitude_feet
    }

    @StringRes
    fun speed(unit: DistanceUnit): Int = when (unit) {
        DistanceUnit.KILOMETERS -> R.string.flight_speed_kmh
        DistanceUnit.MILES -> R.string.flight_speed_mph
    }

    /** A razão de subida segue a **altitude**: é a mesma grandeza, por unidade de tempo. */
    @StringRes
    fun verticalRate(unit: AltitudeUnit): Int = when (unit) {
        AltitudeUnit.METERS -> R.string.flight_vertical_rate_ms
        AltitudeUnit.FEET -> R.string.flight_vertical_rate_ftmin
    }
}
