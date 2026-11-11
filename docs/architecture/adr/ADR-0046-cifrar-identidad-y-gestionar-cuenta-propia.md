# ADR-0046: Completar la protección de identidad y gestionar la cuenta propia

- **Estado:** Aceptado (2026-11-10). Implementado en `pruebas` (commits `89109c8`, `0c43308` y `6503573`); el corte de identidad en base real sigue pendiente.
- **Proyecto / módulo:** Intégrate · autenticación, catálogo, reservas, auditoría y PWA
- **Fecha:** 2026-11-10
- **Trazabilidad:** TejiendoHogar H-01 y edición de perfil de H-09, RNF-01, ADR-0005, ADR-0013, ADR-0014, ADR-0035, ADR-0038, ADR-0039 y ADR-0041
- **Relación:** Detalla la ejecución pendiente de ADR-0039 y propone la edición de la cuenta propia.

## Contexto

La auditoría H-01 exige proteger el documento y el celular de las cuentas y limitar la ubicación pública. ADR-0039 ya decidió el cifrado AES-256-GCM, el índice ciego y la separación de la ubicación exacta. La proyección pública ya redondea coordenadas y omite el punto y las paradas exactas. En el árbol revisado, `auth.users` aún contiene documento y teléfono en claro y la unicidad del documento depende de esas columnas.

H-09 también observa que Mi cuenta no permite modificar nombre, teléfono ni contraseña. La búsqueda administrativa, el alta asistida, los datos antiguos, la anonimización y las sesiones existentes dependen de la identidad actual. Se necesita una transición verificable que preserve esos flujos y permita editar la cuenta propia con el teléfono protegido.

## Decisión

### 1. Inventario y límites de H-01

- Antes de migrar, inventariar columnas, índices, restricciones, vistas, funciones, permisos, semillas, scripts, importaciones, respuestas y consumidores que leen o escriben `auth.users.doc_number` o `auth.users.phone`. Incluir autorregistro, bootstrap, alta asistida, búsqueda administrativa, gestión de anfitriones, comprobaciones de duplicados, anonimización y usuarios activos, pendientes, suspendidos, eliminados y anonimizados.
- Mantener el alcance medible de esta transición sobre documento y celular de `auth.users`. Inventariar por separado otros datos personales, como documentos de participantes y teléfonos de asociación. Registrar su tratamiento y las decisiones pendientes sin contarlos como protegidos por esta migración.
- Completar la verificación de ubicación de ADR-0039. El catálogo solo ofrece referencia general y coordenadas de hasta dos decimales. La proyección pública omite dirección exacta, paradas y enlaces que revelen el punto. La ubicación operativa se entrega por consulta autorizada del recurso para la reserva confirmada y los actores administrativos o gestores autorizados en su ámbito. Las respuestas precisas no admiten caché compartida. Revisar también respuestas, HTML, mapas, registros y otros campos públicos.

### 2. Representación y unicidad

- Reutilizar `SensitiveDataCipher` con AES-256-GCM, una clave de cifrado versionada y AAD que identifique de forma estable la tabla, el campo y el UUID de la cuenta. Generar un nonce seguro para cada cifrado y conservar por registro la versión necesaria para descifrar. La recuperación o rotación de claves debe ser compatible con las copias conservadas conforme al ADR-0041.
- Crear un índice ciego HMAC-SHA-256 del tipo y número de documento con clave independiente del cifrado. Codificar ambos componentes de forma no ambigua y preservar inicialmente la semántica exacta de comparación y unicidad existente, incluidos mayúsculas, minúsculas y estados de cuenta. Probar los límites actuales antes de sustituirlos. El índice y la versión de su clave son datos restringidos y nunca se devuelven al cliente ni se registran.
- La base impone la unicidad del índice ciego durante el corte. El alta concurrente del mismo documento produce una sola cuenta y una respuesta de conflicto para la otra. Una comprobación previa mejora el mensaje y la restricción de base decide la carrera.
- El catálogo de claves se inyecta desde un gestor de secretos, con versiones identificables, permisos mínimos y comprobación de disponibilidad al arrancar. Ninguna clave, índice ciego ni dato en claro se incorpora a migraciones versionadas, logs, trazas o respuestas de error. La clave de búsqueda se rota mediante una columna e índice paralelos, doble cálculo, relleno, verificación de cobertura y unicidad, y corte coordinado. No retirar la versión anterior hasta completar el corte y la política de recuperación.

### 3. Migración y corte por entorno

1. **Preparar.** Tomar y verificar una copia recuperable conforme al ADR-0041. Registrar versión, entorno, responsables, volúmenes y un inventario agregado por estado. Ejecutar antes un ensayo completo en una base aislada con datos sintéticos y una copia representativa autorizada. La migración calcula el índice ciego en la aplicación o en un proceso autorizado que recibe claves fuera de la base y evita imprimir datos. Las migraciones Flyway crean la estructura y las restricciones de forma compatible, sin claves ni valores reales.
2. **Compatibilidad y relleno.** Desplegar una versión que lea registros antiguos y nuevos y escriba de forma coherente ambas representaciones durante la transición. Todas las rutas de escritura, incluidos bootstrap, alta asistida, importaciones y anonimización, deben participar antes de activar el relleno. Procesar en lotes pequeños y reanudables con transacciones por lote y actualización condicionada a la versión de la fila. Un cambio concurrente no puede ser sobrescrito por un lote. Tras una interrupción, volver a ejecutar hasta cubrir las filas faltantes o modificadas.
3. **Verificar antes del corte.** Conciliar conteos por estado, comprobar integridad de descifrado y equivalencia de valores e índices bajo acceso restringido, y detectar colisiones o duplicados sin imprimir los valores. Comprobar concurrentemente alta, búsqueda y cambios de teléfono. Impedir el corte ante errores, discrepancias, claves faltantes o procesos que sigan escribiendo solo en las columnas antiguas.
4. **Cortar.** Coordinar despliegue y barrera de escrituras para evitar versiones antiguas simultáneas, reconciliar de nuevo y activar la lectura y unicidad protegidas. Tras comprobar todos los consumidores, retirar las columnas e índices en claro, ajustar permisos, reportes y funciones de anonimización. No marcar la retirada como completa si una integración aún depende de ellas.
5. **Recuperar.** Documentar una vuelta atrás por etapa. Antes de retirar las columnas, la reversión mantiene las dos representaciones sincronizadas. Después de retirarlas, recuperar mediante el procedimiento aprobado y claves conservadas, con reconciliación de cambios posteriores para no perder altas o ediciones. Ensayar fallo y reanudación, restauración y recuperación antes del corte real. Las copias históricas se mantienen protegidas bajo su retención y acceso, con inventario de las versiones de clave necesarias.

Los alias sintéticos heredados que contienen documentos se inventarían y sustituyen según ADR-0039. La entrega del nuevo identificador se hace por un canal privado, con verificación de ingreso, activación o recuperación según corresponda. Registrar los casos pendientes sin incluir identificadores personales en evidencia pública.

### 4. Cuenta propia

- Agregar operaciones autenticadas para consultar y actualizar nombre y teléfono de la cuenta identificada por el sujeto de sesión. La respuesta de lectura de datos propios entrega únicamente los campos necesarios, con `Cache-Control: no-store`. La actualización valida el nombre y el teléfono E.164 con las reglas existentes, cifra el teléfono en la misma transacción y actualiza la representación compatible mientras dure la migración. No aceptar un identificador de cuenta suministrado por el cliente para elegir otro titular. Auditar la operación y los campos modificados sin guardar valores anteriores o nuevos.
- Incorporar el cambio de contraseña autenticado. Exigir contraseña actual, aplicar la política vigente a la nueva, comprobar su confirmación en la interfaz y usar el codificador existente. La verificación y escritura deben impedir carreras con otro cambio o restablecimiento. La modificación, la invalidación de tokens de recuperación y activación vigentes y la revocación de todos los refresh tokens se realizan de forma consistente. Limitar intentos y registrar el resultado sin contraseñas ni tokens.
- Al confirmar el cambio de contraseña, borrar la cookie de refresco, cerrar la sesión de la pestaña actual y comunicar el cierre a las demás pestañas mediante el mecanismo existente. Solicitar el nuevo ingreso. Los access tokens ya emitidos podrán seguir válidos hasta su expiración si el sistema no ofrece revocación inmediata, con el límite vigente de 15 minutos. Informar ese comportamiento en la evaluación. Respetar el control existente de sincronizaciones offline pendientes antes de realizar la operación, con un resultado explícito y recuperable.
- Exponer nombre, teléfono y cambio de contraseña en Mi cuenta para cada rol autenticado, con validaciones, foco en el primer error, confirmación y mensajes accesibles. Actualizar el estado visible tras guardar y recargarlo desde el servidor tras renovar o volver a ingresar. Usar el cliente HTTP y las reglas de sesión y diseño de FE-DIR-001. La modificación de datos personales de terceros por el administrador queda sujeta a una decisión independiente, conforme a ADR-0043.
- Documentar los nuevos contratos, permisos y errores en OpenAPI, DTO e índice de endpoints. Mantener la compatibilidad de registro, recuperación por correo y activación asistida para cuentas existentes y sintéticas.

### 5. Criterios de aceptación

| Área | Evidencia necesaria |
|---|---|
| Migración y unicidad | Ensayo con datos sintéticos para todos los estados, migración interrumpida y repetida, edición durante el relleno, altas duplicadas concurrentes, colisiones detectadas y corte rechazado ante cualquier discrepancia. La base activa carece de las columnas antiguas tras el corte y la búsqueda por documento conserva su semántica. |
| Cifrado y auditoría | Verificar versión de clave, AAD, fallo por alteración, rotación de cifrado y de índice, acceso de mínimo privilegio y ausencia de valores, índices ciegos y claves en logs y respuestas no autorizadas. Comprobar acceso propio y administrativo autorizado, así como su registro sin valores personales. |
| Ciclo de identidad | Probar autorregistro, bootstrap, enrolamiento asistido, activación, búsqueda administrativa, correos sintéticos y heredados, recuperación y anonimización antes y después del corte. Verificar la transición de alias por titular y registrar casos pendientes. |
| Cuenta propia | En cada rol, consultar y actualizar nombre y teléfono, recargar e ingresar de nuevo. Probar validaciones, acceso sin sesión, intentos sobre otra cuenta y concurrencia con la migración. Para contraseña, probar valor actual incorrecto, política inválida, éxito, carrera con restablecimiento, revocación de refresh y cierre de pestañas. |
| Ubicación | Comprobar sin datos reales la ficha pública, recursos asociados y caché. Visitante, reserva pendiente o cancelada y usuario ajeno no acceden al punto exacto. Titular de reserva confirmada y actores autorizados sí lo obtienen en su ámbito. Verificar navegador y celular. |
| Operación y cierre | Ensayar restauración con claves y datos sintéticos, verificar integridad, permisos y lectura de datos. Ejecutar las pruebas de integración y `mvnw verify`, los cinco controles de FE-DIR-001 y pruebas de accesibilidad, escritorio y celular físico. Regenerar RNF-01 indicando numerador, denominador, entorno, versión y cobertura de columnas y ubicación. Actualizar seguimiento de H-01 y H-09 y conservar evidencia sin datos sensibles. |

H-01 se cierra solo después de verificar el corte, las rutas de acceso, los alias heredados aplicables y la ubicación en la versión de entrega. La parte de cuenta propia de H-09 se cierra con el recorrido completo y la evidencia. El cierre total de H-09 depende también de sus otros criterios.

## Alternativas descartadas

- Proteger únicamente el disco. Una consulta autorizada a la base seguiría devolviendo los campos en claro.
- Usar un hash sin clave para el documento. El espacio de valores permite ensayar documentos y recuperar coincidencias.
- Cambiar todas las columnas en una sola operación sin período de compatibilidad. Un despliegue interrumpido o concurrente podría dejar flujos existentes sin lectura o escritura válida.

## Consecuencias

- La migración exige gestión de claves, coordinación de despliegue y ensayos de recuperación por entorno. La pérdida de una clave necesaria puede impedir leer datos o restaurar copias.
- La edición de Mi cuenta requiere cambios de backend y PWA. El cierre de sesión por cambio de contraseña debe coexistir con el trabajo offline pendiente.
- El índice ciego admite búsqueda exacta y conserva unicidad. Su rotación requiere una transición controlada.
