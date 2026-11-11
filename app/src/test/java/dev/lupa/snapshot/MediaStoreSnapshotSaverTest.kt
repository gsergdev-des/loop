package dev.lupa.snapshot

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Хранилище медиа для теста: строки в памяти, файлы во временной папке. */
class FakeMediaProvider : ContentProvider() {
    class Row(val values: ContentValues, val file: File, val history: MutableList<ContentValues> = mutableListOf())

    override fun onCreate() = true

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        if (rejectInsert) return null
        val id = rows.size + 1L
        val file = File.createTempFile("media$id", ".jpg")
        rows[id] = Row(ContentValues(values), file).also { it.history += ContentValues(values) }
        return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int {
        val row = rows[uri.lastPathSegment!!.toLong()] ?: return 0
        row.values.putAll(values!!)
        row.history += ContentValues(values)
        return 1
    }

    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int =
        if (rows.remove(uri.lastPathSegment!!.toLong()) != null) 1 else 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val row = rows.getValue(uri.lastPathSegment!!.toLong())
        return ParcelFileDescriptor.open(row.file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null

    companion object {
        val rows = linkedMapOf<Long, Row>()
        var rejectInsert = false
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaStoreSnapshotSaverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        FakeMediaProvider.rows.clear()
        FakeMediaProvider.rejectInsert = false
        Robolectric.buildContentProvider(FakeMediaProvider::class.java).create(MediaStore.AUTHORITY)
    }

    private fun bitmap() = Bitmap.createBitmap(30, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF336699.toInt()) }

    @Test
    fun `save puts a finished jpeg into Pictures slash Lupa`() = runBlocking {
        val result = MediaStoreSnapshotSaver(context).save(bitmap())

        assertTrue(result.exceptionOrNull().toString(), result.isSuccess)
        val row = FakeMediaProvider.rows.values.single()
        assertEquals("Pictures/Lupa/", row.values.getAsString(MediaStore.MediaColumns.RELATIVE_PATH))
        assertEquals("image/jpeg", row.values.getAsString(MediaStore.MediaColumns.MIME_TYPE))
        assertTrue(
            row.values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME),
            Regex("Lupa_\\d{8}_\\d{6}\\.jpg").matches(row.values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME)),
        )
        // Пока писалось — «в процессе», потом снято.
        assertEquals(1, row.history.first().getAsInteger(MediaStore.MediaColumns.IS_PENDING))
        assertEquals(0, row.values.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
        val decoded = BitmapFactory.decodeFile(row.file.path)
        assertNotNull("в файле не JPEG", decoded)
        assertEquals(30, decoded.width)
        assertEquals(20, decoded.height)
    }

    @Test
    fun `save fails cleanly when the gallery refuses the new picture`() = runBlocking {
        FakeMediaProvider.rejectInsert = true

        val result = MediaStoreSnapshotSaver(context).save(bitmap())

        assertTrue(result.isFailure)
        assertTrue(FakeMediaProvider.rows.isEmpty())
    }
}
