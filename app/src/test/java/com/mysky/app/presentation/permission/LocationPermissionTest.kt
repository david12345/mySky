package com.mysky.app.presentation.permission

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A decisão "ainda posso voltar a pedir?" é a única lógica do fluxo de permissões que consegue
 * prender o utilizador: classificar mal uma recusa manda-o para um ecrã cuja única saída são as
 * definições do sistema. Por isso vive numa função pura, e é aqui que se fixa a tabela de verdade.
 */
class LocationPermissionTest {

    @Test
    fun `permissao concedida nunca aparece como recusa permanente`() {
        // O sistema deixa de mostrar rationale depois de conceder: sem o `granted ||`, uma
        // permissão ativa seria lida como recusada para sempre.
        assertEquals(
            PermissionOutcome(granted = true, canAskAgain = true),
            permissionOutcomeOf(granted = true, shouldShowRationale = false),
        )
    }

    @Test
    fun `permissao concedida com rationale pendente continua concedida`() {
        assertEquals(
            PermissionOutcome(granted = true, canAskAgain = true),
            permissionOutcomeOf(granted = true, shouldShowRationale = true),
        )
    }

    @Test
    fun `primeira recusa deixa espaco para pedir outra vez`() {
        assertEquals(
            PermissionOutcome(granted = false, canAskAgain = true),
            permissionOutcomeOf(granted = false, shouldShowRationale = true),
        )
    }

    @Test
    fun `recusa sem rationale e recusa permanente`() {
        // O sistema já não mostra o diálogo; a UI tem de oferecer as definições (FR-005).
        assertEquals(
            PermissionOutcome(granted = false, canAskAgain = false),
            permissionOutcomeOf(granted = false, shouldShowRationale = false),
        )
    }
}
