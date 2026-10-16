# Pruebas de carga (k6) · ADR-0019 §5

| Script | RNF del plan | Umbrales (fallan la corrida si se incumplen) |
|---|---|---|
| `login.js` | RNF-08 | p95 de `POST /auth/login` < 800 ms, 100 % de logins correctos |
| `booking-lock.js` | RNF-02 | p95 del bloqueo sostenido (300 tx/min) ≤ 200 ms, ≥ 100 tx/min, sin 5xx inesperados; ráfaga de 100 simultáneos informativa (≤ 1 s) |
| `catalog-checkout-mix.js` | RNF-10 | 500 usuarios navegando + 100 en checkout: p95 < 1,5 s, < 1 % de errores, sin 5xx. La CPU (< 75 %) se mide aparte |

Los scripts crean sus propios datos por la API (asociación, anfitrión, experiencia, franjas y turistas), así que
**solo se ejecutan contra un entorno desechable**: nunca contra producción ni contra la base de desarrollo, que
quedaría llena de reservas de prueba.

## Ejecución local en un entorno aislado

1. Un PostgreSQL propio (otro nombre y puerto que `integrate-postgres`), con el mismo bootstrap:

   ```bash
   docker run -d --name integrate-qa-postgres -p 127.0.0.1:55432:5432 \
     -e POSTGRES_PASSWORD=... -e INTEGRATE_OWNER_PASSWORD=... -e INTEGRATE_API_PASSWORD=... \
     -e INTEGRATE_REPORTING_PASSWORD=... \
     -v "$PWD/infra/postgres/init/00_bootstrap.sql:/docker-entrypoint-initdb.d/00_bootstrap.sql:ro" \
     postgres:16.6-alpine
   ```

2. El backend empaquetado (`./mvnw verify`) con el perfil `dev`, `SERVER_PORT=18080`, la base del paso 1 y
   **secretos generados para esta corrida** (llaves RSA de sesión y de vouchers, llave AES, secreto del webhook Mock y
   `BOOTSTRAP_ADMIN_EMAIL`/`BOOTSTRAP_ADMIN_PASSWORD`). Las variables están en `infra/env/.env.example`. Ejecútelo
   desde un directorio fuera del repositorio para que no cargue `infra/env/.env.development`.

3. k6 con la imagen oficial fijada (no requiere instalarlo):

   ```bash
   docker run --rm -v "$PWD/qa/k6:/scripts" -w /scripts \
     -e BASE_URL=http://host.docker.internal:18080 \
     -e ADMIN_EMAIL=... -e ADMIN_PASSWORD=... -e USER_PASSWORD=... \
     grafana/k6:2.2.0 run --summary-export results/booking-lock.json booking-lock.js
   ```

4. Durante `catalog-checkout-mix.js`, mida la CPU del proceso del backend y anótela en `results/cpu-rnf10.json`.
5. Genere el informe: `node qa/scripts/build-evidence-report.mjs`.
6. Limpie: detenga el backend y `docker rm -f integrate-qa-postgres`.

Las credenciales viajan solo como variables `-e` de la corrida (SEC-DIR-004). `results/` no se versiona: la evidencia
que se conserva es `docs/quality/evidencias-metricas.md`.
