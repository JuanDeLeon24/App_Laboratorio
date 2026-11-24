# Esquemas y migraciones

- Versión actual de la base: **1**. Esquema exportado y versionado en `app/schemas/` (Room `exportSchema = true`).
- `AppDatabase.MIGRATIONS` contiene las rutas explícitas; hoy está vacío porque solo existe la v1.
- **Prohibido** `fallbackToDestructiveMigration` (una prueba lo verifica).

## Al abrir la app (`DbGuard`)

1. Se lee `PRAGMA user_version` en modo solo lectura **sin abrir Room**.
2. Si la versión es **mayor** que la soportada → no se abre; pantalla de diagnóstico (no hay retroceso de versión).
3. Si es **menor** → se copian base, `-wal` y `-shm` (con la base cerrada, copia consistente) a `files/backups/pre-migracion/v<N>-<fecha>/` y luego Room aplica las migraciones.
4. Si la migración falla → pantalla de diagnóstico; los archivos se conservan.

## Para crear la versión 2

1. Cambiar entidades, subir `version = 2`, compilar para exportar `2.json`.
2. Escribir `Migration(1, 2)` con SQL explícito y añadirla a `MIGRATIONS`.
3. Añadir prueba con base creada desde `1.json` + datos → migrar → validar (patrón de `DatabaseSchemaTest`).
4. Incrementar `versionCode`; actualizar `docs/DICCIONARIO_DE_DATOS.md` y el CHANGELOG.

La infraestructura (copia previa, rechazo de versión superior, preservación de datos) se prueba con una migración **solo de prueba** que no forma parte del esquema de producción.

Las muestras aprobadas no se recalculan nunca: su instantánea `calc_json` y su versión de motor se conservan. Los PDF emitidos son archivos inmutables con hash.
