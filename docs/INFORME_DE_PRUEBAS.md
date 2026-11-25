# Informe de pruebas (versión 1.1.0, 2026-11-25)

`./gradlew :core:test :app:testDebugUnitTest` → **60 pruebas, 0 fallos**. Compilan `assembleDebug` y `assembleRelease`. Entorno: OpenJDK 21, Android SDK 35, Robolectric 4.14.1. **Sin dispositivo físico ni emulador.**

| Suite | n | Cubre |
|---|---|---|
| ConoArenaLegacyTest | 11 | 10 densidades del Excel (P y S ±5,1·10⁻⁷), intermedios, promedio 89,907877 %, faltantes, ceros, negativos, divisores, no finitos |
| DecimalTextTest, AbscisaTest, DescriptiveTest, LotEvaluatorTest, CaptureTimeTest, BackupArchiveTest, ReportPdfTest | 41 | Números en español, estadística, criterio k(n), formato de respaldo, motor de PDF |
| RegistroTest | 6 | 10 densidades del Excel dentro de un registro, faltante ≠ cero, guardar y leer, PDF tipo planilla (valores y "NO CUMPLE"), resumen (Dm, s, k), respaldo y restauración |
| UiFlowTest | 2 | Interfaz real: nuevo registro → encabezado → digitar A…L → ver C…S (2.294,97 kg/m³, 91,98 %) → agregar densidad → resumen; lista y resumen con 10 densidades |

Limitaciones: el PDF de Android (`PdfDocument`) no corre en Robolectric (se probó la misma composición con PDFBox); abrir/compartir el PDF y el selector de archivos del respaldo no se probaron en un teléfono.
