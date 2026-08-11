# Investigación de viabilidad: Ultrasonic, Navidrome y oportunidad de mercado

**Fecha:** 9 de agosto de 2026  
**Alcance:** evaluación preliminar de las propuestas recogidas en `DIRECCION_EXPERIENCIA_Y_CONECTIVIDAD.md` frente a la API Subsonic/OpenSubsonic, Navidrome, el estado público de Ultrasonic y el mercado actual de clientes musicales personales.

## 1. Cómo leer este documento

Esta investigación determina qué parece posible, qué ya está soportado públicamente y qué requiere revisar directamente en el fork. No sustituye una auditoría del repositorio privado ni pruebas contra el servidor real.

### Niveles de evidencia

| Nivel | Significado |
| --- | --- |
| **Confirmado por API/servidor** | La documentación oficial define la capacidad y Navidrome declara soporte. |
| **Confirmado públicamente en Ultrasonic** | La ficha, changelog o documentación pública de Ultrasonic declara la capacidad. |
| **Inferencia técnica fuerte** | Android o la arquitectura descrita permiten hacerlo, pero falta comprobar el código del fork. |
| **Hipótesis** | Es una dirección razonable, pero depende de decisiones o capacidades aún no verificadas. |

### Escala de esfuerzo

| Esfuerzo | Interpretación |
| --- | --- |
| **Bajo** | Principalmente UI, textos o reutilización de una capacidad existente. |
| **Medio** | Cambios acotados en red, persistencia, servicio o varias pantallas. |
| **Alto** | Cambios transversales en datos, reproducción, sincronización y estados offline. |
| **Muy alto** | Replanteamiento arquitectónico con riesgo amplio de regresiones. |

---

## 2. Resumen ejecutivo

### Conclusión técnica

La mayoría de la visión es técnicamente posible con Navidrome y la familia Subsonic/OpenSubsonic. La API ya cubre:

- Biblioteca por artistas, álbumes, canciones y géneros.
- Búsqueda y listas de álbumes/canciones.
- Streaming y descarga.
- Transcodificación y límite de bitrate.
- Portadas, letras y metadatos enriquecidos.
- Playlists.
- Favoritos y ratings.
- Scrobbling e historial de reproducción.
- Guardar y recuperar cola, canción actual y posición.

Navidrome declara compatibilidad con Subsonic API 1.16.1, con excepciones documentadas, y soporta explícitamente playlists, favoritos, ratings, descarga, streaming, cola persistente y scrobbling. La limitación central no es la API: es que Ultrasonic parece separar con fuerza el servicio online del modo offline.

### Conclusión de producto

La visión es coherente, pero todavía no es una diferenciación suficiente por sí sola. El mercado ya ofrece muchos clientes con interfaz moderna, offline y Android Auto. Incluso Tempo declara una filosofía cercana: no usar “algoritmos mágicos”, sino historial y aleatoriedad.

La diferenciación defendible tendría que ser la combinación de:

1. **Escucha centrada en la colección.**
2. **Continuidad local/remota invisible.**
3. **Experiencia diseñada para el invitado, no para el administrador.**
4. **Lenguaje sencillo y diagnóstico enviable al administrador.**
5. **Menor trabajo innecesario para servidores domésticos.**

### Conclusión de mercado

Existe un nicho real y activo, pero no parece un mercado masivo independiente:

- Navidrome tiene aproximadamente 22.8 mil estrellas en GitHub.
- Su directorio oficial enumera 82 clientes y reproductores.
- Ultrasonic registra más de 50 mil descargas en Google Play.
- Tempo tiene aproximadamente 2.2 mil estrellas en GitHub.
- Symfonium muestra más de 9 mil reseñas en Google Play y declara cientos de miles de instalaciones.

Esto prueba demanda, pero también competencia intensa. Una app bien ejecutada podría tener buena acogida dentro del ecosistema self-hosted; esperar adopción masiva comparable a Spotify no sería realista sin resolver también instalación, acceso remoto y onboarding del servidor.

---

## 3. Qué permite realmente Navidrome/OpenSubsonic

### Capacidades confirmadas

La documentación de [OpenSubsonic](https://opensubsonic.netlify.app/docs/opensubsonic-api/) define endpoints para:

- `getArtists`, `getArtist`, `getAlbum`, `getSong`.
- `getAlbumList2`, `getRandomSongs`, `getSongsByGenre`.
- `search3`.
- `getPlaylists`, `getPlaylist`, `createPlaylist`, `updatePlaylist`, `deletePlaylist`.
- `stream`, `download`, `getCoverArt` y letras.
- `star`, `unstar`, `setRating`, `scrobble`.
- `getPlayQueue`, `savePlayQueue`.

Navidrome publica una [matriz de compatibilidad](https://www.navidrome.org/docs/developers/subsonic-api/) que confirma soporte de estos endpoints y describe sus excepciones.

### Limitaciones relevantes

1. **No hay navegación real por carpetas.** Navidrome la simula a partir de tags.
2. **`stream` no incrementa reproducciones.** El cliente debe llamar `scrobble` correctamente.
3. **Los IDs son strings.** No deben convertirse a enteros.
4. **Biografías, top songs y similares dependen de integraciones externas.** La pantalla debe fallar suavemente.
5. **La búsqueda es autocomplete simple.** Navidrome no soporta consultas Lucene en `search2/search3`.
6. **La compatibilidad entre servidores no es idéntica.** Una función disponible en Navidrome puede faltar o comportarse distinto en Airsonic, Ampache, Gonic u otros.
7. **La API no ofrece una sincronización incremental universal de toda la biblioteca.** No existe un endpoint estándar equivalente a “dame todos los cambios desde esta versión”.

### Implicación

La app puede ser Navidrome-first o Subsonic-compatible, pero no puede prometer el mismo comportamiento avanzado en todos los servidores sin una capa de capacidades y degradación progresiva.

---

## 4. Matriz de viabilidad de las propuestas

| Propuesta | Viabilidad | Evidencia | Esfuerzo estimado | Principal límite |
| --- | --- | --- | --- | --- |
| Lenguaje sencillo en conexión y errores | **Alta** | Controlado por la app | Bajo–medio | Mapear errores reales sin inventar causas |
| Preferir descarga válida al streaming | **Alta** | Ambos recursos existen | Medio | Confirmar resolución actual del reproductor |
| Mostrar contenido local sin cambiar de servidor | **Posible, compleja** | Android/local DB lo permiten | Alto–muy alto | Separación online/offline de Ultrasonic |
| Volver automáticamente a biblioteca completa | **Alta** | Control de estado cliente | Medio–alto | Evitar refrescos duplicados y saltos visuales |
| Home local-first | **Parcialmente viable** | Depende de DB/caché local | Alto | Home puede depender de endpoints remotos |
| Metadatos locales y enriquecimiento remoto | **Alta** | API + DB/caché | Medio–alto | Qué metadatos persiste Ultrasonic hoy |
| Cola persistente local | **Alta** | Persistencia de cliente | Medio | Ciclo de vida del reproductor |
| Cola sincronizada con Navidrome | **Alta** | `getPlayQueue/savePlayQueue` | Medio | Conflictos entre dispositivos/clientes |
| Reintentos fiables de descargas | **Alta** | Android permite trabajo persistente | Medio–alto | Reanudación parcial y servicio actual |
| Respetar Wi-Fi/datos móviles | **Alta** | Preferencia existente según HANDOFF | Bajo–medio | Verificar todos los caminos de descarga |
| Diagnóstico sanitizado y copiable | **Alta** | Controlado por cliente | Medio | Redacción segura de URLs/tokens |
| Distinguir internet vs biblioteca inaccesible | **Alta con cautela** | Red + `ping` | Medio | Tener red no garantiza internet ni causa exacta |
| Fav/ratings offline con sincronización posterior | **Posible** | API soporta escritura online | Alto | La API no provee cola offline ni conflictos |
| Edición offline de playlists | **Posible, arriesgada** | API soporta playlists online | Alto–muy alto | Orden, conflictos y canciones eliminadas |
| Conservar descarga de canción borrada del servidor | **Posible** | Decisión local | Medio–alto | Relaciones DB, IDs y limpieza |
| Gestión de almacenamiento clara | **Alta** | Android/filesystem | Medio | Varias ubicaciones y capas de caché |
| Android Auto | **Ya existe** | Ultrasonic/F-Droid | Auditoría + medio | APK privado, catálogo y offline |
| QR/enlace de invitación | **No estándar** | Sin endpoint de invitación universal | Alto | Credenciales, seguridad y servidor/admin |
| Reducir llamadas mediante caché | **Alta** | Cliente controla caché | Medio–alto | Invalidación sin delta sync universal |
| Sincronización completa incremental | **Limitada** | Falta API estándar de cambios | Muy alto | Requeriría heurísticas o Navidrome específico |

---

## 5. Evaluación detallada

### 5.1 Lenguaje sencillo y onboarding

**Resultado: viable y recomendable.**

La app puede traducir fallos de transporte y respuestas Subsonic a mensajes comprensibles. La API dispone de `ping` y respuestas de error; la capa HTTP puede distinguir DNS, timeout, TLS, conexión rechazada y autenticación.

Lo que no puede hacer con certeza es inferir causas externas. Por ejemplo, una biblioteca inaccesible no demuestra que Tailscale esté apagado o que el servidor físico esté apagado.

**Recomendación:** implementar una taxonomía interna de errores y mapearla a mensajes humanos. Mantener la causa técnica en `Ver diagnóstico`.

### 5.2 Preferir descargas para reproducir

**Resultado: técnicamente viable; requiere auditoría acotada.**

La API ofrece `download` y `stream`. Navidrome además permite descargar canciones, álbumes, artistas y playlists, y puede aplicar opciones de transcodificación. Una vez que existe un archivo local válido, elegirlo antes que la URL remota es una decisión del cliente.

No se ha confirmado en esta investigación si Ultrasonic ya lo hace en todos los caminos: cola online, Android Auto, playlists y apertura directa de una canción.

**Recomendación:** primera auditoría de código. Es una mejora de alto valor y menor alcance que rehacer Home offline.

### 5.3 Offline automático sin cambiar de servidor

**Resultado: posible, pero es el bloque más difícil.**

Según `HANDOFF.md`, Ultrasonic tiene un servidor Offline global que redirige la app a `OfflineMusicService`. Algunas pantallas, como Downloads, leen `offlineMetaDatabase` directamente sin activar ese modo. Esto demuestra que mezclar fuentes es posible de forma localizada, pero no confirma que toda la app pueda hacerlo sin una refactorización transversal.

Para conseguir la experiencia propuesta probablemente se necesite:

- Una capa de repositorio que exponga datos locales como fuente inmediata.
- Actualización remota opcional.
- Estado de disponibilidad por elemento.
- Resolución de reproducción local/remota.
- Estados parciales por sección.
- Recuperación automática cuando vuelva la biblioteca.

**Recomendación:** no implementar como un único proyecto. Empezar por reproducción, Album Detail descargado y Home parcial.

### 5.4 Home local-first

**Resultado: parcialmente viable.**

Algunas secciones tienen equivalentes locales; otras pueden depender enteramente del servidor:

- Descargas: viable localmente.
- Cola: viable si se persiste.
- Metadatos de álbumes descargados: probablemente viable.
- Recently Played: depende de lo conservado localmente o de scrobbles previos.
- Mix diario: solo puede reproducirse offline si sus canciones están descargadas y sus relaciones están guardadas.
- Recently Added/Discover: podrían mostrar caché anterior, pero no datos nuevos.

**Recomendación:** conservar Home y degradar por secciones. No prometer una réplica completa offline.

### 5.5 Cola y sesión

**Resultado: mucho más viable de lo esperado.**

OpenSubsonic define [`savePlayQueue`](https://opensubsonic.netlify.app/docs/endpoints/saveplayqueue/) y [`getPlayQueue`](https://opensubsonic.netlify.app/docs/endpoints/getplayqueue/). Guardan IDs de la cola, canción actual y posición. Navidrome confirma ambos endpoints.

Hay dos niveles posibles:

1. **Persistencia local:** restaura en el mismo teléfono incluso sin servidor.
2. **Persistencia remota:** permite continuar entre clientes/dispositivos.

Para la propuesta actual, la persistencia local debe liderar; la remota puede sincronizar cuando haya conexión. Esto evita depender del servidor al abrir la app.

**Riesgo:** otro cliente puede sobrescribir la cola remota. Los campos `changed` y `changedBy` ayudan a detectar qué versión es más reciente, pero todavía requiere una política.

### 5.6 Descargas y reintentos

**Resultado: viable, detalles sin confirmar.**

Android ofrece mecanismos de descarga persistente y restricciones por red. Ultrasonic 4.9.0 declara un foreground service de tipo `dataSync`, y F-Droid confirma descarga offline. Sin embargo, no se verificó si el servicio actual usa rangos HTTP, reanuda archivos parciales o reinicia desde cero.

**Recomendación:** inspeccionar `DownloadService`, formato temporal, escritura atómica y respuesta del servidor a `Range`. No migrar automáticamente a WorkManager o Media3 DownloadService; primero medir lo existente.

### 5.7 Acciones offline

**Resultado: la API permite la acción final, no la sincronización offline.**

Favoritos, ratings y playlists están soportados online. Para hacerlos offline se requiere una cola local de operaciones, identificadores estables, reintentos e idempotencia.

Favoritos son relativamente simples: estado final starred/unstarred. Playlists son más difíciles porque involucran orden, adiciones, eliminaciones y ediciones concurrentes.

**Recomendación:** diferir playlists offline. Si se aborda, empezar por favoritos con un indicador discreto de sincronización pendiente.

### 5.8 Diagnóstico

**Resultado: plenamente viable del lado del cliente.**

La app puede generar un resumen estructurado sin copiar logs crudos. Debe redactar tokens, contraseñas, parámetros y URLs. Es especialmente importante en Subsonic porque la autenticación puede viajar en parámetros de solicitud.

**Recomendación:** crear códigos de error estables y construir el diagnóstico desde eventos estructurados, no sanitizar un log arbitrario al final.

### 5.9 Optimización del servidor

**Resultado: viable con límites.**

Se puede reducir trabajo mediante:

- Caché local de metadatos y portadas.
- Cancelación de solicitudes obsoletas.
- Deduplicación de llamadas simultáneas.
- TTL por tipo de dato.
- Actualización por pantalla.
- Paginación.
- Preferencia por archivos descargados.
- Backoff tras fallos.

No es posible asumir sincronización incremental perfecta porque la API estándar no expone una revisión global de biblioteca ni todos los recursos ofrecen `changed`.

**Recomendación:** medir primero solicitudes por recorrido. Optimizar endpoints concretos antes de diseñar una sincronización universal.

### 5.10 Android Auto

**Resultado: capacidad existente que necesita auditoría.**

Ultrasonic documenta soporte de Android Auto. F-Droid 4.9.0 incluso registra una corrección para recordar la última canción cuando se reanuda desde Bluetooth o Android Auto. Las instalaciones fuera de Google Play pueden requerir habilitar fuentes desconocidas dentro de Android Auto.

**Recomendación:** probar el APK privado antes de escribir código. Después evaluar catálogo, descargas, restauración de cola, voz y cambios de red.

---

## 6. Compatibilidad específica con Navidrome

### Muy favorable

Navidrome está especialmente alineado con esta app porque:

- Es music-only; no implementa video.
- Es ligero y adecuado para hardware doméstico limitado.
- Maneja múltiples usuarios con favoritos, playlists y contadores propios.
- Soporta transcodificación por usuario/player.
- Soporta cola remota.
- Expone portadas, letras y metadatos.
- Tiene un ecosistema activo OpenSubsonic.

### Funciones que deben degradarse

- Biografía, artistas similares, top songs y descripciones requieren agentes externos.
- Las carpetas son simuladas.
- La búsqueda avanzada es limitada.
- El comportamiento de descargas transcodificadas depende de configuración del servidor.
- No todos los servidores compatibles implementan las extensiones OpenSubsonic.

### Estrategia recomendada

1. Detectar tipo, versión y extensiones al conectar.
2. Guardar un perfil de capacidades.
3. Mostrar únicamente funciones realmente soportadas.
4. Fallar suavemente si un endpoint opcional no responde.
5. No identificar Navidrome mediante suposiciones indirectas si la respuesta del servidor ya declara tipo/versión.

---

## 7. Estado competitivo del mercado

### Señales de demanda

| Señal | Lectura |
| --- | --- |
| Navidrome: ~22.8k estrellas | Comunidad self-hosted significativa y activa |
| 82 clientes en directorio Navidrome | Ecosistema amplio y fragmentado |
| Ultrasonic: 50k+ descargas Play Store | Base histórica real para un cliente Android |
| Tempo: ~2.2k estrellas | Interés fuerte en cliente Android moderno/FOSS |
| Symfonium: 9.25k reseñas en Play Store | Hay usuarios dispuestos a usar/pagar por una experiencia pulida |
| Substreamer: 733 reseñas y desarrollo activo | Demanda multiplataforma, pero también problemas de compatibilidad/offline |

Fuentes: [Navidrome GitHub](https://github.com/navidrome/navidrome), [directorio de clientes](https://www.navidrome.org/apps/), [Ultrasonic Google Play](https://play.google.com/store/apps/details?id=org.moire.ultrasonic), [Tempo GitHub](https://github.com/CappielloAntonio/tempo), [Symfonium Google Play](https://play.google.com/store/apps/details?id=app.symfonik.music.player), [Substreamer Google Play](https://play.google.com/store/apps/details?id=com.ghenry22.substream2).

### Competidores relevantes

#### Symfonium

Fortalezas:

- Muy pulido y estable.
- Múltiples fuentes: Subsonic, Jellyfin, Plex, local y otras.
- Offline, casting, Android Auto, Wear OS y audio avanzado.
- Gran cantidad de opciones.

Debilidad relativa frente a nuestra visión:

- Su amplitud y personalización pueden resultar abrumadoras para usuarios no técnicos.
- No está centrado únicamente en la experiencia “mi colección compartida por alguien cercano”.

#### Tempo

Fortalezas:

- Android nativo, Material, FOSS.
- Filosofía explícitamente contraria a recomendaciones mágicas.
- Android Auto, playlists, streaming y offline en desarrollo.
- Comunidad técnica visible.

Debilidad/oportunidad:

- Su propio README reconoce limitaciones offline y con múltiples servidores.
- Es el competidor conceptual más cercano; obliga a diferenciar por continuidad y simplicidad real, no solo estética.

#### Substreamer

Fortalezas:

- Android/iOS, gratuito, FOSS y actualizado.
- Interfaz moderna, offline, playlists y soporte amplio de servidores.
- Ya introduce cambio rápido cuando el servidor no responde.

Debilidad/oportunidad:

- Reseñas recientes todavía reportan fallos en playlists, compatibilidad y Android Auto pendiente.
- Demuestra que la compatibilidad amplia tiene un costo elevado de QA.

#### Ultrasonic original

Fortalezas:

- Base estable, ligera y madura.
- Offline, Android Auto, múltiples servidores y muchas funciones ya construidas.
- 50k+ descargas históricas.

Debilidad/oportunidad:

- Herencia de cliente técnico y feature-dense.
- Google Play muestra una versión antigua frente a F-Droid 4.9.0.
- Reseñas señalan problemas históricos de reanudación/cola y reproducción.

---

## 8. ¿Podría tener gran acogida?

### Respuesta corta

**Dentro del nicho self-hosted: sí, potencialmente. Como producto masivo: improbable en su forma actual.**

### Por qué podría gustar

- Existe frustración real con clientes antiguos, técnicos o inestables.
- Los usuarios valoran interfaces modernas y offline fiable.
- Muchos administradores comparten bibliotecas con familiares.
- La privacidad y propiedad de la colección tienen atractivo creciente.
- Ultrasonic aporta una base funcional que reduce tiempo inicial.
- La simplicidad puede contrastar con competidores altamente configurables.

### Por qué no está garantizado

- El nicho ya tiene 82 clientes registrados.
- Symfonium es un referente difícil de superar en funciones y estabilidad.
- Tempo ocupa un posicionamiento FOSS/minimalista cercano.
- La persona no técnica no instala Navidrome por sí sola; depende de un administrador.
- Acceso remoto, HTTPS/VPN, cuentas y distribución siguen siendo fricciones externas a la app.
- Mantener compatibilidad entre servidores y versiones consume mucho esfuerzo.
- Una app privada modificada no tiene canal de actualización, soporte o confianza pública.

### Estimación cualitativa

| Objetivo | Probabilidad |
| --- | --- |
| Ser excelente para Joseph, familia y amigos | Alta |
| Atraer una comunidad pequeña de entusiastas Navidrome | Moderada–alta si se publica y mantiene |
| Convertirse en cliente Android reconocido del nicho | Moderada; requiere estabilidad, distribución y foco |
| Superar a Symfonium por amplitud | Baja y estratégicamente innecesaria |
| Conseguir adopción masiva fuera del self-hosting | Baja sin simplificar también servidor/acceso remoto |

### La oportunidad real

No competir por tener más funciones. Competir por reducir el número de momentos en los que el usuario necesita entender que existe un servidor.

La prueba de diferenciación sería:

> Una persona recibe acceso de un familiar, conecta su biblioteca, escucha, descarga, entra al automóvil, pierde cobertura, vuelve a conectarse y resuelve fallos sin aprender Subsonic.

Si esa experiencia funciona mejor que en Symfonium, Tempo y Substreamer, hay una propuesta defendible.

---

## 9. Modelo de adopción probable

El usuario adquirente no es necesariamente el oyente. El administrador descubre e instala la solución; luego invita a otros.

Esto crea un modelo de difusión de dos niveles:

1. **Administrador:** evalúa compatibilidad, seguridad y mantenimiento.
2. **Oyente invitado:** evalúa sencillez, confiabilidad y música.

Por eso la app debe satisfacer simultáneamente:

- Diagnóstico y compatibilidad para quien administra.
- Invisibilidad técnica para quien escucha.

Una estrategia futura podría dirigirse a administradores con el mensaje “la app que sí puedes dar a tu familia”, mientras la interfaz habla únicamente al oyente.

---

## 10. Distribución, licencia y sostenibilidad

### Licencia

Ultrasonic está publicado bajo GPLv3. Mientras el fork permanezca privado y no se distribuya, el impacto práctico es limitado. Si se entregan APK a terceros o se publica, debe revisarse el cumplimiento de GPLv3 y ofrecer el código fuente correspondiente del trabajo derivado bajo las condiciones aplicables.

Esto no es asesoría legal; es una condición de proyecto que debe revisarse antes de cualquier publicación.

### Distribución

- Google Play reduce fricción, pero requiere cuenta, firma, políticas, actualizaciones y soporte.
- F-Droid encaja con FOSS, pero añade fricción para usuarios no técnicos y puede limitar componentes propietarios como integración completa de Android Auto/Chromecast.
- APK directo es simple para pruebas, pero dificulta confianza y actualizaciones.

### Mantenimiento

La acogida dependería menos del mockup y más de:

- Reproducción estable.
- Migraciones de base de datos seguras.
- Compatibilidad Android.
- Actualizaciones de Media3/Android Auto.
- Matriz real de servidores.
- Diagnóstico y respuesta a bugs.
- Proceso de release reproducible.

---

## 11. Riesgos técnicos principales

1. **Arquitectura offline dual:** mezclar `OfflineMusicService` y servicio remoto sin estados inconsistentes.
2. **Regresiones de reproducción:** cola, audio focus, Android Auto, Bluetooth y notificaciones están acoplados.
3. **Persistencia:** restaurar IDs que el servidor ya no reconoce.
4. **Compatibilidad:** diferencias entre Navidrome y otros servidores.
5. **Caché:** invalidación incorrecta o datos obsoletos.
6. **Seguridad:** tokens/credenciales en URLs y diagnósticos.
7. **Descargas:** archivos parciales, corrupción y almacenamiento lleno.
8. **Alcance:** convertir una mejora personal en una reescritura completa.

---

## 12. Orden recomendado de investigación e implementación

### Fase 0 — Auditoría del fork

1. Mapear `MusicServiceFactory`, `OnlineMusicService`, `OfflineMusicService` y `ActiveServerProvider`.
2. Mapear `offlineMetaDatabase` y qué entidades conserva.
3. Seguir una canción desde tap hasta resolución de URI local/remota.
4. Inspeccionar persistencia actual de cola.
5. Revisar `DownloadService`, temporales y reintentos.
6. Contar llamadas de Home y Album Detail.
7. Probar Android Auto.

### Fase 1 — Alto valor, menor riesgo

1. Taxonomía de errores y plain language.
2. Diagnóstico sanitizado.
3. Preferencia por descarga local.
4. Restauración local de cola/sesión.
5. Prueba y corrección de Android Auto.

### Fase 2 — Continuidad localizada

1. Album Detail descargado sin servidor.
2. Home con banner y acceso local.
3. Recuperación automática por sección.
4. Metadatos locales con enriquecimiento remoto.

### Fase 3 — Optimización

1. Deduplicación de solicitudes.
2. TTL por recurso.
3. Medición de transferencia y latencia.
4. Backoff y sincronización restringida.

### Fase 4 — Solo si sigue teniendo valor

1. Favoritos offline.
2. Cola remota entre dispositivos.
3. Conflictos y contenido eliminado.
4. Invitaciones o QR.
5. Compatibilidad amplia más allá de Navidrome.

---

## 13. Pruebas de mercado de bajo costo

Antes de pensar en publicación:

1. Dar la app a 3–5 personas que no administren el servidor.
2. No explicarles Subsonic, offline ni Tailscale durante la prueba.
3. Observar conexión inicial, búsqueda, reproducción, descarga y recuperación.
4. Desactivar el servidor sin avisar y observar qué entienden.
5. Probar en automóvil si es posible.
6. Registrar cada ocasión en la que piden ayuda al administrador.

Métrica principal:

> Número de intervenciones del administrador por usuario durante la primera semana.

Métricas secundarias:

- Tiempo hasta primera reproducción.
- Éxito al recuperar una sesión.
- Capacidad de encontrar descargas sin ayuda.
- Comprensión de mensajes de error.
- Fallos de reproducción por cambio de red.

Esto validaría mejor la propuesta que preguntar si “les gusta el diseño”.

---

## 14. Veredicto

La dirección es técnicamente plausible y está bien alineada con Navidrome. Aproximadamente:

- **Un tercio** de las ideas son principalmente diseño, lenguaje y reutilización de funciones existentes.
- **Un tercio** son mejoras medianas en persistencia, errores, descargas y servidor.
- **Un tercio** dependen de una evolución seria hacia una capa local-first.

No hay un bloqueo fundamental de API para la visión central. El principal riesgo es intentar resolver toda la continuidad offline de una vez sobre una arquitectura que hoy trata online y offline como modos separados.

En mercado, hay suficiente demanda para que una app muy buena sea apreciada, pero no un espacio vacío. La acogida dependería de ejecutar una promesa concreta mejor que los competidores:

> **Una biblioteca musical privada que cualquier invitado puede disfrutar sin convertirse en administrador del servidor.**

El proyecto tiene sentido incluso si nunca se publica. Si se publica, debería hacerlo después de demostrar esa experiencia con usuarios reales, no después de acumular más funciones.

---

## 15. Fuentes principales

- [Navidrome — Overview](https://www.navidrome.org/docs/overview/)
- [Navidrome — Subsonic API Compatibility](https://www.navidrome.org/docs/developers/subsonic-api/)
- [Navidrome — Client Apps](https://www.navidrome.org/apps/)
- [Navidrome — GitHub](https://github.com/navidrome/navidrome)
- [OpenSubsonic API](https://opensubsonic.netlify.app/docs/opensubsonic-api/)
- [OpenSubsonic — stream](https://opensubsonic.netlify.app/docs/endpoints/stream/)
- [OpenSubsonic — download](https://opensubsonic.netlify.app/docs/endpoints/download/)
- [OpenSubsonic — savePlayQueue](https://opensubsonic.netlify.app/docs/endpoints/saveplayqueue/)
- [OpenSubsonic — getPlayQueue](https://opensubsonic.netlify.app/docs/endpoints/getplayqueue/)
- [Ultrasonic — F-Droid](https://f-droid.org/packages/org.moire.ultrasonic/)
- [Ultrasonic — Google Play](https://play.google.com/store/apps/details?id=org.moire.ultrasonic)
- [Tempo — GitHub](https://github.com/CappielloAntonio/tempo)
- [Symfonium — Google Play](https://play.google.com/store/apps/details?id=app.symfonik.music.player)
- [Symfonium — sitio oficial](https://symfonium.app/)
- [Substreamer — Google Play](https://play.google.com/store/apps/details?id=com.ghenry22.substream2)
- [Android — Offline-first architecture](https://developer.android.com/topic/architecture/data-layer/offline-first)
- [Android — Persistent background work](https://developer.android.com/develop/background-work/background-tasks/persistent)
- [Android — Media downloads](https://developer.android.com/media/media3/exoplayer/downloading-media)
