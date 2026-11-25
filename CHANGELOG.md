# Changelog

## 1.2.x — 2026-11-25
- I, Q y R se digitan dentro de cada densidad, en su fila del Excel (H → I → J … P → Q → R → S); valen igual para todas las densidades.
- Cuando S no se puede calcular, la app dice por qué (p. ej. falta Q).
- Versión automática por compilación y firma fija desde los Secrets de GitHub: las actualizaciones se instalan encima sin desinstalar.
- Verificado contra 'Densidades_toyo (1).xlsx' (L₁ = 0,061; Q = 2395): P y S idénticos a los valores guardados por Excel.

## 1.1.0 (versionCode 2) — 2026-11-25 — App simplificada
- La app reproduce directamente la hoja 'Cono de arena': un registro = una hoja (encabezado, hasta 10 densidades, resumen, observaciones y firmas).
- Solo se digitan las celdas de datos del Excel; C, E, G, K, M, N, O, P y S se calculan.
- Dos pantallas: lista de registros y registro. Sin secciones plegables.
- PDF con forma de planilla (filas A–S × densidades) y resumen con el criterio del formato.
- Se retiraron proyectos, actividades, jornadas, catálogos, lotes, estados, revisiones, fotografías, campos personalizados, historial e informe global.
- Base de datos nueva (`geodensidad_registros.db`): no reutiliza datos de la 1.0.0.

## 1.0.0 — ajuste posterior
- El formulario de muestra pide exactamente los campos del Excel 'Cono de arena' (encabezado, densidad, filas A–R, irregularidades >15 mm, observaciones, elaboró, aprobó), con sus nombres y orden. Nuevos campos: PR inicial, PR final, Lado, Registro, N° irregularidades >15 mm y Aprobó. El PDF individual los incluye.
- La fecha del ensayo es solo fecha, como en el formato.

## 1.0.0 (versionCode 1) — 2026-11-24

Versiones independientes: app 1.0.0 · base de datos v1 · motor `cono-arena-legacy-1.0.0` · plantilla PDF 1.0.0 · formato de respaldo v1 · criterio `LEGACY-DM-KS` v1.0.0 (no validado).

- Proyectos, actividades simultáneas (abierta/pausada/cerrada/archivada), jornadas, muestras y lotes explícitos.
- Captura por secciones con guardado automático, teclado decimal, coma decimal, unidades visibles y humedad en porcentaje.
- Motor de cálculo heredado con trazabilidad (fórmula, sustitución, dependencias) y validaciones bloqueantes y de plausibilidad.
- Estados Borrador/Completa/Revisada/Aprobada/Anulada; revisiones aprobadas bloqueadas; nuevas revisiones con motivo.
- Catálogos (materiales, equipos, calibraciones, referencias de laboratorio, responsables, criterios) con instantáneas por revisión.
- Evidencias fotográficas (cámara/galería) con original inmutable, SHA-256, derivados, fechas EXIF/dispositivo/manual y pies editables.
- PDF individual y global (A4, encabezado institucional, paginación, marca BORRADOR, gráficos, anexo fotográfico), con instantánea y hash.
- Panel con indicadores y gráficos; estadística por grupos compatibles; evaluación de lotes provisional.
- Campos personalizados versionados; historial de auditoría.
- Respaldo completo verificable y restauración controlada con respaldo previo.
