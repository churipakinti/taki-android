# Taki: guía visual y de lenguaje

## Estado y propósito

Este documento define una **dirección de diseño y contenido** para Taki. Sirve como referencia para auditar y corregir la interfaz, pero **no confirma que todos los componentes, estados o comportamientos descritos estén implementados**.

Antes de aplicar cada cambio se debe comprobar:

1. Qué recurso, tema o componente controla actualmente el elemento.
2. Si el cambio afecta todas las pantallas o solo un contexto.
3. Si el comportamiento asociado existe realmente.
4. Si el texto procede de `strings.xml` y tiene traducciones equivalentes.
5. Si el resultado conserva accesibilidad, legibilidad y eficiencia.

Las modificaciones implementadas y verificadas deben registrarse en `CHANGES.md`. Esta guía no sustituye el historial técnico del proyecto.

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

## 17. Referencias

- Material Design 3, Navigation bar: https://m3.material.io/components/navigation-bar
- Material Design 3, Color roles: https://m3.material.io/styles/color/roles
- Android, Material Design 3 in Compose: https://developer.android.com/develop/ui/compose/designsystems/material3
- Android accessibility: https://developer.android.com/guide/topics/ui/accessibility/apps
- Android accessibility principles: https://developer.android.com/guide/topics/ui/accessibility/principles

