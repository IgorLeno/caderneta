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
        repository = LocalRepository(db.localDao(), db.clienteDao(), db)
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
            assertTrue(requireNotNull(db.clienteDao().getClienteById(clienteId)).arquivado)
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

    @Test
    fun arquivarPaiArquivaClientesDaSubarvoreEPreservaOutraArvore() =
        runTest {
            val paiId = repository.insertLocal(Local(nome = "Pai"))
            val filhoId = repository.insertLocal(Local(nome = "Filho", parentId = paiId, level = 1))
            val netoId = repository.insertLocal(Local(nome = "Neto", parentId = filhoId, level = 2))
            val outroId = repository.insertLocal(Local(nome = "Outro"))
            val clientePai = inserirCliente("Cliente pai", paiId)
            val clienteFilho = inserirCliente("Cliente filho", paiId, sublocal1Id = filhoId)
            val clienteNeto = inserirCliente("Cliente neto", paiId, sublocal1Id = filhoId, sublocal2Id = netoId)
            val clienteOutro = inserirCliente("Cliente outro", outroId)

            val arquivado = repository.deleteLocal(requireNotNull(repository.getLocalById(paiId)))

            assertTrue(arquivado)
            listOf(clientePai, clienteFilho, clienteNeto).forEach { clienteId ->
                assertTrue(requireNotNull(db.clienteDao().getClienteById(clienteId)).arquivado)
            }
            assertFalse(requireNotNull(db.clienteDao().getClienteById(clienteOutro)).arquivado)
            assertTrue(
                db
                    .clienteDao()
                    .getClientesByLocalHierarchy(paiId)
                    .first()
                    .none { !it.arquivado },
            )
        }

    @Test
    fun naoPermaneceClienteAtivoLigadoALocalArquivado() =
        runTest {
            val paiId = repository.insertLocal(Local(nome = "Pai"))
            val filhoId = repository.insertLocal(Local(nome = "Filho", parentId = paiId, level = 1))
            inserirCliente("Cliente filho", paiId, sublocal1Id = filhoId)

            repository.deleteLocal(requireNotNull(repository.getLocalById(paiId)))

            val locaisArquivados =
                db
                    .localDao()
                    .getAllLocaisList()
                    .filter { it.arquivado }
                    .map { it.id }
                    .toSet()
            assertTrue(locaisArquivados.containsAll(listOf(paiId, filhoId)))
            assertTrue(
                db
                    .clienteDao()
                    .getAllClientes()
                    .first()
                    .none { cliente ->
                        !cliente.arquivado &&
                            listOf(
                                cliente.localId,
                                cliente.sublocal1Id,
                                cliente.sublocal2Id,
                                cliente.sublocal3Id,
                            ).any { it in locaisArquivados }
                    },
            )
        }

    private suspend fun inserirCliente(
        nome: String,
        localId: Long,
        sublocal1Id: Long? = null,
        sublocal2Id: Long? = null,
        sublocal3Id: Long? = null,
    ): Long =
        db.clienteDao().insertCliente(
            Cliente(
                nome = nome,
                telefone = null,
                localId = localId,
                sublocal1Id = sublocal1Id,
                sublocal2Id = sublocal2Id,
                sublocal3Id = sublocal3Id,
            ),
        )
}
