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
2. **Estabilización del Core Transaccional (MVP Fase 1):** Antes de abordar la complejidad de sincronización desconectada (*offline-first*), se consolida y prueba bajo estrés el flujo transaccional directo: autenticación, bloqueo pesimista de aforo (`SELECT ... FOR UPDATE`), retención de cupos con TTL (15 min), simulación de pagos con split payment (85/15) y emisión del voucher criptográfico.
3. **Resiliencia Territorial Desconectada (Fase 2):** Sobre el core transaccional ya estabilizado se construye la capacidad *offline* en el cliente mediante Service Workers, IndexedDB y validación de firma asimétrica en campo.
4. **Protección Comunitaria Determinista (Fase 3):** Los mecanismos de sincronización en segundo plano y resolución de colisiones implementan como regla inviolable la salvaguarda de los fondos (85%) para las comunidades campesinas y víctimas.
5. **Calidad, Cumplimiento Legal y Producción (Fase 4):** Hardening de seguridad, auditoría inmutable de Habeas Data (Ley 1581), accesibilidad WCAG 2.1 AA / NTC 5854 y despliegue cloud en AWS.

---

## 2. Cronograma de Fases Secuenciales de Desarrollo

El desarrollo se articula en **Dos Grandes Etapas Operativas** trazadas directamente con los tres procesos de negocio transaccionales del Plan de Calidad de Software:

```mermaid
gantt
    title Roadmap de Ejecución Técnica - Intégrate (2 Grandes Etapas Operativas)
    dateFormat  YYYY-MM-DD
    section Etapa 1: Proc 1 y 2 (Identidades & Core Transaccional)
    Fase 0 - Setup & DB (Infraestructura Base) :f0_1, 2026-10-05, 14d
    Fase 1 - Proceso 1: Auth, RBAC & Habeas Data :f1_1, after f0_1, 7d
    Fase 1 - Proceso 2: Catálogo & Aforo Ecológico :f1_2, after f1_1, 7d
    Fase 1 - Proceso 2: Bloqueo Pesimista & TTL   :f1_3, after f1_2, 7d
    Fase 1 - Proceso 2: Checkout, Pago & Voucher QR :f1_4, after f1_3, 7d
    section Etapa 2: Proc 3 (Validación Offline & Dispersión)
    Fase 2 - Proceso 3: PWA Offline & IndexedDB   :f2_1, after f1_4, 7d
    Fase 2 - Proceso 3: Escaneo QR Asimétrico     :f2_2, after f2_1, 7d
    Fase 3 - Proceso 3: Background Sync & Idempotencia :f3_1, after f2_2, 7d
    Fase 3 - Proceso 3: Split Payment 85/15 & 72h :f3_2, after f3_1, 7d
    Fase 4 - Cierre: Auditoría, Estrés & AWS Cloud :f4_1, after f3_2, 7d
```

---

## 3. Desglose Detallado de Tareas por Etapa y Dependencia Técnica

> La numeración de tareas es la de `docs/status/PROJECT_STATUS.md` (fuente de verdad operativa). Los ADR y los commits citan estos mismos identificadores.

---

### ETAPA 1: Núcleo de Identidades, Reserva Concurrente y Checkout Transaccional (Procesos 1 y 2)

#### FASE 0: Entorno Base, Infraestructura y Persistencia (Semanas 1-2) - [Completada]
*Objetivo:* Establecer el entorno de desarrollo reproducible, el pipeline de integración continua y el esquema de base de datos PostgreSQL con migraciones versionadas y políticas de seguridad.

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

#### FASE 1: Core Transaccional - Procesos 1 y 2 (Semanas 3-5)
*Objetivo:* Implementar el flujo transaccional directo de punta a punta: desde la autenticación y registro asistido (Proceso 1), hasta el bloqueo pesimista de aforo (`SELECT ... FOR UPDATE`), cobro electrónico en pasarela con retención en custodia y emisión del voucher criptográfico PDF/QR (Proceso 2).

| ID Tarea | Nombre de la Tarea | Dependencias Técnicas | Desbloquea |
| :--- | :--- | :--- | :--- |
| **T-1.1** | **[Proceso 1] Dominio de Identidad & RBAC:** Entidades, repositorios y servicios de autenticación, sesiones rotativas y consentimiento (ADR-0005). | T-0.5, T-0.6 | T-1.2 |
| **T-1.2** | **[Proceso 1] Endpoints de Sesión:** login/registro/refresh/logout y `/me/consents`, JWT stateless RS256 con claims de rol y Spring Security Resource Server (ADR-0005). | T-1.1 | T-1.3, T-1.4 |
| **T-1.3** | **[Proceso 1] Enrolamiento Asistido & Consentimientos:** Registro asistido de anfitriones por el Gestor de Enlace y captura inmutable de Habeas Data (Ley 1581). | T-1.2, T-1.4 | T-2.2 |
| **T-1.4** | **[Proceso 2] Catálogo Territorial & Parametrización de Franjas:** CRUD de experiencias, integración DIVIPOLA y franjas horarias con tope de aforo ecológico (ADR-0006). | T-0.5, T-1.2 | T-1.5 |
| **T-1.5** | **[Proceso 2] Motor de Concurrencia y Bloqueo Pesimista (`SELECT FOR UPDATE`):** Servicio `BookingConcurrencyEngine` con transacción de bloqueo a nivel de fila (<200 ms), descuento de cupos y estado `PENDING`. | T-1.4 | T-1.6, T-1.7 |
| **T-1.6** | **[Proceso 2] Booking Expiration Worker (TTL 15 min):** Tarea asíncrona `@Scheduled` que audita periódicamente reservas `PENDING` vencidas y reintegra cupos a la franja horaria. | T-1.5 | T-1.7 |
| **T-1.7** | **[Proceso 2] Adaptador de Pagos Desacoplado & Webhook de Cobro:** Implementación de `PaymentGatewayAdapter` con procesamiento en COP, recepción idempotente de webhooks y retención de fondos (*escrow*). | T-1.5 | T-1.8 |
| **T-1.8** | **[Proceso 2] Generador de Voucher Criptográfico & PDF/QR:** Servicio que firma el payload JWT con clave privada RSA-2048, genera código QR de alta densidad y renderiza PDF/A. | T-1.7 | T-1.9, T-2.1 |
| **T-1.9** | **[Proceso 2] Despacho de Notificación Transaccional:** Servicio de correo electrónico (Gmail API / SMTP) que despacha el comprobante y voucher oficial al turista. | T-1.8 | T-1.10 |
| **T-1.10** | **[Proceso 2] Frontend PWA Móvil (Portal Turista):** Catálogo en tarjetas verticales, selector táctil de franjas, formulario de reserva (contacto/EPS) y checkout con sticky bottom bar (smartphone 360–430 px). | T-0.7, T-1.2, T-1.4, T-1.5, T-1.7 | Fin Etapa 1 |

---

### ETAPA 2: Validación Territorial Offline-First y Dispersión Financiera (Proceso 3)

#### FASE 2: PWA Offline-First y Validación Territorial (Semanas 6-7)
*Objetivo:* Dotar a la PWA de capacidades de funcionamiento 100% desconectado para que el Gestor de Enlace pueda descargar padrones locales y verificar vouchers mediante criptografía asimétrica en zonas rurales sin cobertura.

| ID Tarea | Nombre de la Tarea | Dependencias Técnicas | Desbloquea |
| :--- | :--- | :--- | :--- |
| **T-2.1** | **[Proceso 3] Infraestructura Offline en Cliente PWA:** Service Workers con Workbox para caché de assets estáticos y esquema IndexedDB (`IntegrateOfflineDB`). | T-0.7, T-1.8 | T-2.3 |
| **T-2.2** | **[Proceso 3] Endpoints de Descarga Preventiva:** Padrón de vouchers de la jornada y lista delta de revocación (`modules/offline/`). | T-1.3, T-1.8 | T-2.3 |
| **T-2.3** | **[Proceso 3] Módulo de Descarga Preventiva en PWA:** Padrón local, lista de revocación y clave pública RSA del servidor en IndexedDB. | T-2.1, T-2.2 | T-2.4 |
| **T-2.4** | **[Proceso 3] Lector QR y Motor de Validación Asimétrica Offline:** Escáner con cámara trasera y Web Crypto API que valida firma digital, expiración y consistencia contra IndexedDB. | T-2.3 | T-2.5 |
| **T-2.5** | **[Proceso 3] Marcación Local Anti-Doble Canje (RNF-18):** Transición inmediata del voucher a `CONSUMIDO_LOCAL` en IndexedDB para impedir reutilizaciones offline. | T-2.4 | T-2.7 |
| **T-2.6** | **[Proceso 3] Canal Web de Contingencia:** Ingreso y validación manual del código alfanumérico ante avería o descarga del dispositivo móvil en territorio. | T-2.2 | T-2.8 |

---

#### FASE 3: Sincronización Diferida y Dispersión Financiera - Split Payment (Semanas 8-9)
*Objetivo:* Habilitar la sincronización en segundo plano con resolución determinista de colisiones y ejecutar la dispersión bancaria exacta hacia las comunidades campesinas y anfitriones.

| ID Tarea | Nombre de la Tarea | Dependencias Técnicas | Desbloquea |
| :--- | :--- | :--- | :--- |
| **T-2.7** | **[Proceso 3] Cola de Eventos Diferidos & Background Sync:** Encola las asistencias registradas localmente y las despacha al recuperar conectividad celular. | T-2.5 | T-2.8 |
| **T-2.8** | **[Proceso 3] Endpoint de Sincronización Idempotente:** `POST /api/v1/offline/sync` procesa lotes con clave de idempotencia y actualiza reservas a `EXECUTED`. | T-2.7, T-2.6, T-1.5 | T-2.9 |
| **T-2.9** | **[Proceso 3] Motor de Resolución Determinista de Conflictos:** Salvaguarda el 85% de la comunidad si un turista cancelado consume el servicio, derivando la alerta al Admin. | T-2.8, T-1.7 | T-2.10, T-2.11 |
| **T-2.10** | **[Proceso 3] Dispersión Financiera (Split Payment 85/15):** Órdenes de dispersión en COP hacia la Asociación/Anfitrión (85%) y la plataforma (15%), con dispersión supletoria a las 24 horas. | T-2.8, T-2.9 | T-2.12 |
| **T-2.11** | **[Proceso 3] Políticas de Cancelación y Reembolso (72h):** Si $\Delta t > 72\text{ h}$ reembolsa 85% al turista; si $\le 72\text{ h}$ o No-Show transfiere el 85% a la Asociación. | T-1.7, T-2.9 | T-2.12 |

---

#### FASE 4: Auditoría Legal, Hardening, Pruebas de Carga y Cloud (Semana 10)
*Objetivo:* Implementar la auditoría estricta de Habeas Data, asegurar la accesibilidad AA, realizar pruebas de estrés de aforo concurrente y desplegar la solución en infraestructura AWS.

| ID Tarea | Nombre de la Tarea | Dependencias Técnicas | Desbloquea |
| :--- | :--- | :--- | :--- |
| **T-2.12** | **Auditoría Inmutable Habeas Data (Ley 1581 / Ley 1448) & Cifrado AES-256:** Bitácora inmutable de accesos sensibles y cifrado a nivel de columna. Panel de auditoría para Admin. | T-2.10, T-2.11 | T-2.14 |
| **T-2.13** | **Pruebas de Estrés y Accesibilidad:** 200 usuarios concurrentes por los últimos 2 cupos (0% sobreventa, < 200 ms) y auditoría WCAG 2.1 AA / NTC 5854 en la PWA móvil. | T-1.5, T-1.6, T-2.4 | T-2.14 |
| **T-2.14** | **Pipeline CI/CD y Despliegue en AWS:** ECS Fargate, RDS PostgreSQL, certificados SSL/TLS y bucket S3 para vouchers. | T-2.12, T-2.13 | Puesta en Producción |

---

## 4. Criterios de "Done" (Definition of Done - DoD) por Etapa

Cada etapa se cierra formalmente cuando se cumplen los entregables de todas sus fases.

### ETAPA 1 (Procesos 1 y 2)

#### DoD Fase 0: Setup & Base de Datos
- [x] Contenedores Docker de PostgreSQL y LocalStack arrancan de forma limpia con `docker-compose up`.
- [x] Todas las migraciones de Flyway se aplican sin errores y se genera el esquema relacional con sus restricciones de integridad (`CHECK`, `FOREIGN KEY`).
- [x] Los paquetes del backend están 100% aislados bajo `com.integrate.*` sin ninguna referencia o dependencia a nombres del dominio de comidas (*FoodExpress*).
- [ ] Pipeline de GitHub Actions compila el código y ejecuta análisis estático con SonarQube (0 vulnerabilidades críticas).

#### DoD Fase 1: Core Transaccional (Procesos 1 y 2)
- [ ] El turista puede autenticarse, consultar experiencias y solicitar el bloqueo de cupos.
- [ ] El Gestor de Enlace puede enrolar anfitriones con consentimiento asistido de Habeas Data.
- [ ] Prueba de concurrencia unitaria/integrada valida que el bloqueo pesimista (`SELECT ... FOR UPDATE`) no permite sobreventa bajo hilos paralelos (0 colisiones).
- [ ] Las reservas abandonadas se cancelan automáticamente tras 15 minutos exactos y los cupos se liberan en el inventario.
- [ ] El webhook de pago se procesa de forma idempotente y deja los fondos en custodia (*escrow*); la dispersión 85/15 se certifica en la Etapa 2.
- [ ] El voucher criptográfico se emite con firma digital válida y el PDF queda almacenado en S3 y enviado al correo del comprador.
- [ ] La PWA móvil permite al turista recorrer catálogo, franjas, reserva y checkout en un smartphone (360–430 px) sin scroll horizontal.

### ETAPA 2 (Proceso 3)

#### DoD Fase 2: PWA Offline-First y Validación Territorial
- [ ] La PWA se instala correctamente en dispositivos móviles (Android/iOS) y funciona en modo avión (desconectado).
- [ ] El Gestor de Enlace puede descargar con un clic el padrón de reservas y la clave pública institucional.
- [ ] El escáner QR de la PWA valida matemáticamente la autenticidad del voucher sin conexión celular.
- [ ] Todo voucher escaneado se almacena como `CONSUMIDO_LOCAL` en IndexedDB e impide inmediatamente su reuso local.

#### DoD Fase 3: Sincronización Diferida y Políticas Financieras
- [ ] Al recuperar la conexión a internet, las asistencias encoladas se sincronizan automáticamente con el servidor en segundo plano.
- [ ] Ante un conflicto de sincronización por cancelación previa, el sistema ejecuta la resolución determinista: no descuenta el 85% de la comunidad y crea la incidencia contable para el Administrador.
- [ ] La dispersión calcula y ejecuta de forma comprobada el 85% comunidad / 15% plataforma.
- [ ] La regla de cancelación de 72 horas ejecuta con precisión matemática los reembolsos del 85% (>72h) o la retención total a favor del anfitrión ($\le$72h).
- [ ] El Gestor de Enlace puede crear y gestionar experiencias a nombre de anfitriones locales.

#### DoD Fase 4: Auditoría, Hardening y Cloud
- [ ] Tabla de consentimientos de Habeas Data es inmutable (sin permisos de modificación ni eliminación) y los datos sensibles están cifrados con AES-256.
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
