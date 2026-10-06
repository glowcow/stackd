package dev.glowcow.stackd.backup

import dev.glowcow.stackd.data.Card
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class BackupSettings(
    val theme: String,
    val palette: String,
    val maxBrightness: Boolean,
    val autoUpdate: Boolean,
    val updateHours: Int,
    val notifyAllChanges: Boolean,
    val appUpdate: Boolean,
)

/** `backup.json` of an archive. A card's `coverPath` is the cover's entry in the archive. */
@Serializable
data class BackupManifest(
    val version: Int = 1,
    val createdAt: Long,
    val app: String,
    val cards: List<Card>,
    val settings: BackupSettings? = null,
)

/**
 * A backup is a zip: `backup.json`, the stored passes as `passes/<card id>/<file>` and the cover
 * photos as `covers/<file>`.
 */
object BackupArchive {
    const val MANIFEST = "backup.json"

    private val json = Json { ignoreUnknownKeys = true }
    private val PASS_FILE = Regex("""passes/([A-Za-z0-9-]{1,64})/([A-Za-z0-9._@-]{1,64})""")
    private val COVER_FILE = Regex("""covers/([A-Za-z0-9._-]{1,80})""")

    private const val MAX_MANIFEST = 20L * 1024 * 1024
    private const val MAX_TOTAL = 2L * 1024 * 1024 * 1024
    private const val MAX_ENTRIES = 50_000

    /** Writes [cards] with the files they own under [filesDir]; the caller closes [out]. */
    fun write(out: OutputStream, cards: List<Card>, settings: BackupSettings?, app: String, createdAt: Long, filesDir: File) {
        val zip = ZipOutputStream(out)
        val covers = cards.mapNotNull { c -> c.coverPath?.let(::File)?.takeIf { it.isFile && COVER_FILE.matches("covers/${it.name}") }?.let { c.id to it } }.toMap()
        val manifest = BackupManifest(
            createdAt = createdAt,
            app = app,
            cards = cards.map { it.copy(coverPath = covers[it.id]?.let { f -> "covers/${f.name}" }) },
            settings = settings,
        )
        zip.putNextEntry(ZipEntry(MANIFEST))
        zip.write(json.encodeToString(manifest).toByteArray())
        zip.closeEntry()
        fun add(name: String, file: File) {
            zip.putNextEntry(ZipEntry(name))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        for (card in cards) {
            File(filesDir, "passes/${card.id}").listFiles().orEmpty().sortedBy { it.name }.forEach { f ->
                val name = "passes/${card.id}/${f.name}"
                if (f.isFile && PASS_FILE.matches(name)) add(name, f)
            }
        }
        covers.values.distinct().forEach { add("covers/${it.name}", it) }
        zip.finish()
    }

    /** Unpacks a backup into the empty directory [into], keeping only entries a backup can hold. */
    fun read(input: InputStream, into: File): BackupManifest {
        val root = into.canonicalFile
        var manifest: BackupManifest? = null
        var total = 0L
        var entries = 0
        val zip = ZipInputStream(input)
        while (true) {
            val entry = zip.nextEntry ?: break
            if (++entries > MAX_ENTRIES) throw IOException("too many entries")
            val name = entry.name
            if (name == MANIFEST) {
                val bytes = ByteArrayOutputStream()
                copy(zip, bytes, MAX_MANIFEST)
                manifest = json.decodeFromString<BackupManifest>(bytes.toString(Charsets.UTF_8.name()))
                continue
            }
            if (entry.isDirectory || !(PASS_FILE.matches(name) || COVER_FILE.matches(name))) continue
            val target = File(root, name).canonicalFile
            // The patterns allow dots, so a name made of them alone still has to stay inside.
            if (target.name != name.substringAfterLast('/') || !target.path.startsWith(root.path + File.separator)) continue
            target.parentFile?.mkdirs()
            total += target.outputStream().use { copy(zip, it, MAX_TOTAL - total) }
        }
        return manifest ?: throw IOException("no $MANIFEST in the archive")
    }

    private fun copy(from: InputStream, to: OutputStream, limit: Long): Long {
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        while (true) {
            val n = from.read(buffer)
            if (n < 0) return copied
            copied += n
            if (copied > limit) throw IOException("the backup is too large")
            to.write(buffer, 0, n)
        }
    }
}
