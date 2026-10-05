# Infraestructura local · Intégrate

Decisión de diseño: [ADR-0001](../docs/architecture/adr/ADR-0001-infraestructura-local-docker-compose.md).

| Servicio | Perfil | Puerto (solo `127.0.0.1`) | Uso |
| :--- | :--- | :--- | :--- |
| PostgreSQL 16 | *(por defecto)* | `5432` | Base `integrate`, roles del bootstrap |
| LocalStack S3 | `storage` / `full` | `4566` | Bucket de vouchers PDF |
| Mailpit | `mail` / `full` | `1025` SMTP · `8025` UI | Correo transaccional |

## 1. Preparar variables (una vez)

```bash
cp infra/env/.env.example infra/env/.env.development
```

Reemplace cada placeholder por una contraseña fuerte. `DATABASE_PASSWORD` debe coincidir con
`INTEGRATE_API_PASSWORD` y `FLYWAY_PASSWORD` con `INTEGRATE_OWNER_PASSWORD`.
`.env.development` está en `.gitignore`: **nunca lo comitee** (SEC-DIR-004).

## 2. Levantar

Desde `infra/docker/`:

```bash
docker compose --env-file ../env/.env.development up -d
docker compose --env-file ../env/.env.development --profile full up -d
```

Si falta una variable obligatoria, Compose aborta indicando cuál.

## 3. Verificar

```bash
docker compose --env-file ../env/.env.development ps
docker exec integrate-postgres psql -U postgres -c "\du"
```

## 4. Reinicio limpio

`00_bootstrap.sql` solo se ejecuta cuando el volumen de datos está **vacío**. Para cambiar
contraseñas o re-ejecutar el bootstrap hay que borrar los volúmenes (se pierden los datos locales):

```bash
docker compose --env-file ../env/.env.development --profile full down -v
```
