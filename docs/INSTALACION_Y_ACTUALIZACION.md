# Instalación y actualizaciones sin desinstalar

Android instala una versión **encima** de otra (conservando los registros) solo si:
1. el APK nuevo está firmado con **la misma clave**, y
2. tiene un **número de versión mayor**.

El número de versión ya sube solo: GitHub usa el número de ejecución (`versionCode = 100 + ejecución`, `versionName = 1.2.<ejecución>`).
La clave fija se configura **una sola vez** en los Secrets del repositorio:

## 1. Crear la clave (una vez, en su computador)

Necesita `keytool` (viene con Android Studio o cualquier JDK). Elija una contraseña y guárdela.

```bash
keytool -genkeypair -v -keystore geodensidad.jks -storetype PKCS12 -alias geodensidad -keyalg RSA -keysize 4096 -validity 10000
```

Convertirla a texto base64 — Windows (PowerShell):

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("geodensidad.jks")) | Set-Clipboard
```

Linux/Mac:

```bash
base64 -w0 geodensidad.jks
```

**Guarde `geodensidad.jks` y la contraseña en un lugar seguro.** Si se pierden, las versiones nuevas no podrán instalarse encima.

## 2. Cargarla en GitHub (una vez)

Repositorio → *Settings* → *Secrets and variables* → *Actions* → *New repository secret*. Cree cuatro:

| Nombre | Valor |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | el texto base64 del paso anterior |
| `SIGNING_STORE_PASSWORD` | la contraseña |
| `SIGNING_KEY_ALIAS` | `geodensidad` |
| `SIGNING_KEY_PASSWORD` | la contraseña |

## 3. Instalar

- Abra la última ejecución en *Actions*, descargue el artefacto **GeoDensidad-apk-N** e instale `debug/app-debug.apk`.
- La **primera vez** con la clave fija hay que desinstalar la versión anterior (fue firmada con otra clave). Desde ahí, cada APK nuevo se instala encima y conserva los registros.
- Si la ejecución muestra el aviso *"Sin secret SIGNING_KEYSTORE_BASE64"*, los Secrets no están configurados.

Compilación local: `./gradlew :app:assembleDebug` (sin las variables `SIGNING_*` se usa la firma de depuración de esa máquina).
