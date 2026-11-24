package com.geodensidad.core.demo

import com.geodensidad.core.calc.LegacyInputs

/**
 * Diez muestras de referencia del archivo 'Densidades' (hoja 'Cono de arena').
 * Datos DEMOSTRATIVOS: solo se cargan en un proyecto marcado como demostrativo, por acción explícita.
 * Las masas y densidades se expresan como texto decimal canónico (sin errores de representación).
 */
object ReferenceSamples {
    val A = listOf("7139", "6713", "6671", "6558", "6487", "7100", "7038", "7001", "6987", "6963")
    val B = listOf("3317", "2373", "2410", "2312", "2351", "2180", "2144", "2090", "2177", "2132")
    val D = listOf("1735", "1735", "1735", "1735", "1735", "1774", "1774", "1774", "1774", "1774")
    val F = listOf("1.396", "1.396", "1.396", "1.396", "1.396", "1.408", "1.408", "1.408", "1.408", "1.408")
    val H = listOf("410", "601", "587", "717", "813", "1340", "1420", "1206", "1220", "1345")
    const val I = "2.667"
    val J = listOf("3694", "4661", "4519", "4527", "4382", "5460", "5501", "5406", "5201", "5346")
    val L = listOf("0.06", "0.062", "0.058", "0.06", "0.06", "0.075", "0.08", "0.076", "0.08", "0.078")
    const val Q = "2495"
    const val R = "0.0668"

    val expectedP = listOf(2294.972397, 2311.554431, 2320.713487, 2326.302953, 2350.698709, 2168.973650, 2193.203584, 2159.254052, 2127.389903, 2178.952193)
    val expectedS = listOf(91.982862, 92.647472, 93.014569, 93.238595, 94.216381, 86.932812, 87.903951, 86.543249, 85.266128, 87.332753)

    fun inputs(index: Int) = LegacyInputs(
        a = A[index].toDouble(), b = B[index].toDouble(), d = D[index].toDouble(), f = F[index].toDouble(),
        h = H[index].toDouble(), i = I.toDouble(), j = J[index].toDouble(), l = L[index].toDouble(),
        q = Q.toDouble(), r = R.toDouble(),
    )
}
