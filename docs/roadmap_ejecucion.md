# Roadmap Técnico de Ejecución – Sistema Intégrate

**Proyecto:** Intégrate – Plataforma Transaccional de Turismo Comunitario  
**Versión:** 1.0.0  
**Fecha:** Octubre 2026  
**Metodología:** Agile / Scrumban con Gobernanza de Arquitectura Guiada por Dependencias  
**Rol Responsable:** Lead Software Architect & Senior Technical Project Manager  

---

## 1. Estrategia y Principios del Roadmap

El presente roadmap técnico de ejecución establece la secuencia lógica, técnica y temporal para la construcción, validación y despliegue del sistema **Intégrate**.

### Principios Rectores de Dependencia:
1. **Fundación Inmutable Primero:** No se escribe lógica de negocio sin un esquema relacional con transacciones ACID verificado y migraciones de base de datos automatizadas.
2. **Backend antes que Frontend en cada etapa:** Cada etapa construye, estabiliza y certifica el 100% de su backend (Fases 1.1 y 2.1) antes de abordar las interfaces de la PWA móvil (Fases 1.2 y 2.2).
3. **Estabilización del Core Transaccional (Etapa 1):** Antes de abordar la complejidad de sincronización desconectada (*offline-first*), se consolida y prueba el flujo transaccional directo: identidades y enrolamiento asistido, catálogo, bloqueo pesimista de aforo (`SELECT ... FOR UPDATE`), retención de cupos con TTL (15 min), cobro con retención en custodia (*escrow*) y emisión del voucher criptográfico.
4. **Resiliencia Territorial Desconectada (Etapa 2 · Fases 2.1 y 2.2):** Sobre el core ya estabilizado se construyen los endpoints de padrón, contingencia y sincronización, y la capacidad *offline* del cliente con Service Workers, IndexedDB y validación de firma asimétrica en campo.
5. **Protección Comunitaria Determinista (Etapa 2 · Fase 2.1):** La sincronización y la resolución de colisiones implementan como regla inviolable la salvaguarda del 85% para las comunidades campesinas y víctimas; la dispersión 85/15 se ejecuta en esta etapa.
6. **Calidad, Cumplimiento Legal y Producción (Etapa 2 · Fase 2.3):** Pruebas de carga, accesibilidad WCAG 2.1 AA / NTC 5854 y despliegue cloud en AWS; la auditoría inmutable de Habeas Data con AES-256 se construye en la Fase 2.1.

---

## 2. Cronograma de Fases Secuenciales de Desarrollo

El desarrollo se articula en **Dos Grandes Etapas Operativas** trazadas directamente con los tres procesos de negocio transaccionales del Plan de Calidad de Software. Dentro de cada etapa, el backend precede al frontend.

```mermaid
gantt
    title Roadmap de Ejecución Técnica - Intégrate (2 Grandes Etapas Operativas)
    dateFormat  YYYY-MM-DD
    section Etapa 1: Proc 1 y 2 (Identidades & Core Transaccional)
    Fase 0 - Setup & DB (Infraestructura Base)          :f0, 2026-10-05, 14d
    Fase 1.1 - Backend Proc 1: Auth, RBAC & Enrolamiento :f11a, after f0, 7d
    Fase 1.1 - Backend Proc 2: Catálogo, Reservas & TTL  :f11b, after f11a, 7d
    Fase 1.1 - Backend Proc 2: Pago, Voucher & Correo    :f11c, after f11b, 7d
    Fase 1.2 - Frontend PWA Móvil: Turista & Gestor      :f12, after f11c, 7d
    section Etapa 2: Proc 3 (Validación Offline & Dispersión)
    Fase 2.1 - Backend Proc 3: Padrón, Sync & Conflictos :f21a, after f12, 7d
    Fase 2.1 - Backend Proc 3: Split 85/15, 72h & Auditoría :f21b, after f21a, 7d
    Fase 2.2 - Frontend PWA Offline: SW, QR & Background Sync :f22, after f21b, 7d
    Fase 2.3 - QA & DevOps: Carga, Accesibilidad & AWS   :f23, after f22, 7d
```

---

## 3. Desglose Detallado de Tareas por Etapa y Dependencia Técnica

> La numeración de tareas es la de `docs/status/PROJECT_STATUS.md` (fuente de verdad operativa). Los ADR y los commits citan estos mismos identificadores.

---

### ETAPA 1: Núcleo de Identidades, Reserva Concurrente y Checkout Transaccional (Procesos 1 y 2)

#### FASE 0: Entorno Base, Infraestructura y Persistencia - [Completada]
*Objetivo:* Establecer el entorno de desarrollo reproducible y el esquema de base de datos PostgreSQL con migraciones versionadas y políticas de seguridad.

| ID Tarea | Nombre de la Tarea | Dependencias Técnicas | Desbloquea |
| :--- | :--- | :--- | :--- |
| **T-0.1** | Orquestar entorno local con Docker Compose (PostgreSQL 16, LocalStack S3 y Mailpit/SMTP) y plantillas de variables de entorno (ADR-0001). | Ninguna | T-0.2 |
| **T-0.2** | Validar el contenedor PostgreSQL, los roles (`integrate_owner`, `integrate_api`) y la base de datos `integrate`. | T-0.1 | T-0.5 |
| **T-0.3** | Configurar boilerplate Backend (Java 21, Spring Boot 4.1.x, Spring Data JPA, Lombok, Validation, Maven Wrapper) con paquetes `com.integrate.*` (ADR-0002). | Ninguna | T-0.4 |
| **T-0.4** | Configurar `IntegrateApplication` y perfiles `application.yml` / `application-dev.yml` con fail-fast de variables de entorno. | T-0.3 | T-0.5, T-0.6 |
| **T-0.5** | Ejecutar y verificar las migraciones Flyway `V1` a `V10` (tipos, tablas base, índices transaccionales y privilegios). | T-0.2, T-0.4 | T-1.1, T-1.4 |
| **T-0.6** | Estructura común: errores RFC 9457, `ErrorCode`, `GlobalExceptionHandler`, `TraceIdFilter`, ArchUnit (ADR-0003). | T-0.4 | T-1.1 |
| **T-0.7** | Boilerplate Frontend PWA (React, Vite, TypeScript, Tailwind CSS, React Router) (ADR-0004). | Ninguna | T-1.10 |

---

#### FASE 1.1: Backend - Núcleo Transaccional e Identidades (Procesos 1 y 2)
*Objetivo:* Construir, estabilizar y validar el 100% del backend de la Etapa 1: identidades y enrolamiento asistido (Proceso 1), catálogo, bloqueo pesimista de aforo, cobro con retención en custodia, voucher criptográfico y notificación (Proceso 2).

| ID Tarea | Nombre de la Tarea | Dependencias Técnicas | Desbloquea |
| :--- | :--- | :--- | :--- |
| **T-1.1** | **[Proceso 1] Dominio de Identidad & RBAC:** Entidades, repositorios y servicios de autenticación con Argon2id, sesiones rotativas y consentimiento (ADR-0005). | T-0.5, T-0.6 | T-1.2 |
| **T-1.2** | **[Proceso 1] Endpoints de Sesión:** login/registro/refresh/logout y `/me/consents` con JWT RS256 y Spring Security Resource Server (ADR-0005). | T-1.1 | T-1.3, T-1.4, T-1.11 |
| **T-1.3** | **[Proceso 1] Enrolamiento Asistido:** Servicios y endpoints del Gestor de Enlace (`/api/v1/gestor/hosts`), asignación de asociación comunitaria y consentimiento inmutable de Habeas Data (Ley 1581 / Ley 1448). | T-1.2, T-1.4 | T-1.12, T-2.1 |
| **T-1.4** | **[Proceso 2] Catálogo Territorial & Franjas:** Experiencias, municipios DIVIPOLA y franjas horarias con cupo ecológico máximo (ADR-0006). | T-0.5, T-1.2 | T-1.3, T-1.5, T-1.10 |
| **T-1.5** | **[Proceso 2] Motor de Concurrencia y Bloqueo Pesimista (`SELECT FOR UPDATE`):** Reservas `PENDING` con 0% de overbooking y < 200 ms (`modules/booking/`). | T-1.4 | T-1.6, T-1.7 |
| **T-1.6** | **[Proceso 2] Booking Expiration Worker (TTL 15 min):** `@Scheduled` que libera los cupos de reservas vencidas o abandonadas. | T-1.5 | T-1.7 |
| **T-1.7** | **[Proceso 2] Adaptador de Pagos & Webhook:** `PaymentGatewayAdapter` con cobro en COP, webhook idempotente y retención en custodia (*escrow*) (`modules/payment/`). | T-1.5 | T-1.8 |
| **T-1.8** | **[Proceso 2] Voucher Criptográfico:** Firma RSA-2048, QR de alta densidad y exportación a PDF/A (`modules/voucher/`). | T-1.7 | T-1.9, T-2.1 |
| **T-1.9** | **[Proceso 2] Notificaciones Transaccionales:** Correo (Gmail API / SMTP) con el voucher y el resumen de la reserva. | T-1.8 | T-1.10 |

---

#### FASE 1.2: Frontend - Interfaces PWA Móvil y Portales de Usuario (Procesos 1 y 2)
*Objetivo:* Se implementa con el backend de la Fase 1.1 operativo y certificado. Factor de forma móvil estricto (viewport 360–430 px, contenedor `max-w-md`, sticky bottom bar y áreas táctiles $\ge 44\times 44$ px).

| ID Tarea | Nombre de la Tarea | Dependencias Técnicas | Desbloquea |
| :--- | :--- | :--- | :--- |
| **T-1.10a** | **[Proceso 2] Catálogo móvil:** Tarjetas verticales, filtros DIVIPOLA en bottom-sheet y detalle de la experiencia. | T-0.7, T-1.4 | T-1.10b |
| **T-1.10b** | **[Proceso 2] Selector táctil de franjas:** Fechas y franjas con cupos en tiempo real; franjas agotadas inhabilitadas. | T-1.10a | T-1.10c |
| **T-1.10c** | **[Proceso 2] Formulario de reserva:** Identificación, contacto de emergencia, EPS, `inputmode` móviles y aceptación de Habeas Data. | T-1.10b, T-1.5, T-1.11 | T-1.10d |
| **T-1.10d** | **[Proceso 2] Checkout:** Sticky bottom bar, cuenta regresiva del TTL de 15 min, desglose de tarifas y pasarela en COP. | T-1.10c, T-1.6, T-1.7 | T-1.10e |
| **T-1.10e** | **[Proceso 2] Confirmación:** QR del voucher a pantalla completa, ficha de viaje y exportación a PDF/A. | T-1.10d, T-1.8 | Fin Etapa 1 |
| **T-1.11** | **[Proceso 1] Autenticación móvil:** Login, registro de turistas, sesión con refresh token y modal de consentimiento. | T-0.7, T-1.2 | T-1.10c |
| **T-1.12** | **[Proceso 1] Enrolamiento asistido móvil:** Portal del Gestor (`/gestor/anfitriones/enrolamiento`) con formulario táctil y captura de consentimiento. | T-1.3, T-1.11 | Fin Etapa 1 |

---

### ETAPA 2: Validación Territorial Offline-First y Dispersión Financiera (Proceso 3)

#### FASE 2.1: Backend - Sincronización Diferida, Dispersión y Auditoría (Proceso 3)
*Objetivo:* Construir y validar primero la lógica backend de padrón, contingencia, sincronización, reglas financieras y resolución de conflictos.

| ID Tarea | Nombre de la Tarea | Dependencias Técnicas | Desbloquea |
| :--- | :--- | :--- | :--- |
| **T-2.1** | **[Proceso 3] Descarga preventiva:** Endpoints del padrón de vouchers de la jornada y lista delta de revocación (`modules/offline/`). | T-1.3, T-1.8 | T-2.2, T-2.9 |
| **T-2.2** | **[Proceso 3] Canje manual de contingencia:** `POST /api/v1/offline/contingency/redeem` con verificación contra padrón y revocación, sin doble canje. | T-2.1 | T-2.3, T-2.12 |
| **T-2.3** | **[Proceso 3] Sincronización idempotente:** `POST /api/v1/offline/sync` por lotes y transición atómica de reservas a `EXECUTED`. | T-2.1, T-1.5 | T-2.4, T-2.13 |
| **T-2.4** | **[Proceso 3] Resolución determinista de conflictos:** El 85% de la comunidad es irrenunciable; incidencia contable al Administrador. | T-2.3, T-1.7 | T-2.5, T-2.6 |
| **T-2.5** | **[Proceso 3] Dispersión (Split Payment 85/15):** 85% a la Asociación/Anfitrión y 15% a la plataforma, con dispersión supletoria a las 24 h. | T-2.3, T-2.4 | T-2.7 |
| **T-2.6** | **[Proceso 3] Política de cancelación de 72 h:** > 72 h reembolso del 85%; $\le$ 72 h o No-Show, liquidación íntegra a la comunidad. | T-1.7, T-2.4 | T-2.7 |
| **T-2.7** | **[Proceso 3] Auditoría inmutable de Habeas Data:** Bitácora de accesos sensibles (Ley 1581 / Ley 1448) con cifrado AES-256 en reposo (`modules/audit/`). | T-2.5, T-2.6 | T-2.16 |

---

#### FASE 2.2: Frontend - PWA Móvil Offline-First y Validación Territorial (Proceso 3)
*Objetivo:* Implementar para smartphones de campo, consumiendo los endpoints de la Fase 2.1 ya certificados.

| ID Tarea | Nombre de la Tarea | Dependencias Técnicas | Desbloquea |
| :--- | :--- | :--- | :--- |
| **T-2.8** | **[Proceso 3] Infraestructura offline:** Service Workers (Workbox) e IndexedDB (`IntegrateOfflineDB`) acotada a < 10 MB. | T-0.7 | T-2.9 |
| **T-2.9** | **[Proceso 3] Descarga preventiva en PWA:** Padrón local, lista de revocación y clave pública RSA en IndexedDB. | T-2.8, T-2.1 | T-2.10 |
| **T-2.10** | **[Proceso 3] Lector QR y validación offline:** Cámara trasera (`facingMode: 'environment'`), vibración (`navigator.vibrate`) y Web Crypto API. | T-2.9 | T-2.11 |
| **T-2.11** | **[Proceso 3] Anti-doble canje local (RNF-18):** Marcación atómica `CONSUMIDO_LOCAL` en IndexedDB al momento de la lectura. | T-2.10 | T-2.13 |
| **T-2.12** | **[Proceso 3] Contingencia en PWA:** Consulta y canje manual por código alfanumérico ante falla física de la cámara. | T-2.2, T-2.8 | T-2.13 |
| **T-2.13** | **[Proceso 3] Cola de asistencias y Background Sync:** Encola las validaciones locales y las despacha al recuperar cobertura. | T-2.11, T-2.12, T-2.3 | T-2.15 |

---

#### FASE 2.3: Calidad, Hardening y Despliegue Cloud (QA & DevOps)
*Objetivo:* Certificar carga concurrente y accesibilidad, y desplegar la solución en infraestructura AWS.

| ID Tarea | Nombre de la Tarea | Dependencias Técnicas | Desbloquea |
| :--- | :--- | :--- | :--- |
| **T-2.14** | **[Backend/QA] Pruebas de carga:** 200 usuarios concurrentes sobre `SELECT ... FOR UPDATE` (k6/JMeter): 0% sobreventa y < 200 ms. | T-1.5, T-1.6 | T-2.16 |
| **T-2.15** | **[Frontend/QA] Accesibilidad:** Auditoría y remediación WCAG 2.1 AA / NTC 5854 (contraste solar, teclado, lectores de pantalla). | T-1.10e, T-1.12, T-2.13 | T-2.16 |
| **T-2.16** | **[DevOps] CI/CD y despliegue en AWS:** ECS Fargate, RDS PostgreSQL, S3 y HTTPS. | T-2.7, T-2.14, T-2.15 | Puesta en Producción |

---

## 4. Criterios de "Done" (Definition of Done - DoD) por Etapa

Cada etapa se cierra formalmente cuando se cumplen los entregables de todas sus fases.

### ETAPA 1 (Procesos 1 y 2)

#### DoD Fase 0: Setup & Base de Datos
- [x] Contenedores Docker de PostgreSQL y LocalStack arrancan de forma limpia con `docker-compose up`.
- [x] Todas las migraciones de Flyway se aplican sin errores y se genera el esquema relacional con sus restricciones de integridad (`CHECK`, `FOREIGN KEY`).
- [x] Los paquetes del backend están 100% aislados bajo `com.integrate.*` sin ninguna referencia o dependencia a nombres del dominio de comidas (*FoodExpress*).
- [ ] Pipeline de GitHub Actions compila el código y ejecuta análisis estático con SonarQube (0 vulnerabilidades críticas).

#### DoD Fase 1.1: Backend del Core Transaccional
- [ ] El turista puede autenticarse, consultar experiencias y solicitar el bloqueo de cupos (verificado por API).
- [ ] El Gestor de Enlace puede enrolar anfitriones con consentimiento asistido de Habeas Data.
- [ ] Prueba de concurrencia integrada valida que el bloqueo pesimista (`SELECT ... FOR UPDATE`) no permite sobreventa bajo hilos paralelos (0 colisiones).
- [ ] Las reservas abandonadas se cancelan automáticamente tras 15 minutos y los cupos se liberan en el inventario.
- [ ] El webhook de pago se procesa de forma idempotente y deja los fondos en custodia (*escrow*); la dispersión 85/15 se certifica en la Etapa 2.
- [ ] El voucher criptográfico se emite con firma digital válida y el PDF queda almacenado en S3 y enviado al correo del comprador.

#### DoD Fase 1.2: Frontend PWA Móvil
- [ ] El turista recorre catálogo, franjas, reserva, checkout y confirmación en un smartphone (360–430 px) sin scroll horizontal.
- [ ] Login, registro y renovación de sesión funcionan en la PWA con el modal de consentimiento.
- [ ] El Gestor de Enlace completa el enrolamiento asistido desde su smartphone.

### ETAPA 2 (Proceso 3)

#### DoD Fase 2.1: Backend de Validación, Sincronización y Dispersión
- [ ] El padrón y la lista de revocación se descargan por API y el canje de contingencia impide el doble canje.
- [ ] Ante un conflicto de sincronización por cancelación previa, el sistema ejecuta la resolución determinista: no descuenta el 85% de la comunidad y crea la incidencia contable para el Administrador.
- [ ] La dispersión calcula y ejecuta de forma comprobada el 85% comunidad / 15% plataforma.
- [ ] La regla de cancelación de 72 horas ejecuta con precisión matemática los reembolsos del 85% (>72h) o la retención total a favor del anfitrión ($\le$72h).
- [ ] La tabla de consentimientos es inmutable y los datos sensibles están cifrados con AES-256.

#### DoD Fase 2.2: Frontend PWA Offline-First
- [ ] La PWA se instala correctamente en dispositivos móviles (Android/iOS) y funciona en modo avión (desconectado).
- [ ] El Gestor de Enlace puede descargar con un clic el padrón de reservas y la clave pública institucional.
- [ ] El escáner QR de la PWA valida matemáticamente la autenticidad del voucher sin conexión celular.
- [ ] Todo voucher escaneado se almacena como `CONSUMIDO_LOCAL` en IndexedDB e impide inmediatamente su reuso local.
- [ ] Al recuperar la conexión a internet, las asistencias encoladas se sincronizan automáticamente con el servidor en segundo plano.

#### DoD Fase 2.3: Calidad, Hardening y Cloud
- [ ] Prueba de carga de 200 peticiones simultáneas sobre el endpoint de bloqueo responde en un tiempo promedio inferior a 200 ms y con 0% de sobreventa.
- [ ] Auditoría de accesibilidad Lighthouse certifica puntuación $\ge 90$ en accesibilidad cumpliendo WCAG 2.1 AA.
- [ ] El sistema se encuentra desplegado y operativo en AWS con HTTPS activo.

---

## 5. Matriz de Riesgos Técnicos y Planes de Contingencia

| ID Riesgo | Descripción del Riesgo Técnico | Probabilidad | Impacto | Nivel de Severidad | Estrategia de Mitigación y Contingencia |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **R-01** | **Contención y Bloqueos Prolongados en Base de Datos:** Que peticiones de bloqueo pesimista queden en espera indefinida degradando la latencia del API. | Baja | Alto | **Medio** | Transacciones ultracortas (< 200 ms) liberando la conexión de inmediato tras descontar cupos; configuración de `lock_timeout = 2s` en PostgreSQL para abortar y responder con reintento controlado si hay sobrecarga. |
| **R-02** | **Fallas de Conectividad Prolongadas en Campo:** Que el Gestor de Enlace pase múltiples días sin sincronizar asistencias, demorando los pagos. | Alta | Medio | **Medio** | Dispersión supletoria automática programada en el servidor a las 24 horas del evento si no se registra reclamo del turista; módulo web de contingencia para ingreso manual con código alfanumérico. |
| **R-03** | **Fraude por Voucher Duplicado en Modo Desconectado:** Que un usuario comparta una captura de pantalla de su QR con un tercero y ambos intenten acceder en puntos rurales distintos. | Media | Alto | **Alto** | Marcación atómica inmediata en IndexedDB (`CONSUMIDO_LOCAL`) en el dispositivo del gestor; verificación visual obligatoria contra el documento de identidad oficial (CC/Pasaporte) impreso en el voucher. |
| **R-04** | **Reclamaciones por Cancelación con Discrepancia Offline:** Turista que canceló con reembolso pero consume el servicio en territorio desconectado. | Media | Medio | **Medio** | Regla de negocio determinista: el 85% de la comunidad es intocable y garantizado; la plataforma absorbe la contingencia inicial y el Administrador concilia legalmente con el usuario mediante la traza de auditoría. |
| **R-05** | **Fugas de Seguridad en Datos de Víctimas (Ley 1581):** Exposición de la condición de víctima de los anfitriones poniendo en riesgo su seguridad territorial. | Baja | Crítico | **Crítico** | Cifrado en reposo AES-256-GCM a nivel de campo en la base de datos; ofuscación y anonimización de identidades en todas las interfaces públicas del catálogo y logs del sistema. |
| **R-06** | **Fallas o Caídas del Proveedor Externo de Pagos:** Indisponibilidad de la pasarela durante fines de semana o temporadas altas de turismo. | Media | Alto | **Alto** | Desacoplamiento mediante el patrón `PaymentGatewayAdapter`; circuit breaker con Resilience4j para degradación elegante y reintentos automáticos de confirmación vía webhooks con firma HMAC. |

---

## 6. Aprobación y Traspaso a Ejecución

El presente roadmap técnico ha sido estructurado para garantizar el cumplimiento riguroso de los objetivos de la materia Ingeniería de Software III y los requerimientos del negocio comunitario. La ejecución técnica inicia de forma inmediata a partir de las tareas de la **Fase 0**.
