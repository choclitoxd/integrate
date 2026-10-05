# Graph Report - integrate  (2026-10-05)

## Corpus Check
- 38 files · ~54,758 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 6 file(s) not represented in the graph (top: (none) 3, .properties 1, .cmd 1)

## Summary
- 776 nodes · 1230 edges · 61 communities (39 shown, 22 thin omitted)
- Extraction: 91% EXTRACTED · 9% INFERRED · 0% AMBIGUOUS · INFERRED: 105 edges (avg confidence: 0.9)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- RF-01 Autenticación y Control de Sesión (JWT)
- Esquema Catálogo (V3)
- RF-20 Bitácora Inmutable de Auditoría
- V5__payments.sql
- 2.3 Fichas Técnicas de los Object Stores
- POST /api/v1/bookings/lock (lockBookingSlots)
- payments.disbursements (85% en custodia)
- GlobalExceptionHandler.java
- RF-17 Validación Offline de Voucher
- V2__auth.sql
- Bloqueo Pesimista SELECT ... FOR UPDATE
- V7__offline.sql
- V4__booking.sql
- V8__audit_notification.sql
- V6__voucher.sql
- V10__api_contract_alignment.sql
- Contrato de API y DTOs – Núcleo Transaccional Intégrate
- DIRECTIVA DE SEGURIDAD Y GESTIÓN DE SECRETOS (SEC-DIR-004)
- Actor: Administrador de la Plataforma
- 3. Backlog Detallado de Tareas Técnicas
- ADR-0001: Orquestar la infraestructura local con Docker Compose y variables de entorno segregadas por plantilla
- OfflineSyncItemResult
- Actor: Anfitrión Comunitario
- RNF-07 Accesibilidad WCAG 2.1 AA / NTC 5854
- Actor: Turista
- Procedimiento de Ejecución en 2 Pasos
- 01_create_bucket.sh
- Integrate API OpenAPI 3.1 spec
- RF-04 Consulta de Catálogo y Aforos
- ADR-0002: Definición del Stack Base de Construcción y Dependencias del Backend (Spring Boot 4.1.x, Java 21, Maven)
- com.integrate:backend-api
- Directrices de Arquitectura Backend, Estructura Modular y Gobernanza Técnica
- ErrorCode
- .status
- TraceIdFilter.java
- .title
- booking.bookings
- CommonArchitectureTest.java
- ProbeController
- RF-07 Revisión y Autorización de Experiencia
- TraceIdFilterTest.java
- Ley 1581 de 2012 (Habeas Data)
- Política Determinista de Protección Comunitaria (85% irrenunciable)
- RF-12 Procesamiento de Pago y Split Payment
- ErrorCodeTest.java
- Sistema Intégrate (Plataforma de Turismo Comunitario)
- Migraciones Flyway V1–V9
- Arquitectura Hexagonal / Clean Layered Modular
- Proyecto heredado FoodExpress (a erradicar)
- catalog.host_profiles
- Especificación Técnica de Requerimientos Intégrate
- Actor: Gestor de Enlace Comunitario (Proxy Digital)
- Desnormalizaciones controladas D1–D8
- .validate

## God Nodes (most connected - your core abstractions)
1. `ErrorCode` - 32 edges
2. `GlobalExceptionHandler` - 29 edges
3. `GlobalExceptionHandlerTest` - 19 edges
4. `catalog.experiences` - 17 edges
5. `payments.payment_transactions` - 16 edges
6. `booking.bookings` - 14 edges
7. `TraceIdFilter` - 13 edges
8. `BusinessRuleException` - 11 edges
9. `payments.disbursements` - 11 edges
10. `booking.bookings` - 11 edges

## Surprising Connections (you probably didn't know these)
- `4.1 Ubicación Exacta de Componentes Técnicos` --references--> `GlobalExceptionHandler`  [INFERRED]
  docs/architecture/backend_guidelines.md → backend-api/src/main/java/com/integrate/common/exception/GlobalExceptionHandler.java
- `2. Formato transversal de errores (RFC 9457)` --references--> `GlobalExceptionHandler`  [INFERRED]
  docs/api/dtos_contrato_api.md → backend-api/src/main/java/com/integrate/common/exception/GlobalExceptionHandler.java
- `Decisión` --references--> `GlobalExceptionHandler`  [INFERRED]
  docs/architecture/adr/ADR-0003-estructura-comun-errores-rfc9457-trazabilidad.md → backend-api/src/main/java/com/integrate/common/exception/GlobalExceptionHandler.java
- `4. Bitácora de Avances` --references--> `GlobalExceptionHandler`  [INFERRED]
  docs/status/PROJECT_STATUS.md → backend-api/src/main/java/com/integrate/common/exception/GlobalExceptionHandler.java
- `Decisión` --references--> `InvalidParam`  [INFERRED]
  docs/architecture/adr/ADR-0003-estructura-comun-errores-rfc9457-trazabilidad.md → backend-api/src/main/java/com/integrate/common/payload/InvalidParam.java

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Desnormalizaciones controladas D1–D8** — docs_modelo_relacional_d1_available_slots, docs_modelo_relacional_d3_assignment_association, docs_modelo_relacional_d4_booking_price, docs_modelo_relacional_d5_hours_before_start, docs_modelo_relacional_d7_disbursement_association [EXTRACTED 1.00]
- **Operación territorial offline: padrón → validación → sync → conflicto** — docs_especificacion_tecnica_rf_16_descarga_padron, docs_especificacion_tecnica_rf_17_validacion_offline, docs_especificacion_tecnica_rf_18_background_sync, docs_especificacion_tecnica_indexeddb_stores, docs_modelo_relacional_sync_batches, docs_modelo_relacional_offline_validations, docs_modelo_relacional_sync_incidents [EXTRACTED 1.00]
- **Flujo transaccional reserva → pago → voucher** — docs_especificacion_tecnica_rf_09_bloqueo_pesimista, docs_especificacion_tecnica_rf_10_datos_medicos_reserva, docs_especificacion_tecnica_rf_12_pago_split, docs_especificacion_tecnica_rf_15_emision_voucher, docs_especificacion_tecnica_rf_11_expiracion_ttl_worker [INFERRED 0.85]
- **Salvaguarda del 85% comunitario** — docs_especificacion_tecnica_split_payment_85_15, docs_especificacion_tecnica_community_protection_policy, docs_especificacion_tecnica_cancellation_policy_72h, docs_modelo_relacional_disbursements, docs_modelo_relacional_disbursement_state_machine, docs_modelo_relacional_commission_policies, docs_roadmap_ejecucion_t_3_3_conflict_resolution [INFERRED 0.85]
- **Adaptadores de pasarela que producen CanonicalPaymentEvent** — docs_api_openapi_mockpaymentgatewayadapter, docs_api_openapi_wompipaymentgatewayadapter, docs_api_openapi_canonicalpaymentevent, docs_api_openapi_wompievent [INFERRED 0.85]
- **Operación territorial offline: padrón, validación, sincronización** — docs_especificacion_tecnica_rf_16_descarga_padron, docs_especificacion_tecnica_rf_17_validacion_offline, docs_especificacion_tecnica_rf_18_background_sync_conflictos, docs_especificacion_tecnica_integrateofflinedb, docs_modelo_relacional_offline_validations, docs_modelo_relacional_sync_incidents [INFERRED 0.85]

## Communities (61 total, 22 thin omitted)

### Community 0 - "RF-01 Autenticación y Control de Sesión (JWT)"
Cohesion: 0.20
Nodes (10): POST /api/v1/auth/login & refresh, RF-01 Autenticación y Control de Sesión (JWT), RF-02 Captura y Auditoría de Consentimiento Habeas Data, RF-03 Gestión y Asignación de Roles RBAC, Esquema auth (users, roles, user_roles, privacy_policies, habeas_data_consents, login_attempts, refresh_tokens), auth.habeas_data_consents (append-only), auth.refresh_tokens / login_attempts, auth.user_roles / auth.roles (RBAC N:M) (+2 more)

### Community 1 - "Esquema Catálogo (V3)"
Cohesion: 0.09
Nodes (39): catalog.categories, catalog.community_associations, catalog.departments, catalog.experience_categories, catalog.experience_images, catalog.experience_status_history, catalog.experiences, catalog.fn_experience_guard() (+31 more)

### Community 2 - "RF-20 Bitácora Inmutable de Auditoría"
Cohesion: 0.40
Nodes (5): AuditLogService, RF-20 Bitácora Inmutable de Auditoría, RNF-09 Retención de Auditoría 10 años, audit.audit_trail + chain_head (bitácora encadenada SHA-256, particionada), Guía backend: bloqueo de aforo con idempotency_key y lock_timeout 2s

### Community 3 - "V5__payments.sql"
Cohesion: 0.10
Nodes (36): ix_disbursements_association, ix_disbursements_batch, ix_disbursements_due, ix_disbursements_ready, ix_payment_transactions_booking, ix_payment_transactions_open, ix_payment_transactions_policy, ix_payment_webhook_events_payload (+28 more)

### Community 4 - "2.3 Fichas Técnicas de los Object Stores"
Cohesion: 0.08
Nodes (23): 1. Matriz de Delimitación de Alcance de Datos (Inclusión vs. Exclusión), 2.1 Identificación de la Base Local, 2.2 Estructura de Almacenes Locales (6 Object Stores), 2.3 Fichas Técnicas de los Object Stores, 2. Especificación Arquitectónica de los Object Stores, 3.1 Metadatos de Sincronización por Registro, 3.2 Especificación Conceptual de la Cola de Sincronización (`sync_outbox`), 3.3 Matriz de Políticas de Resolución de Conflictos (+15 more)

### Community 5 - "POST /api/v1/bookings/lock (lockBookingSlots)"
Cohesion: 0.07
Nodes (36): AccountLockedProblem, AssistedConsent, AssistedHostRegistrationRequest, AssistedHostRegistrationResponse, AuthenticatedUser, AuthTokenResponse, bearerAuth (JWT RS256, TTL 900 s), BookingConflictProblem (+28 more)

### Community 6 - "payments.disbursements (85% en custodia)"
Cohesion: 0.30
Nodes (11): Actor: Asociación Comunitaria de Turismo, RF-19 Conciliación y Dispersión Definitiva, SplitPaymentService, payments.commission_policies (85/15, 72h, 24h), catalog.community_associations, payments.disbursements (85% en custodia), payments.payment_transactions, Esquema payments (+3 more)

### Community 7 - "GlobalExceptionHandler.java"
Cohesion: 0.08
Nodes (4): BusinessRuleException, GlobalExceptionHandler, InvalidParam, 3. Diccionario de Carpetas y Responsabilidades

### Community 8 - "RF-17 Validación Offline de Voucher"
Cohesion: 0.13
Nodes (22): GET /api/v1/offline/day-roster, Voucher Criptográfico JWT (RS256/Ed25519) en QR, Voucher Criptográfico JWT (RS256/Ed25519) en QR/PDF, IndexedDB Stores (crypto_keys, revoked_vouchers, local_consumed_vouchers), IntegrateOfflineDB (public_signing_keys, revocation_list, operational_roster, local_validations, sync_outbox), RF-15 Emisión de Voucher y Notificación, RF-16 Descarga Preventiva de Padrón y Revocación, RF-17 Validación Offline de Voucher (+14 more)

### Community 9 - "V2__auth.sql"
Cohesion: 0.16
Nodes (22): auth.habeas_data_consents, auth.login_attempts, auth.privacy_policies, auth.refresh_tokens, auth.roles, auth.user_roles, auth.users, auth.v_current_consents (+14 more)

### Community 10 - "Bloqueo Pesimista SELECT ... FOR UPDATE"
Cohesion: 0.09
Nodes (21): POST /api/v1/bookings/lock, POST /api/v1/bookings/lock-slots, TTL de Reserva 15 minutos, BookingConcurrencyEngine, BookingExpirationWorker (@Scheduled 60s), Pasarela de Pagos / Split Payment (Wompi / ePayco), PaymentGatewayAdapter (Mock / Wompi), RF-09 Bloqueo Pesimista de Aforo (+13 more)

### Community 11 - "V7__offline.sql"
Cohesion: 0.17
Nodes (16): ix_devices_manager, ix_offline_validations_batch, ix_offline_validations_device, ix_offline_validations_validated_by, ix_offline_validations_voucher, ix_roster_downloads_association, ix_roster_downloads_device, ix_roster_downloads_signing_key (+8 more)

### Community 12 - "V4__booking.sql"
Cohesion: 0.19
Nodes (15): booking.booking_cancellations, booking.booking_participants, booking.booking_status_history, booking.bookings, booking.fn_participant_guard(), booking.v_schedule_slot_reconciliation, ix_booking_status_history_booking, ix_bookings_pending_expiry (+7 more)

### Community 13 - "V8__audit_notification.sql"
Cohesion: 0.19
Nodes (14): audit.audit_trail, audit.chain_head, audit.fn_chain_append(), audit.verify_chain(), ix_audit_trail_action, ix_audit_trail_actor, ix_audit_trail_details, ix_audit_trail_entity (+6 more)

### Community 14 - "V6__voucher.sql"
Cohesion: 0.33
Nodes (8): ix_vouchers_issued_open, ix_vouchers_revoked_at, ix_vouchers_signing_key, trg_voucher_guard, uq_signing_keys_one_active, voucher.fn_voucher_guard(), voucher.signing_keys, voucher.vouchers

### Community 15 - "V10__api_contract_alignment.sql"
Cohesion: 0.22
Nodes (3): ix_devices_host, uq_bookings_idempotency, uq_offline_validations_local_id

### Community 17 - "Contrato de API y DTOs – Núcleo Transaccional Intégrate"
Cohesion: 0.10
Nodes (21): 1. Endpoints, 3.1 `LoginRequest` → `LoginRequest.java`, 3.2 `AuthTokenResponse` (login y refresh), 3.3 `AssistedHostRegistrationRequest`, 3.4 `AssistedHostRegistrationResponse` (201), 3. Autenticación, 4. Disponibilidad – `GET /experiences/{id}/availability`, 5.1 `BookingLockRequest` (+13 more)

### Community 20 - "DIRECTIVA DE SEGURIDAD Y GESTIÓN DE SECRETOS (SEC-DIR-004)"
Cohesion: 0.11
Nodes (17): 1. Principio Fundamental: Tolerancia Cero al "Hardcoding", 2.1 Configuración Mandatoria en `.gitignore`, 2.2 Plantillas `.env.example`, 2. Gestión de Entornos Locales y `.gitignore`, 3.1 GitHub Encrypted Secrets y Environments, 3.2 GitHub Secret Scanning y Push Protection, 3.3 Barrido en GitHub Actions (PR Gatekeeper), 3. Gobernanza de Secretos en GitHub y CI/CD (+9 more)

### Community 22 - "3. Backlog Detallado de Tareas Técnicas"
Cohesion: 0.09
Nodes (15): TimeConfig, IntegrateApplication, 1.1 Diagnóstico de Artefactos Existentes, 1. Estado y Arquitectura Detectada, 2. Roadmap por Módulos (Backend y Frontend), 3. Backlog Detallado de Tareas Técnicas, 4. Bitácora de Avances, Estado del Proyecto Intégrate (PROJECT_STATUS.md) (+7 more)

### Community 23 - "ADR-0001: Orquestar la infraestructura local con Docker Compose y variables de entorno segregadas por plantilla"
Cohesion: 0.15
Nodes (11): ADR-0001: Orquestar la infraestructura local con Docker Compose y variables de entorno segregadas por plantilla, Alternativas Descartadas, Consecuencias, Contexto, Contrato o Esquema Conceptual, Decisión, 1. Preparar variables (una vez), 2. Levantar (+3 more)

### Community 25 - "OfflineSyncItemResult"
Cohesion: 0.29
Nodes (7): IncidentType, OfflineSyncBatchRequest, OfflineSyncBatchResponse, OfflineSyncItemResult, OfflineValidationItem, ServerOutcome, POST /api/v1/offline/sync (syncOfflineValidations)

### Community 31 - "Procedimiento de Ejecución en 2 Pasos"
Cohesion: 0.33
Nodes (5): 1. Paso Previo (ADR), 2. Validación, Procedimiento de Ejecución en 2 Pasos, PROTOCOLO OBLIGATORIO: GENERACIÓN PREVIA DE ADR, Regla de Ejecución

### Community 35 - "ADR-0002: Definición del Stack Base de Construcción y Dependencias del Backend (Spring Boot 4.1.x, Java 21, Maven)"
Cohesion: 0.29
Nodes (6): ADR-0002: Definición del Stack Base de Construcción y Dependencias del Backend (Spring Boot 4.1.x, Java 21, Maven), Alternativas Descartadas, Consecuencias, Contexto, Contrato o Esquema Conceptual, Decisión

### Community 37 - "Directrices de Arquitectura Backend, Estructura Modular y Gobernanza Técnica"
Cohesion: 0.15
Nodes (13): 1.1 Patrón Arquitectónico, 1.2 Stack Tecnológico Confirmado, 1. Resumen Arquitectónico y Stack Confirmado, 2. Árbol Completo de Directorios (Formato ASCII), 4.1 Ubicación Exacta de Componentes Técnicos, 4. Mapeo de Componentes Técnicos y Aislamiento de Esquemas, 5.1 Matriz de Invocación entre Capas, 5.2 Regla de Oro: Desacoplamiento Escalar de Entidades JPA (+5 more)

### Community 38 - "ErrorCode"
Cohesion: 0.09
Nodes (22): ErrorCode, AUTH_ACCOUNT_LOCKED, AUTH_ACCOUNT_SUSPENDED, AUTH_INVALID_CREDENTIALS, AUTH_REFRESH_TOKEN_INVALID, AUTH_TOKEN_INVALID, AUTH_USER_ALREADY_EXISTS, BOOKING_INSUFFICIENT_SLOTS (+14 more)

### Community 40 - ".status"
Cohesion: 0.21
Nodes (7): GlobalExceptionHandlerTest, D. Object Store: `public_signing_keys`, Delimitación de Atributos Persistidos, Delimitación de Atributos Persistidos, F. Object Store: `sync_outbox`, Matriz de Índices, Matriz de Índices

### Community 42 - ".title"
Cohesion: 0.18
Nodes (10): 2. Formato transversal de errores (RFC 9457), ADR-0003: Estructura base común — errores RFC 9457, catálogo de códigos y trazabilidad `X-Trace-Id`, Alternativas Descartadas, Consecuencias, Contexto, Contrato o Esquema Conceptual, Decisión, A. Object Store: `read_only_catalog` (+2 more)

### Community 43 - "booking.bookings"
Cohesion: 0.23
Nodes (9): RF-10 Reserva y Captura de Datos Médicos, RF-13 Cancelación con Reembolso (>72h), RF-14 Retención Comunitaria (<=72h / No-Show), booking.booking_cancellations, booking.booking_participants (EPS cifrada), Esquema booking, booking.bookings, payments.refunds (+1 more)

### Community 46 - "RF-07 Revisión y Autorización de Experiencia"
Cohesion: 0.20
Nodes (6): RF-04 Consulta de Catálogo Territorial y Aforos, RF-07 Revisión y Autorización de Experiencia, RF-08 Moderación y Suspensión Preventiva, Máquina de estados de Reserva, Máquina de estados de Dispersión (CANCELLED→RELEASED solo en SYNC_CONFLICT), Máquina de estados de Experiencia

### Community 48 - "Ley 1581 de 2012 (Habeas Data)"
Cohesion: 0.28
Nodes (5): Ley 1581 de 2012 (Habeas Data), RF-10 Reserva y Captura de Datos Médicos/Emergencia, RNF-01 Seguridad: AES-256-GCM y BCrypt/Argon2id, R-05 Fuga de datos de víctimas (Ley 1581), T-4.1 Auditoría Inmutable Habeas Data y Cifrado AES-256

### Community 49 - "Política Determinista de Protección Comunitaria (85% irrenunciable)"
Cohesion: 0.32
Nodes (7): POST /api/v1/offline/sync, OfflineSyncService / ConflictResolutionService, RF-18 Background Sync y Resolución Determinista de Conflictos, RF-18 Background Sync y Resolución de Conflictos, R-04 Reclamaciones por cancelación con discrepancia offline, T-3.2 Endpoint de Sincronización Idempotente, T-3.3 Motor de Resolución Determinista de Conflictos

### Community 50 - "RF-12 Procesamiento de Pago y Split Payment"
Cohesion: 0.36
Nodes (8): POST /api/v1/payments/webhook/{mock|wompi}, NotificationDispatcherService (Email/WhatsApp adapters), RF-12 Procesamiento de Pago y Split Payment, RNF-06 Idempotencia en Pagos y Sincronización, Servicio de Notificaciones (Gmail/SES/Twilio), notification.notification_outbox (Transactional Outbox), payments.payment_webhook_events (bandeja idempotente), Webhook INSERT ON CONFLICT (provider, idempotency_key) DO NOTHING

### Community 52 - "Sistema Intégrate (Plataforma de Turismo Comunitario)"
Cohesion: 0.29
Nodes (5): Intégrate - Plataforma Transaccional de Turismo Comunitario, Ley 1448 de 2011 (Víctimas), RNF-04 Arquitectura Offline-First y Resiliencia, Sistema Intégrate (Plataforma de Turismo Comunitario), Roadmap Técnico de Ejecución Intégrate (5 fases)

### Community 53 - "Migraciones Flyway V1–V9"
Cohesion: 0.29
Nodes (4): Roles de BD integrate_owner / integrate_api, Migraciones Flyway V1–V9, V9 Endurecimiento de privilegios, T-0.4 Migración inicial Flyway (V1__initial_schema.sql)

### Community 54 - "Arquitectura Hexagonal / Clean Layered Modular"
Cohesion: 0.33
Nodes (4): Módulos com.integrate (auth, catalog, booking, payment, voucher, offline, audit), Pasarela de Pagos Wompi / ePayco, Stack: Java 21/Spring Boot 3, React 18 PWA, PostgreSQL 16, AWS, T-4.4 Pipeline CI/CD y Despliegue AWS (ECS/RDS/S3)

### Community 55 - "Proyecto heredado FoodExpress (a erradicar)"
Cohesion: 0.33
Nodes (6): Proyecto heredado FoodExpress (a erradicar), Fase 0: Setup e Infraestructura / DB, Fase 1: MVP Core Transaccional, Fase 2: PWA Offline-First y Validación Territorial, Fase 3: Sincronización, Enrolamiento Asistido y Finanzas, Fase 4: Auditoría, Hardening y Cloud AWS

### Community 56 - "catalog.host_profiles"
Cohesion: 0.53
Nodes (6): Esquema catalog (DIVIPOLA, asociaciones, perfiles, experiencias, franjas), catalog.departments / municipalities (DIVIPOLA), catalog.experiences, catalog.host_manager_assignments, catalog.host_profiles, catalog.schedules (objetivo de FOR UPDATE)

### Community 58 - "Actor: Gestor de Enlace Comunitario (Proxy Digital)"
Cohesion: 0.40
Nodes (5): Actor: Gestor de Enlace Comunitario (Proxy Digital), RF-05 Registro Autónomo de Propuesta de Experiencia, RF-06 Registro Asistido por Gestor de Enlace, catalog.link_manager_profiles, T-3.5 Módulo de Gestión Asistida (Proxy Digital)

### Community 59 - "Desnormalizaciones controladas D1–D8"
Cohesion: 0.40
Nodes (3): Esquema DDL inicial (spec §6), Modelo Relacional Intégrate v1.0.0, Normalización 3FN

## Ambiguous Edges - Review These
- `Esquema DDL inicial (spec §6)` → `booking.bookings`  [AMBIGUOUS]
  docs/modelo_relacional.md · relation: semantically_similar_to
- `Migraciones Flyway V1–V9` → `T-0.4 Migración inicial Flyway (V1__initial_schema.sql)`  [AMBIGUOUS]
  docs/roadmap_ejecucion.md · relation: conceptually_related_to

## Knowledge Gaps
- **147 isolated node(s):** `com.integrate:backend-api`, `MALFORMED_REQUEST`, `VALIDATION_FAILED`, `RESOURCE_NOT_FOUND`, `RBAC_FORBIDDEN` (+142 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 251 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **22 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What is the exact relationship between `Esquema DDL inicial (spec §6)` and `booking.bookings`?**
  _Edge tagged AMBIGUOUS (relation: semantically_similar_to) - confidence is low._
- **What is the exact relationship between `Migraciones Flyway V1–V9` and `T-0.4 Migración inicial Flyway (V1__initial_schema.sql)`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
- **Why does `Especificación Técnica de Requerimientos Intégrate` connect `Especificación Técnica de Requerimientos Intégrate` to `2.3 Fichas Técnicas de los Object Stores`, `Sistema Intégrate (Plataforma de Turismo Comunitario)`?**
  _High betweenness centrality (0.172) - this node is a cross-community bridge._
- **Why does `Intégrate - Plataforma Transaccional de Turismo Comunitario` connect `Sistema Intégrate (Plataforma de Turismo Comunitario)` to `RF-17 Validación Offline de Voucher`, `Especificación Técnica de Requerimientos Intégrate`?**
  _High betweenness centrality (0.136) - this node is a cross-community bridge._
- **Why does `Ley 1448 de 2011 (Víctimas)` connect `Sistema Intégrate (Plataforma de Turismo Comunitario)` to `catalog.host_profiles`?**
  _High betweenness centrality (0.100) - this node is a cross-community bridge._
- **Are the 5 inferred relationships involving `GlobalExceptionHandler` (e.g. with `2. Formato transversal de errores (RFC 9457)` and `Decisión`) actually correct?**
  _`GlobalExceptionHandler` has 5 INFERRED edges - model-reasoned connections that need verification._
- **What connects `com.integrate:backend-api`, `MALFORMED_REQUEST`, `VALIDATION_FAILED` to the rest of the system?**
  _147 weakly-connected nodes found - possible documentation gaps or missing edges._