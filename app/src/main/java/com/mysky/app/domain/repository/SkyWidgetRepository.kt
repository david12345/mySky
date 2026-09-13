package com.mysky.app.domain.repository

import com.mysky.app.domain.model.SkyWidgetSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * O último resultado do trabalho de fundo, para quem o quiser mostrar.
 *
 * **Substitui o estado do Glance como sítio deste valor, corrigindo a AD-003** (ver AD-023). O estado
 * do Glance é por instância de widget — `updateAppWidgetState` exige um `GlanceId` — o que obrigaria a
 * escrever uma cópia idêntica por cada widget no ecrã, e deixaria as notificações sem forma de ler o
 * que precisam, por não serem widget e não terem `GlanceId` nenhum.
 *
 * `null` significa **nunca correu**, e é distinto de um snapshot de céu vazio. É a fronteira entre
 * informar e inventar.
 */
interface SkyWidgetRepository {

    /** Emite `null` enquanto nunca tiver corrido um ciclo. Nunca lança. */
    val snapshot: Flow<SkyWidgetSnapshot?>

    /**
     * Substitui o último resultado. Nunca lança.
     *
     * Quem falhou um ciclo **não chama isto** — e é essa ausência de chamada, e não uma regra a
     * lembrar, que faz os dados anteriores sobreviverem a uma falha (FR-014).
     */
    suspend fun save(snapshot: SkyWidgetSnapshot)
}
