# Taki — referencia técnica de Navic

**Estado del documento:** referencia estratégica y técnica  
**Fecha de revisión:** 12 de agosto de 2026  
**Proyecto observado:** [ssalggnikool/Navic](https://github.com/ssalggnikool/Navic)  
**Propósito:** identificar decisiones útiles para acercar Taki a su visión sin convertir Navic en una plantilla ni ampliar innecesariamente el alcance de la primera beta.

**Referencia complementaria:** consultar `TAKI_CLIENT_ECOSYSTEM_REFERENCE.md` para comparar patrones de Tempus, DSub, subStreamer, Record Collection, Sonora, Symfonium y otros clientes. Este documento permanece limitado a la arquitectura y las decisiones técnicas observadas en Navic.

## 1. Alcance y límites de esta referencia

Este documento describe lo observado en el repositorio público de Navic en la fecha indicada. No garantiza el comportamiento real de todas sus funciones, su estabilidad en producción ni su compatibilidad con todos los servidores. Las afirmaciones funcionales tomadas del README se consideran declaraciones del proyecto hasta ser verificadas mediante pruebas.

Navic se estudia porque resuelve problemas técnicos similares a Taki:

- conexión con Navidrome mediante Subsonic/OpenSubsonic;
- reproducción en Android;
- biblioteca disponible localmente;
- descargas y uso offline;
- interfaz moderna;
- privacidad y ausencia de telemetría;
- soporte para más de una plataforma.

No se propone copiar código, diseño ni comportamiento. Aunque ambos proyectos utilizan GPLv3, cualquier reutilización futura deberá conservar atribuciones, historial y cumplimiento de licencia. La opción preferida es aprender de patrones arquitectónicos y comprobarlos contra las necesidades reales de Taki.

## 2. Visión de Taki que debe gobernar las decisiones

> **Taki permite escuchar tu música sin distracciones y mantiene la infraestructura técnica fuera de la experiencia cotidiana.**

La persona usuaria debería poder:

1. abrir la app;
2. encontrar algo que escuchar;
3. iniciar o continuar la reproducción;
4. seguir escuchando cuando cambia la conectividad;
5. entender los errores sin conocer conceptos de servidores.

La eficiencia interna del cliente y del servidor es un medio para conseguir esa experiencia, no una función que deba exponerse.

### Principios no negociables

- Menos decisiones visibles, mejores valores predeterminados.
- Lenguaje cotidiano en lugar de terminología de infraestructura.
- Reproducción y cola confiables antes que abundancia de funciones.
- Biblioteca local coherente, no una experiencia offline reducida a “Descargas”.
- Conectividad y sincronización sin bloquear la interfaz.
- Radio y Mix como acciones inmediatas, no como constructores complejos.
- El modo offline explícito actual no se elimina sin demostrar una alternativa igual de rápida y completa.

## 3. Qué es Navic técnicamente

En el commit revisado, Navic es un cliente multiplataforma en desarrollo activo que declara cubrir gran parte de la API Subsonic y funcionar en Android e iOS. Su repositorio presenta aproximadamente 743 commits, 892 estrellas y una versión `1.0.0-alpha48`.

### Tecnologías observadas

| Capa | Navic | Utilidad potencial para Taki |
|---|---|---|
| Interfaz | Compose Multiplatform + Material 3 | Referencia para la futura migración gradual a Compose |
| Plataformas | Kotlin Multiplatform para Android e iOS | Posible dirección de largo plazo, no requisito de la beta |
| Red | Ktor | Ejemplo de cliente asíncrono compartido; Taki puede conservar Retrofit inicialmente |
| API | `dev.zt64.subsonic:subsonic-client` | Referencia para aislar Subsonic/OpenSubsonic detrás de una interfaz |
| Persistencia | Room 3 + SQLite | Referencia para una biblioteca local consultable y sincronizada |
| Reproducción Android | AndroidX Media3/ExoPlayer | Coincide con la modernización razonable del reproductor de Taki |
| Inyección | Koin | Evidencia de separación por módulos; no obliga a adoptar Koin |
| Preferencias | DataStore/Multiplatform Settings | Referencia para retirar gradualmente SharedPreferences sensibles |
| Imágenes | Coil 3 + Ktor | Posible patrón moderno de caché y carga de carátulas |
| Navegación | Navigation 3 y componentes adaptativos | Referencia futura para teléfono/tablet |
| Traducciones | Recursos Compose + Weblate | Referencia para organizar y ampliar traducciones comunitarias |

Navic usa versiones muy recientes e incluso alpha/beta de varias dependencias. Eso facilita experimentar, pero aumenta el riesgo de regresiones y mantenimiento. Taki no debe actualizar dependencias solo para igualar números de versión.

## 4. Diferencia estratégica entre Navic y Taki

Navic se describe simultáneamente como configurable, ligero y capaz de cubrir casi toda la API Subsonic. Su dirección parece ser un cliente moderno y completo.

Taki no debería competir por cobertura funcional. Su diferenciación debe surgir de la edición: elegir qué merece estar visible y qué debe resolverse automáticamente.

| Pregunta | Navic | Dirección recomendada para Taki |
|---|---|---|
| ¿Cuántas funciones mostrar? | Cobertura amplia | Solo las que mejoran escuchar música diariamente |
| ¿Cuánta configuración ofrecer? | Gran selección de ajustes | Valores predeterminados sólidos y ajustes técnicos progresivamente ocultos |
| ¿Cómo presentar el servidor? | Cliente explícito de Navidrome | “Colección” o nombre configurado, con diagnóstico bajo demanda |
| ¿Cómo presentar offline? | Sincronización local y descargas | La misma biblioteca, con disponibilidad local indicada sutilmente |
| ¿Cómo descubrir música? | Aleatorio, similares y funciones de API | Radio de canción, Radio de artista y Mix diario con una pulsación |
| ¿Cómo crecer? | Más cobertura y plataformas | Más confiabilidad, fluidez y claridad antes que amplitud |

## 5. Aprendizajes técnicos aplicables

### 5.1 Biblioteca local como fuente de lectura

Navic declara sincronizar la biblioteca completa localmente y utiliza Room. Este patrón coincide con la experiencia que Taki descubrió mediante su servidor offline interno: navegar contenido local resulta más rápido y completo que detectar automáticamente una falla y redirigir a Descargas.

#### Dirección para Taki

Evolucionar hacia una arquitectura **local-first para metadatos**:

1. La interfaz observa datos locales.
2. Un proceso de sincronización consulta el servidor fuera del hilo principal.
3. Los resultados válidos se guardan de manera transaccional.
4. La interfaz recibe las actualizaciones sin reconstruirse completamente.
5. La reproducción elige una copia descargada cuando existe; de lo contrario usa el servidor.

Esto no significa descargar toda la música. Se sincronizan metadatos, disponibilidad, favoritos y estado; el audio continúa bajo demanda o descargado explícitamente.

#### Beneficio para la visión

- Home y Library aparecen rápidamente.
- Las pérdidas breves de conexión no vacían las vistas.
- El usuario no debe decidir constantemente entre biblioteca online y offline.
- Se reducen llamadas redundantes al servidor.

### 5.2 Frontera clara alrededor de la API

Navic emplea un cliente Subsonic como dependencia separada. Taki debería continuar la migración ya iniciada hacia una frontera explícita:

```text
UI / ViewModel
      ↓
Casos de uso o repositorios
      ↓
Fuente local + fuente remota
      ↓
Adaptador Subsonic/OpenSubsonic
```

La interfaz nunca debería conocer `Call<T>`, `.execute()`, versiones concretas de la API ni detalles de Navidrome.

#### Reglas recomendadas

- Operaciones remotas suspendidas/cancelables.
- Una sola implementación decide capacidades del servidor.
- Cachear capacidades exitosas y reintentar fallas transitorias con TTL corto.
- Evitar solicitudes duplicadas mediante single-flight por clave.
- Tratar respuestas antiguas como obsoletas mediante identificadores de ejecución.
- Definir errores de dominio: sin red, colección no disponible, credenciales rechazadas, archivo incompatible y error desconocido.

### 5.3 Separación de modelos

Navic contiene modelos de dominio, entidades de base de datos, repositorios y mapeadores. Taki se beneficiará de la misma separación:

- **DTO/API:** representación exacta recibida del servidor.
- **Entidad local:** estructura optimizada para persistencia y sincronización.
- **Modelo de dominio:** lo que necesita la experiencia de reproducción.
- **Modelo de UI:** estado preparado para cada pantalla.

No conviene permitir que modelos generados por la API circulen directamente hasta las vistas. Esta separación hará más segura la migración a Compose porque la nueva UI podrá consumir estados estables sin reescribir red y almacenamiento simultáneamente.

### 5.4 Reproducción como subsistema independiente

Navic mantiene implementaciones de reproductor específicas por plataforma y usa Media3 en Android. Para Taki, el reproductor debe permanecer independiente de las pantallas y del servidor seleccionado.

El contrato debería permitir:

- observar canción, estado, posición y cola;
- reproducir, pausar, saltar y buscar;
- restaurar cola y posición después de cerrar la app;
- cambiar entre archivo local y stream sin recrear innecesariamente el reproductor;
- exponer la misma sesión a notificación, Bluetooth y Android Auto;
- registrar errores reproducibles sin mostrar detalles técnicos inicialmente.

La optimización ya realizada para no reconstruir ExoPlayer/OkHttp cuando el backend efectivo no cambia va en esta dirección.

### 5.5 Sincronización incremental

Una biblioteca local completa solo es útil si la sincronización es eficiente. El objetivo no debe ser descargar todo nuevamente al abrir la app.

Aplicar cuando la API y el servidor lo permitan:

- marcas de modificación (`ifModifiedSince` o equivalentes);
- paginación;
- actualización por tipos de entidad;
- transacciones pequeñas y coherentes;
- índices de base de datos para búsquedas y ordenamientos frecuentes;
- trabajos cancelables y reanudables;
- estado de última sincronización por colección;
- invalidación selectiva al cambiar de servidor.

La app debe mostrar datos existentes mientras actualiza. Solo la primera conexión justifica un estado vacío de carga.

### 5.6 Capacidades variables del servidor

Navic declara una cobertura extensa de Subsonic. Taki debe evitar asumir que todos los servidores implementan lo mismo.

Para cada función avanzada:

1. comprobar capacidad OpenSubsonic declarada cuando exista;
2. probar de forma segura si la declaración no es suficiente;
3. cachear el resultado por servidor;
4. usar una alternativa local cuando sea posible;
5. ocultar o reformular la función si no puede ofrecer un resultado útil.

Esto es especialmente importante para letras sincronizadas, canciones similares, artistas relacionados, ratings y radio.

## 6. Radio y Mix a partir de esta arquitectura

Los archivos `radio` encontrados en Navic parecen representar principalmente estaciones de radio de internet de la API Subsonic. No deben interpretarse automáticamente como un motor de recomendaciones.

Taki puede construir su propuesta con un `RecommendationRepository` independiente de las pantallas y del proveedor concreto.

### Contrato conceptual

```kotlin
interface RecommendationRepository {
    suspend fun songRadio(seedSongId: String, limit: Int): List<Song>
    suspend fun artistRadio(seedArtistId: String, limit: Int): List<Song>
    suspend fun dailyMix(limit: Int): List<Song>
}
```

### Estrategia por niveles

1. **Datos específicos del servidor:** similares y top songs si están disponibles.
2. **Metadatos locales:** artista, álbum, género, año, favoritos, reproducciones y última escucha.
3. **Selección aleatoria controlada:** respaldo cuando los datos son pobres.
4. **Reglas de diversidad:** evitar duplicados y concentración excesiva por artista o álbum.

### Alcance previo a la primera beta

- Radio de canción.
- Radio de artista.
- Mix diario de 30–50 canciones.
- Regeneración de la selección.
- Generación fuera del hilo principal.
- Resultado útil en bibliotecas pequeñas.
- Mensaje sencillo cuando faltan datos.
- Sin constructor de mixes.
- Sin Radio de género por ahora.

### Mix diario inicial

Objetivo aproximado, no cuota rígida:

- 50% música familiar o reproducida recientemente;
- 30% redescubrimiento de música conocida pero poco escuchada últimamente;
- 20% exploración de la biblioteca.

La mezcla debe adaptarse cuando falte historial. No debe completar porcentajes con duplicados ni inventar una precisión que los datos no permiten.

## 7. Modo offline: qué conservar y qué evolucionar

### Conservar para la beta

- El modo offline explícito basado internamente en `http://localhost`.
- La experiencia completa de biblioteca local.
- La rapidez de entrada que ya ha demostrado.
- La posibilidad de elegirlo desde el selector de colección.

### Cambiar en la presentación

- No mostrar `localhost`.
- Nombre principal: **Música en este dispositivo** o **Sin conexión**.
- Estado secundario: cantidad de canciones disponibles o última actualización.
- Cuando el servidor regrese, ofrecer reconexión no disruptiva.

### Evolución posterior

El servidor offline interno puede convertirse gradualmente en una implementación local del mismo repositorio de biblioteca. Solo debe retirarse cuando la nueva ruta:

- abra igual o más rápido;
- muestre álbumes, artistas, canciones y playlists locales;
- conserve cola y sesión;
- no dependa de esperar un timeout de red;
- pase pruebas online → offline → online.

## 8. Migración a Compose inspirada por Navic

Navic demuestra que Compose puede sostener una app completa, adaptativa y multiplataforma. No demuestra que Taki deba reescribirse de una sola vez.

### Condiciones previas

- Fase 7 cerrada con llamadas bloqueantes críticas eliminadas o justificadas.
- Repositorios y estados de dominio independientes de Fragment/Activity.
- Reproducción observable desde un contrato estable.
- Flujos críticos cubiertos por pruebas.
- Primera beta funcional distribuida.

### Secuencia recomendada

1. Definir tokens de color, tipografía, espacios, radios y estados.
2. Crear componentes Compose aislados dentro de pantallas actuales.
3. Migrar pantallas de bajo riesgo: About, Settings secundarios y estados vacíos.
4. Migrar Search y Library cuando sus ViewModels ya expongan estados estables.
5. Migrar Home.
6. Migrar Album y Artist Detail.
7. Migrar Now Playing al final, por su relación con gestos, cola y sesión.
8. Retirar XML solo cuando cada flujo tenga paridad funcional y pruebas.

### Qué no adoptar todavía

- Kotlin Multiplatform solo para obtener iOS.
- Navigation 3 alpha si no resuelve un problema inmediato.
- Dependencias alpha/beta sin justificación.
- Reescritura simultánea de API, base de datos, reproducción e interfaz.

## 9. Decisiones antes de la beta

### Adoptar ahora

- Completar la migración suspend/cancelable de endpoints críticos.
- Terminar Radio de canción, Radio de artista y Mix diario como MVP.
- Mantener biblioteca offline completa.
- Persistir y restaurar cola/sesión.
- Verificar Bluetooth, notificación y Android Auto.
- Cerrar firma de release.
- Excluir credenciales de Android Backup y protegerlas correctamente.
- Medir inicio, carga de Home, búsqueda y cambio de colección.
- Añadir errores de dominio y diagnóstico bajo demanda.

### Preparar, sin reescribir todavía

- Interfaces de repositorio independientes de Android Views.
- Modelos de dominio separados de DTO y entidades.
- Estado de UI observable y estable.
- Tokens de diseño reutilizables por Views y Compose.
- Inventario de SharedPreferences que deberán migrar a DataStore.

### Posponer

- iOS y Kotlin Multiplatform.
- Constructor avanzado de mixes.
- Estadísticas de escucha y rachas.
- Temas y acentos configurables extensos.
- Radio de género.
- Cobertura total de la API por el solo hecho de existir.

## 10. Riesgos y controles

| Riesgo | Control recomendado |
|---|---|
| Convertir la referencia en una reescritura | Toda tarea debe vincularse con un problema observable de Taki |
| Copiar complejidad de Navic | Mantener una lista explícita de funciones que Taki no ofrecerá |
| Romper offline durante la modernización | Pruebas obligatorias online/offline y conservación temporal de localhost |
| Duplicar bases de verdad | Definir autoridad por dato y reglas de sincronización |
| Depender de funciones exclusivas de Navidrome | Capabilities + fallback + degradación clara |
| Adoptar librerías inestables | Preferir versiones estables compatibles con el proyecto actual |
| Hacer recomendaciones lentas | Cálculo cancelable, caché de resultados y límites de candidatos |
| Saturar el servidor | Paginación, TTL, single-flight, sincronización incremental y backoff |
| Perder estabilidad heredada de Ultrasonic | Migración por capas con pruebas de regresión |

## 11. Criterios de aceptación de la dirección técnica

La modernización acerca Taki a su visión si produce resultados verificables:

- La app muestra contenido útil rápidamente con y sin conexión.
- Ninguna llamada de red bloquea el hilo principal.
- Cambiar entre colección remota y música local no reinicia innecesariamente la reproducción.
- El usuario no ve URLs, localhost, códigos HTTP o trazas salvo que abra Diagnóstico.
- Radio y Mix generan una cola diversa y reproducible en tiempo razonable.
- La cola y la canción sobreviven al cierre y reapertura.
- La primera beta release se firma y puede actualizarse posteriormente.
- Las credenciales no entran en backups ni logs.
- Android Auto, Bluetooth y controles del sistema usan la misma sesión.
- La cantidad de ajustes visibles no aumenta sin una necesidad demostrada por testers.

## 12. Instrucción sugerida para el agente

> Usa `TAKI_NAVIC_TECHNICAL_REFERENCE.md` como referencia arquitectónica, no como instrucción para copiar o reescribir Navic. Antes de implementar cualquier patrón, verifica el estado actual de Taki y demuestra qué problema observable resuelve. Conserva el modo offline explícito y su biblioteca local completa. Prioriza cerrar la optimización suspend/cancelable, Radio de canción, Radio de artista, Mix diario, seguridad y release beta. Prepara fronteras de repositorio y modelos de dominio que faciliten una migración gradual a Compose, pero no inicies una reescritura multiplataforma ni agregues configuraciones ajenas a la visión de escuchar música sin distracciones. Documenta decisiones, alternativas descartadas, pruebas y cualquier capacidad de servidor que no pueda darse por garantizada.

## 13. Referencias revisadas

- Repositorio y README de Navic.
- Configuración principal de Compose Multiplatform.
- Catálogo de versiones y dependencias.
- Estructura observable de repositorios, entidades, DAO, ViewModels y MediaPlayer.
- Búsqueda de implementaciones relacionadas con radio, aleatorio, artistas similares y Android Auto.

Esta referencia debe actualizarse si Navic sale de alpha, cambia su arquitectura o si Taki completa su migración de datos, reproducción o Compose.
