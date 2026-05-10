package com.securepass.vision.utils

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.exifinterface.media.ExifInterface
import com.securepass.vision.model.FrameMetadata
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer

/** Funciones de utilidad para conversiones de mapas de bits (bitmaps). */
object BitmapUtils {
    private const val TAG = "BitmapUtils"

    /** Convierte un búfer de bytes en formato NV21 a un bitmap. */
    fun getBitmap(data: ByteBuffer, metadata: FrameMetadata): Bitmap? {
        data.rewind()
        val imageInBuffer = ByteArray(data.limit())
        data.get(imageInBuffer, 0, imageInBuffer.size)
        return try {
            val image = YuvImage(
                imageInBuffer, ImageFormat.NV21, metadata.width, metadata.height, null
            )
            val stream = ByteArrayOutputStream()
            image.compressToJpeg(Rect(0, 0, metadata.width, metadata.height), 80, stream)
            val bmp = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size())
            stream.close()
            rotateBitmap(bmp, metadata.rotation, flipX = false, flipY = false)
        } catch (e: Exception) {
            Log.e("VisionProcessorBase", "Error: ${e.message}")
            null
        }
    }

    /** Convierte una imagen YUV_420_888 de la API CameraX a un bitmap. */
    @ExperimentalGetImage
    fun getBitmap(image: ImageProxy): Bitmap? {
        val frameMetadata = FrameMetadata.Builder()
            .setWidth(image.width)
            .setHeight(image.height)
            .setRotation(image.imageInfo.rotationDegrees)
            .build()
        val imageActual = image.image ?: return null
        val nv21Buffer = yuv420ThreePlanesToNV21(imageActual.planes, image.width, image.height)
        return getBitmap(nv21Buffer, frameMetadata)
    }

    /** Rota un bitmap si se convirtió desde un búfer de bytes. */
    private fun rotateBitmap(
        bitmap: Bitmap, rotationDegrees: Int, flipX: Boolean, flipY: Boolean
    ): Bitmap {
        val matrix = Matrix()

        // Rotate the image back to straight.
        matrix.postRotate(rotationDegrees.toFloat())

        // Mirror the image along the X or Y axis.
        matrix.postScale(if (flipX) -1.0f else 1.0f, if (flipY) -1.0f else 1.0f)
        val rotatedBitmap =
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        // Recycle the old bitmap if it has changed.
        if (rotatedBitmap != bitmap) {
            bitmap.recycle()
        }
        return rotatedBitmap
    }

    @Throws(IOException::class)
    fun getBitmapFromContentUri(contentResolver: ContentResolver, imageUri: Uri): Bitmap? {
        val decodedBitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri) ?: return null
        val orientation = getExifOrientationTag(contentResolver, imageUri)
        var rotationDegrees = 0
        var flipX = false
        var flipY = false

        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipX = true
            ExifInterface.ORIENTATION_ROTATE_90 -> rotationDegrees = 90
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                rotationDegrees = 90
                flipX = true
            }
            ExifInterface.ORIENTATION_ROTATE_180 -> rotationDegrees = 180
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> flipY = true
            ExifInterface.ORIENTATION_ROTATE_270 -> rotationDegrees = -90
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                rotationDegrees = -90
                flipX = true
            }
            ExifInterface.ORIENTATION_UNDEFINED, ExifInterface.ORIENTATION_NORMAL -> {}
            else -> {}
        }

        return rotateBitmap(decodedBitmap, rotationDegrees, flipX, flipY)
    }

    private fun getExifOrientationTag(resolver: ContentResolver, imageUri: Uri): Int {
        if (ContentResolver.SCHEME_CONTENT != imageUri.scheme && ContentResolver.SCHEME_FILE != imageUri.scheme) {
            return 0
        }

        return try {
            resolver.openInputStream(imageUri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: 0
        } catch (e: IOException) {
            Log.e(TAG, "failed to open file to read rotation meta data: $imageUri", e)
            0
        }
    }

    private fun yuv420ThreePlanesToNV21(
        yuv420888planes: Array<android.media.Image.Plane>, width: Int, height: Int
    ): ByteBuffer {
        val imageSize = width * height
        val out = ByteArray(imageSize + 2 * (imageSize / 4))

        if (areUVPlanesNV21(yuv420888planes, width, height)) {
            yuv420888planes[0].buffer.get(out, 0, imageSize)
            val uBuffer = yuv420888planes[1].buffer
            val vBuffer = yuv420888planes[2].buffer
            vBuffer.get(out, imageSize, 1)
            uBuffer.get(out, imageSize + 1, 2 * imageSize / 4 - 1)
        } else {
            unpackPlane(yuv420888planes[0], width, height, out, 0, 1)
            unpackPlane(yuv420888planes[1], width, height, out, imageSize + 1, 2)
            unpackPlane(yuv420888planes[2], width, height, out, imageSize, 2)
        }

        return ByteBuffer.wrap(out)
    }

    private fun areUVPlanesNV21(planes: Array<android.media.Image.Plane>, width: Int, height: Int): Boolean {
        val imageSize = width * height
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val vBufferPosition = vBuffer.position()
        val uBufferLimit = uBuffer.limit()

        vBuffer.position(vBufferPosition + 1)
        uBuffer.limit(uBufferLimit - 1)

        val areNV21 = (vBuffer.remaining() == (2 * imageSize / 4 - 2)) && (vBuffer.compareTo(uBuffer) == 0)

        vBuffer.position(vBufferPosition)
        uBuffer.limit(uBufferLimit)

        return areNV21
    }

    private fun unpackPlane(
        plane: android.media.Image.Plane, width: Int, height: Int, out: ByteArray, offset: Int, pixelStride: Int
    ) {
        val buffer = plane.buffer
        buffer.rewind()

        val numRow = (buffer.limit() + plane.rowStride - 1) / plane.rowStride
        if (numRow == 0) return
        val scaleFactor = height / numRow
        val numCol = width / scaleFactor

        var outputPos = offset
        var rowStart = 0
        for (row in 0 until numRow) {
            var inputPos = rowStart
            for (col in 0 until numCol) {
                out[outputPos] = buffer.get(inputPos)
                outputPos += pixelStride
                inputPos += plane.pixelStride
            }
            rowStart += plane.rowStride
        }
    }
}
