# ADR-0054: Unificar la solicitud de información de salud en la aplicación

- **Estado:** Aceptado
- **Proyecto / Módulo:** Intégrate · PWA, registro y voucher
- **Fecha:** 2026-11-10
- **Hallazgo relacionado:** H-14

## Contexto

La auditoría reporta mensajes contradictorios sobre la autorización relacionada con salud y brechas de legibilidad en textos y PDF. No se debe inferir ni prometer una exención médica desde la interfaz sin una regla aprobada. La información de salud es sensible; la aplicación debe solicitar solo lo necesario para operar y explicar con claridad el propósito y el uso.

## Decisión

Usar una única fuente versionada para el texto que explica qué información de salud se solicita, por qué y cómo se utiliza, y reutilizarla de forma coherente en registro y voucher. Hasta que el responsable del producto apruebe el contenido y la regla aplicable, la interfaz no afirmará que una autorización médica es necesaria o innecesaria. No solicitar diagnósticos ni detalles clínicos libres si no existe una necesidad funcional aprobada; cuando la operación requiera conocer una condición logística, limitarse al dato mínimo y explicar su propósito antes de enviarlo.

Los textos deben ser legibles en móvil, compatibles con lector de pantalla y coherentes entre flujo, confirmación y PDF. La aceptación, cuando corresponda, debe registrar la versión del texto y el momento, conforme al mecanismo existente de consentimiento; no guardar el contenido clínico en logs ni en analítica.

## Alternativas Descartadas

- **Mantener textos independientes en cada pantalla:** permite que el registro y el voucher comuniquen reglas incompatibles.
- **Recolectar información clínica libre por defecto:** excede la minimización sin una necesidad documentada.
- **Afirmar una regla de autorización no aprobada:** puede inducir a error y no resuelve la discrepancia identificada.

## Consecuencias

- **Positivas:** mensajes coherentes, menor recolección de datos y una regla explícita para validar RNF-15 y RNF-23.
- **Negativas / Riesgos asumidos:** el cierre queda condicionado a que el responsable apruebe el texto y la regla de negocio; las funciones que dependan de estos datos deben validar el cambio.

## Criterios de aceptación

- La regla y el texto visible están aprobados por el responsable designado y se versionan.
- Registro, revisión previa, confirmación y voucher muestran la misma regla, en lenguaje claro y legible en móvil.
- Se verifica que no se solicitan campos clínicos no justificados y que no aparecen en logs ni analítica.
- Las pruebas funcionales cubren los casos en que la información sea necesaria y cuando no lo sea, sin bloquear injustificadamente el registro o la reserva.

## Revisión inicial y decisiones pendientes

- El registro ofrece el consentimiento de salud como opcional; el flujo de reserva solicita autorización y la API exige `HEALTH_EMERGENCY` para bloquear cupos. La reserva pide EPS por viajero y contacto de emergencia.
- El texto actual de finalidades menciona la póliza de turismo y la atención de emergencias. El PDF del voucher muestra un aviso general de datos personales, sin reproducir esa explicación ni incluir EPS o contacto de emergencia.
- El desarrollador aceptó como base solicitar la EPS voluntariamente, pedir un contacto de emergencia y no solicitar diagnósticos ni certificados médicos por defecto. La autorización de datos sensibles debe ser expresa e informada; no se debe condicionar la reserva a que el viajero suministre datos sensibles.
- El proyecto aún no tiene aseguradora definida. La necesidad de EPS para una póliza y su finalidad quedan pendientes de confirmar cuando se elija una; no deben presentarse como hechos en la interfaz. El texto propuesto y el tratamiento del contacto deben revisarse frente a la política de privacidad y con el responsable de protección de datos antes del cierre.
- La implementación deberá conciliar esta regla con la precondición actual `HEALTH_EMERGENCY`, el contrato de reservas, la persistencia y el consentimiento de cada participante. H-14 permanece abierto hasta verificar el flujo completo.

## Implementación en curso

- El formulario permite reservar sin EPS; cuando se proporciona, solo admite la del titular autenticado y exige consentimiento vigente. La EPS de acompañantes se rechaza porque el consentimiento del titular no acredita el de terceros.
- La captura y la consulta al anfitrión están desactivadas por defecto hasta configurar una versión de política publicada y aprobada para esa finalidad, conforme a [ADR-0060](ADR-0060-consulta-eps-titular-por-anfitrion.md). No hay aseguradora definida.
- La PWA reutiliza el texto de la finalidad en los puntos de visualización; el PDF tiene actualmente una copia equivalente. Falta consolidar una fuente única versionada para ambos y publicar una política que describa el destinatario, la finalidad, la conservación y la revocación. La versión de consentimiento actualmente registrada es la de la política, no una versión independiente del texto.
- La revisión de protección de datos, las comprobaciones de accesibilidad en dispositivos y la evidencia del flujo desplegado siguen pendientes. No activar la propiedad de política aprobada hasta cerrar esas decisiones.
- El manual de usuario (Markdown y las tres copias HTML publicadas) ya describe la EPS como voluntaria, dada solo por el titular y consultable por el anfitrión en una emergencia (2026-11-11).
- Cierre técnico (2026-11-11): `ConsentService.currentHealthGrant` acredita el acto de salud vigente con el aviso aprobado de la política vigente; el otorgamiento (`/me/consents`) y el registro validan y guardan `healthNoticeVersion` frente al aviso aprobado, y un acto de salud sin aviso no se reutiliza cuando se pide uno. `PublishedPolicy` expone el aviso aprobado (nulo si no hay) y la PWA solo ofrece la EPS cuando existe. Cubierto por `ConsentHealthNoticeTest`; las pruebas de integración con Postgres no se han ejecutado (sin Docker).
- Sigue abierto, y no es código: aprobación del texto por el responsable, publicación del aviso con `approved_for_host` mediante migración o procedimiento administrativo, y consolidar el texto del PDF del voucher (copia equivalente) en la misma fuente versionada.

