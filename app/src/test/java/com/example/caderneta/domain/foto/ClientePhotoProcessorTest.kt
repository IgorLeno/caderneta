package com.example.caderneta.domain.foto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.math.max

@RunWith(RobolectricTestRunner::class)
class ClientePhotoProcessorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val processor = ClientePhotoProcessor(context)

    @Test
    fun processDownsamplesPanoramicImageByLargestDimension() =
        runTest {
            val file = createJpeg("panorama.jpg", width = 6000, height = 600)

            val processed = decode(processor.process(Uri.fromFile(file)))

            assertTrue(max(processed.width, processed.height) <= MAX_DIMENSION)
            assertTrue(processed.width > processed.height)
        }

    @Test
    fun processKeepsLargestDimensionWithinLimitForPortraitAndLandscape() =
        runTest {
            val landscape = decode(processor.process(Uri.fromFile(createJpeg("landscape.jpg", 2400, 1600))))
            val portrait = decode(processor.process(Uri.fromFile(createJpeg("portrait.jpg", 1600, 2400))))

            assertTrue(max(landscape.width, landscape.height) <= MAX_DIMENSION)
            assertTrue(landscape.width > landscape.height)
            assertTrue(max(portrait.width, portrait.height) <= MAX_DIMENSION)
            assertTrue(portrait.height > portrait.width)
        }

    @Test
    fun processAppliesRotateExifOrientation() =
        runTest {
            val file =
                createJpeg(
                    name = "rotate90.jpg",
                    width = 80,
                    height = 40,
                    orientation = ExifInterface.ORIENTATION_ROTATE_90,
                )

            val processed = decode(processor.process(Uri.fromFile(file)))

            assertEquals(40, processed.width)
            assertEquals(80, processed.height)
        }

    @Test
    fun processAppliesHorizontalFlipExifOrientation() =
        runTest {
            val file =
                createJpeg(
                    name = "flip_horizontal.jpg",
                    width = 80,
                    height = 40,
                    orientation = ExifInterface.ORIENTATION_FLIP_HORIZONTAL,
                    splitColors = true,
                )

            val processed = decode(processor.process(Uri.fromFile(file)))

            assertBlueDominant(processed.getPixel(10, processed.height / 2))
            assertRedDominant(processed.getPixel(processed.width - 10, processed.height / 2))
        }

    @Test
    fun processHandlesAllExifOrientations() =
        runTest {
            val expectedSizeByOrientation =
                mapOf(
                    ExifInterface.ORIENTATION_NORMAL to (80 to 40),
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL to (80 to 40),
                    ExifInterface.ORIENTATION_ROTATE_180 to (80 to 40),
                    ExifInterface.ORIENTATION_FLIP_VERTICAL to (80 to 40),
                    ExifInterface.ORIENTATION_TRANSPOSE to (40 to 80),
                    ExifInterface.ORIENTATION_ROTATE_90 to (40 to 80),
                    ExifInterface.ORIENTATION_TRANSVERSE to (40 to 80),
                    ExifInterface.ORIENTATION_ROTATE_270 to (40 to 80),
                )

            expectedSizeByOrientation.forEach { (orientation, expectedSize) ->
                val file =
                    createJpeg(
                        name = "orientation_$orientation.jpg",
                        width = 80,
                        height = 40,
                        orientation = orientation,
                    )
                val processed = decode(processor.process(Uri.fromFile(file)))

                assertEquals("width for orientation $orientation", expectedSize.first, processed.width)
                assertEquals("height for orientation $orientation", expectedSize.second, processed.height)
            }
        }

    @Test
    fun processRejectsHugeImageBeforeFullDecode() =
        runTest {
            val file =
                File(context.cacheDir, "huge_header.jpg").apply {
                    writeBytes(jpegWithPatchedDimensions(width = 8000, height = 8000))
                }

            val failed =
                runCatching {
                    processor.process(Uri.fromFile(file))
                }.exceptionOrNull()

            assertNotNull(failed)
            assertEquals("Imagem muito grande", failed?.message)
        }

    private fun createJpeg(
        name: String,
        width: Int,
        height: Int,
        orientation: Int = ExifInterface.ORIENTATION_NORMAL,
        splitColors: Boolean = false,
    ): File {
        val file = File(context.cacheDir, name)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            if (splitColors) {
                fillSplitColors(bitmap)
            } else {
                bitmap.eraseColor(Color.rgb(40, 120, 200))
            }
            file.outputStream().use { output ->
                require(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output))
            }
        } finally {
            bitmap.recycle()
        }
        if (orientation != ExifInterface.ORIENTATION_NORMAL) {
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
        }
        return file
    }

    private fun fillSplitColors(bitmap: Bitmap) {
        for (x in 0 until bitmap.width) {
            val color = if (x < bitmap.width / 2) Color.RED else Color.BLUE
            for (y in 0 until bitmap.height) {
                bitmap.setPixel(x, y, color)
            }
        }
    }

    private fun decode(bytes: ByteArray): Bitmap =
        requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) {
            "Processed image did not decode"
        }

    private fun assertRedDominant(color: Int) {
        assertTrue(Color.red(color) > Color.blue(color))
    }

    private fun assertBlueDominant(color: Int) {
        assertTrue(
            "Expected blue > red, got red=${Color.red(color)} blue=${Color.blue(color)}",
            Color.blue(color) > Color.red(color),
        )
    }

    private fun jpegWithPatchedDimensions(
        width: Int,
        height: Int,
    ): ByteArray {
        require(width in 1..UShort.MAX_VALUE.toInt())
        require(height in 1..UShort.MAX_VALUE.toInt())

        fun high(value: Int) = ((value ushr 8) and 0xFF).toByte()

        fun low(value: Int) = (value and 0xFF).toByte()

        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val bytes =
            try {
                bitmap.eraseColor(Color.rgb(40, 120, 200))
                bitmapToJpegBytes(bitmap).toMutableList()
            } finally {
                bitmap.recycle()
            }

        var index = 2
        while (index < bytes.size - 9) {
            if (bytes[index] != 0xFF.toByte()) {
                index += 1
                continue
            }
            val marker = bytes[index + 1].toInt() and 0xFF
            if (marker in 0xC0..0xC3) {
                bytes[index + 5] = high(height)
                bytes[index + 6] = low(height)
                bytes[index + 7] = high(width)
                bytes[index + 8] = low(width)
                return bytes.toByteArray()
            }
            val length = ((bytes[index + 2].toInt() and 0xFF) shl 8) or (bytes[index + 3].toInt() and 0xFF)
            index += 2 + length
        }
        error("SOF marker not found")
    }

    private fun bitmapToJpegBytes(bitmap: Bitmap): ByteArray =
        java.io.ByteArrayOutputStream().use { output ->
            require(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output))
            output.toByteArray()
        }

    private companion object {
        const val MAX_DIMENSION = 1024
        const val JPEG_QUALITY = 95
    }
}
