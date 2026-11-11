# ADR-0051: Automatizar los controles de calidad y proteger las ramas

- **Estado:** Aceptado
- **Proyecto / Módulo:** Intégrate · GitHub Actions, backend y PWA
- **Fecha:** 2026-11-10
- **Hallazgo relacionado:** H-06

## Contexto

El seguimiento de auditoría no encontró workflows locales ni evidencia de protección de ramas. Sin controles repetibles por cambio, un error de compilación, pruebas fallidas o un secreto accidental puede llegar a una rama de entrega sin detección automática. El proyecto ya define controles de backend y cinco comandos de calidad para la PWA, además de una política obligatoria de secretos.

## Decisión

Usar GitHub Actions para ejecutar en cada Pull Request los controles existentes, sin duplicar sus definiciones: `./mvnw verify` para el backend; `typecheck`, `lint`, `format:check`, `test` y `build` para la PWA; y escaneo de secretos. Los jobs deben usar permisos mínimos, no imprimir secretos ni aceptarlos desde forks, fijar acciones a versiones revisadas y publicar resúmenes de fallos sin datos sensibles.

Proteger las ramas de integración y entrega con checks obligatorios, revisión por otra persona, prohibición de force-push y eliminación de ramas obsoletas. La publicación a producción debe tener un entorno protegido y aprobación explícita. La configuración de protección se realiza en GitHub y se registra como evidencia del hallazgo; no se considera satisfecha solo por el YAML.

## Alternativas Descartadas

- **Ejecutar controles manualmente antes de integrar:** no garantiza que cada Pull Request pase los mismos controles.
- **Permitir integrar con checks pendientes:** no evita que ramas protegidas reciban cambios sin validar.

## Consecuencias

- **Positivas:** controles reproducibles, menor probabilidad de integrar regresiones y evidencia verificable para H-06.
- **Negativas / Riesgos asumidos:** ejecuciones más lentas, mantenimiento de acciones y necesidad de adaptar secretos y entornos de despliegue sin exponer credenciales.

## Criterios de aceptación

- Un Pull Request válido ejecuta todos los checks y uno con fallo queda bloqueado.
- Un cambio de prueba que falle en backend, PWA o escaneo de secretos demuestra que el gate bloquea la integración.
- La política de ramas exige checks y revisión; se adjunta captura o exportación de la configuración.
- Un despliegue de producción requiere el entorno protegido y no expone secretos en logs.
- Se valida un Pull Request originado desde fork sin acceso a secretos.

## Configuración administrativa pendiente en GitHub

La protección de ramas y los revisores de entornos son ajustes del repositorio, no quedan aplicados por los archivos de workflow. Para `main` y `develop`, exigir PR y una aprobación de otra persona, impedir force-push y eliminación, y marcar como requeridos estos checks: `Backend · Maven verify`, `PWA · typecheck`, `PWA · lint`, `PWA · format:check`, `PWA · test`, `PWA · build` y `Gitleaks`. Crear el entorno `production` con aprobación de un revisor antes del despliegue. Adjuntar evidencia de esos ajustes para cerrar H-06.

