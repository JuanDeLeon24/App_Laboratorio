package com.geodensidad.app.data

import android.content.Context
import androidx.room.*
import com.geodensidad.core.calc.ConoArenaLegacy
import com.geodensidad.core.calc.LegacyInputs
import com.geodensidad.core.calc.LegacyResult
import com.geodensidad.core.number.DecimalText
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Una columna de densidad de la hoja 'Cono de arena'.
 * Solo se guardan las celdas que en el Excel son datos digitados (números fríos y textos);
 * las filas con fórmula (C, E, G, K, M, N, O, P, S) se calculan.
 * Números como texto decimal canónico ("1.396"); humedad L como fracción ("0.06"). Vacío = null.
 */
@Serializable
data class Densidad(
    val capa: String = "",
    val espesor: String = "",
    val pr: String = "",
    val costado: String = "",
    val a: String? = null,
    val b: String? = null,
    val d: String? = null,
    val f: String? = null,
    val h: String? = null,
    val j: String? = null,
    val l: String? = null,
)

/** Una hoja del Excel: encabezado, datos comunes (I, Q, R), hasta 10 densidades y pie. */
@Entity(tableName = "registros")
data class Registro(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val proyecto: String = "",
    val localizacion: String = "",
    val procedencia: String = "",
    val prInicial: String = "",
    val prFinal: String = "",
    val lado: String = "",
    val fecha: String = "",
    val parteRelleno: String = "",
    val lote: String = "",
    val registro: String = "",
    /** I · Gravedad específica bulk (común a todas las densidades, como en el Excel). */
    val i: String? = "2.667",
    /** Q · Densidad máxima de laboratorio (kg/m³). */
    val q: String? = null,
    /** R · Humedad óptima como fracción. */
    val r: String? = null,
    val densidadesJson: String = "[]",
    val observaciones: String = "",
    val elaboro: String = "",
    val aprobo: String = "",
    val creado: Long = System.currentTimeMillis(),
    val modificado: Long = System.currentTimeMillis(),
) {
    companion object { const val MAX_DENSIDADES = 10 }
}

private val registroJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
private val listaDensidades = ListSerializer(Densidad.serializer())

val Registro.densidades: List<Densidad> get() = runCatching { registroJson.decodeFromString(listaDensidades, densidadesJson) }.getOrDefault(emptyList())
fun Registro.conDensidades(d: List<Densidad>) = copy(densidadesJson = registroJson.encodeToString(listaDensidades, d))

fun Registro.entradas(d: Densidad) = LegacyInputs(
    a = DecimalText.storedToDouble(d.a), b = DecimalText.storedToDouble(d.b), d = DecimalText.storedToDouble(d.d),
    f = DecimalText.storedToDouble(d.f), h = DecimalText.storedToDouble(d.h), i = DecimalText.storedToDouble(i),
    j = DecimalText.storedToDouble(d.j), l = DecimalText.storedToDouble(d.l), q = DecimalText.storedToDouble(q),
    r = DecimalText.storedToDouble(r),
)

fun Registro.calcular(d: Densidad): LegacyResult = ConoArenaLegacy.calculate(entradas(d))

@Dao
interface RegistroDao {
    @Query("SELECT * FROM registros ORDER BY modificado DESC") fun todos(): Flow<List<Registro>>
    @Query("SELECT * FROM registros WHERE id = :id") suspend fun obtener(id: String): Registro?
    @Query("SELECT COUNT(*) FROM registros") suspend fun contar(): Int
    @Upsert suspend fun guardar(r: Registro)
    @Delete suspend fun eliminar(r: Registro)
}

@Database(entities = [Registro::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun registros(): RegistroDao

    companion object {
        /** Nombre distinto al de versiones anteriores de la app: no se mezcla con su esquema. */
        const val NAME = "geodensidad_registros.db"
        const val VERSION = 1
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME).setJournalMode(JournalMode.WRITE_AHEAD_LOGGING).build()
    }
}
