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
    fun aceitaClienteComHierarquiaCompletaValida() {
        validator.validar(snapshotComHierarquiaCompleta())
    }

    @Test
    fun rejeitaSublocal1ComPaiIncorreto() {
        val snapshot =
            snapshotComLocaisECliente(
                locais =
                    listOf(
                        Local(id = 1, nome = "Escola A"),
                        Local(id = 2, nome = "Escola B"),
                        Local(id = 3, nome = "Sala B", parentId = 2, level = 1),
                    ),
                cliente = cliente(sublocal1Id = 3),
            )

        assertInvalido(snapshot)
    }

    @Test
    fun rejeitaSublocal2SemSublocal1() {
        val snapshot =
            snapshotComLocaisECliente(
                locais =
                    listOf(
                        Local(id = 1, nome = "Escola"),
                        Local(id = 2, nome = "Sala", parentId = 1, level = 1),
                    ),
                cliente = cliente(sublocal2Id = 2),
            )

        assertInvalido(snapshot)
    }

    @Test
    fun rejeitaSublocal3SemSublocal2() {
        val snapshot =
            snapshotComLocaisECliente(
                locais =
                    listOf(
                        Local(id = 1, nome = "Escola"),
                        Local(id = 2, nome = "Sala", parentId = 1, level = 1),
                        Local(id = 3, nome = "Mesa", parentId = 2, level = 2),
                    ),
                cliente = cliente(sublocal1Id = 2, sublocal3Id = 3),
            )

        assertInvalido(snapshot)
    }

    @Test
    fun rejeitaLocalRepetidoNaCadeiaDoCliente() {
        val snapshot =
            snapshotComLocaisECliente(
                locais = listOf(Local(id = 1, nome = "Escola")),
                cliente = cliente(sublocal1Id = 1),
            )

        assertInvalido(snapshot)
    }

    @Test
    fun rejeitaArvoreCruzadaNaCadeiaDoCliente() {
        val snapshot =
            snapshotComLocaisECliente(
                locais =
                    listOf(
                        Local(id = 1, nome = "Escola A"),
                        Local(id = 2, nome = "Sala A", parentId = 1, level = 1),
                        Local(id = 3, nome = "Escola B"),
                        Local(id = 4, nome = "Mesa B", parentId = 3, level = 1),
                    ),
                cliente = cliente(sublocal1Id = 2, sublocal2Id = 4),
            )

        assertInvalido(snapshot)
    }

    @Test
    fun rejeitaClienteAtivoEmLocalArquivado() {
        val snapshot =
            snapshotComLocaisECliente(
                locais = listOf(Local(id = 1, nome = "Escola", arquivado = true)),
                cliente = cliente(),
            )

        assertInvalido(snapshot)
    }

    @Test
    fun rejeitaClienteAtivoEmSublocalArquivado() {
        val snapshot =
            snapshotComLocaisECliente(
                locais =
                    listOf(
                        Local(id = 1, nome = "Escola"),
                        Local(id = 2, nome = "Sala", parentId = 1, level = 1, arquivado = true),
                    ),
                cliente = cliente(sublocal1Id = 2),
            )

        assertInvalido(snapshot)
    }

    @Test
    fun aceitaClienteArquivadoEmHierarquiaArquivada() {
        val snapshot =
            snapshotComLocaisECliente(
                locais =
                    listOf(
                        Local(id = 1, nome = "Escola", arquivado = true),
                        Local(id = 2, nome = "Sala", parentId = 1, level = 1, arquivado = true),
                    ),
                cliente = cliente(sublocal1Id = 2, arquivado = true),
            )

        validator.validar(snapshot)
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

    private fun snapshotComHierarquiaCompleta(): BackupSnapshot =
        snapshotComLocaisECliente(
            locais =
                listOf(
                    Local(id = 1, nome = "Escola"),
                    Local(id = 2, nome = "Sala", parentId = 1, level = 1),
                    Local(id = 3, nome = "Mesa", parentId = 2, level = 2),
                    Local(id = 4, nome = "Turno", parentId = 3, level = 3),
                ),
            cliente = cliente(sublocal1Id = 2, sublocal2Id = 3, sublocal3Id = 4),
        )

    private fun snapshotComLocaisECliente(
        locais: List<Local>,
        cliente: Cliente,
    ): BackupSnapshot =
        snapshotValido().copy(
            locais = locais,
            clientes = listOf(cliente),
        )

    private fun cliente(
        sublocal1Id: Long? = null,
        sublocal2Id: Long? = null,
        sublocal3Id: Long? = null,
        arquivado: Boolean = false,
    ): Cliente =
        Cliente(
            id = 1,
            nome = "Cliente",
            telefone = null,
            localId = 1,
            sublocal1Id = sublocal1Id,
            sublocal2Id = sublocal2Id,
            sublocal3Id = sublocal3Id,
            arquivado = arquivado,
        )

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
