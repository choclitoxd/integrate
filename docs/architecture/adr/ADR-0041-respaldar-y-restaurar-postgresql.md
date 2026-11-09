# ADR-0041: Respaldar y restaurar PostgreSQL fuera del servidor de la API

- **Estado:** Aceptado (2026-11-09)
- **Proyecto / Módulo:** Intégrate · infraestructura y operación
- **Fecha:** 2026-11-09
- **Trazabilidad:** Tejiendo Hogar, H-03 · PGCP, fiabilidad

## Contexto

El despliegue documentado ejecuta la API en Hetzner y mantiene PostgreSQL en Neon. El informe señala que faltan una programación verificable de respaldos, objetivos de recuperación y un simulacro de restauración. Una rama de pruebas conectada al mismo proveedor permite validar cambios, pero no sustituye una copia independiente.

## Decisión

1. Automatizar un `pg_dump` en formato personalizado por entorno, cada 24 horas, contra el endpoint directo de PostgreSQL. Usar una identidad dedicada con acceso de lectura suficiente para el respaldo completo y una versión compatible de las herramientas. Cifrar antes de transferir y conservar 14 copias diarias en almacenamiento privado externo al servidor de la API y a Neon. La identidad del trabajo puede escribir copias, con lectura para restauración y eliminación por retención gestionadas aparte. Las credenciales y las claves de cifrado se inyectan desde el gestor de secretos.
2. Proponer **RPO ≤ 24 h** y **RTO ≤ 4 h** como objetivos iniciales, pendientes de medición. Registrar hora de inicio, resultado y comprobación de integridad. Alertar por fallo, por copia ausente y cuando la copia verificable más reciente supere el RPO.
3. Realizar el primer simulacro en una base aislada con datos sintéticos. Inicializar los roles y extensiones requeridos, verificar la huella del archivo, descifrar en un entorno restringido y restaurar con `pg_restore` deteniéndose ante errores. Aplicar y verificar permisos, arrancar la API aislada con integraciones salientes desactivadas y comprobar acceso por rol, integridad de auditoría y lectura de campos protegidos. Registrar el punto recuperable y cronometrar desde la declaración del incidente simulado hasta la disponibilidad verificada.
4. Documentar qué queda fuera de un volcado de la base. Inventariar roles globales, versiones de claves y objetos referenciados en B2. Verificar su recuperación por separado antes de presentar el respaldo de PostgreSQL como recuperación integral de Intégrate.

## Alternativas descartadas

- Conservar el respaldo solo en el VPS. Una incidencia del servidor podría afectar la API y la copia.
- Usar únicamente la rama de pruebas como copia. Comparte proveedor y su contenido cambia durante las pruebas.

## Consecuencias

- Las copias tienen el mismo nivel de sensibilidad que los datos de origen. Se necesitan responsables para el almacenamiento, las alertas y el acceso de recuperación.
- La restauración requiere un destino nuevo y aislado. La promoción de una base recuperada depende de la verificación y de la autorización del responsable de operación.

## Criterios de aceptación

- Evidencia de una ejecución programada y una alerta simulada, sin exponer credenciales ni datos personales.
- Restauración completa desde una copia externa, con hora del punto recuperado, tiempo transcurrido y resultados de las comprobaciones.
- Runbook con responsable, precondiciones, verificación de permisos, acceso a claves, referencias de B2 y procedimiento de retorno al servicio. Los objetivos quedan como propuestos hasta contar con el resultado del simulacro.
