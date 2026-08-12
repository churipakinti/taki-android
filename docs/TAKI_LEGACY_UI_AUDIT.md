# Taki — Auditoría y plan de modernización de Legacy UI

## Estado del documento

Documento de referencia para identificar, priorizar y modernizar superficies heredadas de Ultrasonic sin convertir la beta de Taki en una reescritura completa. La clasificación se basa en la estructura actual de `develop`, en los layouts XML, fragments y flujos accesibles. Una señal técnica de legado no implica automáticamente una mala experiencia visual; toda intervención debe confirmarse en dispositivo.

## Objetivo

Eliminar los saltos visuales y de lenguaje que hacen que algunas partes de Taki parezcan pertenecer a otra aplicación, manteniendo intacta la funcionalidad estable y la compatibilidad con Navidrome, Subsonic y OpenSubsonic.

La meta no es modernizar cada archivo antiguo. La meta es que **ninguna superficie que el usuario encuentre normalmente rompa la identidad, simplicidad o fluidez de Taki**.

## Principios

1. Modernizar flujos completos, no archivos aislados.
2. Priorizar superficies visibles y frecuentes.
3. Ocultar o aislar correctamente funciones fuera de la visión antes que invertir tiempo en rediseñarlas.
4. Reutilizar componentes, estilos, dimensiones y lenguaje compartidos.
5. No confundir paquete `legacy` con prioridad automática: revisar el resultado visual real.
6. No modificar lógica estable salvo que sea indispensable para corregir la experiencia.
7. No añadir personalización por el simple hecho de conservar opciones heredadas.
8. Verificar cada flujo online, offline y con errores reales.

## Clasificación

### A. Modernización prioritaria antes o inmediatamente después de la beta

#### A1. Flujo completo de Playlists

Estado: claramente heredado y visible.

Evidencia:

- `PlaylistsFragment` continúa en `fragment.legacy`.
- El código reconoce que fue convertido desde Java pero no modernizado completamente.
- Conserva `GridView`, `BaseAdapter`, manejo manual de vistas y demasiadas responsabilidades en una sola clase.
- Los diálogos `save_playlist.xml` y `update_playlist.xml` utilizan formularios Android antiguos.

Acción:

- Implementar el documento `TAKI_PLAYLIST_UX_REDESIGN.md` como alcance independiente.
- Incluir listado, cuadrícula, detalle, creación, selección de canciones, guardado, edición, menús, descargas y relación con la cola.
- No rediseñar otras superficies desde esta tarea.

#### A2. Editar y añadir colección/servidor

Estado: funcional, pero visual y lingüísticamente heredado.

Problemas:

- Formulario extenso y denso.
- Información básica y avanzada aparecen casi al mismo nivel.
- Expone conceptos técnicos: URL, certificado autofirmado, contraseña en texto plano y jukebox.
- Encabezados en mayúsculas y alineaciones manuales.
- `Probar conexión` y `Guardar` compiten entre sí.
- El formulario no refleja plenamente el lenguaje de “colección” adoptado por Taki.

Dirección:

- Primera sección: nombre, dirección, usuario y contraseña.
- Acción clara `Probar conexión` con estado visible: comprobando, conectada o error.
- Acción primaria `Guardar colección`.
- Opciones técnicas dentro de `Configuración avanzada`, colapsada por defecto.
- Explicar los riesgos de certificado autofirmado y contraseña sin protección en plain language.
- Ocultar jukebox si se mantiene fuera de la experiencia principal de Taki.
- Preservar los valores introducidos ante un fallo.

Criterios de aceptación:

- Una persona no técnica puede conectar Navidrome siguiendo únicamente la sección principal.
- Las opciones de riesgo no están activadas ni expuestas como decisiones normales.
- Los errores dicen qué ocurrió y qué puede intentar el usuario.

#### A3. Cola y listas de canciones compartidas

Estado: parcialmente modernizada.

Fortalezas actuales:

- Fila compacta con portada, título, artista y arrastre.
- Reordenamiento y eliminación funcionales.
- Cola y posición se restauran.

Pendientes:

- Estandarizar encabezado, filas, indicador de reproducción y menús con playlists, álbumes, géneros y búsqueda.
- Mostrar origen de la cola cuando el modelo persistido lo soporte.
- Revisar fast scroll, estados vacíos y accesibilidad.
- Evitar que cada pantalla cree una variante distinta de fila musical.

Esta tarea puede compartir componentes con Playlists, pero los cambios fuera de playlists deben aprobarse como una fase separada después de comprobar que no producen regresiones.

### B. Modernización posterior a la beta o cuando el flujo principal esté estable

#### B1. Ecualizador

Estado: funcional, visualmente heredado.

Evidencia:

- Usa `CheckBox`, `RelativeLayout` y `SeekBar` tradicionales.
- Las bandas se crean dinámicamente con una presentación básica.
- El selector de preset queda como botón aislado.

Dirección:

- Switch Material para activar.
- Encabezado compacto y explicación breve.
- Bandas con frecuencia y ganancia alineadas de manera consistente.
- Presets mediante selector claro.
- Acción visible para restablecer.
- Indicar cuando el dispositivo no admite ecualización.

No reescribir el motor de audio dentro de esta tarea.

#### B2. Widget de Android

Estado: claramente heredado y visible fuera de la app.

Problemas:

- `RelativeLayout` y composiciones duplicadas por tamaño.
- Iconos de reproducción heredados.
- Play desproporcionadamente grande.
- Título, artista y álbum centrados con jerarquía débil.
- Identidad visual distinta del minirreproductor.

Dirección:

- Tratar el widget como extensión del minirreproductor.
- Portada, canción, artista y controles esenciales.
- Iconos de Taki y superficies compatibles con widgets dinámicos del sistema.
- Variantes pequeñas y grandes que compartan jerarquía.
- Verificar modo oscuro, launchers y tamaños de fuente.

#### B3. Selector de colecciones/servidores

Estado: intermedio, no completamente antiguo.

Fortalezas:

- RecyclerView, tarjetas, nombre, dirección y menú contextual.

Pendientes:

- Reducir elevación, borde y peso de las tarjetas.
- Revisar el FAB dominante.
- Presentar Offline como un modo, no como un servidor ficticio, sin eliminar su implementación eficiente.
- Mostrar el servidor activo mediante relleno o marca discreta, sin borde de acento agresivo.
- Aplicar el nombre configurado por el usuario y lenguaje de `colección`.

#### B4. Ajustes y diálogos secundarios

Estado: pantalla principal modernizada; algunos controles internos conservan patrones heredados.

Revisar:

- Selectores y diálogos de preferencias.
- Confirmaciones genéricas.
- Selector antiguo de intervalos `time_span_dialog.xml`.
- Mensajes técnicos y rutas de almacenamiento.

No modernizar un diálogo que solo pertenece a una función oculta o eliminada. El futuro sleep timer debe tener un componente propio y simple; no reutilizar automáticamente el selector heredado de intervalos.

### C. Código heredado visualmente renovado: inspeccionar antes de tocar

#### C1. Géneros

- `SelectGenreFragment` permanece en `fragment.legacy`.
- La vista utiliza RecyclerView y tarjetas modernas.
- El detalle de género recibió una modernización reciente.

Acción: inspección en dispositivo, corrección de inconsistencias concretas y ninguna reescritura motivada únicamente por el nombre del paquete.

#### C2. Letras

- `LyricsFragment` permanece en `fragment.legacy`.
- La vista ya contiene encabezado Material, título, artista, estado de sincronización y RecyclerView.

Acción: comprobar scrolling, resaltado sincronizado, estados sin letras, offline y accesibilidad. No priorizar una reescritura visual si esos casos funcionan.

#### C3. Settings, About, Home, Search, Downloads y reproductor

Estas superficies ya recibieron trabajo significativo y no deben reabrirse como parte de una “modernización general” sin hallazgos concretos.

Permitir solo:

- Correcciones de consistencia demostrables.
- Bugs de insets, teclado, accesibilidad o estados.
- Reutilización necesaria para un componente compartido.

### D. Funciones heredadas ocultas o fuera de la visión

Persisten en código o en el grafo de navegación:

- Chat.
- Podcasts.
- Shares.
- Bookmarks.
- Formularios asociados a compartir.

Varias conservan `ListView` y layouts visualmente antiguos. No deben modernizarse antes de la beta si permanecen fuera de la propuesta de Taki.

Acción requerida:

1. Confirmar que no aparecen en Home, Library, menús, accesos directos ni Android Auto.
2. Confirmar que no pueden abrirse accidentalmente mediante navegación interna rota.
3. Documentar si se mantienen por compatibilidad, se eliminarán posteriormente o podrían regresar.
4. Evitar que recursos o strings de estas funciones contaminen búsquedas, traducciones y menús visibles.
5. No eliminar código heredado sin una auditoría de dependencias y licencia.

### Resolución (2026-08-12) — Fase 6 cerrada

Auditoría de código (sin ejecutar en dispositivo, tres agentes de exploración en paralelo) confirmó los puntos 1 y 2 para las cuatro funciones:

- **Chat**: sin `<action>` en `navigation_graph.xml`, sin entrada de menú, sin call site de navegación en todo el árbol Kotlin. Ya documentado como intencional en `Settings.kt:179-181` y en `HANDOFF.md` (sección "La visión").
- **Podcasts**: mismo resultado — sin acción de navegación ni entrada de menú. El handler de Android Auto (`MediaLibrarySessionCallback.getPodcasts()`) existe pero `getRootItems()`/`getLibrary()` nunca lo exponen en el árbol navegable real.
- **Shares**: tanto navegar shares existentes (`SharesFragment`) como crear uno nuevo (`ShareHandler.createShare()`, `R.string.menu_share`) están completamente muertos — ningún menú contextual de canción/álbum expone "Compartir".
- **Bookmarks**: sin acción de navegación ni entrada de menú (fue removido explícitamente del drawer y del overflow del Player, ver `HANDOFF.md` punto 28). El handler de Android Auto existe pero tampoco se expone en `getRootItems()`/`getLibrary()`.

Las cuatro ya estaban documentadas como ocultas a propósito en `HANDOFF.md` (sección "La visión" — "Music only" y "Local player, not a social music network" — y puntos 5, 14 y 28 de "Current state"), como parte explícita de la visión de Taki como reproductor de música pura, no una red social ni un cliente Subsonic genérico "feature-complete".

**Decisión del usuario (2026-08-12), confirmando la visión ya establecida:** mantener las cuatro ocultas — código conservado, sin punto de entrada. No se reactivan ni se eliminan por ahora. No fue necesario ningún cambio de código: el requisito de "no aparecen en ningún lado" ya estaba cumplido antes de esta auditoría.

Fase 6 se da por cerrada. Si en el futuro se decide reactivar o eliminar alguna, tratarlo como una tarea nueva y explícita, no una continuación silenciosa de este cierre.

## Inventario resumido

| Superficie | Estado | Visibilidad | Prioridad |
| --- | --- | --- | --- |
| Playlists completo | Antiguo | Alta | P0 |
| Editar/agregar colección | Antiguo/intermedio | Alta en onboarding | P0 |
| Cola y filas compartidas | Intermedio | Alta | P1 |
| Selector de colecciones | Intermedio | Media | P1 |
| Ecualizador | Antiguo | Baja/media | P2 |
| Widget | Antiguo | Opcional pero público | P2 |
| Géneros | Legacy interno, visual moderno | Media | Inspeccionar |
| Letras | Legacy interno, visual moderno | Media | Inspeccionar |
| Settings/About | Modernizado | Media | Mantener estable |
| Chat/Podcasts/Shares/Bookmarks | Antiguo y oculto | Ninguna (confirmado) | **Cerrado (2026-08-12)**: se mantienen ocultos, sin cambios de código |

## Sistema visual que debe compartirse

Antes de modernizar más pantallas, consolidar o documentar:

- Espaciado base y márgenes laterales.
- Alturas de filas musicales.
- Tamaños y radios de portadas.
- Tipografías primaria, secundaria y labels.
- Colores de superficie y estados seleccionados.
- Tratamiento del acento: reservarlo para acciones/estados importantes.
- Menú contextual estándar.
- Estado de reproducción actual.
- Estados de descarga.
- Empty state, loading, error y offline.
- Padding inferior con minirreproductor, navegación e insets.

El objetivo es evitar que cada modernización codifique nuevamente estos valores.

## Orden recomendado de trabajo

### Fase 1 — Playlists

Implementar y validar `TAKI_PLAYLIST_UX_REDESIGN.md` completo.

### Fase 2 — Flujo de conexión

Modernizar editor de colección y pulir selector, manteniendo Offline explícito y eficiente.

### Fase 3 — Componentes musicales compartidos

Estandarizar filas, cola, menús y estados sin rediseñar de nuevo Home o álbumes.

### Fase 4 — Verificación de superficies legacy renovadas

Probar géneros y letras; corregir solo problemas reales.

### Fase 5 — Superficies opcionales

Modernizar ecualizador y widget según el feedback de la beta.

### Fase 6 — Deuda oculta (cerrada 2026-08-12)

Decidir el destino de Chat, Podcasts, Shares y Bookmarks; aislar o retirar de forma segura. **Resultado: las cuatro se mantienen ocultas**, confirmando la visión ya documentada en `HANDOFF.md`. Ver "Resolución (2026-08-12)" en la sección D arriba.

## Matriz de verificación por pantalla

Para cada flujo intervenido, probar:

- Tema oscuro y contraste.
- Fuente normal y 200 %.
- Títulos largos y traducciones.
- Sin contenido, cargando, contenido, error y offline.
- Con y sin minirreproductor.
- Navegación por gestos y tres botones.
- Teclado visible cuando existan campos.
- Rotación, si la pantalla la admite.
- TalkBack y áreas táctiles mínimas.
- Servidor Navidrome y, cuando sea viable, otro servidor compatible.
- Ausencia de nuevas llamadas N+1 o trabajo pesado en el hilo principal.

## Criterios globales de terminado

- Ningún flujo principal salta abruptamente a controles Android antiguos.
- Lista, detalle, edición y diálogos de una misma función comparten identidad.
- Los términos técnicos aparecen solo cuando son necesarios y están explicados.
- Las superficies ocultas permanecen realmente inaccesibles.
- Los componentes compartidos no generan regresiones en pantallas ya modernizadas.
- Cada fase incluye capturas antes/después y evidencia de compilación, pruebas y lint.
- La modernización no amplía la funcionalidad ni sustituye lógica estable sin una justificación documentada.

## Entrega esperada del agente por fase

1. Inventario exacto de archivos afectados.
2. Capturas antes y después.
3. Componentes reutilizados o extraídos.
4. Estados y tamaños de pantalla verificados.
5. Resultados de build, tests y lint.
6. Regresiones encontradas y resueltas.
7. Hallazgos fuera de alcance para una fase posterior.

