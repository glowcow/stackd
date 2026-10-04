package dev.glowcow.stackd.barcode

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class ScannedCode(val value: String, val format: BarcodeFormat)

object MlKitFormats {

    fun newScanner(): BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build(),
    )

    fun toScanned(b: Barcode): ScannedCode? {
        val format = when (b.format) {
            Barcode.FORMAT_QR_CODE -> BarcodeFormat.QR_CODE
            Barcode.FORMAT_AZTEC -> BarcodeFormat.AZTEC
            Barcode.FORMAT_DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
            Barcode.FORMAT_PDF417 -> BarcodeFormat.PDF_417
            Barcode.FORMAT_EAN_13 -> BarcodeFormat.EAN_13
            Barcode.FORMAT_EAN_8 -> BarcodeFormat.EAN_8
            Barcode.FORMAT_UPC_A -> BarcodeFormat.UPC_A
            Barcode.FORMAT_UPC_E -> BarcodeFormat.UPC_E
            Barcode.FORMAT_CODE_128 -> BarcodeFormat.CODE_128
            Barcode.FORMAT_CODE_39 -> BarcodeFormat.CODE_39
            Barcode.FORMAT_CODE_93 -> BarcodeFormat.CODE_93
            Barcode.FORMAT_ITF -> BarcodeFormat.ITF
            Barcode.FORMAT_CODABAR -> BarcodeFormat.CODABAR
            else -> return null
        }
        val value = b.rawValue?.takeIf { it.isNotEmpty() } ?: return null
        return ScannedCode(value, format)
    }

    /** Finds the first supported barcode in a still image (gallery pick or captured photo). */
    suspend fun scanImage(context: Context, uri: Uri): ScannedCode? {
        val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull() ?: return null
        val scanner = newScanner()
        return try {
            suspendCancellableCoroutine { cont ->
                scanner.process(image)
                    .addOnSuccessListener { list -> cont.resume(list.firstNotNullOfOrNull(::toScanned)) }
                    .addOnFailureListener { cont.resume(null) }
            }
        } finally {
            scanner.close()
        }
    }
}
