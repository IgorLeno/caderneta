package com.example.caderneta.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.caderneta.data.AppDatabase
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Configuracoes
import com.example.caderneta.data.entity.Local
import com.example.caderneta.data.entity.TipoTransacao
import com.example.caderneta.domain.FinanceiroService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
class BackupRoundTripTest {
    private lateinit var executor: ExecutorService
    private lateinit var db: AppDatabase
    private lateinit var manager: BackupManager
    private lateinit var financeiroService: FinanceiroService

    @Before
    fun setUp() {
        executor = Executors.newSingleThreadExecutor()
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .setTransactionExecutor(executor)
                .build()
        manager = BackupManager(context, db)
        financeiroService = FinanceiroService(db)
    }

    @After
    fun tearDown() {
        db.close()
        executor.shutdownNow()
    }

    @Test
    fun exportaERestauraSnapshotRecalculandoConta() =
        runTest {
            val localId = db.localDao().insertLocal(Local(nome = "Local"))
            val clienteId = db.clienteDao().insertCliente(Cliente(nome = "Cliente", telefone = null, localId = localId))
            db.configuracoesDao().insertConfiguracoes(config())
            financeiroService.registrarVenda(clienteId, localId, TipoTransacao.A_PRAZO, false, 1, 0, 1000, null)
            financeiroService.registrarPagamento(clienteId, null, 300)

            val output = ByteArrayOutputStream()
            manager.exportar(output)
            val (snapshot) = manager.lerResumo(ByteArrayInputStream(output.toByteArray()))

            db.localDao().insertLocal(Local(nome = "Ruido"))
            manager.restaurar(snapshot.copy(contas = emptyList()))

            assertEquals(
                1,
                db
                    .clienteDao()
                    .getAllClientes()
                    .first()
                    .size,
            )
            assertEquals(
                2,
                db
                    .vendaDao()
                    .getAllVendas()
                    .first()
                    .size,
            )
            assertEquals(700L, db.contaDao().getContaByCliente(clienteId)?.saldoCentavos)
        }

    @Test
    fun backupInvalidoNaoAlteraBanco() =
        runTest {
            val localId = db.localDao().insertLocal(Local(nome = "Local"))
            val snapshot =
                BackupSnapshot(
                    app = "outro.app",
                    geradoEmMillis = 1,
                    locais = emptyList(),
                    clientes = emptyList(),
                    operacoes = emptyList(),
                    vendas = emptyList(),
                    contas = emptyList(),
                    configuracoes = emptyList(),
                )

            var falhou = false
            try {
                manager.restaurar(snapshot)
            } catch (_: IllegalArgumentException) {
                falhou = true
            }

            assertTrue(falhou)
            assertEquals(localId, db.localDao().getLocalById(localId)?.id)
        }

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
}
