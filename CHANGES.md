# Changes

Registro de los cambios hechos sobre el Ultrasonic oficial en este fork/copia privada. No commiteado a git todavía (ver conversación) — este archivo es el registro mientras tanto.

## Navegación general (drawer, toolbar)

- **Header del drawer rediseñado** — agregado wordmark (logo + "ULTRASONIC") sobre el selector de servidor, más aire vertical.
  - `ultrasonic/src/main/res/layout/navigation_header.xml`
  - `ultrasonic/src/main/kotlin/org/moire/ultrasonic/activity/NavigationActivity.kt` (conecta el wordmark al color dinámico por servidor)
- **Drawer con esquina redondeada + elevación**, más padding entre ítems del menú.
  - `ultrasonic/src/main/res/drawable/bg_navigation_drawer.xml` (nuevo)
  - `ultrasonic/src/main/res/layout/navigation_activity.xml`
- **Toolbar modernizado** — elevación sutil, colores explícitos `colorSurface`/`colorOnSurface`.
  - `ultrasonic/src/main/res/layout/navigation_activity.xml`
- **Fix de build**: `Build Type debug contains custom resource values, but the feature is disabled` → se habilitó `resValues = true` en `buildFeatures`.
  - `ultrasonic/build.gradle`
- **Nombre del build de debug** cambiado a "ultrasonic-test" (vía `resValue` solo en el buildType `debug`, no afecta release) para distinguirlo de una instalación de producción.
  - `ultrasonic/build.gradle`

## Pantalla "Home" (nueva)

Pantalla de inicio estilo Spotify, ahora la pantalla de arranque de la app (antes era "Media Library"/Browse).

- **Contenido**: saludo dinámico (mañana/tarde/noche), fila de accesos rápidos (Playlists / Albums / Artists / Podcasts), grid 2 columnas "Recently Played", y carruseles horizontales: Favorites (starred), Recently Added, Random, Most Played.
- Toda la data sale de endpoints que la app ya usaba (`getAlbumList2` con tipos `RECENT`, `STARRED`, `NEWEST`, `RANDOM`, `FREQUENT`) — sin motor de recomendaciones, sin backend nuevo.
- Pull-to-refresh, manejo de errores de red (toast), estados vacíos por sección y estado vacío general, reutilizando los patrones ya existentes en la app (`RefreshableFragment`, `toastingExceptionHandler`).

Archivos nuevos:
- `ultrasonic/src/main/kotlin/org/moire/ultrasonic/fragment/HomeFragment.kt`
- `ultrasonic/src/main/kotlin/org/moire/ultrasonic/model/HomeViewModel.kt`
- `ultrasonic/src/main/kotlin/org/moire/ultrasonic/adapters/HomeAlbumDelegate.kt` (tarjeta de carrusel)
- `ultrasonic/src/main/kotlin/org/moire/ultrasonic/adapters/HomeShortcutDelegate.kt` (fila compacta del grid de shortcuts)
- `ultrasonic/src/main/res/layout/home_fragment.xml`
- `ultrasonic/src/main/res/layout/home_carousel_item.xml`
- `ultrasonic/src/main/res/layout/home_shortcut_item.xml`
- `ultrasonic/src/main/res/drawable/ic_menu_home.xml`

Archivos modificados para integrar Home:
- `ultrasonic/src/main/res/navigation/navigation_graph.xml` (nuevo destino `homeFragment`, ahora `startDestination`)
- `ultrasonic/src/main/res/menu/navigation_drawer.xml` (ítem "Home" agregado, primero en la lista)
- `ultrasonic/src/main/kotlin/org/moire/ultrasonic/activity/NavigationActivity.kt` (diálogo de bienvenida ahora navega a Home)
- `ultrasonic/src/main/kotlin/org/moire/ultrasonic/fragment/ServerSelectorFragment.kt` (cambiar de servidor ahora vuelve a Home)
- `ultrasonic/src/main/res/values/strings.xml` (strings de saludo, "Home")

### Bugs encontrados y corregidos en Home

1. **"No media found" aparecía aunque había datos** — condición de carrera entre `LiveData.postValue()` (asíncrono) y la lectura del estado vacío justo después. Corregido usando `.value =` (síncrono, seguro porque siempre se llama desde el hilo principal). → `HomeViewModel.kt`
2. **Solo se veía 1 álbum por carrusel** (incluso con biblioteca real, muchos álbumes) — cada shelf estaba en un `LinearLayout` con `wrap_content` en vez de `match_parent`, colapsando el ancho disponible del `RecyclerView` horizontal de adentro. Corregido en las 5 secciones. → `home_fragment.xml`
3. **Textos cortados en el grid de "Recently Played"** — reusaba el layout de fila de lista completa (pensado para pantalla completa, portada 64dp + estrella), sin espacio para texto en 2 columnas. Se creó un layout compacto dedicado (`home_shortcut_item.xml`, portada 48dp, sin estrella, tamaños de letra más chicos).
4. **Crash al tocar "Media Library" en los accesos rápidos de Home** — `IllegalStateException: Fragment ArtistListFragment ... has null arguments`. Causa: el destino `mediaLibraryFragment` no declara `<argument>` en el nav graph, y `ArtistListFragment` usa `by navArgs()`, que exige un `Bundle` de argumentos no-nulo. Navegar con `navController.navigate(R.id.mediaLibraryFragment)` (por ID crudo) no adjunta ningún Bundle; el drawer, en cambio, siempre navegó ahí con `NavigationGraphDirections.toMediaLibrary()` (una acción, que sí adjunta un Bundle vacío pero no-nulo). Corregido usando la misma acción que el drawer. → `HomeFragment.kt`

## Mini barra de reproducción ("Now Playing")

Ajustada para que combine mejor con la estética nueva de Home (esta barra aparece en toda la app, no solo en Home). Problemas reportados y corregidos:
- Portada sin márgenes, pegada a los bordes → ahora tiene margen de 8dp y esquinas redondeadas (`ShapeableImageView`, mismo tratamiento que otras portadas de la app).
- Texto (título/artista) desproporcionadamente grande comparado con la portada → reducido de `TitleLarge`/20sp a `TitleSmall` (título) y `LabelMedium` (artista).
- Solo había botón de play/pausa, sin adelantar/retroceder (el gesto de swipe izquierda/derecha ya existía pero no era descubrible) → se agregaron botones de anterior/siguiente visibles, reusando `mediaPlayerManager.seekToPrevious()`/`seekToNext()` (la misma lógica que ya usaba el gesto de swipe).

Archivos:
- `ultrasonic/src/main/res/layout/now_playing.xml`
- `ultrasonic/src/main/kotlin/org/moire/ultrasonic/fragment/NowPlayingFragment.kt`

### Segunda pasada de estilo (comparando contra Spotify real)

- **Fondo**: de `colorSecondaryContainer` (tonal, podía verse saturado/de color) a `colorSurfaceContainerHigh` (superficie neutra oscura, más parecida al gris de Spotify).
- **Texto**: de `TitleSmall`/`LabelMedium` (peso medium) a `BodyLarge`/`BodyMedium` (peso regular, menos "bold").
- **Botones**: se agregó `elevation` (2-3dp) a los 3 botones para una sombra sutil.
- **Barra siempre visible**: se quitó el gesto de "deslizar hacia arriba para ocultar" — ahora la barra solo se oculta cuando no hay nada cargado (igual que Spotify), no por gesto del usuario. Nota: esto deja sin uso la suscripción a `dismissNowPlayingCommandObservable` en `NavigationActivity.kt` (nadie la dispara ya) — no la borré todavía, es código muerto inofensivo, no una decisión final.

### Tercera pasada: tarjeta flotante

- Se quitaron las sombras individuales de los 3 botones (se veían mal).
- En su lugar, **toda la barra "flota"**: `layout_margin="8dp"` (se separa de los bordes de la pantalla), fondo redondeado (`bg_now_playing.xml`, nuevo, esquinas de 16dp) en vez de un rectángulo a bordes vivos, y una sola sombra (`elevation="6dp"`) para toda la tarjeta en vez de una por botón.
- Transparencia: `alpha="0.94"` en la barra completa. Nota honesta: esto no es un blur real (no se ve el contenido de atrás desenfocado, técnica compleja con Views clásicas) — es la barra completa un poco más translúcida, texto incluido.
- Portada un poco más grande (56dp → 60dp) para que la barra se sienta más presente/grande, y le quité su sombra propia (ya la tiene la tarjeta completa).

### Cuarta pasada: sin sombra, más transparente

- Se quitó también la sombra de la tarjeta completa (`elevation="6dp"` eliminado).
- Transparencia subida de `alpha="0.94"` a `alpha="0.85"`.

### Quinta pasada: sombra solo en el ícono, no en el botón

El problema original era que `elevation` en Android dibuja la sombra según el **contorno del botón** (circular/redondeado), no según la forma del ícono — por eso se veía como un círculo flotando, ya que el botón no tiene fondo visible. Solución: en vez de `elevation` (nativo de Android), se creó un "drop shadow" manual dibujando cada ícono dos veces dentro de un `layer-list` — una copia oscura y semitransparente, desplazada 1dp, detrás del ícono normal. Así la sombra tiene la forma exacta del ícono (flecha, triángulo, barras), no un círculo.

Archivos nuevos: `media_backward_shadow.xml`, `media_forward_shadow.xml`, `media_pause_shadow.xml`, `media_start_shadow.xml` (uno por ícono; el de play/pausa necesita dos porque el botón alterna de ícono dinámicamente).
Modificados: `now_playing.xml` (usa los `_shadow`), `NowPlayingFragment.kt` (el toggle play/pausa ahora referencia los íconos con sombra).

## Tarjetas del grid "Recently Played"

Las filas del grid de shortcuts no tenían fondo, se sentían "sueltas" comparadas con las tarjetas de los carruseles de abajo (que sí tienen un rectángulo de color detrás del texto). Se les dio el mismo tratamiento: fondo sólido (`?attr/colorSurfaceContainerHigh`) con esquinas redondeadas (12dp) y ripple al tocar, más un pequeño margen entre celdas para que se vean como tarjetas separadas en vez de una grilla continua.

Archivos: `bg_home_shortcut_item.xml` (nuevo), `home_shortcut_item.xml`.

## App enfocada solo en música

Se ocultaron de la interfaz **Podcasts, Video y Chat** — drawer y accesos rápidos de Home. Solo se ocultó de la UI, no se borró código (el soporte de video está compartido con clases de música como `Track`/descargas, borrarlo de raíz sería mucho más riesgoso). Las pantallas siguen existiendo en el nav graph, simplemente no hay forma de llegar a ellas desde la interfaz.

Archivos: `navigation_drawer.xml` (se quitaron los 3 ítems), `home_fragment.xml`/`HomeFragment.kt` (se quitó el botón de acceso rápido a Podcasts).

## Sección "Mix" en Home

Carrusel con ~25 canciones al azar de un artista elegido al azar de toda la biblioteca. La API de Subsonic no tiene un endpoint de "canciones aleatorias de un artista", así que se arma del lado de la app: se elige un artista al azar (`getArtists`/`getIndexes` según el servidor), se piden sus álbumes (`getAlbumsOfArtist`), se toma una muestra de hasta 6 álbumes al azar (para no demorar la carga en artistas con discografías grandes), se traen las canciones de esos álbumes (`getAlbumAsDir` por álbum) y se mezclan tomando 25.

Al tocar una canción del Mix, reemplaza la cola actual y empieza a reproducir desde ahí (mismo patrón que usa `TrackCollectionFragment.playFromHere`) — no navega a un álbum, porque el Mix es para escuchar, no para explorar.

Archivos nuevos:
- `ultrasonic/src/main/kotlin/org/moire/ultrasonic/adapters/HomeTrackDelegate.kt` (tarjeta de carrusel para canciones, reusa el layout de `HomeAlbumDelegate`)

Modificados:
- `HomeViewModel.kt` (`fetchArtistMix()`, se agregó como sexta llamada concurrente en `loadHomeScreen()`)
- `HomeFragment.kt` (carrusel + título dinámico "Mix: <Artista>" + reproducción)
- `home_fragment.xml` (nueva sección, entre Shortcuts y Favoritos)
- `strings.xml` (`home.mix_title`)

## Estilo de Home aplicado a otras pantallas

Empezamos por el **Reproductor completo** (pantalla grande de "Now Playing", `current_playing.xml`), a pedido — es la que más tiempo se mira mientras se escucha.

- El panel de controles (info de la canción + barra de progreso + botones) ahora está dentro de una "bandeja" flotante con esquinas redondeadas arriba (`bg_player_panel.xml`, mismo `colorSurfaceContainerHigh` que el resto de la app), en vez de flotar directo sobre la portada sin separación visual.
- La barra de progreso (`SeekBar`) tenía los colores grises por defecto de Android — se tiñó con `colorPrimary`/`colorSurfaceContainerHighest` para que combine con el tema. (Nota: sigue siendo un `SeekBar` clásico, no el componente `Slider` de Material3 — migrar a ese widget es un cambio más grande que toca el código Kotlin que lo controla, no solo XML.)
- Los botones principales (anterior/play/pausa/siguiente) ahora usan los mismos íconos con sombra (`media_*_shadow.xml`) que ya se hicieron para la mini barra, dándoles la misma sensación de profundidad. Shuffle/repeat quedaron con su ícono normal (son botones secundarios, y repeat cambia de ícono dinámicamente por código, así que decidí no meterle más alcance a esta pasada).

Archivos: `current_playing.xml`, `player_slider.xml`, `media_buttons.xml`, `bg_player_panel.xml` (nuevo).

## Mix: de artista a género

El Mix por artista se sentía repetitivo cuando el artista elegido al azar tenía pocos álbumes (ej. mostraba el mismo álbum una y otra vez). Se cambió a **mix por género**: se elige un género al azar (`getGenres`) y se piden hasta 50 canciones de ese género en **una sola llamada** (`getSongsByGenre`, endpoint que la app ya tenía implementado y no se usaba en ningún lado), de las cuales se mezclan 25. Más simple que el enfoque anterior (que hacía varias llamadas: artista → álbumes → canciones por álbum) y más variado, porque un género normalmente mezcla varios artistas.

`HomeViewModel.kt` (`fetchGenreMix()` reemplaza a `fetchArtistMix()`, LiveData renombradas a `mixGenreName`/`mixTracks`), `HomeFragment.kt` (referencias actualizadas). No hizo falta tocar `MusicService`/`RESTMusicService` — el endpoint de género ya estaba disponible.

## Esquinas de tarjetas menos redondeadas

Las tarjetas del carrusel (`home_carousel_item.xml`) y del grid de shortcuts (`bg_home_shortcut_item.xml`) pasaron de 12-16dp de radio a **4dp** (casi cuadradas, apenas suavizado el borde), a pedido. No se tocó la mini barra de reproducción, el reproductor completo ni el drawer — esas son superficies distintas (barra flotante, panel, borde de pantalla) y se puede evaluar aparte si también se quieren más cuadradas.

## Reproductor: puntuación y portada

- **Puntuación simplificada**: se quitó la escala de 5 estrellas superpuesta sobre la portada (era difícil de ver según la imagen de fondo). Queda solo el corazón de favorito, reubicado en el panel de info (arriba a la derecha del título, como el "+" de Spotify) en vez de flotar sobre la imagen.
  - `current_playing.xml`, `player_media_info.xml`, `PlayerFragment.kt` (se eliminó todo el código de las 5 estrellas: `fiveStar1-5ImageView`, `setSongRating()`, `getStarForRating()`, y el import de `StarRating` que quedó sin uso).
- **Portada con más aire**: antes ocupaba toda la pantalla de borde a borde; ahora tiene margen generoso (36dp) y esquinas redondeadas (`ShapeAppearance.Material3.MediumComponent`), separada visualmente del texto — como la referencia de Spotify.

## Mix: una sola barra, no un carrusel

El Mix pasó de ser un carrusel de 25 tarjetas (una por canción) a **una sola fila tipo playlist**: portada de la primera canción + "Mix: <género>" + "N songs", tocarla reproduce el mix completo desde el principio. Se eliminó `HomeTrackDelegate.kt` (ya no hace falta, no había otro uso).

## Mix: estable durante el día

Antes se generaba un mix nuevo cada vez que se refrescaba Home — muy aleatorio. Ahora se genera **una vez por día**: se guarda el género elegido, la fecha, y el orden exacto de las 25 canciones (`Settings.homeMixDate/homeMixGenre/homeMixTrackIds`, nuevas propiedades en `Settings.kt`, usando el mismo sistema de `SharedPreferences` que ya usa el resto de la app). Mientras sea el mismo día, pull-to-refresh no lo cambia — recién al día siguiente se arma uno nuevo.

Como la API no tiene un endpoint para "traer canciones por ID", restaurar el mix guardado implica volver a pedir canciones de ese género (una sola llamada, igual que antes) y quedarse solo con las que coinciden con los IDs guardados, en el mismo orden. Si por algún motivo no se puede restaurar (ej. el género ya no tiene esas canciones), se genera uno nuevo automáticamente.

Archivos: `HomeViewModel.kt` (`fetchOrRestoreMix()`, `generateMix()`, `restoreMix()`), `Settings.kt`.

## Jerarquía de texto en tarjetas

Títulos (canción/álbum/mix) en **bold**, artista/subtítulo se mantiene sutil (color `colorOnSurfaceVariant`, sin bold) — en las tarjetas del carrusel, el grid de shortcuts, y la fila del Mix. La mini barra de reproducción se dejó como estaba (ya se le había bajado el peso antes por el reclamo opuesto de que se veía muy bold).

Archivos: `home_carousel_item.xml`, `home_shortcut_item.xml`, `home_fragment.xml`.

## "Random" → "Discover"

Se renombró el shelf de Home de "Random" a "Discover". Como ese string también se usa en el dropdown de ordenar álbumes en otras pantallas, se creó un string nuevo dedicado (`home.discover_title`) solo para el shelf de Home, en vez de reusar `main.albums_random` — así el dropdown de orden sigue diciendo "Random" (que ahí sí tiene sentido como nombre de un criterio de orden).

## Reproductor: solo lo esencial, resto a un toque

Se sacó todo lo que no fuera título y artista de la pantalla del reproductor: álbum, género, "4/25" (posición en la cola), y la duración total de la cola. Quedan solo **título, artista, y la barra de progreso** (posición/duración de la canción actual, eso sí se queda — es información básica de reproducción, no "metadata extra").

En su lugar, **tocar el título** navega directo al álbum de la canción (reusa la acción "Go to album" que ya existía en el menú contextual) — ahí sí se ve toda la información completa.

Nota: la app ya tenía una opción en Ajustes ("Show details in Now Playing") que mostraba género/año/bitrate — como esos campos ya no existen en esta pantalla, esa opción quedó sin efecto (no se borró, por si en algún momento se quiere traer de vuelta esa información en otro lugar).

Archivos: `player_media_info.xml`, `PlayerFragment.kt`.

## Corazón de una sola línea

El corazón de favorito se veía con doble contorno (dos capas superpuestas: `rating_heart_hollow` + `rating_heart_hollow_outline`, pensado originalmente para tener un color de relleno y otro de borde). Se simplificó a un solo ícono de una sola línea (`rating_heart_hollow`/`rating_heart_full` directos, sin la capa extra), tintado con el color de acento del tema.

Archivo: `PlayerFragment.kt` (también se limpiaron `setLayerDrawableColors()`, y los imports `LayerDrawable`/`RM` que quedaron sin uso).

## Pendiente

- **Seguir aplicando el estilo de Home al resto de pantallas** (Media Library, listas de canciones, Playlists, Search, Settings, etc.) — se va a ir pantalla por pantalla.
