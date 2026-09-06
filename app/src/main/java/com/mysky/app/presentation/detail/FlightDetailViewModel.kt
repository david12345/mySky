package com.mysky.app.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.mysky.app.domain.repository.SightingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * TODO(feature/flight-detail): expor o avistamento atual + trajeto recente para o `icao24` recebido
 *  em [savedStateHandle].
 */
@HiltViewModel
class FlightDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val sightingRepository: SightingRepository,
) : ViewModel() {

    val icao24: String? get() = savedStateHandle[ARG_ICAO24]

    companion object {
        const val ARG_ICAO24 = "icao24"
    }
}
