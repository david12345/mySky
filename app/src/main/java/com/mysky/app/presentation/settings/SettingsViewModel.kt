package com.mysky.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysky.app.domain.model.AltitudeUnit
import com.mysky.app.domain.model.DistanceUnit
import com.mysky.app.domain.model.RouteUpdateState
import com.mysky.app.domain.model.SkySettings
import com.mysky.app.domain.repository.RouteTableRepository
import com.mysky.app.domain.repository.SettingsRepository
import com.mysky.app.domain.repository.LocationRepository
import com.mysky.app.domain.repository.NotificationPermission
import com.mysky.app.presentation.sky.SkySession
import com.mysky.app.worker.SkyBackgroundWorkCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel das definições.
 *
 * **Não conhece o WorkManager** (AD-017): a atualização da tabela de rotas chega-lhe já traduzida em
 * estado de domínio.
 *
 * As escritas de critério acordam o laço partilhado; as de unidade **não** (AD-019). A diferença não
 * é arbitrária: mudar de quilómetros para milhas não altera que aeronaves estão no céu, e pedir
 * dados por causa disso gastaria um crédito do orçamento para obter exatamente a mesma lista.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val routeTableRepository: RouteTableRepository,
    private val settingsRepository: SettingsRepository,
    private val skySession: SkySession,
    private val backgroundWorkCoordinator: SkyBackgroundWorkCoordinator,
    private val notificationPermission: NotificationPermission,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    private val tableInfo = MutableStateFlow(SettingsUiState())

    /**
     * As permissões do sistema, relidas a pedido.
     *
     * Não são um fluxo porque o Android não emite nada quando o utilizador as muda nas definições do
     * sistema. A app só pode voltar a perguntar — e o momento certo é quando o ecrã volta a ficar
     * visível, que é o mesmo padrão que o `MainViewModel` já usa para a localização.
     */
    private val systemPermissions = MutableStateFlow(readSystemPermissions())

    val uiState: StateFlow<SettingsUiState> = combine(
        tableInfo,
        routeTableRepository.updateState,
        settingsRepository.settings,
        systemPermissions,
    ) { base, update, settings, permissions ->
        base.copy(
            updateState = update,
            settings = settings,
            hasNotificationPermission = permissions.first,
            hasBackgroundLocationPermission = permissions.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

    init {
        refreshTableInfo()
    }

    /** Chamado quando o ecrã volta a ficar visível: é a única forma de detetar uma revogação. */
    fun onScreenVisible() {
        systemPermissions.value = readSystemPermissions()
    }

    private fun readSystemPermissions(): Pair<Boolean, Boolean> =
        notificationPermission.isGranted() to locationRepository.hasBackgroundLocationPermission()

    // --- Notificações (006) -----------------------------------------------------------------------

    /**
     * Liga ou desliga a intenção do utilizador.
     *
     * Chama o `reconcile()` **sempre**, e é isso que faz a feature existir para quem não tem widget:
     * a condição de agendamento é "há widget **ou** notificações ligadas" desde a 005, mas sem esta
     * chamada ninguém a reavalia depois do toque — e a app ficaria em silêncio até ao arranque
     * seguinte, que pode ser dias depois.
     */
    fun onNotificationsEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(notificationsEnabled = enabled) }
            backgroundWorkCoordinator.reconcile()
        }
    }

    fun onNotificationThresholdChanged(degrees: Double) = updatePresentation {
        // Não acorda o laço do ecrã nem reagenda: mudar o limiar não altera que aeronaves são
        // detetadas, só quais delas merecem interromper o utilizador.
        it.copy(notificationThresholdDegrees = degrees)
    }

    // --- Critérios: gravam e acordam o laço ------------------------------------------------------

    fun onRadiusChanged(meters: Double) = updateCriteria { it.copy(detectionRadiusMeters = meters) }

    fun onMinElevationChanged(degrees: Double) = updateCriteria { it.copy(minElevationDegrees = degrees) }

    fun onMinAltitudeChanged(meters: Double) = updateCriteria { it.copy(minAltitudeMeters = meters) }

    /**
     * Repõe **só** o que é ajustável neste ecrã; a tabela de rotas fica onde está (AD-017).
     *
     * Faz as duas coisas — acorda a sessão **e** reconcilia o trabalho de fundo — porque desde a 005
     * o repor também devolve a cadência ao valor de origem. Chamar só `updateCriteria` deixaria o
     * trabalho periódico agendado com a cadência antiga, e o ecrã a mostrar a nova: os dois a
     * discordar, sem erro nenhum.
     */
    fun onResetToDefaults() {
        viewModelScope.launch {
            settingsRepository.update { it.withDefaults() }
            skySession.requestRefresh()
            backgroundWorkCoordinator.reconcile()
        }
    }

    // --- Unidades: gravam e não acordam nada ----------------------------------------------------

    fun onDistanceUnitChanged(unit: DistanceUnit) = updatePresentation { it.copy(distanceUnit = unit) }

    fun onAltitudeUnitChanged(unit: AltitudeUnit) = updatePresentation { it.copy(altitudeUnit = unit) }

    /**
     * Uma alteração de critério pede dados novos.
     *
     * A sessão lê os critérios no início de cada ciclo (AD-018), por isso a alteração apareceria
     * sozinha dentro de 30 segundos — o que já cumpriria o critério de sucesso. Acordar o laço é
     * cortesia sobre essa garantia, e o canal é conflado: um utilizador a arrastar um controlo não
     * gasta mais do que um pedido.
     */
    private fun updateCriteria(transform: (SkySettings) -> SkySettings) {
        viewModelScope.launch {
            settingsRepository.update(transform)
            skySession.requestRefresh()
        }
    }

    private fun updatePresentation(transform: (SkySettings) -> SkySettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    // --- Cadência: grava e reagenda o trabalho de fundo -------------------------------------------

    fun onRefreshIntervalChanged(minutes: Long) = updateSchedule { it.copy(refreshIntervalMinutes = minutes) }

    /**
     * A terceira categoria de escrita deste ecrã, ao lado de "acorda a sessão" e "não acorda nada".
     *
     * Passa pelo `reconcile()` e **não** chama o agendador diretamente (AD-027): mudar a cadência
     * também tem de respeitar a pergunta "isto devia sequer existir?". Chamar o agendador daqui criaria
     * um segundo sítio a decidir isso, e um deles acabaria por discordar do outro.
     *
     * Não acorda a sessão: a cadência é do trabalho de fundo, não do laço de 30 s do ecrã (a armadilha
     * que a AD-019 registou). Pedir dados por causa dela gastaria uma consulta para obter a mesma lista.
     */
    private fun updateSchedule(transform: (SkySettings) -> SkySettings) {
        viewModelScope.launch {
            settingsRepository.update(transform)
            backgroundWorkCoordinator.reconcile()
        }
    }

    // --- Tabela de rotas (003), inalterado -------------------------------------------------------

    fun onUpdateRouteTable() {
        routeTableRepository.requestUpdate()
    }

    fun onUpdateFinished(state: RouteUpdateState) {
        if (state is RouteUpdateState.Success) refreshTableInfo()
    }

    private fun refreshTableInfo() {
        viewModelScope.launch {
            val info = routeTableRepository.tableInfo()
            tableInfo.update {
                it.copy(
                    routeTableGeneratedAtEpochSeconds = info?.generatedAtEpochSeconds,
                    routeCount = info?.routeCount ?: 0,
                )
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
