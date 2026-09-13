package com.mysky.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mysky.app.R
import com.mysky.app.domain.model.OverheadFlight
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * O aviso de "há um avião por cima de ti".
 *
 * Duas regras que atravessam a classe:
 *
 * - **Nunca lança.** Publicar é o último passo de um ciclo que já gravou tudo o que interessa; deixar
 *   uma falha aqui rebentar transformaria um aviso perdido num ciclo falhado, com `retry` e consulta a
 *   mais. É o "melhor esforço" que a AD-004 já assumiu para esta feature.
 * - **Diz sempre quando observou.** O trabalho de fundo pode ter sido adiado horas pelo Doze, e o
 *   aviso chegar muito depois da passagem. "Está por cima de ti" seria falso; é a mesma honestidade
 *   temporal que a 005 impôs ao widget.
 */
@Singleton
class OverheadNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            // `DEFAULT` e não `HIGH`: isto é informação agradável, não urgência. Um avião a passar não
            // justifica um aviso que se sobrepõe ao que o utilizador está a fazer.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /**
     * @param observedAtEpochSeconds quando a aeronave foi vista, não quando isto é chamado.
     */
    fun notifyOverhead(flight: OverheadFlight, observedAtEpochSeconds: Long) {
        try {
            ensureChannel()

            val title = flight.aircraft.callsign
                ?: context.getString(R.string.notification_no_callsign)
            val body = context.getString(
                R.string.notification_body,
                flight.elevationDegrees.toInt(),
                flight.airline?.name ?: context.getString(R.string.notification_unknown_airline),
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(detailIntent(flight.aircraft.icao24))
                .setAutoCancel(true)
                // O instante da observação, que o sistema mostra ao lado do aviso. É isto que impede
                // um aviso adiado pelo Doze de parecer que descreve o presente.
                .setShowWhen(true)
                .setWhen(observedAtEpochSeconds * 1_000)
                .build()

            NotificationManagerCompat.from(context).notify(flight.aircraft.icao24.hashCode(), notification)
        } catch (security: SecurityException) {
            // A permissão pode ter sido revogada entre a verificação e a publicação.
        } catch (throwable: Throwable) {
            // Ver a KDoc da classe: um aviso perdido nunca pode custar o ciclo.
        }
    }

    /** Abre o detalhe **daquela** aeronave, e não a lista (FR-007). */
    private fun detailIntent(icao24: String): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, "$DEEP_LINK_PREFIX$icao24".toUri())
            .setPackage(context.packageName)

        return PendingIntent.getActivity(
            context,
            icao24.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun String.toUri(): Uri = Uri.parse(this)

    companion object {
        const val CHANNEL_ID = "overhead_flights"
        const val DEEP_LINK_PREFIX = "mysky://flight/"
    }
}
