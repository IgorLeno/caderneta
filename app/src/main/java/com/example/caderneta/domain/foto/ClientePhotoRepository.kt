package com.example.caderneta.domain.foto

import android.database.SQLException
import android.net.Uri
import com.example.caderneta.repository.ClienteRepository
import java.io.File

class ClientePhotoRepository(
    private val clienteRepository: ClienteRepository,
    private val store: ClientePhotoStore,
    private val processor: ClientePhotoProcessor,
) {
    suspend fun salvarFotoCliente(
        clienteId: Long,
        uri: Uri,
    ): String {
        val cliente = requireNotNull(clienteRepository.getClienteById(clienteId)) { "Cliente não encontrado" }
        val fotoNome = store.photoNameFor(clienteId)
        val bytes = processor.process(uri)
        val previousFotoNome = cliente.fotoNome
        store.writeAtomic(fotoNome, bytes)
        try {
            clienteRepository.updateCliente(cliente.copy(fotoNome = fotoNome))
        } catch (e: SQLException) {
            if (previousFotoNome == null) store.delete(fotoNome)
            throw e
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
}
