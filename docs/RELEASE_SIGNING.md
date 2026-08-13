# Firma release de Taki

Procedimiento y convenciones para compilar un APK release firmado. **Este documento no
contiene, y nunca debe contener, valores reales de contraseñas, alias ni rutas absolutas de
otros equipos.** Solo nombres de variables y pasos.

## Identidad de firma

Taki usa un keystore propio, generado específicamente para este proyecto (P0.1 de
`TAKI_BETA_COMPLETION_PLAN.md`). El archivo heredado `ultrasonic-keystore.enc` en la raíz del
repo **no es y no debe convertirse en** la identidad de firma de Taki — es un artefacto del
fork original de Ultrasonic.

- Algoritmo: RSA 4096, formato PKCS12.
- Validez: 10.000 días desde la generación.
- El `.jks` vive **fuera del repositorio**, en una carpeta local del equipo que compila el
  release (p. ej. `%USERPROFILE%\TakiRelease\`). Nunca debe copiarse dentro del working tree
  del repo, ni siquiera temporalmente.

## Variables (`keystore.properties`)

`ultrasonic/build.gradle` busca un archivo `keystore.properties` en la **raíz del repositorio**
(ya cubierto por `.gitignore`, líneas `*.jks`, `*.keystore`, `keystore.properties` — verificar
con `git check-ignore -v keystore.properties` si hay dudas). Si no existe, `assembleRelease`
igual compila pero produce un APK **sin firmar** (hay un `logger.warn` en la configuración de
Gradle que lo recuerda).

Claves esperadas en el archivo (formato Java `Properties`; usar `/` en las rutas de Windows
para evitar problemas de escape):

```properties
storeFile=<ruta absoluta al .jks, con / en vez de \>
storePassword=<contraseña del store>
keyAlias=<alias de la clave>
keyPassword=<contraseña de la clave>
```

Nota PKCS12: a diferencia del formato JKS clásico, PKCS12 **no admite contraseñas distintas**
para el store y la key — `keytool` ignora `-keypass` si difiere de `-storepass`. `storePassword`
y `keyPassword` deben tener el mismo valor.

## Regenerar el keystore (solo si se pierde o se compromete)

Regenerar el keystore crea una **identidad de firma nueva**: cualquier instalación existente de
un release firmado con la clave anterior **no podrá actualizarse** sobre esa clave nueva (Android
exige la misma firma para actualizar in situ). Antes de regenerar, agotar toda posibilidad de
recuperar el archivo original desde las copias de seguridad.

```powershell
$keytool = "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
& $keytool -genkeypair -v `
    -keystore <ruta al nuevo .jks, fuera del repo> `
    -alias taki-release `
    -keyalg RSA -keysize 4096 -validity 10000 `
    -storetype PKCS12 `
    -dname "CN=Taki Release, OU=Taki Android, O=Taki, C=US"
```

Luego escribir `keystore.properties` con las cuatro claves de arriba.

## Copias de seguridad (responsabilidad del propietario, no automatizado)

El plan exige al menos dos copias seguras del keystore fuera de esta máquina. No lo automaticé
porque implicaría subir un secreto a un servicio de terceros en tu nombre. Sugerido:

1. Adjuntar el `.jks` a una entrada de tu gestor de contraseñas (1Password, Bitwarden, etc.),
   junto con las cuatro variables de `keystore.properties`.
2. Una segunda copia offline (USB cifrado, disco externo) que no dependa de la misma cuenta
   que la copia 1.

## Compilar y verificar

```bash
./gradlew :ultrasonic:assembleRelease
```

El APK queda en `ultrasonic/build/outputs/apk/release/`. Verificar la firma:

```bash
apksigner verify --verbose --print-certs ultrasonic/build/outputs/apk/release/*.apk
```

Debe imprimir `Verifies` y un `V2 Signer: certificate DN: CN=Taki Release, ...`. Calcular el
checksum de distribución:

```bash
sha256sum ultrasonic/build/outputs/apk/release/*.apk
```

## Qué NO hacer

- No commitear `keystore.properties` ni ningún `.jks`/`.keystore`.
- No pegar contraseñas del keystore en commits, issues, PRs ni en este documento.
- No usar `ultrasonic-keystore.enc` como fallback "por ahora" — rompe la garantía de identidad
  propia que pide P0.1.
