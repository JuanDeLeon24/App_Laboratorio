# Informe de pruebas ejecutadas (2026-11-24)

Entorno: Linux x86_64, OpenJDK 21.0.13, Gradle 8.14.3, Android SDK 35, Robolectric 4.14.1 (SDK 34, SQLite y gráficos nativos). **Sin dispositivo ni emulador** (no hay virtualización disponible).

Comando: `./gradlew :core:test :app:testDebugUnitTest` → **76 pruebas, 0 fallos**. También compilan `assembleDebug` y `assembleRelease`.

| Suite | n | Qué cubre |
|---|---|---|
| ConoArenaLegacyTest | 11 | 10 muestras de referencia (P y S), intermedios de la muestra 1, promedios (89,907877 %), media y s de 5, faltantes ≠ cero, A ≤ B, E ≤ 0, divisores ≤ 0, H negativo / H ≥ J, denominador de P, no finitos, humedad en escala equivocada |
| DecimalTextTest | 7 | Coma decimal, punto, vacío, cero, ambiguos, % ↔ fracción, formato español |
| AbscisaTest | 2 | Abscisas interpretables y no interpretables |
| DescriptiveTest | 5 | Vacío, n = 1 (s no disponible), n = 2, no finitos, histograma con pocos datos |
| LotEvaluatorTest | 10 | 5 y 10 muestras, k inexistente, n = 1 y 2, vacío, exclusiones, pendientes, poblaciones incompatibles, criterio validado, sin criterio |
| CaptureTimeTest | 6 | Cámara, EXIF con y sin zona, sin EXIF (no usa importación), manual, datos propios contra heredados |
| BackupArchiveTest | 7 | Ida y vuelta, rutas inseguras, archivo corrupto o truncado, hash alterado, versión no soportada, archivo no declarado, sin manifiesto |
| ReportPdfTest | 4 | PDF individual borrador (marca, valores, paginación, acentos, pie de foto distinto de emisión), aprobado sin fotos con texto largo, 7 fotos verticales/horizontales, global con estadísticas y gráficos |
| WorkflowTest | 8 | Dos actividades alternando borradores + reapertura, 10 muestras vía app (89,907877 %), lotes y exclusión, aprobación bloquea / nueva revisión conserva la anterior, edición vuelve a borrador e invalida resultado, duplicar sin mediciones, campo personalizado obligatorio y persistente con versionado, catálogo no altera muestras, lote único |
| EvidenceTest | 6 | Cámara (hora dispositivo, hash, original), galería sin EXIF, EXIF sin zona + orientación + archivo externo eliminado + rotación solo en derivados, fecha manual con motivo, imagen corrupta, persistencia tras reinicio |
| BackupRestoreTest | 2 | Respaldo → cambios → restauración (datos, fotos con mismo hash, respaldo previo); versión de base superior rechazada |
| DatabaseSchemaTest | 4 | Esquema exportado = entidades (Room valida), versión superior no se abre, evolución con copia previa sin pérdida, sin migración destructiva |
| ReportServiceTest | 1 | Emisión individual y global, hash, instantánea, informe emitido inmutable tras cambios, nuevo informe en borrador |
| UiFlowTest | 2 | Interfaz Compose real: inicio → proyecto → actividad → muestra, captura de 9 mediciones, resultado 2.294,97 kg/m³ / 91,98 %, trazabilidad, guardado automático; panel, lista y lotes con datos demostrativos |
| ExamplesTest | 1 | Genera los ejemplos de `docs/ejemplos/` |

## Fallos encontrados y corregidos durante las pruebas

- Los servicios conservaban una referencia a la base cerrada tras cerrar y reabrir (afectaba la restauración en el mismo proceso) → se recrean al reabrir.
- El texto de los botones flotantes de Material 3 no era accesible a lectores de pantalla → etiqueta accesible añadida.
- El resultado de apertura de la base podía publicarse fuera del hilo principal → ahora se publica en el `lifecycleScope`.

## Limitaciones de las pruebas

- `PdfDocument` (backend PDF de Android) **no** puede ejecutarse en Robolectric: en las pruebas de la app se inyecta PDFBox sobre la misma composición. El PDF real de Android queda **sin probar** hasta ejecutarlo en un dispositivo.
- La cámara se simula (archivo JPEG); no se probó la app de cámara real, los permisos de GPS ni el selector de fotos.
- Las pruebas de interfaz crean proyecto y actividad por repositorio (los diálogos se probaron aislados); los campos se llenan por reemplazo de texto, sin teclado real.
- No se midió rendimiento (10.000 muestras / miles de fotos es un objetivo de diseño, no una medición).
- No se probó actualizar un APK instalado sobre otro en un dispositivo real.
