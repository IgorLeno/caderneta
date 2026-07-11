package com.example.caderneta.e2e.processdeath

import androidx.test.core.app.ApplicationProvider
import com.example.caderneta.data.entity.TipoTransacao
import com.example.caderneta.fixtures.DatabaseFixture
import com.example.caderneta.fixtures.TestStateReset
import com.example.caderneta.helpers.WaitConditions
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Fase 1 de um cenário de dois processos: semeia config, cliente, venda e foto
 * pelos repositórios diretamente e termina normalmente.
 *
 * Não estende [com.example.caderneta.e2e.BaseE2ETest] de propósito: essa classe roda via
 * `adb shell am instrument` cru (fora do Gradle/Orchestrator), orquestrada por
 * `scripts/audit/process_death_check.sh`, que mata o processo com `am force-stop`
 * logo depois desta instrumentação terminar. `BaseE2ETest` depende de
 * `TestStorage` (APK test-services), que só é instalado automaticamente pelo
 * Gradle/GMD — fora desse fluxo essa dependência não está disponível.
 */
class ProcessDeathSeedE2ETest {
    @Test
    fun seedEstadoParaMorteDeProcesso() {
        TestStateReset.reset()
        val app = TestStateReset.app()
        val container = app.container

        DatabaseFixture.seedConfiguracoes(container)
        val seeded = DatabaseFixture.seedClienteBasico(container)

        runBlocking {
            container.financeiroService.registrarVenda(
                clienteId = seeded.clienteId,
                localId = seeded.localId,
                tipoTransacao = TipoTransacao.A_PRAZO,
                isPromocao = false,
                quantidadeSalgados = 2,
                quantidadeSucos = 0,
                valorCentavos = SALDO_ESPERADO_CENTAVOS,
                promocaoDetalhes = null,
            )

            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val fotoUri = container.clientePhotoSource.captureImage(context)
            requireNotNull(fotoUri) { "Fonte de foto de auditoria deveria retornar uma URI" }
            container.clientePhotoRepository.salvarFotoCliente(seeded.clienteId, fotoUri)
        }

        WaitConditions.awaitDb {
            container.contaRepository.getSaldoCentavos(seeded.clienteId) == SALDO_ESPERADO_CENTAVOS
        }
        WaitConditions.awaitDb {
            container.clienteRepository.getClienteById(seeded.clienteId)?.fotoNome != null
        }
        DatabaseFixture.assertForeignKeysOk(container)
    }

    private companion object {
        const val SALDO_ESPERADO_CENTAVOS = 1200L
    }
}
