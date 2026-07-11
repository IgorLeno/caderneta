package com.example.caderneta.domain.foto

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

interface ClientePhotoSource {
    suspend fun pickImage(context: Context): Uri?

    suspend fun captureImage(context: Context): Uri?
}

object ProductionClientePhotoSource : ClientePhotoSource {
    override suspend fun pickImage(context: Context): Uri? = null

    override suspend fun captureImage(context: Context): Uri? = null
}

class AuditClientePhotoSource : ClientePhotoSource {
    override suspend fun pickImage(context: Context): Uri = createFixtureUri(context, "pick")

    override suspend fun captureImage(context: Context): Uri = createFixtureUri(context, "capture")

    private suspend fun createFixtureUri(
        context: Context,
        label: String,
    ): Uri =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, AUDIT_PHOTO_DIR).apply { mkdirs() }
            dir.listFiles()?.forEach { file -> if (file.isFile) file.delete() }

            val file = File(dir, "cliente_${label}_${System.currentTimeMillis()}.jpg")
            val bitmap = Bitmap.createBitmap(FIXTURE_SIZE_PX, FIXTURE_SIZE_PX, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(if (label == "capture") CAPTURE_COLOR else PICK_COLOR)
                file.outputStream().use { output ->
                    require(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                        "Não foi possível gerar foto de auditoria"
                    }
                }
            } finally {
                bitmap.recycle()
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        }

    private companion object {
        const val AUDIT_PHOTO_DIR = "audit_photo_fixture"
        const val FIXTURE_SIZE_PX = 24
        const val JPEG_QUALITY = 90
        const val PICK_COLOR = 0xFFFF8247.toInt()
        const val CAPTURE_COLOR = 0xFF4CAF50.toInt()
    }
}
