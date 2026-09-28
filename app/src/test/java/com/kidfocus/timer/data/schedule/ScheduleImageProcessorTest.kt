package com.kidfocus.timer.data.schedule

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import org.robolectric.RuntimeEnvironment
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScheduleImageProcessorTest {
    private val context = RuntimeEnvironment.getApplication()
    private val processor = ScheduleImageProcessor(context)
    private val sources = mutableListOf<File>()
    @After fun cleanup() { processor.close(); sources.forEach { it.delete() } }
    private fun source(width: Int, height: Int, orientation: Int = ExifInterface.ORIENTATION_NORMAL, noisy: Boolean = false): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val random = Random(23)
        for (y in 0 until height) for (x in 0 until width) bitmap.setPixel(x, y,
            if (noisy) Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)) else if (x < width / 2) Color.RED else Color.BLUE)
        val file = File.createTempFile("source-", ".jpg", context.cacheDir).also { sources.add(it) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            setAttribute(ExifInterface.TAG_MAKE, "PRIVATE_CAMERA")
            setAttribute(ExifInterface.TAG_USER_COMMENT, "PRIVATE_NAME")
            setLatLong(10.77, 106.69)
            saveAttributes()
        }
        return file
    }
    @Test fun `EXIF rotates pixels and fresh JPEG removes GPS camera and comments`() = runTest {
        val input = source(80, 40, ExifInterface.ORIENTATION_ROTATE_90)
        assertNotNull(ExifInterface(input).getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        val image = processor.prepare(Uri.fromFile(input))
        assertEquals(40, image.width); assertEquals(80, image.height)
        val output = BitmapFactory.decodeFile(image.file.path)
        val top = output.getPixel(20, 10); val bottom = output.getPixel(20, 70)
        assertTrue(Color.red(top) > 200 && Color.blue(top) < 50)
        assertTrue(Color.blue(bottom) > 200 && Color.red(bottom) < 50)
        output.recycle()
        val exif = ExifInterface(image.file)
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_MAKE))
        assertNull(exif.getAttribute(ExifInterface.TAG_USER_COMMENT))
        assertEquals(ExifInterface.ORIENTATION_UNDEFINED, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
        processor.delete(image); assertFalse(image.file.exists())
        assertTrue(input.exists()) // The library original is never changed/deleted.
    }
    @Test fun `all eight EXIF orientations preserve dimensions and mirrored horizontal pixels`() = runTest {
        for (orientation in 1..8) {
            val image = processor.prepare(Uri.fromFile(source(80, 40, orientation)))
            assertEquals(if (orientation >= 5) 40 else 80, image.width)
            assertEquals(if (orientation >= 5) 80 else 40, image.height)
            if (orientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL) {
                val decoded = BitmapFactory.decodeFile(image.file.path)
                assertTrue(Color.blue(decoded.getPixel(10, 20)) > 200)
                assertTrue(Color.red(decoded.getPixel(70, 20)) > 200)
                decoded.recycle()
            }
            processor.delete(image)
        }
    }
    @Test fun `large noisy source downscales and bounds JPEG and base64 size`() = runTest {
        val image = processor.prepare(Uri.fromFile(source(3300, 1700, noisy = true)))
        assertEquals(1600, image.width); assertTrue(image.height <= 1600)
        assertTrue(image.file.length() in 1..1_000_000L)
        val base64 = processor.base64(image)
        assertTrue(base64.length <= 1_400_000)
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        assertEquals(255, bytes[0].toInt() and 255); assertEquals(216, bytes[1].toInt() and 255)
        assertEquals(image.file.length(), bytes.size.toLong())
    }
    @Test fun `high detail JPEG exceeding one MB is recompressed to the limit`() = runTest {
        val input = source(1600, 1600, noisy = true)
        val decoded = BitmapFactory.decodeFile(input.path)
        val atEighty = java.io.ByteArrayOutputStream()
        decoded.compress(Bitmap.CompressFormat.JPEG, 80, atEighty)
        decoded.recycle()
        assertTrue(atEighty.size() > ScheduleImageProcessor.MAX_BYTES)
        val image = processor.prepare(Uri.fromFile(input))
        assertTrue(image.file.length() <= ScheduleImageProcessor.MAX_BYTES)
        assertEquals(1600, image.width); assertEquals(1600, image.height)
    }
    @Test fun `crop creates fresh bounded JPEG and retains only requested pixels`() = runTest {
        val image = processor.prepare(Uri.fromFile(source(80, 40)))
        val clipped = processor.crop(image, ScheduleCrop(0.5f, 0f, 1f, 1f))
        assertEquals(40, clipped.width); assertEquals(40, clipped.height)
        val pixels = BitmapFactory.decodeFile(clipped.file.path)
        assertTrue(Color.blue(pixels.getPixel(20, 20)) > 200)
        pixels.recycle()
        assertNull(ExifInterface(clipped.file).getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertTrue(clipped.file.length() <= ScheduleImageProcessor.MAX_BYTES)
        assertTrue(image.file.exists())
    }
    @Test fun `invalid decode and crop do not leave new image files`() = runTest {
        val invalid = File.createTempFile("invalid-", ".jpg", context.cacheDir).also { sources.add(it); it.writeText("invalid") }
        val before = File(context.cacheDir, "schedule_images").walkTopDown().filter { it.isFile }.count()
        assertTrue(runCatching { processor.prepare(Uri.fromFile(invalid)) }.isFailure)
        assertEquals(before, File(context.cacheDir, "schedule_images").walkTopDown().filter { it.isFile }.count())
        val image = processor.prepare(Uri.fromFile(source(80, 40)))
        assertTrue(runCatching { processor.crop(image, ScheduleCrop(Float.NaN, 0f, 1f, 1f)) }.isFailure)
    }
    @Test fun `capture cancellation and closing clean owned caches and abandoned sessions`() = runTest {
        val abandoned = File(context.cacheDir, "schedule_capture/abandoned/capture.jpg").apply { parentFile!!.mkdirs(); writeText("private") }
        val other = ScheduleImageProcessor(context)
        assertFalse(abandoned.exists())
        val image = processor.prepare(Uri.fromFile(source(80, 40)))
        other.close(); assertTrue(image.file.exists())
        val capture = processor.createCapture()
        assertEquals("content", capture.uri.scheme)
        assertTrue(capture.file.exists())
        processor.deleteCapture(capture); assertFalse(capture.file.exists())
        val next = processor.createCapture()
        processor.close()
        assertFalse(next.file.exists()); assertFalse(image.file.exists())
    }
}
