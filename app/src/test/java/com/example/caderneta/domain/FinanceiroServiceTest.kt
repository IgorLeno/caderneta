package com.example.caderneta.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.caderneta.data.AppDatabase
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Conta
import com.example.caderneta.data.entity.Local
import com.example.caderneta.data.entity.TipoTransacao
import com.example.caderneta.data.entity.TransacaoVenda
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
class FinanceiroServiceTest {
    private lateinit var executor: ExecutorService
    private lateinit var db: AppDatabase
    private lateinit var service: FinanceiroService
    private var localId: Long = 0
    private var clienteId: Long = 0

    @Before
    fun setUp() =
        runTest {
            executor = Executors.newSingleThreadExecutor()
            val context = ApplicationProvider.getApplicationContext<Context>()
            db =
                Room
                    .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                    .setTransactionExecutor(executor)
                    .build()
            service = FinanceiroService(db)
            localId = db.localDao().insertLocal(Local(nome = "Local"))
            clienteId = db.clienteDao().insertCliente(Cliente(nome = "Cliente", telefone = null, localId = localId))
        }

    @After
    fun tearDown() {
        db.close()
        executor.shutdownNow()
    }

    @Test
    fun vendaAVistaNaoAlteraSaldo() =
        runTest {
            service.registrarVenda(clienteId, localId, TipoTransacao.A_VISTA, false, 1, 0, 500, null)

            assertEquals(null, db.contaDao().getContaByCliente(clienteId))
            assertEquals(0L, service.calcularSaldoDoHistorico(clienteId))
        }

    @Test
    fun vendaAPrazoAumentaSaldoEPagamentoReduz() =
        runTest {
            service.registrarVenda(clienteId, localId, TipoTransacao.A_PRAZO, false, 1, 0, 1000, null)
            service.registrarPagamento(clienteId, null, 400)

            assertEquals(600L, saldoConta())
            assertEquals(600L, service.calcularSaldoDoHistorico(clienteId))
        }

    @Test
    fun pagamentoUsaHistoricoQuandoCacheEstaAbaixo() =
        runTest {
            service.registrarVenda(clienteId, localId, TipoTransacao.A_PRAZO, false, 1, 0, 1000, null)
            db.contaDao().insertConta(Conta(clienteId, saldoCentavos = 500))

            service.registrarPagamento(clienteId, null, 200)

            assertEquals(800L, saldoConta())
            assertEquals(800L, service.calcularSaldoDoHistorico(clienteId))
        }

    @Test
    fun pagamentoUsaHistoricoQuandoCacheEstaAcima() =
        runTest {
            service.registrarVenda(clienteId, localId, TipoTransacao.A_PRAZO, false, 1, 0, 1000, null)
            db.contaDao().insertConta(Conta(clienteId, saldoCentavos = 1500))

            service.registrarPagamento(clienteId, null, 200)

            assertEquals(800L, saldoConta())
            assertEquals(800L, service.calcularSaldoDoHistorico(clienteId))
        }

    @Test
    fun pagamentoMaiorQueSaldoFalhaSemParciais() =
        runTest {
            service.registrarVenda(clienteId, localId, TipoTransacao.A_PRAZO, false, 1, 0, 1000, null)
            val vendasAntes =
                db
                    .vendaDao()
                    .getAllVendas()
                    .first()
                    .size
            val operacoesAntes =
                db
                    .operacaoDao()
                    .getAllOperacoes()
                    .first()
                    .size

            assertFalhaEstado {
                service.registrarPagamento(clienteId, null, 1001)
            }

            assertEquals(1000L, saldoConta())
            assertEquals(
                vendasAntes,
                db
                    .vendaDao()
                    .getAllVendas()
                    .first()
                    .size,
            )
            assertEquals(
                operacoesAntes,
                db
                    .operacaoDao()
                    .getAllOperacoes()
                    .first()
                    .size,
            )
            assertEquals(1000L, service.calcularSaldoDoHistorico(clienteId))
        }

    @Test
    fun pagamentoSemDividaFalhaSemParciais() =
        runTest {
            assertFalhaEstado {
                service.registrarPagamento(clienteId, null, 1)
            }

            assertNull(db.contaDao().getContaByCliente(clienteId))
            assertEquals(0, quantidadeVendas())
            assertEquals(0, quantidadeOperacoes())
            assertEquals(0L, service.calcularSaldoDoHistorico(clienteId))
        }

    @Test
    fun pagamentoZeroOuNegativoFalhaAntesDeEscrever() =
        runTest {
            service.registrarVenda(clienteId, localId, TipoTransacao.A_PRAZO, false, 1, 0, 1000, null)

            assertFalhaArgumento {
                service.registrarPagamento(clienteId, null, 0)
            }
            assertFalhaArgumento {
                service.registrarPagamento(clienteId, null, -1)
            }

            assertEquals(1, quantidadeVendas())
            assertEquals(1, quantidadeOperacoes())
            assertEquals(1000L, saldoConta())
            assertEquals(1000L, service.calcularSaldoDoHistorico(clienteId))
        }

    @Test
    fun edicaoDePagamentoNaoNegativaSaldo() =
        runTest {
            service.registrarVenda(clienteId, localId, TipoTransacao.A_PRAZO, false, 1, 0, 1000, null)
            val pagamento = service.registrarPagamento(clienteId, null, 400)

            assertFalhaEstado {
                service.editarOperacao(pagamento, pagamento.copy(valorCentavos = 1200))
            }

            assertEquals(600L, saldoConta())
            assertEquals(600L, service.calcularSaldoDoHistorico(clienteId))
        }

    @Test
    fun trocaAVistaParaAPrazoEAoContrarioAjustaSaldo() =
        runTest {
            val venda = service.registrarVenda(clienteId, localId, TipoTransacao.A_VISTA, false, 1, 0, 700, null)
            service.editarOperacao(venda, venda.copy(transacao = TransacaoVenda.A_PRAZO))
            assertEquals(700L, saldoConta())

            val atualizada = requireNotNull(db.vendaDao().getVendaById(venda.id))
            service.editarOperacao(atualizada, atualizada.copy(transacao = TransacaoVenda.A_VISTA))
            assertEquals(0L, saldoConta())
        }

    @Test
    fun exclusaoReverteEfeitoNoSaldo() =
        runTest {
            val venda = service.registrarVenda(clienteId, localId, TipoTransacao.A_PRAZO, false, 1, 0, 800, null)
            service.excluirOperacao(venda)

            assertEquals(0L, saldoConta())
            assertEquals(
                0,
                db
                    .vendaDao()
                    .getAllVendas()
                    .first()
                    .size,
            )
            assertEquals(
                0,
                db
                    .operacaoDao()
                    .getAllOperacoes()
                    .first()
                    .size,
            )
        }

    @Test
    fun exclusaoNaoPodeNegativarCacheCorrompido() =
        runTest {
            val venda = service.registrarVenda(clienteId, localId, TipoTransacao.A_PRAZO, false, 1, 0, 800, null)
            db.contaDao().insertConta(Conta(clienteId, saldoCentavos = 100))

            assertFalhaEstado {
                service.excluirOperacao(venda)
            }

            assertEquals(
                1,
                db
                    .vendaDao()
                    .getAllVendas()
                    .first()
                    .size,
            )
            assertEquals(100L, saldoConta())
        }

    @Test
    fun reconciliacaoCorrigeCacheCorrompido() =
        runTest {
            service.registrarVenda(clienteId, localId, TipoTransacao.A_PRAZO, false, 1, 0, 1000, null)
            service.registrarPagamento(clienteId, null, 250)
            db.contaDao().insertConta(Conta(clienteId, saldoCentavos = 99))

            val resultado = service.reconciliarConta(clienteId)

            assertTrue(resultado.corrigido)
            assertEquals(99L, resultado.saldoAnteriorCentavos)
            assertEquals(750L, resultado.saldoHistoricoCentavos)
            assertEquals(750L, saldoConta())
        }

    @Test
    fun reconciliarTodasContasIncluiContasSemHistorico() =
        runTest {
            db.contaDao().insertConta(Conta(clienteId, saldoCentavos = 123))

            val resultados = service.reconciliarTodasContas()

            assertEquals(1, resultados.size)
            assertFalse(resultados.single().saldoHistoricoCentavos == resultados.single().saldoAnteriorCentavos)
            assertEquals(0L, saldoConta())
        }

    private suspend fun assertFalhaEstado(block: suspend () -> Unit) {
        var falhou = false
        try {
            block()
        } catch (_: IllegalStateException) {
            falhou = true
        }
        assertTrue(falhou)
    }

    private suspend fun assertFalhaArgumento(block: suspend () -> Unit) {
        var falhou = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            falhou = true
        }
        assertTrue(falhou)
    }

    private suspend fun quantidadeVendas(): Int =
        db
            .vendaDao()
            .getAllVendas()
            .first()
            .size

    private suspend fun quantidadeOperacoes(): Int =
        db
            .operacaoDao()
            .getAllOperacoes()
            .first()
            .size

    private suspend fun saldoConta(): Long = requireNotNull(db.contaDao().getContaByCliente(clienteId)).saldoCentavos
}
