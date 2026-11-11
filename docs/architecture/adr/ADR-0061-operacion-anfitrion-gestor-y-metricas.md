# ADR-0061: Completar la operación del anfitrión y del gestor de enlace por entregas

- **Estado:** Aceptado (2026-11-11)
- **Proyecto / Módulo:** Intégrate · PWA, catálogo, reservas y operación offline
- **Fecha:** 2026-11-11

## Contexto

El anfitrión puede consultar reservas confirmadas y solicitar individualmente la EPS del titular, pero carece en la PWA de gestión de sus experiencias y franjas, de una agenda de jornada y de métricas. El backend ya ofrece operaciones de autoría y horarios (ADR-0006); la consulta de EPS está sujeta a las restricciones de consentimiento del ADR-0060. El gestor cuenta con enrolamiento, padrón, validación y sincronización (ADR-0018 y ADR-0037), pero necesita una preparación de jornada más clara y mejor orientación cuando falla la conexión o una sincronización.

La operación ocurre en teléfonos, con sol y conectividad intermitente. Se requieren información mínima, autorización por propiedad y jurisdicción, lenguaje comprensible y resultados que distingan un dato ausente de un cero. El alcance colectivo de las métricas no fue definido: se propone empezar solo con las experiencias propias del anfitrión y no exponer resultados de la asociación hasta obtener una decisión expresa.

## Decisión

Organizar la implementación en cuatro entregas revisables, con contratos y pruebas por cada una. La aceptación de este ADR autoriza el diseño descrito, sujeto a las decisiones pendientes indicadas abajo; no convierte en implementadas las capacidades mencionadas. Reutilizar las máquinas de estados y servicios existentes, sin confiar en filtros del cliente para autorización. Antes de añadir o modificar endpoints se concretarán sus contratos, errores, paginación y permisos en OpenAPI y se actualizará el índice de endpoints.

### Entrega 1. Experiencias y agenda del anfitrión

- Incorporar navegación propia hacia **Reservas** y **Mis experiencias**, conservando el acceso a cuenta. La lista de experiencias muestra título, estado y acción disponible. Crear, editar, enviar a revisión y gestionar franjas solo cuando las transiciones del ADR-0006 y la autorización vigente lo permitan. Mostrar el motivo de rechazo únicamente si el contrato autorizado lo proporciona, con una acción para corregir y volver a enviar cuando corresponda. Mantener la validación del punto de encuentro y la moderación existentes; no publicar directamente desde el cliente.
- Presentar próximas jornadas por fecha y experiencia, con hora, cantidad de reservas confirmadas y cupos reservados. El detalle de cada reserva conservará el nombre del titular ya autorizado y añadirá cantidad de personas si el backend la entrega tras comprobar propiedad. La ubicación solo se mostrará según los permisos y decisiones de privacidad vigentes; no ampliar el acceso a nombres de acompañantes, contacto de emergencia ni salud. Mantener la consulta excepcional de EPS individual, sin caché y condicionada al consentimiento del ADR-0060.
- Añadir búsqueda o filtros de próximas/historial, fecha y experiencia a la lista paginada; las consultas y el orden serán estables en el servidor, sin descargar todas las reservas. Explicar carga, vacío, error, reintento y estado de conexión. Sin conexión, no presentar datos antiguos como actuales: inicialmente informar que la consulta requiere red, sin persistir datos de reservas en el dispositivo. Una caché offline del anfitrión requiere decisión posterior sobre datos, caducidad y protección.
- **Aceptación:** un HOST solo administra experiencias y ve reservas propias; transiciones inválidas y recursos ajenos se rechazan; la agenda diferencia vacío de error y conserva los controles de privacidad. Verificar creación, corrección, envío, horarios, filtros y navegación en móvil y escritorio dedicado.

### Entrega 2. Métricas individuales del anfitrión

Añadir **Mis métricas**, agregadas en el backend sobre experiencias pertenecientes al HOST autenticado. Período inicial: mes calendario en `America/Bogota`, identificado en pantalla, con selector de mes e indicación de última actualización. Las fechas de filtro son inicio inclusivo y fin exclusivo; mostrar un máximo acotado de meses para evitar consultas ilimitadas. Los cálculos usan la fecha de inicio de la franja, no la fecha de compra, salvo que la etiqueta indique expresamente otro criterio.

| Indicador | Definición y reglas de presentación |
|---|---|
| Reservas confirmadas | Número de reservas actualmente `CONFIRMED` de franjas que empiezan en el período; cada reserva cuenta una vez. Canceladas y estados no confirmados no se incluyen. |
| Personas reservadas | Suma de cupos reservados de esas reservas; no equivale a asistencia real. |
| Próximas jornadas | Número de franjas futuras no canceladas del período que tengan al menos una reserva confirmada; mostrar también sus cupos reservados. El instante de referencia se obtiene del servidor. |
| Experiencias más reservadas | Hasta cinco experiencias propias ordenadas por número de reservas confirmadas en el período, con desempate estable por identificador. Mostrar reservas y no confundirlas con personas. |

La respuesta será agregada, limitada y sin nombres, documentos ni datos sensibles; el servidor obtiene el perfil del usuario autenticado, sin aceptar un anfitrión arbitrario como parámetro. Responder sin caché persistente y con errores y estado vacío diferenciados. Una reserva de una experiencia ajena nunca debe afectar los resultados. Cero significa consulta exitosa sin registros; un error o una métrica no disponible no se representan como cero.

**Asistencia:** no equiparar el canje de un voucher con asistencia individual sin comprobar la granularidad real. Inicialmente mostrar solo reservas con voucher canjeado y su cobertura, si la fuente de canje autoritativa permite calcularlos sin duplicados; identificar que la sincronización pendiente aún no aparece y que el canje no demuestra asistencia de cada integrante. Si no puede garantizarse, mostrar «Dato aún no disponible» y no calcular porcentaje de personas. Definir con usuarios y responsable funcional la fórmula y la fuente antes de activar métricas de asistencia individual.

**Ingresos y pagos:** quedan fuera de esta entrega. Una fase posterior requiere acordar titular del dinero (anfitrión o asociación), liquidaciones y reembolsos, estados de pago y autorización; nunca presentar ventas o el reparto estimado como dinero efectivamente recibido.

- **Aceptación:** pruebas con dos anfitriones, reservas canceladas, cruces de mes y medianoche Bogotá, empates, cero datos y error; verificar que reservas de terceros no alteren agregados, que cada reserva se cuente una vez y que los canjes pendientes o duplicados no se atribuyan como asistencia individual. Validar etiquetas con usuarios reales.

### Entrega 3. Preparación y resolución de problemas del gestor

- Reorganizar el panel alrededor de la pregunta **«¿Estoy listo para la jornada?»**. Comparar la fecha del padrón con hoy en `America/Bogota`: sin padrón, padrón de otra fecha o padrón de hoy; mostrar fecha y hora de generación y de la última actualización de revocaciones cuando exista un dato confiable. Si se desconoce, decirlo. Un padrón de hoy no garantiza que incluya ventas o revocaciones posteriores: mostrar esa limitación y ofrecer actualización con conexión.
- Mantener visibles conexión, registros pendientes y almacenamiento; distinguir navegador en línea de disponibilidad real del servidor. Priorizar la acción requerida y usar términos comprensibles sin perder precisión. Conservar las garantías de firma, canje local atómico, sincronización idempotente y resolución de conflictos de los ADR-0032, 0033 y 0037.
- En la cola, distinguir pendiente, confirmado, conflicto, rechazo y fallo reintentable; explicar la siguiente acción y cuándo contactar soporte, sin inventar que reintentar resolverá un conflicto. No eliminar ni reenviar automáticamente resultados definitivos sin respetar las reglas existentes. En errores de API, mostrar código de seguimiento disponible y evitar mensajes internos sin traducir.
- Incorporar búsqueda y filtro de activación en **Mis anfitriones**, con alcance siempre limitado a la jurisdicción autorizada y búsqueda/paginación en servidor si el contrato actual no la soporta. Mantener el enmascaramiento y la entrega segura del código de activación.
- **Aceptación:** probar padrón inexistente, ayer/hoy y cambio de día, revocaciones sin fecha, servidor inaccesible pese a conexión, cola pendiente, conflicto y rechazo; comprobar que no se pierden registros ni se altera el comportamiento de canje. Probar búsqueda, paginación y aislamiento entre gestores.

### Entrega 4. Enrolamiento ante pérdida de conexión

Antes de iniciar, explicar que el registro y la política requieren conexión y que los datos no se guardan en el teléfono. Ante una caída, conservar los campos **en memoria mientras la pantalla permanezca abierta**, impedir el envío hasta recuperar conexión y permitir reintento explícito; informar que salir, recargar o cerrar puede perderlos y conservar la advertencia de navegación existente. Revalidar la política, el consentimiento y los datos antes de enviar; evitar registros duplicados tras una respuesta incierta y orientar la comprobación con el servidor antes de otro intento.

No crear borradores persistentes ni encolar datos personales, condición de víctima, soportes o consentimientos offline en esta entrega. Un borrador cifrado, sus llaves, caducidad, eliminación, recuperación y consentimiento necesitan un ADR o enmienda posterior y aprobación del responsable de datos. Las mejoras no deben sugerir que se completó un registro hasta recibir confirmación del servidor.

- **Aceptación:** simular pérdida y recuperación de red antes y durante el envío, recarga y salida; comprobar preservación solo en memoria, aviso de pérdida, ausencia de persistencia local, tratamiento de respuesta incierta y prevención de duplicados.

### Orden y dependencias

1. Entrega 1 establece navegación y operación del anfitrión. Entrega 3 puede trabajarse independientemente, siempre que respete los mecanismos offline existentes.
2. Entrega 2 sigue a la definición de propiedad, fechas y datos de la entrega 1; puede exponerse por separado sin esperar a todas las pantallas de edición.
3. Entrega 4 puede ejecutarse después de la 3 o en paralelo, sin depender de las métricas. Cada entrega es desplegable y verificable por separado; ninguna requiere implementar las extensiones aplazadas.

### Reglas transversales y validación

- Cumplir FE-DIR-001: diseño primero a 320–480 px, controles de al menos 44 × 44 px, separación de 8 px, tokens existentes, contraste y foco certificados, movimiento reducido y vistas de escritorio dedicadas donde corresponda. No añadir fuentes, colores o rediseño global sin aprobación.
- Aplicar las fronteras de capas, cliente HTTP común, errores traducidos y trazabilidad sin datos sensibles. Evitar persistir EPS, información de víctima, documentos o datos de salud en caché. Probar autorización en servidor, consentimiento y ausencia de datos sensibles en logs y respuestas; conservar el control de acceso de EPS del ADR-0060.
- Para cambios de frontend ejecutar los cinco controles de FE-DIR-001: `npm run typecheck`, `npm run lint`, `npm run format:check`, `npm test` y `npm run build`. Ejecutar pruebas de integración de backend y migraciones cuando afecten contratos o datos; verificar con dispositivos o emulación móvil, teclado, lector de pantalla, red intermitente y escritorio dedicado. Registrar los resultados por entrega.

## Alternativas descartadas

- **Un único despliegue para ambos roles:** dificulta revisar permisos, probar estados offline y obtener valor incremental.
- **Calcular métricas en el navegador con todas las reservas:** aumenta transferencia y exposición de datos, y no ofrece agregados correctos con paginación.
- **Métricas de toda la asociación por defecto:** el alcance y la autorización no están acordados; se empieza por datos propios.
- **Guardar el enrolamiento en texto plano para trabajar sin conexión:** expone datos sensibles y no resuelve llaves, caducidad ni consentimiento.
- **Mostrar canjes como asistencia individual o ventas como dinero recibido:** comunica certezas que las fuentes y reglas aún no garantizan.

## Consecuencias

- **Positivas:** el anfitrión puede gestionar su oferta y preparar jornadas; las métricas son entendibles y acotadas; el gestor sabe qué preparar y cómo actuar ante fallos; cada entrega se prueba y revisa por separado.
- **Negativas / riesgos:** consultas agregadas y filtros pueden requerir índices y cambios de contrato; el mes actual cambia conforme se cancelan reservas; el trabajo offline puede reflejarse tarde en el servidor; se requieren pruebas de aislamiento, privacidad y usabilidad en campo. La ausencia de borrador persistente implica que una recarga aún pierde el formulario.

## Decisiones pendientes antes de ampliar alcance

1. Confirmar si las métricas deben incluir alguna vista de asociación y qué roles podrían verla. Hasta entonces solo se muestran métricas propias.
2. Validar con anfitriones la utilidad y definición de los indicadores, en particular el significado de asistencia y los permisos para información de jornada.
3. Acordar con responsables funcionales y de datos si se necesitan métricas de pagos y cómo se atribuyen; redactar la decisión correspondiente antes de mostrarlas.
4. Decidir separadamente si se justifica un borrador offline persistente de enrolamiento y sus garantías de seguridad.

## Contrato conceptual

La PWA solicita al backend una agenda paginada y métricas agregadas del HOST autenticado; el backend determina el perfil y filtra por propiedad antes de consultar reservas y franjas. El gestor consulta exclusivamente sus anfitriones autorizados y continúa usando el padrón y la cola offline existentes. Las rutas y esquemas concretos se definirán y documentarán al implementar cada entrega, preservando compatibilidad y las políticas de privacidad.

## Verificación técnica

| Entrega | Evidencia |
|---|---|
| Experiencias y agenda | Pruebas de rutas y reservas del anfitrión. La integración con PostgreSQL verificó el aislamiento de reservas, el filtro por título y los totales por jornada. |
| Mis métricas | En la PWA local, la cuenta de anfitrión abrió la vista y cambió de noviembre a octubre de 2026. A 360 px, la página conservó una sola opción activa y no tuvo desbordamiento horizontal del documento. La integración con PostgreSQL comprobó totales con datos y aislamiento entre anfitriones. |
| Operación del gestor | En la PWA local, la cuenta de gestor descargó el padrón de hoy y vio su fecha de generación y la última consulta de cancelaciones. La búsqueda sin coincidencias mostró el estado vacío. La integración con PostgreSQL comprobó los filtros por nombre y activación y el aislamiento de jurisdicción. |
| Enrolamiento | A 360 px se comprobó el aviso de conexión y pérdida del borrador. La prueba automatizada simuló un corte durante el envío, verificó que los campos se conservaran en memoria y comprobó que volver a estar en línea no provocara un reenvío automático. |

La validación con anfitriones sigue siendo una actividad de producto. La asistencia individual, los ingresos y el borrador persistente conservan el alcance descrito en este ADR.
