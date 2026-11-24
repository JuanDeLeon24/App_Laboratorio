# Arquitectura

## Herramientas (fijadas en `gradle/libs.versions.toml`)

| Elemento | Versión |
|---|---|
| JDK de compilación | 17+ (bytecode 17; probado con OpenJDK 21) |
| Gradle (wrapper) | 8.14.3 |
| Android Gradle Plugin | 8.7.3 |
| Kotlin / KSP | 2.0.21 / 2.0.21-1.0.28 |
| SDK mínimo / compilación / objetivo | 26 / 35 / 35 |
| Paquete (estable) | `com.geodensidad.app` (debug: `com.geodensidad.app.debug`) |

## Capas

```
core (Kotlin JVM puro, sin Android)
 ├─ calc/      Motor cono-arena-legacy (entradas → pasos trazables, incidencias)
 ├─ number/    Interpretación de decimales en español y formato de presentación
 ├─ stats/     Estadística descriptiva, histograma honesto
 ├─ criteria/  Criterio Dm − k(n)·s ≥ α·De versionado; evaluación de lotes
 ├─ photo/     Fechas de captura (EXIF/dispositivo/manual) y pies de foto
 ├─ report/    Motor de composición PDF independiente de plataforma + gráficos
 ├─ backup/    Formato ZIP + manifiesto + SHA-256; extracción segura
 └─ demo/      10 muestras de referencia (solo datos demostrativos)

app (Android)
 ├─ data/      Room v1 (entidades, DAO), DbGuard (versión antes de abrir)
 ├─ domain/    GeoRepository (estados, revisiones, lotes, catálogos, auditoría), ajustes, demo
 ├─ evidence/  Almacenamiento de fotografías y derivados
 ├─ report/    Superficie Canvas (PdfDocument y gráficos en pantalla), servicio de informes, plantillas
 ├─ backup/    Exportación y restauración
 └─ ui/        Compose + Material 3, ViewModel con flujo unidireccional (StateFlow)
```

## Decisiones

- **Inyección de dependencias manual** (`AppContainer`, inyección por constructor). Evita generación de código adicional y permite cerrar y reabrir la base durante la restauración; los servicios se recrean al reabrir. Puede migrarse a Hilt sin cambiar el dominio.
- **PDF**: `android.graphics.pdf.PdfDocument` (parte de AOSP, Apache 2.0, sin dependencia externa, texto vectorial). La composición vive en `core` y dibuja sobre una interfaz `PdfSurface`; en pruebas JVM se usa PDFBox (Apache 2.0) sobre la misma composición.
- **Números**: entradas almacenadas como decimal canónico en texto (`BigDecimal.toPlainString`); cálculo en `Double` sin redondeos intermedios; resultados no finitos rechazados.
- **Local y monodispositivo**: sin servidor ni cuenta. Punto de extensión futuro para sincronización: UUID como clave, auditoría por entidad y marcas `updated_at` en todas las tablas; no se implementa sincronización ficticia.
- **Gráficos**: el mismo renderizador vectorial (`ChartRenderer`) dibuja en pantalla y en el PDF.
- Tema claro de alto contraste para uso a pleno sol.
