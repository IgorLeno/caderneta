package com.example.caderneta.viewmodel

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.caderneta.data.AppDatabase
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Local
import com.example.caderneta.data.entity.TipoTransacao
import com.example.caderneta.domain.FinanceiroService
import com.example.caderneta.repository.VendaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class BalancoCaixaViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: AppDatabase
    private lateinit var financeiroService: FinanceiroService
    private var localId: Long = 0
    private var clienteId: Long = 0

    @Before
    fun setUp() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            db =
                Room
                    .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
            financeiroService = FinanceiroService(db)
            localId = db.localDao().insertLocal(Local(nome = "Local"))
            clienteId = db.clienteDao().insertCliente(Cliente(nome = "Cliente", telefone = null, localId = localId))
        }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun recebimentosSomamEntradasDeCaixaENaoSaldoLiquido() =
        runTest {
            financeiroService.registrarVenda(
                clienteId = clienteId,
                localId = localId,
                tipoTransacao = TipoTransacao.A_VISTA,
                isPromocao = false,
                quantidadeSalgados = 1,
                quantidadeSucos = 0,
                valorCentavos = 500,
                promocaoDetalhes = null,
            )
            financeiroService.registrarVenda(
                clienteId = clienteId,
                localId = localId,
                tipoTransacao = TipoTransacao.A_PRAZO,
                isPromocao = false,
                quantidadeSalgados = 2,
                quantidadeSucos = 0,
                valorCentavos = 1200,
                promocaoDetalhes = null,
            )
            financeiroService.registrarPagamento(
                clienteId = clienteId,
                localId = localId,
                valorCentavos = 400,
            )

            val viewModel = BalancoCaixaViewModel(VendaRepository(db.vendaDao()))
            val state =
                withTimeout(1_000) {
                    viewModel.vendasState.first { it.quantidadeOperacoesDiarias == 3 }
                }

            assertEquals(1700L, state.totalVendasDiariasCentavos)
            assertEquals(900L, state.totalRecebimentosDiariosCentavos)
            assertEquals(3, state.quantidadeOperacoesDiarias)
        }
}
