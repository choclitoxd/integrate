# ADR-0040: Publicar la política y exigir consentimiento inicial

- **Estado:** Aceptado (2026-11-09)
- **Proyecto / Módulo:** Intégrate · autenticación, consentimiento y despliegue
- **Fecha:** 2026-11-09
- **Trazabilidad:** Tejiendo Hogar, H-02 · PGCP, RNF-06

## Contexto

La semilla de demostración enlaza un borrador en `integrate.invalid`. El bootstrap del primer administrador crea la cuenta sin registrar consentimiento. La PWA ya dispone de un diálogo de reconsentimiento y la API mantiene versiones de política y constancias de aceptación.

## Decisión

1. Publicar el texto aprobado por el responsable del tratamiento en una URL HTTPS pública y estable. Verificar que se puede leer desde registro, enrolamiento y primer ingreso. Registrar una versión nueva con la huella SHA-256 del artefacto servido y activarla de forma transaccional. Conservar versiones y constancias históricas para poder identificar el contenido aceptado. Usar una política de demostración identificable y datos sintéticos al validar el flujo en pruebas.
2. Mantener la creación técnica del primer administrador y exigir que la persona titular acepte `PERSONAL_DATA` en su primer ingreso. Reutilizar la consulta de política, el diálogo existente y la escritura en el registro de consentimientos. Aplicar la precondición en servidor para operaciones protegidas, incluidas las administrativas, y permitir las operaciones necesarias para consultar la política, aceptar y cerrar sesión. Ante rechazo, cerrar la sesión. Aplicar la misma precondición al administrador existente si le falta la aceptación vigente.
3. Registrar la aceptación con la versión y el canal reales. Para el autorregistro y el enrolamiento asistido, conservar las constancias existentes y comprobar que los flujos muestran la política vigente antes de confirmar. La publicación del documento oficial requiere que el responsable del tratamiento suministre y apruebe el texto.

## Alternativas descartadas

- Dar por aceptada la política en el bootstrap. La creación automática no registra una acción de la persona titular.
- Editar el borrador vigente en su misma versión. Las aceptaciones anteriores perderían su vínculo con el contenido original.

## Consecuencias

- El administrador necesita completar la aceptación para operar. El despliegue debe publicar la política antes de exigir el nuevo control.
- Una política nueva puede requerir reconsentimiento. La API debe devolver un error explícito y recuperable si falta una política vigente.

## Criterios de aceptación

- Verificar URL pública accesible sin sesión y coincidencia de la huella con el contenido publicado.
- Probar autorregistro, enrolamiento asistido y primer ingreso del administrador con aceptación registrada. Probar por API que el administrador pendiente no puede operar aunque se omita la PWA y que el rechazo no crea una constancia de aceptación.
- Regenerar RNF-06 con los tres canales ejecutados. Medir el canal de bootstrap tras la aceptación real.

## Despliegue por etapas

Publicar y verificar la política vigente antes de activar `INTEGRATE_AUTH_ADMIN_CONSENT_ENFORCEMENT_ENABLED`.
El perfil de integración activa el control para probarlo. En los demás entornos se activa de forma explícita
después de confirmar que la URL se puede consultar y que el contenido coincide con el documento aprobado.
