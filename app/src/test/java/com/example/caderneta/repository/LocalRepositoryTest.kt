package com.example.caderneta.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.caderneta.data.AppDatabase
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Local
import com.example.caderneta.data.entity.TipoTransacao
import com.example.caderneta.domain.FinanceiroService
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
class LocalRepositoryTest {
    private lateinit var executor: ExecutorService
    private lateinit var db: AppDatabase
    private lateinit var repository: LocalRepository

    @Before
    fun setUp() {
        executor = Executors.newSingleThreadExecutor()
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .setTransactionExecutor(executor)
                .build()
        repository = LocalRepository(db.localDao(), db)
    }

    @After
    fun tearDown() {
        db.close()
        executor.shutdownNow()
    }

    @Test
    fun deleteFisicoRemoveLocalSemVinculos() =
        runTest {
            val id = repository.insertLocal(Local(nome = "Livre"))

            val arquivado = repository.deleteLocal(requireNotNull(repository.getLocalById(id)))

            assertFalse(arquivado)
            assertNull(repository.getLocalById(id))
        }

    @Test
    fun deleteComFilhosArquivaSubarvoreAtiva() =
        runTest {
            val paiId = repository.insertLocal(Local(nome = "Pai"))
            val filhoId = repository.insertLocal(Local(nome = "Filho", parentId = paiId, level = 1))
            val netoId = repository.insertLocal(Local(nome = "Neto", parentId = filhoId, level = 2))

            val arquivado = repository.deleteLocal(requireNotNull(repository.getLocalById(paiId)))

            assertTrue(arquivado)
            listOf(paiId, filhoId, netoId).forEach { id ->
                assertTrue(requireNotNull(repository.getLocalById(id)).arquivado)
            }
            assertTrue(
                db.localDao().getAllLocaisList().none { local ->
                    !local.arquivado && local.parentId in listOf(paiId, filhoId, netoId)
                },
            )
        }

    @Test
    fun arquivamentoPreservaHistoricoFinanceiro() =
        runTest {
            val localId = repository.insertLocal(Local(nome = "Com histórico"))
            val clienteId = db.clienteDao().insertCliente(Cliente(nome = "Cliente", telefone = null, localId = localId))
            FinanceiroService(db).registrarVenda(clienteId, localId, TipoTransacao.A_PRAZO, false, 1, 0, 1000, null)

            val arquivado = repository.deleteLocal(requireNotNull(repository.getLocalById(localId)))

            assertTrue(arquivado)
            assertTrue(requireNotNull(repository.getLocalById(localId)).arquivado)
            assertEquals(
                1,
                db
                    .vendaDao()
                    .getAllVendas()
                    .first()
                    .size,
            )
            assertEquals(1000L, db.contaDao().getContaByCliente(clienteId)?.saldoCentavos)
        }
}
