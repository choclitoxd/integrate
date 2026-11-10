# Seguimiento de hallazgos de TejiendoHogar

**Corte documental:** 9 de noviembre de 2026. **Fuente:** [informe original](auditoria-tejiendohogar.md).

Esta revisión contrasta las recomendaciones del informe con los ADR, los entregables disponibles y los archivos de implementación y pruebas del proyecto. Las ejecuciones citadas son evidencias históricas. La validación de la aplicación queda para la etapa descrita al final.

## Cómo actualizar el seguimiento

1. Usa el ID del informe y asigna una persona responsable y una fecha objetivo en el registro de avances.
2. Adjunta el ADR, archivo, PR o acta que documente el cambio. Si el hallazgo tiene varias partes, registra cuáles se cubren.
3. Ejecuta el criterio de cierre y guarda evidencia. Para casos de aplicación, identifica el entorno, la versión, el rol y el dispositivo.
4. Actualiza el estado a **Cerrado** cuando se haya cumplido todo el criterio y el equipo haya revisado la evidencia.

**Parcial** indica que existe una decisión, corrección o evidencia para una parte del hallazgo. **Abierto** indica que el criterio sigue pendiente. **Por confirmar** indica que falta identificar la versión oficial del entregable o conseguir evidencia suficiente.

## Auditoría documental inicial

| ID | Prioridad | Estado | Evidencia revisada y brecha | Criterio de cierre |
|---|---|---|---|---|
| H-01 | Crítica | Parcial | [ADR-0039](../architecture/adr/ADR-0039-proteger-identidad-y-punto-de-encuentro.md) y [proyección de catálogo](../../backend-api/src/main/java/com/integrate/modules/catalog/service/impl/ExperienceViews.java) muestran el redondeo público. [User.java](../../backend-api/src/main/java/com/integrate/modules/auth/entity/User.java) conserva campos de identidad en claro. Quedan el corte de cifrado y los alias heredados. | Medir RNF-01 con 100 % de documento y celular protegidos. Comprobar que la ficha pública no permita reconstruir el punto exacto y que solo los actores autorizados puedan consultarlo. |
| H-02 | Crítica | Parcial | El [ADR-0040](../architecture/adr/ADR-0040-publicar-politica-y-exigir-consentimiento-inicial.md) define publicación y aceptación inicial. Existe el [filtro de consentimiento](../../backend-api/src/main/java/com/integrate/security/AdminConsentFilter.java) y la opción de configuración está desactivada por defecto en [application.yml](../../backend-api/src/main/resources/application.yml). | Publicar la política aprobada, activar el control en el entorno previsto y comprobar lectura, aceptación, rechazo, versión auditada y cobertura de todos los canales de alta. Repetir RNF-06. |
| H-03 | Crítica | Parcial | [ADR-0041](../architecture/adr/ADR-0041-respaldar-y-restaurar-postgresql.md) y [procedimiento de respaldo](../despliegue/respaldo-postgresql.md). Hay scripts, temporizador y evidencia de restauración sintética local con almacenamiento simulado. | Activar respaldo externo y alertas. Restaurar desde una copia externa en una base aislada y comprobar operación y permisos. Medir RPO ≤ 24 h y RTO ≤ 4 h. |
| H-04 | Alta | Parcial | El C4 v2 disponible en Descargas conserva referencias a FoodExpress. La extracción textual del C4 v3 ya no las encuentra. Falta confirmar que la versión final describe el nivel 4 y el stack reales. | Revisar visualmente la versión de entrega y confirmar encabezados, nivel 4 y stack frente a los módulos del repositorio. |
| H-05 | Alta | Parcial | El [despliegue de demo](../despliegue/demo-gratuita.md) describe el entorno en el repositorio. Falta conciliar el diagrama C4, la Definición y el ADR-0035 con el inventario de despliegues vigente. | Documentar por entorno frontend, API, base, almacenamiento y dominios. Incorporar el diagrama a la entrega formal. |
| H-06 | Alta | Abierto | El [protocolo Git](../rules/git-workflow.md) define revisiones y controles. No se encontró un directorio local `.github/workflows` en el árbol revisado. La protección de ramas queda por verificar. | Presentar una ejecución de CI con compilación, pruebas, cobertura y escaneo de secretos. Adjuntar evidencia de protección de rama y un PR revisado por otra persona. |
| H-07 | Alta | Abierto | El [build de la PWA](../../frontend-pwa/vite.config.ts) conserva `sourcemap: true` para el cliente. No se encontró un archivo `_headers` en el frontend revisado. La [auditoría propia](auditoria-procesos-1-y-2-2026-10-27.md) contiene hallazgos relacionados. | Validar el artefacto desplegado sin mapas de código públicos y comprobar CSP, HSTS y protección contra enmarcado. Revisar el cierre de los hallazgos altos relacionados. |
| H-08 | Alta | Parcial | Hay [script de cuentas de prueba](../../infra/scripts/crear-testers.py) y [guion por rol](guion-pruebas-ciclo-de-vida.md). Esos archivos no demuestran franjas disponibles ni credenciales vigentes para todos los roles en el entorno. | Validar las cuentas en `pruebas` y completar catálogo → franja → reserva → pago simulado → voucher. Documentar cómo reponer los datos sintéticos. |
| H-09 | Alta | Parcial | El [ADR-0042](../architecture/adr/ADR-0042-consola-de-administracion-con-shell-propio.md) y las [rutas](../../frontend-pwa/src/app/routes.tsx) muestran la consola de escritorio con resumen, experiencias, asociaciones y auditoría. El [ADR-0043](../architecture/adr/ADR-0043-consola-de-administracion-fase-2-personas-incidentes-y-liquidaciones.md) define la fase 2. Siguen por confirmar los flujos de anfitrión, cancelación en la PWA, edición de perfil y actualización de «Mis experiencias» tras el pago. | Probar las acciones priorizadas en la PWA y documentar qué funciones quedan por API. Evaluar administración según el alcance de escritorio del ADR-0042 y el aviso previsto para móvil. |
| H-10 | Media | Abierto | Hay [configuración de aplicación y logging](../../backend-api/src/main/resources/application.yml). En el material revisado faltan pruebas de disponibilidad, alertas y un registro operativo de incidentes. | Verificar un chequeo público de salud acotado, logs JSON con trazabilidad, alerta por caída y registro de un incidente de prueba. |
| H-11 | Media | Por confirmar | Se localizaron dos copias de la Definición en Descargas y referencias a Bizagi. El [estado del proyecto](../status/PROJECT_STATUS.md) ofrece una base para alinear el cronograma. Falta identificar el archivo oficial y verificar los BPMN adjuntos. | Confirmar versión oficial con cronograma, BPMN, aprobación y revisión de plantilla. |
| H-12 | Media | Abierto | El [informe generado](evidencias-metricas.md) está fechado el 11 de octubre de 2026 y aún tiene RNF marcados «Pendiente Fase 2». El [estado del proyecto](../status/PROJECT_STATUS.md) ya registra avances posteriores. | Regenerar la evidencia sobre el commit de entrega y alinear PGCP §5, matriz de trazabilidad y estado del proyecto con los resultados. |
| H-13 | Media | Parcial | El [guion por rol](guion-pruebas-ciclo-de-vida.md) conserva E-01 a E-09, resultados y revalidaciones requeridas. El [ADR-0038](../architecture/adr/ADR-0038-correo-sintetico-opaco-rol-gestor-revocado-y-firma-del-pdf.md) aborda parte de los defectos. | Repetir el ciclo con usuarios reales y celular físico, incluir cámara, instalación y tiempo de enrolamiento, y adjuntar acta y resultados de los nueve errores. |
| H-14 | Media | Abierto | Las [evidencias de calidad](evidencias-metricas.md) mantienen brechas de legibilidad y conformidad del PDF. El informe señala mensajes contradictorios sobre autorización de salud. | Revisar textos y medir RNF-15. Acordar y documentar cómo se cumple RNF-23 con la política de minimización. Comprobar el comportamiento del registro y el voucher. |
| H-15 | Media | Parcial | Existe un PDF de casos de uso y matriz RACI en Descargas. Queda por verificar su asignación nominal vigente y la evidencia de participación y revisión cruzada. | Adjuntar RACI aprobada y evidenciar responsables, revisiones y seguimiento en PR o actas. |
| H-16 | Media | Abierto | Hay [escenarios k6](../../qa/k6/README.md) y [resultados de referencia](evidencias-metricas.md). La medición local no acredita el rendimiento de la versión desplegada. | Ejecutar carga en un entorno desplegado autorizado y registrar versión, carga, latencia p95, errores y comparación con el umbral. |
| H-17 | Baja | Abierto | El [PGCP v1.10](1.1%20Integrate%20Plan%20de%20Gesti%C3%B3n%20de%20la%20Calidad%20del%20Proyecto%20v1.10.docx) es el archivo a conciliar con la [matriz RNF](matriz-trazabilidad-rnf.md). El informe identifica inconsistencias de numeración, fuentes y portada. | Revisar cada observación de edición de H-17 contra la versión final del PGCP y registrar su corrección. |
| H-18 | Baja | Abierto | El DOCX de Diseño, Reutilización y Pruebas v1.00 disponible en Descargas conserva texto de plantilla y ejemplos de otro dominio. | Diligenciar la sección 9 con pruebas reales o referenciar explícitamente las secciones del PGCP que las contienen. |
| H-19 | Baja | Por confirmar | El [manual](../manual/manual-usuario.md) distingue localhost como entorno de desarrollo. Falta contrastar la versión publicada, revisar textos de semilla y comprobar que el pie de ingreso sea accesible en móvil. | Verificar textos reales de la demo, la URL indicada por el manual y el enlace del pie en un teléfono físico. |
| H-20 | Baja | Abierto | Se consultaron el [modelo relacional](../modelo_relacional.md) y las [migraciones](../../backend-api/src/main/resources/db/migration/). No aparecieron sentencias RLS en las migraciones revisadas. El árbol local contiene artefactos del grafo y no se encontró un archivo de licencia en el proyecto. | Conciliar modelo y migraciones, decidir qué archivos generados se conservan, documentar uso de IA y licencia, y alinear los RNF con las decisiones vigentes. |

Los archivos de Descargas se usaron como copias de trabajo. La revisión de texto del C4, la Definición y el documento de Diseño sirve para identificar diferencias, con diagramas y versiones oficiales pendientes de confirmación.

## Orden de trabajo

1. Avanzar en paralelo en H-01, H-02 y H-03. Sus cierres dependen del corte de identidad, la política aprobada y el simulacro externo, respectivamente.
2. Preparar una versión verificable de `pruebas` para H-07 a H-09. Cargar datos sintéticos y dejar identificada la versión antes de probar. La consola de administración puede avanzar en paralelo según los ADR-0042 y ADR-0043.
3. Alinear los entregables y la evidencia de revisión. Priorizar H-04 a H-06 antes de la entrega y registrar los ajustes de H-11, H-15 y H-17 a H-20.
4. Ejecutar la auditoría de aplicación y publicar resultados de calidad, aceptación y rendimiento para H-10, H-12 a H-14 y H-16.

## Registro de avances

Agrega una fila por cambio o validación. El estado actual de cada hallazgo se actualiza en la matriz.

| Fecha | Hallazgo | Responsable | Acción o resultado | Evidencia | Próximo paso y fecha objetivo |
|---|---|---|---|---|---|
| 2026-11-09 | H-01 a H-20 | Por asignar por hallazgo | Línea base de auditoría documental | Matriz anterior | Asignar responsables y acordar fechas de cierre |

## Etapa final · Auditoría en la aplicación

1. Registrar versión desplegada, fecha, entorno, navegador, dispositivo y cuentas sintéticas por rol. Preparar franjas, pagos simulados y registros de administración. Guardar las credenciales por separado.
2. Ejecutar el [guion por rol](guion-pruebas-ciclo-de-vida.md) en Chrome de escritorio y en un celular físico. Recorrer visitante y turista, administrador, gestor y anfitrión. En administración, comprobar el acceso por rol y las acciones disponibles en escritorio, y el aviso en móvil. En gestor, incluir cámara, instalación, operación sin conexión y sincronización.
3. Comprobar permisos y casos negativos. Intentar abrir rutas con un rol distinto, consultar reservas ajenas, acceder al punto exacto sin autorización y operar como administrador sin aceptación vigente cuando el control esté activo.
4. Complementar la navegación con pruebas de API, respaldos, alertas y rendimiento. Registrar estas evidencias por separado de las capturas de interfaz.
5. Consolidar resultados y vincularlos a la matriz. Para cada caso registrar **Cumple**, **No cumple**, **Bloqueado** o **No aplica**, con motivo para los últimos dos. Indicar la causa solo si se confirmó.

| Hallazgo y caso | Versión y entorno | Rol y dispositivo | Pasos y resultado esperado | Resultado observado | Estado | Evidencia y causa |
|---|---|---|---|---|---|---|
| H-08 · Reserva completa | Por registrar | Turista · Chrome | Elegir franja, reservar, simular pago y abrir voucher | Pendiente de ejecución | Pendiente | — |
