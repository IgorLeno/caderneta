package com.example.caderneta.domain.foto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max

fun interface PhotoProcessor {
    suspend fun process(uri: Uri): ByteArray
}

class ClientePhotoProcessor(
    private val context: Context,
) : PhotoProcessor {
    override suspend fun process(uri: Uri): ByteArray =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Imagem não encontrada" }
                try {
                    BitmapFactory.decodeStream(input, null, options)
                } catch (e: OutOfMemoryError) {
                    throw IllegalArgumentException("Imagem muito grande", e)
                } catch (e: IOException) {
                    if (options.outWidth <= 0 || options.outHeight <= 0) {
                        throw IllegalArgumentException("Arquivo de imagem inválido", e)
                    }
                }
            }
            require(options.outWidth > 0 && options.outHeight > 0) { "Arquivo de imagem inválido" }
            require(options.outWidth.toLong() * options.outHeight.toLong() <= MAX_INPUT_PIXELS) {
                "Imagem muito grande"
            }

            val sampleSize = calculateSampleSize(options.outWidth, options.outHeight)
            val bitmapOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded =
                resolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Imagem não encontrada" }
                    try {
                        requireNotNull(BitmapFactory.decodeStream(input, null, bitmapOptions)) {
                            "Não foi possível ler a imagem"
                        }
                    } catch (e: OutOfMemoryError) {
                        throw IllegalArgumentException("Imagem muito grande", e)
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
        while (max(scaledWidth, scaledHeight) > MAX_DIMENSION) {
            sampleSize *= 2
            scaledWidth /= 2
            scaledHeight /= 2
        }
        return sampleSize
    }

    private fun readOrientation(uri: Uri): Int =
        if (uri.scheme == "file" && uri.path != null) {
            ExifInterface(uri.path!!).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } else {
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
        }

    private fun rotateIfNeeded(
        bitmap: Bitmap,
        orientation: Int,
    ): Bitmap {
        if (isOriginalOrientation(orientation)) {
            return bitmap
        }

        val width = bitmap.width
        val height = bitmap.height
        val outputWidth = if (swapsDimensions(orientation)) height else width
        val outputHeight = if (swapsDimensions(orientation)) width else height
        val sourcePixels = IntArray(width * height)
        val outputPixels = IntArray(outputWidth * outputHeight)
        bitmap.getPixels(sourcePixels, 0, width, 0, 0, width, height)

        for (y in 0 until outputHeight) {
            for (x in 0 until outputWidth) {
                outputPixels[y * outputWidth + x] =
                    sourcePixels[sourceIndexForOrientation(orientation, x, y, width, height)]
            }
        }

        return Bitmap.createBitmap(outputPixels, outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
    }

    private fun isOriginalOrientation(orientation: Int): Boolean =
        orientation == ExifInterface.ORIENTATION_NORMAL ||
            orientation == ExifInterface.ORIENTATION_UNDEFINED

    private fun swapsDimensions(orientation: Int): Boolean = orientation in SWAPPING_ORIENTATIONS

    private fun sourceIndexForOrientation(
        orientation: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ): Int =
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> y * width + (width - 1 - x)
            ExifInterface.ORIENTATION_ROTATE_180 -> (height - 1 - y) * width + (width - 1 - x)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> (height - 1 - y) * width + x
            ExifInterface.ORIENTATION_TRANSPOSE -> x * width + y
            ExifInterface.ORIENTATION_ROTATE_90 -> (height - 1 - x) * width + y
            ExifInterface.ORIENTATION_TRANSVERSE -> (height - 1 - x) * width + (width - 1 - y)
            ExifInterface.ORIENTATION_ROTATE_270 -> x * width + (width - 1 - y)
            else -> y * width + x
        }

    companion object {
        private const val MAX_DIMENSION = 1024
        private const val MAX_INPUT_PIXELS = 40_000_000L
        private const val JPEG_QUALITY = 85
        private val SWAPPING_ORIENTATIONS =
            setOf(
                ExifInterface.ORIENTATION_TRANSPOSE,
                ExifInterface.ORIENTATION_ROTATE_90,
                ExifInterface.ORIENTATION_TRANSVERSE,
                ExifInterface.ORIENTATION_ROTATE_270,
            )
    }
}
