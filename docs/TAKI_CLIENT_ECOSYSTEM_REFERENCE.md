# Taki — referencia del ecosistema de clientes Navidrome

**Estado:** referencia estratégica, funcional y técnica  
**Fecha de revisión:** 12 de agosto de 2026  
**Fuente principal:** [Catálogo de clientes compatibles de Navidrome](https://www.navidrome.org/apps/?platform=android)  
**Documento relacionado:** `TAKI_NAVIC_TECHNICAL_REFERENCE.md`

## 1. Propósito

Este documento identifica patrones útiles del ecosistema de clientes Navidrome/Subsonic/OpenSubsonic para mejorar Taki sin convertirla en un reproductor cargado de funciones y configuraciones.

No es una lista de funciones que deban copiarse. Sirve para responder cuatro preguntas:

1. ¿Qué problemas cotidianos ya resuelven bien otros clientes?
2. ¿Qué soluciones pueden reforzar la visión de Taki?
3. ¿Qué complejidad debe evitarse deliberadamente?
4. ¿Qué merece entrar antes de la primera beta?

El catálogo de Navidrome es un directorio de compatibilidad comunitario, no una certificación ni un ranking de calidad. Las funciones descritas por cada proyecto deben verificarse en código y pruebas antes de usarlas como referencia de implementación.

## 2. Visión que filtra todas las decisiones

> **Taki permite escuchar tu música sin distracciones. La app administra la conexión y la biblioteca para que la persona no tenga que administrar un reproductor.**

Taki no compite por:

- mayor número de funciones;
- máxima personalización;
- mayor cantidad de fuentes musicales;
- ecualización avanzada;
- estadísticas sociales o gamificadas;
- cobertura visible de toda la API.

Taki sí compite por:

- menor tiempo hasta escuchar música;
- continuidad entre online y offline;
- navegación clara;
- reproducción y cola confiables;
- descubrimiento inmediato y comprensible;
- lenguaje para personas no técnicas;
- valores predeterminados suficientemente buenos.

## 3. Mapa del ecosistema relevante

### 3.1 Clientes orientados a simplicidad o foco

| Cliente | Enfoque observable | Lección para Taki | Riesgo |
|---|---|---|---|
| Tempus | Cliente Android ligero con reproducción, offline, Auto e Instant Mix | Mantener un recorrido directo y estudiar su mezcla instantánea | Su alcance continúa creciendo con podcasts, radio e integraciones |
| Chora | Material 3, local/remoto, offline, letras y Android Auto | Referencia de alcance Android moderno relativamente contenido | “Simple” puede terminar significando solo menos funciones |
| Record Collection | Experiencia album-first | Una decisión editorial fuerte simplifica la navegación | No todas las personas escuchan por álbum |
| Subtracks | Acceso limpio y conveniente, vistas filtrables | Claridad de biblioteca antes que decoración | Proyecto menos reciente; no usarlo como referencia tecnológica |
| Yuzic | Interfaz mínima y bonita, offline | Validación de una demanda por clientes visualmente contenidos | La personalización puede crecer y diluir la propuesta |

### 3.2 Clientes orientados a amplitud y personalización

| Cliente | Fortaleza | Qué no trasladar a Taki |
|---|---|---|
| Symfonium | Calidad técnica, múltiples fuentes, audio avanzado y gran integración Android | La personalización como producto y la abundancia de decisiones |
| Navic | Arquitectura moderna, biblioteca local y gran cobertura Subsonic | Cobertura casi total de API y ajustes extensos |
| subStreamer | Descargas, analytics, mixes, ratings y adaptación por servidor | Rachas, heatmaps, ratings y constructor de mixes |
| Wavio | Amplia cobertura: local, Jellyfin, podcasts, radio, EQ y Auto | Convertir Taki en agregador universal |
| Vibrdrome | Audio, visualizadores, EQ, múltiples plataformas y servidores | Complejidad audiófila y personalización visual |
| Firmium | Muchas plataformas, Auto/Wear, EQ, temas, podcasts y smart radio | Crecimiento por plataformas y funciones antes de estabilizar el núcleo |

### 3.3 Clientes con aprendizajes especializados

| Cliente | Especialidad | Aprendizaje potencial |
|---|---|---|
| DSub/DSub2000 | Caché, conexión débil, gapless y cola | Reproducción resiliente y precarga transparente |
| play:Sub | Caché automática/manual, descubrimiento aleatorio e integración del sistema | Ocultar la administración de caché durante la escucha |
| Sonora | Smart mixes, Roadtrip DJ, sincronización y CarPlay | Descubrimiento presentado como una intención cotidiana |
| NaviPlayer/Musly | Material 3 y presentación moderna | Consistencia visual sin adoptar una estética genérica de streaming |
| Cadence | Producto deliberadamente limitado, incluso sin playlists | Eliminar funciones también puede ser una decisión de producto válida |
| SubSwift | Home mínima: recientes, novedades y búsqueda unificada | Una pantalla inicial puede ser útil sin ser extensa |

## 4. Patrones que Taki debería adoptar

### 4.1 Interfaz local-first para metadatos

La interfaz debería poder leer rápidamente una representación local de la biblioteca mientras una sincronización remota trabaja en segundo plano.

**Comportamiento deseado:**

- mostrar contenido existente al abrir;
- actualizar sin vaciar la pantalla;
- navegar álbumes, artistas y canciones sin depender de una respuesta inmediata;
- conservar estados de favoritos, disponibilidad y última actualización;
- usar el stream remoto solo cuando el contenido no esté descargado.

**Referencias:** Navic por su sincronización local; DSub por su resistencia mediante caché.

**Restricción:** no retirar el servidor offline interno de Taki hasta que la nueva ruta alcance paridad de velocidad y navegación.

### 4.2 Caché y precarga transparentes

La persona no debería decidir entre “stream” y “archivo descargado” durante la reproducción normal.

**Reglas propuestas:**

1. Preferir la copia local válida cuando exista.
2. Precargar la siguiente canción si la configuración de datos lo permite.
3. Mantener una caché temporal con límite razonable.
4. Diferenciar internamente caché temporal y descarga elegida por el usuario.
5. Recuperar descargas incompletas y conservar la cola de trabajo.
6. No mostrar progreso persistente salvo que sea útil para decidir o diagnosticar.

**Referencias:** DSub, play:Sub y subStreamer.

### 4.3 Radio con una sola acción

Tempus declara usar `similarSongs` y `similarSongs2` para producir un Instant Mix más amplio. Taki debería auditar su estrategia como referencia técnica.

**Experiencia Taki:**

- `Radio de esta canción`;
- `Radio de este artista`;
- cola inicial de 30–50 canciones;
- sin parámetros previos;
- opción posterior de regenerar;
- deduplicación y límites por artista/álbum;
- fallback cuando el servidor devuelve pocos resultados.

**No confundir:** las “radios” de algunos clientes son estaciones de internet del servidor, no recomendaciones musicales.

### 4.4 Mix diario editorial

El Mix diario no debe depender de elegir un género al azar.

Composición inicial aproximada:

- 50% familiar: favoritos, escuchadas o reproducidas recientemente;
- 30% redescubrimiento: conocidas, pero ausentes durante un periodo significativo;
- 20% exploración: poco reproducidas, nuevas o relacionadas.

Los porcentajes son una dirección, no cuotas rígidas. En bibliotecas pequeñas o sin historial deben adaptarse sin duplicar contenido.

**Referencias:** Sonora para presentar la mezcla como contexto; subStreamer para variedad; Tempus para endpoints similares.

### 4.5 Degradación por capacidades

Las funciones deben ajustarse al servidor sin obligar al usuario a entender OpenSubsonic.

```text
capacidad verificada y resultado suficiente
→ usar respuesta del servidor

capacidad ausente o resultado escaso
→ combinar metadatos e historial local

biblioteca pequeña o metadatos incompletos
→ aleatorio controlado y diverso

sin resultado útil
→ explicación simple + acción alternativa
```

**Referencia:** subStreamer declara adaptar funciones según el servidor; Navic sirve como referencia de separación de API.

### 4.6 Sesión única para todos los controles

La misma reproducción debe gobernar:

- pantalla Now Playing;
- miniplayer;
- notificación;
- pantalla de bloqueo;
- Bluetooth;
- Android Auto;
- restauración después de cerrar la app.

El estado no debe divergir entre superficies. Media3 es la dirección técnica natural en Android, pero una migración debe conservar el comportamiento probado de Ultrasonic.

### 4.7 Android Auto diseñado por contexto

Android Auto no debe replicar toda la aplicación.

Prioridad de navegación:

1. Continuar escuchando.
2. Mix diario.
3. Favoritos.
4. Playlists.
5. Descargadas o música disponible localmente.
6. Radio o descubrimiento seguro cuando la plataforma lo permita.

Las acciones deben ser grandes, predecibles y pocas. Búsqueda por voz se considera posterior a una validación sólida de reproducción, cola y navegación.

### 4.8 Lenguaje humano y diagnóstico progresivo

La aplicación debe comunicar primero el efecto y la acción disponible:

| Situación interna | Mensaje principal | Acción |
|---|---|---|
| Sin red | No tienes conexión | Escuchar música en este dispositivo |
| Servidor no responde | Tu colección no está disponible ahora | Usar música en este dispositivo |
| Credenciales inválidas | No pudimos acceder a esta colección | Revisar acceso |
| Archivo incompatible | Esta canción no se puede reproducir | Ver diagnóstico |
| Descarga incompleta | No se terminó de descargar | Reintentar |

URLs, códigos HTTP y logs solo aparecen en `Ver diagnóstico` y pueden copiarse para enviarlos al administrador.

## 5. Decisiones editoriales para las pantallas

### Home

Debe responder: **“¿Qué puedo escuchar ahora?”**

Contenido recomendado:

- continuar escuchando;
- Mix diario;
- escuchado recientemente;
- añadido recientemente;
- una entrada discreta para `Sorpréndeme` si demuestra utilidad.

No debe convertirse en un dashboard de estadísticas.

### Library

Debe responder: **“¿Dónde está mi música?”**

- favoritos;
- playlists;
- álbumes;
- artistas;
- canciones;
- géneros, si sigue siendo útil en pruebas.

Mostrar el nombre configurado de la colección, no una etiqueta redundante como `Your library` ni la URL del servidor.

### Search

Debe responder: **“¿Cómo encuentro algo concreto?”**

- búsqueda unificada;
- resultados por canción, álbum y artista;
- búsquedas recientes cuando el campo está vacío;
- contenido local cuando no haya conexión;
- navegación inferior oculta o adaptada cuando el teclado reduzca demasiado el espacio útil.

### Now Playing

Debe responder: **“¿Qué está sonando y qué necesito controlar?”**

- portada;
- título y artista;
- progreso y tiempos discretos;
- controles principales claramente alineados;
- favorito;
- cola y letras como acciones secundarias;
- Radio de canción en el menú contextual.

## 6. Funciones que Taki debe rechazar o posponer

### Rechazar como dirección de producto

- personalización completa de pestañas y estructura;
- decenas de temas y acentos;
- estadísticas gamificadas, rachas y heatmaps;
- funciones sociales;
- visualizadores como elemento central;
- múltiples fuentes de medios solo para ampliar compatibilidad;
- lenguaje técnico expuesto permanentemente.

### Posponer hasta que exista evidencia de demanda

- ecualizador avanzado;
- casting múltiple;
- Wear OS y Android TV;
- podcasts;
- constructor de mixes;
- radio por género;
- letras de proveedores externos;
- búsqueda por voz avanzada;
- iOS/Kotlin Multiplatform.

### Mantener disponible, pero fuera del camino

- transcoding;
- calidad por Wi‑Fi/datos móviles;
- certificados y opciones de red;
- almacenamiento y limpieza de caché;
- diagnóstico;
- configuración avanzada del servidor.

## 7. Priorización para la primera beta

### P0 — bloqueantes

- release firmada y actualizable;
- credenciales fuera de Android Backup y logs;
- conexión limpia con Navidrome;
- reproducción, pausa, salto y seek confiables;
- cola y sesión persistentes;
- Home, Library y Search sin bloqueo del hilo principal;
- online → offline → online sin pérdida de sesión;
- notificación, Bluetooth y Android Auto verificados.

### P1 — parte de la propuesta beta

- Radio de canción;
- Radio de artista;
- Mix diario consistente;
- búsquedas recientes;
- progreso sutil en miniplayer;
- lenguaje no técnico y diagnóstico bajo demanda;
- recuperación de descargas incompletas.

### P2 — posterior a feedback

- sincronización local-first más profunda;
- migración gradual a Compose;
- optimización de tablet;
- ReplayGain/gapless si hay fallos demostrados;
- traducciones comunitarias;
- publicación en F-Droid/Play y catálogo de Navidrome.

## 8. Criterio para aceptar una nueva función

Antes de implementar una solicitud, responder:

1. ¿Ayuda a encontrar, reproducir o continuar música?
2. ¿Funciona bien sin configuración?
3. ¿Reduce pasos o añade decisiones?
4. ¿La mayoría de usuarios la necesita visible?
5. ¿Puede resolverse automáticamente o quedar en opciones avanzadas?
6. ¿Añade carga al servidor o al dispositivo?
7. ¿Funciona offline o degrada de manera comprensible?
8. ¿Cómo se probará y cómo sabremos si mejora la experiencia?

Una función no entra solo porque otro cliente la ofrece.

## 9. Método para estudiar otro cliente

No copiar una captura o una descripción promocional. Para cada patrón:

1. identificar el problema concreto;
2. comprobar el comportamiento real en una build publicada;
3. revisar código y licencia si el proyecto es abierto;
4. separar interfaz, algoritmo y arquitectura;
5. medir si el patrón mejora Taki;
6. registrar procedencia si se reutiliza código;
7. realizar pruebas contra Navidrome y una biblioteca pequeña;
8. descartar la idea si aumenta la carga cognitiva sin un beneficio claro.

## 10. Auditorías técnicas sugeridas

### Tempus — Radio y Mix

Revisar:

- uso de `similarSongs` y `similarSongs2`;
- tamaño y expansión de candidatos;
- deduplicación;
- límites por artista/álbum;
- fallback y errores;
- creación e inserción de cola;
- licencia y atribución de cualquier fragmento reutilizado.

### DSub — caché y continuidad

Revisar:

- precarga;
- política de caché;
- recuperación tras conexión inestable;
- cambio de red durante reproducción;
- persistencia de cola.

### subStreamer — descargas y capacidades

Revisar:

- recuperación de cola de descarga;
- scrobbling offline;
- detección de servidor no disponible;
- adaptación por capacidades;
- comportamiento con bibliotecas pequeñas.

### Navic — arquitectura

Usar el documento `TAKI_NAVIC_TECHNICAL_REFERENCE.md` para:

- biblioteca local;
- separación DTO/entidad/dominio/UI;
- repositorios;
- Media3;
- futura migración a Compose.

## 11. Criterios de éxito

La aplicación de estas referencias será exitosa si:

- Taki abre y muestra contenido útil rápidamente;
- empezar a escuchar requiere menos pasos que en los clientes comparados;
- el usuario no necesita configurar caché, servidor o recomendaciones para comenzar;
- una pérdida de conexión no convierte la app en una pantalla vacía;
- Radio y Mix producen colas suficientemente largas, variadas y reproducibles;
- las superficies de reproducción permanecen sincronizadas;
- los errores ofrecen una acción antes que información técnica;
- los testers describen Taki como sencilla y fluida, no como “otra app con menos funciones”;
- el número de ajustes visibles no crece sin evidencia.

## 12. Instrucción sugerida para el agente

> Usa `TAKI_CLIENT_ECOSYSTEM_REFERENCE.md` como filtro de producto y comportamiento. No implementes todas las funciones observadas en otros clientes. Antes de cada cambio, identifica el problema que resuelve, verifica la capacidad real de Taki y del servidor, y demuestra que reduce fricción o mejora continuidad. Para la primera beta prioriza firma y seguridad, reproducción/cola, online–offline, Radio de canción, Radio de artista, Mix diario, búsquedas recientes, recuperación de descargas y Android Auto. Conserva el modo offline explícito hasta que una arquitectura local-first alcance paridad. Mantén configuraciones técnicas fuera del flujo principal y documenta pruebas, fallbacks y procedencia de cualquier código reutilizado.

## 13. Referencias

- [Catálogo de clientes compatibles de Navidrome](https://www.navidrome.org/apps/?platform=android)
- [Guía para añadir aplicaciones al catálogo](https://www.navidrome.org/docs/developers/adding-apps/)
- [Tempus](https://github.com/eddyizm/tempus)
- [Navic](https://github.com/ssalggnikool/Navic)
- [subStreamer](https://substreamer.org/)
- Documento interno `TAKI_NAVIC_TECHNICAL_REFERENCE.md`

Revisar este documento antes de ampliar el alcance de Taki o después de obtener feedback sustancial de la beta.
