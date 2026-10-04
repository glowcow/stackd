package dev.glowcow.stackd.pkpass

import dev.glowcow.stackd.barcode.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.ZoneOffset
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PkPassParserTest {

    private val parser = PkPassParser(Locale.forLanguageTag("ru-RU"), ZoneOffset.ofHours(3))

    private fun zip(vararg files: Pair<String, ByteArray>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            for ((name, bytes) in files) {
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    private val boardingPass = """
        {
          "formatVersion": 1,
          "passTypeIdentifier": "pass.com.example.air",
          "serialNumber": "ABC123",
          "organizationName": "Example Air",
          "description": "Boarding pass",
          "backgroundColor": "rgb(106, 155, 204)",
          "foregroundColor": "#141413",
          "relevantDate": "2026-10-12T14:20+03:00",
          "barcodes": [
            { "format": "PKBarcodeFormatQR", "message": "M1DOE/JOHN", "messageEncoding": "iso-8859-1", "altText": "gate_alt" }
          ],
          "boardingPass": {
            "transitType": "PKTransitTypeAir",
            "headerFields": [ { "key": "gate", "label": "gate_label", "value": "B12" } ],
            "primaryFields": [
              { "key": "from", "label": "SVO", "value": "SVO" },
              { "key": "to", "label": "TLV", "value": "TLV" }
            ],
            "auxiliaryFields": [
              { "key": "boarding", "label": "Boarding", "value": "2026-10-12T13:40+03:00", "timeStyle": "PKDateStyleShort" }
            ],
            "backFields": [ { "key": "note", "label": "Note", "attributedValue": "<a href='x'>Terms</a>", "value": "Terms" } ]
          }
        }
    """.trimIndent().toByteArray()

    @Test
    fun parsesBoardingPass() {
        val strings = "\"gate_label\" = \"Выход\";\n\"gate_alt\" = \"Посадочный\";".toByteArray(Charsets.UTF_16)
        val pass = parser.parse(
            zip(
                "pass.json" to boardingPass,
                "ru.lproj/pass.strings" to strings,
                "logo.png" to byteArrayOf(1),
                "logo@2x.png" to byteArrayOf(2),
            ).inputStream(),
        ).single()

        assertEquals(PassStyle.BOARDING_PASS, pass.style)
        assertEquals("SVO → TLV", pass.title)
        assertEquals("pass.com.example.air", pass.passTypeId)
        assertEquals(0xFF6A9BCC.toInt(), pass.backgroundColor)
        assertEquals(0xFF141413.toInt(), pass.foregroundColor)
        assertEquals(BarcodeFormat.QR_CODE, pass.barcode!!.format)
        assertEquals("Посадочный", pass.barcode.altText)
        assertEquals("Выход", pass.fields(FieldSection.HEADER).single().label)
        assertEquals("13:40", pass.fields(FieldSection.AUXILIARY).single().value)
        assertEquals("Terms", pass.fields(FieldSection.BACK).single().value)
        assertEquals(1_791_804_000_000L, pass.relevantAt)
        assertEquals(2.toByte(), pass.images["logo"]!!.single())
    }

    @Test
    fun parsesStoreCardWithLegacyBarcodeAndNumbers() {
        val json = """
            {"passTypeIdentifier":"pass.shop","serialNumber":"1","organizationName":"Полка","logoText":"Полка",
             "barcode":{"format":"PKBarcodeFormatCode128","message":"46001234","messageEncoding":"utf-8"},
             "storeCard":{"headerFields":[{"key":"b","label":"Баллы","value":1240}],
                          "secondaryFields":[{"key":"d","label":"Скидка","value":0.1,"numberStyle":"PKNumberStylePercent"}]}}
        """.trimIndent().toByteArray()
        val pass = parser.parse(zip("pass.json" to json).inputStream()).single()

        assertEquals(PassStyle.STORE_CARD, pass.style)
        assertEquals("Полка", pass.title)
        assertEquals(BarcodeFormat.CODE_128, pass.barcode!!.format)
        assertEquals("1 240", pass.fields(FieldSection.HEADER).single().value)
        assertEquals("10 %", pass.fields(FieldSection.SECONDARY).single().value)
        assertNull(pass.relevantAt)
    }

    @Test
    fun acceptsTrailingCommasAndComments() {
        val json = """
            {"passTypeIdentifier":"pass.shop","serialNumber":"2", // issuer comment
             "storeCard":{"primaryFields":[{"key":"p","label":"Points","value":"5",},],},}
        """.trimIndent().toByteArray()
        val pass = parser.parse(zip("pass.json" to json).inputStream()).single()
        assertEquals("5", pass.fields(FieldSection.PRIMARY).single().value)
    }

    @Test
    fun keepsOnlyHttpsUpdateService() {
        fun pass(url: String) = String(boardingPass).replaceFirst("{", """{ "webServiceURL": "$url", "authenticationToken": "secret",""").toByteArray()
        val secure = parser.parse(zip("pass.json" to pass("https://example.com/passes/")).inputStream()).single()
        assertEquals("https://example.com/passes/", secure.webServiceUrl)
        assertEquals("secret", secure.authToken)
        assertNull(parser.parse(zip("pass.json" to pass("http://example.com/")).inputStream()).single().webServiceUrl)
        assertNull(parser.parse(zip("pass.json" to boardingPass).inputStream()).single().webServiceUrl)
    }

    @Test
    fun parsesPkpassesBundle() {
        val inner = zip("pass.json" to boardingPass)
        val passes = parser.parse(zip("a.pkpass" to inner, "b.pkpass" to inner).inputStream())
        assertEquals(2, passes.size)
    }

    @Test
    fun rejectsNonPass() {
        assertThrows(PkPassException::class.java) { parser.parse(zip("x.txt" to byteArrayOf(1)).inputStream()) }
        assertThrows(PkPassException::class.java) { parser.parse("not a zip".byteInputStream()) }
    }

    @Test
    fun parsesColors() {
        assertEquals(0xFF0A0B0C.toInt(), PkPassParser.parseColor("rgb(10,11,12)"))
        assertEquals(0xFFD97757.toInt(), PkPassParser.parseColor("#D97757"))
        assertNull(PkPassParser.parseColor("red"))
    }

    @Test
    fun parsesStringsFile() {
        val s = PassStrings.parse(
            """
            /* comment */
            "a" = "1";
            // line comment
            "b" = "say \"hi\"\n";
            """.trimIndent(),
        )
        assertEquals(mapOf("a" to "1", "b" to "say \"hi\"\n"), s)
    }

    @Test
    fun guessesBarcodeFormat() {
        assertEquals(BarcodeFormat.EAN_13, BarcodeFormat.guess("4006381333931"))
        assertEquals(BarcodeFormat.CODE_128, BarcodeFormat.guess("4006381333932"))
        assertEquals(BarcodeFormat.EAN_8, BarcodeFormat.guess("96385074"))
        assertTrue(BarcodeFormat.hasValidGtinCheckDigit("036000291452"))
    }
}
