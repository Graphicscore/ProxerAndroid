package me.proxer.app.manga

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.net.toFile
import com.davemorrissey.labs.subscaleview.decoder.ImageDecoder
import kotlin.math.roundToInt

class AndroidPdfDecoder(
    private val page: Int,
    private val file: java.io.File,
    private val scale: Float,
) : ImageDecoder {
    override fun decode(
        context: Context?,
        uri: Uri,
    ): Bitmap {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)
        val pdfPage = renderer.openPage(page)
        val width = (pdfPage.width * scale).roundToInt()
        val height = (pdfPage.height * scale).roundToInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        pdfPage.close()
        renderer.close()
        fd.close()
        return bitmap
    }
}
