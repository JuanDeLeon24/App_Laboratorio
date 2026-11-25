# Instalación, firma y actualización

## Instalación de prueba (debug)

1. Compilar `./gradlew :app:assembleDebug` (o descargar el artefacto de la integración continua).
2. Copiar `app-debug.apk` al teléfono (Android 8.0 o superior) y abrirlo; permitir "instalar apps desconocidas" para el gestor de archivos.
3. El paquete debug (`com.geodensidad.app.debug`) convive con el de producción y **no comparte datos** con él.

> Los APK debug se firman con la clave de depuración de cada máquina: un APK debug compilado en otra máquina no podrá instalarse como actualización encima. Use el canal de producción para equipos de campo.

## Producción (release)

1. Crear la clave **una sola vez** y custodiarla (gestor de secretos y copia fuera de línea; perderla impide publicar actualizaciones):
   ```bash
   keytool -genkeypair -v -keystore geodensidad-release.jks -alias geodensidad -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Crear `keystore.properties` en la raíz (está en `.gitignore`, **nunca** se versiona):
   ```
   storeFile=../ruta/geodensidad-release.jks
   storePassword=...
   keyAlias=geodensidad
   keyPassword=...
   ```
3. `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`. Sin `keystore.properties` se genera `app-release-unsigned.apk`, que no es instalable.

Canal inicial de distribución propuesto: distribución interna del APK firmado (MDM o enlace controlado). No hay autoactualización.

## Actualizar sin perder datos

- Mismo `applicationId` (`com.geodensidad.app`), misma clave de firma y `versionCode` mayor.
- Instalar el nuevo APK **encima** (sin desinstalar). - Recomendado: crear un respaldo completo antes de actualizar.
- **Pendiente de verificar en dispositivo**: la actualización sobre una instalación existente no se ha probado físicamente (ver LIMITACIONES).

Desinstalar o "borrar datos" elimina toda la información local; solo se recupera con un respaldo.
