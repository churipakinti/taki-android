# Changes

## Optimización interna: Fase 0 — instrumentación de línea base

Primer paso del plan de optimización previo a la beta (`TAKI_CODE_OPTIMIZATION_PLAN.md`). Antes
de tocar ningún camino crítico, se verificaron contra el código actual los seis hallazgos de
arquitectura que el propio plan listaba como "deben confirmarse nuevamente" — los seis se
confirmaron exactos (`SubsonicAPIDefinition` con 49/56 funciones sin `suspend`, `RESTMusicService`
con 56 usos de `.execute()`, ninguna negociación centralizada de capacidades OpenSubsonic —solo
`runCatching { }.getOrNull()` sueltos en `LyricsFragment`/`ArtistDetailModel`—, `CachedMusicService`
mezclando Room/LRU/TTL, el TODO real de `MusicServiceFactory` sobre el costo de
`unloadKoinModules`/`loadKoinModules`, y `getIndexes(..., ifModifiedSince = null)` sin usar la
sincronización incremental que la propia API ya soporta).

**`PerfMetrics`/`PerfMetricsInterceptor`** (nuevos, `util/`): instrumentación de línea base —
marcas y tramos con nombre logueados vía Timber (`mark`/`start`/`end`/`trace`), y un interceptor
de OkHttp que loguea método, *path* (nunca la URL completa, que lleva `u`/`s`/`t` en la query),
duración y tamaño de cada llamada al API de Subsonic. No se agregó ningún flag propio de
"¿está habilitado?": Timber solo planta un árbol (`DebugTree`) en builds debug
(`UApp.onCreate`), así que cualquier `Timber.d(...)` — incluido todo lo que loguea `PerfMetrics` —
ya es un no-op silencioso en `release` sin necesidad de duplicar esa condición. El interceptor de
red se conecta a través del parámetro `baseOkClient` que `SubsonicAPIClient` ya exponía para esto
(sin tocar el módulo `core/subsonic-api`), registrado en Koin con un calificador `named(...)` para
no colisionar con el `OkHttpClient` sin nombre que ya provee `baseNetworkModule`.

Puntos instrumentados: arranque (`NavigationActivity.onCreate`), Home (`HomeViewModel.
loadHomeScreen()` — cubre "con/sin caché" de forma implícita, ya que pasa por
`CachedMusicService`), apertura de listas/álbum/artista/género/playlist (un solo punto en
`MultiListFragment.onViewCreated()`, compartido por todas esas pantallas, marcando la primera
emisión del `LiveData` — no cada refresh), toque→reproducción (`TrackCollectionFragment.
playFromHere()` hasta `MediaPlayerManager.onIsPlayingChanged(true)`), búsqueda
(`SearchFragment.search()`), y cambio de biblioteca/offline (`MusicServiceFactory.
resetMusicService()`). El modo offline explícito no necesitó instrumentación propia: al no hacer
ninguna llamada de red, los mismos marcadores de Home/listas ya muestran su latencia casi nula.

**Verificación**: `ktlintCheck` en verde, `testDebugUnitTest` en verde (incluye
`PerfMetricsTest.kt` nuevo, con Robolectric por la dependencia de `SystemClock`),
`lintRelease` estable en 22 errores (sin cambios), detekt estable en 42 hallazgos — el único
resultado que menciona `PerfMetrics` es el hallazgo ya existente de complejidad de
`NavigationActivity.onCreate`, que ahora cita una línea más en su fragmento de código, no un
hallazgo nuevo. `assembleDebug`, `assembleRelease` y `bundleRelease` compilan sin cambios de
comportamiento en release. **Pendiente**: correr en dispositivo real y recolectar los primeros
p50/p95 de los escenarios mínimos que pide el plan (LAN, remoto, conexión lenta, servidor
apagado, offline explícito, biblioteca chica/grande, canción descargada/solo remota).

Archivos: `ultrasonic/src/main/kotlin/org/moire/ultrasonic/util/PerfMetrics.kt` (nuevo),
`PerfMetricsInterceptor.kt` (nuevo), `di/MusicServiceModule.kt`,
`activity/NavigationActivity.kt`, `model/HomeViewModel.kt`, `fragment/MultiListFragment.kt`,
`fragment/TrackCollectionFragment.kt`, `service/MediaPlayerManager.kt`,
`fragment/SearchFragment.kt`, `service/MusicServiceFactory.kt`,
`src/test/kotlin/org/moire/ultrasonic/util/PerfMetricsTest.kt` (nuevo).

## Optimización interna: Fase 0, continuación — primera medición real en emulador y en el Pixel 7

Primera corrida real de la instrumentación de Fase 0, con hallazgos concretos y una corrección
ya aplicada. Todo verificado con evidencia de logcat, no por inspección de código.

**Hallazgo 1 (aplicado): las carátulas/avatares compartían el pool de conexiones con streaming y
las llamadas de API.** En el emulador, la carga inicial de Home mostró ~5 llamadas
`getAlbumList2.view` en paralelo más hasta una docena de `getCoverArt.view` compitiendo todas por
el límite por defecto de OkHttp de 5 conexiones concurrentes por host — exactamente lo que la
Fase 1 del plan advertía ("Limitar... precargas de carátulas... si compiten con el audio"), solo
que ya estaba pasando incluso antes de tocar audio. **Fix**: `CoverArtFetcher`, `AvatarFetcher` y
`ImageLoaderProvider` ahora inyectan un `SubsonicAPIClient` con calificador Koin
`"ImageSubsonicAPIClient"`, construido con su propio `Dispatcher`/`ConnectionPool` (`di/
MusicServiceModule.kt`) — mismo patrón de aislamiento que ya se usaba para el interceptor de
métricas, sin tocar `core/subsonic-api`. Las pruebas `CoverArtFetcherTest`/`AvatarFetcherTest` se
actualizaron para registrar el mock bajo el mismo calificador.

**Hallazgo 2 (medido, no confirma la Hipótesis 1 como causa dominante): aislar las carátulas no
redujo el tiempo total de `home_load`.** Contra lo esperado, `loadHomeScreen()` (que ni siquiera
espera a las carátulas, solo a los datos) seguía tardando ~5,4-5,8s en el emulador antes y después
del aislamiento. Esto llevó a agregar `PerfMetricsEventListener.kt` (nuevo) — un `EventListener`
de OkHttp que separa DNS/conexión TCP/TLS del resto de cada llamada — para descartar la red como
causa.

**Hallazgo 3: el servidor de prueba se alcanza por Tailscale (100.64.0.0/10), pero eso no es el
problema.** El `tcp_connect` real sobre Tailscale tomó 6-232ms según la corrida — razonable, no es
el cuello de botella. El verdadero hueco (varios segundos sin ninguna actividad de red) estaba
del lado de la app, no de la red.

**Hallazgo 4: resolución fría de Koin (494ms en emulador) y creación de `OkHttpClient` de
`PlaybackService` (443ms, coincide con una violación real de StrictMode `newSSLContext` ya
documentada como hallazgo pendiente).** Ambos confirmados agregando marcas puntuales
(`PerfMetrics.start`/`end`) en `MusicServiceFactory.getMusicService()` (solo la primera resolución)
y `PlaybackService.initializeSessionAndPlayer()`.

**Hallazgo 5 (el más importante): gran parte del tiempo "perdido" en el emulador era contención de
CPU del host, no lentitud real de la app.** Repitiendo exactamente la misma prueba en un Pixel 7
físico conectado por USB (que no comparte CPU con la máquina donde corrían los builds de Gradle en
paralelo), los mismos tres números bajaron drásticamente: `home_load` 5,4-5,8s → **3,36s**;
resolución de Koin 494ms → **128ms**; init de `PlaybackService` 443ms → **117ms**. Conclusión
metodológica para el resto del plan: **el emulador sirve para diagnosticar causas (qué código
está involucrado), pero los números absolutos y el criterio de aceptación deben venir siempre del
dispositivo físico**, tal como ya establecía `docs/POSIBLES_ERRORES_Y_VERIFICACION.md` para otros
cambios de reproducción.

**Hallazgo 6 (no es un bug nuevo): `LeakedClosableViolation` de `AbstractCursor`/
`CursorWrapperInner` en el Pixel 7.** Mismo `CloseGuardException` ya investigado y cerrado como
"bug histórico #7" en una sesión anterior de este mismo archivo. El stack trace confirma de nuevo
que viene de `AsyncQueryHandler`/`ContentResolver.query` internos de Android (probablemente
disparado por `SearchRecentSuggestions`), no de código de Taki. No se reabre sin una pista nueva.

**Hallazgo 7 (medido, queda documentado para la Fase 1 formal, no se tocó código de reproducción
todavía): la restauración de sesión resuelve la URL de streaming de varias canciones de la cola de
forma secuencial, no en paralelo.** Cada resolución individual (`ResolvingDataSource.Resolver` en
`PlaybackService.kt`, instrumentada con `PerfMetrics.start("stream_url_resolve:N")`) es rápida
(15-46ms), pero quedan huecos de 300-600ms sin actividad entre una y la siguiente — Media3 las
prepara una por una como parte de su propio pipeline interno al llamar `prepare()` inmediatamente
después de restaurar la cola en `MediaPlayerManager.restore()`, aunque `autoPlay` sea `false` y el
usuario no haya tocado play. Estas resoluciones comparten el pool principal (no el aislado de
imágenes), así que compiten con las llamadas de Home por las mismas conexiones. Candidato concreto
para la Fase 1: evaluar si `restore()` necesita preparar más de la canción actual antes de que el
usuario pida reproducir. No implementado en esta sesión, a pedido explícito de no mezclar
diagnóstico con cambios de comportamiento de reproducción.

Archivos nuevos: `ultrasonic/src/main/kotlin/org/moire/ultrasonic/di/` (sin archivo nuevo, solo
`MusicServiceModule.kt` editado), `util/PerfMetricsEventListener.kt`. Editados:
`di/MusicServiceModule.kt`, `imageloader/CoverArtFetcher.kt`, `imageloader/AvatarFetcher.kt`,
`subsonic/ImageLoaderProvider.kt`, `service/PlaybackService.kt`, `service/MusicServiceFactory.kt`,
`service/MediaPlayerManager.kt`, `test/kotlin/.../imageloader/CoverArtFetcherTest.kt`,
`AvatarFetcherTest.kt`.

## Optimización interna: Fase 0, cierre — resto de la matriz de escenarios medida en el Pixel 7

Continuación real (no emulador) de la línea base, interactuando con la app de verdad en el
Pixel 7 conectado: abrir un álbum, reproducir una canción, buscar, y cambiar de biblioteca
(remota → Offline). Todos los números vienen de `logcat`, no de estimación.

- **Toque → reproducción real: 538ms** (`play_tap` hasta `playback_started`, álbum "Homework").
  Dentro del objetivo inicial del plan ("Inicio de audio por LAN < 1 s").
- **Búsqueda remota: 325ms** (`search3.view` completo, sobre Tailscale). Cerca del objetivo de
  "resultados locales < 300ms" a pesar de ser una búsqueda de red, no de índice local.
- **Cambio de biblioteca remota → Offline: 1ms.** El `unloadKoinModules`/`loadKoinModules` de
  `MusicServiceFactory.resetMusicService()` es instantáneo una vez que las clases ya están
  cargadas en el proceso (el costo real está en la primera resolución fría, ya medido antes en
  494ms/128ms según emulador/dispositivo, no en cada cambio posterior).
  **Home en modo Offline: 132ms, cero llamadas de red** — confirma exactamente el criterio de
  aceptación del plan ("Entrada al modo offline explícito: sin espera de red") ya se cumple hoy,
  sin necesidad de ningún cambio.

**Lo que sigue sin medir de la matriz completa del plan**: LAN real (solo se probó por
Tailscale), conexión lenta/con latencia simulada, servidor apagado, comparación deliberada de
biblioteca chica vs. grande, canción descargada vs. solo remota, aciertos/fallos de caché,
solicitudes idénticas simultáneas y solicitudes canceladas -- estas últimas tres ni siquiera
están instrumentadas todavía. Sin cambios de código en esta sesión de medición, solo evidencia
nueva agregada a este archivo.

## Preparación de beta: 1.516 traducciones huérfanas bloqueaban assembleRelease/bundleRelease

`lintVitalRelease` (el subconjunto de checks fatales que corre automáticamente al armar la
variante `release`) fallaba con 1.516 errores `ExtraTranslation`, abortando `assembleRelease` y
`bundleRelease` sin generar ningún artefacto. La causa: las sucesivas limpiezas de Settings y
otras pantallas (ver puntos 18/20/21 de `HANDOFF.md`) borraron decenas de strings del locale por
defecto (`values/strings.xml`) sin borrar sus traducciones correspondientes en los 16 idiomas
restantes — cada `values-XX/strings.xml` quedó con entre 71 y 100 strings huérfanas (traducidas
pero inexistentes en el idioma por defecto).

**Fix**: se generó el reporte de lint (`ultrasonic/build/reports/lint-results-release.xml`), se
extrajo el nombre exacto de cada string huérfana por archivo desde el propio reporte, y se
eliminó únicamente esa línea de cada `values-XX/strings.xml` (1.516 líneas en total, 0 líneas
agregadas). Se validó que los 16 archivos siguen siendo XML válido después del cambio. No se tocó
ninguna traducción vigente ni el locale por defecto.

**Verificación**: `lintRelease`, `assembleRelease` y `bundleRelease` se relanzaron después del fix
para confirmar que ExtraTranslation deja de bloquear la compilación de `release` (ver resultado en
la conversación de preparación de la beta). El recuento total de lint (`ultrasonic:lintRelease`,
1.715 errores antes del fix) también debería bajar en la misma medida — pendiente confirmar el
número final e investigar los ~200 hallazgos restantes (mayormente `UnusedResources`) por separado.

Archivos: `ultrasonic/src/main/res/values-{cs,de,es,fr,gl,hu,it,ja,nb-rNO,nl,pl,pt,pt-rBR,ru,zh-rCN,zh-rTW}/strings.xml`.

## Preparación de beta: formato (ktlint) y recursos sin usar (lint UnusedResources)

Continuación de la limpieza de lint iniciada con las traducciones huérfanas.

**ktlintFormat**: se corrigieron automáticamente 154 violaciones de estilo puro (wrapping de
argumentos, orden de imports, espacios, indentación) repartidas en 42 archivos, la mayoría ya
commiteados de sesiones anteriores. `ktlintFormat` solo reescribe lo que puede corregir de forma
determinista dentro del mismo archivo; **no** toca las 4 violaciones que requieren una decisión
humana (nombres de propiedad a `SCREAMING_SNAKE_CASE` en `Settings.kt` y una condición mixta
`&&`/`||` en `Util.kt`), que quedan documentadas como pendientes, no arregladas.

**UnusedResources**: 36 strings + 4 colores + 20 archivos de recurso completo (drawables/menú/
layout) confirmados sin ninguna referencia en `ultrasonic/src` ni `core/*` (verificado con una
búsqueda propia en todo el árbol de código, además del propio detector de lint, antes de borrar
nada). Se dejaron sin tocar 16 casos donde la búsqueda sí encontró una referencia (probablemente
usados solo desde otros drawables tipo layer-list, ej. `thumb.xml`/`rating_star_full.xml`), para
revisión manual aparte. La mayoría corresponde a íconos/strings de Bookmarks, Chat, Podcasts, Share
y del antiguo drawer lateral — funcionalidades ocultas de la UI pero cuyo código Kotlin se conserva
a propósito (ver `HANDOFF.md`); estos recursos concretos ya no tienen ninguna referencia ni siquiera
desde ese código conservado, así que su eliminación no debería afectarlo.

**Falso positivo real encontrado y revertido**: `menu/search_view_menu.xml` fue borrado por el
mismo criterio (lint decía que el *recurso menú* nunca se infla) y rompió la compilación de
`release`: `ArtistDetailFragment.setupToolbarBehavior()` sigue llamando
`menu.findItem(R.id.action_search)?.isVisible = false` — una referencia directa al *ID* declarado
dentro de ese menú, un tipo de recurso distinto que la detección de "menú sin usar" no cubre. Se
restauró el archivo desde git. La línea en `ArtistDetailFragment.kt` es código muerto inofensivo
(usa `?.`, no crashea si el ítem no existe, mismo patrón ya identificado como vestigio de la
migración de toolbar en el bug ya documentado de `TrackCollectionFragment`), pero limpiarla es un
cambio de lógica de aplicación fuera del alcance de esta limpieza de lint — queda documentado como
posible follow-up, no se tocó.

**Segunda cascada de traducciones huérfanas**: borrar esas 36 strings del locale por defecto dejó
huérfanas sus propias traducciones en los 16 idiomas (496 líneas más, incluyendo `common.appname`,
que ya no se usa desde que el punto 39 de `HANDOFF.md` movió las etiquetas visibles a
`taki.appname`). Se aplicó el mismo script de limpieza una segunda vez contra el reporte de lint
actualizado hasta converger. Total acumulado: 2.012 líneas de traducción eliminadas en dos pasadas,
0 agregadas, todos los archivos siguen siendo XML válido.

**Resultado final verificado**: `ultrasonic:lintRelease` bajó de 1.715 → 41 errores reales
(desglose: 20 `UnusedResources` restantes de revisión manual, y 21 hallazgos menores de calidad/
accesibilidad ya clasificados). `assembleRelease` y `bundleRelease` compilan en verde de forma
estable. `testDebugUnitTest` pasa. ktlint/detekt conservan exactamente los mismos 4 y 36 hallazgos
pendientes de decisión humana identificados antes de esta limpieza (nomenclatura de propiedades en
`Settings.kt`, condición mixta en `Util.kt`, y deuda de complejidad/código muerto en detekt) — nada
nuevo se rompió.

## Preparación de beta: cierre de pendientes de lint/ktlint/detekt, y Android Studio se auto-actualizó a mitad de sesión

**16 recursos adicionales confirmados sin uso real** (mi primera verificación cruzada había sido
demasiado cautelosa: buscaba también dentro de `core/*/build`, 94 archivos XML generados/cacheados
que no son código fuente, lo que producía falsos positivos). Corregida la búsqueda contra el árbol
de fuente real, 14 drawables más un par (`thumb.xml`/`thumb_drawable.xml`, huérfano desde que el
bug J los reemplazó por `rounded_swatch_fill.xml`) resultaron sin ninguna referencia real, solo
mencionados en comentarios de código que documentan bugs ya arreglados. Además, 3 strings
(`button_bar.browse`, `menu.common`, `menu.exit`) sin uso en código pero presentes en los 16
idiomas se borraron de todos los archivos a la vez para no repetir la cascada de traducciones
huérfanas.

**Las 20 propiedades `const val` de `Settings.kt` que ktlint pedía en `SCREAMING_SNAKE_CASE`**
(no 3, como se dijo por error al principio — la salida truncada de `ktlintFormat` solo mostraba una
muestra) se renombraron con sus ~40 call sites en todo el árbol de código. Dos usos se escaparon
del primer barrido automático porque `AlbumHeader.kt` y `SelectGenreFragment.kt` importan la
propiedad directamente (`import ...Settings.NOMBRE`) y la usan sin el prefijo `Settings.` — el
propio compilador los encontró al recompilar, se corrigieron. `ktlintCheck` queda en verde.

**`Util.kt`**: se agregó un paréntesis aclaratorio (sin cambiar el resultado — `&&` ya tiene más
precedencia que `||` en Kotlin) al único caso de `mixed-condition-operators` restante, una
expresión de igualdad null-safe ya marcada `@Suppress("SuspiciousEqualsCombination")` de antes.

**Dos restos de código muerto reales, confirmados y eliminados** (el tercer candidato de detekt,
`requestKey` en `TrackCollectionFragment.handleFilterSelectionResult()`, es un falso positivo: la
firma la exige `FragmentManager.setFragmentResultListener`, no se puede quitar sin romper la
referencia de método):
- `NavigationActivity.exit()` — sin ningún llamador; el único punto de entrada posible
  (`menu_exit` del drawer lateral) ya no existe desde que se borró `navigation_drawer.xml`.
- El parámetro `activeServerProvider` del constructor de `ServerRowAdapter` — se guardaba pero
  nunca se leía (la clase usa los miembros estáticos `ActiveServerProvider.OFFLINE_DB_ID`/
  `getActiveServerId()`, no la instancia). Se quitó del constructor y de su único punto de
  construcción en `ServerSelectorFragment.kt`.

**Hallazgo de infraestructura, no de código**: a mitad de esta sesión, Android Studio se
auto-actualizó (`AndroidStudio2026.1.1` → `2026.1.3` en `%LOCALAPPDATA%\Google\`), y con eso la JBR
en `C:\Program Files\Android\Android Studio\jbr` pasó de Java 21 a **Java 25**, rompiendo el
`jvmToolchain(21)` del proyecto sin ningún cambio de código de por medio. Confirmado con el
usuario, se migró el proyecto a JDK 25 en vez de esperar una instalación nueva de JDK 21:
`ultrasonic/build.gradle` (`languageVersion.set(JavaLanguageVersion.of(25))`) y
`core/domain/build.gradle` (`sourceCompatibility`/`targetCompatibility` a `VERSION_25`).
`assembleRelease`/`bundleRelease`/tests compilan limpio bajo JDK 25. **Detekt no podía correr bajo
host JDK 25 con la versión del plugin que usaba este proyecto** (`1.23.8`, `--jvm-target` solo
acepta hasta 22, y el proceso de detekt fallaba igual aunque `jvmTarget` se dejara en 21 — un techo
real de esa versión, no configurable).

**Detekt actualizado a `2.0.0-alpha.6`** (única versión probada contra JDK 25, según la propia
documentación del proyecto). El artefacto y el ID del plugin cambiaron de grupo Maven en la
versión 2.0 (`io.gitlab.arturbosch.detekt` → `dev.detekt`):
`gradle/libs.versions.toml` (versión + `module = "dev.detekt:detekt-gradle-plugin"`) y
`gradle_scripts/code_quality.gradle` (ambos `apply plugin`/`hasPlugin` con el ID nuevo). Es una
versión **alpha**, elegida deliberadamente por ser la única con soporte de JDK 25 documentado;
puede tener comportamiento distinto a una versión estable en el futuro.

El esquema de `config/detekt/detekt.yml` cambió con la 2.0 y detekt rechazó el archivo con
"propiedad mal escrita o no existe" hasta corregir cuatro cosas puntuales:
- Se quitó el bloque `build: maxIssues/weights` (la clave `build` ya no existe como nivel superior
  en 2.0; `maxIssues: 0` era igual al comportamiento por defecto — cualquier hallazgo activo hace
  fallar la tarea — así que quitarlo no cambia nada).
- `TooManyFunctions.thresholdInFiles/InClasses/InInterfaces/InObjects` → renombradas a
  `allowedFunctionsPerFile/PerClass/PerInterface/PerObject`, mismos valores (confirmado que
  `RESTMusicService` con 58 funciones sigue marcado sobre el límite de 25 tras el rename).
  `ForbiddenImport.imports` → `forbiddenImports`, mismo valor.
- `style.UnnecessaryAbstractClass` (ya estaba `active: false`) se quitó en vez de adivinar su
  nombre nuevo — no hay un reemplazo obvio en la lista de propiedades permitidas y, al estar
  inactiva, no cambia ningún comportamiento quitarla.

**Resultado**: detekt corre limpio bajo JDK 25 (sin el crash del host). El nuevo analizador
encuentra 41 issues en `ultrasonic` + 1 nuevo en `core/subsonic-api` (0 antes) — la cifra sube
respecto a los 34 esperados con la versión vieja porque el ruleset por defecto de 2.0 difiere del
de 1.23.8; es un efecto normal de actualizar una herramienta de análisis estático, no una
regresión de este proyecto. `ktlintCheck`, `testDebugUnitTest`, `assembleRelease` y `bundleRelease`
se re-verificaron después del upgrade y siguen en verde; `lintRelease` se mantiene estable en 22
errores.

**`settings.gradle` ahora declara `org.gradle.toolchains.foojay-resolver-convention`** — agregado
automáticamente por Android Studio/Gradle durante el episodio del JDK 25 de esta sesión (es
literalmente la solución que Gradle sugiere en el propio mensaje de error de toolchain). Permite
que Gradle descargue automáticamente un JDK compatible cuando el configurado en la máquina no
coincide con el que pide el proyecto, en vez de fallar directamente — reduce el riesgo de que un
futuro cambio de entorno (otra actualización de Android Studio, por ejemplo) vuelva a bloquear la
compilación por completo. Se mantiene deliberadamente en el commit por ese motivo, aunque no fue un
cambio pedido explícitamente.

**Resultado final de esta sesión**: `ultrasonic:lintRelease` 1.715 → **22 errores** reales
(desglose por tipo en la conversación de preparación de la beta, ya no queda ninguna categoría de
"borrado masivo", son hallazgos individuales de calidad/accesibilidad). `ktlintCheck` en verde.
`testDebugUnitTest`, `assembleRelease` y `bundleRelease` compilan y pasan de forma estable bajo
JDK 25. Detekt sigue con hallazgos reales pendientes de decisión, pero no se puede re-ejecutar en
este entorno hasta resolver el techo de versión del plugin frente a JDK 25.

Archivos adicionales: `NavigationActivity.kt`, `ServerRowAdapter.kt`, `ServerSelectorFragment.kt`,
`Util.kt`, `Settings.kt`, `AlbumHeader.kt`, `SelectGenreFragment.kt`, `ultrasonic/build.gradle`,
`core/domain/build.gradle`, 16 archivos de recurso eliminados,
`ultrasonic/src/main/res/values*/strings.xml` (3 strings más, en los 17 archivos).

Un primer intento del script de limpieza tenía un bug real: trataba cualquier recurso no-string
como "un archivo por recurso" y borró por error el archivo compartido `colors.xml` completo (que
tiene más de veinte colores, solo 4 sin usar) antes de fallar. Se detectó antes de continuar,
`colors.xml` se restauró desde git sin pérdida, y el fix real solo quita las 4 líneas de color
correspondientes.

Archivos: 42 archivos Kotlin reformateados (ktlintFormat); `ultrasonic/src/main/res/values/strings.xml`
y `colors.xml` (líneas eliminadas); 21 archivos de recurso eliminados en
`ultrasonic/src/main/res/{drawable,menu,layout}/`.

## N. La notificación de reproducción usaba el ícono genérico de audífonos de Media3 en vez de la nota de Taki

Reportado por el usuario comparando contra Spotify y Symfonium, que sí muestran su propio ícono en
ese lugar (el panel rápido de notificaciones, junto a "This phone"). La primera hipótesis fue que
ese espacio era un indicador de salida de audio controlado por el sistema (como el fondo de Android
Auto) — se descartó al confirmar que otras apps sí logran mostrar su propio ícono ahí.

**Causa**: `PlaybackService` nunca configuraba un `MediaNotification.Provider` propio, así que Media3
usaba su `DefaultMediaNotificationProvider` sin personalizar — que por default apunta a
`androidx.media3.session:R.drawable.media3_notification_small_icon`, un dibujo genérico de
audífonos incluido en la propia librería (confirmado inspeccionando el `.aar` de
`media3-session:1.10.1` y las fuentes de `DefaultMediaNotificationProvider.java`: la constante se
inicializa exactamente así, línea 295 del código fuente de la librería).

**Fix**: en `PlaybackService.onCreate()` ahora se construye un
`DefaultMediaNotificationProvider.Builder(this).build()` y se le llama
`.setSmallIcon(R.drawable.ic_launcher_monochrome)` antes de registrarlo con
`setMediaNotificationProvider(...)` — reutilizando el ícono monocromático de Taki que ya existía
para el launcher adaptativo (ver punto 39 de `HANDOFF.md`), en vez de crear un asset nuevo.
Verificado en dispositivo: el panel rápido de notificaciones ahora muestra la nota de Taki en vez de
los audífonos genéricos.

Archivo: `ultrasonic/src/main/kotlin/org/moire/ultrasonic/service/PlaybackService.kt`.

## La notificación de reproducción, el panel rápido y Android Auto mostraban una estrella en vez del corazón

Reportado por el usuario comparando contra Spotify y Symfonium en el mismo panel de Android — ambos
muestran un corazón ahí, Taki mostraba una estrella. Esto **no** era una limitación de la
plataforma (se descartó esa hipótesis probando en dispositivo): dos causas reales y separadas, la
segunda encontrada después de que la primera no alcanzó para arreglar lo que se veía en el panel
rápido de notificaciones.

### L. `MediaItemConverter.toMediaItem()` pisaba el `HeartRating` con un `StarRating` legado

`buildMediaItem()` ya declaraba correctamente `setUserRating(HeartRating(starred))`, pero
`toMediaItem()` lo sobreescribía con `StarRating(5, userRating.toFloat())` cuando la pista tenía un
`userRating` no nulo — el campo legado de 5 estrellas del servidor, que la app ya no expone en
ninguna pantalla propia (ver punto 4 de `HANDOFF.md`) pero que muchos servidores igual siguen
devolviendo para pistas calificadas hace tiempo. Esto afectaba el tipo de rating que el sistema
operativo declara (`dumpsys media_session` mostraba `rating type=5` en vez de `1`/heart).

**Fix**: se dejó de llamar `setUserRating(StarRating(...))` en ese bloque — `metadataBuilder` ya
hereda el `HeartRating` correcto de `buildMediaItem()` si no se lo pisa. Verificado con
`dumpsys media_session`: la misma pista que antes declaraba tipo estrella ahora declara
`rating type=1` (heart).

Archivo: `ultrasonic/src/main/kotlin/org/moire/ultrasonic/util/MediaItemConverter.kt`.

### M. El botón "Love"/"Dislike" de la sesión de Media3 usaba literalmente un ícono de estrella

Este era el bug que realmente se veía en el panel rápido de notificaciones y en Android Auto —
independiente del anterior. En `MediaLibrarySessionCallback.getHeartCommandButton()`, el botón
personalizado que Media3 expone al sistema (notificación, panel rápido, Android Auto) se llama
"Love"/"Dislike" pero su ícono estaba fijado a `R.drawable.rating_star_hollow`/`rating_star_full` —
un resto de antes de que la app pasara del sistema de 5 estrellas al corazón único. El nombre ya
sugería corazón; el drawable real seguía siendo una estrella, y por eso el fix anterior (L) no
cambió nada visible en esa tarjeta: son dos superficies distintas alimentadas por dos campos
distintos.

**Fix**: se cambiaron los dos íconos a `R.drawable.rating_heart_hollow`/`rating_heart_full` —
drawables de corazón que ya existían en el proyecto con el mismo naming que las estrellas que
reemplazan. Verificado en dispositivo: el panel rápido de notificaciones ahora muestra un corazón
blanco, y tocarlo sigue alternando correctamente el estado (confirmado con
`dumpsys media_session`: el nombre de la acción pasa de "Love" a "Dislike" y viceversa al togglear).

Archivo: `ultrasonic/src/main/kotlin/org/moire/ultrasonic/service/MediaLibrarySessionCallback.kt`.

**Pendiente de confirmar**: el fix se verificó en el panel rápido de notificaciones del propio
teléfono; la pantalla de Android Auto (el "Now Playing" del head unit) no se pudo probar desde este
entorno de desarrollo por no tener el head unit conectado — debería heredar el mismo arreglo, ya que
ambas superficies leen la misma sesión de Media3, pero conviene confirmarlo la próxima vez que el
usuario use Android Auto.

## Add/Edit server: dos bugs visuales reales encontrados por el usuario en pantalla

Reportados directamente por el usuario mirando la pantalla "Add collection" en el Pixel 7 (misma
sesión que la verificación de Capa 2 de abajo), no parte del barrido de la auditoría.

### J. El selector de color del servidor se veía y se sentía como un switch más, y por default salía rojo

**Síntoma**: en "Advanced settings", la fila "Server color" mostraba una píldora rojo brillante,
idéntica en tamaño/forma/posición a los tres `SwitchMaterial` reales que están justo debajo
(autofirmado, password plano, jukebox). Nada indicaba que en realidad es un botón que abre un
selector de color (`ColorPickerDialog`) — se veía como un cuarto toggle, y en un color que no existe
en la paleta de Taki.

**Causa (dos bugs separados en la misma función)**:
- `EditServerFragment.updateColor()` usaba `R.drawable.thumb_drawable` como `background` del
  `ImageView` — ver `drawable/thumb.xml`: una píldora con 44dp de radio de esquina rellena de
  `?attr/colorPrimary`, literalmente el thumb de un slider/switch, reusado por error para este
  swatch de color.
- `ServerColor.getBackgroundColor()` calculaba el color por default con
  `MaterialColors.getColor(context, android.R.attr.colorPrimary, "")` — el atributo de la
  **plataforma** Android, no el `colorPrimary` de Material3 que la app sí tiene fijado al lime de
  Taki. Todos los demás usos de `MaterialColors.getColor(...)` en el proyecto
  (`ServerRowAdapter`, `TrackViewHolder`, `PlayerFragment`, `LyricsFragment`) ya usan
  correctamente `androidx.appcompat.R.attr.colorPrimary` — este era el único desalineado.

**Fix**: nuevo drawable `rounded_swatch_fill.xml` (rectángulo relleno simple, mismo radio de 28dp
que el borde `rounded_border` ya dibujado encima) reemplaza a `thumb_drawable` como background;
`ServerColor.kt` ahora usa `androidx.appcompat.R.attr.colorPrimary`. Verificado en dispositivo: el
swatch por default ahora sale verde lima, no rojo.

Archivos: `ultrasonic/src/main/kotlin/org/moire/ultrasonic/fragment/EditServerFragment.kt`,
`ultrasonic/src/main/kotlin/org/moire/ultrasonic/util/ServerColor.kt`,
`ultrasonic/src/main/res/drawable/rounded_swatch_fill.xml` (nuevo).

### K. "Test Connection" y "Save" quedaban debajo de la barra de navegación del sistema, imposibles de tocar

**Síntoma**: en el borde inferior de la pantalla Add/Edit server, los botones "Test Connection" y
"Save" aparecían parcialmente tapados por los botones de navegación del sistema (atrás/inicio/
recientes) — en la práctica, no se podían tocar.

**Causa**: `NavigationActivity.applyBottomInset()` ya existía justo para esta clase de problema
(edge-to-edge dibuja detrás de la barra de navegación del sistema desde Android 15/targetSdk 35), y
ya sabía que aplicaba a "Settings/About/Equalizer/ServerSelector/EditServer" — pero sólo le daba el
padding del inset a `bottomNavigation` o a `nowPlayingView` (el mini-player), la que estuviera
visible. En destinos donde **ambos** están ocultos — como EditServerFragment cuando no hay ninguna
canción sonando, así que ni la barra inferior ni el mini-player aparecen — ninguno de los dos
absorbía el inset, y el contenido propio del fragment (los botones, en este caso) quedaba expuesto
debajo de la barra del sistema.

**Fix**: el `FrameLayout` que envuelve a `nav_host_fragment` en `navigation_activity.xml` ahora
tiene id (`nav_host_container`) y `applyBottomInset()` le aplica el padding inferior cuando ni la
barra ni el mini-player están visibles. Verificado en dispositivo: los dos botones quedan
completamente arriba de la barra del sistema, con espacio de sobra.

Archivos: `ultrasonic/src/main/kotlin/org/moire/ultrasonic/activity/NavigationActivity.kt`,
`ultrasonic/src/main/res/layout/navigation_activity.xml`.

## Verificación en dispositivo (Capa 2, Pixel 7): restauración de cola grande y resaltado de la barra inferior

Primera pasada real de la Capa 2 de `docs/AUDITORIA_FUNCIONAMIENTO_INTERNO.md` (uso prolongado en
el Pixel 7 con logcat completo) desde que el documento se escribió. Encontró dos bugs reales, ambos
arreglados y verificados en el dispositivo con el fix aplicado.

### H. Restaurar una cola de más de un puñado de canciones volvía siempre a la pista 0

**Síntoma**: reproducir una cola de 50 canciones, avanzar hasta una pista intermedia (ej. índice 20,
1:38), forzar el cierre del proceso y reabrir la app — la sesión restaurada mostraba la primera
pista de la cola en 0:00 en vez de la pista/posición real donde había quedado. Contradecía lo que el
punto 43 de `HANDOFF.md` daba por verificado, pero esa prueba sólo usó una cola de 2 canciones y el
bug no se manifiesta con colas tan chicas (ver causa).

**Causa**: `MediaPlayerManager.restore()` llamaba a `addToPlaylist(state.songs, ...)` — que sólo
*lanza* la corrutina que agrega las canciones (`mainScope.launch { addToPlaylistMutex.withLock {
addToPlaylistLocked(...) } }`, pensada así a propósito para trocear + `yield()` en colas grandes sin
bloquear el hilo principal, ver la protección anti-ANR más abajo) — y a continuación, en el mismo
cuerpo síncrono, llamaba a `seekTo(state.currentPlayingIndex, state.currentPlayingPosition)`. Como
`addToPlaylist()` no espera a que la corrutina termine, `seekTo()` se ejecutaba con el controller
todavía sin ninguna canción cargada; su propio guard (`if (controller?.currentTimeline?.isEmpty !=
false || index >= controller!!.currentTimeline.windowCount) return`) descartaba la búsqueda en
silencio. Cuando la corrutina finalmente agregaba las canciones, Media3 arrancaba desde el índice
0 por default, y ese estado transitorio (`track=null, index=0`) además se serializaba de inmediato a
disco vía `RxBus.throttledPlayerStateObservable`, pisando el checkpoint correcto que se acababa de
leer. Confirmado con logcat: `Deserialized currentPlayingIndex: 20, currentPlayingPosition: 98654`
seguido, milisegundos después, de `Serialized currentPlayingIndex: 0, currentPlayingPosition: 0`.

**Fix**: `restore()` ahora lanza su propia corrutina que llama a `addToPlaylistLocked()` directamente
(con el mismo mutex) y sólo hace `seekTo()`/`prepare()`/`play()` *después* de que esa llamada
termina — sin carrera posible, porque ambos pasos corren en secuencia dentro de la misma corrutina.
No se tocó `addToPlaylistLocked()` en sí ni sus otros llamadores (Bookmarks, Home, Artist Detail,
Search, tap-to-play en listas), que ya usan su propio parámetro `startIndex`/`startPositionMs` con
semántica distinta (reproducir de inmediato, no restaurar en pausa).

**Verificación**: cola de 50 canciones (Library → Songs → reproducir todo), avance hasta índice 10
(0:39), force-stop, reapertura — restauró exactamente "Airborne Fighter" en 0:36 (el checkpoint
periódico es cada 5s), en pausa, sin autoplay. Regresión: reproducir un álbum completo desde Library
sigue arrancando de inmediato en la pista tocada, sin cambios de comportamiento.

Archivo: `ultrasonic/src/main/kotlin/org/moire/ultrasonic/service/MediaPlayerManager.kt`.

### I. La barra de navegación inferior podía quedar resaltando "Home" al navegar por sub-pantallas de Library

**Síntoma**: entrar a Library → Songs (o Albums/Artists/Genres/Playlists) mostraba el ítem "Home" de
la barra inferior resaltado en vez de "Library", de forma consistente (no dependía de taps rápidos).

**Causa**: el grafo de navegación (`navigation_graph.xml`) es plano — `trackCollectionFragment`,
`albumListFragment`, etc. no están anidados bajo `homeFragment` ni `mainFragment`. El
`setupWithNavController()` de AndroidX sólo actualiza la selección de la barra cuando el destino
actual coincide con uno de los 4 ítems del menú; para cualquier otro destino (la gran mayoría de las
~25 pantallas de la app) deja la selección exactamente como estaba, sin resaltar nada ni limpiarla —
así que lo que se veía resaltado era el último ítem realmente emparejado antes de navegar más
adentro, no necesariamente desde dónde se entró a la pantalla actual.

**Fix**: en el `addOnDestinationChangedListener` de `NavigationActivity.kt` (donde ya se calculaban
`isLibraryTrackCollection`/`isAlbumDetail` para el header/back-button) se agregó una corrección
explícita de `bottomNavigation?.menu?.findItem(...)?.isChecked = true` para los destinos que sólo
son alcanzables desde un único punto de entrada: Songs/Liked Songs (`trackCollectionFragment` con
`libraryRoot`/`getStarred`), Playlists, Albums, Artists y Genres → resaltan "Library" (los 5 salen
únicamente de las filas de `MainFragment`); `downloadedAlbumFragment` → resalta "Downloads" (sólo
sale de `DownloadsFragment`). **Deliberadamente no se tocó** el resto de los destinos compartidos
(`trackCollectionFragment` para detalle de álbum/artista/género/playlist, `artistDetailFragment`) —
son alcanzables tanto desde Home como desde Library según el flujo del usuario, y adivinar cuál
"pertenece" a cuál sin un mecanismo real de seguimiento de origen sería peor que dejarlos como están.

**Hallazgo relacionado, no arreglado**: alternar muy rápido entre las 4 pestañas mientras se está en
una pantalla hija profunda (ej. Album Detail) puede dejar la barra resaltando una pestaña distinta
de la que realmente se muestra — probablemente varias llamadas a `navigate()` encoladas contra
`NavController`/`FragmentManager` más rápido de lo que cada transacción llega a resolverse. No se
identificó una causa concreta ni se intentó un fix especulativo (podría romper la navegación normal
por pestañas); si se retoma, empezar reproduciendo el patrón exacto con logcat de
`FragmentManager`/`NavController` en modo debug.

Archivo: `ultrasonic/src/main/kotlin/org/moire/ultrasonic/activity/NavigationActivity.kt`.

## Auditoría interna: Shuffle, descarga duplicada y listas que no cancelan su carga anterior

Continuación de `docs/AUDITORIA_FUNCIONAMIENTO_INTERNO.md`, trazando los tres "flujos críticos"
que quedaban pendientes (Descargas, Cambio de servidor/Offline, y crecimiento de la cola en vivo).
Los tres arrojaron un bug real cada uno.

### D. El botón Shuffle (notificación/Android Auto) podía volver a producir el ANR ya arreglado

`MediaLibrarySessionCallback.shuffleCurrentPlaylist()` no pasaba por
`MediaPlayerManager.addToPlaylist()` (la función protegida contra ANR, ver la entrada "ANR real al
reproducir un álbum grande" más abajo) — hacía su propio `player.addMediaItems(...)` sin trocear,
directo en el callback de `onCustomCommand`, que corre en el hilo de aplicación. Con una cola
crecida a varios miles de pistas (uso normal a lo largo de una sesión larga con "Agregar a la
cola"/"Reproducir a continuación" repetidos), tocar Shuffle desde la notificación o Android Auto
podía volver a bloquear la app el tiempo suficiente para un ANR — exactamente el bug ya
documentado, sólo que por una puerta distinta que no se tocó en el fix original.

Se aplicó el mismo patrón de troceo + `yield()` que ya usa `addToPlaylistLocked()`. La constante
`ADD_MEDIA_ITEMS_CHUNK_SIZE` pasó de `private` a `internal` en `MediaPlayerManager.kt` para
reusarla en vez de duplicar el número mágico.

Archivos: `ultrasonic/src/main/kotlin/org/moire/ultrasonic/service/MediaLibrarySessionCallback.kt`,
`ultrasonic/src/main/kotlin/org/moire/ultrasonic/service/MediaPlayerManager.kt`.

### E. Descargar la misma pista dos veces casi al mismo tiempo podía correr dos descargas en paralelo

`DownloadService.download()` es `@Synchronized`, pero el `@Synchronized` sólo protegía el
`launch{}` que lanza `downloadAsync()` en una corrutina nueva — no el cuerpo real, que se ejecuta
después, ya fuera del bloque sincronizado. Dos llamadas casi simultáneas (doble tap en descargar, o
la misma pista disparada desde dos pantallas distintas) podían pasar ambas el chequeo "¿ya está en
cola/descargando?" antes de que ninguna la hubiera agregado todavía, resultando en dos
`DownloadTask` escribiendo el mismo archivo en paralelo y una de las dos entradas de
`activeDownloads` pisando silenciosamente a la otra (su `Job` quedaba huérfano, sin forma de
cancelarlo).

Se agregó un `Mutex` (`downloadQueueMutex`) que serializa toda la secuencia de
"filtrar ya descargadas/en curso → calcular prioridad → agregar a la cola" dentro de
`downloadAsync()`, mismo patrón que `addToPlaylistMutex` en `MediaPlayerManager`.

**Nota relacionada, no corregida (queda para una revisión aparte):** el chequeo de "¿está
completa la descarga?" en todo el proyecto es sólo por existencia del archivo
(`Storage.isPathExists`), sin verificar tamaño/checksum, y el paso final de `afterDownload()` que
renombra `.partial` a `.complete`/`.pinned` es copia+borrado, no un rename atómico — si el proceso
muere a mitad de ese renombrado (force-stop/OOM), puede quedar un archivo `.complete` truncado que
todo el resto del código trataría como descargado con éxito. Es una ventana de falla angosta y
arreglarla bien implica tocar dos backends de almacenamiento distintos (`JavaFile`/`StorageFile`),
así que se deja documentada como hallazgo real pero de menor prioridad en vez de parchearla rápido.

Archivo: `ultrasonic/src/main/kotlin/org/moire/ultrasonic/service/DownloadService.kt`.

### G. Artists/Albums/Genres no cancelaban su carga anterior (mismo patrón que el bug de Artist Detail)

`GenericListModel.backgroundLoadFromServer()` — la función base de la que heredan
`ArtistListModel`, `AlbumListModel` y el resto de las pantallas de listado — lanzaba en
`viewModelScope` sin guardar el `Job`, igual que el bug ya corregido en `ArtistDetailFragment` (ver
más abajo en este archivo). Esto es más amplio de lo que parece a simple vista: no sólo un refresh
rápido repetido podía pisar resultados, sino que **cambiar de servidor o entrar/salir de modo
Offline mientras una de estas listas estaba cargando** dejaba la carga vieja corriendo sin cancelar
— si esa respuesta del servidor anterior llegaba después de que ya se hubiera cargado la lista del
servidor nuevo, la pisaba silenciosamente con datos del servidor equivocado.

Al estar en la clase base, un solo fix cubre las tres pantallas (Artists/Albums/Genres) en vez de
repetirlo tres veces: se guarda el `Job` y se cancela antes de relanzar, mismo patrón ya usado en
`ArtistListModel.setSortOrder()` y `ArtistDetailFragment.load()`.

Archivo: `ultrasonic/src/main/kotlin/org/moire/ultrasonic/model/GenericListModel.kt`.

### Lo que se revisó y no mostró problemas

- **Cambio de servidor y modo Offline**: `MediaPlayerManager` y `PlaybackService` ya manejan
  correctamente la transición (detienen Jukebox/descargas, reconstruyen el backend de ExoPlayer
  con el cliente HTTP nuevo). `MusicServiceFactory` no cachea un cliente viejo. El único problema
  real era el de `GenericListModel` de arriba.
- **Descargas: dispatcher e I/O**: todo el trabajo de archivo corre en `Dispatchers.IO`, sin
  llamadas a Media3/MediaController en este flujo.
- **Cancelación de una descarga en curso**: funciona correctamente vía `Job.cancel()` chequeado
  entre cada bloque copiado; los archivos parciales se conservan a propósito para poder reanudar
  con range GET, no es un descuido.

### Verificación

Compilación, pruebas unitarias y `assembleDebug` en verde. No verificado en dispositivo real (el
Shuffle con una cola de miles de pistas y la descarga duplicada por doble tap requieren,
respectivamente, un álbum enorme real y timing preciso — ambos necesitan el Pixel 7 con datos
reales para confirmar en la práctica, no sólo por inspección de código).

## Header genérico (Playlists/Genre/Starred/All songs/Artist-songs por carpeta) modernizado

### Síntoma

Usuario reportó que, al entrar a un artista, la pantalla todavía mostraba "headers antiguos".

### Causa

Al tocar un artista que viene del listado por **carpeta/índice** (servidor sin tags ID3, o
cualquier servidor donde ese artista concreto se resuelve como `Index` en vez de un `Artist` real),
`ArtistListFragment.onItemClick()` navega a `TrackCollectionFragment` con `isArtist = false` en vez
de a `ArtistDetailFragment` — esto ya estaba documentado como pendiente en `HANDOFF.md` ("Index/
folder entries still follow the legacy track-collection route"). Esa ruta usa `HeaderViewBinder` +
`list_header_album.xml`, el único header de este tipo que nunca se tocó en los pases visuales
anteriores: seguía siendo un `RelativeLayout` con `?android:attr/textAppearanceMedium/Small` (pre-
Material3), sin `Ultrasonic.PrimaryText`/`SecondaryText`, con portada cuadrada sin recortar y cinco
líneas de texto apiladas (artista, género, año, cantidad de canciones, duración) cada una en su
propia fila. Este mismo header también aparece — con el mismo aspecto anticuado — al abrir una
playlist, un género, "Starred" o "All songs", no sólo en el caso de artista reportado.

### Fix

Se rediseñó `list_header_album.xml` reusando el patrón ya establecido en el resto de la app para
"portada + texto" (`list_item_album.xml`, `list_item_playlist.xml`, etc.): portada dentro de una
`MaterialCardView` de 4dp de radio, título con `Ultrasonic.PrimaryText` +
`TextAppearance.Material3.TitleMedium`, y una segunda línea de artista con
`Ultrasonic.SecondaryText`. Las cuatro líneas sueltas de género/año/canciones/duración se
consolidaron en una sola línea de metadatos ("Rock · 1990 · 10 songs · 42:10"), con el mismo
formato `joinToString(" · ")` que ya usa `AlbumDetailHeaderBinder` para su subtítulo — mismo
patrón, no uno nuevo. `HeaderViewBinder.kt` se actualizó para los IDs nuevos.

### Verificación

Compilación, pruebas unitarias y `assembleDebug` en verde. **No verificado visualmente**: el
emulador de esta sesión no tiene ningún servidor configurado, así que no hay una playlist/género/
artista real para navegar y ver el header con datos reales. Pendiente confirmar en el Pixel 7 con
un servidor real, idealmente uno en modo carpeta/índice (no ID3) para cubrir exactamente el caso
reportado, además de abrir una playlist y un género para confirmar que las tres pantallas se ven
bien con el nuevo layout.

Archivos: `ultrasonic/src/main/res/layout/list_header_album.xml`,
`ultrasonic/src/main/kotlin/org/moire/ultrasonic/adapters/HeaderViewBinder.kt`.

## Mini reproductor tapado por la barra de navegación de Android en Lyrics (y pantallas equivalentes)

### Síntoma

Usuario reportó: en la pantalla de Letras (Lyrics), los controles de reproducción (el mini
reproductor flotante con anterior/play/siguiente) quedan parcialmente debajo de los botones de
navegación del sistema (atrás/inicio/recientes).

### Causa

`navigation_activity.xml` es un `LinearLayout` vertical con `now_playing_fragment` (mini
reproductor) y `bottom_navigation` como sus dos últimos hijos. El único manejo de insets que
existía (`NavigationActivity.onCreate()`) sólo aplicaba el inset superior de la barra de estado al
root — nunca se aplicó el inset inferior de la barra de navegación a nada. Mientras
`bottom_navigation` está visible esto no se nota (por casualidad, su propio espacio visual absorbe
el área), pero en destinos que la ocultan (`updateChromeVisibility()`: Player, Settings, About,
Equalizer, selector de servidor, editar servidor, Lyrics) mientras el mini reproductor sigue
visible, éste queda pegado al borde físico inferior de la pantalla, debajo de la barra de sistema
dibujada por encima del contenido (edge-to-edge, forzado desde Android 15/targetSdk 35). Lyrics fue
la pantalla reportada, pero Settings/About/Equalizer/ServerSelector/EditServer comparten
exactamente la misma forma del bug.

### Fix

Se extendió el listener de insets ya existente en `NavigationActivity.onCreate()` para capturar
también el inset inferior de `WindowInsetsCompat.Type.navigationBars()`, y una nueva función
`applyBottomInset()` decide en cada cambio de visibilidad cuál de las dos vistas (`bottomNavigation`
o `nowPlayingView`) es la que realmente toca el borde inferior en ese momento, y le aplica el
padding correspondiente (0 a la que no lo toca). Se llama desde `showNowPlaying()`,
`hideNowPlaying()` y al final de `updateChromeVisibility()`, que son los tres puntos que cambian la
visibilidad de esas vistas (incluyendo los disparadores por RxBus de estado de reproducción y el
gesto de descarte del mini reproductor, no sólo la navegación).

No se tocó el Player ni su padding fijo existente (`player_panel_bottom_padding`, 28dp): en Player
ambas vistas (`bottomNavigation` y `nowPlayingView`) están ocultas y su propio contenido ya se
verificó en dispositivo en un pase anterior, así que evitar tocarlo evita un posible regresión
visual no pedida.

### Verificación

Compilación y pruebas unitarias verdes. Instalado en un emulador con barra de navegación de 3
botones (el caso más exigente, más grueso que gestos) y confirmado que la app arranca sin errores.
**No verificado visualmente en Lyrics con reproducción real**: el emulador de esta sesión no tiene
ningún servidor configurado ni contenido descargado, así que no hay forma de tener una pista
sonando y abrir Lyrics con el mini reproductor visible. Pendiente confirmar en el Pixel 7 con un
servidor real: reproducir una canción, abrir Letras, y verificar que el mini reproductor quede
completamente por encima de los botones de navegación del sistema (probar también con navegación
por gestos, no sólo 3 botones).

Archivo: `ultrasonic/src/main/kotlin/org/moire/ultrasonic/activity/NavigationActivity.kt`.

## Auditoría interna: scopes que sobreviven a su vista/fila, y carga sin cancelar en Artist Detail

Continuación de `docs/AUDITORIA_FUNCIONAMIENTO_INTERNO.md` (prioridad actual tras dar por
cerrada la parte visual). Se investigaron los dos sospechosos que el propio documento marcaba
como punto de partida, más un barrido estático del resto de los patrones de bug ya documentados
en este proyecto; ambos sospechosos resultaron reales y se corrigieron, y el barrido reveló un
tercer bug del mismo tipo, no documentado antes, en Artist Detail.

### A. `PlayerFragment.ioScope` nunca se cancelaba

`ioScope` (`CoroutineScope(Dispatchers.IO)`) era independiente del `mainScope` que el archivo ya
reasigna en cada `onCreateView()` y cancela en `onDestroyView()` (ver el bug histórico de
`CoroutineScope by CoroutineScope(...)` más abajo en este mismo archivo de cambios). Al no
cancelarse nunca, podía seguir trabajando después de que la vista fuera destruida.

- El chequeo de disponibilidad de Jukebox (sólo escribe un campo, sin mensaje al usuario) ahora
  corre en `mainScope.launch(Dispatchers.IO + ...)` en lugar de un scope propio — se cancela solo
  si la vista muere antes de terminar.
- `savePlaylistInBackground()` (crea una playlist en el servidor y muestra un toast con el
  resultado) migró a `Fragment.launchWithToast()` (`util/CoroutinePatterns.kt`), que ya existía en
  el proyecto para exactamente este caso pero no se usaba acá. Al estar atado a
  `activity?.lifecycleScope` en vez de al scope de la vista, ya no puede intentar mostrar un toast
  sobre un Fragment desconectado. La llamada de red se envuelve en `withContext(Dispatchers.IO)`
  porque `launchWithToast` corre su bloque en Main por defecto. Los tres mensajes (guardando/listo/
  error) se preservaron textualmente; único cambio de comportamiento real: una cancelación
  genuina (antes tratada como "éxito" y mostraba "Playlist saved") ahora no muestra ningún toast,
  que es lo correcto dado que sólo puede ocurrir si la Activity ya se destruyó.
- Se eliminó el campo `ioScope`.

Archivo: `ultrasonic/src/main/kotlin/org/moire/ultrasonic/fragment/PlayerFragment.kt`.

### B. `TrackViewHolder` con `CoroutineScope` de instancia fija en una fila reciclada

`TrackViewHolder` implementaba `CoroutineScope by CoroutineScope(Dispatchers.IO)` para toda su
vida útil como `ViewHolder`, pero un `ViewHolder` se recicla y se reutiliza para canciones
distintas al hacer scroll. `dispose()` (llamado desde `TrackViewBinder.onViewRecycled()`) sólo
liberaba la suscripción RxJava, nunca cancelaba el scope — una corrutina lanzada para la canción A
podía terminar y tocar la UI de la fila después de que esa misma vista ya mostrara la canción B.

Se reemplazó la delegación de interfaz por un campo `scope` reasignable, cancelado y recreado en
cada `dispose()` (mismo patrón ya usado para `mainScope` en `PlayerFragment`, adaptado a que acá
el "fin de vida" es cada reciclaje, no una destrucción única). El guard existente
`if (it.id != song.id) return@launch` se mantiene como defensa adicional.

Archivo: `ultrasonic/src/main/kotlin/org/moire/ultrasonic/adapters/TrackViewHolder.kt`.

### C. `ArtistDetailFragment.load()` no cancelaba la carga anterior (bug nuevo, mismo patrón que el histórico #4)

`load(refresh)` lanzaba en `model.viewModelScope` sin guardar el `Job`, así que dos refrescos
rápidos seguidos (pull-to-refresh repetido) podían dejar que una respuesta vieja pisara a una más
nueva — el mismo patrón ya corregido en su momento para Library, Crear Playlist y paginación por
género, pero que nunca se aplicó a esta pantalla. Se corrigió con el mismo patrón ya usado en
`ArtistListModel.setSortOrder()`: guardar el `Job` en `loadJob` y cancelarlo antes de relanzar.

Archivo: `ultrasonic/src/main/kotlin/org/moire/ultrasonic/fragment/ArtistDetailFragment.kt`.

### Barrido estático: sin más sospechosos nuevos

Se revisaron además, sin encontrar problemas: los demás `CoroutineScope by CoroutineScope(...)`
del proyecto (todos singletons de Koin que viven todo el proceso — `EqualizerController`,
`ActiveServerProvider`, `ImageLoader`, `FileLoggerTree`, `PlaybackService`, `RatingManager`,
`Scrobbler`, `ImageLoaderProvider`, `CacheCleaner` — patrón aceptado, no un bug de vista/fila);
el resto de la paginación por offset (ya sigue el patrón seguro de `ArtistListModel`); y el acceso
a `mediaPlayerManager`/Media3 desde fragments y desde `MediaPlayerLifecycleSupport`'s
`BroadcastReceiver` (todo corre en el hilo principal).

### Cierre del bug histórico #7 (Cursor sin cerrar, StrictMode)

Se investigó a fondo el `CloseGuardException` de `AbstractCursor.close`/`CursorWrapperInner.close`
detectado el 09/08/2026 al reabrir la app tras un `force-stop`. Los únicos 4 sitios del código que
obtienen un `Cursor` manualmente (`StorageFile.kt`, líneas 38/54/196/209 — `length`, `lastModified`,
`exists()`, `getChildren()`) ya envuelven la consulta en `.use { }`, que garantiza el cierre incluso
ante una excepción o un `return` temprano. No se encontró ningún `Cursor` sin cerrar en el código de
la app. La causa más probable es un path interno de Android (SAF/`DocumentProvider`) invocado
durante la restauración de sesión tras el `force-stop`, fuera del alcance de un fix de código en
este proyecto. Se documenta acá para no reabrir esta investigación sin una pista nueva.

### Verificación

Compilación (`assembleDebug`) y pruebas unitarias verdes. Instalado y arrancado en el emulador: sin
`FATAL EXCEPTION`, sin ANR, navegación a Home correcta; el único hit de StrictMode del log es
preexistente y ajeno a este cambio (`newSSLContext` en `PlaybackService.getLocalPlayer()`, ya
presente antes de esta auditoría). No había un Pixel 7 físico conectado en esta sesión, así que
los escenarios que ejercitan el timing real de estos tres fixes —guardar playlist y salir del
Player de inmediato, abrir/cerrar el overflow del Player varias veces seguidas, scroll rápido en
una lista larga, doble pull-to-refresh rápido en Artist Detail— quedan pendientes de probar en
dispositivo real, siguiendo la distinción de `HANDOFF.md` entre verificación de build y
verificación real en dispositivo.

## ANR real al reproducir un álbum grande (Bach 333, 5.517 canciones)

### Síntomas

- Usuario reportó: abrir el álbum "Bach 333: The New Complete Edition" (222
  discos, 5.517 canciones), tocar una canción para reproducir, la app se
  demora, empieza a reproducir y al rato aparece "Taki isn't responding" —
  bloqueando la reproducción.

### Causa

La protección contra ANR aplicada anteriormente (ver la sección "Corrección
de bloqueo en el mini reproductor" más abajo y
`docs/POSIBLES_ERRORES_Y_VERIFICACION.md`) sólo cubría la restauración de la
sesión guardada al abrir la app. El camino real que dispara este bug —tocar
una canción o el botón Play de un álbum enorme para empezar a reproducir
ahora— nunca pasó por esa protección: `TrackCollectionFragment.playFromHere()`
(y los equivalentes en `ArtistDetailFragment`, `BookmarksFragment`,
`HomeFragment`, `SearchFragment`, y el botón "Play all") llaman a
`MediaPlayerManager.addToPlaylist()`, que convertía **todas** las pistas a
`MediaItem` y hacía **un solo** `controller.addMediaItems()` de forma
síncrona en el hilo principal — con 5.517 pistas, exactamente el mismo
patrón de ANR ya documentado, en un camino de código distinto.

### Fix

`addToPlaylist()` ahora corre en una corrutina de `mainScope` protegida por
un `Mutex` (reemplaza el `@Synchronized` anterior, que no es seguro combinado
con corrutinas):

- La conversión `Track → MediaItem` se hace en `Dispatchers.Default` (fuera
  del hilo principal), ya que es trabajo de CPU puro sin llamadas a Media3.
- `controller.addMediaItems()` se llama en bloques de 200 elementos
  (`ADD_MEDIA_ITEMS_CHUNK_SIZE`), con un `yield()` entre cada bloque — esto
  mantiene la llamada en el hilo de aplicación que Media3 exige, pero le
  devuelve el control al despachador de entrada entre bloques para que la
  app no deje de responder aunque el total de trabajo tome varios segundos.
- Se agregó un parámetro opcional `startIndex`/`startPositionMs` a
  `addToPlaylist()` para que "encolar y reproducir en una posición
  específica" quede atómico dentro de la misma corrutina, en vez de que cada
  pantalla llame `play(index)` por separado inmediatamente después (lo cual
  ya no podía asumirse síncrono). Se actualizaron los 5 puntos de llamada que
  dependían de esa secuencia: `TrackCollectionFragment.playFromHere()`,
  `ArtistDetailFragment.playTrack()`, `BookmarksFragment.playNow()` (con
  posición, para resumir en el punto guardado), `HomeFragment.playMix()`, y
  `SearchFragment.onSongSelected()`.

### Verificación

Compilado, tests unitarios verdes, e instalado en el Pixel 7 físico contra el
álbum real de 5.517 canciones: se tocó una pista a la mitad del disco 1,
logcat confirmó `Adding 5517 media items` (la cola completa, sin recortar) y
cero `FATAL EXCEPTION`/ANR/acceso a `MediaController` desde hilo incorrecto;
la pista tocada quedó marcada como activa y reproduciendo. Se registraron dos
saltos de ~1 segundo de frames durante el encolado (jank perceptible pero muy
por debajo del umbral de 5s de ANR) — no bloquea el uso, queda como posible
ajuste futuro de tamaño de bloque si se quiere más fluidez.

Archivo principal:
`ultrasonic/src/main/kotlin/org/moire/ultrasonic/service/MediaPlayerManager.kt`.
También `TrackCollectionFragment.kt`, `ArtistDetailFragment.kt`,
`BookmarksFragment.kt`, `HomeFragment.kt`, `SearchFragment.kt`.

## Identificación del cliente ante el servidor: Ultrasonic → Taki

- El parámetro `c=` que la app manda en cada request a la API de
  Subsonic/Navidrome (`REST_CLIENT_ID`) decía `"Ultrasonic"`. Es lo que
  Navidrome usa para nombrar la fila en su panel de clientes/players
  conectados, y seguía mostrando el nombre heredado ahí a pesar del rebrand.
  Ahora dice `"Taki"`.
- La fila anterior `Ultrasonic [okhttp]` en ese panel queda inactiva (Navidrome
  identifica cada player por cliente+usuario) y aparece una fila nueva
  `Taki [okhttp]` en el próximo request; no se puede migrar la fila vieja
  automáticamente, se borra a mano desde Navidrome si se quiere.

Archivo: `ultrasonic/src/main/kotlin/org/moire/ultrasonic/util/Constants.kt`.

## Corrección de bloqueo en el mini reproductor

- La barra de progreso consultaba cada segundo `playerDuration` y
  `playerPosition` desde el hilo principal, añadiendo tráfico sincronizado y
  trabajo repetitivo durante toda la reproducción.
- Se eliminó el sondeo: al cambiar el estado se toma una sola posición válida y
  un `ObjectAnimator` lineal mueve la barra por el tiempo restante. Pausa,
  cambio de pista y destrucción de la vista cancelan y recalculan la animación.
  Media3 permanece en su hilo de aplicación obligatorio y no existe trabajo
  periódico ni acceso desde un hilo incorrecto.
- El checkpoint de restauración de sesión también reconstruía la cola completa
  en el hilo principal cada cinco segundos. Ahora reutiliza la última instantánea
  serializada y el checkpoint periódico solo captura índice, posición, shuffle y
  repeat; la cola completa se vuelve a convertir únicamente cuando cambia.

## Corrección de solapamiento con la barra de estado

- El listener central añadido para observar la apertura del teclado reemplazaba
  accidentalmente el manejo automático de `fitsSystemWindows`, dejando títulos
  como Search y Library debajo de la hora y los iconos del sistema.
- `NavigationActivity` aplica ahora explícitamente el inset superior de la barra
  de estado a su raíz y conserva en el mismo listener la detección del IME. El
  ajuste es central y no modifica tamaños ni estructura de las pantallas.

## Pase final de estabilidad y consistencia

- Se recorrieron Home, Library, Liked Songs, Search, Downloads, Now Playing,
  Lyrics, cola, Settings y About en el emulador conservando los datos existentes.
- Se verificaron el regreso desde Liked Songs a Library, el cierre del teclado
  antes de abandonar Search, la ocultación de la navegación de Taki con el IME,
  la apertura de Lyrics y cola, y la ausencia de navegación inferior en Player.
- Now Playing sobrevivió a la recreación portrait/landscape/portrait con título,
  progreso, transporte y controles secundarios presentes en ambas orientaciones.
- About muestra `Taki (0.1.0-beta)` y reserva Ultrasonic únicamente para la
  atribución GPLv3. La búsqueda estática no encontró nuevas superficies visibles
  con la identidad anterior; permanecen identificadores y destinos internos por
  compatibilidad.
- `testDebugUnitTest` y `assembleDebug` finalizaron correctamente. Un ANR aislado
  durante una ráfaga de `uiautomator`/capturas coincidió con saturación de
  `system_server` y no se reprodujo en una ejecución limpia; la segunda ronda
  quedó sin ANR, excepción fatal ni cierre del proceso.

## Identidad del repositorio Taki

- El README y la guía de contribución presentan Taki como el proyecto actual,
  enlazan su repositorio e incidencias y describen sus principios de producto.
- Se añadió `NOTICE.md` para documentar la licencia, el carácter de obra
  modificada, la atribución a Ultrasonic y la razón por la que algunos
  identificadores internos conservan el nombre heredado.
- `LICENSE` se conserva como el texto oficial e inalterado de GNU GPLv3; la
  identidad y atribución específicas del proyecto viven en el aviso separado.

## Mini reproductor y búsquedas recientes

- El mini reproductor muestra una línea de progreso no interactiva de 2dp sobre su borde superior.
  Solo se actualiza mientras la reproducción está activa, se detiene al pausar y se oculta cuando
  la duración no es válida.
- Search conserva localmente hasta diez búsquedas confirmadas. Normaliza espacios, evita duplicados
  sin distinguir mayúsculas y permite reutilizar, eliminar individualmente o limpiar todo el historial.
- El historial se guarda al confirmar desde el teclado o al abrir un resultado; escribir para obtener
  resultados en vivo no crea entradas por sí solo.
- Mientras el teclado está abierto en Search se ocultan el mini reproductor y la navegación inferior
  de Taki, sin alterar la barra de navegación del sistema. El primer gesto o botón Atrás cierra el
  teclado y el siguiente mantiene la navegación habitual.
- Al abrir un resultado se cierra el teclado y se conserva el estado de búsqueda al volver.

## Nombre de los artefactos APK

- Gradle deja de publicar el nombre técnico heredado `ultrasonic-debug.apk`. Cada variante usa
  ahora la marca, versión y tipo de compilación: por ejemplo `Taki-0.1.0-beta-debug.apk` y
  `Taki-0.1.0-beta-release.apk`.
- El sufijo de variante permanece explícito para evitar compartir accidentalmente una build de
  desarrollo como si fuera una versión firmada de distribución.

## Pista activa visible en toda la biblioteca y Search sin icono heredado

- El mismo estado visual usado en la cola —ecualizador pequeño y título con el acento de Taki—
  se aplica ahora a las filas compartidas de canciones. Por ello la pista activa queda marcada al
  reproducir desde un álbum, playlist, lista de canciones o resultado de búsqueda, sin agregar un
  fondo saturado ni modificar las acciones de la fila.
- Fuera de la cola la coincidencia se determina por la canción, aunque su posición visual no sea
  la misma que en la cola. Dentro de la cola se conserva además el índice para distinguir una
  canción repetida varias veces.
- Search reemplaza el símbolo radial heredado de Ultrasonic por una lupa neutra en su estado
  inicial. El indicador de progreso real sigue reservado para una búsqueda en curso.

## Estado de letras y títulos largos en reproducción

- El estado de letras sincronizadas se presenta ahora como un control compuesto: la etiqueta fija
  `Sync` permanece visible debajo y únicamente cambia el icono entre visto y equis. El color sigue
  diferenciando el estado sin depender solo de él para explicar qué representa el símbolo.
- Los títulos largos usan marquee continuo en Lyrics, Now Playing y el mini reproductor, siempre
  dentro del espacio reservado antes de los controles derechos.
- En la cola solo se desplaza la pista que está sonando; las demás filas permanecen quietas para
  evitar una lista llena de animaciones simultáneas. Al reciclar una fila, su estado animado se
  restablece según la pista activa.

## Limpieza de producto: ajustes y funciones heredadas

- Las tasas de bits dejan de presentarse como once números técnicos y pasan a cinco niveles:
  Baja (96), Normal (160), Alta (256), Máxima (320) y Original (sin límite). Las preferencias
  antiguas se migran automáticamente al nivel más cercano.
- Network usa nombres orientados a la tarea: calidad por datos móviles, por Wi-Fi y de descarga.
  ReplayGain y la reproducción por hardware se agrupan como reproducción avanzada; Equalizer y
  el comportamiento al iniciar reproducción permanecen como opciones principales.
- Shares, Bookmarks y Podcasts dejan de publicarse en la raíz de Android Auto. La prueba de
  conexión continúa detectando capacidades internamente, pero su resultado visible se limita a
  Jukebox, la única función adicional que Taki todavía presenta como producto.
- Se conservan modelos, base de datos, endpoints y destinos heredados para compatibilidad; esta
  limpieza elimina accesos visibles, no datos ni soporte interno.

## Cola de reproducción modernizada

- La cola de Now Playing deja de reutilizar la fila técnica heredada y adopta una composición
  musical compacta: encabezado con cantidad, portada de 52dp, título/artista y asa de arrastre a
  la derecha.
- La pista actual se identifica mediante un ecualizador y título en el acento de Taki, sin elevar
  ni rellenar toda la fila; las demás pistas mantienen colores neutros.
- Se conservaron sin cambios tocar para reproducir, arrastrar para reordenar y deslizar para
  eliminar.

## Restauración fiable de la sesión de reproducción

- Se completó la infraestructura heredada que ya serializaba cola, pista actual, posición,
  shuffle y repeat: mientras hay reproducción se guarda ahora un checkpoint cada cinco segundos,
  evitando volver a una posición antigua si Android termina el proceso abruptamente.
- Los cambios de shuffle y repeat publican inmediatamente un nuevo estado para persistirse sin
  depender de que después cambie la pista o se pause.
- La restauración normal al abrir Taki reconstruye la cola y prepara la pista en pausa; solo una
  orden explícita de reproducción recibida externamente puede solicitar autoplay.
- El callback de inicialización se ejecuta también cuando todavía no existe una sesión guardada,
  por lo que el primer uso y los controles multimedia no esperan una restauración inexistente.

## Encabezados internos de Settings

- Todas las categorías de Settings conservan ahora el encabezado tipográfico de Taki y el botón
  de regreso compacto, en lugar de reactivar el toolbar elevado heredado.
- Equalizer adopta el mismo tratamiento al abrirse desde Now Playing.
- Se retiró de la interfaz la preferencia heredada `Clear Bookmark`; no se borran posiciones ni
  datos existentes y no cambia el comportamiento de reproducción.

## Composición visual de Now Playing

- Se reorganizó únicamente la composición del reproductor: el panel de información y controles
  ahora es una superficie flotante con esquinas de 28dp, margen exterior visible y padding interno
  de 24dp horizontal y 28dp vertical.
- Título, artista, progreso y tiempos comparten ejes; el favorito conserva un objetivo táctil fijo
  de 48dp para que títulos largos se truncen sin desplazarlo.
- Se normalizaron ritmo vertical y tamaños de controles: transporte de 64dp, icono principal de
  32dp, anterior/siguiente de 30dp, modos de 24dp y acciones secundarias con objetivos de 48dp.
- Shuffle y repeat mantienen exactamente las mismas acciones, pero ahora distinguen el estado
  inactivo con el gris secundario y el activo con el acento de Taki.
- No se modificaron navegación, reproducción ni otras pantallas. Verificado en un Pixel 7 físico
  con navegación de tres botones, altura reducida a 1080x1920 y fuente al 130%, además de recursos
  debug, compilación Kotlin, pruebas unitarias, ensamblado e instalación del APK.

Archivos principales:
- `ultrasonic/src/main/res/values/player_dimensions.xml`
- `ultrasonic/src/main/res/drawable/bg_player_panel.xml`
- `ultrasonic/src/main/res/layout/current_playing.xml`
- `ultrasonic/src/main/res/layout/player_media_info.xml`
- `ultrasonic/src/main/res/layout/player_slider.xml`
- `ultrasonic/src/main/res/layout/media_buttons.xml`
- `ultrasonic/src/main/res/layout/player_secondary_controls.xml`
- `ultrasonic/src/main/kotlin/org/moire/ultrasonic/fragment/PlayerFragment.kt`

## Pase visual de Search

- Search ahora tiene un encabezado propio consistente con Library y Downloads, y el campo usa una
  superficie neutra compacta con borde discreto en lugar de una cápsula elevada genérica.
- Antes de escribir se muestra `Search artists, albums, and songs`; al limpiar la consulta se
  restablecen el estado inicial y la lista, mientras que una búsqueda vacía conserva el mensaje
  específico de “sin resultados”. Ambos estados tienen recursos equivalentes en español.
- Los encabezados Artists/Albums/Songs adoptan el espaciado y la jerarquía neutra de Taki, y
  `Show more` utiliza un objetivo táctil Material de 48dp alineado con el contenido.
- No se modificaron endpoints, debounce, límites, autoplay ni navegación. Verificado mediante
  recursos debug, compilación Kotlin, pruebas unitarias y `git diff --check`.

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

## Settings: reagrupado siguiendo una app de referencia + Ecualizador ahora visible ahí

El usuario mandó capturas de otra app de música (agrupa Settings en Now Playing/Library/Images/
Audio/Playlists/etc.) y pidió comparar esa estructura contra lo que le quedó a Ultrasonic después
de las dos rondas de recorte. Se armó un documento comparativo (grupos propuestos, qué falta, qué
se puede construir, qué no aplica a nuestra arquitectura de cliente-servidor) y, con el visto
bueno del usuario, se hizo la parte segura de esa comparación:

- **Reagrupado sin sacar nada**: `settings.xml` pasó de "Playback Control/Network/Music Cache/
  Search/Other/Debug" a **Now Playing** (Show Now Playing on Play, Clear Bookmark, Hardware
  offload, ReplayGain, Equalizer), **Library** (ID3 tags online/offline), **Images** (Clear Image
  Cache), **Network**, **Music Cache** y **Other** (ahí se sumó "Clear Search History", que antes
  tenía su propia categoría con un solo ítem). Ningún ítem se eliminó, solo cambiaron de grupo.
- **Ecualizador ahora tiene entrada en Settings**: ya existía completo (`EqualizerFragment` +
  `EqualizerController`) pero solo se llegaba desde el menú del Reproductor — se agregó una acción
  global `toEqualizer` en `navigation_graph.xml` y un `Preference` en "Now Playing" que navega ahí
  directo.
- **Se investigaron, pero NO se implementaron** (decisión explícita del usuario: "simplifiquemos
  un poco, no nos metamos tan adentro por ahora") 4 funciones de la app de referencia que en el
  documento inicial parecían simples y resultaron tener más complejidad real de la esperada:
  volume slider (choca con el volumen que ya maneja ReplayGain automáticamente en cada cambio de
  canción), toggle de "cache de imágenes" (hay dos capas de caché separadas — Coil para miniaturas
  y una propia para portadas completas), "bajar volumen en vez de pausar al perder el foco de
  audio" (es posible que Media3 ya lo haga solo, dado que `handleAudioFocus=true` ya está
  configurado — habría que probarlo en dispositivo antes de escribir nada), y exportar playlist a
  archivo (ya sabemos escribir `.m3u`, pero solo con rutas locales de canciones descargadas — una
  exportación "real" necesita decidir qué guardar ahí). Quedan documentadas como pendientes, no
  descartadas.

Archivos: `settings.xml` (xml/), `navigation_graph.xml`, `SettingsFragment.kt`, `strings.xml`,
`setting_keys.xml`.

## Settings: estructura de dos niveles (como la app de referencia)

El usuario volvió a mandar la misma captura y confirmó que sí quería replicar esa estructura:
una pantalla principal con una tarjeta por grupo (título + subtítulo de lo que contiene), que al
tocarla abre una pantalla aparte solo con las opciones de ese grupo — no una sola lista larga con
títulos de sección como se había dejado antes.

- **`settings.xml`**: cada categoría pasó de `PreferenceCategory` a `PreferenceScreen` anidado
  (con su propia `a:key`/`a:title`/`a:summary`). AndroidX Preference ya soporta este patrón
  nativamente vía el parámetro `rootKey` de `setPreferencesFromResource()` — no hizo falta
  duplicar el XML en archivos separados.
- **Navegación por Nav Component, no por el mecanismo nativo de Preference**: la librería
  normalmente maneja el "entrar a una sub-pantalla" con `onNavigateToScreen` + transacciones
  crudas de `FragmentManager` — pero como esta pantalla vive dentro del `NavHostFragment` que ya
  usa toda la app, mezclar los dos sistemas de navegación podía romper el botón atrás. En cambio,
  se agregó una acción que apunta a `settingsFragment` desde sí mismo (`toEqualizer`-style, con
  argumentos `rootKey`/`groupTitle` nulables) y `SettingsFragment.onPreferenceTreeClick()` se
  override para interceptar el tap en cada tarjeta de grupo y navegar con
  `findNavController().navigate(...)`, igual que cualquier otra pantalla de la app. Esto mantiene
  el botón atrás y el título de la barra superior consistentes con el resto de la navegación.
- El título de la barra superior ahora es el nombre del grupo (o "Settings" en la pantalla
  principal), usando el mismo `FragmentTitle.setTitle()` que ya usa toda la app.

Archivos: `settings.xml` (xml/), `navigation_graph.xml` (acción `settingsToGroup` +
argumentos `rootKey`/`groupTitle`), `SettingsFragment.kt`, `strings.xml` (subtítulos por grupo).

## Settings: "Clear All Downloads" en Music Cache

Nueva acción, con confirmación previa (no se puede deshacer): borra todas las canciones
descargadas/fijadas de una — misma fuente de datos que ya usa la pantalla Downloads
(`activeServerProvider.offlineMetaDatabase.trackDao().get()`) y el mismo
`DownloadService.deleteAsync()` que borra por álbum ahí. `SettingsFragment` ganó acceso a
`ActiveServerProvider` por Koin (no lo necesitaba hasta ahora) para poder consultar esa base.

Archivos: `settings.xml` (xml/), `SettingsFragment.kt`, `strings.xml`, `setting_keys.xml`.

## Search: fila de álbum modernizada (empieza el pase visual de Search)

Comparando pantallas, la fila de álbum en resultados de Search (`AlbumRowDelegate`) era el
elemento más viejo — estrella de rating siempre visible, título sin el estilo tipográfico que ya
usa el resto de la app, `ellipsize="marquee"` (patrón que ya no se usa en ningún otro lado). Este
delegate es compartido con `AlbumListFragment` (pantalla "Albums" de la librería) y con
`TrackCollectionFragment` (navegación por carpetas mixtas) — el arreglo mejora las tres pantallas
a la vez, no solo Search.

- **Estrella eliminada** de `list_item_album.xml` y `grid_item_album.xml` — mismo criterio ya
  aplicado a las filas de canción esta sesión (rating fuera de la fila; en Cola se movió al menú
  contextual, acá no se agregó reemplazo, mismo trade-off aceptado sin pedirlo de nuevo).
- **Título con `Ultrasonic.PrimaryText`** en ambos layouts (antes sin estilo, se veía más
  "delgado" que el resto de los títulos de la app) y `ellipsize="end"` en vez de `marquee`.
- `AlbumRowDelegate.kt`: se sacó toda la lógica de rating (`starDrawable`/`onStarClick`/
  `RxBus.ratingSubmitter`) — quedó más chico y sin las dependencias de rating que ya no usa.

Las filas de **canción** en Search no se tocaron — ya habían heredado el estilo moderno (fila
compartida). Sigue pendiente, si se retoma: decidir si Search tiene su propio campo de búsqueda
en pantalla en vez de depender de la barra del sistema (cambio de estructura más grande, no solo
visual).

## Migración visual de las últimas pantallas "viejas"

Antes de pulir el Player y hacer la auditoría de consistencia (plan de 3 pasos acordado con el
usuario), se buscaron las pantallas que todavía no habían recibido ningún paso de la migración
visual de esta sesión. Se encontraron 4 pantallas completas y 2 detalles menores; todas comparten
el mismo criterio ya usado en el resto de la app: tipografía `Ultrasonic.PrimaryText`/
`Ultrasonic.SecondaryText` + `TextAppearance.Material3.*`, colores por atributo de tema
(`?attr/colorOnSurfaceVariant` en vez de hex fijos), y `MaterialButton`/`MaterialCardView`
explícitos en vez de widgets planos de `android.widget`.

- **About** (`help.xml`): pasó de `RelativeLayout` sin estilo a un layout con jerarquía tipográfica
  clara (título/cuerpo) y los dos botones (web/reportar bug) como `MaterialButton` con
  `materialButtonOutlinedStyle`, igual que el resto de acciones secundarias de la app.
  `AboutFragment.kt` actualizado al tipo `MaterialButton`.
- **Lyrics** (`lyrics.xml`): reordenado para que el título de la canción sea la primera línea
  destacada (antes el artista iba primero y en el mismo tono que el título); texto de letra con
  interlineado más cómodo (`lineSpacingMultiplier=1.3`). El fallback de "no se encontró letra"
  ahora se escribe sobre el título en vez del artista, ya que el título es el elemento visualmente
  prominente.
- **Configured servers** (`server_selector.xml` + `server_row.xml` + `ServerRowAdapter.kt` +
  `ServerSelectorFragment.kt`): la pantalla pasó de `ListView`/`BaseAdapter` (patrón ya
  abandonado en el resto de la app) a `RecyclerView`/`RecyclerView.Adapter`, con un FAB para
  agregar servidor (antes un botón en la esquina). Cada servidor ahora es una `MaterialCardView`
  igual que las filas de Playlists/Downloads, y el servidor activo se marca con un borde del color
  primario del tema (`strokeColor`) en vez de cambiar el fondo. El botón "⋮" del menú contextual
  pasó de `ImageButton` con padding manual (el mismo patrón de ícono borroso/sobredimensionado
  encontrado y corregido varias veces esta sesión) a `MaterialButton` con `app:iconSize` explícito.
- **Equalizer** (`equalizer.xml` + `equalizer_bar.xml`): texto gris fijo (`#c0c0c0`) reemplazado
  por colores de tema, el checkbox "Enabled" y las etiquetas de frecuencia/dB ahora usan la misma
  tipografía que el resto de la app, y el botón "Select Preset" pasó a `MaterialButton` con estilo
  outlined. Sin cambios de lógica — `EqualizerFragment.kt` sigue intacto, solo referencia los
  mismos ids de vista.
- **Add/Edit server** (`server_edit.xml`, detalle menor): se encontraron 4 `TextView` (Server
  color, Allow self-signed HTTPS certificate, Force plain password authentication, Jukebox By
  Default) que usaban por error `style="@style/Widget.AppCompat.CompoundButton.Switch"` — un
  estilo pensado para el texto interno de un widget Switch, no para una etiqueta de fila normal
  junto a un `SwitchMaterial` aparte. Se reemplazaron por `Ultrasonic.PrimaryText` +
  `TextAppearance.Material3.BodyMedium` (y `Ultrasonic.SecondaryText` para la descripción de
  "Force plain password"), y los botones "Test Connection"/"Save" pasaron de `Button` genérico a
  `MaterialButton` explícito.
- **Selector de carpeta en listas** (`list_header_folder.xml`, detalle menor): fila que aparece
  como primer ítem de Artistas/Álbumes/Canciones/Géneros cuando el servidor está en modo de
  navegación por carpetas (no ID3) — actualizada a la misma tipografía y al ícono con tinte de
  tema (`?attr/colorOnSurfaceVariant`) en vez de tamaño/color por defecto del sistema. No se pudo
  verificar visualmente en dispositivo por no tener a mano un servidor configurado en modo
  carpetas, pero los ids de vista (`select_folder_header`, `select_folder_title`,
  `select_folder_name`) no cambiaron, así que `FolderSelectorBinder.kt` sigue funcionando igual.

Verificado en dispositivo físico (dos servidores reales + Offline): navegación a cada pantalla,
cambio de servidor activo desde Configured Servers, apertura de Equalizer desde Settings, y
formulario de Add Server — sin crashes ni errores en logcat.

Archivos: `help.xml`, `AboutFragment.kt`, `lyrics.xml`, `LyricsFragment.kt`, `server_selector.xml`,
`server_row.xml`, `ServerRowAdapter.kt`, `ServerSelectorFragment.kt`, `equalizer.xml`,
`equalizer_bar.xml`, `server_edit.xml`, `list_header_folder.xml`.

Archivos: `list_item_album.xml`, `grid_item_album.xml`, `AlbumRowDelegate.kt`.

## Letras sincronizadas (karaoke) + reparación de bug del reproductor

El usuario pidió pulir la pantalla de Letras ("ahora son solo un texto estático y feo") y
preguntó si se podía hacer sincronizado con texto grande. Investigando, la app solo tenía
implementado el endpoint viejo de Subsonic (`getLyrics.view`, por artista/título, siempre texto
plano) — nunca se había integrado la extensión de OpenSubsonic `getLyricsBySongId` (por id de
canción, devuelve líneas con marca de tiempo cuando el servidor las tiene). Se verificó en vivo
contra el servidor real del usuario (Navidrome) que sí soporta esa extensión.

- **Capa API nueva** (`core/subsonic-api`): `StructuredLyrics.kt` (modelos `LyricsList`/
  `StructuredLyrics`/`LyricsLine`), `GetLyricsBySongIdResponse.kt`, y el endpoint
  `getLyricsBySongId` en `SubsonicAPIDefinition.kt`. No se agregó a `ApiVersionCheckWrapper` a
  propósito — es una extensión de OpenSubsonic, no parte del protocolo versionado de Subsonic, así
  que no tiene sentido controlarla contra `SubsonicAPIVersions`; si el servidor no la soporta la
  llamada simplemente falla y el fallback se encarga.
- **Dominio** (`core/domain/Lyrics.kt`): `Lyrics` ganó `synced: Boolean` y `lines: List<LyricsLine>`
  (antes solo tenía un `text` plano). `APILyricsConverter.kt` sabe convertir tanto la respuesta
  vieja (`Lyrics` → texto plano) como la nueva (`StructuredLyrics`/`LyricsList` → líneas con
  tiempos, prefiriendo la entrada sincronizada si el servidor devuelve varias).
- **Fallback en dos pasos** (`LyricsFragment.kt`): primero intenta `getLyricsBySongId`; si el
  servidor no la soporta, falla, o no devuelve líneas, cae automáticamente al `getLyrics` viejo
  (por artista/título) y separa el texto plano en líneas igual — así la letra siempre se ve grande
  y por líneas, tenga o no sincronía.
- **UI en líneas, no un bloque de texto** (`lyrics.xml`, `lyrics_line_item.xml`,
  `LyricsLineAdapter.kt` nuevo): la pantalla pasó de un `TextView` único dentro de un
  `NestedScrollView` a un `RecyclerView` con una fila grande y centrada por línea. Cuando hay
  sincronía real, un loop de 300ms (mismo patrón del executor de 500ms que ya usa `PlayerFragment`
  para la barra de progreso) calcula la línea activa según `mediaPlayerManager.playerPosition` y la
  resalta (`TextAppearance.Material3.HeadlineSmall`, negrita, opaca) mientras atenúa el resto
  (`TitleMedium`, alfa 0.6), haciendo scroll automático para mantenerla centrada — estilo karaoke.
  Sin sincronía, todas las líneas se ven igual de grandes pero sin resaltado ni scroll automático.
- **Indicador de sincronía, dos rondas de ajuste con el usuario**: preguntó cómo saber si una
  letra está sincronizada sin depender de notar el resaltado en movimiento.
  1. Primera versión: etiqueta de texto en mayúsculas ("SYNCED LYRICS", reusando
     `Ultrasonic.AllCapsLabel`) debajo del artista, visible solo cuando había timestamps. El
     usuario la encontró "grotesca" y pidió un ícono chico (check/X), mandando de referencia una
     captura de Spotify.
  2. Reemplazada por un ícono de 16dp (`ic_lyrics_synced.xml`/`ic_lyrics_unsynced.xml`, paths
     estándar de Material "check"/"close"), **siempre visible** una vez carga la letra — no solo
     cuando está sincronizada, para eliminar del todo la ambigüedad original ("no sincronizada"
     vs. "todavía cargando" se veían igual de vacías antes). Check en `colorPrimary`, X en
     `colorOnSurfaceVariant` (mate, no es un error).
  3. El usuario aceptó el ícono pero pidió reubicarlo: en vez de una fila aparte debajo del
     artista, a la derecha del nombre de la canción, en la misma línea. `lyrics_title` pasó a
     compartir una fila horizontal con el ícono (`layout_weight="1"` + `ellipsize="end"` +
     `singleLine="true"` para no chocar con el ícono en títulos largos), y se sacó la etiqueta de
     texto ("Synced"/"Not synced") que la acompañaba — el ícono solo, contextual junto al título,
     ya comunica el estado sin necesitar texto; ese texto pasó a `contentDescription` para
     accesibilidad.
- **Acceso directo desde el Player**: se agregó un tercer botón a la fila secundaria de controles
  (`player_secondary_controls.xml`, junto a guardar-playlist y cola) que navega directo a Letras
  de la canción actual, reusando el ícono `ic_library` ya usado para la misma función en el menú
  superior. Los 3 botones ahora están centrados como grupo con espacio uniforme entre ellos
  (`layout_marginHorizontal="18dp"` cada uno) en vez del patrón anterior de 2 botones a los
  extremos con un spacer flexible en el medio. La navegación real vive en una función compartida
  `PlayerFragment.navigateToLyrics(track)`, reusada tanto por el botón nuevo (con la canción
  actual) como por el ítem de menú viejo (con la canción del row long-presseado en la cola) —
  antes esa lógica solo existía inline en el `when` del menú. En modo offline, el botón muestra un
  toast ("Lyrics are not available in offline mode") en vez de navegar, ya que la pantalla de
  Letras siempre necesita red.
- **Bug de arrastre del SeekBar, encontrado al mismo tiempo** (no relacionado a las letras, lo
  reportó el usuario probando el Player): `updateSeekBar()` corre cada 500ms vía el mismo executor
  que ya actualiza la posición, y pisaba `progressBar.progress` sin chequear si el usuario tenía el
  dedo en la barra — al intentar retroceder, el thumb se "teletransportaba" de vuelta hacia
  adelante antes de soltar, hacía casi imposible retroceder y se sentía "bugueado". Se agregó
  `isSeekBarDragging` (true en `onStartTrackingTouch`, false en `onStopTrackingTouch` después de
  aplicar el seek) y `updateSeekBar()` ahora se salta la actualización de posición/texto/estado
  habilitado mientras ese flag está activo. Confirmado arreglado por el usuario en dispositivo real.
- **Segundo bug del Player, más serio, encontrado después**: el usuario reportó que al entrar a
  Letras y volver, los botones (play/pause/prev/next/etc.) y la barra de progreso dejaban de
  responder por completo, sin ningún error visible. Causa real: `PlayerFragment` implementaba
  `CoroutineScope by CoroutineScope(Dispatchers.Main)` — un delegado creado **una sola vez** al
  construirse la instancia del fragment, pero cancelado (`cancel(...)`) en cada `onDestroyView()`
  (que se dispara al navegar a cualquier otra pantalla, no solo Letras). Como la instancia del
  fragment sobrevive en el backstack de Nav Component aunque su vista se destruya y se recree, al
  volver ese `CoroutineScope` quedaba cancelado **para siempre** — y absolutamente todos los
  botones del Player usan `launch { ... }` sobre ese mismo scope para ejecutar sus acciones
  (`mediaPlayerManager.play()`/`.pause()`/`.seekToNext()`/etc.), así que después de un primer
  ida-y-vuelta a cualquier pantalla, cada tap silenciosamente no hacía nada (una corrutina
  lanzada sobre un scope cancelado no corre y no lanza excepción, por eso no había nada en el
  log). **Esto no lo introdujo el trabajo de Letras de esta sesión — era un bug latente
  preexistente que cualquier navegación fuera-y-vuelta del Player ya disparaba**, simplemente
  nunca se había notado porque antes no había una forma tan directa y repetida de ir y volver
  (el acceso directo a Letras lo hizo mucho más frecuente). Arreglado reemplazando el delegado
  fijo por una implementación manual de `CoroutineScope` respaldada por un campo `mainScope`
  reasignable: `mainScope = CoroutineScope(Dispatchers.Main)` ahora se ejecuta en cada
  `onCreateView()` (no solo una vez), y `coroutineContext` se resuelve dinámicamente contra
  `mainScope` en cada acceso — así ningún `launch { ... }` existente en el archivo necesitó
  tocarse, todos siguen leyendo el scope "vivo" actual automáticamente. Confirmado arreglado en
  dispositivo real: la posición volvió a avanzar sola después de reproducir tras un viaje a
  Letras y de vuelta, algo que antes quedaba congelado indefinidamente.
- **Tests nuevos**: `SubsonicApiGetLyricsBySongIdTest.kt` (contrato API, parseo de líneas
  sincronizadas + parámetro `id`) y ampliación de `APILyricsConverterTest.kt` (conversión de
  `StructuredLyrics`, preferencia por la entrada sincronizada al elegir entre varias, `null` cuando
  la lista viene vacía) — mismo patrón de pruebas de contrato ya usado para `getArtistInfo2`/
  `getTopSongs` esta sesión.

Verificado en dispositivo físico contra el servidor Navidrome real del usuario, en dos rondas:
primero con una canción sin datos de sincronía (letra grande y centrada, ícono X + "Not synced",
sin resaltado — comportamiento correcto), y después con una canción que sí tenía LRC real en el
servidor: el ícono de check + "Synced" apareció, y la línea activa se vio resaltada (más grande,
negrita, blanca) mientras el resto quedaba atenuado — confirmando en vivo que el resaltado
karaoke funciona de punta a punta, no solo el fallback a texto plano. Los 3 botones de la fila
secundaria del Player también se ven centrados y parejos.

Archivos: `core/subsonic-api/.../models/StructuredLyrics.kt`,
`core/subsonic-api/.../response/GetLyricsBySongIdResponse.kt`, `SubsonicAPIDefinition.kt`,
`core/domain/Lyrics.kt`, `APILyricsConverter.kt`, `MusicService.kt`, `RESTMusicService.kt`,
`CachedMusicService.kt`, `OfflineMusicService.kt`, `navigation_graph.xml`, `lyrics.xml`,
`ic_lyrics_synced.xml`, `ic_lyrics_unsynced.xml`,
`lyrics_line_item.xml`, `LyricsLineAdapter.kt`, `LyricsFragment.kt`,
`player_secondary_controls.xml`, `PlayerFragment.kt`, `strings.xml`,
`SubsonicApiGetLyricsBySongIdTest.kt`, `APILyricsConverterTest.kt`.

## Player: pulido final + auditoría de consistencia visual

Se completó el segundo paso del orden visual acordado (pulido final del Player) y, en la misma
pasada, el tercer paso (auditoría explícita de consistencia entre las pantallas ya migradas).

- **Composición más compacta** (`current_playing.xml`, `player_media_info.xml`,
  `player_slider.xml`, `media_buttons.xml`, `player_secondary_controls.xml`): la portada ganó
  espacio útil (margen 36→28dp) y el panel inferior perdió aire acumulado entre secciones. Título,
  artista, barra, tiempos y las dos filas de controles usan ahora un ritmo consistente; el título
  pasó a `TitleLarge`, los tiempos a `BodySmall` con `colorOnSurfaceVariant`, play/pause bajó de
  48/74dp a 44/64dp y las utilidades quedaron en touch targets de 36dp con separación menor.
- **Toolbar limpia** (`PlayerFragment.kt`, `nowplaying.xml`): el título dejó de mostrar el nombre
  técnico del build (`ultrasonic-test`) y ahora dice `Now Playing`. Se quitó el botón de cola
  duplicado de la toolbar porque la cola ya tiene acceso explícito permanente en la fila
  secundaria; "Guardar playlist" pasó al overflow por la misma razón (el `+` inferior ya es el
  acceso visible). Se eliminó también la rama muerta del handler del menú retirado.
- **Auditoría transversal**: se compararon Home, Artist Detail, Album Detail, Player, Playlists y
  Downloads tanto por layouts como en el Pixel 7. Se conservaron diferencias semánticas válidas
  (check de descarga pasivo en canciones vs. botón de descargar/eliminar en Playlists; no se añadió
  otro estado a las filas Popular). La discrepancia objetiva encontrada fue un radio de tarjeta de
  5dp en cuatro layouts creados en rondas distintas; se unificó al baseline de 4dp en
  `grid_item_playlist.xml`, `list_item_playlist.xml`, `list_item_downloaded_album.xml` y
  `server_row.xml`.
- **Pruebas reales pendientes desde rondas anteriores, ahora hechas**: en el Pixel 7 se recorrieron
  todos los grupos de Settings y el back de la navegación de dos niveles; se alternó y restauró un
  switch; Equalizer abrió y volvió; Downloads renderizó álbumes descargados reales y abrió un
  detalle local; la cola abrió desde el botón inferior y el menú largo mostró `Favorite` y `Rate`
  sin la estrella en la fila. No se confirmó una eliminación ni se cambió un rating porque eso
  habría alterado datos reales del usuario. Sin crashes ni `FATAL EXCEPTION` en logcat.

Archivos: `current_playing.xml`, `player_media_info.xml`, `player_slider.xml`, `media_buttons.xml`,
`player_secondary_controls.xml`, `nowplaying.xml`, `PlayerFragment.kt`,
`grid_item_playlist.xml`, `list_item_playlist.xml`, `list_item_downloaded_album.xml`,
`server_row.xml`.

## Superficie estrictamente musical: Bookmarks fuera + videos filtrados de Search

Se aplicó a Bookmarks el mismo criterio que ya se había usado para Podcasts, Video, Chat y
Shares: **fuera de la superficie del producto, sin borrar la infraestructura interna**. Esto evita
una refactorización riesgosa de modelos/API/descargas compartidas y mantiene más sencilla una
futura integración de cambios upstream.

- `navigation_drawer.xml`: se retiró Bookmarks del drawer.
- `nowplaying.xml` + `PlayerFragment.kt`: se retiraron `Set Bookmark`/`Delete Bookmark` del
  overflow del Player y toda su rama de UI/ejecución. El endpoint y `BookmarksFragment` se
  conservan internamente, pero ya no existe una ruta visible hacia ellos.
- `NavigationActivity.kt` + `navigation_graph.xml`: se eliminó la acción global y el manejo de
  navegación de Bookmarks, junto con referencias dinámicas antiguas a menús de Chat/Shares/
  Podcasts/Video que ya no existen en el drawer. Esos destinos internos permanecen compilables,
  pero dejan de tratarse como destinos top-level de la aplicación.
- `SearchFragment.kt`: la API puede devolver `Track.isVideo` mezclado con canciones aunque la
  pantalla Video esté oculta. Ahora esos resultados se filtran antes de construir la lista y antes
  del autoplay, evitando que Search reintroduzca un acceso accidental al reproductor de video.
- La lista de capacidades de `Test Connection` se mantiene: informa técnicamente qué soporta el
  servidor, pero no ofrece navegación ni activa esas funciones en la experiencia del oyente.

Compilación y pruebas unitarias verificadas; drawer, Player y Search revisados en el Pixel 7.

Archivos: `navigation_drawer.xml`, `nowplaying.xml`, `navigation_graph.xml`,
`NavigationActivity.kt`, `PlayerFragment.kt`, `SearchFragment.kt`.

## Navegación principal inferior + hub “Tu biblioteca”

Se reemplazó el drawer como navegación diaria por cuatro destinos persistentes y predecibles:
**Home, Library, Search y Downloads**. El mini-player permanece inmediatamente encima de la
barra y sigue siendo la entrada al Player completo; en Player y pantallas auxiliares la barra se
oculta para no competir con la navegación contextual. Downloads también se oculta cuando no hay
un servidor activo disponible.

La selección y administración de servidores no quedó perdida con el drawer: el icono de
biblioteca de la toolbar abre el nuevo hub **Your library**, muestra el servidor activo y ofrece
**Switch library**, **Add library**, **Settings** y **About**. Cambiar y agregar reutilizan las
pantallas existentes, incluyendo el argumento `index = -1` requerido para crear un servidor.
Search ahora es un destino principal y su campo expandido se muestra únicamente dentro de esa
pestaña. También se retiró de `NavigationActivity` todo el estado, callbacks y configuración
muertos que pertenecían al drawer.

Verificación: compilación Kotlin, pruebas de `core:subsonic-api`, pruebas unitarias de
`ultrasonic` y `assembleDebug` completaron correctamente. El lint global continúa fallando por
la deuda histórica del repositorio (1.683 errores fuera del alcance de esta migración), no por
errores de compilación de la navegación nueva.

Archivos: `navigation_activity.xml`, `bottom_navigation.xml`, `library_hub_action.xml`,
`library_hub_popup.xml`, `NavigationActivity.kt`, `strings.xml`.

## Home y Library sin header global pesado

La migración de navegación se ajustó a la dirección visual de Taki: Home ya no reserva una
toolbar completa para repetir el nombre de la pestaña. El saludo abre directamente el contenido
y comparte su fila con un botón de tres puntos; ese overflow conserva el acceso a biblioteca
actual, cambiar/agregar biblioteca, Settings y About. Los accesos rápidos quedan inmediatamente
debajo, como en el mockup de Home.

Library también dejó de depender de la toolbar global: ahora tiene un encabezado integrado con
el título **Library** y una acción textual **Your library**, más reconocible que el anterior icono
aislado. Search y las pantallas contextuales siguen mostrando toolbar porque allí contiene el
campo o la navegación de regreso. El popup acepta ahora cualquier vista como ancla para compartir
la misma lógica entre Home, Library y los destinos que aún usan toolbar.

Verificado en Pixel 7: Home sin toolbar, overflow funcional, Library sin toolbar con acceso
explícito y restauración correcta de toolbar + campo expandido al entrar en Search. Pruebas
unitarias y `assembleDebug` completaron correctamente.

Archivos: `home_fragment.xml`, `HomeFragment.kt`, `primary.xml`, `MainFragment.kt`,
`NavigationActivity.kt`.

## About actualizado a la identidad Taki

La pantalla About dejó de presentarse como Ultrasonic: el encabezado usa **Taki** y la versión
actual, incorpora el lema **Your music, without distractions.** y reemplaza la descripción
original por una explicación breve del producto y su compatibilidad Subsonic/OpenSubsonic.
Ultrasonic permanece únicamente como atribución explícita al proyecto base y a sus contribuidores,
junto con la licencia GPLv3. Como todavía no existen URLs propias de Taki configuradas en el
repositorio, los dos enlaces existentes se etiquetan claramente como sitio y tracker del proyecto
original para no hacerlos pasar por canales oficiales de Taki.

Verificado visualmente en Pixel 7 y con `compileDebugKotlin` + `assembleDebug` correctos.

Archivos: `help.xml`, `AboutFragment.kt`, `strings.xml`.

## Inicio de versionado propio de Taki

Taki inicia su numeración pública beta en **0.1.0-beta** mediante `versionName`. Se conserva
`versionCode 131` para mantener la monotonía requerida por Android y permitir que instalaciones
existentes del fork puedan actualizarse sin tratar el paquete como una versión anterior.

Archivo: `ultrasonic/build.gradle`.

La redacción de About se refinó para explicar reproducción online/offline, compatibilidad con
Navidrome y servidores Subsonic/OpenSubsonic, y la atribución GPLv3 a Ultrasonic. El botón se
renombró internamente a **Visit website**, pero permanece oculto mientras Taki no tenga una página
pública propia; así una versión distribuida no envía a usuarios a un repositorio privado o a una
página que represente otro proyecto.

## Search, Downloads y Settings sin toolbar global

Se completó el mismo tratamiento de encabezado integrado aplicado previamente a Home/Library:

- **Search** dejó de usar un `SearchView` inflado en el menú de la Activity. Ahora posee un campo
  redondeado dentro de su propio contenido, conserva búsqueda en vivo con debounce, submit,
  historial de sugerencias y entrada por intents/voz.
- **Downloads** usa un layout propio con título integrado y deja de repetirlo dentro de una barra
  global.
- **Settings** envuelve la lista principal en un encabezado de contenido. Los grupos internos sí
  restauran la toolbar con flecha Atrás y el nombre del grupo, porque allí cumple una función
  contextual real.

También se retiraron el proveedor de menú de Search y la acción de biblioteca en toolbar que ya no
tenían destinos visibles. Verificado en Pixel 7: los tres niveles principales carecen de toolbar,
una consulta real devolvió artistas/álbumes, y un grupo de Settings restauró correctamente toolbar
y navegación. `compileDebugKotlin` y `assembleDebug` completaron correctamente.

Archivos: `NavigationActivity.kt`, `SearchFragment.kt`, `DownloadsFragment.kt`,
`SettingsFragment.kt`, `search.xml`, `downloads.xml`, `settings_fragment.xml`.

## Player inmersivo sin toolbar global

El Player completo dejó de mostrar la franja compartida con “Now Playing”. La navegación Atrás y
el overflow ahora viven dentro del contenido, como controles discretos sobre la composición; el
overflow reutiliza todas las acciones existentes (artista/álbum, guardar/limpiar cola, letras,
ecualizador, jukebox y pantalla encendida). La variante horizontal recibió los mismos controles.

Durante la prueba apareció un toast técnico `Fragment HomeFragment…`: la carga de Home utilizaba
`viewModelScope` y podía intentar actualizar su vista después de abrir el Player. Se cambió a
`viewLifecycleOwner.lifecycleScope`, cancelando correctamente el trabajo cuando la vista deja de
existir. Verificado en Pixel 7 sin título global, sin barra inferior y con overflow funcional;
`assembleDebug` correcto.

Archivos: `NavigationActivity.kt`, `PlayerFragment.kt`, `HomeFragment.kt`,
`current_playing.xml`, `layout-land/current_playing.xml`, `ic_arrow_back.xml`, `strings.xml`.

## Lyrics sin toolbar global

La pantalla de letras continúa el patrón inmersivo del Player: eliminó la franja compartida e
integró Atrás en la cabecera que ya contiene canción, artista e indicador de sincronización. La
carga de letras, el resaltado karaoke y el desplazamiento automático no se modificaron.

Archivos: `NavigationActivity.kt`, `LyricsFragment.kt`, `lyrics.xml`.

Al probar la navegación posterior se corrigió además un crash en `TrackCollectionFragment`: su
proveedor de menú todavía intentaba ocultar `action_search`, aunque esa acción global ya no existe.
La preparación ahora sólo configura su propia acción Play all y tolera que aún no esté inflada.

También se corrigió la apertura del Player en horizontal: `layout-land/current_playing.xml` no
incluía `player_secondary_controls`, aunque `PlayerFragment` requiere Queue, Lyrics y Save
Playlist en ambas variantes. El bloque secundario ahora está presente también en landscape.

## Identidad visual Taki aplicada al producto

Se sustituyó el tema dinámico de Android por una paleta Taki fija basada en el manual de marca:
Lima `#D7FF3F`, progreso `#A9C92F`, fondo `#090A08`, variación `#0D0F0B`, superficie
`#171A14`, marfil `#F5F7EF`, gris `#A8AEA0` y pista `#30352A`. Los tokens cubren Material,
barras del sistema, preferencias, paneles, controles y widget; la navegación inferior usa lima
sólo para el destino activo y gris para el resto.

El launcher adaptive icon ahora usa el símbolo t/nota oficial en lima sobre negro, con variante
monocromática. El nombre visible se fijó como Taki en aplicación, servicios, widget, Search,
navegación y Settings sin renombrar el paquete/clases internos, preservando compatibilidad.

El Player horizontal fue reconstruido como una composición limpia de dos columnas: portada a la
izquierda e información/progreso/transporte/acciones a la derecha. Se eliminaron el fondo de portada
ampliado y las cinco estrellas heredadas, manteniendo Atrás, overflow, favorito, cola y letras.

## Acento Taki suavizado y navegación de Playlists corregida

La paleta compartida sustituyó la lima fluorescente por un acento más calmado: principal
`#B7D63C`, presionado/activo secundario `#91AD30`, progreso `#7C9229` (aprox. 68% de
intensidad), contenedor seleccionado `#293217` y contenido sobre acento `#11130D`. Los fondos y
neutros permanecen iguales. Navegación seleccionada y Play conservan el acento completo; Atrás,
overflow, favorito y Previous/Next usan el activo secundario. El progreso usa su token discreto y
ambos tiempos conservan el mismo gris secundario. Chips/botones tonales toman el contenedor oscuro,
no un relleno verde. El launcher referencia el token central y no quedan valores antiguos directos.

Playlists dejó de restaurar la toolbar global. Además, la barra inferior ahora maneja la reselección
de cualquiera de sus pestañas: si un destino secundario se abrió mientras Home, Library, Search o
Downloads seguía marcado, volver a pulsar esa pestaña retorna correctamente a su raíz.

## Library como índice de colección

Home redujo sus accesos rápidos a Playlists, Albums, Artists y Songs; Genres dejó de ocupar el quinto
chip. Library dejó de replicar los listados mediante tabs y ahora funciona como un índice estable:

- **Your music:** Liked Songs y Playlists.
- **Collection:** Albums, Artists, Songs y Genres.

Liked Songs reutiliza el filtro `getStarred` existente y los demás elementos abren sus destinos ya
probados. “Your library” permanece separado para administrar servidores y ajustes. Se retiraron el
ViewPager, tabs, filtro y adaptador internos que quedaron obsoletos con este cambio.

## Primer pase de la guía visual y de lenguaje Taki

Se adoptó `docs/TAKI_GUIA_VISUAL_Y_LENGUAJE.md` como fuente de verdad para el producto y se
aplicó su primer bloque de mayor impacto. La paleta central ahora define exactamente los roles
`background`, `surfaceLow`, `surface`, `surfaceHigh`, `selectedNeutral`, `onSurface`,
`onSurfaceVariant`, `outline`, `divider`, `error` y `onErrorContainer`; el tema Material 3 los
consume sin depender de colores dinámicos ni valores verdes por defecto.

La navegación inferior dejó de usar verde: destino activo blanco sobre cápsula neutral e inactivos
grises. Library cambió encabezados verdes/mayúsculos por sentence case neutral y sustituyó las
píldoras oliva por filas transparentes. El selector muestra el nombre configurado de la colección
(o “Downloaded music” en modo local), con iconografía y descripción de colección en lugar de
servidor. Los accesos rápidos de Home usan superficies neutrales.

Now Playing reserva el verde para estados reales: Atrás, overflow y controles anterior/siguiente
son neutrales; Play/Pause es la única acción principal de alto contraste, blanca con icono oscuro.
El mini reproductor usa la superficie opaca del sistema y los filtros cumplen un objetivo táctil
mínimo de 48dp. About adopta “Visit website” y “Report a problem”.

## Servidor demo retirado del producto

Taki dejó de empaquetar y ofrecer automáticamente la colección demo heredada. Se eliminaron la
URL, usuario, contraseña, configuración `DEMO_SERVER_CONFIG`, método `addDemoServer()` y textos
traducidos asociados. El primer arranque ahora explica brevemente que debe conectarse una colección
y ofrece `Add collection` / `Agregar colección`, que abre directamente el formulario nuevo; `Not
now` mantiene la app en modo de música descargada. No se borran configuraciones existentes de la
base de datos del usuario.
