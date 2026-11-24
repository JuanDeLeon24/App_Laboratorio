package com.geodensidad.core.report

/** Construye las páginas (lista de visualización) de los informes individual y global. */
object ReportRenderer {

    fun sample(d: SampleReportData, m: TextMeasurer): PageList {
        val l = Layout(m)
        l.heading("1. Identificación")
        l.keyValues(listOf("Muestra" to d.sampleCode, "Revisión" to d.revisionLabel, "Estado documental" to d.documentStatus, "Identificador de informe" to d.issue.reportId) + d.projectPairs)
        l.heading("2. Jornada y localización")
        l.keyValues(d.journeyPairs + d.locationPairs)
        l.heading("3. Equipo, calibración y referencia de laboratorio")
        l.heading("Calibración utilizada", 2)
        l.keyValues(d.calibrationPairs)
        l.heading("Referencia de laboratorio", 2)
        l.keyValues(d.labRefPairs)
        l.heading("4. Mediciones")
        l.table(listOf(Layout.Col("Símb.", 0.6f), Layout.Col("Descripción", 4f), Layout.Col("Valor", 1.4f, true), Layout.Col("Unidad", 1.4f)), d.measurementRows)
        l.heading("5. Resultados y trazabilidad del cálculo")
        l.table(listOf(Layout.Col("Símb.", 0.6f), Layout.Col("Resultado", 3f), Layout.Col("Valor", 1.3f, true), Layout.Col("Unidad", 1.1f), Layout.Col("Fórmula", 3.2f)), d.resultRows)
        l.paragraph("Motor de cálculo: ${d.issue.engine}. Los valores se calculan sin redondeos intermedios; el redondeo mostrado es solo de presentación.", Styles.small)
        d.warnings.forEach { l.note(it) }
        l.heading("6. Criterio y estado técnico")
        d.criterionLines.forEach { l.paragraph(it) }
        l.paragraph("Estado técnico: ${d.technicalStatus}", Styles.body.copy(bold = true))
        l.paragraph("La aprobación documental de la muestra es independiente del resultado técnico.", Styles.small)
        if (d.customPairs.isNotEmpty()) { l.heading("7. Información complementaria"); l.keyValues(d.customPairs) }
        l.heading("Observaciones")
        l.paragraph(d.observations?.takeIf { it.isNotBlank() } ?: "Sin observaciones.")
        l.space(6f)
        l.signatures(listOf("ELABORÓ" to d.elaboratedBy, "APROBÓ" to d.approvedBy))
        photoAnnex(l, d.photos, d.photosPerPage)
        return finish(l, d.template, d.issue)
    }

    fun global(d: GlobalReportData, m: TextMeasurer): PageList {
        val l = Layout(m)
        l.paragraph(d.title, Styles.title)
        l.heading("1. Identificación y alcance")
        l.keyValues(d.scopePairs + ("Identificador de informe" to d.issue.reportId))
        l.heading("2. Resumen ejecutivo")
        d.executiveSummary.forEach { l.paragraph("• $it") }
        l.heading("3. Cobertura")
        l.keyValues(d.coveragePairs)
        l.heading("4. Resultados individuales")
        val cols = d.sampleColumns.mapIndexed { i, t -> Layout.Col(t, if (i == 0) 1.3f else 1f, alignRight = t.contains("kg/m³") || t.contains("%")) }
        l.table(cols, d.sampleRows)
        l.heading("5. Estadísticas por grupos compatibles")
        if (d.statGroups.isEmpty()) l.paragraph("No hay grupos con observaciones vigentes.")
        d.statGroups.forEach { g -> l.heading(g.title, 2); l.keyValues(g.pairs); g.note?.let { l.note(it, RColor.STEEL) } }
        if (d.charts.isNotEmpty()) { l.heading("6. Gráficos"); d.charts.forEach { l.chart(it) } }
        l.heading("7. Evaluación por lote")
        if (d.lots.isEmpty()) l.paragraph("No hay lotes en el alcance seleccionado.")
        d.lots.forEach { lot ->
            l.heading(lot.title, 2)
            l.keyValues(lot.pairs)
            val color = when (lot.favorable) { true -> RColor.OK; false -> RColor.BAD; null -> RColor.STEEL }
            l.note(lot.statusText, color)
            lot.notes.forEach { l.paragraph("– $it", Styles.small) }
        }
        template_formula(l, d.template)
        l.heading("8. Muestras no incluidas en los cálculos")
        if (d.notIncludedRows.isEmpty()) l.paragraph("Todas las muestras del alcance se incluyeron.")
        else l.table(listOf(Layout.Col("Muestra", 1f), Layout.Col("Estado", 1f), Layout.Col("Motivo", 3f)), d.notIncludedRows)
        l.heading("9. Advertencias y limitaciones")
        d.warnings.forEach { l.note(it) }
        l.heading("10. Calibraciones y referencias de laboratorio")
        if (d.calibrationRows.isNotEmpty()) l.table(listOf(Layout.Col("Calibración", 1.2f), Layout.Col("Equipo", 1.2f), Layout.Col("Fecha", 1f), Layout.Col("Constante cono (g)", 1f, true), Layout.Col("Densidad arena (g/cm³)", 1f, true)), d.calibrationRows)
        if (d.labRefRows.isNotEmpty()) l.table(listOf(Layout.Col("Referencia", 1.2f), Layout.Col("Material", 1.4f), Layout.Col("Ensayo", 1.2f), Layout.Col("Densidad seca máx. (kg/m³)", 1f, true), Layout.Col("Humedad óptima (%)", 1f, true)), d.labRefRows)
        l.heading("Observaciones")
        l.paragraph(d.observations?.takeIf { it.isNotBlank() } ?: "Sin observaciones.")
        photoAnnex(l, d.photos, d.photosPerPage)
        return finish(l, d.template, d.issue)
    }

    private fun template_formula(l: Layout, t: TemplateInfo) {
        val path = t.formulaImagePath ?: return
        l.ensure(60f)
        l.paragraph("Desviación estándar muestral utilizada (imagen del formato de origen):", Styles.small)
        val h = 34f; val w = h * t.formulaPxW / maxOf(1, t.formulaPxH)
        l.add(Cmd.Image(path, l.left + 10f, l.y, w, h))
        l.y += h + 8f
    }

    private fun photoAnnex(l: Layout, photos: List<PhotoBlock>, perPage: Int) {
        if (photos.isEmpty()) return
        val pp = perPage.coerceIn(1, 2)
        l.newPage()
        l.heading("Anexo fotográfico")
        photos.chunked(pp).forEachIndexed { gi, group ->
            if (gi > 0) l.newPage()
            val maxH = if (pp == 1) 470f else 225f
            group.forEach { p -> l.figure(p.path, p.pxW, p.pxH, p.caption, maxH) }
        }
    }

    /** Encabezado institucional, marca de borrador y pie con paginación "Página X de Y". */
    private fun finish(l: Layout, t: TemplateInfo, issue: IssueInfo): PageList {
        val total = l.pages.size
        val m = l.m
        l.pages.forEachIndexed { idx, page ->
            val deco = mutableListOf<Cmd>()
            if (issue.draft) {
                val st = TextStyle(96f, bold = true, color = RColor.WATERMARK)
                deco += Cmd.TextRot("BORRADOR", A4.W / 2 - 220f, A4.H / 2 + 180f, st, -45f)
            }
            val top = 28f; val hh = 72f; val left = l.left; val w = l.width
            deco += Cmd.Rect(left, top, w, hh, null, RColor.NAVY, 0.8f)
            val logoW = 130f
            if (t.logoPath != null && t.logoPxW > 0 && t.logoPxH > 0) {
                val maxW = logoW - 10f; val maxH = hh - 10f
                val r = t.logoPxW.toFloat() / t.logoPxH
                var iw = maxW; var ih = iw / r
                if (ih > maxH) { ih = maxH; iw = ih * r }
                deco += Cmd.Image(t.logoPath, left + 5f + (maxW - iw) / 2, top + 5f + (maxH - ih) / 2, iw, ih)
            } else {
                Wrap.lines(t.institution, logoW - 10f, TextStyle(8.5f, true, RColor.NAVY), m).take(4).forEachIndexed { i, s ->
                    deco += Cmd.Text(s, left + 5f, top + 18f + i * 11f, TextStyle(8.5f, true, RColor.NAVY))
                }
            }
            deco += Cmd.Line(left + logoW, top, left + logoW, top + hh, RColor.NAVY, 0.8f)
            val boxW = 135f
            val titleW = w - logoW - boxW
            val ts = TextStyle(8.6f, true, RColor.NAVY_DEEP)
            val tl = Wrap.lines(t.formTitle, titleW - 12f, ts, m).take(5)
            val ty0 = top + hh / 2 - tl.size * 11.5f / 2 + 8.5f
            tl.forEachIndexed { i, s -> deco += Cmd.Text(s, left + logoW + (titleW - m.textWidth(s, ts)) / 2, ty0 + i * 11.5f, ts) }
            val bx = left + w - boxW
            deco += Cmd.Line(bx, top, bx, top + hh, RColor.NAVY, 0.8f)
            val rows = listOf("CÓDIGO" to t.docCode, "VERSIÓN" to t.docVersion, "FECHA" to t.docDate, "PÁGINA" to "${idx + 1} de $total")
            val rh = hh / rows.size
            rows.forEachIndexed { i, (k, v) ->
                val ry = top + i * rh
                if (i > 0) deco += Cmd.Line(bx, ry, bx + boxW, ry, RColor.NAVY, 0.4f)
                deco += Cmd.Text(k, bx + 4f, ry + rh / 2 + 2.6f, TextStyle(6.8f, true, RColor.STEEL))
                deco += Cmd.Text(v, bx + 50f, ry + rh / 2 + 2.6f, TextStyle(7.2f, false, RColor.BLACK))
            }
            // pie general
            val fy = A4.H - 40f
            deco += Cmd.Line(left, fy, left + w, fy, RColor.SKY, 0.8f)
            val fs = TextStyle(6.6f, color = RColor.STEEL)
            deco += Cmd.Text("Informe ${issue.reportCode} · Emitido ${issue.issuedAtText}${issue.issuedBy?.let { " por $it" } ?: ""}", left, fy + 10f, fs)
            deco += Cmd.Text("Plantilla v${t.templateVersion} · ${issue.engine} · App ${issue.appVersion} · Proyecto ${issue.projectShort}", left, fy + 19f, fs)
            val pg = "Página ${idx + 1} de $total"
            deco += Cmd.Text(pg, left + w - m.textWidth(pg, fs.copy(bold = true)), fy + 10f, fs.copy(bold = true))
            if (issue.draft) deco += Cmd.Text("BORRADOR – sin validez documental", left + w - m.textWidth("BORRADOR – sin validez documental", fs), fy + 19f, fs.copy(color = RColor.BAD))
            page.cmds.addAll(0, deco)
        }
        return PageList(l.pages)
    }
}
