# Taki: guía visual y de lenguaje

## Estado y propósito

Este documento define una **dirección de diseño y contenido** para Taki. Sirve como referencia para auditar y corregir la interfaz, pero **no confirma que todos los componentes, estados o comportamientos descritos estén implementados**.

Antes de aplicar cada cambio se debe comprobar:

1. Qué recurso, tema o componente controla actualmente el elemento.
2. Si el cambio afecta todas las pantallas o solo un contexto.
3. Si el comportamiento asociado existe realmente.
4. Si el texto procede de `strings.xml` y tiene traducciones equivalentes.
5. Si el resultado conserva accesibilidad, legibilidad y eficiencia.

Las modificaciones implementadas y verificadas deben quedar documentadas en el mensaje del commit correspondiente. Esta guía no sustituye el historial técnico del proyecto.

---

## 1. Intención de la experiencia

Taki debe sentirse como un reproductor íntimo, moderno y ligero para colecciones musicales personales o compartidas.

> **Your music, without distractions.**

La interfaz debe proteger tres cualidades:

- **Calma:** superficies oscuras y neutras; pocas señales compiten por atención.
- **Claridad:** cada pantalla tiene una jerarquía evidente y una acción principal reconocible.
- **Cercanía:** el usuario interactúa con su música y sus colecciones, no con servidores, APIs o procesos técnicos.

La modernización visual no debe sacrificar la rapidez y eficiencia heredadas de Ultrasonic.

---

## 2. Principio rector del color

El verde Taki es una firma, no el color predeterminado de todos los controles.

Debe reservarse para:

- acciones primarias excepcionales;
- estados activados por el usuario, como favorito o repetición;
- progreso o selección cuando comuniquen información útil;
- identidad de marca en logo, splash y detalles pequeños.

No debe utilizarse sistemáticamente en:

- títulos y encabezados;
- navegación habitual;
- todos los controles del reproductor;
- fondos completos de listas o chips;
- acciones secundarias como volver, abrir un menú o avanzar de pantalla.

> Si todo utiliza el acento, nada parece importante.

---

## 3. Paleta oscura propuesta

| Token conceptual | Hex | Uso |
| --- | --- | --- |
| `background` | `#090B09` | Fondo general de las pantallas |
| `surfaceLow` | `#111410` | Navegación y superficies discretas |
| `surface` | `#171A16` | Mini reproductor, tarjetas y paneles |
| `surfaceHigh` | `#1D211B` | Elementos elevados o presionados |
| `selectedNeutral` | `#FFFFFF14` | Indicadores y selecciones neutras, 8 % blanco |
| `onSurface` | `#F1F2ED` | Texto e iconos principales |
| `onSurfaceVariant` | `#A7AAA2` | Texto e iconos secundarios |
| `outline` | `#FFFFFF1F` | Bordes relevantes y discretos |
| `divider` | `#FFFFFF0D` | Separadores de baja presencia |
| `accent` | `#B7D63C` | Verde de marca y estados activos |
| `accentPressed` | `#91AD30` | Estado presionado del acento |
| `accentContainer` | `#293217` | Fondo verde de baja intensidad |
| `onAccent` | `#11130D` | Contenido sobre el verde principal |
| `error` | `#FFB4AB` | Error sobre fondo oscuro |
| `onErrorContainer` | `#FFDAD6` | Contenido de mensajes de error |

### Reglas técnicas

- Definir los colores como roles semánticos centralizados; evitar valores hexadecimales directos en layouts y componentes.
- No reutilizar `accent` como color genérico de texto o iconos.
- Mantener al menos `4.5:1` de contraste en texto pequeño y `3:1` en texto grande e iconos relevantes.
- No comunicar estados únicamente mediante color: combinar forma, icono, etiqueta o peso visual.
- Validar en pantalla OLED física, brillo bajo, brillo alto y modo de ahorro de batería.
- Evitar que el color dinámico de Android sustituya la identidad fija de Taki, salvo que exista una decisión explícita para ofrecerlo como opción.

---

## 4. Jerarquía de superficies

La profundidad debe expresarse mediante pequeñas diferencias tonales, no mediante grandes bloques verdes.

1. `background`: lienzo principal.
2. `surfaceLow`: navegación y agrupaciones permanentes.
3. `surface`: tarjetas, mini reproductor y paneles interactivos.
4. `surfaceHigh`: estado presionado, modal o elemento temporalmente elevado.

Las portadas deben aportar la mayor parte del color de las pantallas musicales.

---

## 5. Navegación inferior

### Dirección

- Fondo: `surfaceLow`.
- Destino activo: cápsula `selectedNeutral`.
- Icono activo: variante rellena en `onSurface`.
- Etiqueta activa: `onSurface`, peso medio.
- Iconos y etiquetas inactivas: `onSurfaceVariant`.
- No usar verde en la navegación inferior.

El estado activo debe reconocerse mediante tres señales: cápsula, icono relleno y mayor contraste. El color de acento no es necesario.

### Destinos

| Inglés | Español |
| --- | --- |
| Home | Inicio |
| Library | Biblioteca |
| Search | Buscar |
| Downloads | Descargas |

La navegación inferior debe aparecer solo en destinos principales. Las pantallas secundarias utilizan navegación de retorno y no necesitan repetirla.

---

## 6. Encabezado de colección activa

La etiqueta situada junto al título de `Library` identifica la conexión o colección activa. **No debe eliminarse**, pero debe evitar términos técnicos y textos genéricos como `Your library`.

### Contenido

Mostrar el **nombre configurado para el servidor/conexión** exactamente como lo reconoce el usuario, por ejemplo:

- `Joseph’s Music`
- `Home Library`
- `Family Music`
- `Navidrome Home`

Internamente puede seguir siendo `server.name`, pero en la interfaz representa el nombre de la colección.

### Presentación

- Icono de biblioteca, discos o colección; evitar un icono de servidor.
- Texto en `onSurfaceVariant`, no verde.
- Flecha desplegable solo cuando exista la posibilidad de cambiar de colección.
- Descripción accesible: `Switch collection` / `Cambiar colección`.
- Si existe una sola colección y no hay acción disponible, mostrar únicamente el nombre, sin flecha ni apariencia de botón.
- Al tocar el selector, mostrar nombres de colecciones; reservar URL, protocolo y estado técnico para detalles o diagnóstico.

### Configuración

En nuevas conexiones, presentar el campo como:

| Inglés | Español |
| --- | --- |
| Collection name | Nombre de la colección |
| Choose a name you will recognize | Elige un nombre que puedas reconocer |

El sistema puede continuar almacenándolo como nombre de servidor. Se cambia el lenguaje de presentación, no necesariamente el modelo de datos.

---

## 7. Reglas por pantalla

### 7.1 Home

- Título y saludo en `onSurface`.
- Menú de opciones en `onSurfaceVariant`.
- Chips sin seleccionar en una superficie neutra.
- Chip seleccionado con `selectedNeutral`, texto blanco y peso medio.
- Encabezados de sección en blanco o gris claro; no verdes.
- Tarjetas discretas que permitan a las portadas dominar visualmente.
- Evitar aplicar un contenedor verde a categorías completas.

### 7.2 Library

- Encabezados como `Your music` y `Collection` en estilo de sección neutro y sentence case; evitar mayúsculas completas verdes.
- Reemplazar las píldoras verde oliva por filas transparentes o superficies neutras con radio moderado.
- Usar iconos secundarios grises.
- Emplear separadores sutiles si son necesarios para agrupar.
- Mostrar el nombre configurado de la colección en la esquina superior según la sección 6.

### 7.3 Mini reproductor

- Contenedor `surface`.
- Título `onSurface`; artista `onSurfaceVariant`.
- Controles en blanco y gris.
- Evitar colorear simultáneamente anterior, play y siguiente.
- Usar una línea verde fina en el borde superior como progreso, si resulta legible en el dispositivo.
- Reservar verde para un estado activo real o, como máximo, para una única acción primaria.

### 7.4 Now Playing

- Portada como elemento dominante.
- Volver y menú en `onSurfaceVariant`.
- Favorito inactivo con contorno gris; favorito activo en `accent`.
- Play como botón blanco relleno con icono oscuro, o como único control principal de alto contraste.
- Anterior y siguiente en `onSurface`.
- Shuffle y repeat en gris cuando están apagados; verde únicamente cuando están activos.
- Acciones secundarias inferiores en `onSurfaceVariant`.
- Barra restante `#FFFFFF12`.
- Barra reproducida fina y discreta; probar `accent` atenuado frente a blanco al 70 %.
- Tiempo transcurrido y restante con el mismo color secundario.

### 7.5 Settings

- Mantener grupos y acciones en superficies neutras.
- Usar verde únicamente para switches activados o selección efectiva.
- No presentar opciones técnicas que la app pueda resolver automáticamente.
- Separar preferencias cotidianas de `Advanced` / `Avanzado` y `Diagnostics` / `Diagnóstico`.

### 7.6 About

- Mantener la pantalla breve y centrada en la intención del producto.
- Usar `Visit website`, no `Visit webpage`.
- Usar `Report a problem`, más comprensible que `Report a bug` para público general.
- Mostrar versión propia de Taki, por ejemplo `0.1.0-beta`, y no continuar visualmente la numeración de Ultrasonic sin una decisión explícita.
- Mantener atribución a Ultrasonic y GPLv3.

---

## 8. Voz de Taki

Taki habla como un reproductor tranquilo que ayuda, no como un panel de administración.

### Características

- Directa y breve.
- Cotidiana, sin condescendencia.
- Explica qué puede hacer el usuario.
- No culpa al usuario, al servidor ni a la red.
- Muestra primero el mensaje humano; deja los detalles técnicos en `View diagnostics`.
- Utiliza sentence case.
- Evita signos de exclamación salvo acontecimientos positivos excepcionales.

### Fórmula para estados y errores

1. Qué está ocurriendo en lenguaje cotidiano.
2. Qué puede continuar haciendo el usuario.
3. Una o dos acciones concretas.
4. Diagnóstico técnico opcional y separado.

Ejemplo:

> **We couldn’t reach Joseph’s Music.**  
> You can keep listening to downloaded music.  
> `Try again` · `View diagnostics`

---

## 9. Vocabulario recomendado

### Conceptos principales

| Evitar en la experiencia normal | Inglés recomendado | Español recomendado | Uso técnico permitido |
| --- | --- | --- | --- |
| Server | Collection / nombre configurado | Colección / nombre configurado | Connection details, diagnostics |
| Server URL | Collection address | Dirección de la colección | Advanced setup |
| Server unavailable | Collection unavailable / We couldn’t reach… | Colección no disponible / No pudimos conectar con… | Diagnostics |
| Instance | Collection | Colección | Nunca, salvo diagnóstico técnico |
| Host | Address | Dirección | Diagnostics |
| Localhost | Downloaded music / This device | Música descargada / Este dispositivo | Nunca como acción de usuario |
| Offline mode | Listen offline / Downloaded music | Escuchar sin conexión / Música descargada | Ajustes avanzados si es indispensable |
| Cache | Temporary storage | Almacenamiento temporal | Diagnostics / advanced settings |
| Sync | Update collection | Actualizar colección | Technical logs |
| Transcode | Adjust audio quality | Ajustar calidad de audio | Diagnostics / advanced settings |
| API error | We couldn’t complete that action | No pudimos completar esa acción | Diagnostics |
| Credentials | Username and password | Usuario y contraseña | Technical documentation |
| Endpoint | Address | Dirección | Diagnostics only |

### Acciones

| Inglés | Español |
| --- | --- |
| Try again | Intentar de nuevo |
| Keep listening | Seguir escuchando |
| Listen offline | Escuchar sin conexión |
| View downloads | Ver descargas |
| View diagnostics | Ver diagnóstico |
| Copy diagnostics | Copiar diagnóstico |
| Report a problem | Reportar un problema |
| Switch collection | Cambiar colección |
| Update collection | Actualizar colección |
| Download | Descargar |
| Remove download | Eliminar descarga |

---

## 10. Mensajes recomendados

Los siguientes textos son propuestas. Deben vincularse únicamente a estados que la aplicación pueda detectar con fiabilidad.

### Sin conexión a internet

**English**

> **You’re offline.**  
> You can keep listening to downloaded music.

Acciones: `View downloads` y `Try again`.

**Español**

> **No tienes conexión.**  
> Puedes seguir escuchando la música descargada.

Acciones: `Ver descargas` e `Intentar de nuevo`.

### La colección no responde

**English**

> **We couldn’t reach {collectionName}.**  
> It may be temporarily unavailable. You can keep listening to downloaded music.

Acciones: `Try again` y `View diagnostics`.

**Español**

> **No pudimos conectar con {collectionName}.**  
> Puede que no esté disponible temporalmente. Puedes seguir escuchando la música descargada.

Acciones: `Intentar de nuevo` y `Ver diagnóstico`.

### Conexión privada o VPN desactivada

Solo mostrar este mensaje si la aplicación puede identificar ese caso. No asumir Tailscale.

**English**

> **A private connection may be required.**  
> Check the connection used to access {collectionName}, then try again.

**Español**

> **Puede que necesites una conexión privada.**  
> Revisa la conexión que utilizas para acceder a {collectionName} e inténtalo de nuevo.

### Descarga incompleta

**English**

> **Download paused.**  
> Taki will try again when the connection is available.

Acciones: `Try now` y `Cancel download`.

**Español**

> **Descarga pausada.**  
> Taki lo intentará de nuevo cuando haya conexión.

Acciones: `Intentar ahora` y `Cancelar descarga`.

### Archivo no reproducible

**English**

> **This song couldn’t be played.**  
> Try again or send the diagnostics to the person who manages the collection.

**Español**

> **No se pudo reproducir esta canción.**  
> Inténtalo de nuevo o envía el diagnóstico a quien administra la colección.

Acciones: `Try again` y `View diagnostics`.

### Conexión recuperada

Evitar interrumpir la reproducción con un modal. Si hace falta confirmación, usar un mensaje transitorio:

| Inglés | Español |
| --- | --- |
| Back online | Conexión restablecida |
| {collectionName} is available again | {collectionName} está disponible nuevamente |

---

## 11. Descargas y disponibilidad

La aplicación debe priorizar la música, no la procedencia del archivo.

- Usar `Downloaded` / `Descargada` para un estado persistente solicitado por el usuario.
- Usar `Available offline` / `Disponible sin conexión` cuando importe explicar el beneficio.
- Evitar mostrar `local`, `remote`, `cached` o `server copy` en las vistas normales.
- Si existe una copia descargada válida, la app debería preferirla automáticamente; esto es una dirección de producto que requiere verificación técnica.
- Los detalles de procedencia pueden mostrarse en información avanzada o diagnóstico.

Estados recomendados:

| Estado | Inglés | Español |
| --- | --- | --- |
| En cola | Waiting to download | Esperando para descargar |
| Descargando | Downloading | Descargando |
| Pausada | Download paused | Descarga pausada |
| Completa | Downloaded | Descargada |
| Error recuperable | Will try again | Se intentará de nuevo |
| Eliminación | Remove download | Eliminar descarga |

---

## 12. Diagnóstico y soporte

Los diagnósticos no deben aparecer directamente en el mensaje principal.

Flujo recomendado:

1. Mostrar un mensaje comprensible.
2. Ofrecer `View diagnostics`.
3. Mostrar en una segunda vista los datos técnicos disponibles.
4. Permitir `Copy diagnostics`.
5. Advertir y excluir contraseñas, tokens, claves y otros secretos.

La vista puede incluir, cuando estén disponibles:

- nombre de la colección;
- hora del error;
- estado de red;
- tipo general de conexión;
- operación fallida;
- código de respuesta;
- versión de Taki y Android;
- identificador anónimo del error.

No se debe prometer que el diagnóstico identifica automáticamente Tailscale, HTTPS, DNS o fallos del servidor si el código no puede diferenciarlos.

---

## 13. Consistencia editorial

- Utilizar sentence case: `Recently played`, no `RECENTLY PLAYED`.
- Preferir títulos de una a tres palabras.
- Usar verbos en botones: `Try again`, `Remove download`, `Copy diagnostics`.
- Evitar puntos finales en etiquetas y botones.
- Usar contracciones naturales en inglés: `You’re offline`, `We couldn’t connect`.
- No traducir nombres propios: Taki, Navidrome, Subsonic, OpenSubsonic y Ultrasonic.
- Mantener placeholders con nombre estable, por ejemplo `{collectionName}`.
- No concatenar fragmentos para construir frases traducidas; definir cada mensaje completo en `strings.xml`.
- Añadir comentarios de contexto para traductores cuando una palabra pueda ser ambigua.
- Verificar plurales mediante recursos `plurals` y no mediante concatenación.

---

## 14. Accesibilidad y controles

- Objetivo táctil mínimo: `48 × 48 dp`.
- Texto del cuerpo: no menor de `12 sp`; preferir `14–16 sp`.
- Contraste mínimo: `4.5:1` para texto pequeño y `3:1` para texto grande o iconos esenciales.
- Proporcionar `contentDescription` o etiquetas accesibles a iconos interactivos.
- No usar únicamente el verde para distinguir activo/inactivo.
- Respetar escalado de fuentes y probar textos largos en español y alemán.
- Probar con TalkBack, Accessibility Scanner y Compose UI Check cuando corresponda.

---

## 15. Orden recomendado de implementación

1. Centralizar los tokens de color y eliminar colores directos.
1b. Centralizar los tokens de dimensión, tipografía e iconografía (sección 17) y migrar a ellos
    pantalla por pantalla en lugar de repetir valores `dp`/`sp`.
2. Neutralizar la navegación inferior.
3. Corregir filas y encabezados de Library.
4. Mostrar el nombre configurado de la colección.
5. Neutralizar chips y acciones secundarias de Home.
6. Reorganizar la jerarquía del mini reproductor.
7. Reorganizar los controles de Now Playing.
8. Auditar Settings y About.
9. Centralizar textos en recursos y aplicar el vocabulario recomendado.
10. Revisar estados vacíos, carga, conexión, descargas y errores.
11. Probar contraste, tamaños táctiles y escalado de texto.
12. Comparar capturas de todas las pantallas lado a lado antes de cerrar el pase.

---

## 16. Criterios de aceptación

El pase se considera completo cuando:

- el verde aparece únicamente con significado semántico;
- la navegación activa se reconoce sin verde;
- las portadas son la principal fuente de color;
- Home, Library, Player y Settings comparten los mismos roles de superficie;
- el nombre configurado de la colección aparece en lugar de `Your library`;
- ninguna pantalla principal utiliza `server`, `host`, `instance`, `localhost`, `cache`, `API` o `transcode` sin necesidad;
- los errores ofrecen una acción útil y diagnóstico opcional;
- todos los textos visibles proceden de recursos traducibles;
- no se introducen regresiones funcionales;
- las pantallas se verifican en un dispositivo físico.

---

## 17. Tokens compartidos (dimensiones, tipografía, iconos)

Los roles de **color** ya están centralizados en `colors.xml` / `themes.xml` (secciones 3–4).
Esta sección añade la capa que faltaba: **espaciado, radios, jerarquía tipográfica, tamaños de
icono y objetivos táctiles** como recursos únicos. El objetivo es que una pantalla **elija un
token**, no que invente un valor `dp`/`sp`.

### 17.1 Archivos

| Recurso | Contenido |
| --- | --- |
| `res/values/dimens.xml` | Escalas de espaciado, radios, iconos, objetivos táctiles, alturas de fila, huellas de portada, elevación/borde. |
| `res/values/type.xml` | Seis apariencias `TextAppearance.Taki.*` (jerarquía tipográfica). |
| `res/values/styles.xml` | `ShapeAppearanceOverlay.Taki.Small` / `.Medium` (radios como forma, para `ShapeableImageView` y `MaterialCardView`). |

`dimens.xml` y `type.xml` llevan `tools:ignore="UnusedResources"` a propósito: son una base
compartida que las tareas de UI (#4/#5/#7/#11) consumen de forma incremental; la escala se
define completa desde el principio para que no tenga huecos.

### 17.2 Espaciado

Base 4 dp. Todo margen, relleno o hueco toma uno de estos:

| Token | Valor | Uso |
| --- | --- | --- |
| `@dimen/space_xxs` | 2 dp | Separación título ↔ subtítulo. |
| `@dimen/space_xs` | 4 dp | Huecos mínimos internos. |
| `@dimen/space_sm` | 8 dp | Huecos entre elementos, márgenes de chip. |
| `@dimen/space_md` | 12 dp | Relleno de fila, separación portada ↔ texto. |
| `@dimen/space_lg` | 16 dp | Margen de borde de pantalla estándar. |
| `@dimen/space_xl` | 24 dp | Separación entre secciones / estanterías. |
| `@dimen/space_2xl` | 32 dp | Bloques grandes, estados vacíos. |

Alias semánticos (mantener sincronizados con las pantallas):

| Alias | Apunta a | Uso |
| --- | --- | --- |
| `@dimen/space_screen_horizontal` | `space_lg` (16 dp) | El único margen lateral del contenido principal. |
| `@dimen/space_section_gap` | `space_xl` (24 dp) | Hueco vertical entre estanterías de Home / secciones de detalle. |
| `@dimen/space_text_tight` | `space_xxs` (2 dp) | Hueco entre un título y su línea de metadatos inmediata. |

### 17.3 Radios de esquina

| Token | Valor | Uso |
| --- | --- | --- |
| `@dimen/radius_xs` | 4 dp | Chips y elementos en línea muy pequeños. |
| `@dimen/radius_sm` | 8 dp | Miniaturas de portada, tarjetas pequeñas. También como forma: `@style/ShapeAppearanceOverlay.Taki.Small`. |
| `@dimen/radius_md` | 12 dp | Tarjetas, hojas, paneles. Forma: `@style/ShapeAppearanceOverlay.Taki.Medium`. |
| `@dimen/radius_lg` | 20 dp | Paneles grandes / hero. |

El panel del reproductor (`@dimen/player_panel_corner_radius`) ya apunta a `@dimen/radius_lg`
(20 dp) desde #7 (§18.4). Círculos completos: `@style/ShapeAppearanceOverlay.Ultrasonic.Circle`.

### 17.4 Jerarquía tipográfica

Seis roles, cada uno una capa fina sobre una apariencia de Material 3 (se **hereda** la escala y
las métricas, no se duplican). Cada rol fija el peso y el color de énfasis
primario/secundario. Preferirlos a `TextAppearance.Material3.*` directo y a los estilos
heredados `Ultrasonic.PrimaryText` / `Ultrasonic.SecondaryText` (solo tipografía).

| Rol | Padre M3 | Color | Uso |
| --- | --- | --- | --- |
| `TextAppearance.Taki.Hero` | HeadlineSmall | `onSurface` | Título de Now Playing, encabezados de detalle prominentes, saludo de Home. |
| `TextAppearance.Taki.Title` | TitleMedium | `onSurface` | Título de fila / encabezado (álbum, pista, playlist). |
| `TextAppearance.Taki.TitleSmall` | TitleSmall | `onSurface` | Título compacto en tarjetas de rejilla / carrusel. |
| `TextAppearance.Taki.Body` | BodyMedium | `onSurface` | Texto de cuerpo primario. |
| `TextAppearance.Taki.Caption` | BodySmall | `onSurfaceVariant` | Línea de artista, metadatos, duraciones. Peso ligero (absorbe `Ultrasonic.SecondaryText`). |
| `TextAppearance.Taki.SectionHeader` | TitleSmall | `onSurfaceVariant` | Encabezados de estantería/grupo. Sentence case, nunca verde. Sustituye a `Ultrasonic.AllCapsLabel`. |

### 17.5 Iconos

Solo para glifos de acción/control (no para portadas).

| Token | Valor | Uso |
| --- | --- | --- |
| `@dimen/icon_size_sm` | 18 dp | Icono en línea denso (overflow en filas ajustadas). |
| `@dimen/icon_size_md` | 24 dp | Icono de acción estándar (por defecto de Material; coincide con casi todos los vectores). |
| `@dimen/icon_size_lg` | 32 dp | Transporte secundario. |

El botón de play principal (transporte) puede seguir usando dimensiones propias del reproductor.

### 17.6 Objetivos táctiles y alturas de fila

| Token | Valor | Uso |
| --- | --- | --- |
| `@dimen/touch_target_min` | 48 dp | Suelo de cualquier elemento pulsable (icon buttons de fila/cabecera). |
| `@dimen/row_height_sm` | 56 dp | Fila de lista compacta. |
| `@dimen/row_height_md` | 64 dp | Fila de pista estándar. |
| `@dimen/row_height_lg` | 72 dp | Fila con miniatura de portada. |

### 17.7 Portada y profundidad

| Token | Valor | Uso |
| --- | --- | --- |
| `@dimen/artwork_thumb` | 56 dp | Miniatura de portada en fila de lista. |
| `@dimen/artwork_card` | 140 dp | Portada de tarjeta de carrusel. |
| `@dimen/elevation_raised` | 3 dp | Única excepción a la profundidad tonal (elemento realmente flotante). |
| `@dimen/border_thin` | 1 dp | Grosor de borde/stroke. |

La profundidad se expresa por diferencia tonal de superficie (sección 4), no por sombras. No
introducir sombras nuevas, bordes gruesos ni tarjetas sobredimensionadas.

### 17.8 Cómo usarlos

- Una pantalla nueva **no** define valores `dp`/`sp` propios para espaciado, radio, icono,
  objetivo táctil o tipografía: referencia un token.
- Si falta un valor en la escala, se discute antes de añadirlo; se prefiere reutilizar el paso
  más cercano a crear un token nuevo.
- El color sigue las reglas de las secciones 2–4 (el verde solo con significado semántico).

---

## 18. Estado de aplicación de los tokens

### 18.1 Base (#3)

Introducidos y aplicados como prueba de concepto en componentes compartidos de bajo riesgo:

| Archivo | Qué se migró |
| --- | --- |
| `res/values/dimens.xml` | Nuevo: todas las escalas de la sección 17. |
| `res/values/type.xml` | Nuevo: los seis roles `TextAppearance.Taki.*`. |
| `res/values/styles.xml` | Nuevo `ShapeAppearanceOverlay.Taki.Small`; `.Medium` ahora referencia `@dimen/radius_md`. |
| `layout/list_item_library_track.xml` | Alto de fila, inset, miniatura + forma, jerarquía de texto (`Taki.Title` / `.Caption`), objetivos táctiles y relleno de los icon buttons. |
| `layout/home_carousel_item.xml` | Huella de portada, radio, márgenes y jerarquía de texto (`Taki.TitleSmall` / `.Caption`). |
| `layout/home_fragment.xml` | Encabezados de estantería a `Taki.SectionHeader`; margen lateral, hueco de sección y margen inferior a tokens; saludo a `Taki.Hero`; tarjeta de mezcla a `Taki.Title` / `.Caption`. |

### 18.2 Superficies de álbum y colección artwork-first (#4)

La familia de tarjetas/cabeceras de álbum y colección migrada a los tokens, con **una sola**
forma de portada (`ShapeAppearanceOverlay.Taki.Small`, 8 dp), **sin sombras** en las portadas
(la profundidad es tonal), márgenes de rejilla unificados y densidad de texto reducida:

| Archivo | Qué se migró |
| --- | --- |
| `layout/grid_item_album.xml` | Portada a `ShapeableImageView` + `Taki.Small` (antes 0 dp cuadrada); `Taki.TitleSmall` / `.Caption`; márgenes y relleno a tokens. |
| `layout/list_item_album.xml` | Se elimina el `MaterialCardView` con sombra `3 dp` alrededor de la portada; `ShapeableImageView` + `Taki.Small`, portada `artwork_thumb` (56 dp); `Taki.Title` / `.Caption`; bloque de texto a `wrap_content` con `minHeight = row_height_lg`. |
| `layout/list_item_collection_disc.xml` | Igual que la fila de álbum: sin `MaterialCardView`/sombra, portada `artwork_thumb`, `Taki.Title` / `.Caption`, tokens. |
| `layout/grid_item_collection_disc.xml` | Portada `ShapeableImageView` + `Taki.Small`; `Taki.Caption` / `Taki.TitleSmall` / `Taki.Caption`; tokens. |
| `layout/list_item_collection.xml` | `Taki.TitleSmall` / `.Caption`; márgenes de rejilla a `space_sm`. |
| `layout/list_item_downloaded_album.xml` | Portada movida a la izquierda (coherente con el resto de filas), `ShapeableImageView` + `Taki.Small`; sombra de la tarjeta a `0`; `Taki.Title` / `.Caption` (se corrige `LabelSmall` 11 sp); botón de eliminar descarga a `touch_target_min`. |
| `layout/list_header_album.xml` | Sin `MaterialCardView`/sombra en la portada; `Taki.Title` / `.Caption`; espaciado a tokens. |
| `layout/collection_detail_header.xml` | `Taki.Hero` / `.Caption`; espaciado a tokens. |
| `layout/album_detail_header_item.xml` | **Play primario**: FAB a opacidad completa, relleno `colorOnSurface` con icono `colorSurface`, `touch_target_min`, icono `icon_size_md`. Descarga / info / shuffle pasan a `colorOnSurfaceVariant` + `icon_size_sm` + `touch_target_min` (retroceden). Héroe: `Taki.Title` / `.Caption`, espaciado a tokens. |
| `layout/disc_header_item.xml` | Etiqueta a `Taki.SectionHeader`; botones de play/descarga por disco a `touch_target_min` + `icon_size_sm`; espaciado a tokens. |
| `layout/view_stacked_artwork.xml` | Radio de las 3 portadas apiladas `6 dp` → `@dimen/radius_sm` (se conserva la escala de elevación 1/2/4 dp: es la señal de "pila" de la colección). |

### 18.3 Acciones secundarias como iconos consistentes (#5)

La pantalla de detalle de artista (`artist_detail.xml`) alineada con el patrón de acciones
establecido en `album_detail_header_item.xml` (#4), para que las utilidades no compitan
visualmente con la portada, la información y la reproducción:

| Archivo | Qué se migró |
| --- | --- |
| `layout/artist_detail.xml` | **Play primario**: FAB a opacidad completa, relleno `colorOnSurface` con icono `colorSurface`, `touch_target_min`, icono `icon_size_md` (antes `alpha 0.85` + `colorSurfaceContainerHighest`, 38 dp — peso idéntico a los icon buttons). Descarga / radio retroceden a `colorOnSurfaceVariant` + `icon_size_sm` + `touch_target_min`. Encabezados de sección (Popular / Albums / About / Similar) de `Material3.TitleLarge` a `TextAppearance.Taki.SectionHeader` (sentence case, `onSurfaceVariant`, no compiten). Héroe a `Taki.Title` / `.Caption`. Biografía a `Taki.Body` + color atenuado; el toggle "Show more" **sigue siendo texto** (un icono sería ambiguo) pero con objetivo táctil `touch_target_min`. Espaciado / gutters a tokens. |

El resto de "About/info" ya usa icono: `album_detail_info` (`ic_info_outline`, visible solo cuando
hay notas) abre la hoja de información del álbum; el "About" de la app vive en el popup del hub de
biblioteca. La acción "Play all" de la barra del detalle de pista/carpeta se mantiene con texto a
propósito (es la acción **primaria** de esa pantalla) y ya se oculta en álbum/playlist, que tienen
su propio botón de reproducción en el héroe.

### 18.4 Mini reproductor y Now Playing (#7)

Las dos superficies de reproducción alineadas al mismo lenguaje visual, sin tocar el
comportamiento de Media3/sesión:

| Archivo | Qué se migró |
| --- | --- |
| `layout/now_playing.xml` | Portada `60 dp` → `artwork_thumb` (56) con `ShapeAppearanceOverlay.Taki.Small` (una sola forma de portada, como #4); título/artista a `Taki.Title` / `Taki.Caption`; botones prev/next `40 dp` → `touch_target_min`; iconos de transporte a las variantes planas (`media_backward` / `media_pause` / `media_forward`, sin la capa de sombra); espaciado a tokens. |
| `layout/player_media_info.xml` | Título de Now Playing `Material3.TitleLarge` → `TextAppearance.Taki.Hero` (rol pensado para esto, §17.4); artista → `Taki.Caption`; el corazón de "me gusta" deja de estar oculto a accesibilidad (`focusable=false` + `importantForAccessibility=no` → `focusable=true`, conserva su `contentDescription`); márgenes a tokens. |
| `layout/player_slider.xml` | Posición / duración → `Taki.Caption`; márgenes a `space_md`. |
| `layout/media_buttons.xml` | Iconos de transporte a las variantes planas (`media_start` / `media_pause` / `media_backward` / `media_forward`). El botón play/pausa sigue siendo el primario relleno de alto contraste; shuffle/repeat ya se colorean con el verde (`playerModeColor` → `colorPrimary`) sólo cuando están activos — sin cambios. |
| `values/player_dimensions.xml` | Los valores con equivalente exacto en la escala compartida se aliasan a ella (`player_panel_corner_radius` → `radius_lg`, `player_*_target` → `touch_target_min`, `player_transport_icon` → `icon_size_lg`, `player_mode_icon` → `icon_size_md`, márgenes → `space_*`); el resto (altura de botón de transporte 64, skip-icon 30, los dos huecos de sección grandes) es genuinamente específico del reproductor. |
| `drawable/bg_player_panel.xml`, `drawable/bg_now_playing.xml` | Menos "tarjeta": tono de superficie más bajo (`colorSurfaceContainer` en vez de `…High`) para que el panel agrupe los controles sin leerse como tarjeta elevada; radios al esquema de tokens (`radius_lg` / `radius_md`). |
| `layout/current_playing.xml` | Portada del reproductor `ShapeAppearance.Material3.MediumComponent` → `ShapeAppearanceOverlay.Taki.Medium`; márgenes a tokens. |

Se eliminaron los 4 drawables `media_*_shadow` (layer-list de sombra desplazada 1 dp) al quedar
sin uso; `NowPlayingFragment.update()` ahora referencia los iconos planos.

### 18.5 Pendiente en #11

- Filas de pista (`list_item_track*`, `list_item_queue_track`, `list_item_track_details`) y los
  icon buttons sub-48 dp que quedan.
- Encabezados y gutters de Library y Search (extender el trato de `home_fragment.xml`).
- Tarjetas de playlist/artista (`grid_item_playlist`, `list_item_playlist`, `grid_item_artist`,
  `list_item_artist`).

---

## 19. Referencias

- Material Design 3, Navigation bar: https://m3.material.io/components/navigation-bar
- Material Design 3, Color roles: https://m3.material.io/styles/color/roles
- Android, Material Design 3 in Compose: https://developer.android.com/develop/ui/compose/designsystems/material3
- Android accessibility: https://developer.android.com/guide/topics/ui/accessibility/apps
- Android accessibility principles: https://developer.android.com/guide/topics/ui/accessibility/principles

