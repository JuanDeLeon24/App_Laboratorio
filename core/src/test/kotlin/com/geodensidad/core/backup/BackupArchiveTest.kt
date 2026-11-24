package com.geodensidad.core.backup

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun manifest(files: List<BackupFile>, format: Int = 1) = BackupManifest(format, "1.0.0", 1, 1, "cono-arena-legacy-1.0.0", "1.0.0", "2026-11-24T10:00:00-05:00", files = files)

    private fun sampleBackup(): ByteArray {
        val src = tmp.newFolder("src")
        val db = File(src, "db").apply { writeText("SQLite format 3 simulado") }
        val photo = File(src, "p.jpg").apply { writeBytes(ByteArray(5000) { (it % 251).toByte() }) }
        val out = ByteArrayOutputStream()
        BackupArchive.write(out, mapOf("db/geodensidad.db" to db, "files/evidence/original/a.jpg" to photo)) { manifest(it) }
        return out.toByteArray()
    }

    @Test fun idaYVuelta() {
        val dest = tmp.newFolder("dest")
        val m = BackupArchive.extractAndVerify(ByteArrayInputStream(sampleBackup()), dest)
        assertEquals(2, m.files.size)
        assertFalse(m.encrypted)
        assertEquals("SQLite format 3 simulado", File(dest, "db/geodensidad.db").readText())
    }

    @Test fun rutasInseguras() {
        for (p in listOf("../x", "/etc/passwd", "a/../../b", "a\\b", "C:/x", "a//b", "./a", "")) assertFalse(p, BackupArchive.isSafePath(p))
        assertTrue(BackupArchive.isSafePath("files/evidence/a.jpg"))
        val bad = zip("../evil.txt" to "x".toByteArray(), "manifest.json" to "{}".toByteArray())
        val e = runCatching { BackupArchive.extractAndVerify(ByteArrayInputStream(bad), tmp.newFolder()) }.exceptionOrNull()
        assertTrue(e is BackupException && e.message!!.contains("insegura"))
    }

    @Test fun archivoCorrupto() {
        val e = runCatching { BackupArchive.extractAndVerify(ByteArrayInputStream("no soy zip".toByteArray()), tmp.newFolder()) }.exceptionOrNull()
        assertTrue(e is BackupException)
        val truncated = sampleBackup().copyOf(200)
        assertTrue(runCatching { BackupArchive.extractAndVerify(ByteArrayInputStream(truncated), tmp.newFolder()) }.exceptionOrNull() is BackupException)
    }

    @Test fun hashAlterado() {
        val m = manifest(listOf(BackupFile("a.txt", 3, "0".repeat(64))))
        val z = zip("a.txt" to "abc".toByteArray(), "manifest.json" to BackupArchive.json.encodeToString(BackupManifest.serializer(), m).toByteArray())
        val e = runCatching { BackupArchive.extractAndVerify(ByteArrayInputStream(z), tmp.newFolder()) }.exceptionOrNull()
        assertTrue(e?.message, e is BackupException && e.message!!.contains("SHA-256"))
    }

    @Test fun versionNoSoportada() {
        val m = manifest(emptyList(), format = 99)
        val z = zip("manifest.json" to BackupArchive.json.encodeToString(BackupManifest.serializer(), m).toByteArray())
        val e = runCatching { BackupArchive.extractAndVerify(ByteArrayInputStream(z), tmp.newFolder()) }.exceptionOrNull()
        assertTrue(e is BackupException && e.message!!.contains("no soportado"))
    }

    @Test fun archivoNoDeclarado() {
        val m = manifest(emptyList())
        val z = zip("intruso.bin" to byteArrayOf(1), "manifest.json" to BackupArchive.json.encodeToString(BackupManifest.serializer(), m).toByteArray())
        assertTrue(runCatching { BackupArchive.extractAndVerify(ByteArrayInputStream(z), tmp.newFolder()) }.exceptionOrNull() is BackupException)
    }

    @Test fun sinManifiesto() {
        val z = zip("a.txt" to "abc".toByteArray())
        val e = runCatching { BackupArchive.extractAndVerify(ByteArrayInputStream(z), tmp.newFolder()) }.exceptionOrNull()
        assertTrue(e is BackupException && e.message!!.contains("manifest"))
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return out.toByteArray()
    }
}
