# ADR-0053: Observar la disponibilidad y los fallos de la aplicación

- **Estado:** Aceptado
- **Proyecto / Módulo:** Intégrate · backend, PWA y operación
- **Fecha:** 2026-11-10
- **Hallazgo relacionado:** H-10

## Contexto

El seguimiento no encontró evidencia suficiente de comprobaciones de disponibilidad, alertas ni registro operativo de incidentes. Los logs existentes ayudan a investigar solicitudes, pero no prueban que una caída se detecte ni que llegue a una persona responsable. La observabilidad debe evitar exponer información personal, tokens, datos de salud o secretos.

## Decisión

Separar la comprobación pública de disponibilidad del diagnóstico interno. Publicar un endpoint de salud acotado que indique disponibilidad sin revelar dependencias, versiones, configuración ni datos sensibles; mantener diagnósticos profundos en un canal restringido. Correlacionar solicitudes mediante el identificador de trazabilidad existente y emitir logs estructurados con evento, resultado, duración y contexto técnico mínimo, excluyendo cuerpos, credenciales y datos personales.

Configurar un monitor externo para detectar caída y recuperación, con alerta a un canal operativo definido por el equipo. Registrar incidentes con hora, impacto, versión, causa confirmada o hipótesis y recuperación. Probar el mecanismo en `pruebas`; no generar una alarma real de producción como parte de la aceptación sin una ventana acordada.

## Alternativas Descartadas

- **Usar solo logs locales:** no detecta una caída cuando el proceso deja de registrar ni avisa a responsables.
- **Exponer el diagnóstico completo públicamente:** entrega información innecesaria sobre infraestructura y dependencias.
- **Registrar solicitudes completas para facilitar diagnóstico:** aumenta el riesgo de almacenar secretos o información personal.

## Consecuencias

- **Positivas:** detección verificable, investigación correlacionada y registro de fallos con menor exposición de datos.
- **Negativas / Riesgos asumidos:** el monitor y las alertas requieren mantenimiento, responsables y límites para evitar falsas alarmas.

## Criterios de aceptación

- La comprobación pública no expone secretos, estado detallado de dependencias ni información personal.
- Una caída sintética en `pruebas` genera alerta y la recuperación cierra o actualiza la alerta.
- Una solicitud puede seguirse por su identificador de trazabilidad sin registrar tokens, contraseñas ni datos personales.
- Se documenta un incidente de prueba con impacto, cronología y recuperación.

## Implementación local

- Actuator publica únicamente `GET /api/v1/health`; los componentes y detalles se mantienen ocultos.
- Los logs de consola se emiten en ECS JSON. Los fallos HTTP 5xx registran método, ruta normalizada, estado, duración y el `traceId` del MDC. No se incluyen query, cuerpo, ruta cruda ni mensaje de excepción.
- El contrato OpenAPI y el índice de endpoints documentan la ruta pública.
- El runbook documenta cómo configurar el monitor externo y registrar incidentes.

## Pendiente de aceptación operativa

En `pruebas` aún se debe configurar el monitor y su destinatario, simular indisponibilidad, comprobar alerta y recuperación, y anexar el registro del incidente de ensayo. H-10 permanece abierto hasta contar con esa evidencia.

