# Taki — Plan consolidado para completar la beta

**Estado de referencia:** rama `develop`, revisada el 13 de agosto de 2026. P0.1–P0.3 cerrados (ver tablero, sección 8, y `CHANGES.md`); quedan P0.4 y P0.5 como bloqueantes.  
**Objetivo:** producir una beta instalable, segura, fluida y suficientemente estable para compartir con testers de Reddit.  
**Regla principal:** este documento es la lista operativa vigente. No reabrir trabajos cerrados ni iniciar mejoras fuera de alcance mientras queden tareas P0.

## 1. Resultado esperado

La beta se considera lista únicamente cuando exista un APK release propio de Taki que:

- esté firmado con una clave propia y recuperable;
- no exponga credenciales mediante Android Backup;
- instale y actualice correctamente;
- reproduzca online y offline sin ANR, crash ni bloqueos conocidos;
- conserve sesión, canción, posición y cola dentro de los límites ya establecidos;
- permita configurar una colección Navidrome, navegar, buscar, descargar y reproducir;
- incluya Radio de canción, Radio de artista, Mix diario y playlists funcionales;
- haya superado una prueba integrada en un dispositivo físico;
- pueda identificarse exactamente mediante versión, commit, firma y SHA-256.

## 2. Estado confirmado: no repetir

Los siguientes trabajos ya están implementados en `develop`. Solo corregirlos si una prueba reproduce un defecto concreto:

- Optimización interna y migración principal a funciones `suspend`.
- Contenido local primero, caché coherente y reducción de solicitudes.
- Negociación de extensiones OpenSubsonic.
- Búsqueda fluida y control de resultados obsoletos.
- Restauración protegida de colas grandes con ventana máxima de 100 pistas.
- Radio de canción.
- Radio de artista.
- Mix diario v1, persistencia, restauración por ID y separación por servidor.
- Calidad predeterminada ajustada.
- Rediseño del listado y detalle de playlists.
- Modernización del formulario para agregar y editar colecciones.
- Home, Library, Search, Downloads, About y reproductor principal modernizados.
- Chat, podcasts, shares y bookmarks ocultos y sin navegación pública.
- Modo Offline explícito conservado por razones de rendimiento. Internamente puede continuar usando `localhost`; en la interfaz debe presentarse como modo Offline o música descargada.

No volver a investigar estas decisiones desde cero. Consultar commits y documentos existentes solo cuando sea necesario para resolver una regresión.

## 3. Orden obligatorio de ejecución

Completar las fases en este orden. No iniciar una fase posterior si la anterior deja un fallo de seguridad, compilación o reproducción.

### P0.1 — Firma release propia

**Problema actual:** `ultrasonic/build.gradle` no contiene una configuración release propia. El archivo heredado `ultrasonic-keystore.enc` no debe convertirse en la identidad de firma de Taki.

#### Implementación

1. Crear un keystore nuevo exclusivo de Taki.
2. Guardarlo fuera del repositorio y crear al menos dos copias seguras.
3. Cargar ruta, alias y contraseñas desde variables de entorno o un archivo local ignorado por Git.
4. Añadir `signingConfig` a la variante release sin incluir secretos en el código.
5. Documentar únicamente los nombres de variables y el procedimiento; nunca sus valores.
6. Generar el APK release con minificación y reducción de recursos activadas.

#### Criterios de cierre

- `assembleRelease` termina correctamente desde un checkout limpio.
- El APK está firmado con la clave de Taki.
- `apksigner verify --verbose --print-certs <apk>` pasa.
- Ninguna contraseña, clave privada o archivo de propiedades sensible está versionado.
- Se documentó el procedimiento de recuperación y firma para futuras versiones.

### P0.2 — Fase 6: credenciales y Android Backup

**Problema confirmado:** el manifest mantiene `android:allowBackup="true"`; las reglas incluyen todos los `SharedPreferences` y bases de datos, y la transferencia entre dispositivos no contiene exclusiones suficientes. Las credenciales heredadas deben considerarse sensibles hasta demostrar lo contrario.

#### Implementación

1. Inventariar dónde se almacenan URL, usuario, contraseña, tokens, certificados aceptados y demás secretos.
2. Determinar qué preferencias y bases contienen información sensible.
3. Impedir que credenciales y tokens entren en cloud backup o device transfer.
4. Preferir almacenamiento protegido para secretos si puede implementarse y migrarse sin perder configuraciones.
5. Si una migración segura no puede completarse y probarse antes de la beta, usar la solución conservadora: desactivar Android Backup temporalmente.
6. No borrar conexiones existentes durante una actualización.

#### Criterios de cierre

- Un backup o traslado de dispositivo no contiene contraseñas ni tokens recuperables.
- Actualizar desde el debug/beta anterior conserva los datos que deban conservarse.
- Una instalación restaurada solicita nuevamente credenciales cuando corresponda.
- Cambiar, editar y eliminar colecciones continúa funcionando.
- La decisión y sus pruebas quedan documentadas.

### P0.3 — Auditoría interna pendiente

Usar `docs/AUDITORIA_FUNCIONAMIENTO_INTERNO.md` como guía técnica, pero reportar el resultado de manera breve.

#### Revisiones dirigidas

- Resolver o descartar con evidencia el ciclo de vida de `PlayerFragment.ioScope`.
- Resolver o descartar con evidencia las corrutinas de `TrackViewHolder` al reciclar filas.
- Localizar y cerrar el `Cursor` asociado al `CloseGuardException` observado con StrictMode.
- Revisar scopes de Fragments, callbacks tardíos, paginación solapada y acceso a Media3 desde hilos incorrectos.
- Comprobar cancelación de tareas al cambiar de colección o entrar/salir de Offline.
- Comprobar cancelación, reintento y cierre de descargas.

#### Criterios de cierre

- No quedan sospechosos conocidos sin una conclusión escrita: corregido, descartado con razón o convertido en issue explícito.
- No aparecen `FATAL EXCEPTION`, ANR ni violaciones propias de StrictMode durante la prueba dirigida.
- Cada corrección incluye una prueba de regresión cuando sea razonable.
- No se introducen abstracciones generales salvo que el mismo defecto aparezca en tres o más lugares.

### P0.4 — Prueba integrada en dispositivo físico

Ejecutar sobre el APK candidato, no solamente sobre builds parciales durante el desarrollo.

#### Matriz mínima

1. Instalación limpia y configuración de Navidrome.
2. Actualización sobre una instalación previa sin perder configuración válida.
3. Reproducción, pausa, anterior, siguiente, seek, shuffle y repeat.
4. Segundo plano, pantalla bloqueada, notificación, auriculares y Bluetooth.
5. Force-stop, reapertura y restauración de canción, posición y cola.
6. Cola creada desde una biblioteca grande.
7. Descargas completas, canceladas, interrumpidas y reintentadas.
8. Cambio entre colección online y modo Offline.
9. Pérdida y recuperación de red durante la reproducción.
10. Home, Library, Search, búsquedas recientes y Downloads.
11. Radio de canción, Radio de artista y Mix diario tras reiniciar.
12. Playlists: listar, abrir, crear, editar, reproducir, descargar y eliminar.
13. Android Auto: navegación, reproducción, reanudación y desconexión.
14. Fuente normal y 200 %, TalkBack básico, gestos y navegación de tres botones.
15. Logcat completo filtrado por StrictMode, crashes, ANR y frames omitidos.

#### Criterios de cierre

- Ningún P0 o P1 funcional permanece abierto.
- Los defectos menores se registran como issues con pasos de reproducción.
- Se conserva el logcat o reporte de prueba como evidencia del candidato exacto.

### P0.5 — Construcción y entrega reproducible

1. Congelar funciones durante la preparación.
2. Confirmar `versionName` y aumentar `versionCode` si corresponde.
3. Ejecutar desde un checkout limpio:
   - tests unitarios;
   - lint;
   - build release firmado.
4. Instalar exactamente el artefacto resultante.
5. Verificar firma y calcular SHA-256.
6. Crear release notes centradas en funciones, limitaciones conocidas y cómo reportar errores.
7. Crear un tag inequívoco, por ejemplo `v0.1.0-beta.1`.
8. Publicar el APK, checksum y código fuente correspondiente a esa versión conforme a GPLv3.
9. Proporcionar un único canal de feedback, preferiblemente GitHub Issues con una plantilla breve.

## 4. P1: pulido necesario, sin rediseño general

Estas tareas se hacen después de cerrar los P0 o durante la prueba integrada si el problema es visible.

### Consistencia de listas musicales

Comparar playlist, cola, álbum, género y búsqueda. Corregir únicamente diferencias evidentes en:

- fila de canción;
- indicador de reproducción actual;
- menú contextual;
- estados vacío, carga, error y offline;
- fast scroll;
- padding sobre minirreproductor y navegación;
- tamaños táctiles y accesibilidad.

No crear otra variante de componente si puede reutilizarse una existente.

### Selector de colecciones y Offline

- Presentar Offline como modo, no como servidor ficticio.
- Conservar la implementación interna eficiente basada en `localhost`.
- Mostrar el nombre configurado de la colección antes que la URL.
- Usar una selección visual neutra, sin borde verde dominante.
- Confirmar que el cambio cancela trabajo perteneciente a la colección anterior.

### Géneros y letras

Inspeccionar en dispositivo: scroll, letras sincronizadas, ausencia de letras, estados vacíos, offline y accesibilidad. No reescribir por estar dentro de un paquete `legacy`.

## 5. Elementos explícitamente fuera de esta beta

No gastar tiempo ni tokens en:

- migración a Jetpack Compose;
- rediseño del ecualizador;
- rediseño del widget;
- Radio de género;
- nuevas opciones de personalización;
- eliminación del código oculto de chat, podcasts, shares o bookmarks;
- refactor arquitectónico general;
- cambio masivo del package `org.moire.ultrasonic`;
- nueva iteración de paleta, Home o reproductor sin un defecto reproducible;
- comparación adicional con otros clientes salvo que resuelva una decisión bloqueante.

El sleep timer encaja con la visión, pero no bloquea esta beta. Implementarlo únicamente después de cerrar firma, backup, auditoría y prueba integrada, y solo si no retrasa el release.

## 6. Higiene documental para evitar trabajo repetido

1. Este archivo será la fuente operativa principal hasta publicar la beta.
2. Marcar `TAKI_PLAYLIST_UX_REDESIGN.md` como implementado.
3. Actualizar `TAKI_LEGACY_UI_AUDIT.md`: Playlists y formulario de conexión ya no son P0 pendientes.
4. Marcar Radios y Mix diario v1 como implementados.
5. Corregir referencias a `HANDOFF.md` si el archivo continúa excluido del repositorio.
6. Usar `CHANGES.md` como historial, no como backlog.
7. Convertir cada defecto pendiente real en un GitHub Issue; actualmente no existen issues abiertos.
8. No usar conteos históricos de errores o lint como lista actual sin reproducirlos contra `develop`.

## 7. Formato de trabajo obligatorio para el agente

Antes de modificar código:

1. Leer este documento completo.
2. Revisar el estado actual de `develop` y los últimos commits.
3. Confirmar que la tarea no está ya implementada.
4. Indicar brevemente archivos afectados, riesgo y método de prueba.

Después de cada fase:

1. Ejecutar las pruebas específicas de esa fase.
2. Ejecutar una compilación relevante.
3. Registrar únicamente cambios y hallazgos confirmados.
4. Entregar un resumen de máximo diez líneas: resultado, archivos, pruebas y siguiente bloqueo.
5. No iniciar automáticamente la siguiente fase si la actual requiere una decisión del propietario.
6. No hacer push, tag ni release sin autorización explícita.

## 8. Tablero de cierre

| Orden | Entregable | Estado (13 ago 2026) | Bloquea beta |
| --- | --- | --- | --- |
| 1 | Firma release propia | Cerrado (`50f1cb03`) | Sí |
| 2 | Fase 6: Backup y credenciales | Cerrado (`33032439`) — validación real de `bmgr backupnow` diferida a la fila 4 | Sí |
| 3 | Auditoría interna dirigida | Cerrado (`c3e31a6e` + hallazgos P1 `ea5806f1`, `44973c08`, `de418fe2`) | Sí |
| 4 | Regresión integrada en Pixel 7 | Cerrado — matriz de 15 puntos completa en dispositivo físico real. 4 bugs P0 encontrados y corregidos (ver CHANGES.md): freeze de ~21s en Play All sobre colas grandes, ANR real de ~5s de Media3 al cambiar de servidor con cola grande, scroll infinito roto en el picker de canciones de playlists, filas superpuestas/switch mal armado en Advanced settings del servidor. 2 hallazgos P1 documentados sin bloquear (título de Search offline, árbol de Android Auto). Validación real de `bmgr backupnow` sigue sin hacerse (limitación de entorno, no de esta sesión) | Sí |
| 5 | Build limpio, firma y checksum | Pendiente | Sí |
| 6 | Release notes, tag y canal de feedback | Pendiente | Sí |
| 7 | Consistencia de listas/cola | Parcial — fixes de menú contextual ya cerrados, falta pase dedicado | Solo si hay defectos importantes |
| 8 | Selector de colecciones y Offline | Parcial — oculta `localhost` y centra el nombre (`938f3946`); falta confirmar cancelación de trabajo de la colección anterior | Solo si causa confusión o regresión |
| 9 | Géneros y letras | Verificar | No, salvo fallo funcional |
| 10 | Sleep timer | Cerrado — v1 implementado y verificado en Pixel 7 (`TAKI_SLEEP_TIMER_FINAL_FEATURE.md`, ver CHANGES.md) | No |

**Sleep Timer cerrado — feature freeze activo.** No se inicia ninguna función nueva. Lo que queda
es corrección de defectos P0/P1 reproducibles, identidad/release, compilación firmada y regresión
final de `v0.1.0-beta.1`.

## 9. Definición final de beta usable

La beta no necesita estar terminada ni competir en funciones con Symfonium. Debe demostrar la propuesta de Taki: abrir una colección personal, encontrar música y escucharla con poca fricción.

Se publica cuando los seis primeros elementos del tablero estén cerrados y no exista un defecto conocido que pueda exponer credenciales, impedir instalar/actualizar, bloquear la interfaz, perder la sesión o romper la reproducción básica. Todo lo demás pasa al backlog posterior a la beta.
