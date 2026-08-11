# Posibles errores y verificación de estabilidad

Este documento reúne regresiones de alto impacto que deben revisarse antes de compartir una APK o crear un release de Taki. Una compilación exitosa no demuestra que la reproducción, la restauración de sesión ni la navegación funcionen correctamente.

## 1. ANR al restaurar una cola de reproducción grande

### Síntomas

- Aparece el diálogo **“Taki isn't responding”**.
- La aplicación tarda varios segundos en abrir o deja de responder al tocar Play.
- La música no comienza aunque la interfaz parezca cargada.
- Logcat muestra `Input dispatching timed out` y muchos `Skipped ... frames`.

### Causa encontrada

La sesión persistida podía contener miles de canciones. En el caso que reveló el problema se intentaban restaurar **5.517 pistas** mediante Media3 en el hilo principal. Crear todos los `MediaItem` y actualizar la cola completa bloqueaba la interfaz durante más de los cinco segundos que Android permite antes de declarar un ANR.

### Protección actual

- La sesión persistida/restaurada conserva una ventana máxima de 100 pistas alrededor de la canción activa.
- Se conserva la canción actual, su posición, shuffle y repeat.
- El checkpoint periódico reutiliza la última instantánea de la cola y solo actualiza el estado/posición; no convierte nuevamente toda la cola cada cinco segundos.

### Qué no se debe hacer

- No restaurar miles de elementos con `addMediaItems()` o `setMediaItems()` en una sola operación del hilo principal.
- No recorrer `playlist` ni ejecutar `toTrack()` para toda la cola en un temporizador frecuente.
- No asumir que una prueba con un álbum pequeño representa una cola creada desde “Todas las canciones”.

Si en el futuro se necesita conservar una cola completa de miles de pistas, debe implementarse restauración paginada/incremental y medirse en un dispositivo real antes de retirar el límite.

## 2. Acceso a Media3 desde el hilo incorrecto

Los métodos de `MediaController` deben ejecutarse en su application thread, actualmente el hilo principal. Mover directamente lecturas como `duration`, `currentPosition` o `isPlaying` a `Dispatchers.IO`/`Default` provoca:

```text
IllegalStateException: MediaController method is called from a wrong thread
```

Para progreso visual continuo se debe leer el estado en el hilo permitido y animar la vista (`ObjectAnimator`), o construir una abstracción que publique snapshots thread-safe. No se debe consultar el controlador cada segundo desde otro dispatcher.

## 3. Trabajo periódico en el hilo principal

Todo `Handler.postDelayed`, observable periódico o callback frecuente debe revisarse buscando:

- recorridos completos de la biblioteca o cola;
- conversión masiva de modelos;
- serialización o acceso a disco;
- carga/sincronización de red;
- actualizaciones completas de RecyclerView o MediaSession.

La escritura a disco debe ocurrir en IO. El trabajo ejecutado en main debe ser constante y pequeño.

## 4. Checklist obligatorio antes de compartir una APK

### Compilación

- Usar el JBR de Android Studio para evitar incompatibilidad JVM 21/23:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :ultrasonic:testDebugUnitTest :ultrasonic:assembleDebug
```

- Confirmar `BUILD SUCCESSFUL`.
- No confundir warnings históricos de lint con errores introducidos por el cambio.

### Reproducción y restauración

1. Reproducir una canción desde un álbum y confirmar respuesta inmediata.
2. Pausar, reanudar, avanzar y retroceder.
3. Abrir y manipular la cola.
4. Enviar Taki a segundo plano y volver.
5. Forzar el cierre del proceso, abrir nuevamente y comprobar canción/posición.
6. Repetir con una cola grande creada desde Songs/Play All, no solo con un álbum.
7. Mantener reproducción al menos 30 segundos y comprobar que no haya congelamientos periódicos.
8. Probar una canción descargada sin conexión.

### Interfaz y navegación

- Home, Library, Search y Downloads no deben superponerse con la barra de estado.
- El botón atrás debe funcionar en álbum, artista, playlists, Liked Songs, servidores y Settings.
- La navegación inferior debe regresar a Home/Library incluso después de abrir una subpantalla.
- Now Playing, Lyrics y Queue deben abrir, cerrar y sobrevivir a una rotación sin crash.
- Los `MenuItem` y `findViewById` opcionales deben tratarse como nullable cuando una pantalla ya no incluye el toolbar/layout heredado.

### Diagnóstico en dispositivo

Después de limpiar logcat, buscar al menos:

```text
FATAL EXCEPTION
ANR in org.moire.ultrasonic
Input dispatching timed out
MediaController method is called from a wrong thread
Skipped ... frames
```

Uno o dos saltos pequeños durante el arranque pueden requerir observación, pero saltos repetidos, pausas perceptibles o cualquier ANR bloquean la entrega. No se debe clasificar un ANR como ruido de automatización sin revisar la traza, el PID y la línea temporal de logcat.

## 5. Regla para cambios de reproducción

Todo cambio en `MediaPlayerManager`, `PlaybackStateSerializer`, `MediaPlayerLifecycleSupport`, `PlaybackService`, `PlayerFragment` o `NowPlayingFragment` requiere prueba en dispositivo. Como mínimo: inicio, play/pause, segundo plano, restauración del proceso, cola grande y revisión de logcat.

La APK solo debe considerarse lista cuando compila, pasa pruebas y supera esta verificación conductual sin ANR ni excepciones.
