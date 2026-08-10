# Auditoría de funcionamiento interno (concurrencia, hilos, recursos)

## Propósito

La parte visual y de experiencia de usuario de Taki se considera terminada: es
moderna y funcional (ver `HANDOFF.md`). Lo que falta ahora es una revisión **en
profundidad de los procesos internos** — no cómo se ve la app, sino cómo
*funciona por dentro* — con un único objetivo: que el usuario final nunca vea
un freeze, un ANR, un crash, un dato viejo pisando uno nuevo, o una acción que
"no responde" sin motivo aparente. Todo debe fluir de forma lineal, rápida y
predecible.

Este documento existe porque ese tipo de bug **ya pasó varias veces en este
proyecto** (ver la lista en la sección siguiente) y siempre tuvo la misma
forma: código que compila, pasa los tests, y se ve perfecto en una prueba
rápida, pero rompe bajo un patrón de uso específico (colas grandes, navegación
rápida de ida y vuelta, cierre abrupto del proceso, scroll rápido). Por eso la
auditoría no puede ser "abrir la app y tocar todo" — tiene que ser dirigida a
los patrones concretos que ya demostraron ser peligrosos acá.

**No es un rediseño ni una limpieza de estilo.** Si durante la auditoría algo
visual llama la atención, anotarlo aparte y no arreglarlo en el mismo pase —
eso ya se dio por cerrado.

## Historial: bugs reales ya encontrados en este código (léelo antes de auditar)

Cada uno de estos define un patrón a buscar en el resto del código, porque si
pasó una vez en un archivo, es razonable sospechar que pasa en otro:

1. **`CoroutineScope by CoroutineScope(Dispatchers.Main)` como delegate de
   instancia fija en un Fragment cuya vista se destruye/recrea.**
   `PlayerFragment` lo tenía así; al cancelar el scope en `onDestroyView()`
   sin nunca volver a crearlo, **todo `launch {}` del archivo se volvía un
   no-op permanente y silencioso** la primera vez que el usuario volvía a esa
   pantalla (Player → Lyrics → Player). Se arregló con una `var mainScope`
   reasignada en cada `onCreateView()`. Ver `HANDOFF.md`, punto sobre
   `PlayerFragment`.
2. **ANR al restaurar una cola de reproducción grande** — 5.517 pistas
   convertidas a `MediaItem` en el hilo principal en una sola operación.
   Arreglado con ventana máxima de 100 pistas + checkpoint incremental (ver
   `docs/POSIBLES_ERRORES_Y_VERIFICACION.md`, ya verificado en dispositivo).
3. **`LiveData.postValue()` seguido de lectura síncrona inmediata** — condición
   de carrera que mostraba "No media found" con datos reales cargados
   (`HomeViewModel.kt`).
4. **Cambios de filtro/orden rápidos sin cancelar la carga anterior** — una
   respuesta vieja podía pisar a una más nueva en Library, Crear Playlist y
   paginación de canciones por género. Se arregló guardando el `Job` y
   cancelándolo antes de lanzar el siguiente (`TrackCollectionFragment.kt`,
   `CreatePlaylistFragment.kt`, `ArtistListModel.kt`).
5. **Paginación sin guarda de solapamiento** — `getSongsForGenre()` avanzaba el
   offset por tamaño de página solicitado en vez de por cantidad real recibida,
   pudiendo saltar o duplicar canciones en scroll rápido.
6. **Un `Fragment.load()` usando el scope del ViewModel en vez de
   `viewLifecycleOwner.lifecycleScope`** — seguía trabajando después de que la
   vista ya no existía, generando un toast de lifecycle fantasma
   (`HomeFragment.kt`).
7. **Cursor de `ContentResolver` sin cerrar** — encontrado por StrictMode el
   09/08/2026 al reabrir la app tras un `force-stop` (`CloseGuardException:
   AbstractCursor.close`/`CursorWrapperInner.close` not called). No se llegó a
   identificar el origen exacto — primer ítem a investigar en esta auditoría.

**Todos estos bugs comparten una causa raíz: alguna forma de estado o scope
que vive más o menos tiempo del que su código asume.** Esa es la lente con la
que hay que leer cada archivo.

## Sospechosos concretos ya detectados (punto de partida, no investigados a fondo)

Durante la preparación de este documento aparecieron dos patrones que huelen
exactamente igual a los bugs #1 y #7 de arriba. **Empezar por acá**, confirmar
si son reales, y si lo son, corregirlos y documentarlos en `CHANGES.md` con el
mismo detalle que los bugs anteriores.

### A. `PlayerFragment.ioScope` nunca se cancela ni se reasigna

`ultrasonic/src/main/kotlin/org/moire/ultrasonic/fragment/PlayerFragment.kt`:

```kotlin
private var ioScope = CoroutineScope(Dispatchers.IO)   // línea ~144, se crea UNA vez
...
override fun onDestroyView() {
    rxBusSubscription.dispose()
    cancel("CoroutineScope cancelled because the view was destroyed")  // cancela `mainScope`, NO `ioScope`
    cancellationToken.cancel()
    _binding = null
    super.onDestroyView()
}
```

`ioScope` es independiente de `mainScope` (el que sí se reasigna en
`onCreateView()`, ver `HANDOFF.md`). Como nunca se cancela, no cae en el
mismo "no-op permanente" que tuvo `mainScope` — pero por eso mismo puede
seguir corriendo trabajo en segundo plano **después de que la vista fue
destruida**, con la vista ya en `_binding = null`.

Caso concreto a verificar — `savePlaylistInBackground()` (línea ~790):

```kotlin
ioScope.launch {
    val musicService = getMusicService()
    musicService.createPlaylist(null, playlistName, entries)
}.invokeOnCompletion {
    ...
    toast(msg)   // o toast(R.string.download_playlist_done)
}
```

Si el usuario guarda una playlist y navega fuera del Player antes de que la
llamada de red a `createPlaylist` termine, este callback corre igual y llama
`toast(...)` sobre un Fragment potencialmente ya no adjunto (`context`
nulo) → riesgo real de `IllegalStateException: Fragment PlayerFragment not
attached to a context` o similar.

Dato relevante: el proyecto **ya tiene el patrón correcto para esto**,
`Fragment.launchWithToast()` en
`ultrasonic/src/main/kotlin/org/moire/ultrasonic/util/CoroutinePatterns.kt`,
que usa `activity?.lifecycleScope ?: lifecycleScope` (atado al ciclo de vida
del *Fragment*, no de la *vista*, que es justamente lo que evita este
problema) y ya maneja el toast de forma segura. `savePlaylistInBackground()`
no lo usa. Confirmar si aplica y, si aplica, migrar esa función a
`launchWithToast()` en vez de inventar un fix nuevo — coincide con la regla
del proyecto de "reusar patrones existentes agresivamente" (`HANDOFF.md`).

Revisar también el otro uso de `ioScope` (línea ~448, consulta de Jukebox) —
ese en particular sólo escribe un campo (`jukeboxAvailable`), menor riesgo,
pero confirmar igual.

### B. `TrackViewHolder` como `CoroutineScope by CoroutineScope(Dispatchers.IO)` de instancia fija

`ultrasonic/src/main/kotlin/org/moire/ultrasonic/adapters/TrackViewHolder.kt:54`

Un `RecyclerView.ViewHolder` se recicla y se reutiliza para pintar filas
distintas a medida que el usuario hace scroll. Si este scope nunca se cancela
al reciclar la fila (verificar si existe algún `onViewRecycled`/equivalente
que lo haga), una corrutina lanzada para la canción A puede terminar y
actualizar la UI de la fila **después** de que esa misma vista ya se reusó
para mostrar la canción B — el síntoma sería portadas o estados
("reproduciendo ahora") que aparecen en la fila equivocada durante scroll
rápido en listas largas (Songs, Album Detail con muchas pistas, playlists
grandes). Comparar contra el guard que ya existe en `TrackViewHolder.kt` línea
~124 (`if (it.id != song.id) return@launch`) — confirmar si ese guard es
suficiente o si hace falta cancelar el scope directamente al reciclar.

## Metodología: dos capas, en este orden

### Capa 1 — Barrido estático (rápido, sin tocar el dispositivo)

Buscar en todo `ultrasonic/src/main/kotlin` y `core/*/src` los mismos
patrones que ya causaron los bugs del historial. Comandos de partida (ajustar
según lo que vaya apareciendo):

```bash
# Scopes de instancia fija — el sospechoso #1 histórico
grep -rn "CoroutineScope by CoroutineScope(" --include=*.kt ultrasonic/src core

# Para cada uno, verificar a mano: ¿se cancela en algún lifecycle callback?
# ¿y si se cancela, se vuelve a crear después, o queda muerto para siempre?
grep -rn "\.cancel(" --include=*.kt ultrasonic/src/main/kotlin/org/moire/ultrasonic/fragment

# launch()/async() en Fragments que NO usan viewLifecycleOwner.lifecycleScope
# (candidatos a trabajo que sobrevive a la vista destruida)
grep -rn "\.launch(\|\.async(" --include=*.kt ultrasonic/src/main/kotlin/org/moire/ultrasonic/fragment \
  | grep -v "viewLifecycleOwner"

# Callbacks que tocan la UI después de que el trabajo termina —
# cada invokeOnCompletion es candidato a "¿sigue viva la vista acá?"
grep -rn "invokeOnCompletion" --include=*.kt ultrasonic/src

# Paginación: todo loadMore/offset debe tener guard de Job cancelable
# (el bug #4 y #5 del historial). Confirmar que TODOS los flujos paginados
# siguen el mismo patrón que ArtistListModel.setSortOrder(), no sólo los que
# ya se corrigieron.
grep -rln "loadJob\|canLoadMore\|Offset" --include=*.kt ultrasonic/src/main/kotlin/org/moire/ultrasonic

# Acceso a Media3 (MediaController) fuera del hilo de aplicación —
# el bug histórico documentado en docs/POSIBLES_ERRORES_Y_VERIFICACION.md
grep -rn "mediaController\.\|mediaPlayerManager\.\(duration\|currentPosition\|isPlaying\)" \
  --include=*.kt ultrasonic/src | grep -v "Dispatchers.Main"

# Cursor/Cierre de recursos (el hallazgo StrictMode sin resolver, bug #7)
grep -rln "ContentResolver\|\.query(\|Cursor" --include=*.kt ultrasonic/src
```

Para cada resultado: leer el archivo completo (no sólo la línea), entender el
ciclo de vida real del objeto contenedor (¿Fragment? ¿ViewHolder? ¿singleton
inyectado por Koin?), y decidir si el patrón es seguro ahí o repite un bug
conocido.

### Capa 2 — Verificación dinámica en el Pixel 7

StrictMode **ya está activo en debug** con `detectAll()` +
`penaltyLog()` para thread policy y VM policy
(`ultrasonic/src/main/kotlin/org/moire/ultrasonic/app/UApp.kt`, líneas 54-55).
No hace falta configurarlo — hace falta **generar uso real y prolongado de la
app y leer el logcat completo**, porque StrictMode sólo reporta lo que
efectivamente se ejecuta:

1. Instalar el debug APK, limpiar logcat (`adb logcat -c`).
2. Recorrer sesión larga y realista: abrir la app, reproducir, navegar entre
   las 4 pestañas repetidamente y rápido, entrar y salir de Player/Lyrics/Queue
   varias veces seguidas (el patrón exacto que causó el bug #1), hacer scroll
   rápido en una lista larga (Songs, un álbum grande), cambiar de filtro/orden
   rápidamente varias veces seguidas en Library, guardar una playlist y salir
   de la pantalla inmediatamente después de tocar Guardar (para probar el
   sospechoso A), forzar `am force-stop` y reabrir, rotar pantalla varias
   veces.
3. Volcar logcat completo y filtrar:
   ```bash
   adb logcat -d | grep -E "StrictMode|FATAL EXCEPTION|ANR in org.moire|Input dispatching timed out|wrong thread|Skipped .* frames|CloseGuardException"
   ```
4. Cualquier `StrictMode` con stack trace dentro de un paquete
   `org.moire.ultrasonic.*` (no de una librería externa) es un hallazgo real,
   no ruido — este proyecto no tiene falsos positivos de StrictMode
   conocidos, no descartar sin verificar.
5. Complementar con el profiler de Android Studio (CPU + Memory) durante el
   scroll rápido en listas largas, para detectar jank que no llegue a
   "Skipped frames" en logcat pero sí se sienta al usar la app.

## Flujos críticos a trazar de punta a punta

No alcanza con mirar archivos sueltos — varios de los bugs del historial sólo
aparecían siguiendo un flujo completo. Trazar cada uno de estos de principio a
fin, anotando cada hop entre hilos/scopes:

- **Reproducción y transporte**: tap en una canción → `MediaPlayerManager` →
  `PlaybackService` → `MediaController`/Media3 → actualización de UI en
  Player/mini player/notificación, en ambas direcciones (UI → servicio y
  servicio → UI).
- **Restauración de sesión**: serialización periódica, deserialización al
  abrir, y la ventana de 100 pistas — confirmar que el límite se aplica
  también cuando la cola crece por encima de 100 *durante* una sesión (no
  sólo al restaurar), si es que eso es posible.
- **Paginación / scroll infinito**: Songs (todas las canciones, aleatorio, por
  artista, por género), Library, Search — cada uno con su propio
  ViewModel/Job. Confirmar que todos cancelan el `Job` anterior antes de
  lanzar el siguiente, no sólo los que ya se documentaron como corregidos.
- **Descargas**: `DownloadService`/`DownloadTask` — qué corre en qué
  dispatcher, qué pasa si el usuario cancela una descarga en curso o cierra la
  app mientras descarga.
- **Cambio de servidor / modo offline**: qué se cancela y qué sigue vivo del
  servidor anterior al cambiar de servidor o entrar/salir de modo Offline.

## Formato de reporte esperado

Para cada hallazgo confirmado, documentar en `CHANGES.md` (español, con la
misma estructura que las entradas existentes) siguiendo el patrón ya usado en
todo el archivo: síntoma observable, causa raíz, archivo(s), fix aplicado, y
cómo se verificó (dispositivo real, no sólo compilación). Si el hallazgo se
descarta por ser seguro, no hace falta documentarlo — pero sí vale la pena
dejar un comentario corto en el código sólo si la razón de por qué es segura
no es obvia (siguiendo el criterio de comentarios de este proyecto: sólo si el
POR QUÉ no es evidente).

## No-objetivos explícitos

- No tocar estilos, layouts, colores ni copy — esa parte del trabajo está
  cerrada.
- No agregar abstracciones nuevas "por si acaso" para prevenir clases enteras
  de bugs futuros (por ejemplo, un wrapper genérico de CoroutineScope) — a
  menos que el mismo patrón bugueado aparezca en 3 o más lugares, en cuyo caso
  preguntar antes de refactorizar.
- No expandir el límite de 100 pistas de la cola persistida ni tocar la
  protección anti-ANR sin medir explícitamente en dispositivo real con una
  cola de miles de pistas primero.
