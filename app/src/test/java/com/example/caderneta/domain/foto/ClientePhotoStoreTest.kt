package com.example.caderneta.domain.foto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClientePhotoStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun newPhotoNameForUsesVersionedClienteIdName() {
        val store = ClientePhotoStore(context)

        val first = store.newPhotoNameFor(42)
        val second = store.newPhotoNameFor(42)

        assertTrue(first.matches(Regex("cliente_42_[A-Za-z0-9-]+\\.jpg")))
        assertTrue(second.matches(Regex("cliente_42_[A-Za-z0-9-]+\\.jpg")))
        assertNotEquals(first, second)
    }

    @Test
    fun rejectsPathTraversalNames() {
        assertFalse(ClientePhotoStore.isValidPhotoName("../cliente_1.jpg"))
        assertFalse(ClientePhotoStore.isValidPhotoName("cliente_1.png"))
        assertFalse(ClientePhotoStore.isValidPhotoName("cliente_0.jpg"))
        assertTrue(ClientePhotoStore.isValidPhotoName("cliente_1.jpg"))
        assertTrue(ClientePhotoStore.isValidPhotoName("cliente_1_123e4567-e89b-12d3-a456-426614174000.jpg"))
    }

    @Test
    fun writeAtomicPersistsBytesInPrivatePhotoDir() {
        val store = ClientePhotoStore(context)
        val bytes = byteArrayOf(1, 2, 3, 4)

        val file = store.writeAtomic("cliente_999999.jpg", bytes)

        assertArrayEquals(bytes, file.readBytes())
        assertEquals("client_photos", file.parentFile?.name)
        store.delete("cliente_999999.jpg")
    }
}
