# Respaldo y restauración

## Contenido del respaldo (`.zip`, formato v1, **no cifrado**)

- `db/geodensidad.db` (+ `-wal` si lo hay), copiados juntos con el bloqueo de escritura tomado: datos, revisiones, historial, campos personalizados, criterios, instantáneas de cálculo, catálogos y plantillas.
- `files/evidence/original|thumb|pdf/` — fotografías originales y derivados.
- `files/reports/` — PDF emitidos e instantáneas JSON.
- `files/template/` — imágenes institucionales por versión. `files/labdocs/` — documentos de laboratorio.
- `files/datastore/` — configuración.
- `manifest.json` — versiones (app, base, motor, plantilla, formato), conteos, directorio de origen y SHA-256 + tamaño de cada archivo.

Crear: Menú → Respaldos → *Guardar respaldo…* (elija una ubicación externa al teléfono).

## Restauración

Menú → Respaldos → *Seleccionar respaldo…*:

1. Lectura del manifiesto y comprobación del formato (se rechazan versiones superiores).
2. Extracción en área temporal rechazando rutas inseguras (`..`, absolutas), duplicados, archivos no declarados y tamaños excesivos.
3. Verificación de tamaño y SHA-256 de cada archivo.
4. Apertura de la base temporal: `integrity_check`, versión, `foreign_key_check`, conteos, reubicación de rutas y verificación de archivos referenciados.
5. Resumen y confirmación.
6. Respaldo automático previo en `files/backups/auto/`.
7. Sustitución controlada (los datos actuales se apartan y se devuelven si algo falla) y reinicio de la app.

La restauración **reemplaza** el conjunto local completo; no fusiona.

## Situaciones

| Situación | Resultado |
|---|---|
| Actualizar la app | Conserva los datos |
| Desinstalar / borrar datos | Se pierde todo lo local |
| Pérdida del dispositivo | Solo se recupera lo que esté en un respaldo externo |
| Sin respaldo | No hay forma de recuperar información eliminada |

Ejemplo: `docs/ejemplos/respaldo_demostrativo.geodensidad.zip` (datos demostrativos).
