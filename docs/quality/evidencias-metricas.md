# 5. Evidencias métricas

> Generado por `qa/scripts/build-evidence-report.mjs` el 2026-10-11 a partir de ejecuciones reales de la suite de calidad (ADR-0019). No editar a mano: volver a ejecutar la suite y el script.

## 5.1 Resumen

| Indicador | Valor |
|---|---|
| RNF del plan | 30 |
| RNF que cumplen | 18 |
| RNF que no cumplen (no conformidad) | 4 |
| RNF que están en estado «Pendiente Fase 2» | 6 |
| RNF que están en estado «No automatizable» | 1 |
| RNF que están en estado «Brecha» | 1 |
| Pruebas backend ejecutadas (JUnit) | 180 (0 fallidas) |
| Pruebas PWA ejecutadas (Vitest) | 165 (0 fallidas) |
| Mediciones registradas | 49 |

Las fallas de JUnit que aparecen corresponden a las pruebas marcadas `nonconformity`: verifican el criterio literal del plan sobre comportamientos que hoy son distintos y solo corren con `-Pquality`.

## 5.2 Mediciones por característica ISO/IEC 25010

### Seguridad

| RNF | Métrica | Valor medido | Umbral del plan | Resultado | Fuente |
|---|---|---|---|---|---|
| RNF-01 | Atributos del anfitrión o de víctima en endpoints públicos | 0 atributos | = 0 | Cumple | Backend (JUnit + Testcontainers) |
| RNF-01 | Columnas sensibles bajo el dominio de cifrado | 100 % | = 100 % | Cumple | Backend (JUnit + Testcontainers) |
| RNF-01 | Decimales de latitud expuestos en el detalle público | 6 decimales | <= 2 (ofuscación por radio) | **No cumple** | Backend (JUnit + Testcontainers) |
| RNF-01 | Documento y teléfono de auth.users cifrados | 0 % | = 100 % | **No cumple** | Backend (JUnit + Testcontainers) |
| RNF-01 | Valores sensibles de reserva legibles en reposo | 0 valores | = 0 | Cumple | Backend (JUnit + Testcontainers) |
| RNF-04 | Contraseñas almacenadas con hash robusto y sal | 100 % | = 100 % | Cumple | Backend (JUnit + Testcontainers) |
| RNF-04 | Credenciales con Argon2id | 100 % | informativo (el plan nombra BCrypt) | Cumple | Backend (JUnit + Testcontainers) |
| RNF-04 | Portadores de secretos que ocultan el secreto al imprimirse | 100 % | = 100 % | Cumple | Backend (JUnit + Testcontainers) |
| RNF-05 | Tasa de detección de vouchers alterados | 100 % | = 100 % | Cumple | Backend (JUnit + Testcontainers) |
| RNF-06 | Canales de alta de cuentas que registran el consentimiento | 66,67 % | = 100 % (cero tolerancia legal) | **No cumple** | Backend (JUnit + Testcontainers) |
| RNF-06 | Intentos de alterar la bitácora bloqueados | 100 % | = 100 % | Cumple | Backend (JUnit + Testcontainers) |
| RNF-06 | Registros con consentimiento auditado completo | 100 % | = 100 % | Cumple | Backend (JUnit + Testcontainers) |
| RNF-07 | Endpoints de rol que rechazan al rol equivocado | 100 % | = 100 % (403) | Cumple | Backend (JUnit + Testcontainers) |
| RNF-07 | Endpoints protegidos que rechazan acceso sin token válido | 100 % | = 100 % (401) | Cumple | Backend (JUnit + Testcontainers) |

### Eficiencia de desempeño

| RNF | Métrica | Valor medido | Umbral del plan | Resultado | Fuente |
|---|---|---|---|---|---|
| RNF-02 | Respuestas inesperadas bajo concurrencia (deadlocks o 5xx) | 0 respuestas | = 0 | Cumple | Backend (JUnit + Testcontainers) |
| RNF-02 | Tiempo de bloqueo transaccional de aforo (p95) | 72,2 ms | p95 <= 200 ms | Cumple | Backend (JUnit + Testcontainers) |
| RNF-02 | Transacciones de bloqueo por minuto sostenidas | 14.247,58 tx/min | >= 100 tx/min | Cumple | Backend (JUnit + Testcontainers) |
| RNF-02 | Tiempo de bloqueo de aforo HTTP sostenido (p95, k6) | 26,3 ms | p95 <= 200 ms | Cumple | Carga (k6) |
| RNF-02 | Ráfaga de 100 bloqueos simultáneos (p95, k6) | 743,3 ms | informativo (pool de 10 conexiones) | Cumple | Carga (k6) |
| RNF-02 | Transacciones de bloqueo por minuto (k6) | 300 tx/min | >= 100 tx/min | Cumple | Carga (k6) |
| RNF-08 | Tiempo de respuesta en autenticación (p95) | 87,5 ms | p95 < 800 ms | Cumple | Backend (JUnit + Testcontainers) |
| RNF-08 | Tiempo de respuesta en autenticación HTTP (p95, k6) | 55,7 ms | p95 < 800 ms | Cumple | Carga (k6) |
| RNF-09 | Peso del bundle inicial comprimido | 143,4 kB | < 2560 kB (2,5 MB) | Cumple | PWA (build de producción) |
| RNF-10 | 500 usuarios navegando: tiempo de respuesta (p95, k6) | 148,6 ms | p95 < 1500 ms, < 1 % de errores | Cumple | Carga (k6) |
| RNF-10 | 100 checkouts simultáneos: bloqueo (p95, k6) | 305 ms | p95 < 1500 ms, sin 5xx | Cumple | Carga (k6) |
| RNF-10 | Uso máximo de CPU del servidor durante 500 navegando + 100 checkout | 26 % | < 75 % | Cumple | Carga (k6) + monitor de CPU |
| RNF-11 | Efectividad en liberación de cupos (TTL 15 min) | 100 % | = 100 % | Cumple | Backend (JUnit + Testcontainers) |
| RNF-11 | Lecturas del catálogo fallidas durante el barrido | 0 lecturas | = 0 (sin bloquear tablas de consulta) | Cumple | Backend (JUnit + Testcontainers) |
| RNF-11 | Tiempo de ejecución por barrido del worker TTL | 1.172,6 ms | < 1500 ms | Cumple | Backend (JUnit + Testcontainers) |

### Capacidad de interacción

| RNF | Métrica | Valor medido | Umbral del plan | Resultado | Fuente |
|---|---|---|---|---|---|
| RNF-03 | Violaciones WCAG 2.1 A/AA detectadas por axe-core | 0 violaciones | = 0 | Cumple | PWA (Vitest) |
| RNF-12 | Enrolamiento asistido < 3 min | — | — | No automatizable | Prueba de usuario en campo con el Gestor de Enlace |
| RNF-13 | Formularios incompletos rechazados por la validación del cliente | 100 % | = 100 % | Cumple | PWA (Vitest) |
| RNF-13 | Envíos incompletos detenidos antes de llamar a la API | 100 % | = 100 % | Cumple | PWA (Vitest) |
| RNF-13 | Hitos del TTL anunciados al usuario | 100 % | = 100 % | Cumple | PWA (Vitest) |
| RNF-14 | Indicadores visuales de validación offline | — | — | Pendiente Fase 2 | T-2.10 (lector QR) |
| RNF-15 | Legibilidad Flesch-Szigriszt del consentimiento | 56 puntos | > 60 | **No cumple** | PWA (Vitest) |

### Fiabilidad

| RNF | Métrica | Valor medido | Umbral del plan | Resultado | Fuente |
|---|---|---|---|---|---|
| RNF-16 | Franjas con deriva entre inventario y reservas | 0 franjas | = 0 | Cumple | Backend (JUnit + Testcontainers) |
| RNF-16 | Tasa de sobreventa de cupos (overbooking) | 0 % | = 0,0 % | Cumple | Backend (JUnit + Testcontainers) |
| RNF-17 | Operación offline (IndexedDB) | — | — | Pendiente Fase 2 | T-2.8, T-2.9 |
| RNF-18 | Prevención de doble canje offline | — | — | Pendiente Fase 2 | T-2.11 |
| RNF-19 | Background Sync determinista | — | — | Pendiente Fase 2 | T-2.3, T-2.13 |
| RNF-20 | Contingencia por falla del terminal | — | — | Pendiente Fase 2 | T-2.2, T-2.12 |

### Adecuación funcional

| RNF | Métrica | Valor medido | Umbral del plan | Resultado | Fuente |
|---|---|---|---|---|---|
| RNF-21 | Exactitud en dispersión financiera 85/15 (custodia) | 100 % | = 100 % (cero errores de liquidación) | Cumple | Backend (JUnit + Testcontainers) |
| RNF-22 | Política de cancelación 72 h | — | — | Pendiente Fase 2 | T-2.6 |
| RNF-23 | Campos de salud y emergencia presentes en el PDF | 0 % | = 100 % (EPS y contacto de emergencia) | **No cumple** | Backend (JUnit + Testcontainers) |
| RNF-23 | Compras aprobadas con voucher PDF/A conforme | 100 % | = 100 % | Cumple | Backend (JUnit + Testcontainers) |
| RNF-24 | Franjas en base con más cupos que la capacidad de carga | 0 franjas | = 0 | Cumple | Backend (JUnit + Testcontainers) |
| RNF-24 | Parametrizaciones que violan la capacidad de carga rechazadas | 100 % | = 100 % (422) | Cumple | Backend (JUnit + Testcontainers) |
| RNF-25 | Efectos duplicados ante reintentos de webhook | 0 efectos | = 0 | Cumple | Backend (JUnit + Testcontainers) |
| RNF-25 | Entregas de webhook respondidas 200 (sin reintento infinito) | 100 % | = 100 % | Cumple | Backend (JUnit + Testcontainers) |

### Mantenibilidad

| RNF | Métrica | Valor medido | Umbral del plan | Resultado | Fuente |
|---|---|---|---|---|---|
| RNF-26 | Disponibilidad de la API con un worker fallando | 100 % | = 100 % | Cumple | Backend (JUnit + Testcontainers) |
| RNF-26 | Reglas de desacoplamiento de workers cumplidas | 100 % | = 100 % | Cumple | Backend (JUnit + Testcontainers) |
| RNF-27 | Cobertura de líneas backend (servicios y criptografía) | 92,95 % | > 80 % | Cumple | Backend (JaCoCo) |
| RNF-27 | Cobertura de líneas PWA (features y shared) | 93,14 % | > 80 % | Cumple | PWA (Vitest + V8) |
| RNF-28 | Endpoints implementados documentados en OpenAPI | 100 % | = 100 % | Cumple | Backend (JUnit + Testcontainers) |
| RNF-29 | Logs JSON con correlation ID (MTTD < 30 min) | — | — | Brecha | Hay traceId (TraceIdFilter) pero no salida JSON estructurada |
| RNF-30 | Componentes base reutilizados en 3 o más módulos | 100 % | = 100 % (>= 3 vistas o módulos cada uno) | Cumple | PWA (Vitest) |

## 5.3 Detalle de cada medición

- **RNF-01 · Atributos del anfitrión o de víctima en endpoints públicos:** 0 atributos (cumple = 0). GET /experiences y /experiences/{id} sin autenticación
- **RNF-01 · Columnas sensibles bajo el dominio de cifrado:** 100 % (cumple = 100 %). 9 columnas *_enc (víctima, RUV, salud/EPS, contacto de emergencia, cuentas de pago)
- **RNF-01 · Decimales de latitud expuestos en el detalle público:** 6 decimales (no cumple <= 2 (ofuscación por radio)). experiencia creada con latitud 6.251234; respuesta: 6.251234
- **RNF-01 · Documento y teléfono de auth.users cifrados:** 0 % (no cumple = 100 %). auth.users.doc_number y auth.users.phone (dominios [doc_number, phone_e164])
- **RNF-01 · Valores sensibles de reserva legibles en reposo:** 0 valores (cumple = 0). 4 valores cifrados (EPS por participante y contacto de emergencia)
- **RNF-02 · Respuestas inesperadas bajo concurrencia (deadlocks o 5xx):** 0 respuestas (cumple = 0). solo se aceptan 201, 409 (sin cupos) y 503 (lock_timeout con Retry-After)
- **RNF-02 · Tiempo de bloqueo transaccional de aforo (p95):** 72,2 ms (cumple p95 <= 200 ms). 100 bloqueos, 10 concurrentes sobre 5 franjas; p50 = 36,6 ms; en proceso (indicativo, ver k6)
- **RNF-02 · Transacciones de bloqueo por minuto sostenidas:** 14.247,58 tx/min (cumple >= 100 tx/min). 100 de 100 creadas en 0,42 s
- **RNF-02 · Tiempo de bloqueo de aforo HTTP sostenido (p95, k6):** 26,3 ms (cumple p95 <= 200 ms). 300 bloqueos a 5 tx/s (300 tx/min, el triple del mínimo); media 22,2 ms
- **RNF-02 · Ráfaga de 100 bloqueos simultáneos (p95, k6):** 743,3 ms (cumple informativo (pool de 10 conexiones)). 100 usuarios virtuales a la vez; media 513,4 ms, máx. 783,1 ms; sin 5xx: 100 %
- **RNF-02 · Transacciones de bloqueo por minuto (k6):** 300 tx/min (cumple >= 100 tx/min). escenario sostenido de 60 s
- **RNF-03 · Violaciones WCAG 2.1 A/AA detectadas por axe-core:** 0 violaciones (cumple = 0). 7 vistas: Catálogo, Detalle de experiencia, Ingresar, Registro, Activar cuenta, Formulario de reserva, Enrolamiento asistido (Gestor)
- **RNF-04 · Contraseñas almacenadas con hash robusto y sal:** 100 % (cumple = 100 %). 263 credenciales; Argon2id = 263; texto plano = 0
- **RNF-04 · Credenciales con Argon2id:** 100 % (cumple informativo (el plan nombra BCrypt)). Argon2id m=19 MiB, t=2 (ADR-0005 §3)
- **RNF-04 · Portadores de secretos que ocultan el secreto al imprimirse:** 100 % (cumple = 100 %). IssuedToken, IssuedActivationCode y AdminBootstrapCommand
- **RNF-05 · Tasa de detección de vouchers alterados:** 100 % (cumple = 100 %). 31 alteraciones: 10 claims, cupos inflados, firma ajena, truncada, vacía, alg=none y 16 bits invertidos
- **RNF-06 · Canales de alta de cuentas que registran el consentimiento:** 66,67 % (no cumple = 100 % (cero tolerancia legal)). autoservicio y enrolamiento asistido lo registran al crear la cuenta; el bootstrap del ADMIN (ADR-0005 §9) no: lo pide en el primer ingreso (requiresConsentUpdate)
- **RNF-06 · Intentos de alterar la bitácora bloqueados:** 100 % (cumple = 100 %). UPDATE/DELETE/TRUNCATE con el rol de la aplicación (integrate_api)
- **RNF-06 · Registros con consentimiento auditado completo:** 100 % (cumple = 100 %). 10 registros por POST /auth/register (fecha, IP, agente, versión de política)
- **RNF-07 · Endpoints de rol que rechazan al rol equivocado:** 100 % (cumple = 100 % (403)). 30 endpoints de ADMIN/HOST/LINK_MANAGER/TOURIST
- **RNF-07 · Endpoints protegidos que rechazan acceso sin token válido:** 100 % (cumple = 100 % (401)). 32 endpoints protegidos de 48 rutas; token alterado = claims elevados a ROLE_ADMIN sin re-firmar
- **RNF-08 · Tiempo de respuesta en autenticación (p95):** 87,5 ms (cumple p95 < 800 ms). 64 logins, 4 concurrentes; media = 67,9 ms; Argon2id m=19 MiB t=2; en proceso (indicativo)
- **RNF-08 · Tiempo de respuesta en autenticación HTTP (p95, k6):** 55,7 ms (cumple p95 < 800 ms). 241 logins a 4/s durante 60 s; media 45,8 ms, máx. 206 ms
- **RNF-09 · Peso del bundle inicial comprimido:** 143,4 kB (cumple < 2560 kB (2,5 MB)). 6 recursos iniciales (gzip nivel 9); chunk más grande sin comprimir 253.1 kB
- **RNF-10 · 500 usuarios navegando: tiempo de respuesta (p95, k6):** 148,6 ms (cumple p95 < 1500 ms, < 1 % de errores). 42870 peticiones (569,1/s); errores HTTP 0,11 % (409 por cupos agotados)
- **RNF-10 · 100 checkouts simultáneos: bloqueo (p95, k6):** 305 ms (cumple p95 < 1500 ms, sin 5xx). en paralelo con la navegación; sin 5xx inesperados: 100 %
- **RNF-10 · Uso máximo de CPU del servidor durante 500 navegando + 100 checkout:** 26 % (cumple < 75 %). Proceso java del backend, muestreo cada 2 s durante catalog-checkout-mix.js; máximo 26 %, promedio 8,4 % de una máquina de 12 núcleos (k6 corre en la misma máquina, en Docker)
- **RNF-11 · Efectividad en liberación de cupos (TTL 15 min):** 100 % (cumple = 100 %). 400 de 400 cupos devueltos; 14 de 14 franjas con inventario completo
- **RNF-11 · Lecturas del catálogo fallidas durante el barrido:** 0 lecturas (cumple = 0 (sin bloquear tablas de consulta)). 80 lecturas de disponibilidad en paralelo; p95 = 5,9 ms
- **RNF-11 · Tiempo de ejecución por barrido del worker TTL:** 1.172,6 ms (cumple < 1500 ms). 515 reservas vencidas en lotes de 100 (FOR UPDATE SKIP LOCKED)
- **RNF-13 · Formularios incompletos rechazados por la validación del cliente:** 100 % (cumple = 100 %). 7 variantes: sin EPS (titular y acompañante), sin nombre o celular de emergencia, celular inválido, sin documento, nombre corto
- **RNF-13 · Envíos incompletos detenidos antes de llamar a la API:** 100 % (cumple = 100 %). formulario real renderizado, un campo omitido por envío: eps1: detenido; eps2: detenido; emergencyName: detenido; emergencyPhone: detenido; doc1: detenido
- **RNF-13 · Hitos del TTL anunciados al usuario:** 100 % (cumple = 100 %). 5 min, último minuto y vencido; texto visible y región aria-live
- **RNF-15 · Legibilidad Flesch-Szigriszt del consentimiento:** 56 puntos (no cumple > 60). 56 palabras, 123 sílabas, 4 frases; por finalidad: PERSONAL_DATA=58.4, HEALTH_EMERGENCY=62.0, SENSITIVE_VICTIM=41.5
- **RNF-16 · Franjas con deriva entre inventario y reservas:** 0 franjas (cumple = 0). available_slots + reservados = total_slots y v_schedule_slot_reconciliation
- **RNF-16 · Tasa de sobreventa de cupos (overbooking):** 0 % (cumple = 0,0 %). 150 solicitudes con 24 hilos contra 3 franjas (3+5+7 = 15 cupos); respuestas {409=140, 201=10}; cupos vendidos 15
- **RNF-21 · Exactitud en dispersión financiera 85/15 (custodia):** 100 % (cumple = 100 % (cero errores de liquidación)). 12 pagos confirmados con precios [80001, 33333, 12347] × 1-4 cupos; comunidad = CEIL(total × 85.00 %), plataforma = resto
- **RNF-23 · Campos de salud y emergencia presentes en el PDF:** 0 % (no cumple = 100 % (EPS y contacto de emergencia)). EPS presente = false, contacto de emergencia presente = false
- **RNF-23 · Compras aprobadas con voucher PDF/A conforme:** 100 % (cumple = 100 %). 3 compras; PDF/A-2b (XMP pdfaid), fuentes incrustadas, localizador, QR, cupos, titular y términos
- **RNF-24 · Franjas en base con más cupos que la capacidad de carga:** 0 franjas (cumple = 0). consulta sobre toda la base de prueba
- **RNF-24 · Parametrizaciones que violan la capacidad de carga rechazadas:** 100 % (cumple = 100 % (422)). 7 intentos (franjas de 9/12/30/31/0 cupos sobre capacidad 8, reducir capacidad bajo una franja abierta, capacidad 31); códigos [422, 422, 422, 422, 422, 422, 422]
- **RNF-25 · Efectos duplicados ante reintentos de webhook:** 0 efectos (cumple = 0). 16 entregas (10 concurrentes, 5 reintentos, 1 evento nuevo de la misma transacción); efectos {confirmaciones=1, correos de voucher=1, pagos aprobados=1, dispersiones=1, vouchers=1}
- **RNF-25 · Entregas de webhook respondidas 200 (sin reintento infinito):** 100 % (cumple = 100 %). un 5xx haría que la pasarela siga reintentando
- **RNF-26 · Disponibilidad de la API con un worker fallando:** 100 % (cumple = 100 %). 45 llamadas a la API mientras el worker de expiración falló 40 veces seguidas (el planificador siguió ejecutándolo)
- **RNF-26 · Reglas de desacoplamiento de workers cumplidas:** 100 % (cumple = 100 %). 4 reglas ArchUnit sobre 4 workers: [controladores no dependen de workers, @Scheduled solo en paquetes worker, workers apagables por propiedad, servicios no dependen de workers]
- **RNF-27 · Cobertura de líneas backend (servicios y criptografía):** 92,95 % (cumple > 80 %). 14 paquetes; el más bajo common.crypto con 86,7 %; total del backend 91 %
- **RNF-27 · Cobertura de líneas PWA (features y shared):** 93,14 % (cumple > 80 %). sentencias 91,4 %, ramas 83,9 %, funciones 90,4 %
- **RNF-28 · Endpoints implementados documentados en OpenAPI:** 100 % (cumple = 100 %). 47 operaciones implementadas; sin documentar: []; documentadas pendientes de implementar: [POST /api/v1/offline/sync]
- **RNF-30 · Componentes base reutilizados en 3 o más módulos:** 100 % (cumple = 100 % (>= 3 vistas o módulos cada uno)). Button=16, TextField=6, PasswordField=3, Icon=28, ProblemNotice=13, Skeleton=10, StickyActionBar=9, BottomSheet=4

## 5.4 No conformidades detectadas

| RNF | Hallazgo | Valor | Decisión pendiente |
|---|---|---|---|
| RNF-01 | Decimales de latitud expuestos en el detalle público | 6 decimales (umbral <= 2 (ofuscación por radio)) | experiencia creada con latitud 6.251234; respuesta: 6.251234 |
| RNF-01 | Documento y teléfono de auth.users cifrados | 0 % (umbral = 100 %) | auth.users.doc_number y auth.users.phone (dominios [doc_number, phone_e164]) |
| RNF-06 | Canales de alta de cuentas que registran el consentimiento | 66,67 % (umbral = 100 % (cero tolerancia legal)) | autoservicio y enrolamiento asistido lo registran al crear la cuenta; el bootstrap del ADMIN (ADR-0005 §9) no: lo pide en el primer ingreso (requiresConsentUpdate) |
| RNF-23 | Campos de salud y emergencia presentes en el PDF | 0 % (umbral = 100 % (EPS y contacto de emergencia)) | EPS presente = false, contacto de emergencia presente = false |
| RNF-15 | Legibilidad Flesch-Szigriszt del consentimiento | 56 puntos (umbral > 60) | 56 palabras, 123 sílabas, 4 frases; por finalidad: PERSONAL_DATA=58.4, HEALTH_EMERGENCY=62.0, SENSITIVE_VICTIM=41.5 |

## 5.5 Métricas de proceso que no mide la suite

| Métrica (§3.3 / §3.4) | Cómo se obtiene |
|---|---|
| Defectos por módulo, MTTR, cumplimiento de entregables | Gestor de incidencias (Jira) por sprint; las no conformidades de 5.4 se registran allí como defectos |
| Tiempo de enrolamiento asistido (< 3 min, RNF-12) | Prueba de usuario en campo con timestamps de la PWA |
| Eficacia en verificación comunitaria (> 95 %) | Consulta sobre catalog.host_profiles y aval de la asociación en producción |
| Tasa de entrega efectiva del voucher en Gmail (> 99,5 %) | Registros del proveedor SMTP en producción (notification.notification_outbox.sent_at) |
| Efectividad en procesamiento de pagos (> 95 %) | payments.payment_transactions en sandbox Wompi / producción |
| Tiempo de dispersión bancaria (< 4 h hábiles), regla 72 h, validación offline | Se medirán con la Fase 2 (T-2.1 a T-2.13) |
