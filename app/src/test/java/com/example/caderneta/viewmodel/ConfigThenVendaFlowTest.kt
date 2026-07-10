package com.example.caderneta.viewmodel

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.caderneta.data.AppDatabase
import com.example.caderneta.data.backup.BackupManager
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ConfigThenVendaFlowTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var configuracoesRepository: ConfiguracoesRepository
    private var localId: Long = 0
    private var clienteId: Long = 0

    @Before
    fun setUp() =
        runTest {
            context = ApplicationProvider.getApplicationContext()
            db =
                Room
                    .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
            configuracoesRepository = ConfiguracoesRepository(db.configuracoesDao())
            localId = db.localDao().insertLocal(Local(nome = "Local"))
            clienteId = db.clienteDao().insertCliente(Cliente(nome = "Cliente", telefone = null, localId = localId))
        }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun configSalvaPeloViewModelEhUsadaPelaVenda() =
        runTest {
            val configuracoesViewModel =
                ConfiguracoesViewModel(
                    repository = configuracoesRepository,
                    backupManager = BackupManager(context, db),
                )

            configuracoesViewModel.salvarConfiguracoes(config())
            advanceUntilIdle()

            val evento = withTimeout(1_000) { configuracoesViewModel.eventos.first() }
            assertTrue(evento is UiEvento.Sucesso)
            assertEquals(config(), configuracoesRepository.getConfiguracoesOnce())

            val vendasViewModel = newVendasViewModel()
            vendasViewModel.selecionarLocal(localId)
            advanceUntilIdle()
            val cliente = requireNotNull(db.clienteDao().getClienteById(clienteId))

            vendasViewModel.selecionarModoOperacao(cliente, ModoOperacao.VENDA)
            vendasViewModel.selecionarTipoTransacao(cliente, TipoTransacao.A_PRAZO)
            vendasViewModel.updateQuantidadeSalgados(clienteId, 2)
            vendasViewModel.confirmarOperacao(clienteId)
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

    private fun newVendasViewModel(): VendasViewModel =
        VendasViewModel(
            clienteRepository = ClienteRepository(db.clienteDao()),
            localRepository = LocalRepository(db.localDao(), db.clienteDao(), db),
            vendaRepository = VendaRepository(db.vendaDao()),
            configuracoesRepository = configuracoesRepository,
            contaRepository = ContaRepository(db.contaDao()),
            financeiroService = FinanceiroService(db),
        )

    private fun config() =
        Configuracoes(
            precoSalgadoVistaCentavos = 500,
            precoSalgadoPrazoCentavos = 600,
            precoSucoVistaCentavos = 300,
            precoSucoPrazoCentavos = 400,
            promocoesAtivadas = false,
            promo1Nome = "",
            promo1Salgados = 0,
            promo1Sucos = 0,
            promo1VistaCentavos = 0,
            promo1PrazoCentavos = 0,
            promo2Nome = "",
            promo2Salgados = 0,
            promo2Sucos = 0,
            promo2VistaCentavos = 0,
            promo2PrazoCentavos = 0,
        )
}
