package com.example.caderneta.e2e.processdeath

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.example.caderneta.MainActivity
import com.example.caderneta.R
import com.example.caderneta.fixtures.DatabaseFixture
import com.example.caderneta.fixtures.TestStateReset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 2 do cenário de morte de processo real: assume que o processo anterior
 * já foi morto de verdade via `am force-stop` (orquestrado por
 * `scripts/audit/process_death_check.sh`, fora do Gradle/Orchestrator) e que
 * este `am instrument` é um cold start novo — Application/AppContainer/Room
 * recriados do zero. Não chama `TestStateReset.reset()`: precisa dos dados
 * gravados em disco pela fase anterior ([ProcessDeathSeedE2ETest]).
 */
class ProcessDeathVerifyE2ETest {
    @Test
    fun verificaEstadoAposMorteRealDeProcesso() {
        val app = TestStateReset.app()
        val container = app.container

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
            onView(withText("Audit Local Norte")).perform(click())
            onView(withText("Cliente Auditoria")).check(matches(isDisplayed()))
        }

        runBlocking {
            val cliente =
                requireNotNull(
                    container.database
                        .backupDao()
                        .getAllClientes()
                        .singleOrNull { candidato -> candidato.nome == "Cliente Auditoria" },
                ) { "Cliente semeado pela fase anterior nao foi encontrado" }

            assertEquals(SALDO_ESPERADO_CENTAVOS, container.contaRepository.getSaldoCentavos(cliente.id))
            assertEquals(
                PRECO_SALGADO_VISTA_ESPERADO_CENTAVOS,
                container.configuracoesRepository.getConfiguracoesOnce()?.precoSalgadoVistaCentavos,
            )

            val fotoNome = cliente.fotoNome
            assertNotNull("Foto semeada pela fase anterior deveria ter sobrevivido", fotoNome)
            val fotoArquivo = container.clientePhotoRepository.arquivo(fotoNome)
            assertNotNull(fotoArquivo)
            assertTrue(fotoArquivo?.isFile == true)
            assertTrue((fotoArquivo?.length() ?: 0L) > 0L)
        }

        DatabaseFixture.assertForeignKeysOk(container)
    }

    private companion object {
        const val SALDO_ESPERADO_CENTAVOS = 1200L
        const val PRECO_SALGADO_VISTA_ESPERADO_CENTAVOS = 500L
    }
}
