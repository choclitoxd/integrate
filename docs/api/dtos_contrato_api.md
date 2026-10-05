# Contrato de API y DTOs – Núcleo Transaccional Intégrate

**Versión:** 1.0.0 · **Especificación:** [openapi.yaml](openapi.yaml) (OpenAPI 3.1, validada con Redocly)
**Base:** `docs/especificacion_tecnica.md`, `docs/modelo_relacional.md`, `docs/architecture/*`, más las decisiones de clarificación A1–G8 (octubre 2026).

Los DTOs se implementan como `record` de Java 21 en `modules/{m}/dto/request` y `modules/{m}/dto/response`, validados con Jakarta Bean Validation (`@Valid`). Las reglas que cruzan varios campos se implementan con validadores de clase (`@Constraint` a nivel de tipo).

---

## 1. Endpoints

| Método | Ruta | Rol | Éxito | Errores |
|---|---|---|---|---|
| POST | `/api/v1/auth/login` | público | 200 | 400, 401, 403, 422, 423 |
| POST | `/api/v1/auth/refresh` | cookie `refresh_token` | 200 | 401, 403 |
| POST | `/api/v1/auth/register-assisted` | `LINK_MANAGER`, `ADMIN` | 201 | 400, 401, 403, 409, 422 |
| GET | `/api/v1/experiences/{id}/availability` | público (JWT opcional) | 200 | 400, 404, 409, 422 |
| POST | `/api/v1/bookings/lock` | `TOURIST` | 201 (200 si es replay) | 400, 401, 403, 404, 409, 422, 428, 503 |
| POST | `/api/v1/payments/webhook/mock` | firma HMAC | 200 | 400, 401, 422 |
| POST | `/api/v1/payments/webhook/wompi` | firma HMAC | 200 | 400, 401, 422 |
| POST | `/api/v1/offline/sync` | `LINK_MANAGER`, `HOST` | 200 | 400, 401, 403, 409, 422 |

---

## 2. Formato transversal de errores (RFC 9457)

`Content-Type: application/problem+json`. Lo construye `GlobalExceptionHandler` (`common/exception`).

**`ProblemDetails`**

| Campo | Tipo | Req. | Descripción |
|---|---|---|---|
| `type` | URI | ✔ | `about:blank` (protocolo) o `https://api.integrate.co/problems/<slug>` (dominio) |
| `title` | string | ✔ | Resumen estable por `type` |
| `status` | int | ✔ | Código HTTP |
| `detail` | string | | Explicación de esta ocurrencia concreta |
| `instance` | URI-ref | | Ruta invocada |
| `code` | string `^[A-Z][A-Z0-9_]+$` | | Código de negocio estable |
| `traceId` | string | ✔ | Igual a la cabecera `X-Trace-Id` |
| `invalidParams[]` | `{name, reason}` | | Errores por campo (`name` con notación `participants[1].docNumber`) |

Extensiones según el caso: `lockedUntil` (423), `availableSlots` (409 por cupos), `requiredPurposes` y `policyVersion` (428).

**Política de códigos**

| Status | Uso | `type` |
|---|---|---|
| 400 | JSON mal formado o tipo incompatible (texto en un campo numérico) | `about:blank` |
| 401 | Credenciales o token inválidos, ausentes o expirados; firma de webhook inválida | dominio |
| 403 | RBAC, cuenta suspendida o dispositivo no autorizado | dominio |
| 404 | Recurso inexistente o no visible para el invocador | dominio |
| 409 | Conflicto de negocio: cupos, franja no reservable, clave idempotente reutilizada, usuario duplicado, lote alterado | dominio |
| 422 | Validación semántica campo por campo; evento de webhook inaplicable | dominio |
| 423 | **Solo** cuenta bloqueada por intentos fallidos (+ `Retry-After`) | dominio |
| 428 | Falta el consentimiento `HEALTH_EMERGENCY` antes del lock | dominio |
| 503 | `lock_timeout` de PostgreSQL en `FOR UPDATE` (+ `Retry-After: 1`) | dominio |

**Catálogo de códigos**

| `code` | Status | Endpoint(s) |
|---|---|---|
| `MALFORMED_REQUEST` | 400 | todos |
| `VALIDATION_FAILED` | 422 | todos |
| `AUTH_INVALID_CREDENTIALS` | 401 | login |
| `AUTH_TOKEN_INVALID` | 401 | protegidos |
| `AUTH_REFRESH_TOKEN_INVALID` | 401 | refresh |
| `AUTH_ACCOUNT_SUSPENDED` | 403 | login, refresh |
| `AUTH_ACCOUNT_LOCKED` | 423 | login |
| `AUTH_USER_ALREADY_EXISTS` | 409 | register-assisted |
| `RBAC_FORBIDDEN` | 403 | protegidos |
| `RESOURCE_NOT_FOUND` | 404 | availability, lock |
| `CATALOG_EXPERIENCE_NOT_APPROVED` | 409 | availability (panel ADMIN/LINK_MANAGER) |
| `BOOKING_INSUFFICIENT_SLOTS` | 409 | lock |
| `BOOKING_SCHEDULE_NOT_BOOKABLE` | 409 | lock (franja no `OPEN` o ya iniciada) |
| `IDEMPOTENCY_KEY_REUSED` | 409 | lock |
| `CONSENT_HEALTH_EMERGENCY_REQUIRED` | 428 | lock |
| `CONCURRENCY_LOCK_TIMEOUT` | 503 | lock |
| `WEBHOOK_SIGNATURE_INVALID` | 401 | webhooks |
| `PAYMENT_BOOKING_NOT_FOUND` | 422 | webhooks |
| `PAYMENT_PROVIDER_MISMATCH` | 422 | webhooks (`provider` del body ≠ path) |
| `OFFLINE_DEVICE_NOT_AUTHORIZED` | 403 | sync |
| `SYNC_BATCH_PAYLOAD_MISMATCH` | 409 | sync |

---

## 3. Autenticación

### 3.1 `LoginRequest` → `LoginRequest.java`

| Campo | Tipo | Req. | Validación |
|---|---|---|---|
| `email` | string(email) | ✔ | `@NotBlank @Email @Size(max=254)`; se normaliza con `lower()` |
| `password` | string | ✔ | `@NotBlank @Size(max=128)`, sin `\u0000` (→ 400 si viene un payload malicioso); nunca va a logs |

### 3.2 `AuthTokenResponse` (login y refresh)

| Campo | Tipo | Notas |
|---|---|---|
| `accessToken` | string | JWT **RS256**, `kid` en el header; claims `sub`, `roles[]`, `iat`, `exp`, `jti` |
| `tokenType` | `"Bearer"` | |
| `expiresIn` | int `900` | segundos |
| `user.id` | UUID | |
| `user.email` | string | |
| `user.fullName` | string | |
| `user.roles` | `RoleClaim[]` | todos los roles vigentes (`user_roles.revoked_at IS NULL`) |
| `requiresConsentUpdate` | boolean | `true` si no hay consentimiento para la política `is_current` |
| `pendingPolicyVersion` | string \| null | |
| `pendingPurposes` | `ConsentPurpose[]` | vacío si no aplica |

**Cookie de refresh:** `refresh_token=<opaco>; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth; Max-Age=604800`. En `auth.refresh_tokens` solo se persiste `token_sha256`. Cada uso revoca el token anterior y escribe su `replaced_by_id`.

**Bloqueo:** 5 fallos consecutivos en 10 minutos (contados en `auth.login_attempts`) fijan `users.locked_until = now() + 15 min` → 423 con `lockedUntil` y `Retry-After: 900`.

### 3.3 `AssistedHostRegistrationRequest`

| Campo | Tipo | Req. | Validación / Notas |
|---|---|---|---|
| `fullName` | string | ✔ | 3–150 |
| `docType` | `CC\|CE\|PA\|PPT\|TI` | ✔ | |
| `docNumber` | string | ✔ | `^[A-Za-z0-9]{4,20}$`; `(docType, docNumber)` único → 409 |
| `phone` | string | ✔ | E.164 `^\+[1-9][0-9]{7,14}$` |
| `email` | string \| null | | si se omite, el servidor genera `cc{docNumber}@comunidad.integrate.co` |
| `linkManagerId` | UUID \| null | condicional | **obligatorio si el invocador es `ADMIN`**; si es `LINK_MANAGER` se usa el gestor autenticado |
| `victimData.victimCondition` | boolean | ✔ | cifrado → `victim_condition_enc` |
| `victimData.ruvRegistrationNumber` | string \| null | | ≤ 40, cifrado |
| `victimData.victimizationType` | string \| null | | ≤ 80, cifrado |
| `victimData.supportDocS3Key` | string \| null | | ≤ 512 |
| `consent.policyVersion` | string | ✔ | debe ser la versión `is_current` → si no, 422 |
| `consent.purposes` | string[] | ✔ | exactamente `{PERSONAL_DATA, SENSITIVE_VICTIM}` |
| `consent.physicalConsentFormS3Key` | string | ✔ | PDF firmado, subido antes a S3 |

Reglas de servidor: el anfitrión y el gestor deben pertenecer a la misma `association_id` (RF-03, FK compuesta D3). El anfitrión no debe tener un gestor vigente; si lo tiene, la restricción EXCLUDE responde 409. La contraseña inicial es un secreto aleatorio que nunca se devuelve.

### 3.4 `AssistedHostRegistrationResponse` (201)

| Campo | Tipo |
|---|---|
| `userId`, `hostProfileId`, `associationId`, `linkManagerId` | UUID |
| `assignmentId` | int64 |
| `email` | string |
| `emailIsSynthetic` | boolean |
| `roles` | `["ROLE_HOST"]` |
| `credentialStatus` | `"PENDING_RESET"` |
| `consent` | `{channel:"ASSISTED_PROXY", policyVersion, purposes[], capturedBy, capturedAt}` |

Los datos de víctima **nunca** se devuelven en la respuesta.

---

## 4. Disponibilidad – `GET /experiences/{id}/availability`

**Parámetros**

| Nombre | En | Tipo | Req. | Validación |
|---|---|---|---|---|
| `id` | path | UUID | ✔ | |
| `dateFrom` | query | `LocalDate` | ✔ | ISO `YYYY-MM-DD`, America/Bogota |
| `dateTo` | query | `LocalDate` | ✔ | `≥ dateFrom` y `dateTo − dateFrom ≤ 60` días → si no, 422 |
| `minSlots` | query | int | | 1–30 |

**`ExperienceAvailabilityResponse`**

| Campo | Tipo | Notas |
|---|---|---|
| `experienceId` | UUID | |
| `title` | string | |
| `timezone` | `"America/Bogota"` | |
| `currency` | `"COP"` | |
| `unitPriceCop` | int64 | tarifa única por persona |
| `maxCapacityPerSlot` | int 1–30 | |
| `dateFrom`, `dateTo` | date | eco de la consulta |
| `schedules[]` | `ScheduleAvailability` | solo franjas `OPEN` con `start_time > now()`, ordenadas por `startTime` |

**`ScheduleAvailability`:** `scheduleId` (UUID), `localDate` (date), `startTime`/`endTime` (UTC `Z`), `localStartTime`/`localEndTime` (`YYYY-MM-DDTHH:mm:ss` local, solo descriptivo), `totalSlots`, `availableSlots` y `status` (`"OPEN"`).

Visibilidad: una experiencia no `APPROVED` devuelve 404 al público o a un turista, y 409 si el JWT es `ADMIN` o `LINK_MANAGER`. `availableSlots` es una lectura sin lock: es informativa.

---

## 5. Bloqueo pesimista – `POST /bookings/lock`

**Cabecera obligatoria:** `Idempotency-Key: <UUIDv4>`.

### 5.1 `BookingLockRequest`

| Campo | Tipo | Req. | Validación |
|---|---|---|---|
| `scheduleId` | UUID | ✔ | |
| `requestedSlots` | int | ✔ | `1 ≤ n ≤ 10` (≤ 0 → **422**; un tipo no numérico → 400) |
| `emergencyContact.fullName` | string | ✔ | 3–150, se cifra |
| `emergencyContact.phone` | string | ✔ | E.164, se cifra |
| `participants[]` | `BookingParticipant[]` | ✔ | `size == requestedSlots`; `(docType, docNumber)` único; exactamente un `isHolder = true` |
| `participants[].fullName` | string | ✔ | 3–150 |
| `participants[].docType` | enum | ✔ | `CC\|CE\|PA\|PPT\|TI` |
| `participants[].docNumber` | string | ✔ | `^[A-Za-z0-9]{4,20}$` |
| `participants[].medicalEps` | string | ✔ | 2–120 → `eps_enc` (AES-256-GCM) |
| `participants[].isHolder` | boolean | ✔ | |

### 5.2 `BookingLockResponse` (201, o 200 con `Idempotency-Replayed: true`)

| Campo | Tipo | Notas |
|---|---|---|
| `bookingId` | UUID | |
| `status` | `"PENDING"` | |
| `scheduleId`, `experienceId` | UUID | |
| `lockedSlots` | int | |
| `unitPriceCop`, `totalAmountCop` | int64 | `total = unit × slots` (D4) |
| `currency` | `"COP"` | |
| `createdAt`, `expiresAt` | date-time | `expiresAt = createdAt + 15 min` |
| `ttlSecondsRemaining` | int 0–900 | se recalcula en cada replay |

### 5.3 Mecanismo transaccional (prevención de sobreventa)

1. Control de idempotencia: se busca `Idempotency-Key` + `touristId`.
   - Si existe con el mismo hash de payload → replay 200.
   - Si existe con un hash distinto → 409 `IDEMPOTENCY_KEY_REUSED`.
2. Precondición de consentimiento `HEALTH_EMERGENCY` → si falta, 428.
3. Transacción `READ COMMITTED` con `SET LOCAL lock_timeout = '2s'` y `SET LOCAL integrate.actor_id`:
   - `SELECT … FROM catalog.schedules WHERE id = ? FOR UPDATE`. Si vence el timeout → 503 `CONCURRENCY_LOCK_TIMEOUT` con `Retry-After: 1`.
   - Si la experiencia no está `APPROVED` → 404. Si la franja no está `OPEN` o ya empezó → 409 `BOOKING_SCHEDULE_NOT_BOOKABLE`.
   - Si `requestedSlots > available_slots` → 409 `BOOKING_INSUFFICIENT_SLOTS` con `availableSlots`.
   - `UPDATE available_slots = available_slots − n`, luego `INSERT bookings` (`PENDING`, `expires_at = now() + 15 min`), luego `INSERT booking_participants`, luego `INSERT audit.audit_trail` como última sentencia, y COMMIT (< 200 ms).
4. El worker TTL (cada 60 s, `FOR UPDATE SKIP LOCKED`) pasa la reserva a `EXPIRED` y restituye los cupos.

---

## 6. Webhook de pasarela – `POST /payments/webhook/{mock|wompi}`

**Cabeceras:** `X-Signature` (hex de 64 caracteres) y `X-Timestamp` (epoch en segundos).
**Firma:** `HMAC_SHA256(secret_proveedor, X-Timestamp + "." + raw_body)`, comparada en tiempo constante (`MessageDigest.isEqual`). Ventana de ±300 s. Si falla → 401.

### 6.1 `CanonicalPaymentEvent` (Mock, y salida del adaptador Wompi)

| Campo | Tipo | Req. | Notas |
|---|---|---|---|
| `eventId` | string ≤ 100 | ✔ | idempotencia `(provider, event_id)` |
| `provider` | `MOCK_GATEWAY\|WOMPI` | ✔ | debe coincidir con el path (si no, 422 `PAYMENT_PROVIDER_MISMATCH`); en BD se guarda como `MOCK` o `WOMPI` |
| `transactionId` | string ≤ 100 | ✔ | `gateway_transaction_id` |
| `bookingId` | UUID | ✔ | si no existe → 422 `PAYMENT_BOOKING_NOT_FOUND` |
| `amountCop` | int64 | ✔ | |
| `status` | `APPROVED\|DECLINED\|VOIDED\|ERROR` | ✔ | |
| `timestamp` | date-time | ✔ | |

**Adaptación desde Wompi (`WompiEvent`):**

| Campo canónico | Origen en el evento Wompi |
|---|---|
| `transactionId` | `data.transaction.id` |
| `bookingId` | `data.transaction.reference` |
| `amountCop` | `amount_in_cents / 100` |
| `status` | `data.transaction.status` |
| `eventId` | ver §8.6 |

### 6.2 `WebhookAck` (200)

`received` (true), `duplicate` (boolean), `provider`, `eventId`, `paymentStatus` y `bookingStatus`. Un evento duplicado responde 200 con `duplicate = true` y sin efectos secundarios.

### 6.3 Modelo de estados

| Pasarela | Pago | Reserva | Efectos |
|---|---|---|---|
| `APPROVED` | `APPROVED` | `CONFIRMED` | `disbursements` (CEIL 85%, `release_at` = fin de la franja + 24 h), split 85/15 y outbox del voucher |
| `DECLINED` | `REJECTED` | `PAYMENT_FAILED` | libera los cupos en la misma transacción |
| `VOIDED` | `VOIDED` | `CANCELLED_REFUNDED` | libera los cupos si la reserva estaba `PENDING` (transición habilitada en V10) |
| `ERROR` | `ERROR` | `PENDING` (sin cambio) | el TTL de 15 minutos sigue corriendo |

Todo ocurre en una transacción: `INSERT … ON CONFLICT (provider, event_id) DO NOTHING`, luego las transiciones, luego la auditoría.

---

## 7. Sincronización offline – `POST /offline/sync`

### 7.1 `OfflineSyncBatchRequest`

| Campo | Tipo | Req. | Validación |
|---|---|---|---|
| `syncBatchId` | UUID | ✔ | PK de `offline.sync_batches` (idempotencia) |
| `deviceId` | string `^[A-Za-z0-9._-]{4,64}$` | ✔ | `device_identifier`; debe estar `ACTIVE` y pertenecer al usuario autenticado (`manager_id` o `host_id`) → si no, 403 |
| `deviceSentAt` | date-time | ✔ | reloj del dispositivo |
| `validations[]` | 1–100 | ✔ | `localValidationId` único en el lote |
| `validations[].localValidationId` | UUID | ✔ | generado en la PWA |
| `validations[].voucherId` | UUID | ✔ | `jti` |
| `validations[].scannedAt` | date-time | ✔ | |
| `validations[].signatureHash` | hex de 64 caracteres | si `channel = OFFLINE_PWA` | → `signature_sha256`; se omite en `MANUAL_CODE` |
| `validations[].shortCode` | `^[A-HJ-NP-Z2-9]{8}$` | ✔ | igual a `voucher.vouchers.short_code` |
| `validations[].channel` | `OFFLINE_PWA\|MANUAL_CODE` | ✔ | |

### 7.2 `OfflineSyncBatchResponse` (siempre 200 si el lote es válido)

| Campo | Tipo | Notas |
|---|---|---|
| `syncBatchId`, `deviceId` | | |
| `replayed` | boolean | `true` si se reproduce un lote ya procesado |
| `deviceSentAt`, `serverReceivedAt`, `processedAt` | date-time | |
| `clockSkewSeconds` | int | `serverReceivedAt − deviceSentAt` (informativo para la PWA) |
| `summary` | `{received, confirmed, conflicts, rejected}` | |
| `items[]` | `OfflineSyncItemResult` | uno por validación, en el mismo orden |

**`OfflineSyncItemResult`:** `localValidationId`, `voucherId`, `status` (`CONFIRMED\|CONFLICT\|REJECTED`), `serverOutcome`, `bookingStatus`, `incidentType`, `incidentId` y `message`.

### 7.3 Reglas de reconciliación (se evalúan en este orden)

| # | Condición | `serverOutcome` | `status` | Incidente | Efecto en reserva y dispersión |
|---|---|---|---|---|---|
| 1 | `scannedAt > now() + 5 min` | `REJECTED_CLOCK_SKEW` | CONFLICT | `CLOCK_SKEW_FUTURE_SCAN` | ninguno |
| 2 | Voucher inexistente | `REJECTED_UNKNOWN_VOUCHER` | REJECTED | — | ninguno |
| 3 | Fecha local (Bogotá) de `scannedAt` ≠ `valid_date` | `REJECTED_OUT_OF_DATE` | REJECTED | — | ninguno |
| 4 | Voucher `REVOKED` o reserva cancelada | `REJECTED_REVOKED` | CONFLICT | `OVER_REDEMPTION_COMMUNITY_PROTECTED` (HIGH, `financial_impact_cop`) | reserva → `SYNC_CONFLICT`; dispersión `CANCELLED → RELEASED` (85% intocable) |
| 5 | Ya existe una validación `ACCEPTED` del voucher, o hay otra en el mismo lote con un `scannedAt` anterior | `REJECTED_DUPLICATE` | CONFLICT | `DUPLICATE_OFFLINE_REDEMPTION` | ninguno |
| 6 | En otro caso | `ACCEPTED` | CONFIRMED | — | reserva → `EXECUTED` (o `NO_SHOW → EXECUTED`); voucher → `CONSUMED` |

**Idempotencia:** se guarda el hash SHA-256 del payload canónico en `sync_batches`.

- Mismo `syncBatchId` y mismo hash → 200 con el resultado almacenado y `replayed = true`.
- Mismo `syncBatchId` y hash distinto → 409 `SYNC_BATCH_PAYLOAD_MISMATCH`.

Las validaciones de vouchers existentes quedan en `offline.offline_validations` (append-only), sea cual sea su resultado, con `local_validation_id` y `local_result = 'ACCEPTED'`: por lote solo llegan los canjes aceptados en campo (RF-17). Un voucher inexistente se registra solo en `audit.audit_trail`, porque la FK impide guardarlo en esa tabla.

---

## 8. Decisiones de cierre y su propagación

> **Estado:** aprobadas por el equipo el 2026-10-05 y propagadas a `especificacion_tecnica.md`, `modelo_relacional.md`, `architecture/offline_data_indexeddb.md` y a la migración `V10__api_contract_alignment.sql`.

| # | Decisión | Dónde quedó |
|---|---|---|
| 1 | Access token RS256 de **900 s** con `roles[]` (reemplaza los 60 min de RF-01) | RF-01 y §5.1 de la especificación |
| 2 | El consentimiento vive en `auth.habeas_data_consents` (no existe `audit.consent_audit_logs`). Finalidades `PERSONAL_DATA \| SENSITIVE_VICTIM \| HEALTH_EMERGENCY`; columnas `channel`, `captured_by` y `physical_consent_form_s3_key` | V10 §3 |
| 3 | Transición `PENDING → CANCELLED_REFUNDED` (pago `VOIDED`) con devolución de cupos | V10 §4, `modelo_relacional.md` §4 |
| 4 | Webhooks en `/payments/webhook/mock` y `/payments/webhook/wompi`; no hay ruta genérica | Especificación §5.3 |
| 5 | Un `PENDING` de Wompi se acepta con 200 y sin cambios de estado | Contrato |
| 6 | El `eventId` de Wompi se deriva del SHA-256 de `signature.checksum` | Contrato y comentario en V10 §5 |
| 7 | Lock: `isHolder`; replay en 200 con `Idempotency-Replayed`; clave reutilizada → 409; replay válido durante 24 h. La clave y el hash se persisten en `booking.bookings` (`idempotency_key`, `request_sha256`) | V10 §4 |
| 8 | Register-assisted: email sintético generado por el servidor; `linkManagerId` obligatorio si invoca un ADMIN; `users.credential_status = PENDING_RESET` | V10 §2 |
| 9 | Sync: `REJECTED_UNKNOWN_VOUCHER`, `status = REJECTED` y `clockSkewSeconds`. Un `ACCEPTED` no se revierte. `sync_batches` guarda `payload_sha256`, `result_payload` y `device_sent_at`; las validaciones guardan `local_validation_id` | V10 §7–§9 |
| 10 | Login 403 `AUTH_ACCOUNT_SUSPENDED` para `SUSPENDED` y `ANONYMIZED` | Contrato |
| 11 | El código de rol en BD pasa de `COMMUNITY_HOST` a `HOST` (claim `ROLE_HOST`) | V10 §1 |
| 12 | Un dispositivo puede pertenecer a un anfitrión (`offline.devices.host_id`), y el `device_identifier` admite hasta 64 caracteres | V10 §6 |
| 13 | `shortCode` = exactamente 8 caracteres `^[A-HJ-NP-Z2-9]{8}$` (regla de `voucher.vouchers`); `signatureHash` solo es obligatorio en `OFFLINE_PWA`; `MANUAL_CODE` puede llegar en un lote | Contrato, V10 §8, IndexedDB §2.3-B |
| 14 | La versión de la política sigue el formato `vN.N` (ej. `v2.0`) por el `CHECK` de `privacy_policies` | Contrato |

**Pendientes conocidos**
- La firma nativa de Wompi es un checksum en el body (cabecera `X-Event-Checksum`). Si Wompi llama directo, sin un proxy que re-firme con `X-Signature` y `X-Timestamp`, el adaptador debe validar ese checksum.
- Las decisiones citan RNF-13, RNF-18, RNF-23 y RNF-29, que no existen en la especificación (solo hay RNF-01 a RNF-09).
- V10 se detiene a propósito si las tablas append-only ya contienen finalidades, `server_outcome` o `incident_type` antiguos.
