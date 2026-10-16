# Matriz de trazabilidad RNF → prueba

> Plan de Gestión de la Calidad v1.00 · ADR-0019. Generada por `qa/scripts/build-evidence-report.mjs` (2026-10-11).

Estados: **Automatizada** (la suite verifica el criterio), **Parcial** (cubre parte del criterio), **Pendiente Fase 2** (el código aún no existe), **No automatizable** (medición manual u operativa), **Brecha** (no implementado y sin tarea asignada).

| RNF | Característica | Proceso | Requisito | Estado | Verificación | Resultado | Nota |
|---|---|---|---|---|---|---|---|
| RNF-01 | Seguridad | P1 Identidades | Cifrado en reposo/tránsito y anonimización | Parcial | SensitiveDataAtRestQualityTest | No cumple | TLS 1.3 A+ se audita en el despliegue (T-2.16) |
| RNF-04 | Seguridad | P1 Identidades | Hashing y custodia de credenciales | Automatizada | CredentialStorageQualityTest, SecretRedactionQualityTest | Cumple | El plan nombra BCrypt; el sistema usa Argon2id (ADR-0005 §3) |
| RNF-05 | Seguridad | P3 Validación y split | Firma criptográfica de vouchers | Parcial | VoucherTamperDetectionQualityTest | Cumple | Verificación en la PWA sin conexión: T-2.10 |
| RNF-06 | Seguridad | P1 Identidades | Bitácora inmutable de Habeas Data | Automatizada | ConsentAuditQualityTest | No cumple |  |
| RNF-07 | Seguridad | P1 Identidades | RBAC y seguridad en endpoints | Automatizada | EndpointSecuritySweepQualityTest | Cumple |  |
| RNF-02 | Eficiencia de desempeño | P2 Reserva | Bloqueo transaccional < 200 ms | Automatizada | BookingLockLatencyQualityTest, qa/k6/booking-lock.js | Cumple |  |
| RNF-08 | Eficiencia de desempeño | P1 Identidades | Latencia de emisión de JWT < 800 ms | Automatizada | LoginLatencyQualityTest, qa/k6/login.js | Cumple |  |
| RNF-09 | Eficiencia de desempeño | P3 Validación y split | Peso del bundle PWA < 2,5 MB | Automatizada | frontend-pwa/scripts/check-bundle-budget.mjs | Cumple |  |
| RNF-10 | Eficiencia de desempeño | P2 Reserva | 500 navegando + 100 checkout, CPU < 75 % | Automatizada | qa/k6/catalog-checkout-mix.js | Cumple | CPU medida en la máquina local, no en AWS |
| RNF-11 | Eficiencia de desempeño | P2 Reserva | Worker de expiración TTL < 1,5 s | Automatizada | ExpirationSweepQualityTest | Cumple |  |
| RNF-03 | Capacidad de interacción | P1 Identidades | Accesibilidad WCAG 2.1 AA / NTC 5854 | Parcial | accessibility.quality.test.tsx, styles/tokens.test.ts | Cumple | Áreas táctiles y lector de pantalla: auditoría manual T-2.15 |
| RNF-12 | Capacidad de interacción | P1 Identidades | Enrolamiento asistido < 3 min | No automatizable | — | No automatizable | Prueba de usuario en campo con el Gestor de Enlace |
| RNF-13 | Capacidad de interacción | P2 Reserva | Protección contra errores en checkout | Automatizada | checkoutGuards.quality.test.tsx | Cumple |  |
| RNF-14 | Capacidad de interacción | P3 Validación y split | Indicadores visuales de validación offline | Pendiente Fase 2 | — | Pendiente Fase 2 | T-2.10 (lector QR) |
| RNF-15 | Capacidad de interacción | P1 Identidades | Legibilidad del consentimiento (IFSZ > 60) | Automatizada | consentReadability.quality.test.ts | No cumple |  |
| RNF-16 | Fiabilidad | P2 Reserva | Tasa cero de overbooking | Automatizada | OverbookingStressQualityTest | Cumple |  |
| RNF-17 | Fiabilidad | P3 Validación y split | Operación offline (IndexedDB) | Pendiente Fase 2 | — | Pendiente Fase 2 | T-2.8, T-2.9 |
| RNF-18 | Fiabilidad | P3 Validación y split | Prevención de doble canje offline | Pendiente Fase 2 | — | Pendiente Fase 2 | T-2.11 |
| RNF-19 | Fiabilidad | P3 Validación y split | Background Sync determinista | Pendiente Fase 2 | — | Pendiente Fase 2 | T-2.3, T-2.13 |
| RNF-20 | Fiabilidad | P3 Validación y split | Contingencia por falla del terminal | Pendiente Fase 2 | — | Pendiente Fase 2 | T-2.2, T-2.12 |
| RNF-21 | Adecuación funcional | P3 Validación y split | Exactitud de la dispersión 85/15 | Parcial | SplitPaymentExactnessQualityTest | Cumple | Se verifica la custodia; la orden de giro es T-2.5 |
| RNF-22 | Adecuación funcional | P3 Validación y split | Política de cancelación 72 h | Pendiente Fase 2 | — | Pendiente Fase 2 | T-2.6 |
| RNF-23 | Adecuación funcional | P2 Reserva | Voucher PDF/A completo | Automatizada | VoucherPdfConformanceQualityTest | No cumple |  |
| RNF-24 | Adecuación funcional | P2 Reserva | Restricción de la capacidad de carga | Automatizada | CapacityParametrizationQualityTest | Cumple |  |
| RNF-25 | Adecuación funcional | P2 Reserva | Idempotencia de webhooks | Automatizada | WebhookIdempotencyQualityTest | Cumple |  |
| RNF-26 | Mantenibilidad | P2 Reserva | Desacoplamiento de workers | Automatizada | WorkerDecouplingQualityTest | Cumple |  |
| RNF-27 | Mantenibilidad | P1 Identidades | Cobertura > 80 % en lógica crítica | Automatizada | JaCoCo (check en mvn verify), Vitest coverage | Cumple |  |
| RNF-28 | Mantenibilidad | P2 Reserva | Estándares de código y OpenAPI | Automatizada | OpenApiCoverageQualityTest, npm run lint | Cumple | SonarQube no se usa: JaCoCo + ESLint (ADR-0019) |
| RNF-29 | Mantenibilidad | P3 Validación y split | Logs JSON con correlation ID (MTTD < 30 min) | Brecha | — | Brecha | Hay traceId (TraceIdFilter) pero no salida JSON estructurada |
| RNF-30 | Mantenibilidad | P1 Identidades | Reusabilidad de componentes UI | Automatizada | componentReuse.quality.test.ts | Cumple |  |

## Cómo ejecutar

| Suite | Comando | Evidencia |
|---|---|---|
| Backend completo + cobertura | `cd backend-api && ./mvnw verify` | `target/site/jacoco`, `target/quality-metrics` |
| Solo calidad (incluye no conformidades) | `cd backend-api && ./mvnw verify -Pquality` | `target/quality-metrics` |
| PWA + cobertura | `cd frontend-pwa && npm run test:quality` | `coverage/vitest-results.json` |
| Bundle inicial | `cd frontend-pwa && npm run build && npm run quality:bundle` | `coverage/bundle-budget.json` |
| Carga | ver `qa/k6/README.md` | `qa/k6/results/*.json` |
| Informe | `node qa/scripts/build-evidence-report.mjs` | `docs/quality/` |
