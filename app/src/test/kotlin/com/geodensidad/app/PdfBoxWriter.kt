package com.geodensidad.app

import com.geodensidad.app.report.PdfWriterTarget
import com.geodensidad.core.report.PdfBoxSurface
import java.io.OutputStream

/** Robolectric no implementa PdfDocument nativo: en pruebas se usa PDFBox sobre la misma composición. */
class PdfBoxWriter(private val s: PdfBoxSurface = PdfBoxSurface()) : PdfWriterTarget, com.geodensidad.core.report.PdfSurface by s {
    override fun writeTo(out: OutputStream) = s.save(out)
    override fun close() = s.close()
}
