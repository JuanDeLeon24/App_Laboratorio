# Manual de campo

1. **Nuevo registro** (botón inferior). Un registro es una hoja del formato.
2. **Encabezado**: Proyecto, Localización, Procedencia, PR inicial, PR final, Lado, Fecha, Parte del relleno, Lote, Registro.
4. **Densidades**: los botones 1, 2, 3… eligen la densidad; *Agregar* crea la siguiente (máximo 10; copia D y F de la anterior).
   Digite Capa, Espesor, PR, Costado y A, B, D, F, H, I, J, L, Q, R en el mismo orden del Excel. I, Q y R valen igual para todas las densidades (al cambiarlos en una, cambian en todas). Las filas grises (C, E, G, K, M, N, O) y las azules (P y S) se calculan solas. Si S no aparece, la app indica qué falta (normalmente Q).
   - L y R en decimal, como en el Excel: `0,061` y `0,0668`.
   - En A, B, D, H, J y Q puede usar punto de miles (`2.395` = 2395). Debajo de cada campo aparece el valor que entendió la app.
   - Un campo vacío es un dato faltante, no un cero.
5. **Resumen del registro**: tabla de todas las densidades, compactación promedio y criterio Dm − k·s ≥ 0,95·De. Toque una fila para ir a esa densidad.
6. **Observaciones y firmas**: Observaciones, Elaboró, Aprobó.
7. **PDF**: icono PDF arriba a la derecha; se abre o se comparte.

Todo se guarda solo. En la lista, el menú ⋮ de cada registro permite crear un registro nuevo con el mismo encabezado o eliminarlo.
**Respaldo**: menú ⋮ de la lista → *Guardar respaldo* (archivo .zip, no cifrado). *Restaurar respaldo* reemplaza los registros actuales (antes guarda una copia automática).

## Intercambio con Excel y entre teléfonos

- **Importar**: menú ⋮ de la lista → *Importar Excel (.xlsx)*, o abra/comparta el archivo .xlsx hacia GeoDensidad. Cada hoja con el formato 'Cono de arena' se vuelve un registro. Sirve con la plantilla original (toma los valores que el Excel tiene guardados, incluso los del encabezado) y con archivos exportados por otra instalación.
- **Exportar una jornada**: escriba en el buscador la fecha o el lote (p. ej. `2026-11-25`) y use menú ⋮ → *Exportar los N de la búsqueda a Excel*. Sin búsqueda se exportan todos. Cada registro queda en una hoja con las mismas celdas y fórmulas de la plantilla.
- **Un solo registro**: dentro del registro, icono de tabla (arriba) → compartir por WhatsApp, correo, Drive, etc.
- Si se importa un archivo exportado por GeoDensidad, los registros que ya existan se **actualizan** (no se duplican). No borre la fila 56 del Excel: lleva ese identificador.
