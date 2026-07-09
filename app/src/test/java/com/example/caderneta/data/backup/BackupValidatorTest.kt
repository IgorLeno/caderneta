package com.example.caderneta.data.backup

import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Configuracoes
import com.example.caderneta.data.entity.Local
import com.example.caderneta.data.entity.ModoOperacao
import com.example.caderneta.data.entity.Operacao
import com.example.caderneta.data.entity.TransacaoVenda
import com.example.caderneta.data.entity.Venda
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Date

class BackupValidatorTest {
    private val validator = BackupValidator(APP)

    @Test
    fun aceitaSnapshotCompletoValido() {
        validator.validar(snapshotValido())
    }

    @Test
    fun rejeitaDbVersionIncompativel() {
        assertInvalido(snapshotValido().copy(dbVersion = 2))
    }

    @Test
    fun rejeitaIdsDuplicados() {
        val snapshot = snapshotValido()

        assertInvalido(snapshot.copy(locais = snapshot.locais + snapshot.locais.first()))
    }

    @Test
    fun rejeitaOperacaoIdDuplicadoEntreVendas() {
        val snapshot = snapshotValido()

        assertInvalido(snapshot.copy(vendas = snapshot.vendas + snapshot.vendas.first().copy(id = 2)))
    }

    @Test
    fun rejeitaCicloNaHierarquiaDeLocais() {
        val snapshot =
            snapshotValido().copy(
                locais =
                    listOf(
                        Local(id = 1, nome = "A", parentId = 2, level = 1),
                        Local(id = 2, nome = "B", parentId = 1, level = 2),
                    ),
            )

        assertInvalido(snapshot)
    }

    @Test
    fun rejeitaLevelIncoerente() {
        val snapshot =
            snapshotComFilho().copy(
                locais = snapshotComFilho().locais.mapLast { it.copy(level = 3) },
            )

        assertInvalido(snapshot)
    }

    @Test
    fun rejeitaFilhoAtivoSobPaiArquivado() {
        val snapshot =
            snapshotComFilho().copy(
                locais =
                    listOf(
                        Local(id = 1, nome = "Pai", arquivado = true),
                        Local(id = 2, nome = "Filho", parentId = 1, level = 1),
                    ),
            )

        assertInvalido(snapshot)
    }

    @Test
    fun rejeitaVendaOperacaoComClienteDiferente() {
        val snapshot = snapshotValido()

        assertInvalido(snapshot.copy(operacoes = listOf(snapshot.operacoes.first().copy(clienteId = 99))))
    }

    @Test
    fun rejeitaVendaOperacaoComValorDiferente() {
        val snapshot = snapshotValido()

        assertInvalido(snapshot.copy(operacoes = listOf(snapshot.operacoes.first().copy(valorCentavos = 999))))
    }

    @Test
    fun rejeitaVendaOperacaoComDataDiferente() {
        val snapshot = snapshotValido()

        assertInvalido(snapshot.copy(operacoes = listOf(snapshot.operacoes.first().copy(data = Date(2)))))
    }

    @Test
    fun rejeitaTipoIncompativel() {
        val snapshot = snapshotValido()

        assertInvalido(
            snapshot.copy(
                operacoes = listOf(snapshot.operacoes.first().copy(tipoOperacao = ModoOperacao.PAGAMENTO)),
            ),
        )
    }

    @Test
    fun rejeitaOperacaoOrfa() {
        val snapshot = snapshotValido()

        assertInvalido(
            snapshot.copy(
                operacoes = snapshot.operacoes + Operacao(2, 1, ModoOperacao.VENDA, 100, Date(1)),
            ),
        )
    }

    @Test
    fun rejeitaConfiguracaoComIdInesperado() {
        val snapshot = snapshotValido()

        assertInvalido(snapshot.copy(configuracoes = listOf(config().copy(id = 2))))
    }

    private fun assertInvalido(snapshot: BackupSnapshot) {
        assertThrows(IllegalArgumentException::class.java) {
            validator.validar(snapshot)
        }
    }

    private fun snapshotComFilho(): BackupSnapshot {
        val snapshot = snapshotValido()
        return snapshot.copy(
            locais = snapshot.locais + Local(id = 2, nome = "Filho", parentId = 1, level = 1),
        )
    }

    private fun snapshotValido(): BackupSnapshot =
        BackupSnapshot(
            app = APP,
            geradoEmMillis = 1,
            locais = listOf(Local(id = 1, nome = "Local")),
            clientes = listOf(Cliente(id = 1, nome = "Cliente", telefone = null, localId = 1)),
            operacoes =
                listOf(
                    Operacao(
                        id = 1,
                        clienteId = 1,
                        tipoOperacao = ModoOperacao.VENDA,
                        valorCentavos = 100,
                        data = Date(1),
                    ),
                ),
            vendas =
                listOf(
                    Venda(
                        id = 1,
                        operacaoId = 1,
                        clienteId = 1,
                        localId = 1,
                        data = Date(1),
                        transacao = TransacaoVenda.A_VISTA,
                        quantidadeSalgados = 1,
                        quantidadeSucos = 0,
                        valorCentavos = 100,
                    ),
                ),
            contas = emptyList(),
            configuracoes = listOf(config()),
        )

    private fun config() =
        Configuracoes(
            precoSalgadoVistaCentavos = 500,
            precoSalgadoPrazoCentavos = 600,
            precoSucoVistaCentavos = 300,
            precoSucoPrazoCentavos = 400,
            promocoesAtivadas = false,
            promo1Nome = "Promo 1",
            promo1Salgados = 1,
            promo1Sucos = 1,
            promo1VistaCentavos = 700,
            promo1PrazoCentavos = 800,
            promo2Nome = "Promo 2",
            promo2Salgados = 2,
            promo2Sucos = 2,
            promo2VistaCentavos = 900,
            promo2PrazoCentavos = 1000,
        )

    private fun List<Local>.mapLast(transform: (Local) -> Local): List<Local> = dropLast(1) + transform(last())

    private companion object {
        const val APP = "com.example.caderneta"
    }
}
