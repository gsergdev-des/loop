package dev.lupa.ui

import android.graphics.Bitmap
import android.net.Uri
import dev.lupa.snapshot.SnapshotSaver
import kotlinx.coroutines.CompletableDeferred

/** Сохранение для тестов: запоминает снимки, результат и задержку задаёт тест. */
class FakeSnapshotSaver : SnapshotSaver {
    val saved = mutableListOf<Bitmap>()

    /** Если задан, [save] ждёт его: запись «идёт», пока тест не завершит. */
    var gate: CompletableDeferred<Unit>? = null
    var failure: Throwable? = null

    override suspend fun save(bitmap: Bitmap): Result<Uri> {
        saved += bitmap
        gate?.await()
        failure?.let { return Result.failure(it) }
        return Result.success(Uri.parse("content://media/external/images/media/1"))
    }
}
