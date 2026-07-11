package com.example.caderneta.domain.foto

import android.content.Context
import java.io.File

class ClientePhotoStore(
    context: Context,
) {
    private val photosDir = File(context.filesDir, PHOTO_DIR).apply { mkdirs() }

    fun photoNameFor(clienteId: Long): String {
        require(clienteId > 0) { "Cliente inválido para foto" }
        return "cliente_$clienteId.jpg"
    }

    fun photoFile(fotoNome: String?): File? {
        if (fotoNome.isNullOrBlank()) return null
        require(isValidPhotoName(fotoNome)) { "Nome de foto inválido" }
        return File(photosDir, fotoNome)
    }

    fun existingPhotoFile(fotoNome: String?): File? = photoFile(fotoNome)?.takeIf { it.exists() }

    fun writeAtomic(
        fotoNome: String,
        bytes: ByteArray,
    ): File {
        require(isValidPhotoName(fotoNome)) { "Nome de foto inválido" }
        require(bytes.isNotEmpty()) { "Foto vazia" }
        photosDir.mkdirs()
        val target = File(photosDir, fotoNome)
        val temp = File(photosDir, "$fotoNome.tmp")
        temp.outputStream().use { it.write(bytes) }
        if (!temp.renameTo(target)) {
            temp.delete()
            error("Não foi possível salvar a foto")
        }
        return target
    }

    fun delete(fotoNome: String?) {
        photoFile(fotoNome)?.delete()
    }

    /**
     * Remove fotos órfãs após uma restauração: apaga apenas arquivos com nome de foto válido
     * que não estejam em [referenciadas]. Arquivos com nomes fora do padrão nunca são tocados.
     */
    fun deleteUnreferenced(referenciadas: Set<String>) {
        photosDir.listFiles()?.forEach { file ->
            if (PHOTO_NAME_REGEX.matches(file.name) && file.name !in referenciadas) {
                file.delete()
            }
        }
    }

    companion object {
        const val PHOTO_DIR = "client_photos"
        private val PHOTO_NAME_REGEX = Regex("cliente_[1-9][0-9]*\\.jpg")

        fun isValidPhotoName(fotoNome: String): Boolean = PHOTO_NAME_REGEX.matches(fotoNome)
    }
}
