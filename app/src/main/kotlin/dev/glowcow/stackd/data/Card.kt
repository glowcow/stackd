package dev.glowcow.stackd.data

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import dev.glowcow.stackd.barcode.BarcodeFormat
import dev.glowcow.stackd.pkpass.PassField
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class CardKind { CARD, TICKET }

@Serializable
enum class CardSource { SCAN, PHOTO, GALLERY, MANUAL, PKPASS }

@Entity(
    tableName = "cards",
    indices = [Index(value = ["passTypeId", "serial"], unique = true)],
)
data class Card(
    @PrimaryKey val id: String,
    val kind: CardKind,
    val source: CardSource,
    val name: String,
    /** Secondary line: category for hand-made cards, organization for passes. */
    val subtitle: String? = null,
    val barcodeValue: String? = null,
    val barcodeFormat: BarcodeFormat? = null,
    /** Human-readable text under the barcode when it differs from the value. */
    val barcodeAltText: String? = null,
    val bgColor: Int,
    val fgColor: Int,
    val labelColor: Int? = null,
    val note: String? = null,
    val pinned: Boolean = false,
    val createdAt: Long,
    val lastUsedAt: Long? = null,
    val relevantAt: Long? = null,
    val expiresAt: Long? = null,
    val voided: Boolean = false,
    val passTypeId: String? = null,
    val serial: String? = null,
    /** JSON list of [PassField]; empty for hand-made cards. */
    val fieldsJson: String? = null,
    /** Absolute path of the cover photo for cards added from a photo. */
    val coverPath: String? = null,
    /** The issuer's update service from the pass and the pass's token for it. */
    val webServiceUrl: String? = null,
    val authToken: String? = null,
    /** `Last-Modified` of the copy held, and when the issuer was last reached. */
    val lastModified: String? = null,
    val updatedAt: Long? = null,
) {
    val isPass get() = source == CardSource.PKPASS

    /** The pass names a service to fetch a fresh copy from. */
    val canUpdate get() = webServiceUrl != null && authToken != null && passTypeId != null && serial != null

    fun fields(): List<PassField> = fieldsJson?.let { runCatching { json.decodeFromString<List<PassField>>(it) }.getOrNull() }.orEmpty()

    /** `•••• 4821` style tail of the card number. */
    val maskedNumber: String?
        get() {
            val v = (barcodeAltText ?: barcodeValue)?.filter { it.isLetterOrDigit() } ?: return null
            return if (v.length <= 4) v else "•••• ${v.takeLast(4)}"
        }

    val initials: String
        get() = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•"

    companion object {
        val json = Json { ignoreUnknownKeys = true }
        fun encodeFields(fields: List<PassField>) = json.encodeToString(fields)
    }
}
