# Criterios de evaluación (provisionales)

## Criterio heredado `LEGACY-DM-KS` v1.0.0 — NO VALIDADO

`Dm − k(n)·s ≥ α·De`, con α = 0,95 y tabla transcrita del Excel ('Cono de arena'!C43:I44):

| n | 5 | 6 | 7 | 8 | 9 | 10 |
|---|---|---|---|---|---|---|
| k | 0,685 | 0,602 | 0,544 | 0,500 | 0,465 | 0,437 |

El formato cita "ESPECIFICACIÓN: ARTÍCULO 220.5.2.2" e "I.N.V. E – 161"; **no se verificó** contra ninguna norma.

Reglas implementadas:
- Sin coeficientes para otros n; sin interpolación ni extrapolación → "No evaluable".
- n, media y desviación se calculan sobre exactamente las mismas muestras incluidas.
- Excluidas (con motivo) y pendientes (borrador, anulada, con errores) no entran.
- Lotes con distinta De, referencia de laboratorio o material → "No evaluable".
- s con n < 2 → "no disponible".
- Mientras `validated = false`, el resultado se rotula "Evaluación provisional; criterio no validado".
- "Di individuales ≥ α·De" es un indicador descriptivo y **no** es aceptación del lote.

Para habilitar un criterio validado: Catálogos → Criterios → nueva versión con fuente documental, edición, artículo, responsable y fecha de habilitación.

## Auditoría del Excel de origen (no se reproduce)

| Hallazgo | Tratamiento en la app |
|---|---|
| `M53`, `H39`, `N31` con `#REF!` (coeficiente k roto) | Tabla k explícita y versionada |
| `E41`/`K41` usan muestras 1–5 y `N53` cuenta 10 | Lotes explícitos; n coherente |
| `M29`/`M33` promedian muestras 3–10 (89,31 % frente a 89,91 % de las 10) | Siempre se indica el alcance (n) |
| `N37` mezcla 5 resultados con una media de 8 | Desviación sobre la misma población |
| wc = 0,02 incrustado | Parámetro de perfil, rotulado no validado |
| `N29 = 1+0,033` sin uso documentado | No se reproduce |
| 12 consultas a un archivo externo (SharePoint) | Sin dependencia externa |
