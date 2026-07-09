package com.example.caderneta.viewmodel

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.caderneta.data.AppDatabase
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Configuracoes
import com.example.caderneta.data.entity.Local
import com.example.caderneta.data.entity.ModoOperacao
import com.example.caderneta.data.entity.TipoTransacao
import com.example.caderneta.data.entity.TransacaoVenda
import com.example.caderneta.domain.FinanceiroService
import com.example.caderneta.repository.ClienteRepository
import com.example.caderneta.repository.ConfiguracoesRepository
import com.example.caderneta.repository.ContaRepository
import com.example.caderneta.repository.LocalRepository
import com.example.caderneta.repository.VendaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VendasViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var executor: ExecutorService
    private lateinit var db: AppDatabase
    private lateinit var viewModel: VendasViewModel
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
            localId = db.localDao().insertLocal(Local(nome = "Local"))
            clienteId = db.clienteDao().insertCliente(Cliente(nome = "Cliente", telefone = null, localId = localId))
            viewModel = newViewModel()
        }

    @After
    fun tearDown() {
        db.close()
        executor.shutdownNow()
    }

    @Test
    fun configuracoesCarregaEagerSemColetor() =
        runTest {
            db.configuracoesDao().insertConfiguracoes(config())
            viewModel = newViewModel()

            waitConfiguracoes()

            assertEquals(config(), viewModel.configuracoes.value)
        }

    @Test
    fun vendaSemConfigPublicaErroENaoAplicaSelecao() =
        runTest {
            val cliente = requireNotNull(db.clienteDao().getClienteById(clienteId))

            viewModel.selecionarModoOperacao(cliente, ModoOperacao.VENDA)

            val evento = withTimeout(1_000) { viewModel.eventos.first() }
            assertTrue(evento is UiEvento.Erro)
            assertNull(viewModel.getClienteState(clienteId))
        }

    @Test
    fun vendaFuncionaComConfig() =
        runTest {
            salvarConfigESelecionarLocal()
            waitConfiguracoes()
            advanceUntilIdle()
            val cliente = requireNotNull(db.clienteDao().getClienteById(clienteId))

            viewModel.selecionarModoOperacao(cliente, ModoOperacao.VENDA)
            viewModel.selecionarTipoTransacao(cliente, TipoTransacao.A_PRAZO)
            viewModel.updateQuantidadeSalgados(clienteId, 2)
            viewModel.confirmarOperacao(clienteId)
            advanceUntilIdle()

            val venda =
                db
                    .vendaDao()
                    .getAllVendas()
                    .first()
                    .single()
            assertEquals(1200L, venda.valorCentavos)
            assertEquals(TransacaoVenda.A_PRAZO, venda.transacao)
            assertEquals(1200L, db.contaDao().getContaByCliente(clienteId)?.saldoCentavos)
        }

    @Test
    fun pagamentoFuncionaSemConfig() =
        runTest {
            FinanceiroService(db).registrarVenda(
                clienteId = clienteId,
                localId = localId,
                tipoTransacao = TipoTransacao.A_PRAZO,
                isPromocao = false,
                quantidadeSalgados = 1,
                quantidadeSucos = 0,
                valorCentavos = 500,
                promocaoDetalhes = null,
            )
            val cliente = requireNotNull(db.clienteDao().getClienteById(clienteId))

            viewModel.selecionarModoOperacao(cliente, ModoOperacao.PAGAMENTO)
            viewModel.updateValorTotal(clienteId, 200)
            viewModel.confirmarOperacao(clienteId)
            advanceUntilIdle()

            val venda =
                db
                    .vendaDao()
                    .getAllVendas()
                    .first()
                    .last()
            assertEquals(TransacaoVenda.PAGAMENTO, venda.transacao)
            assertEquals(300L, db.contaDao().getContaByCliente(clienteId)?.saldoCentavos)
        }

    @Test
    fun promocaoRespeitaConfig() =
        runTest {
            salvarConfigESelecionarLocal(config(promocoesAtivadas = true))
            waitConfiguracoes()
            advanceUntilIdle()
            val cliente = requireNotNull(db.clienteDao().getClienteById(clienteId))

            viewModel.selecionarModoOperacao(cliente, ModoOperacao.PROMOCAO)
            viewModel.selecionarTipoTransacao(cliente, TipoTransacao.A_VISTA)
            viewModel.updateQuantidadePromo1(clienteId, 2)
            viewModel.confirmarOperacao(clienteId)
            advanceUntilIdle()

            val venda =
                db
                    .vendaDao()
                    .getAllVendas()
                    .first()
                    .single()
            assertEquals(1400L, venda.valorCentavos)
            assertTrue(venda.isPromocao)
            assertEquals(2, venda.quantidadeSalgados)
            assertEquals(2, venda.quantidadeSucos)
        }

    @Test
    fun confirmacaoDuplicadaRegistraUmaVenda() =
        runTest {
            salvarConfigESelecionarLocal()
            waitConfiguracoes()
            advanceUntilIdle()
            val cliente = requireNotNull(db.clienteDao().getClienteById(clienteId))
            viewModel.selecionarModoOperacao(cliente, ModoOperacao.VENDA)
            viewModel.selecionarTipoTransacao(cliente, TipoTransacao.A_VISTA)
            viewModel.updateQuantidadeSalgados(clienteId, 1)

            viewModel.confirmarOperacao(clienteId)
            viewModel.confirmarOperacao(clienteId)
            advanceUntilIdle()

            assertEquals(
                1,
                db
                    .vendaDao()
                    .getAllVendas()
                    .first()
                    .size,
            )
        }

    private suspend fun salvarConfigESelecionarLocal(configuracoes: Configuracoes = config()) {
        db.configuracoesDao().insertConfiguracoes(configuracoes)
        viewModel = newViewModel()
        viewModel.selecionarLocal(localId)
    }

    private suspend fun waitConfiguracoes() {
        withTimeout(1_000) {
            while (viewModel.configuracoes.value == null) {
                delay(10)
            }
        }
    }

    private fun newViewModel(): VendasViewModel =
        VendasViewModel(
            clienteRepository = ClienteRepository(db.clienteDao()),
            localRepository = LocalRepository(db.localDao(), db.clienteDao(), db),
            vendaRepository = VendaRepository(db.vendaDao()),
            configuracoesRepository = ConfiguracoesRepository(db.configuracoesDao()),
            contaRepository = ContaRepository(db.contaDao()),
            financeiroService = FinanceiroService(db),
        )

    private fun config(promocoesAtivadas: Boolean = false) =
        Configuracoes(
            precoSalgadoVistaCentavos = 500,
            precoSalgadoPrazoCentavos = 600,
            precoSucoVistaCentavos = 300,
            precoSucoPrazoCentavos = 400,
            promocoesAtivadas = promocoesAtivadas,
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
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
