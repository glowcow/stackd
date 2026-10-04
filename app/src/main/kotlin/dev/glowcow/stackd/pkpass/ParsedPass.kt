package dev.glowcow.stackd.pkpass

import dev.glowcow.stackd.barcode.BarcodeFormat
import kotlinx.serialization.Serializable

enum class PassStyle(val key: String, val isTicket: Boolean) {
    BOARDING_PASS("boardingPass", true),
    EVENT_TICKET("eventTicket", true),
    COUPON("coupon", false),
    STORE_CARD("storeCard", false),
    GENERIC("generic", false),
}

@Serializable
enum class FieldSection { HEADER, PRIMARY, SECONDARY, AUXILIARY, BACK }

@Serializable
data class PassField(
    val section: FieldSection,
    val key: String,
    val label: String? = null,
    val value: String,
)

data class PassBarcode(
    val format: BarcodeFormat,
    val message: String,
    val altText: String?,
    val encoding: String?,
)

class ParsedPass(
    val passTypeId: String,
    val serial: String,
    val style: PassStyle,
    val organization: String?,
    val description: String?,
    val logoText: String?,
    val backgroundColor: Int?,
    val foregroundColor: Int?,
    val labelColor: Int?,
    val barcode: PassBarcode?,
    val relevantAt: Long?,
    val expiresAt: Long?,
    val voided: Boolean,
    /** Update service of the issuer; only https ones are kept. */
    val webServiceUrl: String?,
    val authToken: String?,
    val fields: List<PassField>,
    /** Best-resolution image per role: logo, icon, strip, thumbnail, background, footer. */
    val images: Map<String, ByteArray>,
    /** The original archive, kept for sharing. */
    val raw: ByteArray,
) {
    fun fields(section: FieldSection) = fields.filter { it.section == section }

    /** Display title: a route for boarding passes, otherwise the pass's own branding. */
    val title: String
        get() {
            val primary = fields(FieldSection.PRIMARY)
            if (style == PassStyle.BOARDING_PASS && primary.size >= 2) {
                return "${primary[0].value} → ${primary[1].value}"
            }
            return logoText?.takeIf { it.isNotBlank() }
                ?: organization?.takeIf { it.isNotBlank() }
                ?: description.orEmpty()
        }
}
