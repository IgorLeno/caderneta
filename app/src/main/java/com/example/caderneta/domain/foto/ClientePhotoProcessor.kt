package com.example.caderneta.domain.foto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class ClientePhotoProcessor(
    private val context: Context,
) {
    suspend fun process(uri: Uri): ByteArray =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Imagem não encontrada" }
                BitmapFactory.decodeStream(input, null, options)
            }
            require(options.outWidth > 0 && options.outHeight > 0) { "Arquivo de imagem inválido" }

            val sampleSize = calculateSampleSize(options.outWidth, options.outHeight)
            val bitmapOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded =
                resolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Imagem não encontrada" }
                    requireNotNull(BitmapFactory.decodeStream(input, null, bitmapOptions)) {
                        "Não foi possível ler a imagem"
                    }
                }

            val rotated = rotateIfNeeded(decoded, readOrientation(uri))
            ByteArrayOutputStream().use { output ->
                require(rotated.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    "Não foi possível compactar a foto"
                }
                if (rotated !== decoded) rotated.recycle()
                decoded.recycle()
                output.toByteArray()
            }
        }

    private fun calculateSampleSize(
        width: Int,
        height: Int,
    ): Int {
        var sampleSize = 1
        var scaledWidth = width
        var scaledHeight = height
        while (scaledWidth / 2 >= MAX_DIMENSION && scaledHeight / 2 >= MAX_DIMENSION) {
            sampleSize *= 2
            scaledWidth /= 2
            scaledHeight /= 2
        }
        return sampleSize
    }

    private fun readOrientation(uri: Uri): Int =
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) {
                ExifInterface.ORIENTATION_NORMAL
            } else {
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }

    private fun rotateIfNeeded(
        bitmap: Bitmap,
        orientation: Int,
    ): Bitmap {
        val degrees =
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> return bitmap
            }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    companion object {
        private const val MAX_DIMENSION = 1024
        private const val JPEG_QUALITY = 85
    }
}
