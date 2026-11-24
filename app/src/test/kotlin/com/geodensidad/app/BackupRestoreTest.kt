package com.geodensidad.app

import com.geodensidad.core.backup.BackupArchive
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class BackupRestoreTest {
    private lateinit var c: AppContainer
    @Before fun setup() { c = TestSupport.freshContainer() }

    @Test fun respaldarYRestaurarDatosFotosEInformes() = runBlocking {
        val pid = c.demo.load()
        val act = c.db.activities().listOpenIds().first()
        val s = c.db.samples().listByActivity(act).first()
        val img = TestSupport.jpeg(File(c.context.cacheDir, "ext/a.jpg"), 640, 480)
        val photoId = c.evidence.importFromUris(listOf(android.net.Uri.fromFile(img)), act, s.id, null).imported.single()
        val photoHash = c.db.photos().get(photoId)!!.sha256
        val zip = File(c.context.cacheDir, "respaldo.zip")
        val manifest = zip.outputStream().use { c.backups.exportTo(it) }
        assertFalse(manifest.encrypted)
        assertEquals(1, manifest.format)
        assertTrue(manifest.files.any { it.path.startsWith("files/evidence/original/") })
        assertTrue(manifest.files.any { it.path == "db/geodensidad.db" })

        // cambios posteriores que la restauración debe revertir
        c.repo.createProject("X", "Proyecto posterior al respaldo")
        assertEquals(2, c.db.projects().count())

        val staging = File(c.context.cacheDir, "restore-test").apply { deleteRecursively(); mkdirs() }
        val m = zip.inputStream().use { BackupArchive.extractAndVerify(it, staging) }
        val plan = c.backups.validateStaging(staging, m)
        assertTrue(plan.summary.any { it.first == "Muestras" && it.second == "10" })
        val pre = c.backups.applyRestore(plan)
        assertTrue("se crea respaldo previo", pre.exists())
        runBlocking { c.open() }
        assertEquals(1, c.db.projects().count())
        assertTrue(c.db.projects().get(pid)!!.isDemo)
        assertEquals(10, c.db.samples().count())
        val p = c.db.photos().get(photoId)!!
        assertEquals(photoHash, BackupArchive.sha256(File(p.originalPath)))
    }

    @Test fun respaldoConVersionDeBaseSuperiorSeRechaza() = runBlocking {
        val zip = File(c.context.cacheDir, "r.zip")
        val m = zip.outputStream().use { c.backups.exportTo(it) }
        val staging = File(c.context.cacheDir, "st2").apply { deleteRecursively(); mkdirs() }
        zip.inputStream().use { BackupArchive.extractAndVerify(it, staging) }
        try { c.backups.validateStaging(staging, m.copy(dbSchemaVersion = 99)); fail() } catch (e: Exception) { assertTrue(e.message!!.contains("v99")) }
    }
}
