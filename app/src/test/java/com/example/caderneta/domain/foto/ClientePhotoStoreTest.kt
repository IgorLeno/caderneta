package com.example.caderneta.domain.foto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClientePhotoStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun photoNameForUsesStableClienteIdName() {
        val store = ClientePhotoStore(context)

        assertEquals("cliente_42.jpg", store.photoNameFor(42))
    }

    @Test
    fun rejectsPathTraversalNames() {
        assertFalse(ClientePhotoStore.isValidPhotoName("../cliente_1.jpg"))
        assertFalse(ClientePhotoStore.isValidPhotoName("cliente_1.png"))
        assertFalse(ClientePhotoStore.isValidPhotoName("cliente_0.jpg"))
        assertTrue(ClientePhotoStore.isValidPhotoName("cliente_1.jpg"))
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
