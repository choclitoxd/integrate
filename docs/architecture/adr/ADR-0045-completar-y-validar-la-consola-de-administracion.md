# ADR-0045: Completar y validar la consola de administración

- **Estado:** Aceptado
- **Proyecto / Módulo:** Intégrate · administración, operación offline, pagos y auditoría
- **Fecha:** 2026-11-10
- **Trazabilidad:** TejiendoHogar H-09, ADR-0033, ADR-0034, ADR-0035, ADR-0042, ADR-0043 y ADR-0044

## Contexto

La revisión de la consola en `pruebas` con una cuenta QA confirmó el ingreso y la lectura en Resumen, Experiencias, Asociaciones, Personas, Auditoría y Mi cuenta. Incidentes y Liquidaciones carecen de pantalla. En Chrome con móvil emulado a 390 px, Personas y Auditoría ensancharon el documento a 614 y 909 px, respectivamente. Mi cuenta quedó fuera del área visible inicial de la barra. Las 44 pruebas enfocadas registradas cubren parte del comportamiento. Falta verificar mutaciones, acceso con otros roles y uso en un teléfono físico. [Evidencia de auditoría](../../quality/seguimiento-auditoria-tejiendohogar.md#revisión-de-la-consola-administrativa-en-pruebas).

El ADR-0043 ya define Incidentes y Liquidaciones. El ADR-0044 integra la consola en AppShell y habilita la interfaz móvil. Quedan por definir la navegación con todos los módulos y la consulta de estado de una dispersión. El puerto actual `PayoutGateway` expone el envío del pago y carece de esa consulta. [ADR-0043](ADR-0043-consola-de-administracion-fase-2-personas-incidentes-y-liquidaciones.md), [ADR-0044](ADR-0044-integrar-administracion-en-appshell-con-identidad-verde.md), [puerto de dispersión](../../../backend-api/src/main/java/com/integrate/modules/payment/gateway/PayoutGateway.java).

## Decisión

### 1. Incidentes de sincronización

- Incorporar `/admin/incidentes` con la lista paginada y los filtros por estado, severidad y tipo previstos por el ADR-0043 §2. Mostrar fecha, impacto estimado e identificadores de reserva y voucher, con una ficha que permita identificar el caso sin exponer datos personales del turista.
- Permitir tomar un caso abierto, y resolver o descartar un caso en revisión. Resolver y descartar requieren una nota no vacía y confirmación del caso. Guardar quién realizó el cierre y la hora de la base de datos. Registrar la toma y el cierre en la bitácora según el ADR-0043.
- Aplicar el cambio de estado y el registro de auditoría en la misma transacción, con control del estado anterior. Ante una transición incompatible o concurrente, devolver `409` y refrescar la ficha. El cierre registra la decisión operativa y preserva reembolsos, reservas y dispersiones.

### 2. Liquidaciones

- Incorporar `/admin/liquidaciones` con lista paginada por asociación y estado, ficha con importes, comisiones, neto, dispersiones, fechas y motivo de fallo, y acceso al comprobante existente cuando corresponda. Autorizar su descarga temporal contra el lote.
- Agregar al puerto de pagos una **consulta de estado** separada del envío. Correlacionar el lote por el identificador de la pasarela o por la referencia estable de la operación cuando el proveedor permita buscarla. La conciliación de un lote `SUBMITTED` confirma `PAID`, registra `FAILED` con motivo o lo deja pendiente según la respuesta. Si el proveedor carece de consulta o la referencia no es verificable, mantener el estado e informar que la conciliación no está disponible.
- Consultar la pasarela fuera de la transacción de base de datos. Al aplicar el resultado, volver a comprobar el estado y coordinar la transición con el procesador automático. Reutilizar el cierre y la emisión del comprobante, con la unicidad por lote y la idempotencia existentes. Una consulta fallida deja el lote recuperable. Una conciliación repetida sobre un lote pagado conserva su resultado y su comprobante. Auditar los resultados conforme al ADR-0043 §3. El reintento de lotes fallidos sigue fuera del alcance según ese ADR.

### 3. Resumen, navegación y vistas

- Agregar accesos y contadores de **Incidentes abiertos** y **Lotes por conciliar** en Resumen. Mostrar los incidentes de severidad alta dentro del conteo y enlazar a la lista filtrada correspondiente. Los lotes que requieren revisión incluyen `SUBMITTED` y `FAILED`, con la acción de conciliar habilitada para los enviados.
- Mantener AppShell, la identidad verde y la navegación exclusiva de administración, incluida Mi cuenta. En móvil, usar tres destinos visibles sin desplazamiento de la barra: **Resumen, Módulos y Mi cuenta**. Módulos abre un menú accesible con Experiencias, Asociaciones, Personas, Incidentes, Liquidaciones y Auditoría. En escritorio, mantener la navegación horizontal con Resumen y Mi cuenta accesibles. Agrupar módulos cuando el ancho disponible no alcance para mostrarlos completos.
- Limitar el desplazamiento horizontal a los contenedores de tablas, incluidas las de Personas y Auditoría. La cabecera, el menú, los filtros y las acciones deben caber en la ventana. Mantener foco visible, operación por teclado, estado activo y áreas táctiles conforme a FE-DIR-001. Usar el mismo alcance funcional en escritorio y móvil.
- Conservar las guardas del ADR-0044. Las cuentas con `ROLE_ADMIN` y `ROLE_TOURIST` mantienen la navegación administrativa y son redirigidas al panel desde las rutas de turista previstas. Proteger las operaciones administrativas en el servidor con `ROLE_ADMIN`. Registrar el contrato de los endpoints nuevos en OpenAPI y en el índice de endpoints. Actualizar FE-DIR-001 y el guion por rol para retirar la referencia al aviso móvil anterior.

### 4. Evaluación y criterio de cierre

Ejecutar la siguiente matriz contra una versión identificada, con cuentas y datos de QA. Registrar en `docs/quality/evaluacion-administrador.md` el entorno, la versión, el dispositivo, los pasos, lo esperado, lo observado, el estado y la evidencia. Una causa se registra como confirmada solo si puede reproducirse y verificarse.

| Caso | Aceptación |
|---|---|
| Acceso y sesión | El administrador llega a Resumen, conserva su menú en Mi cuenta y puede recargar y cerrar sesión. Repetir la apertura directa y la recarga por separado, dado el resultado irregular de la auditoría. La API devuelve `401` sin sesión y `403` con una cuenta autenticada que carece de `ROLE_ADMIN`. |
| Incidentes | Generar un conflicto de sincronización con datos de QA, encontrarlo por filtro, tomarlo y resolverlo con nota. Probar también descarte, nota vacía y dos acciones concurrentes sobre el mismo caso. Verificar el estado persistido, el actor, la hora, el evento y la ausencia de efectos financieros. |
| Liquidaciones | Probar con el simulador un lote pagado, uno rechazado, uno pendiente y una consulta indisponible. Conciliar dos veces y simular concurrencia con el procesador automático. Verificar que no se envíe un segundo pago, que exista como máximo un comprobante y que se registre el resultado. Identificar la evidencia como «verificado con simulador» cuando aplique. |
| Personas | Buscar por correo o documento exactos, verificar el enmascaramiento y el evento de acceso, convertir en anfitrión y acreditar, suspender, reactivar y revocar a un gestor. Tras renovar o volver a iniciar sesión, el gestor revocado pierde el acceso. Verificar los errores de acciones incompatibles. |
| Asociaciones y Experiencias | Crear una asociación y comprobar la respuesta ante NIT duplicado. Moderar una experiencia de QA, verificar su visibilidad en el catálogo cuando sea elegible y comprobar que una transición inválida conserva el estado anterior. |
| Auditoría | Filtrar por los eventos generados, comprobar actor, fecha y resultado, exportar un CSV con esos filtros y verificar la integridad de la bitácora. |
| Interfaz y navegación | Revisar en Chrome de escritorio y celular físico. Medir a 320, 360, 390, 412 y 430 px con datos que llenen las tablas. Verificar que la página no se ensanche, que los desbordamientos queden dentro de las tablas y que Resumen, Módulos y Mi cuenta estén visibles. Comprobar teclado, foco, cierre del menú y accesibilidad con axe. |

Ejecutar las pruebas de integración y `mvnw verify` con su compuerta de cobertura. Ejecutar los cinco controles de FE-DIR-001 en la PWA: tipos, lint, formato, pruebas y build. Actualizar el seguimiento de H-09 con evidencia del tramo administrativo. El cierre de H-09 completo requiere también resolver o documentar los flujos de anfitrión, cancelación, edición de perfil y actualización de Mis experiencias tras el pago.

## Alternativas descartadas

- Usar el envío idempotente para averiguar el estado del pago. Si la pasarela aún desconoce la referencia, el envío podría iniciar una dispersión.
- Añadir cada módulo a la barra móvil. El menú ya deja Mi cuenta fuera de la vista inicial con seis entradas.

## Consecuencias

- Se entregará primero Incidentes, después Liquidaciones y luego la evaluación integral. La corrección de navegación y ancho puede avanzar en paralelo.
- La conciliación real depende de la capacidad de consulta del proveedor. El simulador permite probar las transiciones y el manejo de errores.
- La verificación requiere datos sintéticos que produzcan conflictos offline y lotes en distintos estados, además de sesiones independientes por rol.
