package com.kidfocus.timer.data.schedule

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

// Only re-encoded pixels reach this file. No source URI or EXIF is sent to the backend.
data class ScheduleImage(val file: File, val width: Int, val height: Int)
data class ScheduleCrop(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    fun validate() {
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite())
        require(left >= 0 && top >= 0 && right <= 1 && bottom <= 1 && right > left && bottom > top)
    }
}
data class ScheduleCapture(val file: File, val uri: Uri)

class ScheduleImageProcessor @Inject constructor(@ApplicationContext private val context: Context) {
    private val owner = UUID.randomUUID().toString()
    init {
        synchronized(activeOwners) {
            activeOwners.add(owner)
            // A new process has no active owners: remove files abandoned by process death.
            listOf("schedule_capture", "schedule_images").forEach { name ->
                File(context.cacheDir, name).listFiles()?.filter { it.name !in activeOwners }?.forEach { it.deleteRecursively() }
            }
        }
    }
    private fun directory(name: String) = File(File(context.cacheDir, name), owner).apply { mkdirs() }
    fun close() {
        synchronized(activeOwners) {
            activeOwners.remove(owner)
            listOf("schedule_capture", "schedule_images").forEach { File(File(context.cacheDir, it), owner).deleteRecursively() }
        }
    }
    fun createCapture(): ScheduleCapture {
        val file = File.createTempFile("capture-", ".jpg", directory("schedule_capture"))
        return try { ScheduleCapture(file, FileProvider.getUriForFile(context, "${context.packageName}.schedule-images", file)) }
        catch (error: Exception) { file.delete(); throw error }
    }
    fun delete(image: ScheduleImage?) { image?.file?.delete() }
    fun deleteCapture(capture: ScheduleCapture?) { capture?.file?.delete() }

    suspend fun prepare(uri: Uri): ScheduleImage = ioImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 3200) sample *= 2
        val orientation = context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("IMAGE_DECODE_FAILED")
        val matrix = orientationMatrix(orientation)
        val longEdge = max(decoded.width, decoded.height)
        if (longEdge > MAX_EDGE) {
            val scale = MAX_EDGE.toFloat() / longEdge
            matrix.postScale(scale, scale) // Rotate and resize together, avoiding another large bitmap.
        }
        try {
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (rotated !== decoded) decoded.recycle()
            encode(rotated)
        } finally { if (!decoded.isRecycled) decoded.recycle() }
    }

    suspend fun crop(image: ScheduleImage, crop: ScheduleCrop): ScheduleImage = ioImage {
        crop.validate()
        val bitmap = BitmapFactory.decodeFile(image.file.path) ?: error("IMAGE_DECODE_FAILED")
        try {
            val left = (crop.left * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
            val top = (crop.top * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
            val right = (crop.right * bitmap.width).roundToInt().coerceIn(left + 1, bitmap.width)
            val bottom = (crop.bottom * bitmap.height).roundToInt().coerceIn(top + 1, bitmap.height)
            val clipped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
            if (clipped === bitmap) encode(bitmap.copy(Bitmap.Config.ARGB_8888, false)) else encode(clipped)
        } finally { bitmap.recycle() }
    }

    suspend fun base64(image: ScheduleImage): String = withContext(Dispatchers.IO) {
        require(image.file.length() in 1..MAX_BYTES.toLong())
        Base64.encodeToString(image.file.readBytes(), Base64.NO_WRAP)
    }

    private suspend fun ioImage(block: () -> ScheduleImage): ScheduleImage {
        var created: ScheduleImage? = null
        try { return withContext(Dispatchers.IO) { block().also { created = it } } }
        catch (error: Throwable) { delete(created); throw error }
    }

    private fun encode(source: Bitmap): ScheduleImage {
        var bitmap = source
        var output: File? = null
        try {
            if (max(bitmap.width, bitmap.height) > MAX_EDGE) {
                val scale = MAX_EDGE.toDouble() / max(bitmap.width, bitmap.height)
                val smaller = bitmap.scale((bitmap.width * scale).roundToInt().coerceAtLeast(1),
                    (bitmap.height * scale).roundToInt().coerceAtLeast(1), true)
                if (smaller !== bitmap) bitmap.recycle()
                bitmap = smaller
            }
            var quality = 80
            while (true) {
                val stream = ByteArrayOutputStream()
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream))
                if (stream.size() <= MAX_BYTES) {
                    output = File.createTempFile("image-", ".jpg", directory("schedule_images"))
                    output.writeBytes(stream.toByteArray())
                    return ScheduleImage(output, bitmap.width, bitmap.height)
                }
                if (quality > 50) quality -= 10
                else {
                    val smaller = bitmap.scale((bitmap.width * 0.8).roundToInt().coerceAtLeast(1),
                        (bitmap.height * 0.8).roundToInt().coerceAtLeast(1), true)
                    check(smaller !== bitmap)
                    bitmap.recycle(); bitmap = smaller; quality = 80
                }
            }
        } catch (error: Throwable) { output?.delete(); throw error }
        finally { bitmap.recycle() }
    }

    companion object {
        private val activeOwners = ConcurrentHashMap.newKeySet<String>()
        const val MAX_BYTES = 1_000_000
        const val MAX_EDGE = 1600
        internal fun orientationMatrix(orientation: Int) = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }
    }
}
