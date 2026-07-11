package com.example.caderneta.data.backup

import com.example.caderneta.domain.foto.ClientePhotoStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Descreve uma foto embarcada no backup ZIP: nome de arquivo, checksum e tamanho. */
data class FotoManifest(
    val nome: String,
    val sha256: String,
    val tamanho: Long,
)

/** Cabeçalho do backup ZIP v2, separado dos dados lógicos (`data.json`). */
data class BackupManifest(
    val formatVersion: Int,
    val app: String,
    val dbVersion: Int,
    val geradoEmMillis: Long,
    val fotos: List<FotoManifest>,
)

/** Conteúdo já validado de um backup ZIP: JSON de dados + manifesto + bytes das fotos verificadas. */
data class ArchiveContent(
    val dataJson: String,
    val manifest: BackupManifest,
    val fotos: Map<String, ByteArray>,
)

/**
 * Leitura/escrita do container ZIP de backup (formato v2).
 *
 * A escrita e, principalmente, a leitura tratam o arquivo como conteúdo não confiável:
 * nomes de entrada são restritos a uma whitelist (sem path traversal), tamanhos são limitados
 * (guarda contra zip bomb) e cada foto tem checksum SHA-256, MIME (magic JPEG) e nome validados
 * contra o manifesto antes de ser aceita.
 */
class BackupArchive {
    fun write(
        output: OutputStream,
        dataJson: String,
        manifest: BackupManifest,
        fotos: Map<String, ByteArray>,
    ) {
        ZipOutputStream(output).use { zip ->
            zip.putEntry(ENTRY_MANIFEST, manifestToJson(manifest).encodeToByteArray())
            zip.putEntry(ENTRY_DATA, dataJson.encodeToByteArray())
            fotos.forEach { (nome, bytes) -> zip.putEntry("$PHOTO_PREFIX$nome", bytes) }
        }
    }

    fun read(bytes: ByteArray): ArchiveContent {
        val entradas = extrairEntradas(bytes)
        val data = requireNotNull(entradas.dataJson) { "Backup sem dados" }
        val manifest = manifestFromJson(requireNotNull(entradas.manifestJson) { "Backup sem manifesto" })
        verificarFotos(manifest, entradas.fotos)
        return ArchiveContent(data, manifest, entradas.fotos)
    }

    private fun extrairEntradas(bytes: ByteArray): EntradasBrutas {
        val acc = EntradasBrutas()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                adicionarEntrada(zip, entry, acc)
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return acc
    }

    private fun adicionarEntrada(
        zip: ZipInputStream,
        entry: ZipEntry,
        acc: EntradasBrutas,
    ) {
        if (entry.isDirectory) return
        require(++acc.count <= MAX_ENTRIES) { "Backup com entradas em excesso" }
        val nome = validarNomeEntrada(entry.name)
        val conteudo = zip.readLimited(MAX_ENTRY_BYTES)
        acc.totalBytes += conteudo.size
        require(acc.totalBytes <= MAX_TOTAL_BYTES) { "Backup excede o tamanho máximo" }
        when {
            nome == ENTRY_MANIFEST -> acc.manifestJson = conteudo.decodeToString()
            nome == ENTRY_DATA -> acc.dataJson = conteudo.decodeToString()
            else -> acc.fotos[nome.removePrefix(PHOTO_PREFIX)] = conteudo
        }
    }

    private class EntradasBrutas {
        var dataJson: String? = null
        var manifestJson: String? = null
        val fotos = LinkedHashMap<String, ByteArray>()
        var totalBytes = 0L
        var count = 0
    }

    private fun verificarFotos(
        manifest: BackupManifest,
        fotos: Map<String, ByteArray>,
    ) {
        val esperadas = manifest.fotos.associateBy { it.nome }
        require(esperadas.keys == fotos.keys) { "Fotos do backup não correspondem ao manifesto" }
        fotos.forEach { (nome, conteudo) ->
            require(ClientePhotoStore.isValidPhotoName(nome)) { "Nome de foto inválido no backup" }
            require(temAssinaturaJpeg(conteudo)) { "Foto do backup não é JPEG" }
            val entrada = requireNotNull(esperadas[nome])
            require(entrada.tamanho == conteudo.size.toLong()) { "Tamanho de foto divergente" }
            require(entrada.sha256 == sha256Hex(conteudo)) { "Checksum de foto divergente" }
        }
    }

    private fun validarNomeEntrada(nome: String): String {
        require(!nome.contains("..")) { "Entrada de backup com caminho inválido" }
        require(!nome.startsWith("/") && !nome.contains("\\")) { "Entrada de backup com caminho inválido" }
        val permitida =
            nome == ENTRY_MANIFEST ||
                nome == ENTRY_DATA ||
                (nome.startsWith(PHOTO_PREFIX) && !nome.removePrefix(PHOTO_PREFIX).contains("/"))
        require(permitida) { "Entrada de backup não permitida: $nome" }
        return nome
    }

    private fun ZipOutputStream.putEntry(
        nome: String,
        bytes: ByteArray,
    ) {
        putNextEntry(ZipEntry(nome))
        write(bytes)
        closeEntry()
    }

    private fun ZipInputStream.readLimited(limite: Int): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(BUFFER_SIZE)
        var total = 0
        while (true) {
            val lido = read(chunk)
            if (lido < 0) break
            total += lido
            require(total <= limite) { "Entrada de backup excede o tamanho máximo" }
            buffer.write(chunk, 0, lido)
        }
        return buffer.toByteArray()
    }

    private fun manifestToJson(manifest: BackupManifest): String =
        JSONObject()
            .put("formatVersion", manifest.formatVersion)
            .put("app", manifest.app)
            .put("dbVersion", manifest.dbVersion)
            .put("geradoEmMillis", manifest.geradoEmMillis)
            .put(
                "fotos",
                JSONArray(
                    manifest.fotos.map {
                        JSONObject()
                            .put("nome", it.nome)
                            .put("sha256", it.sha256)
                            .put("tamanho", it.tamanho)
                    },
                ),
            ).toString(2)

    private fun manifestFromJson(json: String): BackupManifest {
        val root = JSONObject(json)
        require(root.getInt("formatVersion") == FORMAT_VERSION_ZIP) { "Versão de backup não suportada" }
        val fotosArray = root.getJSONArray("fotos")
        val fotos =
            (0 until fotosArray.length()).map { index ->
                val foto = fotosArray.getJSONObject(index)
                FotoManifest(
                    nome = foto.getString("nome"),
                    sha256 = foto.getString("sha256"),
                    tamanho = foto.getLong("tamanho"),
                )
            }
        return BackupManifest(
            formatVersion = root.getInt("formatVersion"),
            app = root.getString("app"),
            dbVersion = root.getInt("dbVersion"),
            geradoEmMillis = root.getLong("geradoEmMillis"),
            fotos = fotos,
        )
    }

    companion object {
        const val FORMAT_VERSION_ZIP = 2
        const val ENTRY_MANIFEST = "manifest.json"
        const val ENTRY_DATA = "data.json"
        const val PHOTO_PREFIX = "photos/"

        private const val MAX_ENTRY_BYTES = 8 * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 128L * 1024 * 1024
        private const val MAX_ENTRIES = 5_000
        private const val BUFFER_SIZE = 8 * 1024

        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it) }

        private fun temAssinaturaJpeg(bytes: ByteArray): Boolean =
            bytes.size >= 3 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xD8.toByte() &&
                bytes[2] == 0xFF.toByte()
    }
}
