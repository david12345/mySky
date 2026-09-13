package com.mysky.app.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavHostController
import com.mysky.app.presentation.navigation.MySkyNavHost
import com.mysky.app.presentation.theme.MySkyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Guardado para o [onNewIntent] lhe poder entregar deep links.
     *
     * Não é elegante ter uma referência ao controlador aqui, mas a alternativa é pior: sem isto, uma
     * notificação tocada com a app **já viva** não abre o detalhe.
     */
    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MySkyTheme {
                MySkyNavHost(onControllerReady = { navController = it })
            }
        }
    }

    /**
     * O deep link de uma notificação, quando a Activity já existe.
     *
     * **É o caso comum, não o raro.** Com `launchMode="singleTask"`, o Android entrega o `Intent` novo
     * aqui em vez de recriar a Activity — e o Navigation só consome deep links sozinho no `Intent` de
     * arranque, na primeira composição. Sem este método, tocar num aviso com a app em segundo plano
     * abria o ecrã que já lá estava, e não a aeronave avisada.
     *
     * E é mesmo o cenário mais provável: o aviso nasce de um ciclo que a app acabou de correr, por isso
     * o processo está quase sempre vivo quando o utilizador lhe toca.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navController?.handleDeepLink(intent)
    }
}
