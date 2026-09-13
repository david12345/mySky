package com.mysky.app.data.repository

import com.mysky.app.aircraft
import com.mysky.app.data.local.SightingDao
import com.mysky.app.data.local.entity.SightingEntity
import com.mysky.app.domain.model.NotificationPolicy
import com.mysky.app.domain.time.TimeProvider
import com.mysky.app.overheadFlight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A deduplicação, com o relógio fixo.
 *
 * Só é testável assim depois de o `TimeProvider` ter sido injetado — antes a classe lia
 * `System.currentTimeMillis()` diretamente e não havia forma de fixar o instante. A revisão apanhou
 * isso como violação de convenção; o efeito prático é este teste poder existir.
 */
class SightingRepositoryImplTest {

    private val agora = 1_700_000_000L

    /** DAO em memória: o SQL real é do Room e não se testa na JVM, mas a política é desta classe. */
    private class FakeDao : SightingDao {
        val rows = mutableListOf<SightingEntity>()
        var falharInsert = false

        override fun observeRecent(limit: Int): Flow<List<SightingEntity>> = flowOf(rows)
        override fun observeTrackFor(icao24: String): Flow<List<SightingEntity>> = flowOf(rows)

        override suspend fun countNotifiedSince(icao24: String, sinceEpochSeconds: Long): Int =
            rows.count { it.icao24 == icao24 && it.notified && it.observedAtEpochSeconds >= sinceEpochSeconds }

        override suspend fun insert(sighting: SightingEntity): Long {
            if (falharInsert) throw IllegalStateException("esquema partido")
            rows += sighting
            return rows.size.toLong()
        }

        override suspend fun deleteOlderThan(beforeEpochSeconds: Long): Int {
            val antes = rows.size
            rows.removeAll { it.observedAtEpochSeconds < beforeEpochSeconds }
            return antes - rows.size
        }
    }

    private val dao = FakeDao()
    private val repository = SightingRepositoryImpl(dao, TimeProvider { agora })

    private val voo = overheadFlight(aircraft = aircraft(icao24 = "abc123"), elevationDegrees = 70.0)

    @Test
    fun `uma aeronave nunca avisada nao conta como avisada`() = runTest {
        assertFalse(repository.wasNotifiedRecently("abc123", NotificationPolicy.DEDUPLICATION_WINDOW_SECONDS))
    }

    @Test
    fun `uma aeronave avisada agora conta como avisada`() = runTest {
        repository.record(voo, agora, notified = true)

        assertTrue(repository.wasNotifiedRecently("abc123", NotificationPolicy.DEDUPLICATION_WINDOW_SECONDS))
    }

    @Test
    fun `passada a janela deixa de contar e a aeronave pode ser avisada outra vez`() = runTest {
        // A mesma aeronave numa passagem posterior, horas depois, **merece** aviso. Uma janela sem fim
        // silenciaria para sempre os voos regulares — precisamente os que passam todos os dias por
        // cima de quem instalou a app.
        repository.record(voo, agora - NotificationPolicy.DEDUPLICATION_WINDOW_SECONDS - 1, notified = true)

        assertFalse(repository.wasNotifiedRecently("abc123", NotificationPolicy.DEDUPLICATION_WINDOW_SECONDS))
    }

    @Test
    fun `um avistamento nao avisado nunca suprime um aviso`() = runTest {
        // É a razão de `notified` ser um parâmetro explícito: quando a feature do histórico começar a
        // gravar avistamentos, eles não podem passar a silenciar avisos por engano.
        repository.record(voo, agora, notified = false)

        assertFalse(repository.wasNotifiedRecently("abc123", NotificationPolicy.DEDUPLICATION_WINDOW_SECONDS))
    }

    @Test
    fun `gravar limpa o que ja passou a retencao`() = runTest {
        repository.record(voo, agora - NotificationPolicy.RETENTION_WINDOW_SECONDS - 100, notified = true)
        assertEquals(1, dao.rows.size)

        repository.record(voo, agora, notified = true)

        assertEquals("o registo antigo devia ter sido limpo", 1, dao.rows.size)
        assertEquals(agora, dao.rows.single().observedAtEpochSeconds)
    }

    @Test
    fun `uma falha a gravar nao sobe para quem chamou`() = runTest {
        // O worker acabou de publicar um aviso; rebentar aqui perderia o ciclo inteiro por causa de
        // um registo que só serve para não repetir.
        dao.falharInsert = true

        repository.record(voo, agora, notified = true)
    }

    @Test
    fun `na duvida assume que ja foi avisada`() = runTest {
        // A assimetria deliberada: não avisar custa um aviso; avisar por engano interrompe alguém com
        // base num estado que não se conseguiu ler.
        val partido = object : SightingDao by dao {
            override suspend fun countNotifiedSince(icao24: String, sinceEpochSeconds: Long): Int =
                throw IllegalStateException("esquema partido")
        }

        assertTrue(
            SightingRepositoryImpl(partido, TimeProvider { agora })
                .wasNotifiedRecently("abc123", NotificationPolicy.DEDUPLICATION_WINDOW_SECONDS),
        )
    }
}
