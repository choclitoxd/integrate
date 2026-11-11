# ADR-0055: Validar el rendimiento con carga en un entorno desplegado

- **Estado:** Propuesto
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

