# GeoDensidad 1.0.0

Aplicación Android nativa, local y sin conexión, para gestionar ensayos de **densidad en el terreno por cono de arena**: proyectos → actividades → jornadas → muestras, lotes de evaluación, evidencias fotográficas, cálculo trazable, estadísticas, informes PDF, respaldo y restauración.

Identidad visual: colorimetría de **Construcciones El Cóndor S.A.** (azul marino `#0B294B`, marino profundo `#041B37`, acero `#435667`, pizarra `#6E7F8F`, celeste `#B5CCEB`, niebla `#D1DCE5`), muestreada del logotipo incluido en el Excel de origen.

## Estado (honesto)

| Aspecto | Estado |
|---|---|
| Compilación debug y release (sin firmar) | ✅ Compila sin errores (`assembleDebug`, `assembleRelease`) |
| Pruebas automatizadas | ✅ 76 pruebas ejecutadas, 0 fallos (ver [docs/INFORME_DE_PRUEBAS.md](docs/INFORME_DE_PRUEBAS.md)) |
| Reproducción de las 10 muestras del Excel | ✅ Dentro de ±5,1·10⁻⁷ |
| Prueba en dispositivo físico o emulador | ❌ **No realizada** (el entorno de desarrollo no tiene virtualización). Pendiente antes de uso en campo |
| Validez normativa de fórmulas y criterio | ❌ **No validada**: reproducción aritmética del Excel, rotulada como provisional |

## Compilar

Requisitos: JDK 17 o superior (probado con JDK 21), Android SDK con `platforms;android-35` y `build-tools;35.0.0`.

```bash
./gradlew :core:test :app:testDebugUnitTest
```

```bash
./gradlew :app:assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`. Para producción ver [docs/INSTALACION_Y_ACTUALIZACION.md](docs/INSTALACION_Y_ACTUALIZACION.md).

## Estructura

- `core/` — Kotlin puro (sin Android): motor de cálculo, números, estadística, criterios, composición de PDF, formato de respaldo. Probado en JVM.
- `app/` — Android: Room, DataStore, evidencias, informes (PdfDocument), respaldos, interfaz Compose/Material 3.
- `app/schemas/` — esquemas Room exportados y versionados.
- `docs/` — documentación y `docs/ejemplos/` (PDF, respaldo y capturas con datos DEMOSTRATIVOS).

## Documentación

| Documento | Contenido |
|---|---|
| [ARQUITECTURA](docs/ARQUITECTURA.md) | Capas, decisiones, versiones de herramientas |
| [FORMULAS](docs/FORMULAS.md) | Catálogo de fórmulas, precisión, validaciones |
| [CRITERIOS](docs/CRITERIOS.md) | Criterio provisional heredado y auditoría del Excel |
| [DICCIONARIO_DE_DATOS](docs/DICCIONARIO_DE_DATOS.md) | Tablas y columnas |
| [MIGRACIONES](docs/MIGRACIONES.md) | Estrategia de esquemas y actualizaciones |
| [INSTALACION_Y_ACTUALIZACION](docs/INSTALACION_Y_ACTUALIZACION.md) | Instalación, firma, actualización |
| [RESPALDO_Y_RESTAURACION](docs/RESPALDO_Y_RESTAURACION.md) | Procedimiento de respaldo y recuperación |
| [MANUAL_DE_CAMPO](docs/MANUAL_DE_CAMPO.md) | Uso breve en campo |
| [DEPENDENCIAS](docs/DEPENDENCIAS.md) | Inventario y licencias |
| [INFORME_DE_PRUEBAS](docs/INFORME_DE_PRUEBAS.md) | Pruebas ejecutadas y limitaciones |
| [LIMITACIONES](docs/LIMITACIONES.md) | Lista concreta de limitaciones |
| [CHANGELOG](CHANGELOG.md) | Historial de versiones |
