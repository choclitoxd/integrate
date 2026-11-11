# Respaldo y recuperación de PostgreSQL

Operación de ADR-0041. El responsable de infraestructura configura el almacenamiento y el temporizador. El responsable de recuperación conserva la identidad privada de cifrado y ejecuta el simulacro. RPO ≤ 24 horas y RTO ≤ 4 horas son objetivos hasta medirlos.

## Programar el respaldo

1. Preparar un ejecutor Linux con `pg_dump` compatible con el servidor, `age`, AWS CLI, `curl` y `sha256sum`. Usar un bucket privado externo a Neon, con prefijos separados para pruebas y producción. Dar al ejecutor permiso de escritura sobre su prefijo. Una identidad separada descarga copias y administra su retención. Configurar una regla que expire el archivo y su manifiesto juntos tras 14 días, incluidas versiones no vigentes si se habilita versionado.
2. Crear la cuenta de sistema `integrate-backup` y ubicar el checkout en `/opt/integrate`. Configurar las rutas y valores no sensibles en `/etc/integrate/backup/<entorno>.conf`. Inyectar las credenciales desde el gestor de secretos como archivos de acceso restringido al usuario del servicio.
3. Definir `PGSERVICEFILE` y `PGPASSFILE`. Crear la entrada libpq `integrate-backup-pruebas` o `integrate-backup-produccion` para el endpoint directo, con TLS verificable y una cuenta que pueda leer todas las tablas, secuencias y objetos incluidos en el respaldo. Verificar el acceso en pruebas con datos sintéticos antes de programar la copia.
4. Definir `BACKUP_BUCKET` y `BACKUP_AGE_RECIPIENT`. Si el destino es compatible con S3 y tiene endpoint propio, definir `BACKUP_S3_ENDPOINT` como HTTPS. Inyectar las credenciales de almacenamiento mediante su mecanismo estándar. Mantener la identidad privada de age en el gestor de secretos de recuperación.
5. Definir `BACKUP_NOTIFY_OK_CONFIG` y `BACKUP_NOTIFY_FAIL_CONFIG` como rutas a configuraciones privadas de curl con los destinos de éxito y fallo del monitor. Configurar una alerta si pasan 24 horas desde el último éxito y comprobar que el aviso llega al responsable de guardia. El manifiesto se publica tras el archivo cifrado. Una copia sin manifiesto se considera incompleta.
6. Instalar las unidades de `infra/systemd`. Ejecutar primero la unidad `integrate-postgres-backup@pruebas.service` y revisar `backup_published` en el journal. Habilitar `integrate-postgres-backup@pruebas.timer` y verificar su próxima ejecución. Simular una falla en pruebas, revisar `backup_failed` y comprobar la alerta. Repetir la configuración de producción tras validar el simulacro.

## Simulacro de restauración

1. Registrar la hora de declaración del incidente y seleccionar un par de archivos con el mismo sello UTC. Descargar ambos con la identidad de recuperación en un entorno aislado. Para el primer simulacro, usar un respaldo de datos sintéticos.
2. Preparar una instancia PostgreSQL compatible accesible por socket Unix. Aprovisionar los roles y extensiones requeridos por el volcado. Crear desde `template0` una base vacía con prefijo `integrate_restore_`. Mantener desactivados el correo, pagos, jobs que produzcan efectos externos y conexiones a servicios de producción.
3. Montar un directorio privado de memoria o cifrado para `TMPDIR` y recuperar temporalmente la identidad age. El script comprueba la huella del archivo cifrado, descifra la copia y restaura en una sola transacción. Usar el nombre real de la copia y su manifiesto.

   ```bash
   bash infra/scripts/restore-postgres.sh COPIA.dump.age COPIA.sha256 IDENTIDAD_AGE /var/run/postgresql integrate_restore_simulacro
   ```

4. Verificar permisos con las cuentas restauradas, integridad de auditoría y lectura autorizada de campos protegidos. Verificar por separado las versiones de claves necesarias y la disponibilidad de los objetos referenciados en B2. Arrancar la API aislada y validar los flujos por rol antes de registrar la recuperación como disponible.
5. Registrar el sello del respaldo, pérdida de datos estimada, duración medida, pruebas y anomalías. Retirar la copia local y la identidad temporal conforme al procedimiento del equipo. La decisión de promover una base recuperada corresponde al responsable de operación tras estas comprobaciones.

## Evidencia local del 9 de noviembre de 2026

`infra/scripts/test-postgres-recovery.sh` ejecuta el recorrido en PostgreSQL 16.6 desechable con datos sintéticos y cifrado age. Recuperó la fila de control. También rechazó una copia alterada y un destino ocupado, y emitió la señal esperada ante un fallo de `pg_dump`. El simulacro sustituye el almacenamiento y el monitor por dobles locales. El ejercicio de recuperación integral y los objetivos RPO/RTO requieren un respaldo en el destino externo y la validación de la API con sus permisos.
