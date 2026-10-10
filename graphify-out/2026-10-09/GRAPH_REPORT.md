# Graph Report - integrate  (2026-10-09)

## Corpus Check
- 482 files · ~201,525 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 9 file(s) not represented in the graph (top: (none) 4, .example 2, .properties 1)

## Summary
- 3865 nodes · 11852 edges · 163 communities (117 shown, 46 thin omitted)
- Extraction: 91% EXTRACTED · 9% INFERRED · 0% AMBIGUOUS · INFERRED: 1014 edges (avg confidence: 0.86)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `999aa86d`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- RF-01 Autenticación y Control de Sesión (JWT)
- Esquema Catálogo (V3)
- jakarta.persistence.Entity
- V5__payments.sql
- 2.3 Fichas Técnicas de los Object Stores
- POST /api/v1/bookings/lock (lockBookingSlots)
- payments.disbursements (85% en custodia)
- GlobalExceptionHandler.java
- RF-17 Validación Offline de Voucher
- V2__auth.sql
- RF-09 Bloqueo Pesimista de Aforo
- V7__offline.sql
- V4__booking.sql
- V8__audit_notification.sql
- V6__voucher.sql
- V10__api_contract_alignment.sql
- OutboxDispatcher.java
- DIRECTIVA DE SEGURIDAD Y GESTIÓN DE SECRETOS (SEC-DIR-004)
- Actor: Administrador de la Plataforma
- Estado del Proyecto Intégrate (PROJECT_STATUS.md)
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
- 4. Bitácora de Avances
- ErrorCode
- GestorHostServiceImpl.java
- .uniqueEmail
- TraceIdFilter
- ApiError
- booking.bookings
- compilerOptions
- session/index.ts
- api/index.ts
- TestKeys
- PaymentApiIntegrationTest
- Política Determinista de Protección Comunitaria (85% irrenunciable)
- BookingApiIntegrationTest
- VoucherApiIntegrationTest.java
- AesGcmSensitiveDataCipher
- devDependencies
- Sistema Intégrate (Plataforma de Turismo Comunitario)
- compilerOptions
- BookingStatusPage.tsx
- package.json
- Actor: Gestor de Enlace Comunitario (Proxy Digital)
- uuid
- ADR-0004: Boilerplate de `frontend-pwa` — Vite 8 + React 19 + TypeScript 6 + Tailwind CSS 4, estructura por *features*
- NotificationIntegrationTest
- Decisión
- catalog.host_profiles
- ConsentAndBootstrapIntegrationTest.java
- Icon
- BookingFormPage.tsx
- AssistedEnrollmentApiIntegrationTest
- AssistedEnrollmentServiceImpl.java
- .prettierrc.json
- org.springframework.stereotype.Component
- vite-env.d.ts
- tsconfig.json
- PaymentEventProcessor.java
- CredentialActivationCode
- StorageConfig.java
- EnrollmentDocuments.java
- SigningKey
- HostExperienceController.java
- AuthApiIntegrationTest.java
- ClientInfo
- Experience
- RegisterPage.tsx
- CommonArchitectureTest.java
- LinkManagerResponse
- list
- PROTOCOLO DE FLUJO DE TRABAJO EN GIT Y GOBERNANZA DE CAMBIOS
- catalog/queries.ts
- org.junit.jupiter.api.Test
- SlotPickerPage.tsx
- PageResponse
- .provisionHost
- .notFound
- .register
- Decisión
- CatalogQueryServiceImpl.java
- CatalogApiIntegrationTest
- LoginRequest
- User
- HostExperienceServiceImpl.java
- BookingLockServiceImpl.java
- BusinessRuleException
- DocType
- BookingPayments
- ActorPropagatingTransactionManager.java
- SecurityConfig.java
- MockPaymentGatewayAdapter.java
- Directrices de Arquitectura Backend, Estructura Modular y Gobernanza Técnica
- Decisión
- duration
- PaymentMethod
- VoucherEmail.java
- HostExperienceServiceImpl
- org.springframework.context.annotation.Bean
- standardcharsets
- HostExperienceResponse
- ScheduleRepository
- ExperienceViews.java
- Booking
- PasswordResetApiIntegrationTest
- 3. Autenticación
- PaymentWebhookController.java
- org.springframework.web.bind.annotation.PostMapping
- BookingConfirmedEvent
- lombok.RequiredArgsConstructor
- VoucherPdfRenderer
- org.springframework.web.bind.annotation.GetMapping
- Decisión
- tokens.ts
- S3DocumentStorage.java
- VoucherDocumentStorage
- VoucherApiIntegrationTest
- java.net.InetAddress
- PostgresIntegrationTest
- JwtAccessTokenIssuer.java
- PaymentProvider
- main.tsx
- WompiPaymentGatewayAdapterTest
- eslint.config.js
- ProblemAuthenticationEntryPoint.java
- ADR-0016: Checkout móvil con cuenta regresiva anclada al servidor y pago por redirección a la pasarela
- org.springframework.security.oauth2.jwt.Jwt
- WompiPaymentGatewayAdapter
- scripts
- java.security.SecureRandom
- InMemoryVoucherDocumentStorage
- BookingController.java
- dependencies
- Ley 1581 de 2012 (Habeas Data)
- vite.config.ts
- .title
- Proyecto heredado FoodExpress (a erradicar)
- auth.password_reset_tokens
- PaymentGatewayAdapter (Mock / Wompi)

## God Nodes (most connected - your core abstractions)
1. `BusinessRuleException` - 76 edges
2. `ErrorCode` - 65 edges
3. `ClientInfo` - 58 edges
4. `Icon()` - 50 edges
5. `RoleCode` - 48 edges
6. `4. Bitácora de Avances` - 48 edges
7. `Experience` - 47 edges
8. `HostExperienceResponse` - 45 edges
9. `ConsentPurpose` - 44 edges
10. `BookingApiIntegrationTest` - 44 edges

## Surprising Connections (you probably didn't know these)
- `6. Auditoría (`common/audit`, primer escritor de `audit.audit_trail`)` --references--> `AuditTrail`  [INFERRED]
  docs/architecture/adr/ADR-0008-reserva-concurrente-bloqueo-pesimista-ttl.md → backend-api/src/main/java/com/integrate/common/audit/AuditTrail.java
- `4. Cifrado AES-256-GCM de datos de víctima (`common/crypto`)` --references--> `SensitiveDataCipher`  [INFERRED]
  docs/architecture/adr/ADR-0007-enrolamiento-asistido-gestor-enlace.md → backend-api/src/main/java/com/integrate/common/crypto/SensitiveDataCipher.java
- `1. Alcance y rutas` --references--> `PageResponse`  [INFERRED]
  docs/architecture/adr/ADR-0007-enrolamiento-asistido-gestor-enlace.md → backend-api/src/main/java/com/integrate/common/payload/PageResponse.java
- `5. Documentos en S3: subida prefirmada (`common/storage`)` --references--> `DocumentStorage`  [INFERRED]
  docs/architecture/adr/ADR-0007-enrolamiento-asistido-gestor-enlace.md → backend-api/src/main/java/com/integrate/common/storage/DocumentStorage.java
- `6. Adjuntos` --references--> `VoucherDocumentStorage`  [INFERRED]
  docs/architecture/adr/ADR-0011-notificaciones-outbox-correo-voucher.md → backend-api/src/main/java/com/integrate/common/storage/VoucherDocumentStorage.java

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Desnormalizaciones controladas D1–D8** — docs_modelo_relacional_d1_available_slots, docs_modelo_relacional_d3_assignment_association, docs_modelo_relacional_d4_booking_price, docs_modelo_relacional_d5_hours_before_start, docs_modelo_relacional_d7_disbursement_association [EXTRACTED 1.00]
- **Operación territorial offline: padrón → validación → sync → conflicto** — docs_especificacion_tecnica_rf_16_descarga_padron, docs_especificacion_tecnica_rf_17_validacion_offline, docs_especificacion_tecnica_rf_18_background_sync, docs_especificacion_tecnica_indexeddb_stores, docs_modelo_relacional_sync_batches, docs_modelo_relacional_offline_validations, docs_modelo_relacional_sync_incidents [EXTRACTED 1.00]
- **Flujo transaccional reserva → pago → voucher** — docs_especificacion_tecnica_rf_09_bloqueo_pesimista, docs_especificacion_tecnica_rf_10_datos_medicos_reserva, docs_especificacion_tecnica_rf_12_pago_split, docs_especificacion_tecnica_rf_15_emision_voucher, docs_especificacion_tecnica_rf_11_expiracion_ttl_worker [INFERRED 0.85]
- **Salvaguarda del 85% comunitario** — docs_especificacion_tecnica_split_payment_85_15, docs_especificacion_tecnica_community_protection_policy, docs_especificacion_tecnica_cancellation_policy_72h, docs_modelo_relacional_disbursements, docs_modelo_relacional_disbursement_state_machine, docs_modelo_relacional_commission_policies, docs_roadmap_ejecucion_t_3_3_conflict_resolution [INFERRED 0.85]
- **Adaptadores de pasarela que producen CanonicalPaymentEvent** — docs_api_openapi_mockpaymentgatewayadapter, docs_api_openapi_wompipaymentgatewayadapter, docs_api_openapi_canonicalpaymentevent, docs_api_openapi_wompievent [INFERRED 0.85]
- **Operación territorial offline: padrón, validación, sincronización** — docs_especificacion_tecnica_rf_16_descarga_padron, docs_especificacion_tecnica_rf_17_validacion_offline, docs_especificacion_tecnica_rf_18_background_sync_conflictos, docs_especificacion_tecnica_integrateofflinedb, docs_modelo_relacional_offline_validations, docs_modelo_relacional_sync_incidents [INFERRED 0.85]

## Communities (163 total, 46 thin omitted)

### Community 0 - "RF-01 Autenticación y Control de Sesión (JWT)"
Cohesion: 0.22
Nodes (10): POST /api/v1/auth/login & refresh, RF-01 Autenticación y Control de Sesión (JWT), RF-02 Captura y Auditoría de Consentimiento Habeas Data, RF-03 Gestión y Asignación de Roles RBAC, Esquema auth (users, roles, user_roles, privacy_policies, habeas_data_consents, login_attempts, refresh_tokens), auth.habeas_data_consents (append-only), auth.refresh_tokens / login_attempts, auth.user_roles / auth.roles (RBAC N:M) (+2 more)

### Community 1 - "Esquema Catálogo (V3)"
Cohesion: 0.09
Nodes (39): catalog.categories, catalog.community_associations, catalog.departments, catalog.experience_categories, catalog.experience_images, catalog.experience_status_history, catalog.experiences, catalog.fn_experience_guard() (+31 more)

### Community 2 - "jakarta.persistence.Entity"
Cohesion: 0.21
Nodes (10): PgDomains, RefreshToken, Role, UserRole, BookingParticipant, Category, CommissionPolicy, Disbursement (+2 more)

### Community 3 - "V5__payments.sql"
Cohesion: 0.10
Nodes (36): ix_disbursements_association, ix_disbursements_batch, ix_disbursements_due, ix_disbursements_ready, ix_payment_transactions_booking, ix_payment_transactions_open, ix_payment_transactions_policy, ix_payment_webhook_events_payload (+28 more)

### Community 4 - "2.3 Fichas Técnicas de los Object Stores"
Cohesion: 0.06
Nodes (32): 1. Matriz de Delimitación de Alcance de Datos (Inclusión vs. Exclusión), 2.1 Identificación de la Base Local, 2.2 Estructura de Almacenes Locales (6 Object Stores), 2.3 Fichas Técnicas de los Object Stores, 2. Especificación Arquitectónica de los Object Stores, 3.1 Metadatos de Sincronización por Registro, 3.2 Especificación Conceptual de la Cola de Sincronización (`sync_outbox`), 3.3 Matriz de Políticas de Resolución de Conflictos (+24 more)

### Community 5 - "POST /api/v1/bookings/lock (lockBookingSlots)"
Cohesion: 0.07
Nodes (36): AccountLockedProblem, AssistedConsent, AssistedHostRegistrationRequest, AssistedHostRegistrationResponse, AuthenticatedUser, AuthTokenResponse, bearerAuth (JWT RS256, TTL 900 s), BookingConflictProblem (+28 more)

### Community 6 - "payments.disbursements (85% en custodia)"
Cohesion: 0.17
Nodes (19): POST /api/v1/payments/webhook/{mock|wompi}, Actor: Asociación Comunitaria de Turismo, NotificationDispatcherService (Email/WhatsApp adapters), RF-12 Procesamiento de Pago y Split Payment, RF-19 Conciliación y Dispersión Definitiva, RNF-06 Idempotencia en Pagos y Sincronización, Servicio de Notificaciones (Gmail/SES/Twilio), SplitPaymentService (+11 more)

### Community 8 - "RF-17 Validación Offline de Voucher"
Cohesion: 0.13
Nodes (22): GET /api/v1/offline/day-roster, Voucher Criptográfico JWT (RS256/Ed25519) en QR, Voucher Criptográfico JWT (RS256/Ed25519) en QR/PDF, IndexedDB Stores (crypto_keys, revoked_vouchers, local_consumed_vouchers), IntegrateOfflineDB (public_signing_keys, revocation_list, operational_roster, local_validations, sync_outbox), RF-15 Emisión de Voucher y Notificación, RF-16 Descarga Preventiva de Padrón y Revocación, RF-17 Validación Offline de Voucher (+14 more)

### Community 9 - "V2__auth.sql"
Cohesion: 0.16
Nodes (22): auth.habeas_data_consents, auth.login_attempts, auth.privacy_policies, auth.refresh_tokens, auth.roles, auth.user_roles, auth.users, auth.v_current_consents (+14 more)

### Community 10 - "RF-09 Bloqueo Pesimista de Aforo"
Cohesion: 0.18
Nodes (11): POST /api/v1/bookings/lock, POST /api/v1/bookings/lock-slots, AuditLogService, TTL de Reserva 15 minutos, RF-09 Bloqueo Pesimista de Aforo, RF-11 Worker de Expiración por TTL, RF-20 Bitácora Inmutable de Auditoría, RNF-09 Retención de Auditoría 10 años (+3 more)

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

### Community 17 - "OutboxDispatcher.java"
Cohesion: 0.06
Nodes (15): Backoff, Dispatch, NotificationProperties, OutboxMessage, OutboxMessageRepository, Attachment, EmailDeliveryException, EmailMessage (+7 more)

### Community 20 - "DIRECTIVA DE SEGURIDAD Y GESTIÓN DE SECRETOS (SEC-DIR-004)"
Cohesion: 0.11
Nodes (17): 1. Principio Fundamental: Tolerancia Cero al "Hardcoding", 2.1 Configuración Mandatoria en `.gitignore`, 2.2 Plantillas `.env.example`, 2. Gestión de Entornos Locales y `.gitignore`, 3.1 GitHub Encrypted Secrets y Environments, 3.2 GitHub Secret Scanning y Push Protection, 3.3 Barrido en GitHub Actions (PR Gatekeeper), 3. Gobernanza de Secretos en GitHub y CI/CD (+9 more)

### Community 22 - "Estado del Proyecto Intégrate (PROJECT_STATUS.md)"
Cohesion: 0.09
Nodes (22): 1.1 Diagnóstico de Artefactos Existentes, 1.2 Delimitación Formal de Plataforma: Aplicación Móvil (PWA Smartphone), 1. Estado y Arquitectura Detectada, 2.1 Cobertura de Módulos por Etapa, 2. Estructuración del Proyecto en Dos Etapas, 3. Backlog Detallado de Tareas Técnicas por Etapa, Cimiento Base: Infraestructura y Persistencia (Fase 0 - Completada), Estado del Proyecto Intégrate (PROJECT_STATUS.md) (+14 more)

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

### Community 37 - "4. Bitácora de Avances"
Cohesion: 0.05
Nodes (47): IntegrateApplication, Decisión, ADR-0012: Shell móvil de la PWA, estado de servidor con TanStack Query y flujo de catálogo y franjas del turista, Alternativas Descartadas, Consecuencias, Contexto, Contrato o Esquema Conceptual, Decisión (+39 more)

### Community 38 - "ErrorCode"
Cohesion: 0.05
Nodes (35): ErrorCode, AUTH_ACCOUNT_LOCKED, AUTH_ACCOUNT_SUSPENDED, AUTH_ACTIVATION_INVALID, AUTH_INVALID_CREDENTIALS, AUTH_REFRESH_TOKEN_INVALID, AUTH_RESET_TOKEN_INVALID, AUTH_TOKEN_INVALID (+27 more)

### Community 39 - "GestorHostServiceImpl.java"
Cohesion: 0.18
Nodes (6): HostManagerAssignment, LinkManagerProfile, HostManagerAssignmentRepository, LinkManagerProfileRepository, GestorHostServiceImpl, LinkManagers

### Community 40 - ".uniqueEmail"
Cohesion: 0.18
Nodes (3): ConsentAcceptance, ConsentAndBootstrapIntegrationTest, RegistrationServiceIntegrationTest

### Community 42 - "ApiError"
Cohesion: 0.06
Nodes (46): policy, problem(), booking, consents(), day, fillAll(), fillTraveler(), LockCall (+38 more)

### Community 43 - "booking.bookings"
Cohesion: 0.13
Nodes (13): BookingExpirationWorker (@Scheduled 60s), RF-10 Reserva y Captura de Datos Médicos, RF-11 Expiración Automática de Cupos por TTL, RF-13 Cancelación con Reembolso (>72h), RF-14 Retención Comunitaria (<=72h / No-Show), booking.booking_cancellations, booking.booking_participants (EPS cifrada), Esquema booking (+5 more)

### Community 44 - "compilerOptions"
Cohesion: 0.08
Nodes (23): compilerOptions, allowImportingTsExtensions, erasableSyntaxOnly, exactOptionalPropertyTypes, jsx, lib, module, moduleDetection (+15 more)

### Community 45 - "session/index.ts"
Cohesion: 0.07
Nodes (47): RequireSessionProps, ADR-0013, ConsentAcceptance, ConsentState, ConsentStatus, getCurrentPolicy(), getMyConsents(), grantConsents() (+39 more)

### Community 46 - "api/index.ts"
Cohesion: 0.08
Nodes (35): ADR-0003, ADR-0009, RFC-9457, 3. Matriz de Cumplimiento Rápido para Desarrolladores, Directiva 5: Consumo HTTP Estandarizado y Manejo de Errores (RFC 9457), AppProviders(), AppProvidersProps, createQueryClient() (+27 more)

### Community 47 - "TestKeys"
Cohesion: 0.16
Nodes (3): RsaSigningKeys, RsaSigningKeysTest, TestKeys

### Community 49 - "Política Determinista de Protección Comunitaria (85% irrenunciable)"
Cohesion: 0.18
Nodes (10): POST /api/v1/offline/sync, OfflineSyncService / ConflictResolutionService, RF-08 Moderación y Suspensión Preventiva, RF-18 Background Sync y Resolución Determinista de Conflictos, RF-18 Background Sync y Resolución de Conflictos, Máquina de estados de Dispersión (CANCELLED→RELEASED solo en SYNC_CONFLICT), Máquina de estados de Experiencia, R-04 Reclamaciones por cancelación con discrepancia offline (+2 more)

### Community 51 - "VoucherApiIntegrationTest.java"
Cohesion: 0.10
Nodes (3): SensitiveDataException, RoleAssignmentService, AuthTestData

### Community 53 - "devDependencies"
Cohesion: 0.09
Nodes (23): devDependencies, eslint, eslint-config-prettier, @eslint/js, eslint-plugin-jsx-a11y-x, eslint-plugin-react-hooks, eslint-plugin-react-refresh, globals (+15 more)

### Community 54 - "Sistema Intégrate (Plataforma de Turismo Comunitario)"
Cohesion: 0.08
Nodes (17): Especificación Técnica de Requerimientos Intégrate, Módulos com.integrate (auth, catalog, booking, payment, voucher, offline, audit), Esquema DDL inicial (spec §6), Intégrate - Plataforma Transaccional de Turismo Comunitario, Ley 1448 de 2011 (Víctimas), Pasarela de Pagos Wompi / ePayco, Sistema Intégrate (Plataforma de Turismo Comunitario), Stack: Java 21/Spring Boot 3, React 18 PWA, PostgreSQL 16, AWS (+9 more)

### Community 55 - "compilerOptions"
Cohesion: 0.12
Nodes (16): compilerOptions, allowImportingTsExtensions, lib, module, moduleDetection, moduleResolution, noEmit, noUnusedLocals (+8 more)

### Community 56 - "BookingStatusPage.tsx"
Cohesion: 0.08
Nodes (41): Decisión, Booking, BookingStatus, CheckoutData, PaymentCheckout, PaymentMethod, simulatePayment(), startPayment() (+33 more)

### Community 57 - "package.json"
Cohesion: 0.11
Nodes (17): description, engines, node, name, private, type, version, eslint (+9 more)

### Community 58 - "Actor: Gestor de Enlace Comunitario (Proxy Digital)"
Cohesion: 0.20
Nodes (8): Actor: Gestor de Enlace Comunitario (Proxy Digital), RF-04 Consulta de Catálogo Territorial y Aforos, RF-05 Registro Autónomo de Propuesta de Experiencia, RF-06 Registro Asistido por Gestor de Enlace, RF-07 Revisión y Autorización de Experiencia, RNF-04 Arquitectura Offline-First y Resiliencia, catalog.link_manager_profiles, T-3.5 Módulo de Gestión Asistida (Proxy Digital)

### Community 59 - "uuid"
Cohesion: 0.10
Nodes (6): LoginAttemptRepository, PasswordResetTokenRepository, ExperienceCategoryCode, ExperienceCategoryRepository, ReservedSlots, ScheduleSettlement

### Community 60 - "ADR-0004: Boilerplate de `frontend-pwa` — Vite 8 + React 19 + TypeScript 6 + Tailwind CSS 4, estructura por *features*"
Cohesion: 0.09
Nodes (20): ADR-0004: Boilerplate de `frontend-pwa` — Vite 8 + React 19 + TypeScript 6 + Tailwind CSS 4, estructura por *features*, Alternativas Descartadas, Consecuencias, Contexto, Contrato o Esquema Conceptual, Enmienda (2026-10-05, implementación), Nota de renumeración (2026-10-07), 1. Visión y Principio Rector (+12 more)

### Community 62 - "Decisión"
Cohesion: 0.14
Nodes (13): 1. Alcance, 3. Despachador con reintentos (`@Scheduled`), 4. Transporte (**punto de decisión A**), 5. Plantillas (**punto de decisión B**), 6. Adjuntos, 7. Fronteras y configuración, 8. Pruebas, ADR-0011: Notificar por correo con un transactional outbox y un despachador SMTP con reintentos (+5 more)

### Community 63 - "catalog.host_profiles"
Cohesion: 0.16
Nodes (13): BookingConcurrencyEngine, RNF-02 Latencia Transaccional de Aforo < 200 ms, RNF-02 Latencia de Aforo < 200 ms, RNF-03 Consistencia ACID y 0% Overbooking, Esquema catalog (DIVIPOLA, asociaciones, perfiles, experiencias, franjas), catalog.departments / municipalities (DIVIPOLA), catalog.experiences, catalog.host_manager_assignments (+5 more)

### Community 64 - "ConsentAndBootstrapIntegrationTest.java"
Cohesion: 0.11
Nodes (8): AdminBootstrapRunner, RegisteredUser, AdminBootstrapCommand, BootstrapOutcome, ADMIN_ALREADY_EXISTS, CREATED, IDENTITY_TAKEN, RegistrationService

### Community 65 - "Icon"
Cohesion: 0.10
Nodes (33): 4.1 Catálogo público, panel del anfitrión y moderación (ADR-0006), ResultMessage(), ExperienceDetail, ExperienceSummary, ExperienceCard(), ExperienceCardProps, ADR-0012, ExperienceFacts() (+25 more)

### Community 66 - "BookingFormPage.tsx"
Cohesion: 0.08
Nodes (35): 2. Token y almacenamiento (`V13__password_reset_tokens.sql`), 3. Entrega: enlace con el token en el fragmento, 4. Restablecimiento (una sola transacción), 5. Frontend (T-1.13b, FE-DIR-001), 6. Errores nuevos, 7. Pruebas, Decisión, lockSlots() (+27 more)

### Community 67 - "AssistedEnrollmentApiIntegrationTest"
Cohesion: 0.16
Nodes (4): SensitiveDataCipher, AssistedEnrollmentApiIntegrationTest, Gestor, 4. Cifrado de datos de salud y emergencia

### Community 68 - "AssistedEnrollmentServiceImpl.java"
Cohesion: 0.10
Nodes (12): ConsentChannel, ASSISTED_PROXY, SELF_SERVICE, AssistedAccountService, ProvisionedHost, AssistedHostRegistrationRequest, VictimData, AssistedHostRegistrationResponse (+4 more)

### Community 69 - ".prettierrc.json"
Cohesion: 0.50
Nodes (3): printWidth, singleQuote, trailingComma

### Community 70 - "org.springframework.stereotype.Component"
Cohesion: 0.07
Nodes (19): BookingSchedulingConfig, BookingExpirationService, ExpiredBatch, BookingExpirationWorker, DispatchScheduling, OutboxDispatchWorker, SweepScheduling, VoucherAsyncConfig (+11 more)

### Community 73 - "PaymentEventProcessor.java"
Cohesion: 0.06
Nodes (30): StatusReasonContext, PaymentSimulationRequest, WebhookAck, PaymentStatus, APPROVED, ERROR, INITIATED, PENDING (+22 more)

### Community 76 - "EnrollmentDocuments.java"
Cohesion: 0.11
Nodes (8): DocumentStorage, PresignedUpload, StoredObject, DocumentKind, PHYSICAL_CONSENT_FORM, VICTIM_SUPPORT_DOC, EnrollmentDocuments, InMemoryDocumentStorage

### Community 78 - "HostExperienceController.java"
Cohesion: 0.19
Nodes (5): PageRequests, AssistedRegistrationController, AssistedEnrollmentService, PaymentCheckoutController, VoucherController

### Community 79 - "AuthApiIntegrationTest.java"
Cohesion: 0.07
Nodes (3): KeyPair, JwtConfig, JwtProperties

### Community 80 - "ClientInfo"
Cohesion: 0.13
Nodes (7): AuthErrors, AuthServiceImpl, IssuedSession, SessionTransactions, AuthenticatedUser, ClientInfo, Session

### Community 81 - "Experience"
Cohesion: 0.17
Nodes (3): Experience, ExperienceContent, ExperienceSpecifications

### Community 82 - "RegisterPage.tsx"
Cohesion: 0.09
Nodes (45): Notas de implementación (2026-10-09), DocType, registerTourist(), ConsentGate(), ADR-0013, DOC_TYPES, email, FieldErrors (+37 more)

### Community 83 - "CommonArchitectureTest.java"
Cohesion: 0.18
Nodes (7): AuthArchitectureTest, CommonArchitectureTest, ModuleBoundariesTest, ADR-0008: Reservar cupos con bloqueo pesimista sobre la franja y liberarlos con un worker TTL `SKIP LOCKED`, Alternativas Descartadas, Consecuencias, Contexto

### Community 84 - "LinkManagerResponse"
Cohesion: 0.24
Nodes (8): AdminLinkManagerController, LinkManagerRequest, LinkManagerResponse, AccreditationStatus, ACTIVE, REVOKED, SUSPENDED, LinkManagerAdminService

### Community 85 - "list"
Cohesion: 0.09
Nodes (18): ConsentAction, GRANTED, REVOKED, ConsentPurpose, HEALTH_EMERGENCY, PERSONAL_DATA, SENSITIVE_VICTIM, PrivacyPolicy (+10 more)

### Community 86 - "PROTOCOLO DE FLUJO DE TRABAJO EN GIT Y GOBERNANZA DE CAMBIOS"
Cohesion: 0.17
Nodes (11): 1. Ciclo de Vida y Estados de un Cambio en Git, 2. ¿Cuándo Hacer Commit? (Reglas de Compromiso Local), 3. ¿Cuándo Hacer Push y Subir Cambios a GitHub? (Política por Fases), 4.1 Formato Estandarizado de Mensajes (Conventional Commits), 4.2 Anatomía del Diff: Qué SÍ y Qué NO debe contener un commit, 4. ¿Qué Esperar en Cada Cambio? (Estructura y Anatomía del Diff), 5. Estrategia de Ramificación (Branching Model), Ciclo Operativo por Fases: (+3 more)

### Community 88 - "catalog/queries.ts"
Cohesion: 0.11
Nodes (41): CATALOG_PAGE_SIZE, getAvailability(), getExperience(), listCategories(), listDepartments(), listMunicipalities(), searchExperiences(), withSignal() (+33 more)

### Community 89 - "org.junit.jupiter.api.Test"
Cohesion: 0.08
Nodes (6): ErrorCodeTest, GlobalExceptionHandlerTest, Participant, ProbeRequest, AuthApiIntegrationTest, ActivationCodesTest

### Community 90 - "SlotPickerPage.tsx"
Cohesion: 0.09
Nodes (55): SlotSummary(), BookingDetails(), Category, ExperienceAvailability, ExperiencePage, PageMeta, Place, ScheduleAvailability (+47 more)

### Community 91 - "PageResponse"
Cohesion: 0.14
Nodes (8): PageResponse, AdminCatalogController, AssociationRequest, HostProfileRequest, ReasonRequest, AssociationResponse, HostProfileResponse, CatalogAdminService

### Community 92 - ".provisionHost"
Cohesion: 0.11
Nodes (5): AssistedAccountServiceImpl, Emails, PasswordPolicy, AssistedHostAccount, PasswordPolicyTest

### Community 94 - ".register"
Cohesion: 0.13
Nodes (7): Actors, GestorController, DocumentUploadRequest, ActivationCodeResponse, DocumentUploadResponse, ManagedHostResponse, GestorHostService

### Community 96 - "Decisión"
Cohesion: 0.10
Nodes (20): 3.2.2 Consentimientos del titular (`/api/v1/me/consents`), 3.2 `AuthTokenResponse` (login y refresh), 10. Actor de la transacción para PostgreSQL, 11. Estructura y entidades, 12. Pruebas (criterio de aceptación de T-1.1/T-1.2), 1. Validación del access token con el *resource server* de Spring Security (sin filtro JWT propio), 2. Llave de firma de sesión por variable de entorno (SEC-DIR-004), 3. Hash de contraseñas: Argon2id directo (+12 more)

### Community 97 - "CatalogQueryServiceImpl.java"
Cohesion: 0.08
Nodes (13): PublicCatalogController, CategoryResponse, ExperienceAvailabilityResponse, ScheduleAvailability, ExperienceDetailResponse, ExperienceSummaryResponse, PlaceResponse, PhysicalIntensity (+5 more)

### Community 101 - "LoginRequest"
Cohesion: 0.23
Nodes (3): LoginRequest, AuthService, AuthServiceIntegrationTest

### Community 102 - "User"
Cohesion: 0.08
Nodes (23): CredentialStatus, ACTIVE, PENDING_RESET, RevokeReason, ADMIN, LOGOUT, PASSWORD_CHANGE, REUSE_DETECTED (+15 more)

### Community 103 - "HostExperienceServiceImpl.java"
Cohesion: 0.12
Nodes (7): ExperienceCategory, Key, Schedule, ScheduleStatus, CANCELLED, CLOSED, OPEN

### Community 104 - "BookingLockServiceImpl.java"
Cohesion: 0.08
Nodes (23): AuditEntry, Severity, CRITICAL, HIGH, INFO, WARNING, AuditTrail, ConsentService (+15 more)

### Community 105 - "BusinessRuleException"
Cohesion: 0.12
Nodes (4): BusinessRuleException, InvalidParam, BookingErrors, PaymentErrors

### Community 106 - "DocType"
Cohesion: 0.11
Nodes (7): RegisterRequest, DocType, CC, CE, PA, PPT, TI

### Community 108 - "BookingPayments"
Cohesion: 0.08
Nodes (20): BookingPayments, BookingPaymentsImpl, PayableBooking, 7. Worker de expiración (T-1.6), 10. Fuera de alcance, 2. Módulo `payment` y fronteras, 3. Inicio del pago (`POST /bookings/{id}/payment`), 5. Mapeo de estados (+12 more)

### Community 110 - "SecurityConfig.java"
Cohesion: 0.10
Nodes (5): PublicAuthEndpoints, SecurityConfig, 8. Seguridad, contrato y pruebas, 4. Webhook: verificación e idempotencia, 1. Endpoints (públicos, en `PublicAuthEndpoints`)

### Community 111 - "MockPaymentGatewayAdapter.java"
Cohesion: 0.11
Nodes (7): JpaAuditTrail, BookingRequestHasher, Canonical, CanonicalParticipant, Mock, PaymentProperties, MockPaymentGatewayAdapter

### Community 112 - "Directrices de Arquitectura Backend, Estructura Modular y Gobernanza Técnica"
Cohesion: 0.14
Nodes (14): 1.1 Patrón Arquitectónico, 1.2 Stack Tecnológico Confirmado, 1. Resumen Arquitectónico y Stack Confirmado, 2. Árbol Completo de Directorios (Formato ASCII), 3. Diccionario de Carpetas y Responsabilidades, 4.1 Ubicación Exacta de Componentes Técnicos, 4. Mapeo de Componentes Técnicos y Aislamiento de Esquemas, 5.1 Matriz de Invocación entre Capas (+6 more)

### Community 113 - "Decisión"
Cohesion: 0.12
Nodes (16): 1. Alcance de T-1.3: anfitrión autónomo + moderación del administrador, 2. Municipios DIVIPOLA por migración versionada (`V11__divipola_municipalities.sql`), 3. Alta de asociaciones y anfitriones por el administrador (RF-03, mínimo viable), 4. Experiencias: autoría del anfitrión y máquina de estados, 5. Franjas (`schedules`), 6. Consulta pública (RF-04) — sin JWT, 7. Errores, contrato y estructura, 8. Pruebas (criterio de aceptación de T-1.3) (+8 more)

### Community 114 - "duration"
Cohesion: 0.10
Nodes (3): SensitiveDataProperties, AdminBootstrapProperties, CorsProperties

### Community 115 - "PaymentMethod"
Cohesion: 0.15
Nodes (12): PaymentStartRequest, PaymentCheckoutResponse, PaymentMethod, CARD, DAVIPLATA, NEQUI, PSE, CommissionPolicyRepository (+4 more)

### Community 116 - "VoucherEmail.java"
Cohesion: 0.10
Nodes (15): FrontendProperties, PasswordResetEmails, UserSummary, NotificationOutboxImpl, Attachment, EmailRequest, NotificationOutbox, VoucherEmail (+7 more)

### Community 118 - "org.springframework.context.annotation.Bean"
Cohesion: 0.13
Nodes (4): TimeConfig, PasswordHashingConfig, MutableClock, TestClockConfig

### Community 120 - "HostExperienceResponse"
Cohesion: 0.11
Nodes (11): HostExperienceResponse, ExperienceStatus, APPROVED, DRAFT, PENDING_REVIEW, REJECTED, RETIRED, SUSPENDED (+3 more)

### Community 122 - "ScheduleRepository"
Cohesion: 0.16
Nodes (3): CategoryRepository, ScheduleRepository, CatalogQueryServiceImpl

### Community 124 - "ExperienceViews.java"
Cohesion: 0.09
Nodes (20): AssociationStatus, ACTIVE, SUSPENDED, AssociationType, ASOCIACION_VICTIMAS, COOPERATIVA_CAMPESINA, JUNTA_ACCION_COMUNAL, OTRA (+12 more)

### Community 125 - "Booking"
Cohesion: 0.11
Nodes (13): Booking, EmergencyCiphertexts, BookingStatus, CANCELLED_BY_PLATFORM, CANCELLED_NO_REFUND, CANCELLED_REFUNDED, CONFIRMED, EXECUTED (+5 more)

### Community 127 - "3. Autenticación"
Cohesion: 0.27
Nodes (9): 3.1 `LoginRequest` → `LoginRequest.java`, 3.2.1 `RegisterRequest` → `RegisterRequest.java` (`POST /api/v1/auth/register`), 3.2.2.1 Política vigente (`GET /api/v1/privacy-policies/current`, ADR-0013 §1), 3.2.3 Logout, 3.3 `AssistedHostRegistrationRequest`, 3.4 `AssistedHostRegistrationResponse` (201), 3.5 Gestor de Enlace y activación asistida (ADR-0007), 3.6 Recuperación de contraseña (ADR-0014) (+1 more)

### Community 129 - "PaymentWebhookController.java"
Cohesion: 0.25
Nodes (3): DevPaymentSimulatorController, PaymentWebhookController, PaymentWebhookService

### Community 130 - "org.springframework.web.bind.annotation.PostMapping"
Cohesion: 0.06
Nodes (14): AuthController, ClientInfos, RefreshCookies, ActivationRequest, ForgotPasswordRequest, ResetPasswordRequest, AuthTokenResponse, UserResponse (+6 more)

### Community 131 - "BookingConfirmedEvent"
Cohesion: 0.10
Nodes (17): BookingConfirmedEvent, 10. Pruebas, 11. Fuera de alcance, 2. Token: JWT RS256 con claims mínimos (RF-15, RNF-05), 3. Token re-derivable sin guardarlo, 4. Código corto, 5. Emisión: asíncrona con barrido durable, 6. QR y PDF/A (**punto de decisión C**) (+9 more)

### Community 132 - "lombok.RequiredArgsConstructor"
Cohesion: 0.12
Nodes (16): RoleCode, ADMIN, HOST, LINK_MANAGER, TOURIST, RoleRepository, UserRepository, UserRoleRepository (+8 more)

### Community 134 - "org.springframework.web.bind.annotation.GetMapping"
Cohesion: 0.11
Nodes (9): MeConsentController, PrivacyPolicyController, ConsentStatusResponse, PurposeResponse, PrivacyPolicyResponse, PublishedPolicy, PrivacyPolicyService, ProbeController (+1 more)

### Community 135 - "Decisión"
Cohesion: 0.13
Nodes (14): 1. Alcance y rutas, 2. Acreditación de gestores por el administrador (RF-03), 4. Cifrado AES-256-GCM de datos de víctima (`common/crypto`), 5. Documentos en S3: subida prefirmada (`common/storage`), 6. Activación asistida de la cuenta (`PENDING_RESET → ACTIVE`), 7. Errores nuevos (`ErrorCode`), 9. Fuera de alcance, ADR-0007: Enrolar anfitriones por intermediación del Gestor de Enlace con datos de víctima cifrados y consentimiento asistido (+6 more)

### Community 136 - "tokens.ts"
Cohesion: 0.52
Nodes (5): calculateContrastRatio(), calculateRelativeLuminance(), COLOR_PALETTE, hexToRgb(), ADR-0004

### Community 138 - "VoucherDocumentStorage"
Cohesion: 0.09
Nodes (12): PresignedDownload, VoucherDocumentStorage, Holder, VoucherSubject, ScheduleSummary, VoucherResponse, VoucherClaims, VoucherQueryServiceImpl (+4 more)

### Community 140 - "java.net.InetAddress"
Cohesion: 0.14
Nodes (9): HabeasDataConsent, LoginAttempt, LoginFailureReason, ACCOUNT_LOCKED, ACCOUNT_SUSPENDED, BAD_CREDENTIALS, UNKNOWN_USER, PasswordResetToken (+1 more)

### Community 142 - "JwtAccessTokenIssuer.java"
Cohesion: 0.27
Nodes (4): AccessTokenIssuer, IssuedToken, JwtAccessTokenIssuer, Notas de implementación (T-1.1 / T-1.2, 2026-10-05)

### Community 143 - "PaymentProvider"
Cohesion: 0.10
Nodes (11): PaymentProvider, EPAYCO, MOCK, WOMPI, IgnoredEventException, PaymentGatewayAdapter, PaymentGateways, VerifiedEvent (+3 more)

### Community 144 - "main.tsx"
Cohesion: 0.21
Nodes (11): ConfigErrorScreen(), ConfigErrorScreenProps, routes, bootstrap(), container, ADR-0013, AppConfig, ConfigError (+3 more)

### Community 146 - "eslint.config.js"
Cohesion: 0.20
Nodes (9): ADR-0004, noDeepFeatureImports, eslint-config-prettier, @eslint/js, eslint-plugin-jsx-a11y-x, eslint-plugin-react-hooks, eslint-plugin-react-refresh, globals (+1 more)

### Community 147 - "ProblemAuthenticationEntryPoint.java"
Cohesion: 0.19
Nodes (3): ProblemAccessDeniedHandler, ProblemAuthenticationEntryPoint, Notas de implementación (2026-10-09)

### Community 148 - "ADR-0016: Checkout móvil con cuenta regresiva anclada al servidor y pago por redirección a la pasarela"
Cohesion: 0.33
Nodes (5): ADR-0016: Checkout móvil con cuenta regresiva anclada al servidor y pago por redirección a la pasarela, Alternativas Descartadas, Consecuencias, Contexto, Contrato o Esquema Conceptual

### Community 149 - "org.springframework.security.oauth2.jwt.Jwt"
Cohesion: 0.21
Nodes (5): HostExperienceController, ScheduleBatchRequest, Slot, ScheduleResponse, HostExperienceService

### Community 150 - "WompiPaymentGatewayAdapter"
Cohesion: 0.20
Nodes (3): Wompi, Digests, WompiPaymentGatewayAdapter

### Community 151 - "scripts"
Cohesion: 0.20
Nodes (10): scripts, build, dev, format, format:check, lint, preview, test (+2 more)

### Community 152 - "java.security.SecureRandom"
Cohesion: 0.18
Nodes (3): ActivationCodes, RefreshTokenCodec, ShortCodes

### Community 154 - "BookingController.java"
Cohesion: 0.08
Nodes (17): BookingController, BookingLockRequest, EmergencyContact, Participant, BookingLockResponse, BookingLockService, BookingQueryService, LockOutcome (+9 more)

### Community 155 - "dependencies"
Cohesion: 0.33
Nodes (6): dependencies, react, react-dom, react-router, @tanstack/react-query, zod

### Community 157 - "Ley 1581 de 2012 (Habeas Data)"
Cohesion: 0.28
Nodes (5): Ley 1581 de 2012 (Habeas Data), RF-10 Reserva y Captura de Datos Médicos/Emergencia, RNF-01 Seguridad: AES-256-GCM y BCrypt/Argon2id, R-05 Fuga de datos de víctimas (Ley 1581), T-4.1 Auditoría Inmutable Habeas Data y Cifrado AES-256

### Community 158 - "vite.config.ts"
Cohesion: 0.33
Nodes (4): ADR-0004, @tailwindcss/vite, vite, @vitejs/plugin-react

### Community 159 - ".title"
Cohesion: 0.08
Nodes (24): 1. Endpoints, 2. Formato transversal de errores (RFC 9457), 4. Disponibilidad – `GET /experiences/{id}/availability`, 5.1 `BookingLockRequest`, 5.2 `BookingLockResponse` (201, o 200 con `Idempotency-Replayed: true`), 5.3 Mecanismo transaccional (prevención de sobreventa), 5. Bloqueo pesimista – `POST /bookings/lock`, 7.1 `OfflineSyncBatchRequest` (+16 more)

### Community 160 - "Proyecto heredado FoodExpress (a erradicar)"
Cohesion: 0.33
Nodes (6): Proyecto heredado FoodExpress (a erradicar), Fase 0: Setup e Infraestructura / DB, Fase 1: MVP Core Transaccional, Fase 2: PWA Offline-First y Validación Territorial, Fase 3: Sincronización, Enrolamiento Asistido y Finanzas, Fase 4: Auditoría, Hardening y Cloud AWS

### Community 161 - "auth.password_reset_tokens"
Cohesion: 0.60
Nodes (3): auth.password_reset_tokens, ix_password_reset_tokens_user_requested, uq_password_reset_tokens_user_live

### Community 162 - "PaymentGatewayAdapter (Mock / Wompi)"
Cohesion: 0.40
Nodes (5): Pasarela de Pagos / Split Payment (Wompi / ePayco), PaymentGatewayAdapter (Mock / Wompi), R-06 Caída del proveedor de pagos (circuit breaker Resilience4j), T-1.5 Adaptador de Pagos y Split Payment, T-1.6 Generador de Voucher Criptográfico PDF/QR

## Ambiguous Edges - Review These
- `Esquema DDL inicial (spec §6)` → `booking.bookings`  [AMBIGUOUS]
  docs/modelo_relacional.md · relation: semantically_similar_to
- `Migraciones Flyway V1–V9` → `T-0.4 Migración inicial Flyway (V1__initial_schema.sql)`  [AMBIGUOUS]
  docs/roadmap_ejecucion.md · relation: conceptually_related_to

## Knowledge Gaps
- **584 isolated node(s):** `com.integrate:backend-api`, `INFO`, `WARNING`, `HIGH`, `CRITICAL` (+579 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 944 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **46 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What is the exact relationship between `Esquema DDL inicial (spec §6)` and `booking.bookings`?**
  _Edge tagged AMBIGUOUS (relation: semantically_similar_to) - confidence is low._
- **What is the exact relationship between `Migraciones Flyway V1–V9` and `T-0.4 Migración inicial Flyway (V1__initial_schema.sql)`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
- **Why does `4. Bitácora de Avances` connect `4. Bitácora de Avances` to `org.springframework.web.bind.annotation.PostMapping`, `BookingConfirmedEvent`, `lombok.RequiredArgsConstructor`, `org.springframework.web.bind.annotation.GetMapping`, `GlobalExceptionHandler.java`, `VoucherDocumentStorage`, `java.net.InetAddress`, `JwtAccessTokenIssuer.java`, `PaymentProvider`, `OutboxDispatcher.java`, `Estado del Proyecto Intégrate (PROJECT_STATUS.md)`, `TraceIdFilter`, `ApiError`, `api/index.ts`, `VoucherApiIntegrationTest.java`, `ConsentAndBootstrapIntegrationTest.java`, `Icon`, `BookingFormPage.tsx`, `AssistedEnrollmentServiceImpl.java`, `PaymentEventProcessor.java`, `RegisterPage.tsx`, `org.junit.jupiter.api.Test`, `PageResponse`, `LoginRequest`, `BookingLockServiceImpl.java`, `BookingPayments`, `SecurityConfig.java`, `VoucherEmail.java`, `org.springframework.context.annotation.Bean`?**
  _High betweenness centrality (0.243) - this node is a cross-community bridge._
- **Why does `Especificación Técnica de Requerimientos Intégrate` connect `Sistema Intégrate (Plataforma de Turismo Comunitario)` to `2.3 Fichas Técnicas de los Object Stores`?**
  _High betweenness centrality (0.083) - this node is a cross-community bridge._
- **Why does `Intégrate - Plataforma Transaccional de Turismo Comunitario` connect `Sistema Intégrate (Plataforma de Turismo Comunitario)` to `RF-17 Validación Offline de Voucher`?**
  _High betweenness centrality (0.069) - this node is a cross-community bridge._
- **What connects `com.integrate:backend-api`, `INFO`, `WARNING` to the rest of the system?**
  _584 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Esquema Catálogo (V3)` be split into smaller, more focused modules?**
  _Cohesion score 0.09393939393939393 - nodes in this community are weakly interconnected._