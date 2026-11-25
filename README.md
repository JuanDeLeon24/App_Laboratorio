# GeoDensidad 1.2

App Android sencilla que reproduce la hoja **'Cono de arena'** del formato Excel *Densidad o masa unitaria del suelo en el terreno – Método del cono de arena (I.N.V. E – 161)*, con la colorimetría de **Construcciones El Cóndor S.A.**

- **Un registro = una hoja del Excel**: encabezado + hasta 10 densidades + resumen + observaciones y firmas.
- **Solo se digitan las celdas que en el Excel son datos** (números fríos y textos). Las filas con fórmula se calculan solas.
- Funciona sin internet. Guarda automáticamente. Genera el PDF con forma de planilla.

## Qué se digita y qué se calcula

| Fila del Excel | En la app |
|---|---|
| Proyecto, Localización, Procedencia, PR inicial, PR final, Lado, Fecha, Parte del relleno, Lote, Registro | Se digitan (encabezado) |
| Capa No., Espesor capa, PR, Costado | Se digitan por densidad |
| **A** Peso frasco y arena inicial · **B** Peso frasco y arena restante · **D** Constante del cono · **F** Densidad de la arena · **H** Material retenido en 3/4 · **J** Peso material extraído húmedo · **L** Humedad | Se digitan por densidad (D y F se copian de la densidad anterior, como en el Excel) |
| **I** Gravedad específica bulk · **Q** Densidad máxima de laboratorio · **R** Humedad óptima | Se digitan en su fila dentro de la densidad; el valor es común a todas (I trae 2,667) |
| **C, E, G, K, M, N, O, P, S** | Se calculan con las fórmulas del Excel |
| Dm, s, k(n), Dm − k·s ≥ 0,95·De | Se calculan en el resumen con todas las densidades del registro |
| Observaciones, Elaboró, Aprobó | Se digitan |

Diferencias deliberadas con el Excel: la humedad L y R se digitan en porcentaje (6 para 6 %); el resumen usa todas las densidades del registro (el Excel mezclaba 5, 8 y 10 y tenía referencias rotas `#REF!`). Ver [docs/CRITERIOS.md](docs/CRITERIOS.md) y [docs/FORMULAS.md](docs/FORMULAS.md).

## Compilar

```bash
./gradlew :app:assembleDebug
```

APK en `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions lo genera en cada push (artefacto **GeoDensidad-apk-N**). Para que las actualizaciones se instalen encima, configure la firma fija: [docs/INSTALACION_Y_ACTUALIZACION.md](docs/INSTALACION_Y_ACTUALIZACION.md). Pruebas: `./gradlew :core:test :app:testDebugUnitTest` (62 pruebas).

## Estructura

- `core/` — motor de cálculo (fórmulas del Excel), estadística, composición del PDF y formato de respaldo. Kotlin puro, probado sin dispositivo.
- `app/` — 2 pantallas (lista de registros y registro), base de datos local Room (tabla `registros`), PDF y respaldo.

Documentación: [MANUAL_DE_CAMPO](docs/MANUAL_DE_CAMPO.md) · [FORMULAS](docs/FORMULAS.md) · [CRITERIOS](docs/CRITERIOS.md) · [INSTALACION_Y_ACTUALIZACION](docs/INSTALACION_Y_ACTUALIZACION.md) · [DEPENDENCIAS](docs/DEPENDENCIAS.md) · [INFORME_DE_PRUEBAS](docs/INFORME_DE_PRUEBAS.md) · [LIMITACIONES](docs/LIMITACIONES.md) · [CHANGELOG](CHANGELOG.md)
