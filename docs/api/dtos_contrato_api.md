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
| POST | `/api/v1/auth/register` | público (solo crea `TOURIST`) | 201 | 400, 409, 422 |
| POST | `/api/v1/auth/logout` | cookie `refresh_token` (opcional) | 204 | — |
| GET | `/api/v1/me/consents` | autenticado (cualquier rol) | 200 | 401 |
| POST | `/api/v1/me/consents` | autenticado (cualquier rol) | 200 | 400, 401, 422 |
| POST | `/api/v1/auth/register-assisted` | `LINK_MANAGER`, `ADMIN` | 201 | 400, 401, 403, 404, 409, 422 |
| POST | `/api/v1/auth/activate` | público | 204 | 400, 401, 422 |
| POST | `/api/v1/gestor/uploads` | `LINK_MANAGER`, `ADMIN` | 201 | 400, 401, 403, 422 |
| GET | `/api/v1/gestor/hosts` | `LINK_MANAGER` | 200 (paginado) | 401, 403 |
| POST | `/api/v1/gestor/hosts/{hostProfileId}/activation-code` | `LINK_MANAGER` | 201 | 401, 403, 404, 409 |
| POST | `/api/v1/admin/link-managers` | `ADMIN` | 201 | 400, 401, 403, 404, 409, 422 |
| POST | `/api/v1/admin/link-managers/{id}/{suspend\|reinstate\|revoke}` | `ADMIN` | 200 | 401, 403, 404, 409 |
| GET | `/api/v1/experiences/{id}/availability` | público (JWT opcional) | 200 | 400, 404, 409, 422 |
| POST | `/api/v1/bookings/lock` | `TOURIST` | 201 (200 si es replay) | 400, 401, 403, 404, 409, 422, 428, 503 |
| GET | `/api/v1/bookings/{bookingId}` | `TOURIST` (dueño) | 200 | 400, 401, 403, 404 |
| POST | `/api/v1/bookings/{bookingId}/payment` | `TOURIST` (dueño) | 201 (200 si retoma el intento abierto) | 400, 401, 403, 404, 409, 422 |
| POST | `/api/v1/dev/payments/{paymentId}/simulate` | `TOURIST` (dueño), **solo `dev`/`test`** | 200 | 401, 403, 404, 422 |
| POST | `/api/v1/payments/webhook/mock` | firma HMAC | 200 | 400, 401, 422 |
| POST | `/api/v1/payments/webhook/wompi` | checksum nativo de Wompi | 200 | 400, 401, 404, 422 |
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
| `AUTH_USER_ALREADY_EXISTS` | 409 | register, register-assisted |
| `AUTH_ACTIVATION_INVALID` | 401 | activate (correo, código erróneo, vencido, consumido o agotado: misma respuesta) |
| `ENROLLMENT_MANAGER_NOT_ACTIVE` | 403 | register-assisted, gestor/* (gestor sin acreditación `ACTIVE`) |
| `ENROLLMENT_MANAGER_EXISTS` | 409 | admin/link-managers |
| `ENROLLMENT_DOCUMENT_INVALID` | 422 | register-assisted (PDF inexistente, de otro tipo, > 5 MB o ya usado) |
| `RBAC_FORBIDDEN` | 403 | protegidos |
| `RESOURCE_NOT_FOUND` | 404 | availability, lock |
| `CATALOG_EXPERIENCE_NOT_APPROVED` | 409 | availability (panel ADMIN/LINK_MANAGER) |
| `CATALOG_INVALID_STATUS_TRANSITION` | 409 | host (editar/enviar), admin (moderación, acreditación de gestores), register-assisted (gestor indicado no activo), activation-code (cuenta ya activa) |
| `CATALOG_SCHEDULE_CONFLICT` | 409 | franjas (duplicado, cancelación con reservas) |
| `CATALOG_HOST_PROFILE_EXISTS` | 409 | admin/host-profiles |
| `CATALOG_ASSOCIATION_EXISTS` | 409 | admin/associations |
| `BOOKING_INSUFFICIENT_SLOTS` | 409 | lock |
| `BOOKING_SCHEDULE_NOT_BOOKABLE` | 409 | lock (franja no `OPEN` o ya iniciada) |
| `IDEMPOTENCY_KEY_REUSED` | 409 | lock |
| `CONSENT_HEALTH_EMERGENCY_REQUIRED` | 428 | lock |
| `CONCURRENCY_LOCK_TIMEOUT` | 503 | lock |
| `WEBHOOK_SIGNATURE_INVALID` | 401 | webhooks |
| `PAYMENT_BOOKING_NOT_FOUND` | 422 | webhooks |
| `PAYMENT_PROVIDER_MISMATCH` | 422 | webhooks (`provider` del body ≠ path) |
| `PAYMENT_AMOUNT_MISMATCH` | 422 | webhooks (monto del evento ≠ monto del intento; `expectedAmountCop`, `receivedAmountCop`) |
| `PAYMENT_BOOKING_NOT_PAYABLE` | 409 | bookings/{id}/payment (reserva no `PENDING` o TTL vencido; `bookingStatus`) |
| `OFFLINE_DEVICE_NOT_AUTHORIZED` | 403 | sync |
| `SYNC_BATCH_PAYLOAD_MISMATCH` | 409 | sync |

---

## 3. Autenticación

### 3.1 `LoginRequest` → `LoginRequest.java`

| Campo | Tipo | Req. | Validación |
|---|---|---|---|
| `email` | string(email) | ✔ | `@NotBlank @Email @Size(max=254)`; se normaliza con `lower()` |
| `password` | string | ✔ | `@NotEmpty @Size(max=128)`, sin `\u0000` (→ 422 `VALIDATION_FAILED`, sin intentar autenticar); nunca va a logs |

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

**Bloqueo:** 5 fallos consecutivos en 10 minutos (contados en `auth.login_attempts`) fijan `users.locked_until = now() + 15 min` → 423 con `lockedUntil` y `Retry-After: 900`. Durante el bloqueo no se verifica la contraseña. Correo inexistente, contraseña errónea y `credential_status = PENDING_RESET` responden el mismo 401; `SUSPENDED` responde 403 solo con la contraseña correcta.

**Rotación (ADR-0005 §5):** presentar un token ya rotado dentro de los 10 s siguientes responde 401 sin más efectos (pestañas concurrentes, reintentos de red); pasada esa ventana se trata como robo y se revocan todas las sesiones del titular (`REUSE_DETECTED`). Todo 401 de `/refresh` borra la cookie. En los endpoints públicos de `/api/v1/auth/**` se ignora la cabecera `Authorization`: un access token expirado no impide renovar la sesión.

### 3.2.1 `RegisterRequest` → `RegisterRequest.java` (`POST /api/v1/auth/register`)

| Campo | Tipo | Req. | Validación / Notas |
|---|---|---|---|
| `email` | string | ✔ | `@Email`, ≤ 254 y `^[^@\s]+@[^@\s]+\.[^@\s]+$` (dominio `common.email`); único sin distinguir mayúsculas → 409 |
| `password` | string | ✔ | 12–128 caracteres (code points), sin `\u0000`, sin reglas de composición (NIST SP 800-63B); Argon2id |
| `fullName` | string | ✔ | 3–150, al menos 3 caracteres sin espacios externos |
| `docType` | `CC\|CE\|PA\|PPT\|TI` | ✔ | |
| `docNumber` | string | ✔ | `^[A-Za-z0-9]{4,20}$`; `(docType, docNumber)` único → 409 |
| `phone` | string | ✔ | E.164 `^\+[1-9][0-9]{7,14}$` |
| `consent.policyVersion` | string | ✔ | versión `is_current` → si no, 422 en `consent.policyVersion` |
| `consent.purposes` | `ConsentPurpose[]` | ✔ | debe incluir `PERSONAL_DATA`; admite `HEALTH_EMERGENCY`; `SENSITIVE_VICTIM` → 422 |

No hay campo de rol: el autorregistro crea siempre `TOURIST`. Respuesta 201 `RegisterResponse {userId, email, roles: [ROLE_TOURIST]}` con `Location`; no emite tokens ni cookie.

### 3.2.2 Consentimientos del titular (`/api/v1/me/consents`)

`ConsentAcceptance {policyVersion, purposes[]}` (también es el objeto `consent` del registro). `POST` otorga sobre la política vigente, omite las finalidades ya vigentes (idempotente) y responde el estado resultante; `SENSITIVE_VICTIM` solo con `ROLE_HOST`.

`ConsentStatusResponse`:

| Campo | Tipo | Notas |
|---|---|---|
| `currentPolicyVersion` | string \| null | `null` si no hay política publicada |
| `consents[].purpose` | `ConsentPurpose` | una entrada por finalidad aplicable a los roles del titular |
| `consents[].state` | `VALID\|OUTDATED\|REVOKED\|MISSING` | `OUTDATED` = otorgado sobre una versión anterior |
| `consents[].policyVersion` | string \| null | versión del último acto |
| `consents[].occurredAt` | date-time \| null | UTC |
| `requiresConsentUpdate` | boolean | falta alguna finalidad exigida por rol (`PERSONAL_DATA`; `+SENSITIVE_VICTIM` para HOST) |
| `pendingPurposes` | `ConsentPurpose[]` | finalidades exigidas no vigentes |

### 3.2.3 Logout

`POST /api/v1/auth/logout` revoca el refresh token de la cookie (`LOGOUT`), la borra y responde 204, también sin cookie o con un token inválido.

### 3.3 `AssistedHostRegistrationRequest`

| Campo | Tipo | Req. | Validación / Notas |
|---|---|---|---|
| `fullName` | string | ✔ | 3–150 |
| `docType` | `CC\|CE\|PA\|PPT\|TI` | ✔ | |
| `docNumber` | string | ✔ | `^[A-Za-z0-9]{4,20}$`; `(docType, docNumber)` único → 409 |
| `phone` | string | ✔ | E.164 `^\+[1-9][0-9]{7,14}$` |
| `email` | string \| null | | si se omite, el servidor genera `{docType}{docNumber}@comunidad.integrate.co` en minúsculas (p. ej. `cc1094857483@…`) |
| `linkManagerId` | UUID \| null | condicional | **obligatorio si el invocador es `ADMIN`**; si es `LINK_MANAGER` se usa el gestor autenticado |
| `victimData.victimCondition` | boolean | ✔ | cifrado → `victim_condition_enc` |
| `victimData.ruvRegistrationNumber` | string \| null | | ≤ 40, cifrado |
| `victimData.victimizationType` | string \| null | | ≤ 80, cifrado |
| `victimData.supportDocS3Key` | string \| null | | ≤ 512; llave emitida por `/gestor/uploads` con `kind = VICTIM_SUPPORT_DOC` |
| `consent.policyVersion` | string | ✔ | debe ser la versión `is_current` → si no, 422 |
| `consent.purposes` | string[] | ✔ | exactamente `{PERSONAL_DATA, SENSITIVE_VICTIM}` |
| `consent.physicalConsentFormS3Key` | string | ✔ | PDF firmado, subido antes a S3 con `/gestor/uploads` (`kind = PHYSICAL_CONSENT_FORM`) |

Reglas de servidor (ADR-0007 §3):
- **Gestor efectivo:** un `LINK_MANAGER` sin acreditación `ACTIVE` recibe 403 `ENROLLMENT_MANAGER_NOT_ACTIVE`. Si invoca un `ADMIN`, `linkManagerId` falta → 422 (`invalidParams[0].name = linkManagerId`), no existe → 404, no está `ACTIVE` → 409 `CATALOG_INVALID_STATUS_TRANSITION`.
- **Asociación:** es siempre la del gestor; el cliente no la elige. Así se cumple la regla de misma asociación (RF-03), que además respaldan las FK compuestas (D3). Una asociación inactiva responde 409 `CATALOG_HOST_PROFILE_EXISTS`.
- **Datos de víctima:** con `victimCondition = false` no se admiten RUV, hecho victimizante ni soporte (422 por campo). Se cifran con AES-256-GCM en la aplicación, atados a su fila y columna.
- **Documentos:** cada llave debe tener el prefijo de su tipo, existir en S3, ser `application/pdf` de hasta 5 MB y no estar ya usada; si no, 422 `ENROLLMENT_DOCUMENT_INVALID`.
- **Credencial:** la contraseña inicial es un secreto aleatorio que nunca se devuelve. La cuenta se habilita con un código de activación (§3.5).

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

### 3.5 Gestor de Enlace y activación asistida (ADR-0007)

**Acreditación** (`POST /api/v1/admin/link-managers`, `ADMIN`): `{userId, associationId, accreditedOn}` (fecha no futura) crea la acreditación `ACTIVE` y otorga `ROLE_LINK_MANAGER`, visible desde el siguiente login o refresh. Respuesta: `{linkManagerId, userId, associationId, accreditationStatus, accreditedOn}`. Una acreditación vigente responde 409 `ENROLLMENT_MANAGER_EXISTS`. Las acciones `suspend` (`ACTIVE → SUSPENDED`), `reinstate` (`SUSPENDED → ACTIVE`) y `revoke` (terminal) responden 409 `CATALOG_INVALID_STATUS_TRANSITION` con `currentStatus` si no aplican.

**Subida prefirmada** (`POST /api/v1/gestor/uploads`): `{kind: PHYSICAL_CONSENT_FORM | VICTIM_SUPPORT_DOC}` → 201 `{s3Key, uploadUrl, contentType: "application/pdf", maxSizeBytes: 5242880, expiresAt}`. El servidor genera la llave (`consents/physical/{yyyy}/{MM}/{uuid}.pdf` o `host-support/…`). El cliente hace `PUT uploadUrl` con `Content-Type: application/pdf` antes de `expiresAt` (5 minutos); el backend no recibe el binario.

**Anfitriones del gestor** (`GET /api/v1/gestor/hosts`, paginado, orden de asignación más reciente): `ManagedHost` = `{hostProfileId, userId, fullName, email, emailIsSynthetic, phone, docType, docNumberLast4, credentialStatus, assignedSince}`. Solo asignaciones vigentes del gestor autenticado; nunca datos de víctima ni el documento completo.

**Código de activación** (`POST /api/v1/gestor/hosts/{hostProfileId}/activation-code`): 201 `{code: "XXXXX-XXXXX", expiresAt}` con `Cache-Control: no-store`. Base32 de Crockford, vence a las 72 h y solo se guarda su SHA-256. Emitir uno nuevo revoca el anterior. Un anfitrión fuera de la jurisdicción vigente responde 404; una cuenta ya activa, 409.

**Activación** (`POST /api/v1/auth/activate`, público): `{email, code, newPassword}` → 204. La contraseña cumple la política de 12–128 caracteres (422). El código tolera minúsculas, guiones y espacios. Correo inexistente, código erróneo, vencido, consumido o con 5 intentos fallidos responden el mismo 401 `AUTH_ACTIVATION_INVALID`. Tras activar, el anfitrión inicia sesión con `/auth/login`.

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

## 4.1 Catálogo público, panel del anfitrión y moderación (ADR-0006)

**Endpoints**

| Método | Ruta | Rol | Éxito | Errores |
|---|---|---|---|---|
| GET | `/api/v1/experiences` | público | 200 (paginado) | 400, 422 |
| GET | `/api/v1/experiences/{id}` | público | 200 | 404 |
| GET | `/api/v1/catalog/categories` · `/departments` · `/departments/{code}/municipalities` | público | 200 | 404 |
| POST · GET | `/api/v1/host/experiences` | `HOST` | 201 · 200 | 401, 403, 422 |
| GET · PUT | `/api/v1/host/experiences/{id}` | `HOST` (titular) | 200 | 404, 409, 422 |
| POST | `/api/v1/host/experiences/{id}/submit` | `HOST` (titular) | 200 | 404, 409 |
| POST · GET | `/api/v1/host/experiences/{id}/schedules` | `HOST` (titular) | 201 · 200 | 404, 409, 422 |
| POST | `/api/v1/host/schedules/{id}/cancel` | `HOST` (titular) | 200 | 404, 409 |
| POST · GET | `/api/v1/admin/associations` | `ADMIN` | 201 · 200 | 403, 409, 422 |
| POST | `/api/v1/admin/host-profiles` | `ADMIN` | 201 | 403, 404, 409 |
| GET | `/api/v1/admin/experiences?status=` | `ADMIN` | 200 | 403 |
| POST | `/api/v1/admin/experiences/{id}/{approve\|reject\|suspend\|reinstate\|retire}` | `ADMIN` | 200 | 403, 404, 409, 422 |

**Reglas**
- **Paginación:** `?page=0&size=20` (máximo 50) → `{items, page, size, totalElements, totalPages}`; orden fijo `created_at DESC, id`.
- **Privacidad (R-05):** las vistas públicas (`ExperienceSummary`, `ExperienceDetail`) muestran la asociación (`associationName`) y el municipio, nunca al anfitrión.
- **Búsqueda:** `departmentCode`, `municipalityCode`, `category`, `intensity`, `q` (título, sin distinguir mayúsculas). Con `dateFrom`, `dateTo` o `minSlots` solo aparecen experiencias con una franja `OPEN` futura en el rango (por defecto, los próximos 60 días) y `minSlots` cupos (por defecto 1).
- **Titularidad:** una experiencia o franja de otro anfitrión responde 404. Un usuario con `ROLE_HOST` sin perfil vigente recibe 403.
- **Estados:** `DRAFT → PENDING_REVIEW → APPROVED | REJECTED`, `REJECTED → DRAFT` al editar, `APPROVED ↔ SUSPENDED`, `→ RETIRED`. Una transición no permitida responde 409 `CATALOG_INVALID_STATUS_TRANSITION` con `currentStatus`. `reject`, `suspend` y `retire` exigen `{reason}` (≤ 1000), que queda en `experience_status_history` junto con el actor.
- **Suspensión y retiro** pasan las franjas futuras `OPEN → CLOSED`; **reactivar** devuelve las futuras `CLOSED → OPEN`.
- **Franjas:** lote de 1 a 60 `{localStart, localEnd, totalSlots}` en hora local `America/Bogota`. Deben ser futuras, con `localEnd > localStart` y `totalSlots ≤ maxCapacityPerSlot` (422 por elemento, `schedules[i].campo`). Un inicio duplicado en el lote o ya existente responde 409 `CATALOG_SCHEDULE_CONFLICT`, y el lote es atómico. Cancelar una franja con cupos vendidos responde 409 hasta la Fase 3.
- **Experiencia:** `maxCapacityPerSlot` 1–30 y no menor que el aforo de franjas futuras abiertas; `unitPriceCop` entero > 0; `municipalityCode` en DIVIPOLA; `latitude` y `longitude` juntas o ninguna; `categoryCodes` ≤ 5 y existentes.
- **Alta de anfitrión:** `{userId, associationId}` crea `host_profiles` (sin datos de víctima) y otorga `ROLE_HOST`, visible desde el siguiente login o refresh. Un perfil vigente o una asociación inactiva responden 409 `CATALOG_HOST_PROFILE_EXISTS`; un NIT o RNT duplicado, 409 `CATALOG_ASSOCIATION_EXISTS`.

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
| `status` | enum `BookingStatus` | Siempre `PENDING` en el 201; estado actual en el replay 200 y en `GET /bookings/{bookingId}` (ADR-0008 §5) |
| `scheduleId`, `experienceId` | UUID | |
| `lockedSlots` | int | |
| `unitPriceCop`, `totalAmountCop` | int64 | `total = unit × slots` (D4) |
| `currency` | `"COP"` | |
| `createdAt`, `expiresAt` | date-time | `expiresAt = createdAt + 15 min` |
| `ttlSecondsRemaining` | int 0–900 | se recalcula en cada respuesta; 0 si ya no está `PENDING` |

`GET /api/v1/bookings/{bookingId}` devuelve este mismo esquema solo al turista dueño; una reserva ajena o inexistente responde 404 (ADR-0008 §1).

### 5.3 Mecanismo transaccional (prevención de sobreventa)

1. Control de idempotencia: se busca `Idempotency-Key` + `touristId`.
   - Si existe con el mismo hash de payload y tiene menos de 24 h → replay 200.
   - Si existe con un hash distinto, o tiene más de 24 h → 409 `IDEMPOTENCY_KEY_REUSED`.
   - Si dos peticiones con la misma clave chocan en el índice único `uq_bookings_idempotency`, la perdedora se revierte completa (incluido el descuento) y se responde como replay (ADR-0008 §3).
2. Precondición de consentimiento `HEALTH_EMERGENCY` → si falta, 428.
3. Transacción `READ COMMITTED` con `lock_timeout = 2s` (fijado en el rol `integrate_api`, `00_bootstrap.sql`) y `SET LOCAL integrate.actor_id`:
   - `SELECT … FROM catalog.schedules WHERE id = ? FOR UPDATE`. Si vence el timeout → 503 `CONCURRENCY_LOCK_TIMEOUT` con `Retry-After: 1`.
   - Si la experiencia no está `APPROVED` → 404. Si la franja no está `OPEN` o ya empezó → 409 `BOOKING_SCHEDULE_NOT_BOOKABLE`.
   - Si `requestedSlots > available_slots` → 409 `BOOKING_INSUFFICIENT_SLOTS` con `availableSlots`.
   - `UPDATE available_slots = available_slots − n`, luego `INSERT bookings` (`PENDING`, `expires_at = now() + 15 min`), luego `INSERT booking_participants`, luego `INSERT audit.audit_trail` como última sentencia, y COMMIT (< 200 ms).
4. El worker TTL (cada 60 s, `FOR UPDATE SKIP LOCKED`) pasa la reserva a `EXPIRED` y restituye los cupos.

---

## 6. Webhook de pasarela – `POST /payments/webhook/{mock|wompi}`

**Mock — cabeceras:** `X-Signature` (hex de 64 caracteres) y `X-Timestamp` (epoch en segundos).
**Mock — firma:** `HMAC_SHA256(PAYMENT_WEBHOOK_SECRET_MOCK decodificado de Base64, X-Timestamp + "." + raw_body)`, comparada en tiempo constante (`MessageDigest.isEqual`). Ventana de ±300 s. Si falla → 401.

**Wompi — firma nativa (ADR-0009 §4):** `signature.checksum = SHA-256(valores de signature.properties leídos de data, en el orden declarado + timestamp + WOMPI_EVENTS_SECRET)`, comparada en tiempo constante y sin distinguir mayúsculas. Durante una rotación también vale `WOMPI_EVENTS_SECRET_PREVIOUS`. No hay ventana de 300 s: Wompi reintenta a los 30 min, 3 h y 24 h con el `timestamp` original, así que solo se rechaza un `timestamp` de más de 25 h o de más de 5 min en el futuro.

**Comunes:** una firma inválida no se guarda en `payment_webhook_events` (evita ocupar la clave única de un evento legítimo) y se audita `PAYMENT_WEBHOOK_REJECTED` (`WARNING`). Un proveedor sin secretos configurados responde 404.

### 6.0 Inicio del pago — `POST /bookings/{bookingId}/payment` (ADR-0009 §3)

`PaymentStartRequest`: `paymentMethod` (`PSE|CARD|NEQUI|DAVIPLATA`). `PaymentCheckoutResponse`: `paymentId`, `bookingId`, `provider` (`MOCK_GATEWAY|WOMPI`), `paymentMethod`, `status` (`INITIATED|PENDING`), `amountCop`, `currency`, `bookingExpiresAt` y `checkout` (siempre con `type`; Wompi agrega `publicKey`, `currency`, `amountInCents`, `reference` e `integritySignature`). Un solo intento abierto (`INITIATED|PENDING`) por reserva: una segunda llamada lo retoma (200); un intento en `ERROR` no se reutiliza.

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
| intento de pago | `data.transaction.reference` (= `paymentId`; la reserva se obtiene del intento) |
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
| `PENDING` (solo Wompi) | `PENDING` | sin cambio | — |
| `APPROVED` sobre una reserva ya no `PENDING` | `APPROVED` | sin cambio (p. ej. `EXPIRED`) | **aprobación tardía (S3):** `refunds` `LATE_APPROVAL` por el 100% en `REQUESTED` (ejecución en T-2.6), sin dispersión, auditoría `HIGH` |

`disbursements.amount_cop = CEIL(amount × community_pct / 100)` (política congelada en el intento) y `release_at = end_time` de la franja `+ payout_grace_hours`. El 15% de la plataforma no se almacena: es `amount − disbursement`. Un evento sobre un pago ya final se registra (`processing_error`) y responde 200 sin efectos. Al confirmar se publica `BookingConfirmedEvent` después del `COMMIT` (señal para T-1.8).

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
- ~~La firma nativa de Wompi es un checksum en el body.~~ **Resuelto en ADR-0009 §4:** `/webhook/wompi` valida el checksum nativo (sin `X-Signature`/`X-Timestamp`), con rotación de dos secretos y ventana de 25 h.
- Pendiente: conciliación periódica contra la API de Wompi (intentos colgados o webhooks perdidos tras una rotación mal hecha); fuera del alcance de T-1.7.
- Las decisiones citan RNF-13, RNF-18, RNF-23 y RNF-29, que no existen en la especificación (solo hay RNF-01 a RNF-09).
- V10 se detiene a propósito si las tablas append-only ya contienen finalidades, `server_outcome` o `incident_type` antiguos.
