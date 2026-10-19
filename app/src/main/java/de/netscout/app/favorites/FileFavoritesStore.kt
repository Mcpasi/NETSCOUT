package de.netscout.app.favorites

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

/** Private app storage; AtomicFile preserves the previous collection if a write is interrupted. */
class FileFavoritesStore(private val file: AtomicFile) : FavoritesStore {
    constructor(context: Context) : this(AtomicFile(File(context.filesDir, "favorites.json")))

    override suspend fun load(): List<Favorite> = withContext(Dispatchers.IO) {
        try {
            file.openRead().bufferedReader(Charsets.UTF_8).use { FavoritesCodec.decode(it.readText()) }
        } catch (missing: FileNotFoundException) {
            if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw missing
            emptyList()
        }
    }

    override suspend fun save(favorites: List<Favorite>) = withContext(Dispatchers.IO) {
        val bytes = FavoritesCodec.encode(favorites).toByteArray(Charsets.UTF_8)
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
    }
}
