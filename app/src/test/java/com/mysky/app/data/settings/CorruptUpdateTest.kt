package com.mysky.app.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CorruptUpdateTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `update apos corrupcao lanca`() = runTest {
        val file = File(folder.root, "corrompido.preferences_pb")
        file.writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05))
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            produceFile = { file },
        )
        val repo = SettingsRepositoryImpl(store)
        assertThrows(Throwable::class.java) {
            kotlinx.coroutines.runBlocking { repo.update { it } }
        }
    }
}
