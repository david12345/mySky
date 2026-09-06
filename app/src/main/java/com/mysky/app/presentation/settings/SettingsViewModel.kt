package com.mysky.app.presentation.settings

import androidx.lifecycle.ViewModel
import com.mysky.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * TODO(feature/settings): expor [SettingsRepository.settings] como estado e escrever alterações,
 *  reagendando o worker periódico sempre que a frequência mudar.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel()
