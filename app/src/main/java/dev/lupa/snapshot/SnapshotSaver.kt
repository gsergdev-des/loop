package dev.lupa.snapshot

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Сохраняет готовый снимок в галерею. */
interface SnapshotSaver {
    suspend fun save(bitmap: Bitmap): Result<Uri>
}

/**
 * Запись через MediaStore в `Pictures/Lupa`: JPEG качества 92, на время записи `IS_PENDING = 1`, чтобы
 * галерея не показала недописанный файл. Разрешение на хранилище не нужно (minSdk 29).
 */
class MediaStoreSnapshotSaver(context: Context) : SnapshotSaver {
    private val resolver = context.applicationContext.contentResolver

    override suspend fun save(bitmap: Bitmap): Result<Uri> = withContext(Dispatchers.IO) {
        var uri: Uri? = null
        try {
            val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "Lupa_$name.jpg")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Lupa/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val target = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("MediaStore не создал запись")
            uri = target
            val stream = resolver.openOutputStream(target) ?: throw IOException("Не открыть файл для записи")
            stream.use {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)) {
                    throw IOException("Не удалось закодировать JPEG")
                }
            }
            resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            Result.success(target)
        } catch (e: CancellationException) {
            discard(uri)
            throw e
        } catch (e: Exception) {
            discard(uri)
            Result.failure(e)
        }
    }

    /** Недописанный файл из галереи убираем, чтобы не остался пустой «снимок». */
    private fun discard(uri: Uri?) {
        if (uri == null) return
        try {
            resolver.delete(uri, null, null)
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val JPEG_QUALITY = 92
    }
}
