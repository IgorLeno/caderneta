package com.example.caderneta.domain.foto

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.UUID

class ClientePhotoStore(
    context: Context,
) {
    private val photosDir = File(context.filesDir, PHOTO_DIR).apply { mkdirs() }
    private val captureDir = File(context.cacheDir, PHOTO_CAPTURE_DIR)
    private val fileProviderAuthority = "${context.packageName}.fileprovider"

    fun newPhotoNameFor(clienteId: Long): String {
        require(clienteId > 0) { "Cliente inválido para foto" }
        return "cliente_${clienteId}_${UUID.randomUUID()}.jpg"
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
            temp.deleteOrLog()
            error("Não foi possível salvar a foto")
        }
        return target
    }

    fun delete(fotoNome: String?): Boolean = photoFile(fotoNome)?.deleteOrLog() ?: true

    /**
     * Remove fotos órfãs após uma restauração: apaga apenas arquivos com nome de foto válido
     * que não estejam em [referenciadas]. Arquivos com nomes fora do padrão nunca são tocados.
     */
    fun deleteUnreferenced(referenciadas: Set<String>): List<String> {
        val falhas = mutableListOf<String>()
        photosDir.listFiles()?.forEach { file ->
            if (PHOTO_NAME_REGEX.matches(file.name) && file.name !in referenciadas && !file.deleteOrLog()) {
                falhas += file.name
            }
        }
        return falhas
    }

    fun findUnreferenced(referenciadas: Set<String>): List<String> =
        photosDir
            .listFiles()
            ?.map { it.name }
            ?.filter { PHOTO_NAME_REGEX.matches(it) && it !in referenciadas }
            ?.sorted()
            .orEmpty()

    fun deleteInternalCaptureTemp(uri: Uri): Boolean {
        if (!isInternalCaptureTemp(uri)) return true
        val fileName = uri.pathSegments.getOrNull(1)
        return if (fileName != null && isValidCaptureTempName(fileName)) {
            File(captureDir, fileName).deleteOrLog()
        } else {
            true
        }
    }

    private fun isInternalCaptureTemp(uri: Uri): Boolean =
        uri.scheme == "content" &&
            uri.authority == fileProviderAuthority &&
            uri.pathSegments.firstOrNull() == PHOTO_CAPTURE_DIR

    private fun isValidCaptureTempName(fileName: String): Boolean =
        fileName.startsWith(CAPTURE_PREFIX) && fileName.endsWith(CAPTURE_SUFFIX)

    private fun File.deleteOrLog(): Boolean {
        if (!exists()) return true
        val deleted = delete()
        if (!deleted) {
            Log.w(TAG, "Não foi possível apagar arquivo de foto: $name")
        }
        return deleted
    }

    companion object {
        private const val TAG = "ClientePhotoStore"
        const val PHOTO_DIR = "client_photos"
        const val PHOTO_CAPTURE_DIR = "photo_capture"
        private const val CAPTURE_PREFIX = "cliente_"
        private const val CAPTURE_SUFFIX = ".jpg"
        private val PHOTO_NAME_REGEX = Regex("cliente_[1-9][0-9]*(_[A-Za-z0-9-]+)?\\.jpg")

        fun isValidPhotoName(fotoNome: String): Boolean = PHOTO_NAME_REGEX.matches(fotoNome)
    }
}
