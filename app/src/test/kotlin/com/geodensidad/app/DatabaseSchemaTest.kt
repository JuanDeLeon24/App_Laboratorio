package com.geodensidad.app

import android.database.sqlite.SQLiteDatabase
import com.geodensidad.app.data.AppDatabase
import com.geodensidad.app.data.DbCheck
import com.geodensidad.app.data.DbGuard
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Pruebas de esquema y de la infraestructura de evolución SIN inflar el esquema de producción:
 * la "versión 2" existe solo aquí, como migración de prueba aplicada con SQL.
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseSchemaTest {
    private lateinit var c: AppContainer
    @Before fun setup() { c = TestSupport.freshContainer() }

    private fun schemaJson(): JsonObject = Json.parseToJsonElement(File("schemas/com.geodensidad.app.data.AppDatabase/1.json").readText()).jsonObject

    @Test fun esquemaExportadoCoincideConLasEntidades() = runBlocking {
        c.closeDb()
        val dbFile = c.context.getDatabasePath(AppDatabase.NAME)
        dbFile.parentFile!!.listFiles()!!.forEach { it.delete() }
        // base creada SOLO a partir del esquema exportado v1
        val db = schemaJson()["database"]!!.jsonObject
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { sdb ->
            db["entities"]!!.jsonArray.forEach { e ->
                val o = e.jsonObject; val table = o["tableName"]!!.jsonPrimitive.content
                sdb.execSQL(o["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                o["indices"]?.jsonArray?.forEach { i -> sdb.execSQL(i.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
            }
            db["setupQueries"]!!.jsonArray.forEach { sdb.execSQL(it.jsonPrimitive.content) }
            sdb.version = 1
        }
        // Room valida tablas y hash de identidad al abrir: si el esquema no coincide, falla
        assertTrue(DbGuard.check(c.context) is DbCheck.UpToDate)
        c.open()
        assertEquals(0, c.db.projects().count())
        c.repo.createProject("P", "Proyecto"); assertEquals(1, c.db.projects().count())
    }

    @Test fun versionSuperiorNoSeAbre() = runBlocking {
        c.repo.createProject("P", "Datos que no deben perderse")
        c.closeDb()
        val f = c.context.getDatabasePath(AppDatabase.NAME)
        SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version = 99 }
        val chk = c.open()
        assertTrue(chk is DbCheck.Incompatible)
        assertEquals(99, (chk as DbCheck.Incompatible).found)
        assertTrue(f.exists())
        SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READONLY).use { d -> d.rawQuery("SELECT COUNT(*) FROM projects", null).use { it.moveToFirst(); assertEquals(1, it.getInt(0)) } }
    }

    @Test fun evolucionDeEsquemaConCopiaPreviaYSinPerdida() = runBlocking {
        val id = c.repo.createProject("P", "Proyecto existente")
        c.closeDb()
        val f = c.context.getDatabasePath(AppDatabase.NAME)
        // Simula que una app futura (v2) encuentra una base v1: la guarda antes de migrar
        val check = DbGuard.check(c.context, supported = 2)
        assertTrue(check is DbCheck.NeedsMigration)
        val backupDir = (check as DbCheck.NeedsMigration).backupDir
        assertTrue(File(backupDir, AppDatabase.NAME).exists())
        // Migración de PRUEBA 1→2 (no forma parte de producción)
        SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READWRITE).use { d ->
            d.beginTransaction()
            try { d.execSQL("ALTER TABLE projects ADD COLUMN test_region TEXT"); d.execSQL("UPDATE projects SET test_region = 'N/A'"); d.version = 2; d.setTransactionSuccessful() } finally { d.endTransaction() }
            d.rawQuery("SELECT name, test_region FROM projects WHERE id = ?", arrayOf(id)).use { cur ->
                assertTrue(cur.moveToFirst()); assertEquals("Proyecto existente", cur.getString(0)); assertEquals("N/A", cur.getString(1))
            }
        }
        // la copia previa sigue siendo v1 e íntegra
        SQLiteDatabase.openDatabase(File(backupDir, AppDatabase.NAME).path, null, SQLiteDatabase.OPEN_READONLY).use { d ->
            assertEquals(1, d.version)
            d.rawQuery("PRAGMA integrity_check", null).use { it.moveToFirst(); assertEquals("ok", it.getString(0)) }
        }
        // y la app v1 actual se niega a abrir la base v2
        assertTrue(DbGuard.check(c.context) is DbCheck.Incompatible)
    }

    @Test fun noHayMigracionDestructiva() {
        assertEquals(0, AppDatabase.MIGRATIONS.size) // v1: sin rutas todavía; ver docs/MIGRACIONES.md
        val src = File("src/main/kotlin/com/geodensidad/app/data/AppDatabase.kt").readText()
        assertFalse(src.contains("fallbackToDestructiveMigration("))
    }
}
