package pl.bbtv.tv

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object PairQr {
    fun svg(text: String): String {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2))
        val path = StringBuilder()
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) if (matrix.get(x, y)) path.append("M$x ${y}h1v1h-1z")
        return "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 ${matrix.width} ${matrix.height}' shape-rendering='crispEdges'><path fill='#000' d='$path'/></svg>"
    }
    fun card(link: String, code: String): String {
        val site = Pages.escape(link.substringAfter("://").substringBefore("/"))
        val manual = if (code.isNotEmpty()) "<span class='m'>albo wejdź na $site<br>i wpisz kod </span><em>$code</em>" else "<span class='m'>Łączenie z $site…</span>"
        return "<div class='q'>${svg(link)}<div><b>Steruj z telefonu</b><span class='m'>Zeskanuj aparatem<br></span>$manual</div></div>"
    }
}
