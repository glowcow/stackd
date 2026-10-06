package dev.glowcow.stackd.backup

import dev.glowcow.stackd.data.Card
import dev.glowcow.stackd.data.CardKind
import dev.glowcow.stackd.data.CardSource
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

class BackupTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun card(id: String, cover: File? = null) = Card(
        id = id,
        kind = CardKind.CARD,
        source = CardSource.PKPASS,
        name = "Coffee $id",
        bgColor = 1,
        fgColor = 2,
        createdAt = 3,
        authToken = "secret",
        coverPath = cover?.path,
    )

    private fun seal(data: ByteArray, password: String) = ByteArrayOutputStream().also { out ->
        BackupCrypto.encrypt(out, password.toCharArray()).use { it.write(data) }
    }.toByteArray()

    private fun open(sealed: ByteArray, password: String) = BackupCrypto.decrypt(ByteArrayInputStream(sealed), password.toCharArray()).readBytes()

    @Test
    fun archiveKeepsCardsFilesAndSettings() {
        val files = tmp.newFolder("files")
        File(files, "passes/a").mkdirs()
        File(files, "passes/a/pass.pkpass").writeBytes(byteArrayOf(1, 2, 3))
        File(files, "passes/a/logo.png").writeBytes(byteArrayOf(4))
        val cover = File(files, "covers/shot.jpg").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(5, 6)) }
        val settings = BackupSettings("DARK", "WARM", false, true, 12, true, false)
        val out = ByteArrayOutputStream()
        BackupArchive.write(out, listOf(card("a"), card("b", cover)), settings, "0.4.1", 99, files)

        val into = tmp.newFolder("restore")
        val manifest = BackupArchive.read(ByteArrayInputStream(out.toByteArray()), into)

        assertEquals(listOf("a", "b"), manifest.cards.map { it.id })
        assertEquals("secret", manifest.cards[0].authToken)
        assertNull(manifest.cards[0].coverPath)
        assertEquals("covers/shot.jpg", manifest.cards[1].coverPath)
        assertEquals(settings, manifest.settings)
        assertEquals(99, manifest.createdAt)
        assertArrayEquals(byteArrayOf(1, 2, 3), File(into, "passes/a/pass.pkpass").readBytes())
        assertArrayEquals(byteArrayOf(4), File(into, "passes/a/logo.png").readBytes())
        assertArrayEquals(byteArrayOf(5, 6), File(into, "covers/shot.jpg").readBytes())
    }

    @Test
    fun readSkipsEntriesThatDoNotBelong() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
            put(BackupArchive.MANIFEST, """{"createdAt":1,"app":"x","cards":[]}""")
            put("../escape.txt", "x")
            put("passes/a/../../escape.txt", "x")
            put("passes/a/..", "x")
            put("databases/stackd.db", "x")
            put("covers/ok.jpg", "x")
        }
        val into = tmp.newFolder("restore")
        BackupArchive.read(ByteArrayInputStream(out.toByteArray()), into)

        assertEquals(listOf("covers/ok.jpg"), into.walkTopDown().filter { it.isFile }.map { it.relativeTo(into).path }.toList())
        assertFalse(File(into.parentFile, "escape.txt").exists())
    }

    @Test
    fun readRefusesAnArchiveWithoutAManifest() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { it.putNextEntry(ZipEntry("covers/a.jpg")) }
        assertThrows(IOException::class.java) { BackupArchive.read(ByteArrayInputStream(out.toByteArray()), tmp.newFolder("restore")) }
        assertThrows(IOException::class.java) { BackupArchive.read(ByteArrayInputStream("not a zip".toByteArray()), tmp.newFolder("other")) }
    }

    @Test
    fun passwordRoundTripsAcrossChunks() {
        for (size in listOf(0, 1, 64 * 1024, 64 * 1024 + 1, 200_000)) {
            val data = Random(size).nextBytes(size)
            val sealed = seal(data, "pässword")
            assertTrue(BackupCrypto.isEncrypted(sealed))
            assertArrayEquals(data, open(sealed, "pässword"))
        }
        assertFalse(BackupCrypto.isEncrypted("PK\u0003\u0004 plain zip".toByteArray()))
    }

    @Test
    fun wrongPasswordAndDamageAreToldApart() {
        val sealed = seal(Random(1).nextBytes(200_000), "right")
        assertThrows(WrongPasswordException::class.java) { open(sealed, "wrong") }

        val damaged = sealed.copyOf().also { it[it.size - 20] = (it[it.size - 20] + 1).toByte() }
        val e = assertThrows(IOException::class.java) { open(damaged, "right") }
        assertFalse(e is WrongPasswordException)

        // Cut at a chunk border: every chunk left is intact, yet none of them is the last one.
        val cut = sealed.copyOf(28 + 4 + 64 * 1024 + 16)
        assertThrows(IOException::class.java) { open(cut, "right") }
    }
}
