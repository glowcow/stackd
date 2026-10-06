package dev.glowcow.stackd.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class CardRepository(private val dao: CardDao, private val filesDir: File) {

    val cards: Flow<List<Card>> = dao.observeAll()

    fun observe(id: String): Flow<Card?> = dao.observe(id)

    suspend fun all(): List<Card> = dao.all()

    suspend fun get(id: String): Card? = dao.get(id)

    suspend fun findPass(passTypeId: String, serial: String): Card? = dao.findPass(passTypeId, serial)

    suspend fun save(card: Card) = dao.upsert(card)

    suspend fun markUsed(id: String) = dao.markUsed(id, System.currentTimeMillis())

    suspend fun markChecked(id: String) = dao.markChecked(id, System.currentTimeMillis())

    suspend fun setPinned(id: String, pinned: Boolean) = dao.setPinned(id, pinned)

    suspend fun delete(id: String) {
        val card = dao.get(id) ?: return
        dao.delete(id)
        withContext(Dispatchers.IO) {
            passDir(id).deleteRecursively()
            card.coverPath?.let { File(it).delete() }
        }
    }

    fun passDir(id: String) = File(filesDir, "passes/$id")

    fun passImage(id: String, role: String): File? = File(passDir(id), "$role.png").takeIf { it.isFile }

    fun passArchive(id: String) = File(passDir(id), "pass.pkpass")

    val filesRoot: File get() = filesDir

    fun coverFile(name: String): File = File(filesDir, "covers/$name").also { it.parentFile?.mkdirs() }

    fun newCoverFile(): File = File(filesDir, "covers/${UUID.randomUUID()}.jpg").also { it.parentFile?.mkdirs() }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}
