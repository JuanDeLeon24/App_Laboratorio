package com.geodensidad.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.geodensidad.app.report.CanvasSurface
import com.geodensidad.core.report.ChartRenderer
import com.geodensidad.core.report.ChartSpec
import com.geodensidad.core.report.renderTo

/** Dibuja en pantalla exactamente el mismo gráfico vectorial que se emite en el PDF. */
@Composable
fun ChartView(spec: ChartSpec, modifier: Modifier = Modifier) {
    val logicalW = 515f; val logicalH = 230f
    Canvas(modifier.fillMaxWidth().height(250.dp).semantics { contentDescription = "${spec.title}. ${spec.footnote ?: ""}" }) {
        val scale = size.width / logicalW
        drawIntoCanvas { c ->
            val s = CanvasSurface(c.nativeCanvas, scale)
            ChartRenderer.draw(spec, 0f, 0f, logicalW, minOf(logicalH, size.height / scale), s) { it.renderTo(s) }
        }
    }
}
