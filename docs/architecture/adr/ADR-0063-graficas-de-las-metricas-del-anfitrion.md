# ADR-0063: Gráficas en «Mis métricas» del anfitrión: series por día y por mes, ocupación y vista de tabla

- **Estado:** Aceptado (2026-11-11). El desarrollador delegó en la evaluación y recomendación de este ADR
- **Proyecto / Módulo:** Intégrate · `backend-api/` (`modules/booking`: `HostMetricsService`, `HostMetricsController`) y `frontend-pwa/` (`features/host`)
- **Fecha:** 2026-11-11
- **Relacionado:** ADR-0061 (Entrega 2: métricas individuales del anfitrión), ADR-0059 (modal y escritorio), ADR-0020 (celular y computador), FE-DIR-001 (tokens, contraste y táctil)

## Contexto

«Mis métricas» (ADR-0061, Entrega 2) entrega cuatro números del mes y un ranking de hasta cinco experiencias. El anfitrión no puede ver **cómo se movió el mes** (qué días concentran reservas), **si va mejor o peor que los meses anteriores**, ni **cuánto de su capacidad futura está ocupada**. La pantalla se ve vacía y no responde las preguntas que un anfitrión se hace: «¿cuándo me reservan?», «¿estoy creciendo?», «¿me quedan cupos?».

Los datos para responderlas ya existen: `booking.bookings` (reservas confirmadas y cupos) y `catalog.schedules` (`start_time`, `total_slots`, `available_slots`). Las definiciones de ADR-0061 se mantienen: una reserva cuenta una vez, por la **fecha de inicio de la franja** en `America/Bogota` y solo si está `CONFIRMED`; las personas no son asistencia; cero significa consulta exitosa sin registros y un dato no disponible nunca se muestra como cero. El proyecto no usa librería de gráficas, y los colores deben salir de los tokens certificados (FE-DIR-001).

## Decisión

Ampliar `GET /api/v1/host/metrics` con tres series agregadas y dibujar cuatro gráficas simples en la PWA, todas con la misma regla de acceso (solo las experiencias del anfitrión autenticado) y sin caché persistente.

### 1. Backend: series nuevas, mismo endpoint

La respuesta conserva todos los campos actuales y agrega, sin romper a quien ya la consume:

| Campo | Contenido | Regla |
| :--- | :--- | :--- |
| `daily` | Reservas y personas por **día** del mes seleccionado: `{ date, reservations, people }` | Solo días con datos; el cliente completa con ceros los días vacíos del mes. Día = fecha de inicio de la franja en Bogotá. |
| `monthly` | Reservas y personas por **mes** de los últimos 12 (los mismos que ofrece el selector): `{ month, reservations, people }` | Incluye meses en cero para que la serie sea continua y se distinga «sin reservas» de «sin dato». |
| `occupancy` | De las **franjas futuras no canceladas** del anfitrión: `{ slots, booked }` (suma de `total_slots` y de `total_slots - available_slots`) | Referencia temporal del servidor. Si no hay franjas futuras, `slots = 0` y el cliente muestra «Sin jornadas próximas», no 0 %. |
| `ranking[].people` | Personas por experiencia del ranking | Se agrega al elemento existente. |

Las consultas son agregados SQL acotados (12 meses, un mes, cinco experiencias), con el titular tomado del JWT y sin aceptar el anfitrión como parámetro. No hay migración de base de datos.

### 2. Frontend: cuatro gráficas y una vista de tabla

Todas se dibujan con HTML y SVG propios (sin dependencia nueva), con tokens existentes: `brand` para la serie, `border-subtle` y `surface` para el fondo y la guía. Una sola serie por gráfica: no hay leyenda de colores ni paleta categórica que validar, y el significado nunca depende solo del color.

| Gráfica | Forma | Pregunta que responde |
| :--- | :--- | :--- |
| **Reservas por día** del mes | Columnas (una por día), con la etiqueta del valor solo en el máximo y el eje con los días 1, 8, 15, 22, 29 | ¿Qué días me reservan? |
| **Tendencia de 12 meses** | Columnas por mes con el mes seleccionado resaltado y los demás en tono atenuado (énfasis) | ¿Estoy creciendo o bajando? |
| **Experiencias más reservadas** | Barras horizontales ordenadas, valor en la punta y porcentaje del total | ¿Cuál experiencia me trae más reservas? |
| **Ocupación de próximas jornadas** | Medidor: cupos reservados frente a cupos ofrecidos, con la cifra en texto | ¿Cuánta capacidad futura ya está ocupada? |

Reglas comunes: barras de máximo 24 px con la punta de datos redondeada y base recta; separación de 2 px entre marcas; guías de 1 px apenas visibles; el texto va en tokens de texto, nunca con el color de la serie; cada marca se puede enfocar con teclado y muestra su valor en un recuadro al pasar el cursor o enfocar. Cada gráfica incluye el botón **«Ver como tabla»**, que sustituye el dibujo por una tabla con los mismos datos; el nombre accesible de la gráfica enuncia su resumen («12 reservas en el mes; el día con más reservas fue el 14»).

La pantalla usa el ancho de computador (dos columnas para las gráficas) y una sola columna en celular, sin barras fijas al pie. El estado de carga usa esqueletos; el vacío explica qué ocurrirá cuando haya reservas; el error ofrece reintentar.

### 3. Lo que no cambia ni se agrega

- **Asistencia** sigue como «Dato aún no disponible» hasta que se decida la fuente (ADR-0061).
- **Ingresos y pagos** siguen fuera de alcance.
- No se agrega comparación con otros anfitriones ni con la asociación.

### 4. Entrega por fases

1. **Fase 1 (sin backend):** tarjetas con ícono, ranking como barras horizontales con porcentaje, vista de tabla y diseño de computador, con los datos que la API ya entrega.
2. **Fase 2 (backend):** campos `daily`, `monthly`, `occupancy` y `ranking[].people`, con sus pruebas, contrato OpenAPI e índice de endpoints.
3. **Fase 3 (frontend):** gráficas de día y mes y medidor de ocupación.

## Alternativas Descartadas

- **Librería de gráficas (Recharts, Chart.js, ECharts).** Suma peso al paquete de una PWA para celulares con conexión intermitente y trae su propia paleta, que habría que forzar a los tokens certificados. Las cuatro gráficas son simples y se dibujan con HTML y SVG.
- **Series calculadas en el cliente a partir de la lista de reservas.** Obligaría a descargar todas las reservas, que la lista pagina (20 por página), y daría totales incompletos.
- **Endpoints separados por gráfica.** Cuatro peticiones por pantalla en una conexión intermitente, con riesgo de cifras de momentos distintos. Una respuesta única garantiza coherencia.
- **Gráfica de ingresos o de asistencia ahora.** No hay definición ni fuente acordadas (ADR-0061); mostrar una cifra inventada engañaría al anfitrión.
- **Dos series por gráfica (reservas y personas).** Exigiría leyenda, paleta categórica validada para daltonismo y un eje doble (prohibido). Las personas se muestran en tarjeta y en la tabla.

## Consecuencias

- **Positivas:** el anfitrión ve el movimiento del mes, su tendencia y su capacidad futura de un vistazo; las cifras son coherentes porque vienen de una sola consulta; sin dependencia nueva; accesible por teclado y con tabla equivalente; la API sigue compatible hacia atrás.
- **Negativas / Riesgos asumidos:**
  - Tres agregados SQL más por petición. Mitigación: están acotados por 12 meses y por anfitrión; se verifica el plan de ejecución con datos de prueba y se mantiene el criterio de no cachear.
  - El conteo por «fecha de la franja» no coincide con «fecha de compra»; la etiqueta debe decirlo y es fuente de preguntas de usuarios.
  - Dibujar gráficas a mano exige pruebas de geometría y de accesibilidad propias; se cubren con pruebas de componente y de tabla equivalente.
  - La ocupación suma cupos de franjas abiertas y cerradas no canceladas; una franja cerrada con cupos libres reduce el porcentaje. Se documenta en la etiqueta del medidor.

## Decisiones tomadas (2026-11-11)

El desarrollador pidió seguir la evaluación y recomendación del ADR. Se aplican así:

1. **Fases:** se ejecutan las tres en este orden (tarjetas y ranking, backend, gráficas).
2. **Ocupación:** cuenta todas las franjas futuras no canceladas (abiertas y cerradas) y, como cupos reservados, solo las reservas `CONFIRMED`. No usa `total_slots - available_slots`, porque ese valor incluye cupos retenidos por reservas aún sin confirmar y desharía la definición de ADR-0061.
3. **Comparación con el mes anterior:** no se incluye ahora. La serie de 12 meses ya permite ver la tendencia; un «+3 frente a octubre» exige decidir qué hacer con meses incompletos o en cero y queda para una enmienda.

## Estado de la implementación

- **Backend (hecho):** `HostMetricsService` devuelve `daily`, `monthly` (12 meses con ceros), `occupancy` y `ranking[].people`; la prueba de integración con Postgres real (`hostMetricsCountOwnConfirmedReservationsInBogotaMonth`) cubre el día, los 12 meses, la ocupación, el aislamiento entre anfitriones y el estado vacío. OpenAPI e índice de endpoints actualizados.
- **Frontend (hecho):** reservas por día, últimos 12 meses (mes elegido resaltado), ocupación en medidor y ranking en barras, cada una con vista de tabla y resumen para lectores de pantalla; ancho de computador. Quality Gate de la PWA en verde.
- **Pendiente:** ver las gráficas con datos reales en pantalla (requiere sesión de anfitrión con reservas), definir la fuente de asistencia (ADR-0061) y la comparación con el mes anterior.

## Contrato o Esquema Conceptual

Campos nuevos de `GET /api/v1/host/metrics` (el resto no cambia): `daily[] { date, reservations, people }`, `monthly[12] { month, reservations, people }`, `occupancy { slots, booked }` y `ranking[].people`.
