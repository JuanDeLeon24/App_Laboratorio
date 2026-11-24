# Catálogo de fórmulas — motor `cono-arena-legacy-1.0.0`

Reproduce **aritméticamente** la hoja `Cono de arena` del Excel de origen. Reproducir el Excel **no** certifica validez técnica ni normativa.

## Entradas

| Símb. | Significado | Unidad / representación |
|---|---|---|
| A | Masa inicial de frasco y arena | g |
| B | Masa final de frasco y arena | g |
| D | Constante del cono | g |
| F | Densidad de la arena | g/cm³ |
| H | Masa retenida en tamiz ¾″ | g |
| I | Parámetro bulk (heredado) | número usado como I×1000 kg/m³ (ver nota) |
| J | Masa húmeda extraída | g |
| L | Humedad total | fracción (se digita en %: 6 → 0,06) |
| Q | Densidad seca máxima de laboratorio (De) | kg/m³ |
| R | Humedad óptima | fracción (se digita en %) |
| wc | Parámetro de humedad gruesa | 0,02 en el perfil heredado — **supuesto pendiente de validación** |

**Nota sobre I**: el Excel lo llama "gravedad específica bulk" pero muestra g/cm³. El modelo lo usa numéricamente como `I × 1000`. Requiere revisión técnica.

## Resultados

| Símb. | Fórmula | Unidad |
|---|---|---|
| C | A − B | g |
| E | C − D | g |
| G | E / F | cm³ |
| K | (H / J) × 100 | % (0–100) |
| M | (J / G) × 1000 | kg/m³ |
| N | ((100 × L) − (wc × K)) / (100 − K) | fracción |
| O | M / (1 + L) | kg/m³ |
| P | [O × (100 − K)] / [100 − ((O × K)/(I × 1000))] | kg/m³ (Di) |
| S | (P / Q) × 100 | % (0–100) |
| ΔW | (L − R) × 100 | puntos porcentuales (añadido; no está en el Excel) |

## Precisión y tolerancias

- Sin redondeo intermedio (IEEE-754 doble). El redondeo es solo de presentación (HALF_UP).
- Pruebas: valores publicados con 6 decimales comparados con tolerancia 5,1·10⁻⁷; intermedios de la muestra 1 con 10⁻⁹–10⁻¹⁴ frente a un cálculo independiente en Python.
- Muestra 1: P = 2.294,972397 kg/m³, S = 91,982862 %. Promedio de S de las 10: 89,907877 %.

## Validaciones

Bloqueantes (impiden "Completa"; el borrador puede guardarse): A ≤ B; D < 0; E ≤ 0; F, I, J, Q ≤ 0; H < 0; H ≥ J; L o R negativos; denominador de P ≤ 0; dato faltante (nunca se toma como cero); resultado no finito.
Advertencias de plausibilidad: F fuera de 1,2–1,8; I fuera de 2,0–3,2; L > 40 % (¿escala?) o muy baja; K > 50 %; S fuera de 70–110 %; G < 300 cm³.

Entrada en español: un solo separador decimal (coma o punto); se rechazan separadores de miles ("1.234,5") por ambiguos. Al invalidar una entrada, el valor queda faltante y los resultados dependientes dejan de mostrarse como vigentes.
