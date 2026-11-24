# Limitaciones concretas de la versión 1.0.0

1. **No probada en un dispositivo físico ni en un emulador.** Cámara real, selector de fotos, permisos, `PdfDocument`, vista previa (`PdfRenderer`), compartir y reinicio tras restaurar están implementados pero no se verificaron en un teléfono.
2. **Validez técnica y normativa pendiente**: fórmulas heredadas del Excel, wc = 0,02, interpretación de I y criterio `Dm − k·s ≥ 0,95·De` con su tabla k: todo rotulado como no validado.
3. Código, versión y fecha documentales de la plantilla: el Excel no los traía; quedan "(por definir)" y se configuran en la app.
4. Imagen `Picture 1` del Excel (EMF): se convirtió a PNG y se usa como imagen de la fórmula de s en el informe global. Su posición original (filas 40–42) no se replica en el formato por muestra.
5. Campos personalizados: solo de entidad **Muestra** en esta versión.
6. Fecha superpuesta sobre la imagen: no implementada (opcional en los requisitos).
7. La hora de captura de cámara es la del dispositivo al volver de la cámara; no está verificada externamente.
8. El respaldo **no está cifrado**. La restauración reemplaza todo; no fusiona.
9. Atribución por nombre local, sin autenticación; el historial local no es inalterable frente a acceso privilegiado.
10. Sin sincronización ni trabajo multiusuario.
11. Rendimiento con 10.000 muestras: listas e índices diseñados para ello, pero sin medición. El panel recalcula estadísticas en memoria.
12. APK sin minificar (~13 MB release). La firma de producción requiere crear y custodiar la clave (no incluida).
13. Textos de la interfaz en el código (no en `strings.xml`): solo español.
14. Edición de muestras: un texto numérico inválido no se guarda (el valor queda faltante) y se pierde al reabrir.
