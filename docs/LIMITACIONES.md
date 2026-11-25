# Limitaciones (1.1.0)

1. No probada todavía en un teléfono físico.
2. Fórmulas, wc = 0,02, interpretación de I y criterio Dm − k·s ≥ 0,95·De (tabla k del Excel) reproducen el formato; **no están verificados contra la norma**.
3. El resumen evalúa todas las densidades del registro con resultado; la tabla k solo existe para n de 5 a 10 (fuera de ese rango: "No evaluable").
4. Código, versión y fecha documental del encabezado del PDF quedan en blanco, como en el Excel.
5. N° irregularidades >15 mm no se incluye: en el Excel venía por fórmula de una base de datos externa.
6. El respaldo no está cifrado; restaurar reemplaza todos los registros.
7. Los APK de prueba compilados en GitHub cambian de firma en cada compilación: para instalar uno nuevo hay que desinstalar el anterior (se pierden los datos si no hay respaldo).
