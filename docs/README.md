# Documentación de Intégrate

Índice de toda la documentación del proyecto, agrupada por lo que necesita quien la lee. Para la visión
general y el inicio rápido, vea el [README principal](../README.md).

## Usuarios finales

| Documento | Contenido |
| :--- | :--- |
| [Manual de usuario](manual/README.md) | Uso de la aplicación paso a paso para Turista, Gestor de Enlace, Anfitrión y Administrador ([Markdown](manual/manual-usuario.md) · [HTML](manual/manual-usuario.html)). |

## Desarrollo y operación

| Documento | Contenido |
| :--- | :--- |
| [Guía de desarrollo](guia-desarrollo.md) | Requisitos, variables de entorno, infraestructura, backend, PWA, datos de prueba, pruebas y problemas frecuentes. |
| [Infraestructura local](../infra/README.md) | Servicios de Docker Compose, perfiles y reinicio limpio. |
| [Despliegue de demostración gratuito](despliegue/demo-gratuita.md) | Publicar la demo sin tarjeta: backend en SnapDeploy (o en su PC con Cloudflare Tunnel), Neon, Cloudflare Pages y Backblaze B2 (ADR-0024 a ADR-0026). |
| [Guía: búsqueda de experiencias](guia_despliegue_busqueda_servicio.md) | Despliegue local enfocado en el catálogo y sus filtros. |
| [Pruebas de carga k6](../qa/k6/README.md) | Scripts de RNF-02, RNF-08 y RNF-10 y su ejecución en un entorno aislado. |

## Producto y requerimientos

| Documento | Contenido |
| :--- | :--- |
| [Especificación técnica](especificacion_tecnica.md) | Visión, actores, alcance, RF-01…RF-20 (casos de uso) y RNF-01…RNF-30. |
| [Roadmap de ejecución](roadmap_ejecucion.md) | Cronograma por etapas y dependencias técnicas. |
| [Estado del proyecto](status/PROJECT_STATUS.md) | Fuente de verdad operativa: diagnóstico, etapas y cobertura de módulos. |

## Arquitectura y diseño

| Documento | Contenido |
| :--- | :--- |
| [Guía de backend](architecture/backend_guidelines.md) | Monolito modular hexagonal `com.integrate.*`, reglas entre capas, entidades JPA. |
| [Modelo relacional](modelo_relacional.md) | PostgreSQL 16: esquemas, llaves, triggers y políticas RLS. |
| [Datos offline en IndexedDB](architecture/offline_data_indexeddb.md) | Persistencia `IntegrateOfflineDB` y sincronización diferida (Etapa 2). |
| [OpenAPI 3](api/openapi.yaml) | Contrato de la API REST. |
| [DTOs del contrato](api/dtos_contrato_api.md) | Estructura de los DTOs Java de la API. |

### Decisiones de arquitectura (ADR)

| ADR | Decisión | Estado |
| :--- | :--- | :--- |
| [0001](architecture/adr/ADR-0001-infraestructura-local-docker-compose.md) | Infraestructura local con Docker Compose y variables segregadas por plantilla | Aceptado |
| [0002](architecture/adr/ADR-0002-stack-base-backend-spring-boot.md) | Stack base del backend: Spring Boot 4.1, Java 21, Maven | Aceptado |
| [0003](architecture/adr/ADR-0003-estructura-comun-errores-rfc9457-trazabilidad.md) | Errores RFC 9457, catálogo de códigos y trazabilidad `X-Trace-Id` | Aceptado |
| [0004](architecture/adr/ADR-0004-boilerplate-frontend-pwa-vite-react.md) | Boilerplate de la PWA: Vite 8, React 19, TypeScript 6, Tailwind 4 | Aceptado |
| [0005](architecture/adr/ADR-0005-autenticacion-jwt-rbac-sesiones.md) | Autenticación JWT RS256, refresh rotativo y RBAC | Aceptado |
| [0006](architecture/adr/ADR-0006-catalogo-territorial-experiencias-franjas.md) | Catálogo territorial: experiencias, franjas, moderación y consulta pública | Aceptado |
| [0007](architecture/adr/ADR-0007-enrolamiento-asistido-gestor-enlace.md) | Enrolamiento asistido por el Gestor de Enlace con datos de víctima cifrados | Aceptado |
| [0008](architecture/adr/ADR-0008-reserva-concurrente-bloqueo-pesimista-ttl.md) | Reserva con bloqueo pesimista y worker TTL `SKIP LOCKED` | Aceptado |
| [0009](architecture/adr/ADR-0009-pagos-adaptador-webhook-idempotente-escrow.md) | Adaptador de pagos, webhook firmado e idempotente, escrow del 85 % | Aceptado |
| [0010](architecture/adr/ADR-0010-voucher-criptografico-jwt-qr-pdf.md) | Voucher JWT RS256 en QR y PDF/A con emisión asíncrona | Aceptado |
| [0011](architecture/adr/ADR-0011-notificaciones-outbox-correo-voucher.md) | Correo con *transactional outbox* y despachador SMTP con reintentos | Aceptado |
| [0012](architecture/adr/ADR-0012-shell-movil-catalogo-franjas-pwa.md) | Shell móvil, TanStack Query y flujo de catálogo y franjas | Aceptado |
| [0013](architecture/adr/ADR-0013-sesion-movil-autenticacion-consentimiento-pwa.md) | Sesión móvil: access token en memoria, refresh *single-flight*, consentimiento | Aceptado |
| [0014](architecture/adr/ADR-0014-recuperacion-restablecimiento-contrasena.md) | Recuperación de contraseña con enlace de un solo uso | Aceptado |
| [0015](architecture/adr/ADR-0015-formulario-reserva-movil-pwa.md) | Formulario de reserva con datos de viajeros solo en memoria | Aceptado |
| [0016](architecture/adr/ADR-0016-checkout-movil-ttl-pasarela-pwa.md) | Checkout con cuenta regresiva anclada al servidor | Aceptado |
| [0017](architecture/adr/ADR-0017-confirmacion-voucher-qr-pwa.md) | Confirmación con QR dibujado en el cliente y PDF/A por URL prefirmada | Aceptado |
| [0018](architecture/adr/ADR-0018-enrolamiento-asistido-movil-gestor-pwa.md) | Enrolamiento desde el smartphone del gestor con subida directa a S3 | Aceptado |
| [0019](architecture/adr/ADR-0019-suite-calidad-trazable-plan-gestion-calidad.md) | Suite de calidad trazable a los RNF (ISO/IEC 25010) | Aceptado |
| [0020](architecture/adr/ADR-0020-responsive-movil-y-vistas-escritorio-por-factor-de-forma.md) | Responsive 320–480 px y vistas de escritorio por factor de forma | Aceptado |
| [0021](architecture/adr/ADR-0021-recarga-en-caliente-entorno-desarrollo.md) | Recarga en caliente en desarrollo (DevTools + Vite HMR) | Propuesto |
| [0022](architecture/adr/ADR-0022-datos-de-recorrido-distancia-paradas-ida-vuelta.md) | Distancia, paradas e ida y vuelta del recorrido | Aceptado |
| [0024](architecture/adr/ADR-0024-despliegue-demo-gratuito-neon-koyeb-cloudflare.md) | Despliegue de demostración gratuito: Neon, Cloudflare Pages y B2 (Koyeb reemplazado por ADR-0025) | Aceptado |
| [0025](architecture/adr/ADR-0025-backend-demo-en-pc-propia-con-cloudflare-tunnel.md) | Backend de la demo en un PC propio publicado con Cloudflare Tunnel (`quick` / `named`); plan B de ADR-0026 | Aceptado |
| [0026](architecture/adr/ADR-0026-backend-demo-en-snapdeploy-plan-gratuito.md) | Backend de la demo en SnapDeploy (plan gratuito) con proxy que traduce «despertando» a 503 | Aceptado |
| [0027](architecture/adr/ADR-0027-backend-24-7-en-vps-hetzner.md) | Alojar el backend 24/7 en un VPS de Hetzner Cloud | Propuesto |
| [0028](architecture/adr/ADR-0028-publicacion-manual-usuario-en-pwa.md) | Publicar el manual de usuario como activo estático en la PWA (Cloudflare Pages y Local) | Aceptado |

## Calidad

| Documento | Contenido |
| :--- | :--- |
| [Plan de Gestión de la Calidad v1.10](quality/) | Documento Word del plan (`1.1 Integrate Plan de Gestión de la Calidad del Proyecto v1.10.docx`). |
| [Matriz de trazabilidad RNF](quality/matriz-trazabilidad-rnf.md) | Relación entre cada RNF, su prueba y su evidencia. |
| [Evidencias métricas](quality/evidencias-metricas.md) | Informe generado por la suite (no editar a mano). |

## Reglas del equipo

| Documento | Código | Cuándo aplica |
| :--- | :--- | :--- |
| [Flujo ADR](rules/adr-workflow.md) | — | Antes de cualquier funcionalidad o cambio estructural del backend. |
| [Directiva de frontend](rules/frontend-rules.md) | FE-DIR-001 | Cualquier cambio en la PWA. |
| [Flujo de Git](rules/git-workflow.md) | GIT-WF-001 | Ramas, commits, push y Pull Requests. |
| [Política de secretos](security/security_secrets_policy.md) | SEC-DIR-004 | Código, configuración, commits y GitHub Actions. |
