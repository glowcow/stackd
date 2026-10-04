package dev.glowcow.stackd.barcode

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.BarcodeFormat as ZxFormat

/** Renders barcodes at one pixel per module; scale up with nearest-neighbour filtering. */
object BarcodeRenderer {

    fun render(value: String, format: BarcodeFormat, encoding: String? = null): Bitmap? {
        val hints = buildMap<EncodeHintType, Any> {
            put(EncodeHintType.MARGIN, 0)
            put(EncodeHintType.CHARACTER_SET, encoding?.takeIf { format.is2d } ?: "UTF-8")
            if (format == BarcodeFormat.PDF_417) put(EncodeHintType.PDF417_COMPACT, false)
        }
        val matrix = runCatching { MultiFormatWriter().encode(value, format.zxing, 0, 0, hints) }.getOrNull() ?: return null
        return matrix.toBitmap(if (format.is2d) matrix.height else 1)
    }

    private fun BitMatrix.toBitmap(rows: Int): Bitmap {
        val pixels = IntArray(width * rows)
        for (y in 0 until rows) {
            for (x in 0 until width) {
                pixels[y * width + x] = if (get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, width, rows, Bitmap.Config.ARGB_8888)
    }

    private val BarcodeFormat.zxing: ZxFormat
        get() = when (this) {
            BarcodeFormat.QR_CODE -> ZxFormat.QR_CODE
            BarcodeFormat.AZTEC -> ZxFormat.AZTEC
            BarcodeFormat.DATA_MATRIX -> ZxFormat.DATA_MATRIX
            BarcodeFormat.PDF_417 -> ZxFormat.PDF_417
            BarcodeFormat.EAN_13 -> ZxFormat.EAN_13
            BarcodeFormat.EAN_8 -> ZxFormat.EAN_8
            BarcodeFormat.UPC_A -> ZxFormat.UPC_A
            BarcodeFormat.UPC_E -> ZxFormat.UPC_E
            BarcodeFormat.CODE_128 -> ZxFormat.CODE_128
            BarcodeFormat.CODE_39 -> ZxFormat.CODE_39
            BarcodeFormat.CODE_93 -> ZxFormat.CODE_93
            BarcodeFormat.ITF -> ZxFormat.ITF
            BarcodeFormat.CODABAR -> ZxFormat.CODABAR
        }
}
