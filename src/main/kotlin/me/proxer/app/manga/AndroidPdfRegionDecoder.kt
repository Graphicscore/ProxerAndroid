package me.proxer.app.manga

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder
import kotlin.math.roundToInt

class AndroidPdfRegionDecoder(
    private val page: Int,
    private val file: java.io.File,
    private val scale: Float,
) : ImageRegionDecoder {
    private var renderer: PdfRenderer? = null
    private var pdfPage: PdfRenderer.Page? = null
    private var fd: ParcelFileDescriptor? = null

    override fun init(
        context: Context?,
        uri: Uri,
    ): Point {
        fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(fd!!)
        pdfPage = renderer!!.openPage(page)
        return Point((pdfPage!!.width * scale).roundToInt(), (pdfPage!!.height * scale).roundToInt())
    }

    override fun isReady() = pdfPage != null

    override fun decodeRegion(
        sRect: Rect,
        sampleSize: Int,
    ): Bitmap {
        val outputScale = scale / sampleSize
        val outputWidth = ((sRect.width().toFloat()) / sampleSize).roundToInt().coerceAtLeast(1)
        val outputHeight = ((sRect.height().toFloat()) / sampleSize).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val transform = Matrix()
        transform.postScale(outputScale, outputScale)
        transform.postTranslate(-(sRect.left.toFloat() / sampleSize), -(sRect.top.toFloat() / sampleSize))
        pdfPage!!.render(bitmap, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    override fun recycle() {
        pdfPage?.close()
        pdfPage = null
        renderer?.close()
        renderer = null
        fd?.close()
        fd = null
    }
}
