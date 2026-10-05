# Directrices de Arquitectura Backend, Estructura Modular y Gobernanza Técnica

**Sistema:** Plataforma Transaccional "Intégrate"  
**Módulo:** Servidor Central (`backend-api`) e Infraestructura (`infra`)  
**Versión:** 1.0.0 (Línea Base – Fase 0: Fundación Inmutable)  
**Rol:** Arquitecto de Software & Tech Lead Senior  
**Estado:** Activo / Normativo  

---

## 1. Resumen Arquitectónico y Stack Confirmado

A partir de la especificación técnica formal (C4 Model N1–N4, Modelo Relacional v1.0.0 PostgreSQL 16, Plan de Calidad ISO/IEC 25010 y Ficha Técnica Offline IndexedDB), se consolidan las directrices tecnológicas y de diseño estructural del sistema.

### 1.1 Patrón Arquitectónico
- **Arquitectura Hexagonal (Puertos y Adaptadores) / Clean Architecture Modular:** El núcleo de negocio (dominio y casos de uso) se mantiene agnóstico de frameworks, mecanismos de persistencia o servicios de terceros.
- **Desacoplamiento Nivel C4:**
  - **Canal Cliente (N2):** Progressive Web App (PWA) con soporte *Offline-First* estructurada en React, TypeScript y Tailwind CSS; persistencia local gestionada sobre la API IndexedDB (`IntegrateOfflineDB`) para zonas rurales sin conectividad.
  - **Servidor Central (N2/N3):** Monolito modular desacoplado en capas ejecutables mediante Spring Boot REST API, asegurando transaccionalidad ACID local.
  - **Almacenamiento y Persistencia (N2):** PostgreSQL 16 con separación física en 8 esquemas de datos y AWS S3 (o compatible) para la custodia inmutable de comprobantes y vouchers en PDF.

### 1.2 Stack Tecnológico Confirmado
- **Plataforma Backend:** Java 21 (LTS).
- **Framework:** Spring Boot 4.1.x (ADR-0002) (Spring Web MVC, Spring Data JPA / JDBC, Spring Security, Spring Validation).
- **Gestor de Construcción y Dependencias:** Maven (`pom.xml`).
- **Motor de Base de Datos:** PostgreSQL 16.
- **Mecanismo de Versionamiento y Migraciones:** Flyway (Migraciones secuenciales `V1` a `V9`).
- **Mecanismos Criptográficos y de Seguridad:**
  - **Autenticación e Identidad:** JSON Web Tokens (JWT) bajo modelo RBAC y mitigación de fuerza bruta con tabla transaccional de intentos.
  - **Hashing de Credenciales:** BCrypt (coste $\ge 12$) o Argon2id.
  - **Cifrado en Reposo (Habeas Data - Ley 1581 / Ley 1448):** Algoritmo simétrico AES-256 (AES-GCM con rotación de llaves `sensitive_key_version`) para campos sensibles de víctimas y documentos de identidad.
  - **Firma Digital de Vouchers:** Criptografía asimétrica (RSA-2048 / Ed25519) para códigos QR verificables sin conexión vía llaves públicas cacheadas en cliente.

---

## 2. Árbol Completo de Directorios (Formato ASCII)

```text
integrate-system/
├── docs/
│   ├── especificacion_tecnica.md
│   ├── modelo_relacional.md
│   ├── roadmap_ejecucion.md
│   └── architecture/
│       ├── backend_guidelines.md
│       └── offline_data_indexeddb.md
├── infra/
│   ├── docker/
│   │   ├── Dockerfile               # se agrega con T-0.3 (pom.xml)
│   │   └── docker-compose.yml
│   ├── env/
│   │   ├── .env.example             # único .env versionado (SEC-DIR-004)
│   │   └── .env.development         # local, ignorado por Git; producción usa Secrets Manager/SSM
│   ├── localstack/
│   │   └── init/
│   │       └── 01_create_bucket.sh
│   └── postgres/
│       └── init/
│           └── 00_bootstrap.sql
└── backend-api/
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/
        │   │   └── com/
        │   │       └── integrate/
        │   │           ├── common/
        │   │           │   ├── exception/
        │   │           │   │   ├── ErrorCode.java
        │   │           │   │   ├── BusinessRuleException.java
        │   │           │   │   └── GlobalExceptionHandler.java
        │   │           │   ├── payload/
        │   │           │   │   └── InvalidParam.java
        │   │           │   └── web/
        │   │           │       └── TraceIdFilter.java
        │   │           ├── config/
        │   │           │   ├── TimeConfig.java
        │   │           │   └── AsyncJpaAuditConfig.java
        │   │           ├── security/
        │   │           │   ├── SecurityConfig.java              # ADR-0005: resource server, sin filtro JWT propio
        │   │           │   ├── ProblemAuthenticationEntryPoint.java / ProblemAccessDeniedHandler.java
        │   │           │   ├── ActorPropagatingTransactionManager.java   # integrate.actor_id por transacción
        │   │           │   ├── PasswordHashingConfig.java       # Argon2id
        │   │           │   ├── jwt/                             # JwtConfig, JwtAccessTokenIssuer, RsaSigningKeys
        │   │           │   └── crypto/
        │   │           │       ├── AesGcmEncryptor.java
        │   │           │       └── QrDigitalSigner.java
        │   │           └── modules/
        │   │               ├── auth/
        │   │               │   ├── controller/
        │   │               │   ├── dto/
        │   │               │   ├── entity/
        │   │               │   ├── repository/
        │   │               │   └── service/
        │   │               ├── catalog/
        │   │               ├── booking/
        │   │               │   ├── controller/
        │   │               │   ├── dto/
        │   │               │   ├── entity/
        │   │               │   ├── repository/
        │   │               │   ├── service/
        │   │               │   └── worker/
        │   │               ├── payment/
        │   │               ├── voucher/
        │   │               ├── offline/
        │   │               └── integration/
        │   │                   ├── adapter/
        │   │                   │   └── PaymentGatewayAdapter.java
        │   │                   ├── audit/
        │   │                   ├── notification/
        │   │                   └── storage/
        │   │                       └── StatementStoreClient.java
        │   └── resources/
        │       ├── application.yml
        │       ├── application-dev.yml
        │       ├── application-prod.yml
        │       └── db/
        │           └── migration/
        │               ├── V1__init_extensions.sql
        │               ├── V2__auth_schema.sql
        │               ├── V3__catalog_schema.sql
        │               ├── V4__booking_schema.sql
        │               ├── V5__payments_schema.sql
        │               ├── V6__voucher_schema.sql
        │               ├── V7__offline_schema.sql
        │               ├── V8__audit_notification.sql
        │               └── V9__indexes_and_constraints.sql
        └── test/
            └── java/
                └── com/
                    └── integrate/
                        ├── architecture/
                        │   └── ArchitectureTest.java
                        ├── unit/
                        └── integration/
```

---

## 3. Diccionario de Carpetas y Responsabilidades

| Ruta / Carpeta | Qué se sitúa aquí | Responsabilidad técnica / de negocio | Qué está ESTRICTAMENTE PROHIBIDO colocar ahí |
| :--- | :--- | :--- | :--- |
| `docs/` | Especificaciones de negocio, modelos relacionales, diagramas C4, ADRs y normas de calidad. | Custodia formal del diseño, directrices de arquitectura y actas de decisión técnica. | Código fuente ejecutable, scripts de migración activos o binarios compilados. |
| `infra/postgres/init/` | Script `00_bootstrap.sql`. | Aprovisionamiento inicial del clúster de BD, creación de bases de datos, asignación de roles con login y directivas globales de timeout de conexión. | Sentencias DDL de esquemas de negocio, tablas de la aplicación o sentencias DML de prueba. |
| `infra/docker/` | `Dockerfile` y `docker-compose.yml`. | Orquestación de contenedores locales (PostgreSQL 16, LocalStack/S3, red interna de desarrollo). | Secretos productivos codificados en texto plano o dependencias de compilación no containerizadas. |
| `infra/env/` | Plantillas `.env.example` y configuraciones locales segregadas por perfil. | Gestión de variables de entorno para Docker Compose y perfiles de arranque. | Claves criptográficas productivas, certificados SSL privados o contraseñas reales. |
| `backend-api/.../common/` | `GlobalExceptionHandler`, catálogo `ErrorCode`, `BusinessRuleException`, `InvalidParam`, `TraceIdFilter` (ADR-0003), formateadores utilitarios. | Tratamiento unificado de errores HTTP (RFC 9457, sin sobre en respuestas 2xx), correlación `X-Trace-Id` y utilidades puras sin estado. | Lógica de negocio específica de un caso de uso o inyecciones de repositorios JPA de módulos. |
| `backend-api/.../config/` | Clases de configuración de Spring (`@Configuration`), beans de OpenAPI/Swagger, hilos de ejecución asíncrona y auditoría JPA. | Ajuste del contenedor de inversión de control (IoC) y parametrización de beans globales del framework. | Lógica de validación de negocio, controladores REST o consultas directas a base de datos. |
| `backend-api/.../security/` | `SecurityConfig` (resource server de Spring Security, ADR-0005), emisor y decoder JWT (`security/jwt`), manejadores 401/403 RFC 9457, propagación del actor a PostgreSQL, `AesGcmEncryptor`, gestor de firmas QR. | Control de acceso perimetral, RBAC, criptografía de campos en reposo (AES-256) y validación de tokens JWT. | Lógica transaccional de pagos, persistencia de catálogos o controladores expuestos. |
| `backend-api/.../modules/{m}/controller/` | Clases `@RestController` que exponen endpoints HTTP/JSON. | Adaptador primario: deserialización de payloads, validación de esquemas (`@Valid`), autorización declarativa y enrutamiento. | Lógica transaccional de persistencia (`@Transactional`), cálculos contables directos o consultas SQL. |
| `backend-api/.../modules/{m}/dto/` | Registros Java (Records) o POJOs inmutables para Request/Response. | Contratos de intercambio de datos entre la capa web y la lógica del sistema. | Anotaciones de mapeo relacional JPA (`@Entity`, `@Table`) o lógica operativa de negocio. |
| `backend-api/.../modules/{m}/entity/` | Clases POJO anotadas con `@Entity`, `@Table(schema = "...")`, asignación de llaves compuestas e índices. | Mapeo objeto-relacional (ORM) estricto de las tablas definidas en las migraciones Flyway. | Anotaciones de controladores HTTP, transformaciones a DTOs de presentación o acceso a red. |
| `backend-api/.../modules/{m}/repository/` | Interfaces que extienden `JpaRepository` o `CrudRepository` con consultas JPQL/Nativas (`SELECT FOR UPDATE`). | Adaptador secundario de persistencia: abstracción de acceso y operaciones sobre PostgreSQL 16. | Lógica de orquestación de servicios externos, llamadas HTTP o lógica de negocio. |
| `backend-api/.../modules/{m}/service/` | Interfaces de casos de uso e implementaciones `@Service` correspondientes. | Reglas de negocio de Intégrate: split payment 85/15, control de aforos, políticas de cancelación de 72h. | Referencias a `HttpServletRequest`, códigos de estado HTTP directos o sentencias SQL nativas. |
| `backend-api/.../modules/{m}/worker/` | Clases `@Component` con anotaciones `@Scheduled` para tareas asíncronas. | Workers de segundo plano: monitoreo de expiración de bloqueos temporales (TTL 15 min) y procesamiento de colas internas. | Exposición de endpoints REST o interacción directa con la capa de presentación. |
| `backend-api/.../modules/integration/` | Clientes HTTP y adaptadores de infraestructura para servicios externos (`PaymentGatewayAdapter`, `NotificationClient`, `StatementStoreClient`, `AuditEventPublisher`). | Adaptadores secundarios: encapsulamiento de comunicación con pasarela de pagos externa, proveedores de SMS/Email (Twilio/SES) y S3. | Entidades JPA de esquemas de negocio centrales o exposición de APIs directas de presentación. |
| `backend-api/.../resources/db/migration/` | Archivos SQL de migración versionados secuencialmente de `V1__...` a `V9__...`. | Definición inmutable del DDL y DCL: esquemas, extensiones, tablas, restricciones, índices y permisos de base de datos. | Datos volátiles de sesiones, scripts temporales manuales o modificaciones directas sin versionamiento. |
| `backend-api/.../resources/` | `application.yml`, perfiles de entorno (`dev`, `test`, `prod`) y templates de correo/voucher. | Parametrización declarativa del runtime y variables del sistema. | Claves de producción codificadas en duro o contraseñas en texto claro. |
| `backend-api/src/test/` | Pruebas unitarias de servicios, pruebas de integración con testcontainers y pruebas de arquitectura con ArchUnit. | Validación de calidad de software según ISO/IEC 25010: concurrencia, cálculos de comisión y aislamiento. | Mocking excesivo que enmascare comportamientos transaccionales críticos en PostgreSQL. |

---

## 4. Mapeo de Componentes Técnicos y Aislamiento de Esquemas

### 4.1 Ubicación Exacta de Componentes Técnicos
- **Entidades / Modelos de Persistencia:** Ubicadas dentro de `backend-api/.../modules/{modulo}/entity/`. Cada entidad debe explicitar el esquema de BD mediante `@Table(name = "tabla", schema = "esquema_especifico")`.
- **DTOs y Contratos de Entrada/Salida:** Ubicados en `backend-api/.../modules/{modulo}/dto/request/` y `backend-api/.../modules/{modulo}/dto/response/`. Implementados preferiblemente con Records inmutables de Java 21.
- **Lógica de Negocio / Casos de Uso:** Ubicados en `backend-api/.../modules/{modulo}/service/impl/`. Responden a las interfaces definidas en `backend-api/.../modules/{modulo}/service/`, garantizando cumplimiento de los diagramas N3 y CU-01 al CU-20.
- **Interfaces / Puertos:** Definidos como interfaces Java en la raíz de cada paquete de servicio (`service/`) y para llamadas externas en `integration/` (`PaymentGatewayAdapter`, etc.).
- **Adaptadores de Salida (Repositorios e Integración):**
  - Repositorios de datos: `backend-api/.../modules/{modulo}/repository/`.
  - Integración con APIs externas (Pasarela, AWS S3, Notificaciones): `backend-api/.../modules/integration/`.
- **Adaptadores de Entrada (Controladores REST):** Ubicados en `backend-api/.../modules/{modulo}/controller/`.
- **Manejo Global de Errores y Excepciones:** Centralizado en `backend-api/.../common/exception/` bajo un `@RestControllerAdvice` (`GlobalExceptionHandler`) que mapea excepciones de dominio (`BusinessRuleException` + `ErrorCode`) y del framework a `application/problem+json` (RFC 9457) con `code`, `traceId` e `invalidParams`, según ADR-0003. Las respuestas exitosas devuelven el DTO del contrato sin sobre.
- **Seguridad, Autenticación y Autorización:** Centralizado en `backend-api/.../security/` (ADR-0005). La validación del access token la hace el resource server de Spring Security (`BearerTokenAuthenticationFilter` + `NimbusJwtDecoder` RS256); no hay filtro JWT propio. La emisión de tokens es un puerto del módulo `auth` (`AccessTokenIssuer`) implementado en `security/jwt`. RBAC por URL y `@PreAuthorize`.
- **Criptografía (AES-256 y Firmas Digitales):** Ubicado en `backend-api/.../security/crypto/`. `AesGcmEncryptor` gestiona el cifrado/descifrado de atributos de víctimas (Ley 1581 / Ley 1448) y `QrDigitalSigner` la firma asimétrica de vouchers para validación offline.

---

## 5. Reglas de Dependencia y Flujo de Datos

Para salvaguardar la modularidad y prevenir ciclos de dependencia (acoplamiento cruzado), se establecen normas estrictamente acotadas de visibilidad e invocación:

### 5.1 Matriz de Invocación entre Capas
- **Controladores (`controller/`) $\to$** Invocan únicamente a las interfaces de servicio de su propio módulo (`service/`). Queda prohibida la inyección directa de repositorios u otros controladores.
- **Servicios (`service/`) $\to$** Invocan repositorios de su módulo (`repository/`) y puertos de integración (`integration/`). Si un servicio requiere capacidades de otro dominio funcional (ej. el servicio de booking al confirmar pago), debe invocar a la **interfaz de servicio pública** del módulo correspondiente (o publicar un evento de dominio), nunca acceder al repositorio JPA de un esquema foráneo.
- **Repositorios (`repository/`) $\to$** Acceden exclusivamente a las entidades de su respectivo esquema de datos. Queda estrictamente vedado ejecutar operaciones directas `INSERT`/`UPDATE`/`DELETE` hacia tablas ajenas a la frontera de su esquema.

### 5.2 Regla de Oro: Desacoplamiento Escalar de Entidades JPA
Las clases base de entidad (`entity/`) se mantienen completamente desacopladas. **Las relaciones foráneas entre distintos esquemas (ej. `booking_reservations.user_id` $\to$ `auth_users.id`) se mapean en la entidad únicamente a nivel de atributo escalar (`UUID userId`)**, delegando la integridad referencial al DDL de base de datos para no obligar acoplamiento de ciclo de vida entre entidades JPA ni provocar consultas N+1 entre límites de dominio.

---

## 6. Infraestructura y Parámetros de Runtime

### 6.1 Parámetros de Timeout en Clúster (`00_bootstrap.sql`)
Aprovisionado exclusivamente por el superusuario `postgres` una sola vez (el script es la fuente de verdad):
- Rol `integrate_api` (runtime Spring Boot):
  - `lock_timeout = '2s'` (contención acotada del bloqueo pesimista `SELECT ... FOR UPDATE`, R-01).
  - `statement_timeout = '15s'`
  - `idle_in_transaction_session_timeout = '30s'`
  - `default_transaction_isolation = 'read committed'`
- Rol `integrate_reporting`: `default_transaction_read_only = on`, `statement_timeout = '120s'`.
- Creación de roles segregados:
  - `integrate_owner` (LOGIN): propietario de los esquemas, utilizado exclusivamente por Flyway para la ejecución de DDL.
  - `integrate_app` (NOLOGIN): rol de grupo sin privilegios DDL, con permisos DML para el runtime; `integrate_api` (LOGIN) lo hereda.
  - `integrate_readonly` (NOLOGIN): rol de grupo de solo lectura; `integrate_reporting` (LOGIN) lo hereda.
- En local, el script se monta en `/docker-entrypoint-initdb.d/` desde `infra/docker/docker-compose.yml` (ADR-0001).

### 6.2 Parámetros de Pool de Conexiones HikariCP (Perfil `prod`)
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 20000 # 20s
      idle-timeout: 300000       # 5 min
      max-lifetime: 1200000      # 20 min
  flyway:
    schemas: auth,catalog,booking,payments,voucher,offline,audit,notification
    user: integrate_owner
```
