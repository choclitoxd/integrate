# ADR-0055: Validar el rendimiento con carga en un entorno desplegado

- **Estado:** Aceptado (2026-11-11)
- **Proyecto / Módulo:** Intégrate · backend, PWA y QA de rendimiento
- **Fecha:** 2026-11-10
- **Hallazgo relacionado:** H-16

## Contexto

El proyecto tiene escenarios k6 y mediciones locales, pero esas mediciones no representan necesariamente la versión desplegada ni sus límites de infraestructura. El seguimiento H-16 solicita medir la aplicación en un entorno desplegado y registrar versión, carga, latencia p95 y errores. La prueba debe evitar afectar usuarios y no usar datos personales.

## Decisión

Ejecutar las pruebas k6 existentes contra `pruebas` o un entorno aislado equivalente, con versión identificada, cuentas y datos sintéticos, y autorización operativa. No ejecutar carga contra producción como criterio rutinario de aceptación. Mantener los umbrales RNF vigentes —incluido RNF-10: p95 menor de 1500 ms, menos de 1 % de errores y CPU menor de 75 %— salvo que el propietario de requisitos apruebe un cambio trazable.

Cada corrida registrará commit o versión desplegada, fecha, entorno, escenario, concurrencia, duración, latencia p95, tasa de errores, consumo de recursos y resultado frente al umbral. Repetir una corrida si el entorno se degrada por causas ajenas a la aplicación y conservar ambas evidencias con su explicación.

## Alternativas Descartadas

- **Usar únicamente resultados locales:** no mide red, límites ni configuración de la versión desplegada.
- **Probar con carga en producción sin control:** introduce riesgo de degradar el servicio y afectar usuarios reales.
- **Cambiar umbrales durante la prueba para obtener aprobación:** invalida la comparación con el plan de calidad.

## Consecuencias

- **Positivas:** resultados reproducibles y comparables para H-16 y RNF-10.
- **Negativas / Riesgos asumidos:** requiere un entorno representativo, disponibilidad coordinada y control de costos y recursos durante la ejecución.

## Criterios de aceptación

- Se identifica y autoriza el entorno antes de iniciar la prueba.
- Se ejecutan los escenarios pertinentes de navegación, autenticación y reserva sin usar datos reales.
- Se reportan todos los datos de medición requeridos y se comparan con umbrales preexistentes.
- Un fallo genera una acción de diagnóstico; el hallazgo no se cierra por una corrida local.

## Precondiciones para ejecutar

Procedimiento y plantilla de evidencia: [prueba-carga-desplegada.md](../../quality/prueba-carga-desplegada.md).

- **Entorno:** `pruebas` (SnapDeploy, rama `pruebas`, Neon de pruebas). La corrida solo es válida si el contenedor ejecuta el commit que se declara; una versión desplegada anterior no acredita el estado actual.
- **Estado al 2026-11-11:** el contenedor de pruebas es `integrate-pruebas` (el nombre guardado en `infra/env/.env.snapdeploy` es otro contenedor, `integrate-0da6e`, en `FAILED`; usar `SNAPDEPLOY_NAME=integrate-pruebas`). El push de `42a3fd0` a `pruebas` se leyó como `FAILED` al minuto, pero el mismo despliegue figura `COMPLETED` horas después: el estado inicial es transitorio y no basta una lectura temprana. Los fallos reales son los del 2026-11-10 05:42, 06:17 y 07:47 UTC, cuyo log de construcción ya no está disponible (SnapDeploy lo conserva solo mientras corre el despliegue y poco después; usar `snapdeploy.sh buildlog` en el siguiente). El mismo jar arranca sin errores en local con `infra/env/.env.demo.pruebas` contra la base Neon de pruebas (Flyway aplicó V18 y V19). El contenedor está `STOPPED` y el límite de despliegues (5 por 12 h) se agotó: el `wake` y la corrida quedan para cuando se reinicie (2026-11-11 ~12:32 hora local).
- **Humo ejecutado contra la base de pruebas** (jar local, no desplegado, no cuenta para H-16): `qa/k6/smoke-desplegado.js`, 24 de 24 comprobaciones (salud acotada, rol HOST, reserva sin EPS, EPS bloqueada sin aviso aprobado).
- **Primera corrida (2026-11-11):** el humo funcional pasó 24 de 24 en el despliegue; `login.js` incumplió RNF-08 (p95 3,12 s, 235 de 235 comprobaciones fallidas), `booking-lock.js` quedó bloqueado por la regla «Container flood guard» de Cloudflare (429) y el contenedor cayó a 502, por lo que `catalog-checkout-mix.js` no se ejecutó. Detalle y acciones de diagnóstico en [prueba-carga-desplegada.md](../../quality/prueba-carga-desplegada.md#registro-de-corridas). H-16 sigue abierto.
- **Costo:** un push a `pruebas` y un `wake` consumen usos del límite diario de SnapDeploy (5); se avisa antes de gastarlos y se apaga el contenedor al terminar.

