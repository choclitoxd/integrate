# Hoja de ruta de ejecución — Proceso 3 (Etapa 2)

Fuente: `docs/roadmap_ejecucion.md` (Etapa 2) y `docs/status/PROJECT_STATUS.md`. Regla: backend completo antes del frontend; cada bloque backend abre con su ADR (`docs/rules/adr-workflow.md`) y espera aceptación expresa antes de producir código. Todo bloque cierra con el Quality Gate.

## Punto de partida (verificado en el repo)

- Existen los módulos `auth`, `booking`, `catalog`, `notification`, `payment`, `voucher`. **No existen** `modules/offline/` ni `modules/audit/`.
- Las tablas ya están migradas: `V7__offline.sql` (`devices`, `roster_downloads`, `sync_batches`, `offline_validations`, `sync_incidents`) y `V8__audit_notification.sql`. Los bloques B1–B3 deberían necesitar poco o nada de DDL nuevo; confirmarlo en cada ADR.
- Siguiente número de ADR libre: **ADR-0032**.
- Ninguna tarea T-2.x está iniciada. Las dependencias de Etapa 1 (T-1.3, T-1.5, T-1.7, T-1.8) están completas.

## Orden de ejecución

El orden sigue el camino crítico de dependencias; los bloques con la misma letra pueden ir en paralelo.

| Bloque | Tareas | ADR | Entregable | Depende de |
| :--- | :--- | :--- | :--- | :--- |
| **B1 Padrón y contingencia** | T-2.1, T-2.2 | ADR-0032 | `modules/offline/`: descarga de padrón + delta de revocación; canje manual sin doble canje | Etapa 1 |
| **B2 Sincronización** | T-2.3, T-2.4 | ADR-0033 | `POST /offline/sync` idempotente por lotes, reservas a `EXECUTED`, resolución determinista de conflictos (85 % intocable, incidencia al Administrador) | B1 |
| **B3 Dinero** | T-2.5, T-2.6 | ADR-0034 | Split 85/15, dispersión supletoria a 24 h (job programado), política de cancelación de 72 h | B2 |
| **B4 Auditoría** | T-2.7 | ADR-0035 | `modules/audit/`: bitácora inmutable de Habeas Data, AES-256 en reposo | B3 |
| **F1 Infra offline PWA** | T-2.8 | ADR-0036 | Workbox + `IntegrateOfflineDB` (< 10 MB) | puede iniciar desde ya (solo depende de T-0.7) |
| **F2 Padrón + QR** | T-2.9, T-2.10, T-2.11 | ADR-0037 | Descarga local, lector QR con Web Crypto, `CONSUMIDO_LOCAL` atómico | F1, B1 |
| **F3 Contingencia + cola** | T-2.12, T-2.13 | (cubierto por ADR-0037 o uno propio) | Canje manual en PWA, cola de asistencias y Background Sync | F2, B2 |
| **Q1 Carga** | T-2.14 | ADR-0038 | k6: 200 usuarios, 0 % sobreventa, < 200 ms (RNF de la matriz de ADR-0019) | puede ir en paralelo con B1–B4 (solo T-1.5/T-1.6) |
| **Q2 Accesibilidad** | T-2.15 | — | Auditoría WCAG 2.1 AA / NTC 5854, Lighthouse ≥ 90 | F3 |
| **D1 CI/CD y AWS** | T-2.16 | ADR-0039 | Pipeline, ECS Fargate, RDS, S3, HTTPS | B4, Q1, Q2 |

Para ir más rápido: F1 y Q1 no dependen del backend nuevo y pueden avanzar en una sesión aparte mientras B1–B4 se hacen en secuencia.

## Ciclo de trabajo por bloque

1. Redactar el ADR (formato Nygard) y pedir aceptación. Sin ADR aceptado no hay código.
2. Pruebas primero cuando el contrato es observable (TDD por rebanada vertical: un endpoint o una regla a la vez).
3. Implementar respetando capas y `ApiError`/`createHttpClient` (frontend).
4. Quality Gate de la Directiva 7 en verde; actualizar `PROJECT_STATUS.md` (casillas + bitácora) y la matriz de RNF.
5. `graphify update .` y commit según `docs/rules/git-workflow.md` (sin secretos; checklist pre-commit).

## Cobertura de RNF a enlazar

ADR-0019 dejó fuera, a la espera del Proceso 3, los RNF-14, 17, 18, 19, 20, 21 y 22. Cada bloque debe añadir su prueba a la suite `quality` y pasarlos de «Pendiente» a medidos en `docs/quality/evidencias-metricas.md`. RNF-18 (anti-doble canje local) cae en F2.

## Riesgos a vigilar

- **R-02** (sin conectividad prolongada): justifica la dispersión supletoria a 24 h (B3) y la contingencia manual (B1/F3).
- Conflicto de sincronización vs. cancelación: la regla del 85 % es irrenunciable; B2 y B3 deben decidir juntas el orden de precedencia, así que revisar ADR-0033 y ADR-0034 como par.
- Cuota de IndexedDB y de iOS Safari (F1): fijar el límite de 10 MB desde el ADR.

## Primer paso propuesto

Redactar **ADR-0032 (padrón preventivo, delta de revocación y canje de contingencia)** y, en paralelo en otra sesión, **ADR-0036 (infraestructura offline de la PWA)**.
