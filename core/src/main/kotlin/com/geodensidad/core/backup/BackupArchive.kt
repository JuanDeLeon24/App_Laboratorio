package com.geodensidad.core.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Formato de respaldo GeoDensidad v1: archivo ZIP con "manifest.json" y los archivos listados
 * en él, cada uno con tamaño y SHA-256. El respaldo NO está cifrado.
 */
@Serializable
data class BackupManifest(
    val format: Int,
    val appVersionName: String,
    val appVersionCode: Int,
    val dbSchemaVersion: Int,
    val engineVersion: String,
    val templateVersion: String,
    val createdAt: String,
    val encrypted: Boolean = false,
    val counts: Map<String, Int> = emptyMap(),
    val files: List<BackupFile>,
)

@Serializable
data class BackupFile(val path: String, val size: Long, val sha256: String)

class BackupException(message: String) : Exception(message)

object BackupArchive {
    const val FORMAT = 1
    const val MANIFEST = "manifest.json"
    val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun sha256(file: File): String = file.inputStream().use { sha256(it) }
    fun sha256(input: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Ruta relativa segura: sin absolutas, sin "..", sin barras invertidas ni caracteres de control. */
    fun isSafePath(path: String): Boolean {
        if (path.isEmpty() || path.length > 512) return false
        if (path.startsWith("/") || path.contains('\\') || path.contains(':')) return false
        if (path.any { it.code < 0x20 }) return false
        val parts = path.split('/')
        return parts.none { it.isEmpty() || it == "." || it == ".." }
    }

    /**
     * Escribe el respaldo. [entries] = ruta dentro del archivo → archivo local.
     * El manifiesto se escribe al final con los hashes calculados durante la copia.
     */
    fun write(out: OutputStream, entries: Map<String, File>, manifestFor: (List<BackupFile>) -> BackupManifest): BackupManifest {
        val files = mutableListOf<BackupFile>()
        ZipOutputStream(out.buffered()).use { zip ->
            for ((path, file) in entries.toSortedMap()) {
                if (!isSafePath(path) || path == MANIFEST) throw BackupException("Ruta no permitida en respaldo: $path")
                zip.putNextEntry(ZipEntry(path))
                val md = MessageDigest.getInstance("SHA-256")
                var size = 0L
                file.inputStream().use { inp ->
                    val buf = ByteArray(64 * 1024)
                    while (true) { val n = inp.read(buf); if (n < 0) break; md.update(buf, 0, n); zip.write(buf, 0, n); size += n }
                }
                zip.closeEntry()
                files += BackupFile(path, size, md.digest().joinToString("") { "%02x".format(it) })
            }
            val manifest = manifestFor(files)
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            return manifest
        }
    }

    /**
     * Extrae en [dest] (área temporal vacía) y verifica: manifiesto, versión de formato,
     * rutas seguras, ausencia de archivos no declarados o duplicados, tamaños y SHA-256.
     */
    fun extractAndVerify(input: InputStream, dest: File, maxTotalBytes: Long = 8L * 1024 * 1024 * 1024): BackupManifest {
        require(dest.isDirectory && (dest.list()?.isEmpty() == true)) { "El área temporal debe existir y estar vacía" }
        val seen = HashSet<String>()
        var total = 0L
        var manifestBytes: ByteArray? = null
        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = e.name
                    if (e.isDirectory) continue
                    if (!isSafePath(name)) throw BackupException("El respaldo contiene una ruta insegura: $name")
                    if (!seen.add(name)) throw BackupException("Entrada duplicada en el respaldo: $name")
                    if (name == MANIFEST) { manifestBytes = zip.readBytes().also { if (it.size > 32 * 1024 * 1024) throw BackupException("Manifiesto demasiado grande") }; continue }
                    val target = File(dest, name)
                    if (!target.canonicalPath.startsWith(dest.canonicalPath + File.separator)) throw BackupException("Ruta fuera del área temporal: $name")
                    target.parentFile?.mkdirs()
                    target.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = zip.read(buf); if (n < 0) break
                            total += n
                            if (total > maxTotalBytes) throw BackupException("El respaldo excede el tamaño máximo permitido")
                            o.write(buf, 0, n)
                        }
                    }
                }
            }
        } catch (e: BackupException) { throw e } catch (e: Exception) {
            throw BackupException("Archivo de respaldo ilegible o dañado: ${e.message ?: e.javaClass.simpleName}")
        }
        val mb = manifestBytes ?: throw BackupException("El archivo no contiene manifest.json: no es un respaldo de GeoDensidad")
        val manifest = try { json.decodeFromString(BackupManifest.serializer(), mb.toString(Charsets.UTF_8)) } catch (e: Exception) {
            throw BackupException("Manifiesto ilegible: ${e.message}")
        }
        if (manifest.format > FORMAT) throw BackupException("Formato de respaldo ${manifest.format} no soportado por esta versión (máximo $FORMAT). Actualice la aplicación.")
        if (manifest.format < 1) throw BackupException("Formato de respaldo inválido")
        val declared = manifest.files.associateBy { it.path }
        val extra = seen - MANIFEST - declared.keys
        if (extra.isNotEmpty()) throw BackupException("Archivos no declarados en el manifiesto: ${extra.take(3)}")
        for (f in manifest.files) {
            if (!isSafePath(f.path)) throw BackupException("Ruta insegura en manifiesto: ${f.path}")
            val file = File(dest, f.path)
            if (!file.isFile) throw BackupException("Falta el archivo declarado: ${f.path}")
            if (file.length() != f.size) throw BackupException("Tamaño incorrecto: ${f.path}")
            if (sha256(file) != f.sha256) throw BackupException("Hash SHA-256 no coincide: ${f.path}")
        }
        return manifest
    }
}
