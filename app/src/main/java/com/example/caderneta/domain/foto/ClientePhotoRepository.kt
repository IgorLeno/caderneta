package com.example.caderneta.domain.foto

import android.net.Uri
import com.example.caderneta.data.entity.Cliente
import java.io.File

interface ClientePhotoClientStore {
    suspend fun getClienteById(id: Long): Cliente?

    suspend fun updateCliente(cliente: Cliente)
}

class ClientePhotoRepository(
    private val clienteRepository: ClientePhotoClientStore,
    private val store: ClientePhotoStore,
    private val processor: PhotoProcessor,
) {
    suspend fun salvarFotoCliente(
        clienteId: Long,
        uri: Uri,
    ): String {
        val cliente = requireNotNull(clienteRepository.getClienteById(clienteId)) { "Cliente não encontrado" }
        val previousFotoNome = cliente.fotoNome
        val fotoNome = store.newPhotoNameFor(clienteId)
        try {
            val bytes = processor.process(uri)
            store.writeAtomic(fotoNome, bytes)
            clienteRepository.updateCliente(cliente.copy(fotoNome = fotoNome))
            store.delete(previousFotoNome)
        } catch (
            @Suppress("TooGenericExceptionCaught") exception: Exception,
        ) {
            store.delete(fotoNome)
            throw exception
        } finally {
            store.deleteInternalCaptureTemp(uri)
        }
        return fotoNome
    }

    suspend fun removerFoto(clienteId: Long) {
        val cliente = requireNotNull(clienteRepository.getClienteById(clienteId)) { "Cliente não encontrado" }
        val fotoNome = cliente.fotoNome ?: return
        clienteRepository.updateCliente(cliente.copy(fotoNome = null))
        store.delete(fotoNome)
    }

    fun arquivo(fotoNome: String?): File? = store.existingPhotoFile(fotoNome)

    fun deleteFile(fotoNome: String?) {
        store.delete(fotoNome)
    }
}
