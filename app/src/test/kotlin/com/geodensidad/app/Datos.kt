package com.geodensidad.app

import com.geodensidad.app.data.Densidad
import com.geodensidad.app.data.Registro
import com.geodensidad.app.data.conDensidades
import com.geodensidad.core.demo.ReferenceSamples as R

/** Registro con las 10 densidades de la hoja 'Cono de arena' del Excel de origen. */
fun registroDelExcel(): Registro = Registro(
    proyecto = "Vías de acceso (prueba)", localizacion = "Tramo 1", procedencia = "Cantera", prInicial = "PR 003+000", prFinal = "PR 004+200",
    lado = "Derecho", fecha = "2026-11-24", parteRelleno = "Núcleo", lote = "1", registro = "1", i = R.I, q = R.Q, r = R.R,
    observaciones = "Datos de prueba tomados del Excel", elaboro = "Ing. Prueba", aprobo = "Ing. Revisor",
).conDensidades((0 until 10).map { k ->
    Densidad(capa = "2", espesor = "20 cm", pr = "PR 00${3 + k / 5}+${(k % 5) * 25 + 100}", costado = if (k % 2 == 0) "Derecho" else "Izquierdo",
        a = R.A[k], b = R.B[k], d = R.D[k], f = R.F[k], h = R.H[k], j = R.J[k], l = R.L[k])
})
