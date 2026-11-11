# ADR-0060: Restringir la consulta de EPS al anfitrión de la experiencia

- **Estado:** Aceptado
- **Proyecto / Módulo:** Intégrate · reservas, catálogo y privacidad
- **Fecha:** 2026-11-10

## Contexto

El responsable solicita que el anfitrión pueda consultar la EPS voluntariamente suministrada por el titular de una reserva para una emergencia. La EPS es un dato sensible y el consentimiento de salud existente debe informar esta finalidad y sus destinatarios; la reserva sigue siendo posible sin EPS.

La API todavía no ofrece una consulta de EPS al anfitrión. La asignación a la experiencia se determina a través de la franja y el perfil anfitrión, y la EPS se conserva cifrada por participante.

## Decisión

Habilitar un acceso autenticado para el rol HOST exclusivamente a la EPS del titular de una reserva `CONFIRMED` cuya experiencia pertenece al anfitrión solicitante. La respuesta devolverá únicamente la EPS o `null` cuando esté ausente, con `Cache-Control: no-store`; se responderá sin revelar la existencia de reservas no autorizadas o no confirmadas. No se devolverán datos de acompañantes ni contacto de emergencia.

Antes de descifrar se verificará la propiedad y el estado de la reserva. Cada consulta autorizada quedará auditada con actor, reserva y momento, sin registrar la EPS ni otra información clínica. Se mantendrá el cifrado en reposo y se aplicará el consentimiento informado vigente para esta finalidad, incluida la comunicación al anfitrión. Si el texto o mecanismo vigente no cubre esa comunicación, no se habilitará la divulgación hasta contar con la política y aceptación correspondientes.

La condición de emergencia es la finalidad declarada del acceso: una petición autenticada por sí sola no permite verificar que exista una emergencia real. El diseño deberá dejar explícita esa limitación y las medidas operativas que acuerde el responsable de datos.

El anfitrión necesita una pantalla propia para localizar reservas confirmadas de sus experiencias: el listado mostrará solo los datos mínimos necesarios para identificar la reserva y la franja, sin EPS. Una acción individual de consulta mostrará la EPS en un diálogo accesible, sin guardarla en el dispositivo. Se definirán el alcance temporal y los datos visibles junto con el responsable de protección de datos.

## Alternativas descartadas

- **Entregar EPS en el listado general de reservas:** amplía la exposición y evita auditar cada consulta específica.
- **Permitir consulta por cualquier anfitrión:** no limita el acceso a quien opera la experiencia.
- **Exigir EPS para reservar:** contradice su carácter voluntario y la minimización de datos.

## Consecuencias

- **Positivas:** acceso delimitado a la experiencia y reserva, con trazabilidad y respuesta mínima.
- **Negativas / Riesgos asumidos:** la API no puede comprobar por sí sola la emergencia; se requiere gobierno del consentimiento y supervisión de accesos. La revocación y el alcance temporal deben respetar las reglas vigentes de privacidad.

## Contrato conceptual

`GET /api/v1/host/bookings/{bookingId}/holder-eps`: HOST propietario, reserva confirmada, consentimiento aplicable; respuesta `200 { "medicalEps": string | null }`, sin caché. Otros roles se rechazan; recurso inexistente, ajeno o fuera del estado autorizado no revela su existencia. Las pruebas deberán cubrir autorización, estados, ausencia, consentimiento, cifrado y auditoría sin datos sensibles.

## Configuración pendiente para divulgación

La captura y divulgación de EPS están bloqueadas hasta que el consentimiento acredite el aviso exacto aprobado para comunicación al anfitrión. Configurar una versión de política no habilita el acceso ni constituye aprobación o evidencia del aviso; la verificación se hace en la propia consulta: la reserva debe estar ligada al acto de salud vigente del titular, que a su vez registra el aviso aprobado de la política vigente (`ConsentService.currentHealthGrant`, 2026-11-11). Una revocación, una nueva política o un aviso no aprobado cierran el acceso. Falta publicar un aviso aprobado y validar con pruebas de integración en Postgres, que aún no se han ejecutado.
