# Prueba de carga en el entorno desplegado (H-16 · ADR-0055)

Procedimiento para medir RNF-02, RNF-08 y RNF-10 contra `pruebas` con los escenarios de [`qa/k6`](../../qa/k6/README.md). Los umbrales no se modifican durante la corrida; un cambio exige aprobación trazable del propietario de requisitos.

## Antes de empezar

1. **Autorización:** el desarrollador autoriza la corrida y el entorno por escrito en la sesión.
2. **Versión:** el contenedor de `pruebas` está construido desde el commit que se declara (`git rev-parse origin/pruebas`) y su estado es `RUNNING`. Si la construcción falla, no se mide: se diagnostica con el log de construcción del panel.
3. **Datos sintéticos:** los scripts crean sus propias asociaciones, anfitriones, experiencias, franjas y turistas. `pruebas` no debe contener datos de personas reales; si los tuviera, usar una base aislada.
4. **Credenciales:** `ADMIN_EMAIL`, `ADMIN_PASSWORD` y `USER_PASSWORD` se pasan como variables `-e` de la corrida y no se escriben en disco ni en este documento (SEC-DIR-004).
5. **Presupuesto SnapDeploy:** anotar los usos gastados (push, `wake`) y apagar con `SNAPDEPLOY_NAME=integrate-pruebas bash infra/scripts/snapdeploy.sh stop` al terminar. Variables siempre con `ENV_FILE=infra/env/.env.demo.pruebas`. El nombre por defecto de `.env.snapdeploy` es otro contenedor.
6. **Origen de la carga:** k6 corre desde un equipo con red estable; la latencia de red del origen se anota porque suma a la medida.

## Ejecución

```bash
docker run --rm -v "$PWD/qa/k6:/scripts" -w /scripts \
  -e BASE_URL=https://<url-del-contenedor-pruebas> \
  -e ADMIN_EMAIL=... -e ADMIN_PASSWORD=... -e USER_PASSWORD=... \
  grafana/k6:2.2.0 run --summary-export results/<escenario>-desplegado.json <escenario>.js
```

Orden de la corrida, con un solo `wake`: `smoke-desplegado.js` (humo funcional; debe dar 100 %), `login.js`, `booking-lock.js` y `catalog-checkout-mix.js`. Los escenarios crean sus datos por la API y, como la verificación del punto de encuentro es asíncrona (ADR-0030), esperan o confirman el punto antes de aprobar la experiencia.

Con k6 instalado en lugar de Docker, sustituir el `docker run` por `k6 run` con las mismas variables. Escenarios: `login.js`, `booking-lock.js` y `catalog-checkout-mix.js`. La CPU se lee del panel del contenedor durante `catalog-checkout-mix.js`.

## Plantilla de evidencia (una por corrida)

| Campo | Valor |
| :--- | :--- |
| Fecha y hora | |
| Entorno / URL | |
| Commit desplegado | |
| Escenario | |
| Concurrencia y duración | |
| Latencia p95 (umbral) | |
| Tasa de errores (umbral < 1 %) | |
| 5xx inesperados | |
| CPU / memoria del contenedor (umbral CPU < 75 %) | |
| Origen de la carga y latencia de red | |
| Usos de SnapDeploy gastados | |
| Resultado frente al umbral | Cumple / No cumple |
| Observaciones | |

Si el entorno se degrada por causas ajenas a la aplicación, se repite la corrida y se conservan ambas evidencias con su explicación. Un incumplimiento abre una acción de diagnóstico; H-16 no se cierra con una corrida local ni con una versión desplegada distinta de la declarada.

## Registro de corridas

### 2026-11-11 · `integrate-pruebas` · commit `42a3fd0`

Contenedor SnapDeploy de 0,25 vCPU y heap de 144 MB, desplegado desde `pruebas` (PostgreSQL 18.6 en Neon). Origen de la carga: un equipo en Colombia con Docker (`grafana/k6:2.2.0`). Usos de SnapDeploy: 1 push y 1 `wake` (5 por 12 h; quedan 2). Contenedor apagado al terminar.

| Escenario | Resultado | Detalle |
| :--- | :--- | :--- |
| `smoke-desplegado.js` | **Cumple** | 24 de 24 comprobaciones; p95 de la petición 1,3 s con 1 usuario. Confirma que corre la versión de hoy (salud acotada, V18 y V19 aplicadas, EPS bloqueada sin aviso aprobado, rol HOST). |
| `login.js` (RNF-08, p95 < 800 ms, 100 % correctos) | **No cumple** | 4 inicios por segundo durante 1 min: p95 3,12 s, mediana 90 ms; fallaron 235 de 235 comprobaciones `login_ok`; 5 iteraciones descartadas. El código de respuesta de cada fallo no quedó registrado. |
| `booking-lock.js` (RNF-02) | **No ejecutado** | El inicio de sesión del `setup` recibió 429 de la regla «Container flood guard» de Cloudflare de la zona `snapdeploy.app`, justo después de `login.js`. |
| `catalog-checkout-mix.js` (RNF-10) | **No ejecutado** | Tras las dos corridas anteriores el contenedor respondía 502 (más de 2 minutos, estado `RUNNING`), así que se apagó. Con 600 usuarios simultáneos solo se habría repetido el efecto. |

**Lectura:** el resultado no mide la aplicación sino los límites de la plataforma de pruebas: una regla de Cloudflare limita la tasa por origen y el contenedor gratuito cayó con una carga de 4 inicios de sesión por segundo (cada uno con hash de contraseña sobre 0,25 vCPU). No se eludió la regla de Cloudflare ni se repitió con otro origen. H-16 sigue **abierto**; no se cambió ningún umbral.

**Acciones de diagnóstico:**
1. Registrar el código de respuesta y la latencia por intento en `login.js` (hoy solo cuenta aciertos) y repetir con una tasa menor para hallar el punto de caída.
2. Consultar con SnapDeploy los umbrales de «Container flood guard» y si admite una excepción para pruebas de carga autorizadas, o ejecutar la carga desde la red de la plataforma.
3. Medir RNF-10 en un entorno con recursos representativos del despliegue final (el contenedor gratuito de 0,25 vCPU no lo es) o aprobar, con el propietario de requisitos, el alcance de la prueba para este entorno.
4. Leer los logs de ejecución del contenedor durante la caída para saber si fue memoria (`ExitOnOutOfMemoryError` con heap de 144 MB) o la plataforma; hoy `snapdeploy.sh logs` devolvió registros del día anterior.
