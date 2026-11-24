package com.geodensidad.app

import com.geodensidad.app.report.PdfWriterTarget
import com.geodensidad.core.report.PdfBoxSurface
import java.io.OutputStream

/** Escritor de prueba: Robolectric no implementa PdfDocument nativo, así que se usa PDFBox. */
class PdfBoxWriter(private val s: PdfBoxSurface = PdfBoxSurface()) : PdfWriterTarget, com.geodensidad.core.report.PdfSurface by s {
    override fun writeTo(out: OutputStream) = s.save(out)
    override fun close() = s.close()
}
