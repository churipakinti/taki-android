# Taki — Rediseño y estandarización de Playlists

## Estado del documento

Propuesta de implementación para revisar la experiencia completa de playlists antes de la beta. Este documento describe el comportamiento y la presentación deseados; no asume que todos los componentes o datos necesarios ya existan en la implementación actual.

## Objetivo

Hacer que las playlists se sientan como parte coherente de Taki: una biblioteca musical simple, contemporánea y enfocada en escuchar. El cambio debe abarcar tanto la pantalla que enumera las playlists como la vista que se abre al entrar en una de ellas.

Los nombres visibles en las capturas, incluidas las fechas y nombres como `Never Played` o `Random Mix`, son contenido de prueba. **Renombrar, interpretar o corregir esas playlists queda fuera del alcance de este trabajo.**

## Problemas observados

### Pantalla de playlists

- Existe demasiado espacio vacío entre la navegación superior y el contenido.
- El botón para alternar entre lista y cuadrícula flota sin contexto.
- La jerarquía entre portada, nombre, cantidad de canciones y estado de descarga es débil.
- La vista de lista utiliza tarjetas demasiado altas y muestra poca información útil para el espacio ocupado.
- `Not downloaded` se repite en todos los elementos aunque represente el estado normal.
- El icono de descarga permanente compite con la acción principal, que debería ser abrir la playlist.
- Los collages tienen bordes y contenedores pesados y pueden dominar visualmente el contenido.
- El último contenido puede quedar oculto detrás del minirreproductor y la navegación inferior.
- Lista y cuadrícula parecen dos diseños independientes en lugar de dos representaciones del mismo componente.

### Interior de una playlist

- Debe revisarse como parte del mismo trabajo, no dejarse con el diseño heredado.
- La jerarquía, las filas, los menús y los estados deben coincidir con las demás colecciones de canciones y con la cola de reproducción.
- No debe crearse una tercera variante visual de fila de canción únicamente para playlists.

## Principios de diseño

1. **La música es la acción principal.** Abrir y reproducir deben ser más evidentes que descargar o administrar.
2. **Menos estados visibles por defecto.** Solo se muestra un estado secundario cuando aporta información real.
3. **Una sola gramática visual.** Álbumes, playlists, resultados de búsqueda y cola deben compartir tipografía, espaciado, iconografía y comportamiento.
4. **Las acciones destructivas o administrativas son secundarias.** Deben vivir en un menú contextual.
5. **Sin configuración innecesaria.** Se conserva el selector lista/cuadrícula, pero no se agregan más opciones de apariencia.
6. **Diseño resistente a contenido real.** Debe funcionar con títulos largos, playlists sin portada, una sola canción, miles de canciones y estados offline.

## 1. Pantalla principal de Playlists

### Encabezado

Crear un encabezado compacto y alineado con las pantallas modernas de contenido de Taki:

```text
←  Playlists                         [lista/cuadrícula]
   8 playlists
```

- Mantener el botón Atrás cuando esta pantalla se abre desde Library.
- Mostrar `Playlists` como título.
- Mostrar el número total como texto secundario solo si el dato está disponible sin una petición costosa adicional.
- Integrar el selector de vista en el encabezado; eliminar el icono flotante y el gran espacio vacío.
- El icono debe representar la vista a la que cambiará o utilizar un control cuyo estado actual resulte inequívoco.
- Preservar la preferencia de vista existente, si ya se guarda.

### Vista de lista

Usar filas compactas, no tarjetas grandes aisladas:

```text
[portada 72 × 72]  Nombre de playlist             ⋮
                   50 canciones
                   Descargando · 42 %
```

Requisitos:

- Altura objetivo aproximada: 88–96 dp, adaptándose al contenido y accesibilidad.
- Portada cuadrada de 72 dp, con esquinas coherentes con álbumes y minirreproductor.
- Nombre: máximo dos líneas; no cortar prematuramente si existe espacio.
- Metadato secundario: cantidad de canciones.
- No mostrar `No descargada`/`Not downloaded`: la ausencia de descarga es el estado normal.
- Mostrar estado únicamente cuando corresponda:
  - `Descargada` acompañado de un check discreto.
  - `Descargando · 42 %` con progreso accesible.
  - `Descarga en pausa` o `Error de descarga` solo cuando sean estados reales y accionables.
- Sustituir el icono permanente de descarga por un menú `⋮`.
- La fila completa abre la playlist.
- El menú secundario debe incluir únicamente acciones soportadas y válidas para ese estado, por ejemplo:
  - Reproducir ahora.
  - Reproducir después.
  - Añadir al final de la cola.
  - Descargar / Quitar descarga.
  - Renombrar, cuando el servidor y permisos lo permitan.
  - Eliminar, con confirmación.
- No agregar acciones que la API o el servidor actual no puedan ejecutar de forma fiable.

### Vista de cuadrícula

- Mantener dos columnas en teléfonos.
- Usar el mismo modelo de información que la lista: portada, nombre, cantidad y menú.
- Agrandar la portada y reducir el marco oscuro alrededor del collage.
- Nombre en un máximo de dos líneas.
- Cantidad de canciones como texto secundario.
- Colocar `⋮` en una posición consistente sin cubrir la portada.
- No mostrar el icono de descarga permanentemente.
- Si hay un estado de descarga activo, expresarlo con un indicador pequeño sobre la portada o debajo de los metadatos; debe ser consistente con la vista de lista.
- Mantener separaciones horizontales y verticales uniformes.
- Evitar que el collage repita una sola carátula cuatro veces cuando solo existe una imagen útil. Definir fallback:
  1. Cuatro portadas distintas si existen.
  2. Dos portadas distintas con composición equilibrada.
  3. Una portada ocupando todo el espacio.
  4. Placeholder de Taki si no existe ninguna portada.

### Scroll e insets

- El último elemento debe poder desplazarse completamente por encima del minirreproductor y de la navegación inferior.
- Calcular el padding inferior a partir de los componentes realmente visibles y los system insets; evitar valores rígidos duplicados.
- Comprobar los casos:
  - Sin reproducción activa.
  - Con minirreproductor.
  - Con navegación por gestos.
  - Con navegación Android de tres botones.
  - Orientación horizontal.

## 2. Vista interior de una playlist

### Objetivo de estandarización

La pantalla interna debe reutilizar el patrón visual y los componentes compartidos por álbumes, colecciones de canciones y cola. No debe mantener filas heredadas diferentes ni introducir una variante exclusiva sin justificación funcional.

Antes de modificarla, comparar en el código:

- Detalle de álbum.
- Lista de canciones por género.
- Resultados de búsqueda.
- Canciones descargadas.
- Cola de reproducción.

Elegir el componente compartido más cercano como base y extraer/reutilizar tokens o componentes si actualmente existen implementaciones duplicadas.

### Encabezado de playlist

Debe contener:

- Botón Atrás.
- Portada o collage.
- Nombre completo de la playlist.
- Cantidad de canciones y, si está disponible, duración total.
- Acción primaria `Reproducir`.
- Acción secundaria `Aleatorio`.
- Menú `⋮` para descargar, renombrar, eliminar u otras acciones administrativas compatibles.

No mostrar información técnica del servidor ni IDs. Evitar una tarjeta de encabezado excesivamente alta; el contenido debe comenzar pronto y aprovechar la portada como foco visual.

### Filas de canciones

Estandarizar con las demás listas de Taki:

- Misma altura mínima, padding, tipografía y colores.
- Título como información primaria.
- Artista y, cuando aporte valor, álbum como información secundaria.
- Indicador discreto de canción descargada; no mostrar texto repetitivo.
- Menú `⋮` para acciones secundarias.
- Tocar una canción inicia la playlist desde esa posición.
- Mantener pulsado entra en selección múltiple solo si ese patrón ya está estandarizado en el resto de la app.
- La canción que está sonando debe usar el mismo indicador que la cola y otras listas, no solo color de acento en todo el texto.
- La fila actual debe seguir siendo legible sin depender únicamente del color.

### Acciones de canciones

Unificar nombres y orden con el resto de Taki:

1. Reproducir ahora.
2. Reproducir después.
3. Añadir al final de la cola.
4. Iniciar radio.
5. Añadir a playlist, cuando aplique.
6. Descargar / Quitar descarga.
7. Quitar de esta playlist.

`Quitar de esta playlist` debe distinguirse claramente de eliminar el archivo descargado o eliminar la canción del servidor. Confirmar solamente las acciones destructivas o difíciles de revertir.

### Relación con la cola

- Al tocar una canción, construir la cola con las canciones de la playlist en su orden actual y comenzar en la posición tocada.
- `Reproducir` comienza desde la primera canción.
- `Aleatorio` utiliza la playlist completa con shuffle activado.
- `Reproducir después` y `Añadir al final` deben reutilizar los mismos modos de inserción que las otras colecciones.
- La cola resultante debe poder reordenarse y restaurarse como ya lo hace Taki.
- Si es viable dentro del modelo existente, registrar el origen como `Playlist · <nombre>` para una futura visualización del contexto. Si requiere ampliar el estado persistido, documentarlo como tarea separada y no bloquear este rediseño.

### Estados de pantalla

Diseñar y verificar explícitamente:

- Cargando: indicador discreto o skeleton coherente con otras pantallas.
- Vacía: mensaje en lenguaje natural y acción útil cuando exista.
- Sin conexión con copia local: mostrar el contenido disponible sin bloquear la pantalla.
- Sin conexión y sin contenido local: explicar qué ocurre y ofrecer cambiar a Offline o reintentar según el flujo vigente de Taki.
- Error del servidor: mensaje simple, `Reintentar` y acceso opcional a diagnóstico.
- Playlist eliminada o inaccesible: regresar de forma segura sin crash.

## 3. Lenguaje recomendado

### Inglés

- `Playlists`
- `%d songs`
- `Downloaded`
- `Downloading · %d%%`
- `Download paused`
- `Download failed`
- `Play`
- `Shuffle`
- `Play next`
- `Add to queue`
- `Remove from playlist`

### Español

- `Playlists` o `Listas`, según la decisión lingüística global vigente; no mezclar ambas dentro del mismo flujo.
- `%d canciones`
- `Descargada`
- `Descargando · %d %`
- `Descarga en pausa`
- `Error de descarga`
- `Reproducir`
- `Aleatorio`
- `Reproducir después`
- `Añadir a la cola`
- `Quitar de la playlist` o `Quitar de la lista`, siguiendo la misma decisión terminológica global.

Evitar:

- `Not downloaded` repetido en cada elemento.
- `Pin/Unpin` como texto visible para usuarios no técnicos.
- `Directory`, `server playlist`, IDs o nombres internos de la API.

## 4. Accesibilidad

- Áreas táctiles mínimas de 48 × 48 dp.
- Descripciones de contenido para Atrás, cambiar vista, menú y estados de descarga.
- No depender únicamente del verde para selección, descarga o reproducción actual.
- Comprobar escalado de fuente al 200 %.
- Mantener contraste adecuado entre texto primario, secundario y superficies.
- El orden de TalkBack debe seguir: portada/nombre → metadatos → estado → menú.

## 5. Restricciones técnicas

- No migrar esta pantalla a Compose como parte de este cambio salvo que exista ya una migración aprobada y activa.
- No reescribir la lógica de sincronización de playlists sin una razón funcional demostrable.
- No cambiar nombres ni contenido de playlists de prueba.
- No implementar nuevas capacidades que Navidrome/Subsonic no soporten sin detectar primero la capacidad.
- Reutilizar adaptadores, view holders, estilos, dimensiones y drawables compartidos siempre que sea razonable.
- Mantener compatibilidad con modo Offline y con servidores Subsonic/OpenSubsonic compatibles.
- Evitar llamadas adicionales por fila; la UI no debe introducir consultas N+1 para obtener duración, descarga o portada.

## 6. Plan sugerido de implementación

### Fase A — Auditoría breve

1. Identificar fragmentos, layouts, adaptadores y view holders de la lista y detalle de playlists.
2. Compararlos con álbumes, género, búsqueda y cola.
3. Documentar qué componentes pueden compartirse y qué diferencias son funcionalmente necesarias.
4. Identificar cómo se calculan actualmente portada, cantidad y estado de descarga.

### Fase B — Lista y cuadrícula

1. Integrar encabezado y selector de vista.
2. Rediseñar fila compacta.
3. Rediseñar tarjeta de cuadrícula.
4. Implementar fallbacks del collage.
5. Mover descarga y administración al menú contextual.
6. Corregir padding inferior e insets.

### Fase C — Detalle de playlist

1. Estandarizar encabezado y acciones principales.
2. Reutilizar la fila común de canciones.
3. Unificar menús y terminología.
4. Verificar construcción de cola desde una posición concreta.
5. Implementar estados vacío, offline y error.

### Fase D — Verificación

1. Ejecutar pruebas existentes y añadir pruebas para lógica nueva.
2. Probar manualmente lista y cuadrícula con contenido representativo.
3. Probar una playlist vacía, una de una canción, una de 50 y una de más de 1.000.
4. Probar títulos largos y caracteres especiales.
5. Probar descarga completa, parcial, error y eliminación de descarga.
6. Probar online y Offline.
7. Probar reproducción desde primera, media y última canción.
8. Probar con y sin minirreproductor y ambos modos de navegación Android.

## 7. Criterios de aceptación

- La pantalla muestra `Playlists` y el selector de vista en un encabezado compacto.
- No existe el gran espacio vacío ni un selector flotante sin contexto.
- Lista y cuadrícula comparten jerarquía y estados.
- `Not downloaded` no aparece repetidamente.
- Descargar es una acción secundaria; los estados activos siguen siendo visibles.
- El último elemento nunca queda inaccesible detrás del minirreproductor o navegación.
- Entrar en una playlist muestra una pantalla coherente con álbumes y otras colecciones.
- Las filas de canciones utilizan el mismo lenguaje visual y comportamiento de la cola y listas equivalentes.
- Tocar una canción reproduce la playlist desde esa posición.
- Play, shuffle, reproducir después y añadir a la cola generan resultados correctos.
- Los estados vacío, cargando, offline y error tienen tratamiento explícito.
- No se agregan consultas de red por cada fila.
- No se cambian los nombres de las playlists de prueba.
- Compilan debug y release; pasan pruebas y lint aplicables.

## 8. Entrega esperada del agente

Al finalizar, entregar:

1. Resumen de los componentes modificados y reutilizados.
2. Capturas de lista, cuadrícula y detalle de playlist.
3. Evidencia de los casos con y sin minirreproductor.
4. Resultados de compilación, pruebas y lint.
5. Limitaciones encontradas en la API o en la implementación heredada.
6. Lista separada de mejoras detectadas pero deliberadamente fuera de alcance.

