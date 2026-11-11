# Prueba de carga en el entorno desplegado (H-16 · ADR-0055)

Procedimiento para medir RNF-02, RNF-08 y RNF-10 contra `pruebas` con los escenarios de [`qa/k6`](../../qa/k6/README.md). Los umbrales no se modifican durante la corrida; un cambio exige aprobación trazable del propietario de requisitos.

## Antes de empezar

1. **Autorización:** el desarrollador autoriza la corrida y el entorno por escrito en la sesión.
2. **Versión:** el contenedor de `pruebas` está construido desde el commit que se declara (`git rev-parse origin/pruebas`) y su estado es `RUNNING`. Si la construcción falla, no se mide: se diagnostica con el log de construcción del panel.
3. **Datos sintéticos:** los scripts crean sus propias asociaciones, anfitriones, experiencias, franjas y turistas. `pruebas` no debe contener datos de personas reales; si los tuviera, usar una base aislada.
4. **Credenciales:** `ADMIN_EMAIL`, `ADMIN_PASSWORD` y `USER_PASSWORD` se pasan como variables `-e` de la corrida y no se escriben en disco ni en este documento (SEC-DIR-004).
5. **Presupuesto SnapDeploy:** anotar los usos gastados (push, `wake`) y apagar con `bash infra/scripts/snapdeploy.sh stop` al terminar. Variables siempre con `ENV_FILE=infra/env/.env.demo.pruebas`.
6. **Origen de la carga:** k6 corre desde un equipo con red estable; la latencia de red del origen se anota porque suma a la medida.

## Ejecución

```bash
docker run --rm -v "$PWD/qa/k6:/scripts" -w /scripts \
  -e BASE_URL=https://<url-del-contenedor-pruebas> \
  -e ADMIN_EMAIL=... -e ADMIN_PASSWORD=... -e USER_PASSWORD=... \
  grafana/k6:2.2.0 run --summary-export results/<escenario>-desplegado.json <escenario>.js
```

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
