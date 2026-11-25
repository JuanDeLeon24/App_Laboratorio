# Inventario de dependencias y licencias

Todas las versiones están fijadas en `gradle/libs.versions.toml` (sin rangos dinámicos).

| Dependencia | Versión | Licencia | Uso |
|---|---|---|---|
| Kotlin stdlib / plugin | 2.0.21 | Apache 2.0 | Lenguaje |
| kotlinx-coroutines | 1.9.0 | Apache 2.0 | Concurrencia |
| kotlinx-serialization-json | 1.7.3 | Apache 2.0 | JSON (formularios, instantáneas, manifiesto) |
| AndroidX Core KTX | 1.15.0 | Apache 2.0 | |
| Activity Compose | 1.9.3 | Apache 2.0 | |
| Lifecycle (ViewModel, runtime-compose) | 2.8.7 | Apache 2.0 | |
| Compose BOM (UI, Material 3, iconos) | 2024.12.01 | Apache 2.0 | Interfaz |
| Navigation Compose | 2.8.5 | Apache 2.0 | Navegación |
| Room (runtime, ktx, compiler) | 2.6.1 | Apache 2.0 | Persistencia SQLite |
| DataStore Preferences | 1.1.1 | Apache 2.0 | Preferencias |
| android.graphics.pdf.PdfDocument | plataforma | Apache 2.0 (AOSP) | Generación PDF en la app |

Solo pruebas: JUnit 4.13.2 (EPL 1.0), Robolectric 4.14.1 (MIT), AndroidX Test core 1.6.1 (Apache 2.0), Room testing 2.6.1, Compose UI test (Apache 2.0), Apache PDFBox 3.0.3 (Apache 2.0), fuentes DejaVu (licencia libre Bitstream Vera/DejaVu, ver `core/src/testFixtures/resources/geodensidad-test-fonts/LICENSE-DejaVu.txt`).

Recursos gráficos: logotipo de Construcciones El Cóndor S.A. e imagen de la fórmula de desviación estándar, **extraídos del Excel suministrado** (`xl/media/image2.png` y `image1.emf` convertido a PNG). Su uso corresponde al titular.
