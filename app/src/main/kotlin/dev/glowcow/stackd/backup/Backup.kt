package dev.glowcow.stackd.backup

import android.content.Context
import android.net.Uri
import dev.glowcow.stackd.data.AppSettings
import dev.glowcow.stackd.data.Card
import dev.glowcow.stackd.data.CardRepository
import dev.glowcow.stackd.data.Palette
import dev.glowcow.stackd.data.SettingsStore
import dev.glowcow.stackd.data.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream

sealed interface RestoreOutcome {
    data class Restored(val cards: Int) : RestoreOutcome
    data object WrongPassword : RestoreOutcome
    data object Invalid : RestoreOutcome
}

/** Saves the cards, their files and the settings to a file the user picks, and reads them back. */
class Backup(private val context: Context, private val cards: CardRepository, private val settings: SettingsStore) {

    /** Writes a backup to [uri], encrypted when [password] is given. */
    suspend fun save(uri: Uri, password: String?): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val all = cards.all()
            val s = settings.settings.first()
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("no stream")
            (if (password.isNullOrEmpty()) out else BackupCrypto.encrypt(out, password.toCharArray())).use {
                BackupArchive.write(
                    out = it,
                    cards = all,
                    settings = BackupSettings(s.theme.name, s.palette.name, s.maxBrightness, s.autoUpdate, s.updateHours, s.notifyAllChanges, s.appUpdate),
                    app = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty(),
                    createdAt = System.currentTimeMillis(),
                    filesDir = cards.filesRoot,
                )
            }
        }.isSuccess
    }

    /** Whether the file at [uri] asks for a password; null when it cannot be read. */
    suspend fun isEncrypted(uri: Uri): Boolean? = withContext(Dispatchers.IO) {
        runCatching { open(uri).use { BackupCrypto.isEncrypted(it.readNBytes(BackupCrypto.MAGIC.size)) } }.getOrNull()
    }

    /**
     * Adds the cards of the backup at [uri] to the ones here. With [overwrite] a card already here is
     * replaced by its copy and the settings are taken from the backup; without it both are left alone,
     * and the settings are restored only into an app that holds no cards.
     */
    suspend fun restore(uri: Uri, password: String?, overwrite: Boolean): RestoreOutcome = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val manifest = open(uri).use { raw ->
                val input = BufferedInputStream(raw)
                input.mark(BackupCrypto.MAGIC.size)
                val encrypted = BackupCrypto.isEncrypted(input.readNBytes(BackupCrypto.MAGIC.size))
                input.reset()
                if (encrypted && password.isNullOrEmpty()) return@withContext RestoreOutcome.WrongPassword
                BackupArchive.read(if (encrypted) BackupCrypto.decrypt(input, password!!.toCharArray()) else input, staging)
            }
            val empty = cards.all().isEmpty()
            val placed = manifest.cards.count { place(it, staging, overwrite) }
            if (overwrite || empty) manifest.settings?.let { settings.restore(it.toSettings()) }
            RestoreOutcome.Restored(placed)
        } catch (e: WrongPasswordException) {
            RestoreOutcome.WrongPassword
        } catch (e: Exception) {
            RestoreOutcome.Invalid
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun open(uri: Uri): InputStream = context.contentResolver.openInputStream(uri) ?: error("no stream")

    /** True when the card was written. */
    private suspend fun place(card: Card, staging: File, overwrite: Boolean): Boolean {
        if (!ID.matches(card.id)) return false
        // A pass already here under another id keeps that id: its type and serial are unique.
        val known = if (card.passTypeId != null && card.serial != null) cards.findPass(card.passTypeId, card.serial) else null
        val id = known?.id ?: card.id
        if (!overwrite && cards.get(id) != null) return false
        val files = File(staging, "passes/${card.id}")
        if (files.isDirectory) {
            val target = cards.passDir(id)
            target.deleteRecursively()
            target.parentFile?.mkdirs()
            files.copyRecursively(target, overwrite = true)
        }
        val cover = card.coverPath?.let { File(staging, it) }?.takeIf { it.isFile }?.let { it.copyTo(cards.coverFile(it.name), overwrite = true) }
        cards.save(card.copy(id = id, coverPath = cover?.path))
        return true
    }

    private fun BackupSettings.toSettings() = AppSettings().let { d ->
        AppSettings(
            theme = runCatching { ThemeMode.valueOf(theme) }.getOrDefault(d.theme),
            palette = runCatching { Palette.valueOf(palette) }.getOrDefault(d.palette),
            maxBrightness = maxBrightness,
            autoUpdate = autoUpdate,
            updateHours = updateHours,
            notifyAllChanges = notifyAllChanges,
            appUpdate = appUpdate,
        )
    }

    private companion object {
        val ID = Regex("[A-Za-z0-9-]{1,64}")
    }
}
