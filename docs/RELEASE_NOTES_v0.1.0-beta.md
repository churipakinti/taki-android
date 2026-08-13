# Taki v0.1.0-beta

Primera beta pública de Taki, un cliente Android para Navidrome y servidores Subsonic/OpenSubsonic
compatibles, enfocado en abrir tu colección personal, encontrar música y escucharla con poca
fricción.

## Identificación de este build

- **Versión:** 0.1.0-beta (`versionCode` 131)
- **Commit:** `7e4255a1a8669207cae8b8052cbb0985b1fa2e91`
- **Firma:** `CN=Taki Release, OU=Taki Android, O=Taki` (clave propia de Taki, no la heredada del
  fork de Ultrasonic)
- **SHA-256 del APK:** `099f836a4ef13fd3e4fab75997d0b6a2a9e23621ce84b3836d87fa597ca546c4`

Antes de instalar, confirmá que el SHA-256 de tu descarga coincide con el de arriba.

## Qué incluye esta beta

- Conectar una colección Navidrome/Subsonic/OpenSubsonic propia y navegarla (Home, Library,
  Search, Downloads).
- Reproducción online y offline (modo de música descargada), con sesión, canción, posición y cola
  restauradas al reabrir la app.
- Descargas: completas, canceladas, interrumpidas y reintentadas.
- Radio de canción, Radio de artista y Mix diario.
- Playlists: listar, abrir, crear, editar, reproducir, descargar y eliminar.
- Android Auto: reproducción, navegación básica y reanudación.
- Firma propia y credenciales excluidas de Android Backup / device transfer.

## Limitaciones conocidas

- **Colas muy grandes (miles de canciones) se limitan a 100 pistas por operación.** Reproducir un
  álbum o playlist enorme de una sola vez con "Play All" ya no carga todo junto — se recorta a una
  ventana de 100 canciones para evitar que Android mate la app al sincronizar la cola con Android
  Auto/Bluetooth. Es una limitación deliberada, no un bug.
- En **Search offline**, algunas canciones descargadas pueden mostrar el nombre de archivo en vez
  del título limpio (por ejemplo, con el número de pista incluido). Cosmético, no afecta la
  reproducción.
- El árbol de navegación de **Android Auto** es más simple (basado en texto, sin carátulas ni
  pantalla de inicio) que la app en el teléfono.
- Sin sleep timer todavía.
- Chat, podcasts, shares y bookmarks del fork original están ocultos en esta beta (no forman parte
  de la propuesta actual de Taki).

## Cómo reportar un problema

Usá [GitHub Issues](https://github.com/churipakinti/taki-android/issues) del repositorio. Incluí:

1. Qué esperabas que pasara y qué pasó.
2. Pasos para reproducirlo.
3. Modelo de teléfono y versión de Android.
4. Si podés, un logcat del momento del problema.

## Licencia

Taki es un fork de [Ultrasonic](https://gitlab.com/ultrasonic/ultrasonic), distribuido bajo
GPLv3. El código fuente correspondiente a esta versión está disponible en el commit indicado
arriba.
