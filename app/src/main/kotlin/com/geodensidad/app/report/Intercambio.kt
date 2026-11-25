package com.geodensidad.app.report

import android.content.Context
import com.geodensidad.app.data.AppDatabase
import com.geodensidad.app.data.Densidad
import com.geodensidad.app.data.Registro
import com.geodensidad.app.data.conDensidades
import com.geodensidad.app.data.densidades
import com.geodensidad.core.xlsx.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Intercambio con el formato Excel de la plantilla 'Cono de arena' (.xlsx):
 * importar hojas del Excel original o de otra instalación, y exportar registros con las mismas celdas y fórmulas.
 */
class Intercambio(private val context: Context, private val db: () -> AppDatabase) {

    data class Resultado(val nuevos: Int, val actualizados: Int, val hojasIgnoradas: List<String>) {
        val texto get() = buildString {
            append("Importados: $nuevos nuevo(s)")
            if (actualizados > 0) append(", $actualizados actualizado(s)")
            if (hojasIgnoradas.isNotEmpty()) append(". Hojas sin el formato 'Cono de arena': ${hojasIgnoradas.joinToString()}")
        }
    }

    fun aHoja(r: Registro) = HojaDensidades(
        proyecto = r.proyecto, localizacion = r.localizacion, procedencia = r.procedencia, prInicial = r.prInicial, prFinal = r.prFinal,
        lado = r.lado, fecha = r.fecha, parteRelleno = r.parteRelleno, lote = r.lote, registro = r.registro, i = r.i, q = r.q, r = r.r,
        columnas = r.densidades.map { ColumnaExcel(it.capa, it.espesor, it.pr, it.costado, it.a, it.b, it.d, it.f, it.h, it.j, it.l) },
        observaciones = r.observaciones, elaboro = r.elaboro, aprobo = r.aprobo, idApp = r.id,
    )

    private fun deHoja(h: HojaDensidades, id: String, creado: Long?) = Registro(
        id = id, proyecto = h.proyecto, localizacion = h.localizacion, procedencia = h.procedencia, prInicial = h.prInicial, prFinal = h.prFinal,
        lado = h.lado, fecha = h.fecha, parteRelleno = h.parteRelleno, lote = h.lote, registro = h.registro, i = h.i, q = h.q, r = h.r,
        observaciones = h.observaciones, elaboro = h.elaboro, aprobo = h.aprobo,
        creado = creado ?: System.currentTimeMillis(), modificado = System.currentTimeMillis(),
    ).conDensidades(h.columnas.map { Densidad(it.capa, it.espesor, it.pr, it.costado, it.a, it.b, it.d, it.f, it.h, it.j, it.l) }.ifEmpty { listOf(Densidad()) })

    suspend fun importar(input: InputStream): Resultado = withContext(Dispatchers.IO) {
        val hojas = XlsxLector.leer(input)
        val validas = hojas.filter { PlantillaConoArena.esPlantilla(it) }
        if (validas.isEmpty()) throw XlsxException("El archivo no tiene ninguna hoja con el formato 'Cono de arena'")
        var nuevos = 0; var actualizados = 0
        val dao = db().registros()
        for (h in validas) {
            val hd = PlantillaConoArena.leer(h)
            val existente = hd.idApp?.let { dao.obtener(it) }
            dao.guardar(deHoja(hd, existente?.id ?: hd.idApp ?: UUID.randomUUID().toString(), existente?.creado))
            if (existente != null) actualizados++ else nuevos++
        }
        Resultado(nuevos, actualizados, hojas.filter { it !in validas }.map { it.nombre })
    }

    suspend fun exportar(registros: List<Registro>, out: OutputStream) = withContext(Dispatchers.IO) {
        require(registros.isNotEmpty()) { "No hay registros para exportar" }
        val usados = HashSet<String>()
        val hojas = registros.mapIndexed { i, r ->
            var nombre = if (registros.size == 1) "Cono de arena" else "Registro ${r.registro.ifBlank { "${i + 1}" }}".replace(Regex("[\\\\/?*\\[\\]:]"), "-").take(28)
            var k = 2; val base = nombre
            while (!usados.add(nombre.lowercase())) nombre = "$base ($k)".also { k++ }
            PlantillaConoArena.escribir(aHoja(r), nombre)
        }
        XlsxEscritor.escribir(out, hojas)
    }

    /** Archivo .xlsx en el almacenamiento de la app, listo para compartir. */
    suspend fun archivo(registros: List<Registro>, nombre: String): File {
        val dir = File(context.filesDir, "excel").apply { mkdirs() }
        val f = File(dir, nombre.replace(Regex("[^A-Za-z0-9._ -]"), "_"))
        f.outputStream().use { exportar(registros, it) }
        return f
    }
}
