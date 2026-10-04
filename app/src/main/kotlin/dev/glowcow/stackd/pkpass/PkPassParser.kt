package dev.glowcow.stackd.pkpass

import dev.glowcow.stackd.barcode.BarcodeFormat
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale
import java.util.zip.ZipInputStream

class PkPassException(message: String) : Exception(message)

/**
 * Reads `.pkpass` (one pass) and `.pkpasses` (a zip of passes) archives.
 * The signature is not verified: the pass is only displayed, never trusted.
 */
class PkPassParser(
    private val locale: Locale = Locale.getDefault(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    // Wallet accepts sloppy pass.json (trailing commas, comments) and real issuers ship it.
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        allowTrailingComma = true
        allowComments = true
    }

    fun parse(input: InputStream): List<ParsedPass> {
        val raw = input.readLimited(MAX_ARCHIVE_BYTES)
        val entries = unzip(raw)
        if ("pass.json" in entries) return listOf(parseSingle(raw, entries))
        val nested = entries.filterKeys { it.endsWith(".pkpass", ignoreCase = true) }
        if (nested.isEmpty()) throw PkPassException("Not a pkpass archive: pass.json is missing")
        return nested.values.map { parseSingle(it, unzip(it)) }
    }

    private fun unzip(raw: ByteArray): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        var total = 0L
        try {
            ZipInputStream(ByteArrayInputStream(raw)).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory) continue
                    if (out.size >= MAX_ENTRIES) throw PkPassException("Too many entries")
                    val bytes = zip.readLimited(MAX_ARCHIVE_BYTES)
                    total += bytes.size
                    if (total > MAX_UNPACKED_BYTES) throw PkPassException("Archive is too large")
                    out[e.name.removePrefix("/")] = bytes
                }
            }
        } catch (e: java.util.zip.ZipException) {
            throw PkPassException("Not a zip archive: ${e.message}")
        }
        return out
    }

    private fun parseSingle(raw: ByteArray, entries: Map<String, ByteArray>): ParsedPass {
        val passBytes = entries["pass.json"] ?: throw PkPassException("pass.json is missing")
        val root = try {
            json.parseToJsonElement(PassStrings.decode(passBytes).trim()).jsonObject
        } catch (e: IllegalArgumentException) {
            throw PkPassException("pass.json is not valid JSON: ${e.message}")
        }

        val lang = pickLanguage(entries.keys)
        val strings = lang?.let { entries["$it.lproj/pass.strings"] }?.let(PassStrings::parse).orEmpty()
        fun loc(s: String?) = s?.let { strings[it] ?: it }

        val style = PassStyle.entries.firstOrNull { root[it.key] is JsonObject }
            ?: throw PkPassException("Unknown pass style")
        val styleObj = root[style.key]!!.jsonObject

        val fields = buildList {
            for ((section, key) in SECTIONS) {
                (styleObj[key] as? JsonArray)?.forEach { f ->
                    parseField(section, f as? JsonObject ?: return@forEach, ::loc)?.let(::add)
                }
            }
        }

        return ParsedPass(
            passTypeId = root.string("passTypeIdentifier") ?: throw PkPassException("passTypeIdentifier is missing"),
            serial = root.string("serialNumber") ?: throw PkPassException("serialNumber is missing"),
            style = style,
            organization = loc(root.string("organizationName")),
            description = loc(root.string("description")),
            logoText = loc(root.string("logoText")),
            backgroundColor = parseColor(root.string("backgroundColor")),
            foregroundColor = parseColor(root.string("foregroundColor")),
            labelColor = parseColor(root.string("labelColor")),
            barcode = parseBarcode(root, ::loc),
            relevantAt = root.string("relevantDate")?.let(::parseInstant) ?: firstRelevantDate(root),
            expiresAt = root.string("expirationDate")?.let(::parseInstant),
            voided = (root["voided"] as? JsonPrimitive)?.booleanOrNull ?: false,
            webServiceUrl = root.string("webServiceURL")?.takeIf { it.startsWith("https://", ignoreCase = true) },
            authToken = root.string("authenticationToken"),
            fields = fields,
            images = IMAGE_ROLES.mapNotNull { role -> bestImage(entries, role, lang)?.let { role to it } }.toMap(),
            raw = raw,
        )
    }

    private fun pickLanguage(names: Set<String>): String? {
        val langs = names.mapNotNull { n -> n.substringBefore('/').takeIf { n.contains(".lproj/") }?.removeSuffix(".lproj") }.distinct()
        if (langs.isEmpty()) return null
        fun base(tag: String) = tag.substringBefore('-').substringBefore('_').lowercase()
        return langs.firstOrNull { base(it) == locale.language.lowercase() }
            ?: langs.firstOrNull { base(it) == "en" }
            ?: langs.first()
    }

    private fun bestImage(entries: Map<String, ByteArray>, role: String, lang: String?): ByteArray? {
        val names = listOf("$role@3x.png", "$role@2x.png", "$role.png")
        val dirs = listOfNotNull(lang?.let { "$it.lproj/" }, "")
        for (dir in dirs) for (n in names) entries[dir + n]?.let { return it }
        return null
    }

    private fun parseBarcode(root: JsonObject, loc: (String?) -> String?): PassBarcode? {
        val candidates = buildList {
            (root["barcodes"] as? JsonArray)?.forEach { (it as? JsonObject)?.let(::add) }
            (root["barcode"] as? JsonObject)?.let(::add)
        }
        for (b in candidates) {
            val format = BarcodeFormat.fromPassKit(b.string("format")) ?: continue
            val message = b.string("message") ?: continue
            return PassBarcode(format, message, loc(b.string("altText")), b.string("messageEncoding"))
        }
        return null
    }

    private fun firstRelevantDate(root: JsonObject): Long? =
        (root["relevantDates"] as? JsonArray)?.firstNotNullOfOrNull { d ->
            val o = d as? JsonObject ?: return@firstNotNullOfOrNull null
            (o.string("startDate") ?: o.string("date"))?.let(::parseInstant)
        }

    private fun parseField(section: FieldSection, f: JsonObject, loc: (String?) -> String?): PassField? {
        val key = f.string("key") ?: return null
        val prim = (f["attributedValue"] ?: f["value"]) as? JsonPrimitive ?: return null
        val value = when {
            !prim.isString && prim.doubleOrNull != null -> formatNumber(prim.doubleOrNull!!, f)
            else -> {
                val s = loc(prim.content)!!.stripTags()
                if (f.string("dateStyle") != null || f.string("timeStyle") != null) formatDate(s, f) ?: s else s
            }
        }
        if (value.isBlank()) return null
        return PassField(section, key, loc(f.string("label"))?.takeIf { it.isNotBlank() }, value)
    }

    private fun formatNumber(n: Double, f: JsonObject): String {
        f.string("currencyCode")?.let { code ->
            runCatching { Currency.getInstance(code) }.getOrNull()?.let { cur ->
                return NumberFormat.getCurrencyInstance(locale).apply { currency = cur }.format(n)
            }
        }
        return when (f.string("numberStyle")) {
            "PKNumberStylePercent" -> NumberFormat.getPercentInstance(locale).format(n)
            else -> NumberFormat.getNumberInstance(locale).format(n)
        }
    }

    private fun formatDate(value: String, f: JsonObject): String? {
        val dt = runCatching { OffsetDateTime.parse(value) }.getOrNull() ?: return null
        val ignoreZone = (f["ignoresTimeZone"] as? JsonPrimitive)?.booleanOrNull == true
        val local: LocalDateTime = if (ignoreZone) dt.toLocalDateTime() else dt.atZoneSameInstant(zone).toLocalDateTime()
        val date = dateStyle(f.string("dateStyle"))
        val time = dateStyle(f.string("timeStyle"))
        val fmt = when {
            date != null && time != null -> DateTimeFormatter.ofLocalizedDateTime(date, time)
            date != null -> DateTimeFormatter.ofLocalizedDate(date)
            time != null -> DateTimeFormatter.ofLocalizedTime(time)
            else -> return null
        }
        return fmt.withLocale(locale).format(local)
    }

    private fun dateStyle(s: String?): FormatStyle? = when (s) {
        "PKDateStyleShort" -> FormatStyle.SHORT
        "PKDateStyleMedium" -> FormatStyle.MEDIUM
        "PKDateStyleLong" -> FormatStyle.LONG
        "PKDateStyleFull" -> FormatStyle.FULL
        else -> null
    }

    private fun parseInstant(s: String): Long? =
        runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli() }.getOrNull()

    companion object {
        private const val MAX_ARCHIVE_BYTES = 20L * 1024 * 1024
        private const val MAX_UNPACKED_BYTES = 60L * 1024 * 1024
        private const val MAX_ENTRIES = 500

        private val SECTIONS = listOf(
            FieldSection.HEADER to "headerFields",
            FieldSection.PRIMARY to "primaryFields",
            FieldSection.SECONDARY to "secondaryFields",
            FieldSection.AUXILIARY to "auxiliaryFields",
            FieldSection.BACK to "backFields",
        )
        val IMAGE_ROLES = listOf("logo", "icon", "strip", "thumbnail", "background", "footer")

        private val RGB = Regex("""rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*(?:,\s*[\d.]+\s*)?\)""")

        /** PassKit colors are `rgb(r, g, b)`; `#RRGGBB` shows up in the wild too. Returns ARGB. */
        fun parseColor(s: String?): Int? {
            if (s == null) return null
            RGB.matchEntire(s.trim())?.let { m ->
                val (r, g, b) = m.destructured.toList().map { it.toInt().coerceIn(0, 255) }
                return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            val hex = s.trim().removePrefix("#")
            if (hex.length == 6) hex.toIntOrNull(16)?.let { return (0xFF shl 24) or it }
            return null
        }

        private fun JsonObject.string(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.contentOrNull

        private val TAG = Regex("<[^>]+>")
        private fun String.stripTags() = replace(TAG, "")

        private fun InputStream.readLimited(limit: Long): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = read(buf)
                if (n < 0) break
                total += n
                if (total > limit) throw PkPassException("File is too large")
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }
}
