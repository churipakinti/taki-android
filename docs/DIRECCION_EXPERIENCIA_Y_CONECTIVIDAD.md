# Dirección de producto: escucha intencional, continuidad y uso eficiente del servidor

## Estado del documento

Este documento reúne ideas y decisiones de dirección discutidas para este fork privado de Ultrasonic. Su objetivo es orientar futuras investigaciones e implementaciones.

**No describe necesariamente el comportamiento actual de la app. Tampoco confirma que la API Subsonic, Ultrasonic, Navidrome u otros servidores compatibles permitan implementar todo lo propuesto.** Cada apartado debe contrastarse con:

1. El código actual del fork.
2. El comportamiento real de Ultrasonic.
3. Las capacidades y limitaciones de la API Subsonic.
4. Las diferencias entre servidores compatibles.
5. Las restricciones de Android y Android Auto.
6. Pruebas reales en dispositivo, con y sin conexión.

Este documento no sustituye `HANDOFF.md` ni `CHANGES.md`. `HANDOFF.md` describe la visión y el estado conocido del proyecto; `CHANGES.md` registra los cambios realmente implementados. Las ideas de este documento solo deben trasladarse a esos archivos cuando hayan sido investigadas o implementadas.

## Lenguaje de estado

Para evitar que una propuesta se interprete como una capacidad existente, cada requisito investigado deberá clasificarse con una de estas etiquetas:

| Etiqueta | Significado |
| --- | --- |
| **Dirección** | Objetivo estable de producto; guía decisiones aunque todavía no tenga implementación. |
| **Hipótesis** | Comportamiento deseable que parece viable, pero aún no se ha verificado. |
| **Hallazgo** | Comportamiento o limitación confirmado mediante código, documentación o prueba. |
| **Decisión validada** | Comportamiento elegido después de investigar viabilidad y efectos secundarios. |
| **Implementado** | Construido y verificado; debe registrarse también en `CHANGES.md`. |
| **No viable / diferido** | No puede hacerse con la arquitectura actual o no justifica su complejidad por ahora. |

En este documento, salvo que se indique lo contrario, los comportamientos descritos son **direcciones o hipótesis**, no hallazgos.

---

## 1. Propuesta de valor

### Propuesta principal

La app debe permitir disfrutar una biblioteca musical privada con un enfoque centrado en la música: álbumes, artistas, canciones, playlists, historial y continuidad de escucha.

> **Tu música como colección, no como contenido.**

Versión funcional provisional:

> Un reproductor para disfrutar tu biblioteca musical privada con atención, sin feeds sociales, recomendaciones invasivas ni configuraciones técnicas innecesarias.

La app accede a música alojada en un servidor, pero la infraestructura no debe dominar la experiencia. El usuario debería sentir que abre su colección musical, no que administra un cliente de red.

### Propuesta secundaria

La experiencia debe ser suficientemente sencilla para personas que no instalaron ni administran el servidor: familiares, amigos o invitados que solo quieren escuchar música.

Esta segunda propuesta apoya a la primera. La app no se convierte en una herramienta administrativa; reduce la fricción técnica para proteger la experiencia de escucha.

### Lo que la app no pretende ser

- Una red social musical.
- Un feed infinito diseñado para maximizar consumo.
- Un panel de administración del servidor.
- Una herramienta que obligue al oyente a entender formatos, bitrates, cachés, protocolos, VPN o transcodificación.
- Una copia completa de Spotify basada en recomendaciones externas.
- Una promesa de funcionamiento independiente del servidor cuando el contenido no se haya descargado o almacenado localmente.

---

## 2. Doble objetivo del sistema

Las decisiones deben equilibrar dos objetivos inseparables.

### 2.1 Facilitar la escucha para personas no técnicas

- Mostrar lenguaje cotidiano y acciones claras.
- Tomar automáticamente decisiones técnicas razonables.
- Mantener continuidad ante cambios de red.
- Recuperarse sin pedir intervención cuando sea posible.
- Separar el mensaje humano del diagnóstico técnico.
- Pedir configuración solo cuando represente una preferencia real del usuario.

### 2.2 Optimizar el uso del servidor, la red y el dispositivo

- No solicitar repetidamente datos ya disponibles y vigentes en el dispositivo.
- Preferir archivos descargados válidos cuando existan.
- Reutilizar metadatos y portadas almacenados localmente.
- Evitar sondeos constantes del servidor.
- Cancelar solicitudes obsoletas.
- Reintentar con límites y espera progresiva.
- Sincronizar únicamente lo necesario y cuando las condiciones sean apropiadas.
- Evitar que varias pantallas soliciten independientemente la misma información.

Estos objetivos no compiten necesariamente. Una arquitectura local-first puede hacer que la app se sienta más rápida y estable mientras disminuye el número de llamadas al servidor.

---

## 3. Principios de producto

Cada nueva función o cambio debería evaluarse con estas preguntas:

1. ¿Ayuda a escuchar música que el usuario eligió conservar?
2. ¿Acerca al usuario a la música o añade distracción?
3. ¿Puede entenderse sin conocer la infraestructura?
4. ¿Puede la app tomar una decisión segura sin añadir otra preferencia?
5. ¿Reutiliza datos disponibles o genera trabajo innecesario para el servidor?
6. ¿Sigue funcionando de manera razonable con una conexión lenta o intermitente?
7. ¿Expone información técnica únicamente cuando es útil para diagnóstico?

Principios abreviados:

> La complejidad pertenece al sistema; la calma pertenece al usuario.

> La conectividad amplía la colección, pero su pérdida no debería desarmar la experiencia.

> La app utiliza la mejor fuente disponible sin pedir al usuario que elija entre servidor y dispositivo.

---

## 4. Modelo conceptual de disponibilidad

### Dirección

La app debería combinar contenido local y remoto sin exigir que el usuario cambie manualmente a un servidor o perfil llamado “Offline”. Cuando la conexión regrese, la biblioteca completa debería recuperarse automáticamente.

### Advertencia técnica

Ultrasonic tiene un modo offline real basado en `ActiveServerProvider.isOffline()` y un servidor especial que cambia globalmente la implementación de `MusicService`. Mostrar datos locales sin cambiar ese servidor puede requerir una arquitectura diferente o una combinación cuidadosa de fuentes. No debe asumirse que el comportamiento deseado ya existe.

### Estados internos que deben investigarse

| Estado conceptual | Comportamiento deseado | Pregunta técnica |
| --- | --- | --- |
| Descarga completa y válida | Reproducible sin conexión | ¿Cómo verifica Ultrasonic integridad y finalización? |
| Descarga parcial | No marcar como disponible; reanudar si es posible | ¿El sistema actual soporta reanudación parcial? |
| Descarga pendiente | Esperar una red permitida | ¿Qué restricciones y cola usa `DownloadService`? |
| Metadatos locales | Mostrar inmediatamente | ¿Qué tablas y campos conserva `offlineMetaDatabase`? |
| Portada en caché | Reutilizar sin nueva petición | ¿Qué capas de caché existen y cuándo expiran? |
| Contenido solo remoto | Requiere conexión | ¿Puede identificarse sin intentar reproducirlo? |
| Elemento reproducido antes | No asumir que está disponible | ¿La reproducción crea caché reutilizable o solo streaming temporal? |

### Orden deseado para elegir la fuente de audio

1. Archivo descargado completo y válido.
2. Streaming desde el servidor.
3. Reintento limitado si el fallo parece transitorio.
4. Mensaje claro y posibilidad de saltar si la reproducción no es posible.

Esto es una hipótesis. Debe verificarse si el reproductor actual ya prioriza descargas, cómo resuelve rutas locales y remotas, y qué ocurre con transcodificación, ReplayGain y diferentes formatos.

---

## 5. Experiencia local-first

### Comportamiento deseado

Las pantallas deberían mostrar primero información local disponible y actualizarla desde el servidor cuando sea posible:

1. Abrir la pantalla.
2. Mostrar datos locales sin bloquear la interfaz.
3. Determinar si conviene consultar el servidor.
4. Solicitar únicamente datos faltantes o desactualizados.
5. Guardar la respuesta localmente.
6. Actualizar la interfaz sin reemplazarla por una pantalla de carga completa.
7. Mantener los datos previos si la actualización falla.

### Beneficios esperados

- Menor tiempo hasta mostrar contenido.
- Menos pantallas vacías ante fallos parciales.
- Menos llamadas repetidas a la API.
- Menor transferencia de portadas y metadatos.
- Mejor transición entre Wi-Fi, datos móviles y falta de conexión.
- Menor dependencia de la latencia o capacidad del servidor doméstico.

### Investigación requerida

- Identificar qué repositorios leen actualmente del servidor y cuáles de bases locales.
- Determinar si la base local puede actuar como fuente de verdad para navegación, o solo para descargas.
- Mapear la política actual de caché de listas, portadas y metadatos.
- Comprobar si la API ofrece marcas de modificación, paginación o consultas incrementales.
- Medir las llamadas reales al abrir Home, álbumes, artistas, búsqueda y reproductor.
- Detectar peticiones duplicadas o simultáneas.
- Confirmar diferencias entre Navidrome y otros servidores compatibles.

No debe proponerse una migración completa a una arquitectura offline-first sin medir primero su alcance y riesgo.

---

## 6. Home y navegación sin conexión

### Hipótesis recomendada

Home conserva su estructura e identidad, pero adapta sus secciones al contenido disponible. No debería convertirse abruptamente en otra pantalla ni abrir un diálogo modal por el solo hecho de perder conectividad.

Comportamiento deseado:

- Banner discreto: **“Estás sin conexión. Mostrando la música disponible en este dispositivo.”**
- La cola y el mini reproductor permanecen visibles.
- `Recently Played` muestra elementos reproducibles localmente cuando esa información exista.
- Se destaca el acceso a Descargas.
- Las secciones sin contenido útil se ocultan o presentan un estado simple, sin errores repetidos.
- Cuando vuelve la conexión, las secciones remotas reaparecen automáticamente.
- No se cambia manualmente el servidor activo.

### Aspectos por validar

- Si Home dispone de datos locales suficientes para reconstruir sus secciones.
- Si el Mix diario puede determinar qué canciones están descargadas.
- Si `Recently Played` depende exclusivamente del servidor.
- Si ocultar elementos remotos altera posiciones, accesibilidad o estado de scroll.
- Qué comportamiento resulta menos confuso en pruebas reales.

---

## 7. Configuración inicial y lenguaje sencillo

### Dirección

Una persona no técnica debería necesitar únicamente los datos de acceso proporcionados por quien administra su biblioteca.

Pantalla conceptual:

- Dirección de la biblioteca.
- Usuario.
- Contraseña.
- Botón `Conectar`.

Texto provisional:

> **Conecta tu biblioteca**  
> Introduce los datos que recibiste de la persona que administra tu música.

### Validación deseada

Al conectar, la app debería intentar distinguir:

1. Dirección inválida.
2. Falta de conexión del teléfono.
3. Biblioteca inaccesible.
4. Credenciales rechazadas.
5. Certificado o conexión insegura.
6. API o servidor incompatible.

### Advertencias

- Debe comprobarse qué errores diferencia realmente la capa de red.
- No debe afirmarse una causa que no pueda conocerse con certeza.
- `localhost`, DNS, timeout, HTTP, Subsonic y transcodificación no pertenecen al mensaje principal.
- “Servidor” puede aparecer en diagnóstico o administración; “biblioteca” es preferible en la experiencia del oyente.
- QR o enlaces de invitación son ideas futuras y requieren analizar seguridad, credenciales y compatibilidad. No forman parte del alcance inicial.

---

## 8. Mensajes de estado y recuperación

### Sistema conceptual de mensajes

| Situación percibida | Mensaje principal provisional | Acción primaria | Acción secundaria |
| --- | --- | --- | --- |
| Sin internet | Estás sin conexión | Ver descargas | Reintentar |
| Internet disponible, biblioteca inaccesible | Tu biblioteca no está disponible | Escuchar descargas | Reintentar / Ver diagnóstico |
| Credenciales rechazadas | No pudimos acceder a tu biblioteca | Revisar acceso | Ver diagnóstico |
| Canción no reproducible | No se puede reproducir esta canción | Saltar | Reintentar / Ver diagnóstico |
| Descarga interrumpida | La descarga continuará cuando sea posible | Ver descargas | Reintentar |
| Poco espacio | No hay suficiente espacio para descargar | Administrar descargas | Cancelar |
| Error interno | Algo salió mal en la app | Reintentar | Ver diagnóstico |

### Reglas

- El mensaje explica qué se sabe, no una causa supuesta.
- Siempre que sea posible, la app ofrece una acción que permite seguir escuchando.
- El diagnóstico se abre solo si el usuario lo solicita.
- Un fallo de una sección no debería inutilizar toda la pantalla.
- Los errores persistentes deben ser distinguibles de interrupciones transitorias.

Los textos son provisionales y deberán probarse en contexto, traducirse consistentemente y ajustarse a los estados técnicos que realmente puedan detectarse.

---

## 9. Diagnóstico para el administrador

### Objetivo

Permitir que una persona no técnica envíe información útil a quien administra la biblioteca sin copiar logs crudos ni exponer credenciales.

### Flujo deseado

1. El usuario ve un mensaje normal.
2. Elige `Ver diagnóstico`.
3. Revisa un resumen separado.
4. Pulsa `Copiar diagnóstico` si necesita ayuda.

### Información candidata

- Código estable del problema, por ejemplo `LIBRARY_UNREACHABLE`.
- Fecha y hora.
- Versión de la app y Android.
- Tipo general de conexión.
- Etapa del proceso que falló: resolución, conexión, autenticación, API, descarga o reproducción.
- Código HTTP, si existe y es útil.
- Dominio parcialmente oculto.
- ID interno del contenido relacionado, si es necesario.
- Extracto técnico breve y sanitizado.

### Información prohibida

- Contraseñas.
- Tokens.
- Cabeceras de autorización.
- URL completas con parámetros o credenciales.
- Logs completos del dispositivo.
- Información de otras apps.
- Historial de escucha no relacionado.
- Datos de otros usuarios del servidor.

### Investigación requerida

- Auditar qué registra actualmente Ultrasonic.
- Identificar si las URLs contienen tokens o credenciales.
- Diseñar una capa de redacción antes de exponer o copiar datos.
- Establecer códigos de error estables.
- Determinar cuánto historial mínimo es útil.

---

## 10. Descargas, reintentos y datos móviles

### Dirección

Las descargas deberían recuperarse de fallos transitorios con mínima intervención, respetando la preferencia existente sobre uso de datos móviles.

### Hipótesis de comportamiento

- Si se pierde la red, la descarga queda pendiente.
- Al volver una red permitida, continúa o se reinicia según lo que soporte la implementación.
- Los reintentos usan espera creciente y no son indefinidos ni agresivos.
- Después de fallos persistentes se muestra `Reintentar`.
- Los archivos parciales irrecuperables se limpian.
- Una canción solo muestra el check cuando la descarga es completa y válida.
- La reproducción local tiene prioridad sobre una nueva transferencia.

### Datos móviles

- La reproducción iniciada explícitamente puede usar datos móviles.
- Las descargas automáticas o sus reintentos respetan la preferencia existente de Wi-Fi/datos.
- No debe añadirse otra preferencia si la actual cubre correctamente el caso.

### Investigación requerida

- Revisar `DownloadService` y su cola actual.
- Comprobar si soporta rangos HTTP y reanudación.
- Determinar cómo persiste trabajos tras cerrar la app o reiniciar Android.
- Revisar número y política actual de reintentos.
- Confirmar qué significa “descargado” en base de datos y filesystem.
- Verificar cómo se comportan servidores con y sin transcodificación.

No deben imponerse números concretos de reintentos antes de esta auditoría.

---

## 11. Almacenamiento

### Dirección

El usuario debe poder entender qué ocupa espacio sin conocer carpetas, bases de datos o cachés internas.

### Distinción requerida

| Tipo | Política deseada |
| --- | --- |
| Caché de imágenes y metadatos | Puede limpiarse automáticamente. |
| Archivos temporales incompletos | Pueden limpiarse si no son recuperables. |
| Música descargada explícitamente | No se elimina automáticamente sin una decisión validada y comunicación clara. |
| Cola y estado de reproducción | Se conservan con tamaño mínimo. |
| Diagnósticos antiguos | Se eliminan automáticamente. |

### Experiencia deseada

- Mostrar espacio usado por descargas.
- Advertir si una nueva descarga no cabe.
- Pausar de forma segura si el espacio se agota.
- Separar `Limpiar caché` de `Eliminar descargas`.
- Confirmar la eliminación de música descargada.
- Permitir liberar espacio por álbum o playlist.

### Investigación requerida

- Identificar ambas capas de imágenes descritas en `HANDOFF.md`.
- Comprobar dónde se guardan música, temporales, bases de datos y portadas.
- Verificar si Android puede informar de espacio disponible de manera fiable en todas las ubicaciones soportadas.
- Revisar el comportamiento actual de `Clear All Downloads`.

---

## 12. Sincronización y conflictos

### Dirección

El servidor es la fuente principal de la biblioteca; el dispositivo conserva descargas, cola y acciones pendientes cuando sea posible.

### Hipótesis iniciales

- Metadatos: prevalece el servidor cuando puede consultarse.
- Favoritos: una acción offline se refleja inmediatamente y se sincroniza después, si la API y el modelo actual lo permiten.
- Playlists: los cambios offline requieren investigación; no asumir soporte.
- Cola: pertenece al dispositivo y debería sobrevivir al cierre.
- Descargas: no se eliminan silenciosamente por un conflicto.

### Contenido eliminado del servidor

Hipótesis conservadora:

- Si existe una descarga válida, no borrarla automáticamente.
- Marcarla como no disponible en la biblioteca remota.
- Permitir al usuario eliminar elementos que ya no existen en el servidor.

Esto debe validarse. Puede ser incompatible con los IDs, rutas, permisos o modelo de base de datos actual.

### Investigación requerida

- Si la API ofrece timestamps o versiones para detectar conflictos.
- Qué acciones pueden ejecutarse offline y reproducirse posteriormente.
- Si IDs de canciones permanecen estables tras reescaneo del servidor.
- Cómo responde Navidrome a favoritos y playlists desactualizados.
- Qué hacer si una operación pendiente deja de ser válida.

---

## 13. Persistencia de la sesión de reproducción

### Comportamiento deseado

Al cerrar y volver a abrir la app:

- Restaurar la cola y su orden.
- Restaurar la canción seleccionada.
- Restaurar la posición.
- Restaurar shuffle y repeat.
- Abrir en pausa; no reproducir automáticamente.
- Saltar de forma segura elementos que ya no existan.
- Conservar la cola aunque cambie la conectividad.

### Investigación requerida

- Qué persiste actualmente `MediaPlayerManager` o la capa Media3.
- Diferencia entre proceso destruido, app cerrada y reinicio del dispositivo.
- Si la cola contiene información suficiente para resolver archivos locales y remotos.
- Qué ocurre con una cola restaurada antes de autenticar o conectar.
- Interacción con Android Auto, notificaciones y controles Bluetooth.

---

## 14. Android Auto

### Estado conocido

La documentación del proyecto Ultrasonic indica soporte para Android Auto, con requisitos adicionales para APK instalados fuera de Google Play. Esto no confirma que la compilación privada actual aparezca, navegue correctamente ni conserve todas las funciones.

### Dirección

Android Auto debería reutilizar la misma lógica de disponibilidad y continuidad, con una navegación reducida y segura.

Categorías candidatas:

- Continuar escuchando.
- Descargas.
- Playlists.
- Álbumes.
- Artistas.
- Favoritos.

### Reglas deseadas

- Preferir música descargada cuando exista.
- Restaurar la cola anterior.
- Evitar configuración y mensajes técnicos mientras se conduce.
- Mostrar errores breves; el diagnóstico se consulta después en el teléfono.
- Mantener controles de reproducción y metadatos correctos.

### Auditoría requerida

- Confirmar que el APK privado aparece en Android Auto.
- Identificar el `MediaBrowserService`/`MediaSession` existente.
- Inspeccionar el árbol de contenido expuesto al automóvil.
- Probar play, pause, anterior, siguiente, cola y voz.
- Probar con servidor accesible, inaccesible y contenido descargado.
- Probar cambios Wi-Fi ↔ datos móviles.
- Verificar Android Auto real o un emulador adecuado; no inferir funcionamiento solo porque compile.

Android Auto debe tratarse primero como auditoría de una capacidad existente, no como implementación desde cero.

---

## 15. Estrategia de red y servidor

### Comportamiento deseado

- No usar polling constante para comprobar disponibilidad.
- Mostrar datos locales mientras se actualiza.
- Sincronizar al abrir si los datos necesitan actualización.
- Actualizar una pantalla al entrar en ella, en lugar de refrescar toda la biblioteca sin necesidad.
- Permitir actualización manual.
- Cancelar solicitudes que ya no corresponden a la pantalla o consulta actual.
- Agrupar peticiones cuando la API lo permita.
- Reutilizar respuestas y portadas.
- Aplicar backoff a fallos transitorios.
- Reactivar trabajos cuando cambien las condiciones de red, sin asumir que conectividad equivale a servidor accesible.

### Métricas recomendadas para la auditoría

- Solicitudes al abrir la app.
- Solicitudes por sección de Home.
- Tiempo hasta mostrar datos locales.
- Tiempo hasta completar actualización.
- Datos transferidos por portadas y metadatos.
- Peticiones repetidas a los mismos endpoints.
- Peticiones canceladas o respuestas obsoletas.
- Fallos por endpoint y servidor.
- Consumo de batería durante sesiones normales.

No se deben introducir telemetría externa ni recopilación de datos del usuario para obtener estas métricas. Pueden medirse localmente durante desarrollo mediante logs controlados, profiler o interceptores de red seguros.

---

## 16. Responsabilidad ante errores

| Situación | Responsable principal | Respuesta deseada |
| --- | --- | --- |
| Teléfono sin internet | Oyente | Acción simple para revisar conexión; mantener descargas. |
| Wi-Fi o datos desactivados | Oyente | Orientación del sistema sin jerga. |
| Almacenamiento lleno | Oyente | Administrar descargas. |
| Biblioteca inaccesible | Administrador | Continuidad local y diagnóstico copiable. |
| Credenciales inválidas | Compartida | Revisar datos de acceso; diagnóstico si persiste. |
| Certificado o dirección incorrecta | Administrador | Mensaje seguro y diagnóstico. |
| Archivo corrupto/incompatible | Administrador | Saltar, continuar y reportar datos técnicos. |
| Descarga temporalmente interrumpida | App | Recuperarse automáticamente. |
| Error interno de la app | Desarrollo | Mensaje claro y diagnóstico sanitizado. |

Regla general:

> La app primero intenta recuperarse sola. Solo involucra al oyente cuando existe una acción sencilla en su dispositivo; involucra al administrador cuando el problema pertenece a la biblioteca o su infraestructura.

---

## 17. Alcance inicial recomendado

Antes de intentar una arquitectura completa, investigar y priorizar:

1. Detección y clasificación real de conectividad y errores.
2. Prioridad actual entre archivo descargado y streaming.
3. Capacidad de mostrar metadatos y navegación local sin cambiar al servidor offline.
4. Recuperación automática cuando vuelve el servidor.
5. Persistencia de cola, canción y posición.
6. Mensajes de error en lenguaje sencillo.
7. Diagnóstico seguro y copiable.
8. Política actual de descargas, reintentos y datos móviles.
9. Auditoría funcional de Android Auto.
10. Medición de solicitudes repetidas y oportunidades de caché.

### Fuera del alcance inicial

- Contacto automático con el administrador.
- Invitaciones por QR o enlace.
- Sincronización offline compleja de playlists.
- Resolución avanzada de conflictos.
- Diagnóstico remoto o telemetría externa.
- Reescritura completa de la capa de datos.
- Rediseño completo de Android Auto.
- Prometer funcionamiento offline de contenido no descargado.

---

## 18. Proceso obligatorio antes de implementar cada bloque

Para cada tema:

1. **Definir la pregunta concreta.** Ejemplo: “¿El reproductor ya prefiere una descarga válida?”
2. **Buscar el flujo actual en el código.** Identificar clases, base de datos, servicios y preferencias involucrados.
3. **Revisar la API.** Confirmar endpoints, errores y diferencias entre servidores.
4. **Observar el comportamiento real.** Probar al menos conexión normal, conexión perdida y recuperación.
5. **Registrar el hallazgo.** Actualizar este documento o un informe de auditoría con evidencia.
6. **Comparar opciones.** Incluir complejidad, riesgo, efecto sobre el servidor y beneficio para el usuario.
7. **Tomar una decisión validada.** No convertir automáticamente la hipótesis inicial en requisito.
8. **Implementar incrementalmente.** Evitar barridos amplios que mezclen reproducción, red, base local y UI.
9. **Verificar.** Compilación, prueba en dispositivo, comportamiento del servidor y regresiones offline.
10. **Actualizar `CHANGES.md`.** Solo cuando exista un cambio real.

### Plantilla de ficha de investigación

```md
## Tema

**Estado:** Hipótesis | Hallazgo | Decisión validada | Implementado | Diferido

### Objetivo para el usuario

### Beneficio esperado para servidor/red/dispositivo

### Comportamiento actual confirmado

### Evidencia
- Código:
- API/documentación:
- Prueba en dispositivo:
- Prueba de servidor:

### Limitaciones y diferencias entre servidores

### Opciones consideradas

### Decisión

### Criterios de aceptación

### Archivos modificados
```

---

## 19. Criterio de éxito

La dirección será exitosa si una persona que no conoce Subsonic, Navidrome, Tailscale, codecs o cachés puede:

- Conectarse con los datos que recibió.
- Encontrar y escuchar música sin configurar detalles técnicos.
- Continuar con música descargada cuando la conexión falla.
- Recuperar automáticamente la biblioteca cuando vuelve la conexión.
- Retomar su cola después de cerrar la app.
- Entender qué ocurrió cuando algo falla.
- Enviar un diagnóstico seguro al administrador cuando no puede resolverlo.

Al mismo tiempo, el sistema debería:

- Evitar transferencias y peticiones redundantes.
- Preferir recursos locales válidos.
- Reutilizar metadatos y portadas.
- Recuperarse con reintentos limitados.
- No mantener comprobaciones agresivas en segundo plano.
- No exigir más trabajo al servidor del necesario para ofrecer una experiencia actualizada.

La meta no es ocultar que existe una biblioteca remota. Es hacer que esa infraestructura sirva a la escucha sin convertirse en el centro de la experiencia.
