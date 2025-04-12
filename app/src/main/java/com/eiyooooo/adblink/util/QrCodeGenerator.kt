package com.eiyooooo.adblink.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.nio.charset.StandardCharsets

object QrCodeGenerator {

    private const val DEFAULT_SIZE = 512

    private fun encodeQrCode(contents: String, size: Int = DEFAULT_SIZE): BitMatrix {
        val hints = mutableMapOf<EncodeHintType, Any>()
        if (!isIso88591(contents)) {
            hints[EncodeHintType.CHARACTER_SET] = StandardCharsets.UTF_8.name()
        }
        hints[EncodeHintType.MARGIN] = 0
        return MultiFormatWriter().encode(contents, BarcodeFormat.QR_CODE, size, size, hints)
    }

    fun encodeQrCodeToBitmap(
        contents: String, size: Int = DEFAULT_SIZE,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE
    ): Bitmap {
        val bitMatrix = encodeQrCode(contents, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = createBitmap(width, height)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap[x, y] = if (bitMatrix.get(x, y)) foregroundColor else backgroundColor
            }
        }
        return bitmap
    }

    private fun isIso88591(contents: String): Boolean {
        val encoder = StandardCharsets.ISO_8859_1.newEncoder()
        return encoder.canEncode(contents)
    }
}
