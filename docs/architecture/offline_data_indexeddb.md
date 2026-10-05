# Ficha Técnica y Delimitación del Esquema de Datos Offline en Cliente (IndexedDB)

**Sistema:** Plataforma Transaccional "Intégrate"  
**Módulo:** Almacenamiento Local Desconectado en Cliente PWA  
**Rol Operativo Primario:** Gestor de Enlace Comunitario (Proxy Digital) y Anfitrión Comunitario en Campo  
**Clasificación del Documento:** Especificación Formal de Arquitectura de Datos de Cliente  
**Estado:** Activo / Normativo  

---

## 1. Matriz de Delimitación de Alcance de Datos (Inclusión vs. Exclusión)

La arquitectura *offline-first* para la aplicación web progresiva (PWA) de **Intégrate** delimita de forma estricta los datos que residen en el cliente local. Esta delimitación responde al principio de **minimización de datos** (Ley Estatutaria 1581 de 2012 de Protección de Datos Personales de Colombia y Ley 1448 de 2011), a las restricciones de memoria y cuota en dispositivos móviles rurales de gama baja, y a la imposibilidad técnica de ejecutar lógicas financieras o bloqueos transaccionales pesimistas fuera del servidor central.

| Entidad Backend | Veredicto Local | Justificación Operativa | Justificación Técnica y de Seguridad |
| :--- | :--- | :--- | :--- |
| `auth.users` | **Parcialmente replicada** | Identificación del usuario autenticado en la PWA (Gestor de Enlace / Anfitrión) y visualización del titular del voucher escaneado en campo. | **Seguridad crítica:** Se excluyen hashes de contraseñas (`password_hash`), intentos de inicio de sesión (`login_attempts`), bloqueos y metadatos de auditoría interna de cuentas. Solo se retiene la tupla mínima pública del usuario activo en sesión (`id`, `full_name`, `doc_type`, `doc_number`). |
| `auth.roles` / `auth.user_roles` | **Parcialmente replicada** | Habilitación condicional de vistas e interfaces de trabajo en la PWA según el perfil activo del actor. | Se replican únicamente los códigos de rol vigentes contenidos en las declaraciones del token de sesión para evaluar privilegios en UI. |
| `auth.privacy_policies` / `auth.habeas_data_consents` | **Excluidas** | Operaciones de consentimiento normativo formalizadas previamente durante el registro en línea. | Auditoría pesada e inmutable de cumplimiento legal. Requiere almacenamiento centralizado y sellado de tiempo autoritativo en servidor. |
| `auth.refresh_tokens` | **Excluida** | Renovación de sesiones en línea. | **Riesgo crítico de seguridad:** Almacenar tokens de actualización o credenciales persistentes en IndexedDB expone las identidades a vectores de robo de sesión física o *cross-site scripting* (XSS). |
| `catalog.departments` / `catalog.municipalities` | **Parcialmente replicada** | Filtros geográficos y contextualización territorial de los puntos de encuentro en zonas veredales. | Catálogo estático codificado bajo DIVIPOLA DANE. Se persiste únicamente el subconjunto territorial adscrito a la jurisdicción de la Asociación Comunitaria operada por el Gestor. |
| `catalog.community_associations` | **Parcialmente replicada** | Despliegue de datos de contacto institucional, razón social, NIT y RNT sombrilla. | Se omiten cuentas de dispersión bancaria y auditorías de creación. Solo reside la ficha informativa de la asociación a la que está adscrito el Gestor en sesión. |
| `catalog.host_profiles` | **Parcialmente replicada** | Asociación de experiencias turísticas con el anfitrión responsable en campo. | **Protección de datos sensibles (Ley 1448 de 2011):** Se excluyen terminantemente los atributos cifrados de condición de víctima (`victim_condition_enc`, `ruv_registration_number_enc`, `victimization_type_enc`) y soportes S3. En el cliente solo se mantiene la referencia de contacto operativo y nombre del anfitrión. |
| `catalog.link_manager_profiles` / `catalog.host_manager_assignments` | **Parcialmente replicada** | Verificación de facultades operativas del Gestor para validar vouchers y gestionar experiencias de sus anfitriones asignados. | Solo se almacenan los identificadores vigentes de relación (`manager_id`, `host_id`, `association_id`) para filtrar registros locales. |
| `catalog.categories` / `catalog.experience_categories` | **Persistidas** | Navegación temática y clasificación de experiencias en el catálogo local. | Datos maestros estáticos y livianos con nulo impacto en cuota de almacenamiento. |
| `catalog.experiences` | **Persistidas** | Consulta de itinerarios, puntos de encuentro, veredas, guías de seguridad y recomendaciones técnicas sin red. | Catálogo funcional para la operación asistida en territorio. Se excluyen históricos de moderación y auditorías de plataforma. |
| `catalog.experience_images` | **Parcialmente replicada** | Despliegue visual ligero del catálogo sin saturar la red ni el almacenamiento del terminal. | Solo se persisten metadatos y URL locales resueltas de la imagen de portada (`is_cover = true`). La galería pesada de alta resolución se delega al almacenamiento de objetos en la nube y caché de Service Worker bajo demanda. |
| `catalog.experience_status_history` | **Excluida** | Historial administrativo de moderación del catálogo. | Información de auditoría interna requerida únicamente por el panel administrativo central. |
| `catalog.schedules` | **Parcialmente replicada** | Consulta de fechas, horas de inicio/fin y cronograma de salidas de la jornada. | **Restricción transaccional:** El atributo de aforo dinámico (`available_slots`) requiere sentencias de bloqueo pesimista en base de datos (`SELECT ... FOR UPDATE`), inviables de resolver de forma aislada en IndexedDB sin generar sobreventa (*overbooking*). En el cliente se almacena como itinerario de solo lectura. |
| `booking.bookings` | **Parcialmente replicada** | Padrón de reservas confirmadas vinculadas a la jornada del anfitrión o asociación. | Se persisten únicamente las reservas en estado confirmado vinculadas a los vouchers activos de la jornada. Se excluyen montos financieros, temporizadores transaccionales (TTL 15 min de reservas no pagadas) e historiales de estado. |
| `booking.booking_participants` | **Persistidas** | Verificación nominal en campo de los turistas que conforman el grupo de visita (control de acceso y seguridad). | Necesario para identificar a los asistentes físicos. Los datos médicos altamente sensibles (como `eps_enc`) no se replican en texto plano a IndexedDB para evitar fugas de información personal. |
| `booking.booking_status_history` / `booking.booking_cancellations` | **Excluidas** | Trazabilidad del ciclo de vida y liquidación de penalizaciones por cancelación. | Reglas transaccionales y de compensación ejecutadas exclusivamente por los workers de backend. |
| `payments.*` (Todas las entidades financieras) | **Excluidas** | Pagos en línea, políticas de comisión, pasarelas de pago, reembolsos, dispersiones (*split payment*) y lotes. | **Prohibición de seguridad financiera:** Operaciones financieras críticas no operan bajo supuestos desconectados. Los cálculos de liquidación y dispersión comunitaria (85%/15%) son ejecutados de manera centralizada mediante webhooks de pasarela y workers de dispersión bancaria. |
| `voucher.signing_keys` | **Persistidas** | Validación criptográfica asimétrica de la firma de los códigos QR/vouchers sin consultar el backend. | Almacén exclusivo de las llaves públicas criptográficas (`public_key_pem`, `kid`, `algorithm`) emitidas por la autoridad central. |
| `voucher.vouchers` | **Parcialmente replicada** | Lista de comprobantes de servicio emitidos y lista local de revocaciones (vouchers cancelados antes de la jornada). | Permite el contraste inmediato contra canjes fraudulentos o reservas anuladas previamente en el servidor. |
| `offline.devices` | **Parcialmente replicada** | Identificación del terminal físico autorizado para la captura de eventos. | El cliente almacena su propio identificador de hardware/instalación (`device_identifier`) para estampar los registros de auditoría. |
| `offline.roster_downloads` | **Parcialmente replicada** | Marca de agua y control de frescura de la última descarga del padrón local. | Control interno del cliente para verificar vigencia operativa antes de ingresar a campo desconectado. |
| `offline.sync_batches` | **Parcialmente replicada** | Agrupación e idempotencia en la transmisión de mutaciones acumuladas. | Estructuración del lote transaccional generado por el cliente al reanudar conectividad celular. |
| `offline.offline_validations` | **Persistidas** | Registro transaccional de escaneos y canjes de vouchers efectuados en zonas sin cobertura. | **Núcleo operativo desconectado:** Almacena temporalmente la prueba del escaneo para impedir doble canje local y alimentar la cola de salida hacia el servidor. |
| `offline.sync_incidents` | **Excluida** | Gestión de contingencias y arbitraje manual por parte del administrador de plataforma. | Gestión post-sincronización que opera únicamente en el backend ante fallos de integridad. |
| `audit.*` / `notification.*` | **Excluidas** | Cadenas de bloques hash de auditoría y buzón de salida de mensajería (Gmail/WhatsApp/SMS). | Componentes centrales del servidor. La PWA solo genera telemetría de eventos dentro de su propia cola de sincronización. |

---

## 2. Especificación Arquitectónica de los Object Stores

### 2.1 Identificación de la Base Local
- **Nombre Lógico:** `IntegrateOfflineDB`
- **Versión Arquitectónica Inicial:** `1`
- **Motor Subyacente:** W3C Indexed Database API (IndexedDB)
- **Modo Transaccional Primario:** `readwrite` para operaciones de captura local y `readonly` para consultas de catálogo/padrón.

### 2.2 Estructura de Almacenes Locales (6 Object Stores)
La base de datos `IntegrateOfflineDB` se compone estrictamente de 6 Object Stores especializados:
1. `read_only_catalog`
2. `operational_roster`
3. `revocation_list`
4. `public_signing_keys`
5. `local_validations`
6. `sync_outbox`

---

### 2.3 Fichas Técnicas de los Object Stores

#### A. Object Store: `read_only_catalog`
- **Rol en el Sistema Offline:** Catálogo maestro desnormalizado de solo lectura. Permite al Gestor de Enlace consultar itinerarios, rutas, datos de anfitriones y recomendaciones de seguridad en campo.
- **Estrategia de Llave Primaria (KeyPath):** `id` (Identificador autoritativo UUID provisto por el servidor central).
- **Política de Generación:** Inmutable en cliente. Proviene directamente de la sincronización de bajada (*delta pull*) desde la base relacional.

##### Matriz de Índices
| Nombre del Índice | Ruta del Campo (`keyPath`) | Propiedades | Justificación Funcional |
| :--- | :--- | :--- | :--- |
| `idx_catalog_host` | `hostId` | Directo, No Único | Filtrado de experiencias por anfitrión asignado en territorio. |
| `idx_catalog_municipality` | `municipalityCode` | Directo, No Único | Consultas de oferta por división político-administrativa DANE. |
| `idx_catalog_status` | `status` | Directo, No Único | Visualización exclusiva de experiencias activas y habilitadas comercialmente. |

##### Delimitación de Atributos Persistidos y Desnormalización Funcional
- `id` (UUID): Identificador único de la experiencia turística.
- `hostId` (UUID): Identificador del anfitrión titular.
- `hostName` (String): Desnormalización funcional desde `auth.users` para evitar lecturas cruzadas en cliente.
- `title` (String): Título comercial de la experiencia.
- `description` (String): Descripción narrativa del recorrido y memoria histórica territorial.
- `safetyGuidelines` (String): Directrices de seguridad y normas ambientales del sendero.
- `physicalIntensity` (String): Nivel de exigencia física (`BAJA`, `MEDIA`, `ALTA`).
- `durationMinutes` (Integer): Duración total estimada del recorrido.
- `municipalityCode` (String): Código DIVIPOLA del municipio.
- `municipalityName` (String): Desnormalización funcional de `catalog.municipalities`.
- `vereda` (String): Localización rural específica de la experiencia.
- `meetingPoint` (String): Descripción textual del punto de encuentro.
- `coordinates` (Object): Estructura compuesta `{ latitude: number, longitude: number }` para renderizado en mapa local.
- `maxCapacityPerSlot` (Integer): Capacidad de carga máxima autorizada por turno.
- `unitPriceCop` (Numeric): Valor facial de referencia por cupo en pesos colombianos.
- `coverImageUrl` (String): URL local resuelta o recurso persistido de la imagen de portada.
- `categories` (Array de Strings): Lista desnormalizada de etiquetas temáticas asociadas.
- `schedules` (Array de Objetos): Subdocumento embebido con las franjas horarias programadas para el ciclo operativo activo (`scheduleId`, `startTime`, `endTime`, `status`).

---

#### B. Object Store: `operational_roster`
- **Rol en el Sistema Offline:** Padrón operativo de solo lectura con las reservas formalizadas y autorizadas para la jornada de operación en territorio.
- **Estrategia de Llave Primaria (KeyPath):** `voucherId` (Identificador UUID del voucher emitido).
- **Política de Generación:** Autoridad estricta del backend. Inyectado durante el procedimiento matutino de descarga del padrón (*roster download*).

##### Matriz de Índices
| Nombre del Índice | Ruta del Campo (`keyPath`) | Propiedades | Justificación Funcional |
| :--- | :--- | :--- | :--- |
| `idx_roster_short_code` | `shortCode` | Directo, Único | Contingencia manual: búsqueda inmediata si la cámara del dispositivo falla al leer el QR. |
| `idx_roster_schedule` | `scheduleId` | Directo, No Único | Listado agrupado de asistentes programados por franja horaria. |
| `idx_roster_token_hash` | `tokenSha256` | Directo, Único | Comprobación unívoca del resumen criptográfico del voucher escaneado. |
| `idx_roster_holder_doc` | `holderDocument` | Directo, No Único | Búsqueda por documento de identidad del turista titular en la mesa de control. |

##### Delimitación de Atributos Persistidos y Desnormalización Funcional
- `voucherId` (UUID): Identificador único del voucher.
- `bookingId` (UUID): Identificador de la reserva vinculada.
- `scheduleId` (UUID): Identificador de la franja horaria programada.
- `experienceTitle` (String): Desnormalización funcional del título de la experiencia.
- `shortCode` (String): Código alfanumérico legible de exactamente 8 caracteres sin ambigüedades (`^[A-HJ-NP-Z2-9]{8}$`) para validación asistida.
- `tokenSha256` (String): Hash SHA-256 del cuerpo del voucher para validación de integridad.
- `signingKeyKid` (String): Identificador (`kid`) de la llave pública requerida para verificar la firma.
- `holderName` (String): Nombre del titular de la reserva extraído de `auth.users`.
- `holderDocument` (String): Documento de identidad del titular (`doc_type` + `doc_number`).
- `slotsCount` (Integer): Cantidad total de cupos amparados bajo el voucher.
- `participants` (Array de Objetos): Nómina nominal de viajeros asociados (`fullName`, `docType`, `docNumber`). **Seguridad:** Se omite `eps_enc` para evitar fuga de datos sensibles en el dispositivo.
- `emergencyContact` (Object): Datos desnormalizados de contacto de emergencia extraídos de la reserva para protocolos de rescate en senderos (`name`, `phone`).

---

#### C. Object Store: `revocation_list`
- **Rol en el Sistema Offline:** Lista negra (*deny-list*) de solo lectura que consolida los vouchers revocados por cancelaciones de última hora formalizadas en el servidor antes del ingreso a campo.
- **Estrategia de Llave Primaria (KeyPath):** `voucherId` (UUID del voucher anulado).
- **Política de Generación:** Suministrada por el servidor en la sincronización diferencial del padrón.

##### Matriz de Índices
| Nombre del Índice | Ruta del Campo (`keyPath`) | Propiedades | Justificación Funcional |
| :--- | :--- | :--- | :--- |
| `idx_revocation_short_code` | `shortCode` | Directo, Único | Rechazo inmediato de vouchers cancelados ingresados por contingencia manual. |
| `idx_revocation_timestamp` | `revokedAt` | Directo, No Único | Auditoría temporal de revocación para cálculo de políticas de retención. |

##### Delimitación de Atributos Persistidos
- `voucherId` (UUID): Identificador del voucher invalidado.
- `shortCode` (String): Código corto del voucher.
- `revokedAt` (String ISO-8601): Marca de tiempo del servidor en la que se ejecutó la anulación.
- `revocationReason` (String): Causa técnica o administrativa de la baja de la reserva.

---

#### D. Object Store: `public_signing_keys`
- **Rol en el Sistema Offline:** Almacén local de seguridad que contiene las claves públicas institucionales vigentes para autenticar la criptografía asimétrica de los vouchers.
- **Estrategia de Llave Primaria (KeyPath):** `kid` (Key Identifier - Cadena identificadora del par criptográfico).
- **Política de Generación:** Inmutable. Publicada y distribuida exclusivamente por el backend de Intégrate.

##### Matriz de Índices
| Nombre del Índice | Ruta del Campo (`keyPath`) | Propiedades | Justificación Funcional |
| :--- | :--- | :--- | :--- |
| `idx_key_status` | `status` | Directo, No Único | Restricción para validar únicamente con llaves en estado `ACTIVE`. |

##### Delimitación de Atributos Persistidos
- `kid` (String): Identificador unívoco del par de llaves.
- `algorithm` (String): Algoritmo de firma digital (`RS256` o `Ed25519`).
- `publicKeyPem` (String): Representación en formato PEM de la llave pública.
- `status` (String): Estado de vigencia de la clave (`ACTIVE`, `RETIRED`).
- `validFrom` (String ISO-8601): Inicio de vigencia operativa de la clave.

---

#### E. Object Store: `local_validations`
- **Rol en el Sistema Offline:** Registro de escaneos y canjes ejecutados localmente en campo. Funciona como barrera inmediata contra fraudes por reuso (doble canje) del mismo voucher dentro del radio desconectado.
- **Estrategia de Llave Primaria (KeyPath):** `localValidationId` (UUIDv4 generado de forma autónoma en el cliente).
- **Política de Generación:** Cliente local mediante generador criptográfico aleatorio estándar (`crypto.randomUUID()`) para prevenir colisiones en sincronización distribuida.

##### Matriz de Índices
| Nombre del Índice | Ruta del Campo (`keyPath`) | Propiedades | Justificación Funcional |
| :--- | :--- | :--- | :--- |
| `idx_val_voucher_id` | `voucherId` | Directo, No Único | **Control antifraude:** Consulta rápida previa al canje para validar si el voucher ya fue consumido en este dispositivo. |
| `idx_val_scanned_at` | `scannedAt` | Directo, No Único | Reconstrucción cronológica de los accesos a los senderos ecológicos. |
| `idx_val_sync_status` | `syncStatus` | Directo, No Único | Monitoreo del estado de transferencia hacia el servidor central. |

##### Delimitación de Atributos Persistidos
- `localValidationId` (UUIDv4): Llave local única de la operación de escaneo.
- `voucherId` (UUID): Identificador del voucher validado.
- `shortCode` (String): Código corto verificado en campo.
- `deviceId` (UUID): Identificador del dispositivo del Gestor que efectúa la lectura.
- `validatedBy` (UUID): Identificador del usuario (Gestor) en sesión activa en la PWA.
- `channel` (String): Canal de captura (`OFFLINE_PWA`, `MANUAL_CODE`).
- `localResult` (String): Veredicto local emitido (`VALIDATED_OFFLINE`, `REJECTED_REVOKED`, `REJECTED_ALREADY_CONSUMED`).
- `signatureSha256` (String): Huella hash de la firma criptográfica leída.
- `scannedAt` (String ISO-8601): Marca de tiempo del dispositivo al momento del escaneo físico.
- `syncStatus` (String): Estado de integración (`PENDING`, `SYNCED`, `CONFLICT`).

---

#### F. Object Store: `sync_outbox`
- **Rol en el Sistema Offline:** Cola transaccional de salida de mutaciones encoladas. Garantiza el patrón *Transactional Outbox* en cliente para transferir eventos hacia el backend al restablecerse la red celular.
- **Estrategia de Llave Primaria (KeyPath):** `mutationId` (UUIDv4 generado en cliente).
- **Política de Generación:** Autogenerado en el cliente en la misma transacción atómica en que se escribe el registro funcional local.

##### Matriz de Índices
| Nombre del Índice | Ruta del Campo (`keyPath`) | Propiedades | Justificación Funcional |
| :--- | :--- | :--- | :--- |
| `idx_outbox_order` | `sequenceNumber` | Directo, Único | **Ordenamiento estricto:** Garantiza procesamiento FIFO durante el despacho del lote. |
| `idx_outbox_status` | `status` | Directo, No Único | Despachador de red: filtra registros en estado `QUEUED` o `FAILED_RETRYABLE`. |
| `idx_outbox_aggregate` | `[aggregateType, aggregateId]` | Compuesto, No Único | Localización y conciliación rápida de mutaciones sobre la misma entidad de negocio. |

##### Delimitación de Atributos Persistidos
- `mutationId` (UUIDv4): Identificador unívoco del comando encolado.
- `sequenceNumber` (Integer/BigInt): Consecutivo local autoincremental para preservación de orden.
- `syncBatchId` (UUIDv4): Identificador del lote de sincronización en preparación.
- `aggregateType` (String): Entidad afectada (`OFFLINE_VALIDATION`, `INCIDENT_REPORT`).
- `aggregateId` (UUID): Identificador del registro local afectado.
- `actionType` (String): Verbo operativo (`CREATE_VALIDATION`, `FLAG_SUSPICIOUS`).
- `payload` (Object): Carga de datos completa con la mutación serializada requerida por la API de sincronización.
- `status` (String): Estado operativo (`QUEUED`, `PROCESSING`, `FAILED_RETRYABLE`, `DEAD_LETTER`).
- `retryCount` (Integer): Contador de intentos de envío consumidos.
- `maxRetries` (Integer): Límite máximo de reintentos automáticos permitidos (por defecto 5).
- `lastError` (String): Diagnóstico de respuesta HTTP o causa del fallo de red.
- `createdAt` (String ISO-8601): Marca de tiempo de inserción en la cola.
- `updatedAt` (String ISO-8601): Marca de tiempo de la última evaluación de red.

---

## 3. Protocolo Conceptual de Sincronización y Outbox

### 3.1 Metadatos de Sincronización por Registro
Cada entidad transaccional mutable o de captura que resida en el cliente IndexedDB debe contener de manera obligatoria una sección estandarizada de metadatos para gobernar su ciclo de vida y estado de propagación.

### 3.2 Especificación Conceptual de la Cola de Sincronización (`sync_outbox`)

1. **Orden de Ejecución (FIFO con Dependencias):**
   - El despacho de mutaciones se rige por un ordenamiento estricto sobre `sequenceNumber` ascendente.
   - Ninguna mutación dependiente puede ser transmitida si la mutación base que le dio origen se encuentra en estado fallido o pendiente.

2. **Control de Reintentos y Retroceso Exponencial con Jitter (Backoff):**
   - El despachador de sincronización implementa un esquema de reintentos ante fallas transitorias de red (códigos HTTP 500, 502, 503, 504 o fallas físicas de transporte TCP).
   - La fórmula de temporización aplica retroceso exponencial con variación aleatoria (*jitter*) para prevenir colapsos por tormentas de peticiones simultáneas desde múltiples terminales rurales:
     $$\text{Intervalo} = \min(\text{IntervaloMáximo}, \text{IntervaloBase} \times 2^{\text{retryCount}}) \pm \text{Jitter}$$
   - Al alcanzar el límite operativo (`maxRetries = 5`), la mutación pasa automáticamente a estado `DEAD_LETTER`, bloqueando el lote para evitar corrupción y alertando al Gestor de Enlace en la interfaz.

3. **Garantía de Idempotencia en Servidor:**
   - Cada lote enviado hacia el backend (`POST /api/v1/offline/sync`, máximo 100 validaciones) utiliza el `syncBatchId` (UUIDv4) como clave de idempotencia en la API REST. Si se reenvía el mismo `syncBatchId` con un contenido distinto, el servidor responde 409 y el lote no debe reintentarse (pasa a `DEAD_LETTER`).
   - Cada ítem individual encolado viaja respaldado por su `mutationId` y `localValidationId`. Si el servidor recibe una petición duplicada por cortes de red durante la respuesta, el motor en backend omite la doble inserción en la base de datos relacional y devuelve el resultado previamente computado.

---

### 3.3 Matriz de Políticas de Resolución de Conflictos

En arquitecturas *offline-first* no existe una regla única de resolución de discrepancias. La estrategia depende del impacto financiero, normativo o ambiental del dato de negocio:

| Entidad / Flujo Transaccional | Regla de Resolución Aplicable | Justificación y Mecanismo Operativo en el Negocio |
| :--- | :--- | :--- |
| **Validación de Voucher vs. Revocación Previa en Servidor** | **Prevalencia del Servidor (Server-Wins) con Alerta de Contingencia** | Si un turista canceló formalmente su reserva antes del evento (generando reembolso o retención en el servidor), pero el Gestor validó el voucher en campo porque no descargó la lista de revocación a tiempo, el servidor anula la validez del canje local.<br>**Acción técnica:** El backend rechaza el canje (`server_outcome = REJECTED_REVOKED`). Además, aplica la protección comunitaria de RF-18: la reserva pasa a `SYNC_CONFLICT`, la dispersión pasa de `CANCELLED` a `RELEASED` (el 85% queda intocable) y se registra un incidente HIGH en `offline.sync_incidents` (`incident_type = OVER_REDEMPTION_COMMUNITY_PROTECTED`) para la conciliación del Administrador. |
| **Doble Canje en Campo (Mismo Voucher Escaneado en 2 Terminales Desconectados)** | **First-Write-Wins por `scannedAt` más temprano, con Retención para Arbitraje** | Dos gestores en diferentes puntos de acceso a un sendero ecológico escanean simultáneamente el mismo voucher en modo desconectado.<br>**Acción técnica:** Gana el escaneo con el `scannedAt` más temprano (`server_outcome = ACCEPTED`). Los demás reciben `REJECTED_DUPLICATE` y un incidente `DUPLICATE_OFFLINE_REDEMPTION` para auditoría física y bloqueo de fraude. Una validación ya respondida como `ACCEPTED` no se revierte: si llega más tarde un escaneo con un `scannedAt` anterior, se rechaza y queda en arbitraje. |
| **Reloj del Dispositivo Adelantado o Fecha Incorrecta** | **Validación Temporal en Servidor** | Si `scannedAt > now() + 5 min` → `REJECTED_CLOCK_SKEW` con incidente `CLOCK_SKEW_FUTURE_SCAN`. Si la fecha local (America/Bogota) del escaneo difiere de la `valid_date` del voucher → `REJECTED_OUT_OF_DATE`. La PWA envía `deviceSentAt` en cada lote y recibe `clockSkewSeconds` para corregir su reloj. |
| **Actualización de Ficha de Experiencia (Borrador Local vs. Modificación en Servidor)** | **Versionado Optimista Condicional (Version-Checking)** | Si el Gestor edita notas operativas de la experiencia en campo desconectado mientras el Administrador modificó condiciones comerciales en la web:<br>**Acción técnica:** Se contrasta el `serverVersionTag`. Si el hash de versión del cliente difiere del estado actual en el backend, el servidor deniega la sobrescritura y devuelve el payload del servidor. La PWA preserva los cambios locales en un borrador de conflicto, requiriendo que el Gestor confirme manualmente qué campos conservar. |
| **Catálogo Maestro y Datos DIVIPOLA DANE** | **Sobrescritura Absoluta del Servidor (Server-Authoritative)** | Entidades de solo lectura para el cliente. El servidor siempre tiene la verdad absoluta. Los datos del cliente se purgan y actualizan completamente durante cada barrido de sincronización (*delta sync*). |

---

## 4. Ficha de Políticas Operativas y Ciclo de Vida del Almacenamiento

### 4.1 Política de Cuota y Retención

1. **Garantía de Persistencia en el Navegador:**
   - Por defecto, los motores de navegación modernos (Blink, WebKit, Gecko) gestionan el almacenamiento local bajo una política de desalojo voluntario (*Best-Effort Eviction*), pudiendo purgar bases de datos IndexedDB si el dispositivo se queda sin espacio en disco.
   - La PWA de Intégrate invocará de manera obligatoria y programática la interfaz `StorageManager.persist()` al iniciar la sesión del Gestor de Enlace o Anfitrión.
   - La aplicación verifica periódicamente con `StorageManager.estimate()` el volumen utilizado. Si la persistencia es denegada por el sistema operativo, la PWA despliega una alerta crítica indicando que el dispositivo opera en modo volátil y recomienda liberar espacio en el terminal móvil.

2. **Estimación de Consumo por Volumen de Negocio:**
   La proyección de consumo de almacenamiento local se delimita bajo los parámetros operativos estándar de una Asociación Comunitaria en un horizonte semanal de alta intensidad:

| Entidad / Componente Local | Volumen Proyectado | Peso Promedio por Fila | Consumo Estimado |
| :--- | :--- | :--- | :--- |
| `read_only_catalog` | 50 Experiencias locales | ~8.0 KB (con franjas desnormalizadas y texto) | ~400 KB |
| `operational_roster` | 1,000 Reservas / Vouchers (Semana) | ~3.5 KB (con participantes y contactos) | ~3,500 KB (3.5 MB) |
| `revocation_list` | 200 Vouchers anulados | ~0.5 KB | ~100 KB |
| `public_signing_keys` | 5 Llaves criptográficas | ~2.0 KB | ~10 KB |
| `local_validations` | 1,000 Validaciones locales | ~1.5 KB | ~1,500 KB (1.5 MB) |
| `sync_outbox` | 200 Mutaciones pendientes de despacho | ~2.5 KB | ~500 KB |
| **Índices del Motor B-Tree** | Overhead estructural de IndexedDB | ~30% del volumen total | ~1,800 KB (1.8 MB) |
| **Total Estimado de Almacenamiento** | **Operación Semanal Típica** | — | **~7.8 MB** |

> **Conclusión Técnica:** Un consumo aproximado de **8.0 MB** es plenamente compatible con el límite estipulado para dispositivos móviles rurales de gama baja, asegurando que no se sature la memoria física del equipo y cumpliendo con holgura los requerimientos no funcionales de optimización de recursos.

---

### 4.2 Estrategia de Caducidad y Depuración

1. **Ciclo de Vida de Catálogos y Padrón (*Time-To-Live* Conceptual):**
   - **Catálogo de Experiencias:** TTL estático de 72 horas. Al expirar, la PWA ejecuta un chequeo condicional contra el backend mediante cabeceras `If-Modified-Since` para descargar únicamente novedades.
   - **Padrón Operativo (`operational_roster`):** TTL operativo estricto de 24 horas. Los registros vinculados a fechas de servicio vencidas son purgados automáticamente durante la descarga matutina para evitar sobrecarga y mantener la minimización de datos personales.
   - **Llaves Criptográficas (`public_signing_keys`):** Permanecen indefinidamente hasta que el servidor informe formalmente el estado `RETIRED` o expire el atributo `validFrom`.

2. **Políticas de Vaciado en Cierre de Sesión e Inactividad:**
   - **Cierre de Sesión Seguro (*Logout Guard*):** Antes de destruir el contexto de usuario, el sistema audita el almacén `sync_outbox`. Si existen registros en estado `QUEUED`, `PROCESSING` o `FAILED_RETRYABLE`, **el cierre de sesión se bloquea preventivamente**, advirtiendo al Gestor que perderá asistencias validadas en campo que no han subido al servidor central.
   - **Limpieza Selectiva:** Una vez la cola se encuentra vacía (0 pendientes), el proceso de cierre de sesión purga de forma obligatoria los almacenes con datos sensibles o transaccionales (`operational_roster`, `revocation_list`, `local_validations`). El almacén `read_only_catalog` y `public_signing_keys` puede preservarse para acelerar la carga en aperturas posteriores.
   - **Purga por Inactividad:** Tras 7 días continuos sin conexión ni actividad en un terminal, el Service Worker ejecuta una rutina de limpieza total de IndexedDB para salvaguardar los datos de los turistas amparados bajo la Ley 1581 de 2012.

---

### 4.3 Procedimiento de Versionamiento y Migración

Toda evolución del esquema en el cliente IndexedDB se rige por el evento canónico de actualización de versión (`onupgradeneeded`). Las modificaciones se ejecutarán siguiendo estas pautas estrictas de ingeniería para evitar la pérdida de eventos desconectados no propagados:

1. **Inmutabilidad y No Destrucción:**
   - Queda terminantemente prohibido invocar métodos destructivos masivos (como `deleteDatabase()` o `clear()` ciego) durante las transiciones de versión en dispositivos en campo.
   - Los almacenes que contienen datos de auditoría operativa (`sync_outbox` y `local_validations`) deben preservarse de forma íntegra a través de cualquier versión.

2. **Protocolo para Modificación de Estructuras e Índices:**
   - **Nuevo Índice Requerido:** Se declara el incremento de la versión entera de la base (ej. de v1 a v2). Dentro de la transacción de actualización se verifica la existencia previa con `objectStore.indexNames.contains()` antes de ordenar la creación.
   - **Modificación de Campos en Entidades Existentes:** Se implementan cursores de transformación secuencial. La rutina lee cada registro, mapea las propiedades agregadas con valores predeterminados seguros y persiste de nuevo el objeto sin corromper llaves existentes.
   - **Retiro de un Object Store Deprecado:** Se valida previamente que el almacén a eliminar no contenga registros pendientes de sincronización. Si tiene transacciones huérfanas, los datos se migran automáticamente hacia un almacén de contingencia temporal antes de invocar la remoción formal.

3. **Recuperación ante Errores de Migración:**
   - Si la transacción de actualización falla o se interrumpe (por ejemplo, por descarga súbita de batería del dispositivo móvil en zona rural), el motor IndexedDB revierte automáticamente la base de datos al estado anterior de la versión previa.
   - La PWA atrapa la excepción, mantiene la operatividad en modo seguro de solo lectura y genera una bandera de diagnóstico para que el Gestor de Enlace acuda a una zona con cobertura y reporte la novedad técnica.
