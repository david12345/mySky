package com.mysky.app.presentation.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.shouldShowRationale
import com.mysky.app.R

/**
 * Fluxo de permissão de localização.
 *
 * Pede **apenas localização aproximada** (FR-003): a 30 km de raio, algumas centenas de metros não
 * mudam o ângulo de elevação de forma visível, e pedir precisão a mais é pedir ao utilizador que
 * confie mais do que o necessário. A permissão de localização **em segundo plano** não aparece
 * aqui nem por engano: pertence à feature de notificações, e só a partir das definições
 * (princípio III). O gate de T051 verifica isso com um grep, por isso nem o nome dela se escreve.
 */
@Stable
@OptIn(ExperimentalPermissionsApi::class)
class LocationPermissionController internal constructor(
    private val state: MultiplePermissionsState,
    private val requestedState: MutableState<Boolean>,
) {
    /** Aproximada chega: basta uma das duas ter sido concedida. */
    val isGranted: Boolean get() = state.permissions.any { it.status.isGranted }

    /** `false` enquanto o diálogo do sistema nunca foi mostrado: aí ainda é hora do rationale. */
    val requested: Boolean get() = requestedState.value

    /**
     * Antes do primeiro pedido o sistema mostra sempre o diálogo. Depois, `shouldShowRationale`
     * distingue uma recusa de uma recusa permanente — é o único sinal que o Android dá, e sem ele
     * a app não saberia se deve voltar a pedir ou encaminhar para as definições.
     */
    val canAskAgain: Boolean get() = !requested || state.shouldShowRationale

    fun request() {
        requestedState.value = true
        state.launchMultiplePermissionRequest()
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberLocationPermissionController(): LocationPermissionController {
    // Fine vai no pedido porque o sistema mostra as duas opções no mesmo diálogo; a app funciona
    // com a aproximada e é isso que `LocationRepository` verifica.
    val state = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ),
    )
    val requested = rememberSaveable { mutableStateOf(false) }
    return remember(state, requested) { LocationPermissionController(state, requested) }
}

/**
 * Explicação mostrada **antes** de qualquer diálogo do sistema (FR-001, princípio III).
 *
 * O utilizador tem de perceber a troca antes de a fazer: um diálogo do sistema sem contexto é uma
 * pergunta a que só se pode responder com desconfiança.
 */
@Composable
fun LocationRationale(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PermissionMessage(
        title = stringResource(R.string.sky_permission_denied_title),
        body = stringResource(R.string.permission_location_rationale),
        actionLabel = stringResource(R.string.sky_permission_grant),
        onAction = onRequestPermission,
        modifier = modifier,
    )
}

/**
 * Recusa permanente: o sistema já não mostra o diálogo, por isso a ação leva às definições da app
 * (FR-005). Sem isto o utilizador ficaria num beco sem saída — a app a pedir uma permissão que
 * nenhum botão dentro dela consegue conceder.
 */
@Composable
fun LocationPermanentlyDenied(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    PermissionMessage(
        title = stringResource(R.string.sky_permission_denied_title),
        body = stringResource(R.string.sky_permission_permanently_denied_body),
        actionLabel = stringResource(R.string.sky_permission_open_settings),
        onAction = { context.openAppSettings() },
        modifier = modifier,
    )
}

@Composable
private fun PermissionMessage(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(text = body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Button(onClick = onAction) { Text(actionLabel) }
    }
}

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
