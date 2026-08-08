# Changes

## Pantalla interna de artista

- Se agregó `ArtistDetailFragment`, una pantalla dedicada inspirada en Spotify que reemplaza
  la antigua redirección del artista a una cuadrícula genérica de álbumes.
- El encabezado usa la portada real del artista a todo lo ancho, degradado hacia la superficie,
  nombre destacado, cantidad real de álbumes y un toolbar compacto que revela el nombre al
  desplazarse.
- Se incorporaron acciones funcionales para reproducir o descargar toda la discografía del
  artista usando las rutas existentes de `MediaPlayerManager` y `DownloadUtil`.
- La sección **Popular** muestra cinco accesos compactos con portada, álbum y duración. Al tocar
  una canción se inicia la cola del artista recuperada por la búsqueda del servidor desde esa
  posición.
- La sección **Albums** reutiliza las tarjetas horizontales de Home, ordena la discografía por
  año descendente y abre cada álbum en su colección normal de canciones.
- La carga tiene pull-to-refresh, estado vacío seguro y un fallback que toma canciones de los
  primeros álbumes cuando el servidor no devuelve coincidencias de búsqueda.
- Se añadió soporte para `getTopSongs`: **Popular** usa ahora el ranking real del servidor y
  conserva la búsqueda/primeros álbumes como fallback si el endpoint no está disponible o no
  devuelve resultados para ese artista.
- Se añadió soporte para `getArtistInfo2`: cuando el servidor lo entrega, la pantalla muestra
  una biografía expandible y un carrusel de artistas similares, cuyas tarjetas abren su propia
  página de artista. Ambos bloques se ocultan limpiamente cuando faltan metadatos.
- La información externa es enriquecimiento opcional: fallos de agentes externos o servidores
  Subsonic antiguos no impiden cargar la discografía ni reproducir el artista.
- Se añadieron pruebas de contrato para el parseo y los parámetros de `getArtistInfo2` y
  `getTopSongs`. El APK se compiló, instaló y verificó contra el servidor real: el ranking de
  Rammstein devolvió canciones destacadas como “Du hast”, “Deutschland” y “Sonne”.
- La lista general de artistas conserva sus vistas de lista/cuadrícula y filtros; solo cambió
  el destino de sus tarjetas reales. Los índices/carpetas mantienen su navegación anterior.

Archivos principales:
- `ultrasonic/src/main/kotlin/org/moire/ultrasonic/fragment/ArtistDetailFragment.kt`
- `ultrasonic/src/main/kotlin/org/moire/ultrasonic/model/ArtistDetailModel.kt`
- `ultrasonic/src/main/kotlin/org/moire/ultrasonic/adapters/ArtistPopularTrackDelegate.kt`
- `ultrasonic/src/main/kotlin/org/moire/ultrasonic/adapters/SimilarArtistDelegate.kt`
- `ultrasonic/src/main/res/layout/artist_detail.xml`
- `ultrasonic/src/main/res/layout/artist_popular_track_item.xml`
- `ultrasonic/src/main/res/layout/artist_similar_item.xml`
- `ultrasonic/src/main/res/drawable/bg_artist_hero_gradient.xml`
- `ultrasonic/src/main/res/navigation/navigation_graph.xml`
- `core/subsonic-api/src/main/kotlin/org/moire/ultrasonic/api/subsonic/SubsonicAPIDefinition.kt`
- `core/subsonic-api/src/main/kotlin/org/moire/ultrasonic/api/subsonic/models/ArtistInfo.kt`
- `core/subsonic-api/src/integrationTest/kotlin/org/moire/ultrasonic/api/subsonic/SubsonicApiGetArtistInfo2Test.kt`
- `core/subsonic-api/src/integrationTest/kotlin/org/moire/ultrasonic/api/subsonic/SubsonicApiGetTopSongsTest.kt`

- Simplified the playlist creation row label and softened its typography and icon treatment.
- Added compact-list and two-column square-grid playlist views with a view toggle,
  four-cover collages, neutral surfaces, and integrated download actions.
- Added subtle gutters between playlist collage covers for clearer visual separation.
- Removed the full-card grid scrim and the outer frame around the new-playlist grid item.
- Restyled playlist list mode as compact genre-inspired horizontal cards while preserving
  the existing two-column square grid.
- Added wider neutral gutters inside playlist collages and moved grid playlist titles below
  smaller, better-spaced covers with a more restrained shadow.
- Rebound playlist cells when switching layouts so list and grid changes appear immediately.
- Differentiated playlist collage frames by layout: pure black in list mode and a wider,
  lighter neutral border around the unchanged grid-card footprint.

Registro cronológico de los cambios hechos sobre el Ultrasonic oficial en este fork/copia privada.

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

### Portada cuadrada en el reproductor completo

- La carátula del reproductor completo deja de llenar el área rectangular disponible y mantiene una relación fija 1:1, centrada y limitada por el lado más corto de la pantalla.
- Se conserva `centerCrop`, por lo que la imagen se recorta al cuadrado sin deformarse.

Archivo: `current_playing.xml`.

### Tipografía con más aire en el reproductor completo

- El título baja de `TitleLarge` a `BodyLarge`, manteniendo el peso medio de `Ultrasonic.PrimaryText`; el artista conserva `BodyMedium` con el estilo secundario ligero.
- Se amplían los márgenes laterales a 20dp, se agregan 6dp reales entre título y artista y se ancla el margen inferior del artista para separar correctamente el texto del slider.

Archivo: `player_media_info.xml`.

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

## Media Library: artistas estilo Spotify

- La lista vertical de artistas pasó a una cuadrícula de tres columnas, inspirada en la biblioteca de Spotify: retratos circulares grandes y nombre centrado debajo.
- Se ocultaron las letras de sección dentro de cada celda para dar prioridad a las imágenes. El selector de carpeta conserva el ancho completo cuando el servidor lo necesita.
- El cambio está aislado a Media Library; los resultados de artistas en Search siguen usando la fila compacta existente.
- Se conserva el toque para abrir los álbumes del artista y la pulsación larga para el menú contextual.

Archivos: `ArtistListFragment.kt`, `ArtistRowBinder.kt`, `grid_item_artist.xml` (nuevo), `styles.xml`.

### Fix de compilación

- AAPT interpretaba `ShapeAppearanceOverlay.Ultrasonic.Circle` como un estilo heredado de `ShapeAppearanceOverlay.Ultrasonic`, que no existe. Se agregó `parent=""` explícito al estilo circular para impedir esa herencia implícita.

### Placeholder para artistas sin imagen

- Se reemplazó el placeholder blanco de la cuadrícula por uno inspirado en la referencia: círculo `colorSurfaceContainerHighest` con una silueta clara `colorOnSurfaceVariant`.
- El nuevo recurso se usa solo en Media Library; otras pantallas conservan su placeholder actual.

Archivos nuevos: `artist_placeholder.xml`, `artist_placeholder_icon.xml`.

- Los nombres bajo los retratos usan `sans-serif-light`: trazos más finos, pero conservando el ancho y las proporciones normales de las letras.

### Tipografía secundaria consistente

- Se creó el estilo reutilizable `Ultrasonic.SecondaryText`, con `sans-serif-light`, para nombres de artistas y metadatos musicales secundarios.
- Se aplicó en Home, Media Library/Search, listas y cuadrículas de álbumes, filas de canciones, cabecera de álbum, mini reproductor, reproductor completo y letras. Los títulos principales conservan su peso actual.

Archivos: `styles.xml`, `home_carousel_item.xml`, `home_shortcut_item.xml`, `home_fragment.xml`, `grid_item_artist.xml`, `list_item_artist.xml`, `grid_item_album.xml`, `list_item_album.xml`, `list_item_track_details.xml`, `list_header_album.xml`, `now_playing.xml`, `player_media_info.xml`, `player_slider.xml`, `lyrics.xml`.

## Álbumes del artista estilo Spotify

- La pantalla que se abre al tocar un artista ahora inicia en una cuadrícula de tres columnas.
- Las portadas son cuadradas 1:1, sin tarjeta, elevación ni fondo visible; debajo quedan el título y el artista ligero en una sola línea.
- Se ocultó la estrella superpuesta en la vista de cuadrícula para reducir ruido visual. El menú contextual mediante pulsación larga conserva las acciones existentes, incluido favorito.
- El control de vista permanece disponible para cambiar manualmente a la lista compacta.

Archivos: `AlbumListFragment.kt`, `grid_item_album.xml`.

### Vista de lista de álbumes

- Se equilibró la jerarquía tipográfica: título `BodyLarge` y artista ligero `BodyMedium`, con color secundario y truncado al final.
- La portada creció a 72dp y quedó casi cuadrada (radio de 3dp), dentro de un `MaterialCardView` con elevación sutil de 3dp para simular la profundidad de una caja de CD.
- Las filas crecieron a 88dp para dar aire a la portada y los textos sin perder densidad.
- Se ocultó también la estrella de favorito en la vista de lista y el área de texto ahora aprovecha todo el ancho. Favorito sigue accesible mediante el menú contextual de pulsación larga.

Archivo: `list_item_album.xml`.

## Géneros: tarjetas con portadas reales

- La lista antigua de texto se reemplazó por una cuadrícula de dos columnas con tarjetas rectangulares, esquinas de 4dp y elevación sutil.
- Cada tarjeta muestra el nombre del género y carga progresivamente la portada de una canción que realmente pertenece a ese género. La imagen aparece inclinada a la derecha, inspirada en las tarjetas de exploración de Spotify.
- Para no saturar el servidor, las portadas se solicitan solo cuando una tarjeta se hace visible, con un máximo de cuatro consultas simultáneas y caché durante la vida de la pantalla.
- Se consultan hasta diez canciones por género para escoger la primera con portada. Si ninguna tiene imagen o la consulta falla, queda un placeholder oscuro con una nota musical.
- Pull-to-refresh invalida las portadas de la pantalla y evita que respuestas antiguas se apliquen después de refrescar.

Archivos: `SelectGenreFragment.kt`, `GenreAdapter.kt`, `select_genre.xml`, `genre_card_item.xml` (nuevo), `genre_placeholder.xml` (nuevo), `genre_placeholder_icon.xml` (nuevo).

## Peso medio para títulos musicales

- Los títulos musicales que usaban `bold` pasan al estilo reutilizable `Ultrasonic.PrimaryText`, basado en `sans-serif-medium` y peso normal de esa familia (aprox. 500 en vez de 700).
- El cambio mantiene la jerarquía frente al texto secundario ligero, pero con contornos más limpios y mejor definición en tamaños pequeños.
- Aplicado a títulos de tarjetas y shortcuts de Home, Mix diario, nombre de canción del reproductor y tarjetas de género. No afecta menús, controles ni Chat.

Archivos: `styles.xml`, `home_carousel_item.xml`, `home_shortcut_item.xml`, `home_fragment.xml`, `player_media_info.xml`, `genre_card_item.xml`.

### Mini reproductor: peso y escala tipográfica

- El título de la mini barra adopta también `Ultrasonic.PrimaryText` (`sans-serif-medium`).
- Para recuperar proporción con la portada de 60dp, el título baja de `BodyLarge` a `BodyMedium` y el artista de `BodyMedium` a `BodySmall`; el artista conserva el estilo ligero secundario.

Archivo: `now_playing.xml`.

## Barra de filtros de álbumes más compacta

- El selector Lista/Cover deja de mostrar las palabras “List” y “Cover”: queda como botón de icono de 40dp, con descripción accesible.
- El icono ahora representa la acción disponible: en cuadrícula ofrece el icono de lista y en lista ofrece el de cuadrícula.
- El desplegable de orden baja de 48dp a 40dp, reduce padding y usa `LabelMedium`.
- El marco del desplegable mantiene un grosor fijo de 1dp incluso al enfocarse y usa `colorOutlineVariant` para verse más sutil.
- Al ser un componente compartido, el cambio se aplica tanto a los álbumes de un artista como a la biblioteca general.
- El selector de vista queda sin fondo ni contorno: solo se muestra el icono, manteniendo un área táctil de 40dp y el ripple al pulsar.

Archivos: `filter_button_bar.xml`, `FilterButtonBar.kt`, `styles.xml`.

## Artistas: cuadrícula, lista y orden

- Artistas adopta la misma barra compacta de Álbumes, con icono sin marco para alternar entre cuadrícula y lista y un desplegable de orden.
- El desplegable usa las mismas categorías disponibles en Álbumes: añadidos recientemente, reproducidos recientemente, frecuentes, mejor valorados, aleatorios, favoritos, alfabético y género (respetando las capacidades online/offline del servidor).
- Los criterios basados en actividad consultan la colección de álbumes correspondiente y muestran sus artistas en el mismo orden; Favoritos combina artistas marcados y artistas de álbumes favoritos. Género abre el mismo selector usado por Álbumes.
- Se corrigió el dato `albumCount`: el API lo entregaba, pero `APIArtistConverter` no lo copiaba al modelo de dominio. Ahora queda disponible tanto para artistas ID3 como para índices de carpetas.
- La cuadrícula conserva tres columnas, retratos circulares y placeholder oscuro. La lista usa retratos circulares de 56dp, filas de 72dp y nombres ligeros.
- El selector de carpeta, cuando aparece, ocupa las tres columnas completas.
- Funciona tanto en la pantalla independiente de Artistas como dentro de la biblioteca con pestañas. También se corrigió la persistencia de la vista elegida en la biblioteca general.

Archivos: `ArtistListFragment.kt`, `ArtistListModel.kt`, `APIArtistConverter.kt`, `APIArtistConverterTest.kt`, `ArtistRowBinder.kt`, `list_item_artist.xml`, `MainFragment.kt`, `SortOrder.kt`, `FilterButtonBar.kt`, `strings.xml`.

### Fix de crash al abrir Artistas

- La primera implementación del orden asignaba el resultado de una expresión nullable directamente a `MutableLiveData.value`. Antes de la primera carga esa expresión era `null`, por lo que `EntryListFragment.defaultObserver`, que recibe una lista no nula, caía con `NullPointerException` al activarse la vista.
- El modelo nuevo nunca publica `null`: guarda el criterio antes de la carga y solo emite listas completas. También cancela una consulta anterior si el usuario cambia rápidamente de filtro, evitando que una respuesta vieja reemplace la selección más reciente.

Archivo: `ArtistListModel.kt`.

## Media Library: pantalla Songs simplificada

- La pestaña Songs deja de abrir una selección aleatoria disfrazada de biblioteca: ahora inicia en “Todas las canciones” y carga la colección por páginas mediante búsqueda vacía de Subsonic/OpenSubsonic.
- Se completó el soporte faltante de `songOffset` en `search2` y `search3`; al llegar al final de la lista se solicita la página siguiente sin repetir los primeros resultados. Se añadieron pruebas de los parámetros para ambos endpoints.
- Offline usa las canciones almacenadas en la base local, ordenadas por título y paginadas con el mismo tamaño.
- El filtro compacto ofrece “Todas las canciones”, “Aleatorio”, “Favoritas”, “Por artista” y “Por género”. Se corrigió la traducción anterior “Número de estrellas”, que no describía el contenido.
- “Por artista” abre el selector alfabético de artistas y pagina una búsqueda validada por `artistId`/nombre exacto; offline consulta directamente las canciones cacheadas del artista. “Por género” reutiliza el selector y el endpoint `getSongsByGenre` existentes.
- Songs tiene una fila propia de 76dp: portada cuadrada de 56dp, título, `artista · álbum` como texto secundario y menú de tres puntos. Se eliminan de esta pestaña las estrellas, checkboxes, indicadores técnicos y la barra inferior de selección permanente.
- Tocar una canción comienza a reproducirla inmediatamente desde su posición en la lista cargada. La pulsación larga y el botón de tres puntos conservan las acciones contextuales existentes.
- Se eliminan Share y el Play duplicado de la toolbar exclusivamente en Songs. Play pasa al hueco que las pestañas con cuadrícula usan para alternar Lista/Grid y reproduce todas las canciones actualmente cargadas por el filtro activo.
- En Aleatorio el primer lote conserva `Settings.maxSongs` (25 por defecto); al llegar al final se agrega otro lote. Play incluye todos los lotes que estén visibles en ese momento.
- Los álbumes, playlists, géneros y otras pantallas que reutilizan `TrackCollectionFragment` conservan sus filas y selección masiva anteriores.

Archivos principales: `TrackCollectionFragment.kt`, `TrackCollectionModel.kt`, `LibraryTrackBinder.kt` (nuevo), `list_item_library_track.xml` (nuevo), `SearchCriteria.kt`, `RESTMusicService.kt`, `OfflineMusicService.kt`, `SubsonicAPIDefinition.kt`, `ApiVersionCheckWrapper.kt`, `MainFragment.kt`, `SortOrder.kt`, `FilterButtonBar.kt`, `strings.xml` y pruebas `SubsonicApiSearchTwoTest.kt`/`SubsonicApiSearchThreeTest.kt`.

### Fix: las tarjetas de Géneros no respondían

- El listener estaba conectado al `MaterialCardView` exterior, pero el `ConstraintLayout` interior también era `clickable` y consumía el toque sin ejecutar la navegación.
- La tarjeta completa queda como único elemento clicable/focalizable y conserva el ripple; tocar el texto, el placeholder o la portada abre nuevamente las canciones del género.

Archivo: `genre_card_item.xml`.

## Home: accesos directos desplazables a la biblioteca

- La fila superior de Home funciona como carrusel horizontal sin barra visible y ahora reúne Playlists, Artists, Albums, Songs y Genres.
- Se elimina el acceso ambiguo a la pantalla contenedora “Media Library”: cada botón abre directamente su apartado.
- Albums y Songs incorporan su propia barra compacta de filtros cuando se abren desde Home, conservando cuadrícula/lista, orden y Play para las canciones mostradas.

Archivos: `home_fragment.xml`, `HomeFragment.kt`, `navigation_graph.xml`, `AlbumListFragment.kt`, `TrackCollectionFragment.kt`, `list_layout_track_filterable.xml` (nuevo).

## Playlists: descarga directa y creación

- Cada playlist muestra un icono de descarga al final de la fila, reutilizando la misma descarga que antes estaba únicamente en el menú contextual.
- Una fila final con “+” inicia la creación de una playlist y solicita primero su nombre.
- La creación ya no guarda una fila vacía: después del nombre abre un editor con búsqueda y filtros Todas, Aleatorio, Por artista y Por género.
- La selección se conserva al cambiar de búsqueda o filtro, muestra el contador y solo habilita Guardar cuando hay canciones elegidas. La playlist se envía al servidor con sus canciones y la lista se refresca al regresar.
- Las acciones que dependen del servidor se ocultan en modo offline; los menús contextuales existentes se conservan.
- Se corrigió la apertura de playlists tras añadir el botón de descarga: toda la fila tiene ahora su propio listener, mientras el icono conserva una acción independiente. La navegación envía únicamente `playlistId`/`playlistName`, evitando interpretar el identificador como álbum o carpeta.
- Cada fila resuelve en segundo plano el estado agregado de sus canciones y muestra “Comprobando”, “No descargada”, “Descargando”, “Descargada”, “Parcial”, “Error” o “Vacía”. Los cambios de `DownloadService` actualizan las playlists afectadas en tiempo real.
- Cuando toda la playlist está descargada, el icono cambia a eliminar. Tras confirmar, se borran únicamente los archivos locales y la copia offline de la lista; la playlist del servidor permanece intacta.

Archivos: `PlaylistsFragment.kt`, `CreatePlaylistFragment.kt`, `PlaylistTrackPickerBinder.kt`, `list_item_playlist.xml`, `list_item_create_playlist.xml`, `list_item_playlist_track_picker.xml`, `create_playlist.xml`, `create_playlist_editor.xml`, `bg_playlist_create_icon.xml`, `navigation_graph.xml` y `strings.xml`.
