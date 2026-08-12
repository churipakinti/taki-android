# Taki — Radio de canción, radio de artista y Mix diario

**Estado:** propuesta de producto y especificación inicial.  
**Objetivo:** ayudar al usuario a seguir escuchando su propia música sin tener que decidir constantemente qué reproducir.  
**Principio:** Taki no intenta replicar el sistema de recomendaciones de Spotify. Construye sesiones útiles y transparentes usando exclusivamente la biblioteca del usuario, sus metadatos, su historial y las capacidades disponibles del servidor.

## 1. Decisión de producto

Taki tendrá tres experiencias diferenciadas:

1. **Radio de canción:** continúa desde una canción concreta con música relacionada.
2. **Radio de artista:** construye una sesión alrededor de un artista.
3. **Mix diario:** selección personal, variada y estable durante el día.

Por ahora no se añadirá **Radio de género** como función independiente. Los géneros pueden conservar sus acciones normales de reproducción y aleatorio. Esto evita agregar otra categoría poco diferenciada y reduce la dependencia de etiquetas de género, que suelen ser incompletas o inconsistentes.

## 2. Intención de cada experiencia

| Función | Pregunta que resuelve |
| --- | --- |
| Radio de canción | “Quiero seguir escuchando algo parecido a esta canción.” |
| Radio de artista | “Quiero escuchar este artista y música relacionada.” |
| Mix diario | “No quiero elegir; dame una selección equilibrada de mi música.” |

Estas funciones no deben competir entre sí ni utilizar nombres intercambiables.

## 3. Radio de canción

### Acceso

Desde el menú contextual de cualquier canción:

```text
Reproducir ahora
Reproducir a continuación
Añadir a la cola
Iniciar radio
Ir al álbum
Ir al artista
```

`Iniciar radio` debe ser el nombre principal. Si el contexto requiere mayor precisión, puede usarse `Radio de canción` como título de pantalla o cola.

### Resultado esperado

- La canción elegida inicia la reproducción o permanece como semilla de la sesión.
- Se genera una cola inicial de aproximadamente 25–40 canciones.
- La cola utiliza únicamente música disponible en la biblioteca configurada.
- Se prefieren archivos descargados cuando la misma canción está disponible localmente.
- La cola puede ampliarse al acercarse al final, si la arquitectura lo permite sin complejidad excesiva.

### Estrategia de selección

Orden de fuentes sugerido:

1. Canciones similares devueltas por el servidor.
2. Canciones de artistas relacionados.
3. Otras canciones del artista de la semilla.
4. Canciones con géneros o etiquetas compatibles.
5. Selección aleatoria controlada como relleno.

La implementación debe confirmar primero qué endpoints Subsonic/OpenSubsonic y qué información expone realmente Navidrome. No asumir que “similar” estará disponible o tendrá calidad suficiente en todos los servidores.

### Reglas

- Evitar duplicados por ID.
- No incluir nuevamente la canción semilla.
- Máximo orientativo de 3–4 canciones del mismo artista en la cola inicial.
- Evitar más de dos canciones consecutivas del mismo artista.
- Evitar canciones reproducidas muy recientemente cuando exista información confiable.
- No depender exclusivamente del género.
- No devolver una radio de cinco canciones solo porque el servidor devolvió cinco similares.
- Si hay menos de 15 candidatas relacionadas, completar progresivamente con los fallbacks.

### Presentación

En la cola o encabezado:

```text
Radio basada en “The Sky People”
De tu biblioteca
```

No afirmar que son recomendaciones inteligentes o personalizadas al nivel de servicios comerciales.

## 4. Radio de artista

### Acceso

Desde Artist Detail y el menú contextual de un artista:

```text
Iniciar radio
```

### Resultado esperado

La radio debe equilibrar al artista elegido con música relacionada. No debe convertirse simplemente en “reproducir todas las canciones del artista”.

Distribución inicial sugerida:

- 40–50 % del artista semilla.
- 30–40 % de artistas relacionados, si el servidor los expone.
- 10–20 % de canciones compatibles utilizadas para completar variedad.

Los porcentajes son objetivos, no requisitos rígidos. Una biblioteca pequeña necesita fallbacks.

### Reglas

- Alternar razonablemente al artista semilla con artistas relacionados.
- No colocar bloques largos de un solo artista, salvo que no existan alternativas.
- Distribuir canciones entre distintos álbumes del artista.
- Evitar que un álbum muy grande domine toda la radio.
- Excluir duplicados y limitar repeticiones recientes.
- Si no existen artistas relacionados, crear una sesión centrada en el artista y comunicarla como tal; no inventar relaciones.

### Presentación

```text
Radio de Queens of the Stone Age
Canciones del artista y música relacionada
```

## 5. Mix diario

### Nombre

Usar simplemente:

```text
Mix diario
```

No llamarlo `Tu Mix diario`, `Daily Discovery` ni `Mix por género`. El nombre breve encaja mejor con Taki.

### Propósito

El Mix diario debe equilibrar familiaridad y redescubrimiento. No debe depender de un género escogido al azar, porque eso puede producir selecciones diminutas o repetitivas.

### Composición propuesta

Versión inicial:

- **50 % familiar:** canciones ya escuchadas, favoritas o reproducidas con cierta frecuencia.
- **30 % redescubrimiento:** canciones poco escuchadas o no reproducidas recientemente.
- **20 % exploración interna:** canciones nunca escuchadas, añadidas recientemente o alejadas de los hábitos recientes.

Esta mezcla es una hipótesis de producto. Debe ajustarse según los datos que realmente exponga Navidrome/Subsonic. Si no existe información confiable de conteo o última reproducción, utilizar señales disponibles sin fingir precisión.

### Tamaño

- Objetivo: 30 canciones.
- Mínimo aceptable: 15 canciones.
- Si una categoría no alcanza su cuota, completar desde las otras categorías y finalmente desde una selección general controlada.
- Nunca mostrar un Mix diario de solo cinco canciones salvo que la biblioteca completa disponible sea así de pequeña.

### Estabilidad diaria

- Generar el mix una vez por día y biblioteca.
- Guardar fecha, `serverId` e IDs seleccionados.
- Reutilizar la misma selección durante el día.
- Cambiar de pantalla o reiniciar la app no debe regenerarlo.
- Cambiar de servidor no debe reutilizar el mix de la biblioteca anterior.
- `Actualizar` puede regenerarlo únicamente si se decide ofrecer esa acción explícita.

### Diversidad

- Máximo orientativo de 2–3 canciones por artista.
- Distribuir canciones entre álbumes.
- Evitar duplicados y versiones idénticas cuando puedan detectarse.
- Reducir canciones reproducidas en las últimas 24–48 horas, excepto cuando pertenecen a la fracción familiar y son relevantes.
- No obligar a incluir todos los géneros.
- No usar el género como estructura principal del mix.

### Presentación en Home

```text
Mix diario
30 canciones para hoy
```

La tarjeta debe permitir reproducir directamente y abrir el detalle. No necesita explicar los porcentajes al usuario.

## 6. Géneros

No crear Radio de género en esta primera versión.

Mantener en la pantalla de género:

- `Reproducir`;
- `Aleatorio`;
- listado de canciones, álbumes o artistas disponible actualmente.

Si más adelante las pruebas muestran que los usuarios buscan sesiones por género y los metadatos son suficientemente buenos, puede reevaluarse. No debe bloquear Radio de canción, Radio de artista ni Mix diario.

## 7. Fallback común para radios

Toda radio debe utilizar una tubería de candidatos y no depender de una sola respuesta del servidor:

```text
Semilla
  → similares del servidor
  → artistas relacionados
  → mismo artista / otros álbumes
  → etiquetas compatibles
  → aleatorio controlado
  → deduplicación y límites
  → cola final
```

Si una fuente no está soportada:

- omitirla sin mostrar un error técnico;
- registrar diagnóstico seguro;
- continuar con la siguiente fuente;
- no hacer repetidamente solicitudes destinadas a fallar;
- utilizar el perfil de capacidades del servidor cuando corresponda.

## 8. Relación con la cola existente

Antes de implementar, el agente debe investigar:

- cómo se crea y persiste actualmente una cola;
- cómo se evita reconstruirla innecesariamente;
- cómo funciona `DuplicateRequestGuard`;
- cómo afecta shuffle y repeat;
- cómo se restauran colas después de cerrar la app;
- cómo se filtran pistas incompletas al cambiar a Offline;
- cómo Android Auto consume la biblioteca y la cola.

Las radios no deben revertir las optimizaciones recientes de reproducción. Generar 30 canciones no debe reconstruir miles de elementos ni bloquear el hilo principal.

## 9. Comportamiento offline

- El modo offline explícito se conserva sin cambios.
- Una radio iniciada offline solo puede usar canciones descargadas y metadatos locales.
- No consultar endpoints remotos para decidir que no hay conexión.
- Si no hay suficientes canciones locales, crear una cola más corta y comunicarlo en lenguaje simple.
- Una radio iniciada online puede preferir copias descargadas de las canciones seleccionadas.
- Cambiar entre online y offline no debe interrumpir una canción descargada que ya se está reproduciendo.

## 10. Métricas de evaluación

Medir en debug, sin telemetría remota:

- tiempo desde `Iniciar radio` hasta comenzar reproducción;
- número de candidatas devueltas por cada fuente;
- tamaño final de la cola;
- cantidad de artistas y álbumes únicos;
- porcentaje de la cola proveniente de fallback;
- solicitudes de red necesarias;
- duplicados eliminados;
- tiempo de generación del Mix diario;
- reutilización correcta del Mix diario durante el mismo día.

## 11. Criterios de aceptación

### Radio de canción

- Comienza desde una canción y genera una sesión coherente.
- Produce al menos 15 canciones cuando la biblioteca lo permite.
- No queda limitada por una respuesta pequeña del servidor.
- Respeta límites de artista y duplicados.
- Funciona con fallback en un servidor sin similitud disponible.

### Radio de artista

- Incluye al artista semilla sin dominar toda la cola.
- Incorpora relacionados cuando existen.
- Se degrada correctamente a una sesión centrada en el artista cuando no existen.
- No se concentra innecesariamente en un solo álbum.

### Mix diario

- Contiene aproximadamente 30 canciones.
- Mantiene una composición cercana a 50/30/20 cuando los datos lo permiten.
- Permanece estable durante el día y separado por servidor.
- No depende de un género aleatorio.
- Nunca queda con cinco canciones si existen suficientes candidatas para completarlo.

### General

- No hay ANR ni trabajo de red en el hilo principal.
- Cola, canción y posición continúan restaurándose.
- No se rompe el modo offline explícito.
- No se mezclan datos entre servidores.
- Los errores técnicos no se presentan directamente al usuario.

## 12. Orden de implementación sugerido

1. Auditar endpoints y datos reales disponibles.
2. Diseñar una clase pura para seleccionar, deduplicar y equilibrar candidatas.
3. Añadir pruebas unitarias sobre colecciones pequeñas, grandes y metadatos incompletos.
4. Implementar Radio de canción.
5. Validarla con varias semillas y servidores/fallbacks.
6. Reutilizar el selector para Radio de artista.
7. Implementar persistencia diaria por servidor.
8. Implementar Mix diario con la composición 50/30/20.
9. Integrar accesos en menús y Home.
10. Ejecutar regresión de cola, offline, reinicio y Android Auto.

No implementar las tres experiencias en un único commit.

## 13. Instrucción para el agente

> Lee este documento completo y confirma primero qué endpoints, metadatos e historial están realmente disponibles en el código actual y en las APIs objetivo. No asumas que Navidrome puede producir recomendaciones equivalentes a Spotify. Presenta un plan con las fuentes reales de candidatos, fallbacks y archivos afectados antes de modificar código. Implementa primero Radio de canción como un flujo vertical completo y medible. Conserva el modo offline explícito basado actualmente en `localhost`, las optimizaciones de cola y la separación entre servidores. No agregues Radio de género en esta fase. No hagas push ni release sin autorización explícita.

## 14. Decisiones pendientes de validar

- Si la radio reemplaza la cola actual o pregunta/permite añadirla.
- Si la canción semilla debe ser siempre la primera cuando ya está reproduciéndose otra canción.
- Si las radios se extienden automáticamente cerca del final.
- Qué señal utilizar para “familiar” cuando el servidor no exponga conteos confiables.
- Qué significa “nunca escuchada” en servidores sin historial suficiente.
- Si el Mix diario admite regeneración manual.
- Si se mostrará una explicación breve del Mix diario en su primera apertura.

Estas decisiones deben resolverse antes de cerrar la implementación, pero no impiden investigar las capacidades técnicas y construir el selector de candidatas.

## 15. Handoff de implementación — estado actual

Esta sección resume dónde quedó la implementación en `develop` para que el siguiente agente no
repita investigación ni reabra decisiones ya tomadas durante la sesión.

### Commits relevantes ya integrados

- `10e3c195` — Implementa radio de canción.
- `a0ee5feb` — Moderniza detalle de género.
- `6ced929c` — Implementa radio de artista.
- `d2275ab7` — Usa icono de radio para artista.
- `9cf40439` — Implementa Mix diario en Home.
- `b1f32ceb` — Completa detalle y regeneración de Mix diario.
- `3618bb3e` — Corrige regeneración de Mix diario.
- `29d2a0af` — Corrige restauración parcial de Mix diario.
- `63f79e05` — Documenta restauración parcial de Mix diario.
- `5c57fe9e` — Cierra Mix diario v1 y resuelve la deuda v1.1 (restauración exacta por
  `getSong.view`, señales de historial, comunicación de cola corta, explicación de primera
  apertura, estabilidad por servidor).

### Implementado

- Radio de canción desde menú contextual de canciones.
- Radio de artista desde Artist Detail y menú contextual de artistas.
- No se agregó Radio de género.
- El detalle de género se modernizó para no conservar el estilo viejo.
- Home ya no usa Mix por género aleatorio; ahora muestra `Mix diario` / `Daily Mix`.
- El Mix diario genera hasta 30 canciones por día y servidor.
- La selección usa una mezcla 50/30/20 aproximada:
  - familiaridad: favoritos y álbumes frecuentes;
  - redescubrimiento: pool aleatorio controlado;
  - exploración interna: álbumes nuevos y canciones recientes cuando existen metadatos.
- El Mix diario guarda fecha, `serverId` e IDs seleccionados.
- La tarjeta de Home permite:
  - abrir detalle del Mix;
  - reproducir directo;
  - regenerar manualmente con botón shuffle.
- El selector evita duplicados, filtra videos, limita dominio por artista/álbum y reduce bloques
  largos del mismo artista cuando hay alternativas.
- La restauración parcial ya no se acepta como Mix válido: si se guardaron 30 IDs pero al refrescar
  solo se pueden recuperar, por ejemplo, 19, Home regenera una lista nueva de 30 en vez de mostrar
  un contador parcial.

### Validación realizada

- Tests unitarios verdes:
  - `TrackRadioSelectorTest`;
  - `ArtistRadioSelectorTest`;
  - `DailyMixSelectorTest`;
  - `DailyMixQueueBuilderTest`.
- Build/debug validado durante la implementación con `:ultrasonic:assembleDebug`.
- Pixel 7 físico:
  - Radio de canción generó una cola de 30 y reprodujo sin `FATAL EXCEPTION`.
  - Radio de artista generó una cola de 30 y reprodujo sin `FATAL EXCEPTION`.
  - Mix diario en Home mostró `30 songs`.
  - Botón shuffle regeneró el Mix diario.
  - Pull-to-refresh reprodujo el caso de restauración parcial (`expectedSize=30`,
    `restoredSize=19`) y el fix regeneró correctamente a `30 songs`.

### Decisión temporal importante (resuelta en `5c57fe9e`)

La restauración ya no depende del pool de candidatos: `DailyMixQueueBuilder.restore()` resuelve
cada id guardado directamente contra `getSong.view` (concurrente, vía `restoreTracksByExactId()`).
Si un id puntual ya no existe en el servidor (canción borrada/movida), se descarta solo ese id; si
faltan demasiados para completar el Mix guardado, `shouldUseRestoredDailyMix()` sigue rechazando la
restauración parcial y Home regenera un Mix nuevo de 30, igual que antes.

### Cierre de Mix diario v1 — completado en `5c57fe9e`

1. **Reinicio de app:** verificado en Pixel 7 físico. Con un Mix diario de 30 canciones ya
   generado, `am force-stop` + reapertura mostró `Daily Mix / 30 songs for today` de inmediato;
   logcat confirmó ~30 llamadas concurrentes a `getSong.view` (restauración exacta) sin
   `FATAL EXCEPTION`.
2. **Cambio de servidor:** cubierto a nivel de código por `shouldAttemptDailyMixRestore()` y sus
   tests (mismo día + distinto `serverId` → no restaura). No se validó en dispositivo físico por
   no contar con un segundo servidor de prueba en esa sesión; si el siguiente agente tiene acceso
   a uno, vale la pena confirmarlo en vivo.
3. **Test de estabilidad por `serverId`:** añadido (`DailyMixQueueBuilderTest` cubre
   `shouldAttemptDailyMixRestore` para mismo servidor, servidor distinto, día distinto y
   `forceRefresh`).
4. **Subtítulo:** actualizado a `"%d songs for today"`, como proponía la sección 5 del documento.

### Deuda v1.1 — estado tras `5c57fe9e`

- ✅ Restauración exacta del Mix diario por ID (`getSong.view` de punta a punta).
- ✅ Preferir copias descargadas/locales: ya estaba garantizado por la arquitectura existente
  (`CachedDataSource`/`FileUtil.getCompleteFile()` resuelven cualquier pista contra el archivo
  local antes de hacer streaming, sin importar qué builder armó la cola) — no fue necesario
  código nuevo, solo se documentó.
- ✅ Comportamiento offline explícito: también ya garantizado — `MusicServiceFactory` resuelve
  `OfflineMusicService` automáticamente en modo offline y los tres builders piden esa instancia en
  cada llamada, así que no hay ruta de red posible offline. Sin código nuevo.
- ✅ Comunicar cola más corta: Mix diario y ambas radios exponen `TARGET_SIZE` y muestran un toast
  en lenguaje simple cuando el resultado queda corto (pero no vacío).
- ✅ Señales más finas de historial: `Track.playCount`/`Track.lastPlayed` (null si el servidor no
  los expone, nunca se interpreta como "cero"), migración `META_MIGRATION_3_4` de `MetaDatabase`.
  `DailyMixSelector` penaliza (no excluye) canciones reproducidas en las últimas 48h y amplía el
  criterio de "nunca escuchada" en el bucket de exploración con `playCount == 0`.
  "Nunca escuchada" se define como `playCount == null || playCount == 0`; no se intenta distinguir
  más allá de eso porque el propio DTO de Subsonic ya colapsa "servidor no expone el dato" y
  "cero reproducciones confirmadas" en el mismo valor por defecto.
- ✅ Explicación de primera apertura: implementada como toast largo, una sola vez
  (`Settings.homeMixIntroShown`), sin diálogo bloqueante.
- ⏳ Regresión más amplia de cola/reinicio/offline/Android Auto: solo se repitió el caso de
  reinicio con conexión real; falta offline explícito y Android Auto para dar por cerrado todo el
  documento con la profundidad de regresión que pide esta sección.

### Precauciones para el siguiente agente

- No reintroducir Mix por género aleatorio.
- No agregar Radio de género en esta fase.
- No cambiar el botón de radio de artista de vuelta a shuffle; se cambió a icono de radio por
  claridad de producto.
- No aceptar restauraciones parciales como Mix válido: ese fue el bug que hacía que Home mostrara
  menos de 20 canciones después de refrescar.
- Mantener los cambios separados en commits pequeños; el documento original pidió no implementar
  las tres experiencias en un único commit.
