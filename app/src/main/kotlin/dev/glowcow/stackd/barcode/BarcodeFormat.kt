package dev.glowcow.stackd.barcode

enum class BarcodeFormat(val label: String, val is2d: Boolean) {
    QR_CODE("QR", true),
    AZTEC("Aztec", true),
    DATA_MATRIX("Data Matrix", true),
    PDF_417("PDF417", true),
    EAN_13("EAN-13", false),
    EAN_8("EAN-8", false),
    UPC_A("UPC-A", false),
    UPC_E("UPC-E", false),
    CODE_128("Code 128", false),
    CODE_39("Code 39", false),
    CODE_93("Code 93", false),
    ITF("ITF", false),
    CODABAR("Codabar", false);

    companion object {
        fun fromPassKit(format: String?): BarcodeFormat? = when (format) {
            "PKBarcodeFormatQR" -> QR_CODE
            "PKBarcodeFormatPDF417" -> PDF_417
            "PKBarcodeFormatAztec" -> AZTEC
            "PKBarcodeFormatCode128" -> CODE_128
            else -> null
        }

        /** Best guess for a hand-typed number. */
        fun guess(value: String): BarcodeFormat {
            val digits = value.all { it.isDigit() }
            return when {
                digits && value.length == 13 && hasValidGtinCheckDigit(value) -> EAN_13
                digits && value.length == 8 && hasValidGtinCheckDigit(value) -> EAN_8
                digits && value.length == 12 && hasValidGtinCheckDigit(value) -> UPC_A
                else -> CODE_128
            }
        }

        fun hasValidGtinCheckDigit(value: String): Boolean {
            if (value.isEmpty() || !value.all { it.isDigit() }) return false
            val body = value.dropLast(1).reversed()
            val sum = body.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 3 else 1 }.sum()
            return (10 - sum % 10) % 10 == value.last() - '0'
        }
    }
}
