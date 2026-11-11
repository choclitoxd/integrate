# Evidencia del ensayo del corte de identidad (ADR-0046) y simulacro de restauración (ADR-0041)

| Campo | Valor |
|---|---|
| Fecha | 2026-11-10 |
| Tipo | Ensayo local con datos sintéticos, no productivo |
| Base | PostgreSQL 16.6 (Alpine) en contenedor descartable, puerto local, base `integrate_ensayo` |
| Aplicación | `backend-api` 0.1.0-SNAPSHOT, commit `89109c8` más la corrección de `IdentityMigration` descrita en §6 |
| Esquema | Flyway: 17 migraciones aplicadas, versión `v17` |
| Credenciales | Solo de prueba, generadas para el ensayo y no registradas aquí |

## 1. Conteos agregados

| Medida | Resultado |
|---|---|
| Filas en `auth.users` | 1 001 (1 000 sintéticas, 1 de arranque) |
| Estados | 981 `ACTIVE`, 20 `SUSPENDED` |
| Filas con documento protegido (`doc_number_enc`) | 1 001 / 1 001 (100 %) |
| Filas con celular protegido (`phone_enc`) | 1 001 / 1 001 (100 %) |
| Filas con índice ciego de documento | 1 001 / 1 001 (100 %) |
| Duplicados detectados por el índice | 0 en el relleno. Son 0 porque la restricción previa ya impedía duplicados exactos. |
| Filas rechazadas en la verificación | 0 |
| Filas verificadas (`VERIFY`) | 1 001 (981 + 20, conciliadas con el total) |
| Rechazo de duplicado tras el corte | Un `INSERT` con el mismo `document_index` fue rechazado por `uq_users_document_index` |

## 2. Modos de escritura

| Paso | Modo | Resultado |
|---|---|---|
| Migraciones con modo `LEGACY` | `LEGACY` | Arranque correcto, 17 migraciones |
| Relleno (`BACKFILL`) | `DUAL` | 1 001 filas procesadas, cobertura completa, 12 s incluido el arranque |
| Verificación (`VERIFY`) | `DUAL` | Correcta tras la corrección de §6, 14 s incluido el arranque |
| Corte (`adr0046-cut-legacy-user-identity.sql`) | — | `COMMIT` correcto |
| Arranque en modo protegido | `PROTECTED` | Arranque correcto en 11 s |

Después del corte, `auth.users` no tiene columnas `doc_number` ni `phone` en claro. Consultado en `information_schema.columns`: 0 columnas.

## 3. Semillas

| Archivo | Prueba | Resultado |
|---|---|---|
| `infra/postgres/seed/dev_test_data.sql` | Sin `-v allow_legacy_identity_seed=1` | Bloqueada. Nada se insertó (1 001 filas antes y después). |
| `import_testers.sql` (copia local, ignorada por Git) | Sin la bandera | Bloqueada. Nada se insertó. |

El guardia de `import_testers.sql` existe solo en la copia local de trabajo, porque ese archivo está en `.gitignore`.

## 4. Permisos

| Verificación | Resultado |
|---|---|
| Rol `integrate_readonly` con `SELECT` sobre `doc_number_enc` o `phone_enc` | No (`false` en ambos) |
| Rol `integrate_readonly` con `SELECT` sobre la tabla `auth.users` completa | No (`false`). Solo tiene privilegios por columna sobre datos no sensibles. |

## 5. Bitácora de accesos sensibles

| Medida | Resultado |
|---|---|
| Filas `SENSITIVE_DATA_ACCESSED` con finalidad `IDENTITY_READ` | 2 002 (dos por usuario en la verificación: documento y celular) |
| `audit.verify_chain()` | 0 filas, es decir, sin rupturas en la cadena |

## 6. Hallazgos del ensayo

1. **Verificación en transacción de solo lectura (corregido, sin commit).** La primera verificación falló con «Identity operation failed at the database». `IdentityMigration.verify` y `verifyRotation` eran `readOnly = true`, pero ahora descifran y registran cada lectura, y esa escritura falla en una transacción de solo lectura. Se quitó `readOnly` y se mantuvo `REPEATABLE READ`. Las pruebas `IdentityProtectionIntegrationTest` (3), `IdentityCutPostgresTest` (1) y `AuthApiIntegrationTest` (20) pasaron después del cambio. El cambio está en el árbol de trabajo y no en un commit.
2. **Rotación de la llave de cifrado no es operable con la configuración actual.** `application.yml` solo mapea la versión 1 de `SENSITIVE_DATA_KEY`, así que al declarar la versión 2 el arranque falla con «the key for the current version 2 is missing». Hace falta mapear las versiones adicionales. No se ejecutó `ROTATE` ni `VERIFY_ROTATION`.
3. **Rotación de la llave del índice no ejecutada.** Se requiere `adr0046-promote-next-user-index.sql` y una llave siguiente, y no se hizo en este ensayo.
4. **Volumen de la bitácora.** Cada verificación agrega dos filas por usuario, y cada relleno de documento o celular también. Con millones de usuarios, eso puede saturar la cadena de auditoría. Conviene decidir si la verificación se registra por lote y no por fila.

## 7. Simulacro de restauración (ADR-0041, H-03)

| Medida | Resultado |
|---|---|
| Script | `infra/scripts/test-postgres-recovery.sh` |
| Entorno | Contenedor descartable `postgres:16.6-alpine` con `bash` y `age`; el servidor se inicializó dentro del contenedor |
| Respaldo | Publicado y cifrado con `age` (`backup_published`, snapshot `20261110T220721Z`) |
| Restauración | Cargada en `integrate_restore_simulacro`; el dato de prueba se recuperó íntegro |
| Rechazos | Destino ocupado y copia corrupta rechazados; fallo de `pg_dump` reportado como fallo |
| Resultado del script | `OK` (exit 0) |
| Tiempo total del simulacro | 8 s, con arranque del contenedor incluido |

**RPO y RTO no medidos.** El simulacro usa un volumen mínimo y reemplaza S3 y el canal de alertas por simulaciones. Sus tiempos no representan un respaldo real. Por diseño, el RPO es el intervalo entre copias (24 h según ADR-0041), y el RTO solo puede medirse con un respaldo real de producción.

## 8. RNF-01 (cobertura de migración y exposición)

| Medida | Resultado en este ensayo | Fuente |
|---|---|---|
| Documento y celular de `auth.users` cifrados | 100 % (1 001 / 1 001) y 0 columnas en claro | Consulta SQL de §1 y §2 |
| Acceso del rol de reportes a documento y celular | Sin acceso | Consulta de §4 |
| Latitud pública | 2 decimales (regla `approximate`) | Prueba `CatalogApiIntegrationTest` en verde, no medida de nuevo aquí |
| Punto de encuentro exacto solo para la reserva confirmada del turista | Verificado | `BookingApiIntegrationTest` (14 pruebas en verde, incluida la de punto exacto) |
| Duplicados concurrentes | Rechazados | `IdentityCutPostgresTest` y restricción `uq_users_document_index` |

`docs/quality/evidencias-metricas.md` es del 19 de octubre y todavía reporta «No cumple» para cifrado de documento y celular y para latitud. No encontré un generador automático de ese archivo, así que esta tabla no lo reemplaza y su regeneración es manual.

## 9. Pendientes que este ensayo no cubre

- Rotación de llave de cifrado (hallazgo 2) y de llave del índice (hallazgo 3).
- Ensayo de restauración con respaldo real y medición de RPO y RTO.
- Pruebas de accesibilidad, escritorio y celular físico, y ejecución completa de `mvnw verify`.
- Acceso administrativo y de gestor a la ubicación exacta (ADR-0046 §1).
