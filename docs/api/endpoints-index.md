# Índice de endpoints → controlador → ADR

Mapa de consulta rápida. Contrato completo en [`openapi.yaml`](openapi.yaml); decisiones en `docs/architecture/adr/`.
Prefijo común de todas las rutas: `/api/v1`. Controladores en `backend-api/src/main/java/com/integrate/modules/<módulo>/controller/`.
La columna ADR se infirió de los títulos de las ADR; si cambia un endpoint, actualice esta tabla.

## Autenticación y consentimiento — módulo `auth`
| Método y ruta | Controlador | Rol | ADR |
|---|---|---|---|
| POST `/auth/login`, `/auth/register`, `/auth/activate`, `/auth/refresh`, `/auth/logout` | `AuthController` | público / sesión | ADR-0005, ADR-0013 |
| POST `/auth/forgot-password`, `/auth/reset-password` | `AuthController` | público | ADR-0014 |
| GET, PATCH `/auth/me`; POST `/auth/me/password` | `OwnAccountController` | autenticado | ADR-0046 |
| GET `/privacy-policies/current` | `PrivacyPolicyController` | público | ADR-0013 |
| GET, POST `/me/consents` | `MeConsentController` | autenticado | ADR-0013, ADR-0035 |

## Catálogo público — módulo `catalog`
| Método y ruta | Controlador | Rol | ADR |
|---|---|---|---|
| GET `/experiences`, `/experiences/{id}`, `/experiences/{id}/availability` | `PublicCatalogController` | público | ADR-0006, ADR-0012, ADR-0022 |
| GET `/catalog/categories`, `/catalog/departments`, `/catalog/departments/{code}/municipalities` | `PublicCatalogController` | público | ADR-0006 |

## Anfitrión — `HostExperienceController` (rol HOST)
| Método y ruta | ADR |
|---|---|
| POST, GET `/host/experiences`; GET, PUT `/host/experiences/{id}` | ADR-0006 |
| POST `/host/experiences/{id}/submit` | ADR-0006 |
| POST `/host/experiences/{id}/meeting-point/check` | ADR-0030 |
| POST, GET `/host/experiences/{id}/schedules`; POST `/host/schedules/{id}/cancel` | ADR-0006 |

## Administración — rol ADMIN
| Método y ruta | Controlador | ADR |
|---|---|---|
| POST, GET `/admin/associations`; PUT `/admin/associations/{id}`; POST `/admin/associations/{id}/suspend\|activate`; POST `/admin/host-profiles` | `AdminCatalogController` | ADR-0006, ADR-0007, ADR-0047 |
| GET `/admin/experiences`; POST `/admin/experiences`; PUT `/admin/experiences/{id}`; POST `/admin/experiences/{id}/approve\|reject\|suspend\|reinstate\|retire` | `AdminCatalogController` | ADR-0006, ADR-0047, ADR-0048 |
| POST `/admin/experiences/{id}/meeting-point/confirm` | `AdminCatalogController` | ADR-0030 |
| POST `/admin/link-managers`; POST `/admin/link-managers/{id}/suspend\|reinstate\|revoke` | `AdminLinkManagerController` | ADR-0007 |
| GET `/admin/audit`, `/admin/audit/export`, `/admin/audit/verify` | `AuditController` (módulo `audit`) | ADR-0035 |
| GET `/admin/users?role=`, POST `/admin/users` | `AdminPeopleController` | ADR-0048 |
| GET `/admin/users/search?email=\|docType=&document=`, `/admin/users/{id}` | `AdminPeopleController` | ADR-0043 §1 |
| GET `/admin/link-managers`, `/admin/host-profiles` | `AdminPeopleController` | ADR-0043 §1 |
| GET `/admin/incidents`, `/admin/incidents/{id}`; POST `/admin/incidents/{id}/take\|resolve\|dismiss` | `AdminIncidentController` (módulo `offline`) | ADR-0043 §2, ADR-0045 §1 |
| GET `/admin/payout-batches`, `/admin/payout-batches/{id}`, `/admin/payout-batches/{id}/receipt`; POST `/admin/payout-batches/{id}/reconcile` | `AdminPayoutController` (módulo `payment`) | ADR-0043 §3, ADR-0045 §2 |

## Gestor de enlace — rol LINK_MANAGER
| Método y ruta | Controlador | ADR |
|---|---|---|
| POST `/auth/register-assisted` (LINK_MANAGER o ADMIN) | `AssistedRegistrationController` | ADR-0007, ADR-0018 |
| POST `/gestor/uploads` (LINK_MANAGER o ADMIN); GET `/gestor/hosts`; POST `/gestor/hosts/{hostProfileId}/activation-code` | `GestorController` | ADR-0007, ADR-0018 |

## Reservas, pagos y voucher — rol TOURIST (módulos `booking`, `payment`, `voucher`)
| Método y ruta | Controlador | ADR |
|---|---|---|
| POST `/bookings/lock`; GET `/bookings/{bookingId}` | `BookingController` | ADR-0008, ADR-0015 |
| GET `/me/bookings` | `MyBookingsController` | ADR-0023 |
| POST `/bookings/{bookingId}/payment` | `PaymentCheckoutController` | ADR-0009, ADR-0016 |
| POST `/bookings/{bookingId}/cancel` | `BookingCancellationController` | ADR-0034 (regla 72 h, reembolsos) |
| GET `/bookings/{bookingId}/voucher` | `VoucherController` | ADR-0010, ADR-0017 |
| GET `/bookings/{bookingId}/meeting-point` | `BookingController` | ADR-0046 §1 y §4, ADR-0049 (punto exacto y paradas; solo `CONFIRMED`, `no-store`) |
| POST `/payments/webhook/mock`, `/payments/webhook/wompi` (público, idempotente) | `PaymentWebhookController` | ADR-0009, ADR-0029 |
| POST `/dev/payments/{paymentId}/simulate` (solo demo) | `DevPaymentSimulatorController` | ADR-0029 |

Sin endpoint propio: dispersión 85/15 y comprobante contable (ADR-0034) y outbox de correo del voucher (ADR-0011), que corren como servicios internos.

## Operación offline — módulo `offline` (rol LINK_MANAGER; `/offline/sync` también HOST)
| Método y ruta | Controlador | ADR |
|---|---|---|
| GET `/offline/roster`, `/offline/revocations` | `OfflineController` | ADR-0032 |
| POST `/offline/contingency/redeem` | `OfflineController` | ADR-0032 |
| POST `/offline/sync` | `OfflineController` | ADR-0033 |

## ADR transversales (sin endpoint propio)
ADR-0001 infraestructura, ADR-0002 stack backend, ADR-0003 errores RFC 9457, ADR-0019 suite de calidad, ADR-0035 auditoría/cifrado (aplica a todo acceso sensible).
