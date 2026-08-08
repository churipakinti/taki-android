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

## Auditoría: bugs encontrados y corregidos

Revisión completa del código de los rediseños de Home, Media Library/Playlists y Artist Detail
(no solo lo documentado en este changelog), enfocada en bugs reales de concurrencia y manejo de
errores, no en estilo.

1. **Home se quedaba en blanco si un solo shelf fallaba** — `loadHomeScreen()` lanzaba los 6
   fetches (shortcuts, favoritos, nuevos, aleatorios, frecuentes, mix) como hijos `async` de un
   mismo `coroutineScope`; si uno fallaba (por ejemplo, navegación offline por carpetas, donde
   `getAlbumList()` siempre lanza excepción), cancelaba a los demás antes de asignar ninguna
   `LiveData`, dejando toda la pantalla vacía. Se protegió cada fetch individualmente para que un
   fallo puntual no tumbe a los demás. → `HomeViewModel.kt`
2. **Artist Detail no "fallaba suave" como decía este changelog** — el fallback de canciones
   (`fetchArtistTracks`/`fetchTracksFromFirstAlbums`) no estaba protegido con `runCatching`, a
   diferencia de la biografía y el top de canciones. Si fallaba, la excepción abortaba `load()`
   antes de mostrar la discografía ya obtenida, dejando la pantalla atascada. → `ArtistDetailModel.kt`
3. **Paginación de canciones por género se podía corromper** — `getSongsForGenre()` no tenía guard
   anti-solapamiento (a diferencia de `getAllSongs`/`getSongsForArtist`) y avanzaba el offset por
   el tamaño de página solicitado en vez de por la cantidad real de canciones recibidas, pudiendo
   saltar o duplicar canciones al hacer scroll rápido. → `TrackCollectionModel.kt`,
   `TrackCollectionFragment.kt` (loadMoreTracks ahora respeta `canLoadMoreGenreSongs`)
4. **Cambiar de filtro rápido podía mostrar datos de otro filtro** — en Library y en Crear
   Playlist, cada cambio de filtro lanzaba una corrutina nueva sin cancelar la anterior; ganaba la
   respuesta que llegaba último, no la más reciente. Se agregó un `Job` cancelable en cada punto de
   entrada, siguiendo el mismo patrón ya usado en `ArtistListModel.setSortOrder()`. →
   `TrackCollectionFragment.kt`, `CreatePlaylistFragment.kt`

## Álbum: pantalla de detalle estilo Spotify

Siguiente pantalla en el orden ya acordado de migración visual. `TrackCollectionFragment` no tenía
una pantalla de álbum dedicada — reusaba el mismo header/fila genéricos que playlists, géneros y
canciones de artista. Se creó una variante scoped a `navArgs.isAlbum == true`, sin tocar el
comportamiento de esas otras pantallas.

- **Hero**: portada del álbum a todo lo ancho (300dp) con degradado, título y subtítulo
  (artista/año/cantidad de canciones), mismo lenguaje visual que Artist Detail. El título del
  toolbar se revela al hacer scroll más allá del hero (la cabecera es el ítem 0 de la lista, no un
  scroll view persistente, así que se rastrea el offset de scroll contra una altura fija en vez de
  medir la vista, que se recicla al salir de pantalla).
- **Acciones**: fila de play/shuffle/descarga en el hero, reemplazando al "play all" del menú
  overflow (oculto solo para álbumes, sigue disponible en otras pantallas).
- **Filas de canción**: número de pista siempre visible (no respeta el toggle de Ajustes en esta
  pantalla en particular), con prefijo de disco (“1-03”) en álbumes multi-disco; artista oculto
  cuando el álbum tiene un solo artista, visible en compilaciones; sin estrella de rating por fila
  (sigue disponible por el menú contextual de pulsación larga, mismo criterio que se aplicó antes
  al Reproductor); sin portada por fila (ya se ve una vez en el hero). Selección múltiple, estado
  de descarga y menú contextual siguen funcionando igual que antes, sin tocar la lógica Kotlin
  compartida (`TrackViewHolder`/`TrackViewBinder`), solo el layout que bindean.
- Playlists, géneros, canciones de artista, favoritos, todas las canciones y video conservan su
  header y fila genéricos sin ningún cambio.

Archivos nuevos: `AlbumDetailHeaderBinder.kt`, `album_detail_header_item.xml`,
`list_item_track_album.xml`.
Archivos modificados: `TrackCollectionFragment.kt` (registro condicional de binders, listener de
revelado de título, ocultar "play all" para álbumes), `TrackViewBinder.kt`/`TrackViewHolder.kt`
(nuevos parámetros opcionales `layout`/`showArtist`/`showRating`/`trackNumberText`, todos con
default que preserva el comportamiento existente en el resto de la app), `strings.xml`.

## Álbum: sección "About" (reseña/notas del álbum)

Se agregó soporte para `getAlbumInfo2` — el endpoint hermano de `getArtistInfo2` que ya usa Artist
Detail, disponible desde Subsonic API 1.14.0. Devuelve `notes` (reseña del álbum, viene del mismo
agente de metadata externo que la biografía del artista, típicamente Last.fm), además de
`musicBrainzId`/`lastFmUrl` que no se usan todavía. Igual que la biografía del artista, es
enriquecimiento opcional: si el servidor no lo soporta o el álbum no tiene reseña, la sección
simplemente no aparece, sin afectar el resto de la pantalla.

- Sección "About" expandible en el hero de Album Detail, mismo patrón de "mostrar más/menos" que
  la biografía del artista (colapsada a 5 líneas, HTML del servidor convertido a texto plano).
- La info del álbum se pide en paralelo a la lista de canciones (llamada más lenta y separada);
  como el header de Album Detail es un ítem de `RecyclerView` (no una vista persistente como en
  Artist Detail), al llegar la reseña se reconstruye el objeto `AlbumHeader` con las notas y se
  reenvía la lista completa — `AlbumHeader` no tiene `equals()`/`hashCode()` propios, así que
  `DiffUtil` solo detecta el cambio si es una instancia nueva, no alcanza con mutar los campos del
  objeto existente. Funciona sin importar si la reseña llega antes o después de que la lista de
  canciones ya se haya renderizado.

## Listas de canciones: tap reproduce, long-press selecciona

Cambio de fondo en `TrackViewBinder`/`TrackViewHolder`, usado por Album Detail, Playlists, Género,
canciones de artista, Favoritos, Todas las canciones y Bookmarks (todo lo que no sea el tab Songs
de la biblioteca, que ya tenía su propia fila). Motivado por feedback directo: la fila se sentía
"sin tocar" y tenía un checkbox siempre visible + una barra inferior de 7 botones que aparecía de
forma poco intencional.

- **Bug de fondo corregido**: hasta ahora, tocar una canción en estas pantallas **no la
  reproducía** — activaba el checkbox de selección (`checkable` era una constante siempre `true`).
  Reproducir requería seleccionar y tocar "Play Now", o abrir el menú contextual. Ahora tocar
  reproduce inmediatamente (misma cola que ya arma `playFromHere`), como cualquier reproductor de
  música.
- **Selección por long-press**: mantener presionada una fila entra en "modo selección" (estilo
  Gmail/Fotos) y la selecciona; con el modo activo, tocar otras filas las selecciona/deselecciona
  en vez de reproducir. Deseleccionar la última fila sale del modo automáticamente. El menú
  contextual (Play Next, Play Last, Ir al álbum, Compartir, Pin...) ya no se abre con long-press.
- **Fila simplificada fuera del modo selección**: se quita el checkbox siempre visible; quedan
  tres íconos por fila — descargar, agregar a playlist ("+") y "⋮" (abre el mismo menú contextual
  de siempre, sin perder ninguna acción).
- **Barra inferior de selección simplificada**: de 7 botones (Play Now/Next/Last, Pin, Unpin,
  Download, Delete) a 2 — Descargar y Agregar a playlist. Las demás acciones bulk (pin, unpin,
  delete, play next/last) quedan disponibles solo canción por canción vía el menú "⋮", no en
  bloque; si hace falta recuperarlas para selección múltiple, es una tarea aparte.
- `BookmarksFragment` (reanuda desde la posición guardada) se adaptó: en vez de depender del
  botón "Play Now" ahora eliminado, sobreescribe `onItemClick` para reanudar al tocar.

Archivos: `TrackViewBinder.kt`, `TrackViewHolder.kt`, `list_item_track.xml`,
`list_item_track_album.xml`, `track_buttons.xml` (simplificado a 2 botones),
`TrackCollectionFragment.kt`, `BookmarksFragment.kt`, `strings.xml`.

## Nuevo: agregar canciones a una playlist existente

No existía en ningún lado de la app — solo "Crear playlist" (arma una playlist nueva). Se agregó
`PlaylistUtil.kt`: como Subsonic no tiene un endpoint de "agregar canción", `addToPlaylist` trae
las canciones actuales de la playlist elegida (`getPlaylist`) y reenvía la lista completa más las
nuevas vía `createPlaylist(id = ...)` (que en Subsonic sobreescribe una playlist existente cuando
se le pasa su id). El diálogo para elegir playlist reusa `ItemSelectionDialogFragment` (mismo
patrón que ya usaba el selector de artista/género en Crear Playlist). Disponible desde el ícono
"+" de cada fila y desde el botón "Agregar a playlist" de la barra de selección múltiple. Solo
online (oculto en modo offline, igual que el resto de acciones de servidor).

Archivos nuevos: `util/PlaylistUtil.kt`.
Modificados: `TrackCollectionFragment.kt`, `strings.xml`.

## Pulido tras feedback con capturas de Spotify

- **Bug: back durante selección múltiple mandaba a Home** en vez de salir del modo selección.
  Se agregó un `OnBackPressedCallback` habilitado solo mientras `selectionModeActive`, que limpia
  la selección y sale del modo en vez de dejar que el back navegue hacia atrás en la pila.
  → `TrackCollectionFragment.kt`.
- **Check de selección rediseñado como círculo** (relleno con `colorPrimary` + tilde blanca cuando
  está marcado, anillo sutil `colorOnSurfaceVariant` cuando no) en vez del cuadrado clásico de
  Android. → `btn_check_circle_on.xml`, `btn_check_circle_off.xml`, `btn_check_circle.xml`
  (nuevos), `list_item_track.xml`, `list_item_track_album.xml`.
- **Fila de canción simplificada a un solo ícono** ("⋮"), sacando los íconos de descargar y
  agregar-a-playlist por fila que se habían agregado en el paso anterior — comparado contra
  capturas reales de Spotify, la fila se sentía sobrecargada. Ambas acciones (y todas las demás)
  siguen disponibles tocando "⋮", que abre el mismo menú contextual de siempre; se agregó
  "Add to playlist" a ese menú ya que antes solo estaba como ícono aparte. →
  `TrackViewHolder.kt`, `TrackViewBinder.kt`, `list_item_track.xml`, `list_item_track_album.xml`,
  `context_menu_track_collection.xml`, `TrackCollectionFragment.kt`.
- **Se quitó "Share" de toda la app** — no es una función que se vaya a usar; la visión del
  proyecto es un reproductor local/enfocado en escuchar, no una red social de música. Se sacaron
  los ítems de menú y el código que los maneja: menú contextual de canciones (lista y Reproductor),
  overflow de Library/Playlists/Álbumes, toolbar del Reproductor completo (compartir canción y
  compartir playlist/cola). También se ocultó **Shares** del drawer (pantalla de gestión de links
  compartidos del servidor) — mismo criterio que Podcasts/Video/Chat: se oculta de la UI, no se
  borra el código (`SharesFragment`, `ShareHandler` siguen existiendo, simplemente sin ningún
  punto de entrada). Archivos: `context_menu_track.xml`, `context_menu_track_collection.xml`,
  `track_collection_menu.xml`, `nowplaying.xml`, `navigation_drawer.xml`, `ContextMenuUtil.kt`,
  `Utils.kt` (adapters), `TrackCollectionFragment.kt`, `PlayerFragment.kt`.

## Segunda ronda de pulido en Album Detail (feedback en dispositivo)

- **Números de pista de álbum ya no se fuerzan**: la pasada anterior mostraba el número siempre,
  ignorando el ajuste general de la app (`Settings.shouldShowTrackNumber`, apagado por defecto).
  Se sacó ese forzado — Album Detail ahora se comporta como el resto de pantallas, y por defecto
  no muestra números (igual que la referencia de Spotify).
- **Más aire entre el check de selección y la columna de número** cuando ambos son visibles (antes
  quedaban pegados, sin margen). → `list_item_track_album.xml`, `list_item_track_details.xml`.
- **Agrupación por disco en álbumes dobles**: en vez de un prefijo "1-03" en cada número de pista,
  ahora aparece un separador liviano ("Disc 2" + línea) antes de las canciones de cada disco
  siguiente. Nuevo tipo de ítem `DiscHeader`, insertado entre pistas del `RecyclerView` solo
  cuando el álbum tiene más de un disco. Archivos nuevos: `DiscHeader.kt`, `DiscHeaderBinder.kt`,
  `disc_header_item.xml`.
- **Se quitó el ícono de descargar del header** de Album Detail (quedaban play + shuffle + descarga
  compitiendo por espacio); descargar el álbum completo sigue disponible seleccionando canciones
  y usando la barra inferior, o canción por canción desde el "⋮".

Archivos: `TrackCollectionFragment.kt`, `AlbumDetailHeaderBinder.kt`, `album_detail_header_item.xml`,
`list_item_track_album.xml`, `list_item_track_details.xml`, `strings.xml`.

## Ícono de "descargado" más discreto

El ícono que aparece junto a la duración cuando una canción ya está descargada (no es un botón,
es un indicador de estado) usaba `ic_downloaded.xml`, una flecha hacia una bandeja bastante pesada
visualmente. A pedido, se mantiene (indica info real y útil para un reproductor offline-first)
pero se reemplazó por un círculo relleno con tilde blanca (mismo lenguaje visual que el check de
selección), en 16dp en vez del tamaño por defecto — más chico y discreto. Aplica a todas las
pantallas que usan `TrackViewHolder` (Álbum, Playlists, Género, etc.), no solo Album Detail.

Archivos nuevos: `ic_downloaded_circle.xml`.
Modificados: `TrackViewHolder.kt`, `list_item_track_album.xml`.

## Downloads: pantalla funcional (antes era de solo lectura)

Downloads existía pero no hacía nada: no se podía tocar una canción, no había menú contextual, no
se podía eliminar ni despinear desde ahí (había TODOs de los devs originales sobre esto). Se
reconstruyó sobre `TrackCollectionFragment` (mismo patrón que ya usa `BookmarksFragment`), ganando
gratis todo lo ya construido: fila con estilo Spotify, selección por long-press, menú "⋮",
"Play All" en el overflow.

- **Nueva acción "Delete"** en el menú contextual compartido (`song_menu_delete`) — usa
  `DownloadAction.DELETE`, que ya existía en el código pero no estaba conectado a ningún botón
  desde que se simplificó la barra inferior. Disponible en todas las pantallas de canciones, no
  solo Downloads (mismo criterio que Pin/Unpin/Download, que tampoco son exclusivos de una
  pantalla).
- **`TrackCollectionModel.downloadedTracks`**: expone `DownloadService.observableDownloads` (ya
  reactivo, se actualiza solo mientras progresan las descargas) como la fuente de datos de esta
  pantalla — no hay "refresh" real porque no se pide nada al servidor, el pull-to-refresh solo
  limpia el spinner.
- Como es una mezcla de canciones de álbumes distintos, se desactivó el header tipo álbum
  (`listModel.showHeader = false`) — no tendría sentido ahí.
- **Navegación con Safe Args**: como `TrackCollectionFragment` espera argumentos (aunque sea
  vacíos, ver nota ya existente en `NavigationActivity.kt` sobre esto — mismo problema que causó
  el crash de "Media Library" documentado antes), se agregó la acción global `toDownloads` y se
  actualizó el ítem del drawer para usarla en vez de navegar por ID crudo.

Archivos: `DownloadsFragment.kt` (reescrito), `TrackCollectionModel.kt`, `navigation_graph.xml`,
`NavigationActivity.kt`, `context_menu_track_collection.xml`, `ContextMenuUtil.kt`.

## Fix: botón de play circular + se repone descargar álbum completo

Primera captura real del dispositivo para Album Detail. Dos correcciones:

- **El botón de play no era un círculo** — se veía como un cuadrado redondeado violeta "fuera de
  lugar". Causa: Material3 por defecto le da al `FloatingActionButton` una esquina redondeada
  fija, no un círculo completo (a diferencia de Material2). Se fuerza el círculo con
  `app:shapeAppearanceOverlay="@style/ShapeAppearanceOverlay.Ultrasonic.Circle"` (estilo que ya
  existía en el proyecto, usado para retratos circulares). El mismo bug existía en Artist Detail
  con el código idéntico — se corrigió ahí también.
- **Se repuso el botón de descargar el álbum completo**, al lado de shuffle — se había sacado en
  una pasada anterior por error de interpretación de un pedido de simplificación que en realidad
  era sobre otra cosa.

Archivos: `album_detail_header_item.xml`, `artist_detail.xml`, `AlbumDetailHeaderBinder.kt`,
`TrackCollectionFragment.kt`, `strings.xml`.

## Header más chico + descarga por disco

Segunda captura real: los íconos de descargar/shuffle/play del header se veían "grotescos", muy
grandes. Se achicaron: descargar y shuffle de 48dp a 38dp (íconos de 25/22dp a 18/16dp), el círculo
de play de 56dp a 44dp (`app:fabCustomSize`/`app:maxImageSize`). Mismo ajuste aplicado a Artist
Detail para no reintroducir la inconsistencia que ya se había señalado antes.

Además, se agregó **descarga por disco**: cada separador "Disc N" ahora tiene su propio ícono de
descarga al final de la línea, para álbumes dobles donde no se quiere bajar los dos discos a la
vez. `DiscHeader` pasó a llevar la lista de canciones de ese disco (agrupadas antes de armar la
lista mezclada del `RecyclerView`), y `DiscHeaderBinder` dispara la descarga de esas canciones
puntuales.

Archivos: `album_detail_header_item.xml`, `artist_detail.xml`, `disc_header_item.xml`,
`DiscHeader.kt`, `DiscHeaderBinder.kt`, `TrackCollectionFragment.kt`, `strings.xml`.

## Botón de play más discreto

El círculo de play seguía "llamando mucho la atención" incluso después de achicarlo. Se bajó de
44dp a 38dp (mismo tamaño que descargar/shuffle), se cambió el fondo de `colorPrimary` (violeta
vivo/dinámico) a `colorSurfaceContainerHighest` (superficie neutra, coherente con el resto del
sistema de diseño en vez de un color de acento saturado), se le agregó `alpha="0.85"` y se sacó la
sombra. Aplicado también en Artist Detail para no reintroducir la inconsistencia.

Archivos: `album_detail_header_item.xml`, `artist_detail.xml`.

## Tipografía: título de álbum y nombres de canción más chicos

- Título del álbum (`album_detail_title`): `HeadlineLarge` (32sp) → `TitleMedium` (16sp), "un
  poquito más grande" que el nombre de las canciones en vez de dominar la pantalla. Mismo cambio
  en Artist Detail (`artist_detail_name`), que tenía el mismo tamaño excesivo.
- Nombre de canción en filas de álbum (`song_title`): `BodyMedium` (14sp) → `BodySmall` (12sp).
- No se tocó el peso de fuente (`Ultrasonic.PrimaryText`/`sans-serif-medium`, ya ajustado de bold
  real en una pasada anterior) — solo tamaño.

Archivos: `album_detail_header_item.xml`, `artist_detail.xml`, `list_item_track_album.xml`.

## Separador de disco: indent + ícono más chico

- El separador "Disc N" + línea se sentía pegado a las filas de canciones, como una sola tira
  continua. Se le subió el `paddingStart` (16dp → 24dp) y algo más de aire vertical, para que se
  lea como un elemento de sección propio.
- El ícono de descargar por disco bajó de 32dp a 28dp (glifo visible a 18dp) para igualar el
  tamaño del ícono de descargar del header, que se había achicado en una pasada anterior.

Archivo: `disc_header_item.xml`.

## Fix: ícono de disco borroso + filas de álbum compactadas

- El ícono de descargar por disco se veía borroso y grande a pesar del ajuste anterior — la causa
  real era usar un `ImageButton` simple con padding manual para controlar el tamaño (un mecanismo
  poco confiable para escalar vectores). Se cambió a `MaterialButton` con `app:iconSize`, el mismo
  mecanismo que ya usan los íconos del header y que sí renderiza nítido.
- Altura de fila en Album Detail: 56dp → 48dp, para reducir el espacio vacío entre canciones ahora
  que el texto es más chico, sin aplanar la lista del todo.

Archivos: `disc_header_item.xml`, `list_item_track_album.xml`.

## "⋮" más sutil (mismo fix que el ícono de disco)

Mismo problema de raíz: el "⋮" era un `ImageButton` simple con padding manual, se veía "rellenito"
comparado con los demás íconos. Se cambió a `MaterialButton` con `app:iconSize` (16dp) — igual que
descargar/shuffle del header y el ícono de disco. Aplicado en `list_item_track_album.xml` y
también en `list_item_track.xml` (compartido por Playlists/Género/canciones de artista/etc.) para
no dejar la misma inconsistencia en otra pantalla.

Archivos: `list_item_track_album.xml`, `list_item_track.xml`.

## Separador de disco: espaciado simétrico

La línea del separador "Disc N" no quedaba centrada entre el texto y el ícono de descarga: 12dp de
espacio después de "Disc N" pero solo 8dp antes del ícono. Se igualaron ambos a 12dp. (No se alineó
la línea con el final de la duración de las canciones a propósito — esa columna cambia de ancho
según cada canción, alinear con eso nunca se vería consistente entre filas.)

Archivo: `disc_header_item.xml`.

## Fix: Downloads no mostraba nada

Bug real reportado en dispositivo: canciones claramente descargadas (con el check azul en Album
Detail) no aparecían en la pantalla Downloads recién migrada.

Causa: `DownloadService.observableDownloads` (la fuente de datos que usaba Downloads, heredada del
código original) solo refleja la cola de descarga **activa** — lo que se está descargando o está
en cola. En cuanto una descarga termina, sale de esa lista por completo; no es un catálogo de "lo
que tengo descargado", es un indicador transitorio de progreso. Por eso cualquier cosa descargada
en una sesión anterior nunca aparecía.

El catálogo real ya existe: es la misma base de datos local (`trackDao`, vía
`activeServerProvider.offlineMetaDatabase`) que usa el modo offline para navegar la música
descargada — se completa cuando una descarga termina (`DownloadTask.kt`) y se limpia cuando se
elimina (`CacheCleaner.kt`). `TrackCollectionModel.downloadedTracks` (LiveData reactiva) se
reemplazó por `getDownloadedTracks()` (consulta puntual a esa base, como el resto de la carga en
esta clase), y `DownloadsFragment` pasa a pedirla en cada carga/refresh, igual que Bookmarks.

Archivos: `TrackCollectionModel.kt`, `DownloadsFragment.kt`.

Archivos nuevos (capa API/dominio, siguiendo el mismo patrón que `ArtistInfo`):
`core/subsonic-api/.../models/AlbumInfo.kt`, `core/subsonic-api/.../response/GetAlbumInfo2Response.kt`,
`core/domain/.../AlbumInfo.kt`, `ultrasonic/.../domain/APIAlbumInfoConverter.kt`.
Modificados: `SubsonicAPIDefinition.kt`, `ApiVersionCheckWrapper.kt` (versión mínima V1_14_0),
`MusicService.kt`/`RESTMusicService.kt`/`CachedMusicService.kt` (offline no necesita override,
usa el default `null`), `TrackCollectionModel.kt` (`getAlbumInfo()`, falla suave con
`runCatching`), `AlbumHeader.kt` (campo `notes` mutable), `AlbumDetailHeaderBinder.kt`,
`album_detail_header_item.xml`, `TrackCollectionFragment.kt`, `strings.xml`.

## Fix: ícono de descargar de Playlists borroso (mismo bug de siempre)

Tercera aparición del mismo problema (ver "Fix: ícono de disco borroso" y "'⋮' más sutil" arriba):
`playlist_download` en `list_item_playlist.xml`/`grid_item_playlist.xml` era un `ImageButton` con
padding manual. Mismo fix: `MaterialButton` + `app:iconSize="20dp"`. Se revisó el resto de
`<ImageButton>` del proyecto por el mismo patrón — el único otro caso real es el "⋮" del tab Songs
(`list_item_library_track.xml`), pero ahí el tamaño del botón coincide con el tamaño natural del
ícono (no lo achica), así que no sufre el mismo bug y se dejó igual. Los `ImageButton` de
`server_row.xml` y los widgets de pantalla de inicio (`appwidget_*.xml`) quedan fuera de alcance:
los widgets usan `RemoteViews`, que no soporta `MaterialButton`.

Archivos: `list_item_playlist.xml`, `grid_item_playlist.xml`, `PlaylistsFragment.kt`.

## Downloads: de lista plana de canciones a gestor de descargas por álbum

El usuario esperaba que Downloads se pareciera más a Playlists — específicamente, tarjetas con
portada en vez de una lista plana de canciones sin relación entre sí. Se rediseñó como un gestor
de descargas real: la pantalla principal ahora agrupa las canciones descargadas por álbum y las
muestra como tarjetas (mismo lenguaje visual que Playlists — `MaterialCardView`,
`colorSurfaceContainerHigh`, portada + título + artista), y cada tarjeta tiene su propio ícono de
papelera para liberar espacio sin tener que entrar al álbum primero.

- **`DownloadsFragment` deja de extender `TrackCollectionFragment`** — ahora extiende
  `MultiListFragment<Album>` directamente. La pantalla muestra tarjetas de álbum, no filas de
  canción, así que no ganaba nada de la maquinaria de selección/menú contextual de
  `TrackCollectionFragment` (pensada para `Track`); `MultiListFragment` ya da swipe-refresh,
  estado vacío y RecyclerView gratis, igual que usa el grid de discografía de Artist Detail.
- **`TrackCollectionModel.getDownloadedAlbums()`**: agrupa la misma base local
  (`offlineMetaDatabase`) por álbum. La cantidad de canciones que muestra la tarjeta es el conteo
  real de tracks descargados de ese álbum, no el `songCount` del servidor (que puede ser mayor si
  solo se bajaron algunas canciones del álbum).
- **Tocar una tarjeta abre `DownloadedAlbumFragment`** (nuevo, extiende `TrackCollectionFragment`
  con `isAlbum = true`) — reutiliza el header hero, agrupación por disco y menú "⋮" que ya tiene
  Album Detail, pero con `getDownloadedAlbumTracks()` como fuente de datos: lee directo de
  `trackDao().byAlbum(id)`, igual que la lista plana anterior. Deliberadamente **no** navega por
  `toTrackCollection`/`getAlbumAsDir` (que depende del servidor vía `MusicServiceFactory`) — el
  sentido de esta pantalla es que funcione sin red, y ese camino fallaría o mostraría canciones no
  descargadas si el álbum solo se bajó parcialmente.
- **Papelera por tarjeta**: `DownloadService.deleteAsync()` directo sobre los tracks de ese álbum
  (sin pasar por `DownloadUtil.justDownload`, que dispara su propio toast async — acá se espera el
  borrado, se refresca la lista y recién ahí se muestra el toast, para evitar una carrera entre
  ambos). El álbum desaparece solo de la lista una vez que su conteo de tracks llega a cero
  (`CacheCleaner` ya lo poda de `albumDao` al borrar el último track).
- Confirmado con el usuario: Downloads sí funciona como una vista de "modo offline" en el sentido
  de que lee siempre la base local — pero el interruptor real de "modo offline" de la app
  (`ActiveServerProvider.isOffline()`, servidor especial "Offline") es independiente y afecta a
  toda la app, no solo a esta pantalla.

Archivos nuevos: `DownloadedAlbumFragment.kt`, `DownloadedAlbumRowBinder.kt`,
`list_item_downloaded_album.xml`.
Archivos modificados: `DownloadsFragment.kt` (reescrito), `TrackCollectionModel.kt`
(`getDownloadedAlbums()`, `getDownloadedAlbumTracks()`, `getDownloadedTracksForAlbum()`),
`navigation_graph.xml` (`downloadedAlbumFragment` + acción `toDownloadedAlbum`), `strings.xml`.

## Se eliminó el selector de tema (claro/oscuro/negro/día-noche)

El usuario notó que el modo claro se sentía "conflictivo" y no le gustaba, y planteó una visión
más amplia: que la app sea *out of the box*, oscura únicamente (como Spotify, que no tiene un
selector de tema), sin nada que configurar aparte del servidor. Se sacó el selector y las 4
variantes de tema, dejando un solo `UltrasonicTheme.Dark` fijo.

- **`themes.xml`**: se borraron `UltrasonicTheme.Light`, `.Black` y `.DayNight`, queda solo
  `.Base` + `.Dark`. Se eliminó `values-night/themes.xml` completo (era solo un override de
  `.DayNight` para modo oscuro del sistema, ya no aplica a nada).
- **`Util.applyTheme()`** ya no lee `Settings.theme` ni rama por variante — aplica
  `UltrasonicTheme_Dark` siempre. Se borró `getStyleFromSettings()`.
- Se eliminó la propiedad `Settings.theme`, el `ListPreference` de "Theme" en `settings.xml`
  (categoría "Appearance", que conserva sus otras opciones), el campo `theme`/su wiring en
  `SettingsFragment.kt`, y el evento `RxBus.themeChangedEvent*` (publisher + observable +
  suscripción en `NavigationActivity` que hacía `recreate()`) — ya no tiene sentido sin un
  selector que lo dispare.
- Limpieza de recursos ahora sin uso: arrays `themeNames`/`themeValues`, claves
  `setting_key.theme*`, strings `settings.theme_title`/`settings.theme_day_night`/etc. en
  `values/` (base). Las traducciones de `settings.theme_title` en otros idiomas (`values-es`,
  `values-de`, etc.) se dejaron intactas a propósito — quedan huérfanas pero inofensivas, no vale
  la pena tocar ~15 archivos de idioma por un string sin uso.
- Se compiló e instaló en el emulador conectado; la app abre sin crash. No se verificó
  manualmente la pantalla de Settings en un dispositivo real (no había Pixel 7 conectado en esta
  sesión) — conviene confirmar que la categoría "Appearance" se ve bien sin la fila de tema.
- **Pendiente, a futuro**: definir una paleta fija tipo Spotify (verde) en vez de depender de
  `Theme.Material3.DynamicColors.Dark` (que saca `colorPrimary` del wallpaper del celular — la
  misma causa del bug del FAB "violeta" arreglado antes). Es un paso aparte, más grande, que
  necesita que el usuario defina colores/referencias primero.

Archivos: `themes.xml`, `Util.kt`, `Settings.kt`, `SettingsFragment.kt`, `settings.xml` (xml/),
`RxBus.kt`, `NavigationActivity.kt`, `arrays.xml`, `setting_keys.xml`, `strings.xml`.
Eliminado: `values-night/themes.xml`.

## Player: ícono de cola explícito (antes era un gesto oculto en la portada)

El usuario reportó que no le quedaba claro cuándo tocar algo lo llevaba a la cola de reproducción
y cuándo al álbum — dos gestos sin ninguna pista visual: tocar la portada alternaba a la cola
(`toggleFullScreenAlbumArt()`, un `ViewFlipper` de dos caras) y tocar el título llevaba al álbum.
Se resolvió sacando el gesto oculto y agregando un ícono explícito de cola en la fila de controles
(junto a shuffle/repeat), como hace Spotify.

- **`media_buttons.xml`**: nuevo `button_queue`, mismo estilo/tamaño que shuffle y repeat
  (`Widget.Material3.Button.IconButton`, 20dp), usando `ic/media_toggle_list.xml` — un ícono de
  lista que ya existía en el proyecto sin usar, aparentemente pensado para esto originalmente.
- **`PlayerFragment.kt`**: se sacó `albumArtImageView.setOnClickListener { toggleFullScreenAlbumArt() }`
  y se movió esa misma llamada al nuevo `button_queue`. El `setOnTouchListener` de la portada
  (gestos de swipe para pista siguiente/anterior) no se tocó — es un mecanismo aparte
  (`GestureDetector`), no el click.
- Tocar el título sigue llevando al álbum sin cambios.

Archivos: `media_buttons.xml`, `PlayerFragment.kt`, `strings.xml`.

## Player: segunda fila de controles, más discreta (estilo Spotify)

Al ver el ícono de cola en la fila principal de controles, el usuario propuso sacarlo de ahí y
ponerlo en una segunda fila más discreta debajo, junto con un ícono de "+" para guardar la cola
actual como playlist nueva — para mantener la fila principal (shuffle/anterior/play/siguiente/
repetir) simétrica, como hace Spotify.

- **`media_buttons.xml`** vuelve a su forma original (sin el ícono de cola).
- **Nuevo `player_secondary_controls.xml`**: fila horizontal aparte, debajo de la principal,
  con dos íconos más chicos (40dp de botón, 18dp de ícono, `colorOnSurfaceVariant` en vez del
  `colorControlNormal` de la fila principal) en los extremos — "+" (`ic_add_white.xml`) a la
  izquierda, cola (`media_toggle_list.xml`) a la derecha, con un espaciador flexible en el medio.
  Mismo margen horizontal (12dp) que la fila principal, para que ambas filas queden alineadas.
- El "+" reutiliza la función de "guardar playlist" que ya existía en el menú de arriba
  (`menu_item_save_playlist` → `showSavePlaylistDialog()`), ahora accesible también desde acá.
- La cola sigue siendo accesible por duplicado desde el menú de arriba (`menu_item_toggle_list`)
  — se deja así a propósito porque el usuario planea sacar esa barra superior más adelante y
  reemplazar la navegación del drawer izquierdo por íconos; cuando eso pase, este botón de cola
  queda como el único camino.

Archivos nuevos: `player_secondary_controls.xml`.
Archivos modificados: `media_buttons.xml`, `current_playing.xml`, `PlayerFragment.kt`.

## Cola de reproducción: sin estrellas, drag handle más discreto

Pedido explícito del usuario ("aún no implementes, pregunta para estar bien alineados"), resuelto
con 3 preguntas antes de tocar código porque el layout de fila es compartido con otras pantallas:

- **Estrellas fuera, solo en la Cola**: `song_star` usaba ya un parámetro existente
  (`TrackViewBinder.showRating`) para ocultarse — se pasó `showRating = false` únicamente en la
  llamada de `initPlaylistDisplay()` (PlayerFragment). Playlists/Género/canciones de artista (que
  comparten el mismo `list_item_track.xml`) no se tocaron, siguen mostrando la estrella igual que
  antes.
- **El rating se movió al menú contextual** (mantener presionado una fila de la Cola,
  `nowplaying_context.xml` — este menú es exclusivo de la Cola, no el compartido
  `context_menu_track.xml`): dos ítems nuevos, "Favorito" (toggle del corazón, mismo mecanismo que
  ya usa la pantalla completa del Player) y "Calificar" (abre el mismo selector de 1 a 5 estrellas
  que antes se abría con long-press sobre la estrella — mismo menú `R.menu.rating`, misma lógica
  de envío, ahora en `PlayerFragment.showRatingPopup()` en vez de duplicarse desde
  `TrackViewHolder`).
- **Ícono de arrastrar (`ic_drag_vertical.xml`) más chico y más tenue**: tinte
  `colorControlNormal` → `colorOnSurfaceVariant` (el mismo tono apagado que ya usan otros
  elementos secundarios), y más padding alrededor en `list_item_track.xml` (7dp/4dp → 11dp/7dp)
  para reducir el tamaño visible del glifo sin achicar el contenedor táctil de 38dp.

Archivos: `PlayerFragment.kt`, `nowplaying_context.xml`, `list_item_track.xml`,
`ic_drag_vertical.xml`, `strings.xml`.

## Bookmarks: decisión de sacarla de la cola de pantallas (sin tocar código todavía)

Al explicarle al usuario qué son los bookmarks de Subsonic (marcador de *posición* dentro de una
canción, para retomar en un segundo exacto — no son favoritos, eso es el corazón/`starred`), decidió
que no le sirve para una app solo de música (sin podcasts/audiolibros, que es donde esto se usa
más). Se sacó de la lista de pantallas pendientes de rediseño visual en `HANDOFF.md`. No se tocó
código — es una decisión registrada, no una instrucción de eliminar la pantalla (a diferencia de
los temas, donde sí pidió sacarlos explícitamente). Si se retoma, confirmar si el pedido es
ocultarla del drawer (como Shares) o eliminarla del todo.

También quedó anotada una idea relacionada, sin desarrollar: que la cola de reproducción persista
entre sesiones (volver a la misma cola al reabrir la app), en vez de bookmarks por canción.

## Search: filtrado en tiempo real con debounce (reduce carga al servidor)

Cada búsqueda le pega al servidor (`search2`/`search3`), no filtra una lista ya cargada — así que
"tiempo real" real (una request por tecla) saturaría al servidor. Se implementó con debounce:

- **`NavigationActivity.setupSearchField()`**: nuevo `SearchView.OnQueryTextListener`.
  `onQueryTextSubmit` no cambia (sigue yendo por el intent `ACTION_SEARCH` de siempre, que también
  guarda la query en las sugerencias recientes). `onQueryTextChange` dispara `scheduleLiveSearch()`.
- **`scheduleLiveSearch()`**: cancela cualquier búsqueda en espera, ignora queries de menos de 2
  caracteres (evita pedidos demasiado amplios/costosos) y las que se repiten sin cambios, espera
  400ms de pausa en el tipeo, y recién ahí navega a `searchFragment` con la query nueva. Al limpiar
  el campo se resetea la última query recordada, para que buscar lo mismo en una visita nueva no
  quede bloqueado por el dedupe de una visita anterior.
- **Navegación con `NavOptions` (`setLaunchSingleTop` + `setPopUpTo(searchFragment, inclusive=true)`)**:
  sin esto, cada tecla habría agregado una entrada nueva al back stack — con esto, refinar la
  búsqueda mientras ya estás viendo resultados reemplaza la entrada existente en vez de apilar una
  por letra.
- **`SearchFragment.search()`**: se agregó cancelación del `Job` anterior antes de lanzar uno
  nuevo (mismo patrón `loadJob?.cancel()` ya usado en otras pantallas esta sesión) — sin esto, una
  respuesta lenta de una query vieja podría llegar después y pisar los resultados de una query más
  nueva.

Archivos: `NavigationActivity.kt`, `SearchFragment.kt`.

## Search: se sacó la estrella de rating de las filas de canción

Mismo criterio que ya se aplicó en la Cola: `showRating = false` en el `TrackViewBinder` de
`SearchFragment` (único call site afectado, Playlists/Género/canciones de artista siguen
mostrando la estrella igual que antes). A diferencia de la Cola, acá no se agregó un reemplazo
en el menú contextual — el usuario pidió sacarla sin más, y el menú de long-press de Search
(`context_menu_track.xml`) no tenía rating para empezar.

Archivo: `SearchFragment.kt`.

## Settings: se sacó la categoría "Sharing" (código muerto)

Primer paso del pase de Settings, confirmado con el usuario (alcance: visual + limpieza de
contenido). La categoría "Sharing" (saludo/descripción/expiración por defecto de shares) no tiene
forma de usarse desde que se sacó Share de toda la app esta sesión — no queda ningún botón que
dispare ese flujo. Se sacó del `PreferenceScreen`, mismo criterio que el resto de código
relacionado a Share: se oculta, no se borra (`Settings.kt`, `ShareHandler.kt`, `SharesFragment.kt`
siguen intactos, solo inalcanzables).

Archivo: `settings.xml` (xml/).

Pendiente: el resto del pase visual (restyle de las 8 categorías restantes) necesita ver cómo se
ve hoy en pantalla real primero — a diferencia de las demás pantallas (RecyclerView/MultiType
propio), Settings usa `PreferenceScreen` de AndroidX, que tiene su propio sistema de estilos
(`?attr/preferenceTheme`) y no se puede restylear con la misma libertad que el resto de la app sin
un trabajo de ViewHolder/adapter a medida.

## Settings: checkboxes → switches

Primer feedback sobre la captura real: las 20 `CheckBoxPreference` de `settings.xml` pasaron a
`SwitchPreferenceCompat` (mismo widget, look de switch de Material en vez de casilla cuadrada).
`SettingsFragment.kt` tenía 4 referencias tipadas (`showArtistPicture`, `useId3TagsOffline`,
`debugLogToFile`, `customCacheLocation`) que solo usan `.isChecked`/`.isEnabled`/`.summary` — se
retipearon a `SwitchPreferenceCompat` sin tocar la lógica.

Archivos: `settings.xml` (xml/), `SettingsFragment.kt`.

## Settings: categoría "Appearance" eliminada (fijada a valores fijos)

Segundo feedback sobre la captura real: el usuario propuso la visión de que Settings solo debería
tener cosas verdaderamente técnicas — el resto se fija a un valor razonable y se saca de la
pantalla, coherente con la idea de app "out of the box" (nada que configurar salvo el servidor).
Se revisó cada opción de "Appearance" antes de tocar nada, porque una ("Use Folders For Artist
Name") no es gusto visual sino compatibilidad con la estructura de la librería del servidor — se
confirmó con el usuario antes de fijarla.

- **`Settings.kt`**: `shouldDisplayBitrateWithArtist`, `shouldUseFolderForArtistName`,
  `shouldShowTrackNumber` y `serverScaling` pasaron de `var ... by BooleanSetting(...)` a
  `const val` con el valor que ya tenían de comportamiento real (no necesariamente el default del
  delegate, que en algunos casos no coincidía con el default declarado en el XML — se usó el
  valor que realmente se veía en la captura). `shouldSortByDisc` se fijó en `true` (antes `false`
  por defecto) — cambio de comportamiento real, confirmado explícitamente: ordenar siempre por
  disco es más correcto para álbumes múltiples y no tiene efecto en álbumes de un solo disco.
- **`showNowPlayingDetails` se eliminó por completo** (no solo se fijó) — ya era código muerto,
  nada lo lee desde que esos campos se sacaron de la pantalla del Player en una pasada anterior.
- La categoría "Appearance" quedó vacía (sus 5 opciones se fueron) y se borró del todo del
  `PreferenceScreen`; el switch muerto de "Show details in Now Playing" también se sacó de
  "Playback Control Settings".
- Limpieza de recursos: claves (`setting_key.*`) y strings de título/resumen de las 6 opciones
  eliminadas, más `settings.appearance_title` (sin uso ya que la categoría desapareció).

Archivos: `Settings.kt`, `settings.xml` (xml/), `strings.xml`, `setting_keys.xml`.

## Settings: segunda ronda — "esto no es una app para geeks que configuran servidores"

El usuario planteó la pregunta que terminó de definir el alcance real de todo este trabajo: la
app es para la persona que va a *escuchar* música (su papá, su mamá, primas de 10 años), no para
quien arma el servidor. Cosas como "resume al conectar audífonos" ya son comportamiento estándar
en cualquier reproductor moderno — no deberían ser una decisión que alguien tiene que descubrir y
activar. Se armó una lista de candidatos entre las categorías restantes y se confirmó sacar todo.

- **Auto-resume/pausa por audífonos y bluetooth**: `resumePlayOnHeadphonePlug` fijo en `true`.
  El resume/pause por bluetooth (antes configurable entre "cualquier dispositivo" / "solo A2DP" /
  "desactivado", con valores por defecto asimétricos) se fijó a **solo A2DP** para ambos —
  resumir/pausar únicamente con dispositivos de audio reales (auriculares, parlantes, el auto),
  no con cualquier periférico bluetooth emparejado. `BluetoothIntentReceiver.kt` se simplificó:
  los `when` que leían `Settings.resumeOnBluetoothDevice`/`pauseOnBluetoothDevice` y manejaban 3
  niveles se redujeron a dos booleans directos comparando el estado A2DP. Se sacó también todo el
  diálogo de selección custom en `SettingsFragment.kt` (`setupBluetoothDevicePreferences`,
  `showBluetoothDevicePreferenceDialog`, `bluetoothDevicePreferenceToString`) y las constantes
  `Constants.PREFERENCE_VALUE_ALL/A2DP/DISABLED`, ya sin uso.
- **Notificación de reproducción siempre visible**: `showNowPlaying` fijo en `true` — nadie
  apagaría la notificación de qué está sonando. Categoría "Notifications" (que solo tenía esta
  opción) eliminada del todo.
- **Chat Refresh Interval sacado**: no es que se haya vuelto irrelevante, ya era código
  **inalcanzable** desde antes de esta sesión — el ítem del drawer para Chat no existe en
  `navigation_drawer.xml`, así que nadie puede llegar nunca a esa pantalla para que este valor
  importe. `chatRefreshInterval` queda como `const val` fijo solo para que `ChatFragment.kt`
  (código muerto, no borrado, mismo criterio que Podcasts/Video) siga compilando si algún día se
  reactiva.
- **Network Timeout sacado — resultó ser un ajuste completamente muerto**: se investigó a fondo
  antes de tocarlo y no hay ni una sola línea de código en toda la app (ni en `core/subsonic-api`)
  que lea esta preferencia. Estaba en la UI, se guardaba en SharedPreferences, pero nunca hizo
  nada. Directamente eliminado, no había ningún valor de comportamiento que preservar.
- **Ajustes de tuning interno fijados a su valor por defecto de siempre** (nadie salvo un
  desarrollador los tocaría): `preloadCount=3`, `parallelDownloads=3`, `directoryCacheTime=300`.
- **Las 6 de "Search" fijadas a sus defaults** (`defaultArtists=3`, `maxArtists=10`,
  `defaultAlbums=5`, `maxAlbums=20`, `defaultSongs=10`, `maxSongs=25`) — con un hallazgo
  importante: `maxAlbums` y `maxSongs` no son solo el tope de resultados de Search, también
  funcionan como tamaño de página por defecto en Browse, "Reproducir canciones al azar", creación
  de playlists y cualquier pantalla de colección de canciones que no especifique un tamaño
  explícito. Se usaron los valores que ya eran el default en todos esos lugares, así que ningún
  comportamiento cambia en ninguno de ellos.
- **`seekInterval` (salto al adelantar/retroceder) fijo en 5000ms** — se preservaron ambas
  unidades reales en juego: `PlaybackService`/`JukeboxMediaPlayer` usan el valor crudo en ms para
  reproducción local, pero `seekIntervalMillis` (que a pesar del nombre son segundos, no ms —
  bug de nomenclatura preexistente, no corregido, solo documentado) sigue siendo la unidad correcta
  para el modo Jukebox, que trackea posición en segundos del lado del servidor.
- **`shouldShowArtistPicture` fijo en `true`** — era cosmético, no técnico, aunque vivía en la
  categoría "Playback Control".
- **Scrobble siempre activo** (`scrobbleEnabled = true`): confirmado con el usuario — Subsonic no
  tiene una bandera de capacidad por servidor para esto (a diferencia de chat/bookmarks/shares),
  así que no hay nada que autodetectar. Ahora siempre se intenta; si el servidor no tiene un
  destino de scrobble configurado (sin Last.fm vinculado, por ejemplo), el llamado no hace nada.
- **"Hide media from other apps" sacado por completo**: el usuario fue explícito — no quiere
  esconder su música de otras apps, quiere ser un reproductor completo, no un cliente de servidor
  paranoico por derechos de autor. A diferencia de las demás, esta ni siquiera tenía una
  propiedad en `Settings.kt` — vivía solo como un caso reactivo en
  `SettingsFragment.onSharedPreferenceChanged` (creaba/borraba una subcarpeta `.nomedia` dentro
  de `ultrasonicDirectory`) sin aplicación al iniciar la app. Se borró el caso del listener y
  `setHideMedia()` entero; lo que haya en disco de instalaciones previas queda como estaba, no se
  fuerza nada al arrancar.
- **Categorías resultantes**: "Appearance" y "Notifications" desaparecieron del todo. "Search"
  quedó con un solo ítem (Clear Search History). El resto de categorías (Playback Control,
  Network, Music Cache, Other, Debug) perdieron las filas mencionadas arriba pero mantienen
  contenido genuinamente técnico o de elección real (bitrate máximo, ID3 tags, ReplayGain,
  hardware offload, idioma, etc.) — no tocado, sigue siendo configuración legítima.
- Limpieza extensa de recursos: claves (`setting_keys.xml`), strings de título/resumen/valores
  numerados, y los arrays completos que ya no tenía sentido mantener (`incrementTime*`,
  `chatRefresh*`, `networkTimeout*`, `preloadCount*`, `directoryCacheTime*`, `search*`,
  `bluetoothDeviceSettingNames`).
- **Pendiente, mencionado pero no implementado**: traducir "Max Bitrate" (números crudos en Kbps)
  a algo tipo "Calidad: Baja/Media/Alta" — el usuario lo propuso pero es un rediseño en sí mismo,
  no un simple sacar-y-fijar, se deja para una pasada aparte si se retoma.

Archivos: `Settings.kt`, `settings.xml` (xml/), `SettingsFragment.kt`,
`BluetoothIntentReceiver.kt`, `Constants.kt`, `strings.xml`, `setting_keys.xml`, `arrays.xml`.
