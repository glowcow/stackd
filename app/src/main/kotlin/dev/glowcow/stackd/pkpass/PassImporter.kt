package dev.glowcow.stackd.pkpass

import android.content.Context
import android.net.Uri
import dev.glowcow.stackd.data.Card
import dev.glowcow.stackd.data.CardColors
import dev.glowcow.stackd.data.CardKind
import dev.glowcow.stackd.data.CardRepository
import dev.glowcow.stackd.data.CardSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

class PassImporter(private val context: Context, private val repo: CardRepository) {

    /** Imports every pass in the file and returns their card ids. Re-importing a pass updates it in place. */
    suspend fun import(uri: Uri): List<String> {
        val passes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { PkPassParser().parse(it) }
                ?: throw PkPassException("Cannot open the file")
        }
        return passes.map { store(it) }
    }

    /** Replaces [card] with a copy fetched from its issuer; fails if the archive holds another pass. */
    suspend fun replace(card: Card, archive: ByteArray, lastModified: String?): Card {
        val pass = withContext(Dispatchers.IO) { PkPassParser().parse(archive.inputStream()) }
            .firstOrNull { it.passTypeId == card.passTypeId && it.serial == card.serial }
            ?: throw PkPassException("The issuer returned a different pass")
        return repo.get(store(pass, lastModified, System.currentTimeMillis())) ?: throw PkPassException("The card is gone")
    }

    /** Fills in the update service of passes imported before it was stored. */
    suspend fun backfill() {
        for (card in repo.cards.first()) {
            if (!card.isPass || card.webServiceUrl != null) continue
            val pass = withContext(Dispatchers.IO) {
                runCatching { repo.passArchive(card.id).inputStream().use { PkPassParser().parse(it) } }.getOrNull()
            }?.firstOrNull { it.passTypeId == card.passTypeId && it.serial == card.serial } ?: continue
            if (pass.webServiceUrl != null) repo.save(card.copy(webServiceUrl = pass.webServiceUrl, authToken = pass.authToken))
        }
    }

    private suspend fun store(p: ParsedPass, lastModified: String? = null, fetchedAt: Long? = null): String {
        val existing = repo.findPass(p.passTypeId, p.serial)
        val id = existing?.id ?: CardRepository.newId()
        withContext(Dispatchers.IO) {
            val dir = repo.passDir(id)
            dir.deleteRecursively()
            dir.mkdirs()
            repo.passArchive(id).writeBytes(p.raw)
            p.images.forEach { (role, bytes) -> File(dir, "$role.png").writeBytes(bytes) }
        }
        val bg = p.backgroundColor ?: CardColors.forName(p.title)
        repo.save(
            Card(
                id = id,
                kind = if (p.style.isTicket) CardKind.TICKET else CardKind.CARD,
                source = CardSource.PKPASS,
                name = p.title.ifBlank { p.passTypeId },
                subtitle = p.organization ?: p.description,
                barcodeValue = p.barcode?.message,
                barcodeFormat = p.barcode?.format,
                barcodeAltText = p.barcode?.altText,
                bgColor = bg,
                fgColor = p.foregroundColor ?: CardColors.inkFor(bg),
                labelColor = p.labelColor,
                note = existing?.note,
                pinned = existing?.pinned ?: false,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                lastUsedAt = existing?.lastUsedAt,
                relevantAt = p.relevantAt,
                expiresAt = p.expiresAt,
                voided = p.voided,
                passTypeId = p.passTypeId,
                serial = p.serial,
                fieldsJson = Card.encodeFields(p.fields),
                webServiceUrl = p.webServiceUrl,
                authToken = p.authToken,
                lastModified = lastModified,
                updatedAt = fetchedAt,
            ),
        )
        return id
    }
}
