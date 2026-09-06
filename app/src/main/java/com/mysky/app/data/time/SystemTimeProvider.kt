package com.mysky.app.data.time

import com.mysky.app.domain.time.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton

/** O relógio real, do lado de fora do domínio. Substituído por um valor fixo nos testes. */
@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun nowEpochSeconds(): Long = System.currentTimeMillis() / MILLIS_PER_SECOND

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
