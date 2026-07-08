package com.example.caderneta.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Local
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
class AppDatabaseSmokeTest {
    private lateinit var executor: ExecutorService
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        executor = Executors.newSingleThreadExecutor()
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .setTransactionExecutor(executor)
                .build()
    }

    @After
    fun tearDown() {
        db.close()
        executor.shutdownNow()
    }

    @Test
    fun criaBancoComForeignKeysEIndices() =
        runTest {
            val localId = db.localDao().insertLocal(Local(nome = "Escola"))
            val clienteId =
                db.clienteDao().insertCliente(
                    Cliente(
                        nome = "Cliente",
                        telefone = null,
                        localId = localId,
                    ),
                )

            val cliente = db.clienteDao().getClienteById(clienteId)
            assertEquals(localId, cliente?.localId)

            db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
                assertEquals(0, cursor.count)
            }

            val indices = mutableSetOf<String>()
            db.openHelper.writableDatabase.query("PRAGMA index_list('clientes')").use { cursor ->
                val nameColumn = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    indices += cursor.getString(nameColumn)
                }
            }

            assertTrue("index_clientes_localId" in indices)
            assertTrue("index_clientes_sublocal1Id" in indices)
            assertTrue("index_clientes_sublocal2Id" in indices)
            assertTrue("index_clientes_sublocal3Id" in indices)
        }
}
