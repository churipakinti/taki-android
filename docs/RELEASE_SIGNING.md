# Firma release de Taki

Procedimiento y convenciones para compilar un APK release firmado. **Este documento no contiene, y nunca debe contener, valores reales de contraseñas, alias ni rutas absolutas de otros equipos.** Solo nombres de variables y pasos.

## Identidad de firma

Taki usa un keystore propio, generado específicamente para este proyecto. El archivo heredado `ultrasonic-keystore.enc`, retirado del árbol actual durante la preparación del release pero conservado en el historial de Ultrasonic, **no es y no debe convertirse en** la identidad de firma de Taki.

- Algoritmo: RSA 4096, formato PKCS12.
- Validez: 10.000 días desde la generación.
- El `.jks` vive **fuera del repositorio**, en una carpeta local del equipo que compila el release (p. ej. `%USERPROFILE%\TakiRelease\`). Nunca debe copiarse dentro del working tree del repo, ni siquiera temporalmente.

## Variables (`keystore.properties`)

`ultrasonic/build.gradle` busca un archivo `keystore.properties` en la **raíz del repositorio**. El archivo y los formatos de keystore están cubiertos por `.gitignore` (`*.jks`, `*.keystore`, `keystore.properties`). Verificar con `git check-ignore -v keystore.properties` si hay dudas.

Si `keystore.properties` no existe, `assembleRelease` puede compilar un APK sin firmar. Ese artefacto no debe publicarse como release.

Claves esperadas:

```properties
storeFile=<ruta absoluta al .jks, con / en vez de \>
storePassword=<contraseña del store>
keyAlias=<alias de la clave>
keyPassword=<contraseña de la clave>
```

Nota PKCS12: `storePassword` y `keyPassword` deben usar el mismo valor.

## Regenerar el keystore

Regenerar el keystore crea una **identidad de firma nueva**. Las instalaciones firmadas con la clave anterior no podrán actualizarse normalmente con una clave distinta. Solo debe considerarse después de agotar la recuperación de las copias de seguridad o si la clave se ha comprometido.

```powershell
$keytool = "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
& $keytool -genkeypair -v `
    -keystore <ruta al nuevo .jks, fuera del repo> `
    -alias taki-release `
    -keyalg RSA -keysize 4096 -validity 10000 `
    -storetype PKCS12 `
    -dname "CN=Taki Release, OU=Taki Android, O=Taki"
```

Luego escribir `keystore.properties` con las cuatro claves de arriba.

## Copias de seguridad

Mantener al menos dos copias seguras del keystore fuera de la máquina de build. La copia del secreto no se automatiza desde el repositorio.

Opciones habituales:

1. Guardar el `.jks` como adjunto protegido en un gestor de contraseñas junto con los valores necesarios de `keystore.properties`.
2. Mantener una segunda copia offline cifrada que no dependa de la misma cuenta que la primera.

## Compilar y verificar

```bash
./gradlew :ultrasonic:assembleRelease
```

El APK queda en `ultrasonic/build/outputs/apk/release/`.

Verificar la firma:

```bash
apksigner verify --verbose --print-certs ultrasonic/build/outputs/apk/release/*.apk
```

Registrar el fingerprint SHA-256 del certificado y calcular el checksum del APK:

```bash
sha256sum ultrasonic/build/outputs/apk/release/*.apk
```

El APK que se smoke-testee en el dispositivo debe ser exactamente el mismo archivo que se publique.

## Qué no hacer

- No commitear `keystore.properties` ni ningún `.jks`/`.keystore`.
- No pegar contraseñas del keystore en commits, issues, PRs ni documentación.
- No usar `ultrasonic-keystore.enc` como fallback.
- No publicar un APK release sin verificar su firma.
