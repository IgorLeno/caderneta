package com.example.caderneta.domain.foto

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.caderneta.data.entity.Cliente
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClientePhotoRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun replaceFailureKeepsPreviousPhotoAndDeletesNewFile() =
        runTest {
            val store = ClientePhotoStore(context)
            store.deleteUnreferenced(emptySet())
            val previousName = store.newPhotoNameFor(1)
            val previousBytes = byteArrayOf(1, 2, 3)
            store.writeAtomic(previousName, previousBytes)
            val clientStore =
                FakeClientStore(
                    cliente = cliente(fotoNome = previousName),
                    failOnUpdate = true,
                )
            val repository =
                ClientePhotoRepository(
                    clienteRepository = clientStore,
                    store = store,
                    processor = PhotoProcessor { byteArrayOf(9, 8, 7) },
                )

            var failed = false
            try {
                repository.salvarFotoCliente(1, Uri.EMPTY)
            } catch (_: IllegalStateException) {
                failed = true
            }

            assertTrue(failed)
            assertEquals(previousName, clientStore.cliente.fotoNome)
            assertArrayEquals(previousBytes, store.existingPhotoFile(previousName)?.readBytes())
            assertEquals(listOf(previousName), clientPhotoNames(store))
            store.delete(previousName)
        }

    @Test
    fun replaceSuccessWritesVersionedPhotoAndDeletesPrevious() =
        runTest {
            val store = ClientePhotoStore(context)
            store.deleteUnreferenced(emptySet())
            val previousName = store.newPhotoNameFor(2)
            store.writeAtomic(previousName, byteArrayOf(1))
            val clientStore = FakeClientStore(cliente(previousName, id = 2))
            val repository =
                ClientePhotoRepository(
                    clienteRepository = clientStore,
                    store = store,
                    processor = PhotoProcessor { byteArrayOf(4, 5, 6) },
                )

            val newName = repository.salvarFotoCliente(2, Uri.EMPTY)

            assertNotEquals(previousName, newName)
            assertEquals(newName, clientStore.cliente.fotoNome)
            assertFalse(store.existingPhotoFile(previousName)?.exists() == true)
            assertArrayEquals(byteArrayOf(4, 5, 6), store.existingPhotoFile(newName)?.readBytes())
            store.delete(newName)
        }

    private fun clientPhotoNames(store: ClientePhotoStore): List<String> {
        val dir = requireNotNull(store.photoFile("cliente_1.jpg")?.parentFile)
        return dir
            .listFiles()
            ?.map { it.name }
            ?.filter { ClientePhotoStore.isValidPhotoName(it) }
            .orEmpty()
    }

    private fun cliente(
        fotoNome: String?,
        id: Long = 1,
    ): Cliente =
        Cliente(
            id = id,
            nome = "Cliente",
            telefone = null,
            localId = 1,
            fotoNome = fotoNome,
        )

    private class FakeClientStore(
        var cliente: Cliente,
        private val failOnUpdate: Boolean = false,
    ) : ClientePhotoClientStore {
        override suspend fun getClienteById(id: Long): Cliente? = cliente.takeIf { it.id == id }

        override suspend fun updateCliente(cliente: Cliente) {
            if (failOnUpdate) error("db failure")
            this.cliente = cliente
        }
    }
}
