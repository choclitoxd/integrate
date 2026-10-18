# Guía de desarrollo · Intégrate

Cómo levantar Intégrate en una máquina local, cargar datos de prueba, trabajar con recarga en caliente y
ejecutar las pruebas. Para el uso de la aplicación como usuario final, vea el
[manual de usuario](manual/README.md).

---

## 1. Requisitos

| Herramienta | Versión | Para qué |
| :--- | :--- | :--- |
| Docker + Docker Compose | reciente | PostgreSQL 16, LocalStack S3 y Mailpit |
| Java (JDK) | 21 | `backend-api` (el *wrapper* `mvnw` descarga Maven) |
| Node.js | ≥ 22.12 | `frontend-pwa` |
| OpenSSL | cualquiera | Generar llaves RSA y AES locales |

Puertos usados (todos ligados a `127.0.0.1`): `5432` PostgreSQL · `4566` LocalStack · `1025`/`8025` Mailpit ·
`8080` backend · `5173` PWA.

## 2. Variables de entorno

Intégrate versiona una sola plantilla: [`infra/env/.env.example`](../infra/env/.env.example). Cópiela y
complete los valores:

```bash
cp infra/env/.env.example infra/env/.env.development
```

| Variable | Cómo obtener el valor local |
| :--- | :--- |
| `POSTGRES_SUPERUSER_PASSWORD`, `INTEGRATE_*_PASSWORD` | Contraseñas fuertes inventadas por usted. |
| `DATABASE_PASSWORD` | El mismo valor que `INTEGRATE_API_PASSWORD`. |
| `FLYWAY_PASSWORD` | El mismo valor que `INTEGRATE_OWNER_PASSWORD`. |
| `JWT_SIGNING_KEY` | `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \| base64 -w0` |
| `VOUCHER_SIGNING_KEY` | Igual que la anterior, pero **otra** llave distinta. |
| `SENSITIVE_DATA_KEY_V1` | `openssl rand -base64 32` — respáldela: si se pierde, los datos cifrados son irrecuperables. |
| `PAYMENT_WEBHOOK_SECRET_MOCK` | `openssl rand -base64 32` |
| `PAYMENT_PROVIDER` | `MOCK` en local (no se cobra dinero real). |
| `SMTP_*` | Déjelos apuntando a Mailpit; la plantilla trae ejemplos comentados para Gmail y Brevo. |

El perfil `dev` del backend importa automáticamente `infra/env/.env.development`, así que no hace falta
exportar las variables en la terminal.

> `infra/env/.env.development` y `infra/env/.env.test-users` están ignorados por Git. **Nunca** los comitee ni
> copie sus valores en código, documentación o issues ([SEC-DIR-004](security/security_secrets_policy.md)).

## 3. Infraestructura local

Desde `infra/docker/`:

```bash
docker compose --env-file ../env/.env.development --profile full up -d
docker compose --env-file ../env/.env.development ps
```

| Perfil | Servicios |
| :--- | :--- |
| *(ninguno)* | Solo PostgreSQL |
| `storage` | + LocalStack S3 (buckets de vouchers y de documentos sensibles) |
| `mail` | + Mailpit (UI en <http://localhost:8025>) |
| `full` | Todos |

El script `infra/postgres/init/00_bootstrap.sql` crea los roles `integrate_owner` (DDL/Flyway),
`integrate_api` (DML del backend) e `integrate_readonly`, **solo la primera vez** que el volumen está vacío.
Si cambia contraseñas, reinicie desde cero (borra los datos locales):

```bash
docker compose --env-file ../env/.env.development --profile full down -v
```

Más detalle: [infra/README.md](../infra/README.md).

## 4. Backend (`backend-api`)

```bash
cd backend-api
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev      # Windows: mvnw.cmd ...
```

- Al arrancar, Flyway aplica las migraciones de `src/main/resources/db/migration` y, en `dev`, la semilla
  mínima de `db/seed-dev`.
- La API queda en <http://localhost:8080/api/v1>. Los errores siguen el formato RFC 9457 e incluyen un
  `traceId` para buscar en los logs.
- **Recarga en caliente** ([ADR-0021](architecture/adr/ADR-0021-recarga-en-caliente-entorno-desarrollo.md)):
  con DevTools, recompilar (`./mvnw compile` en otra terminal o desde el IDE) reinicia el contexto en
  segundos. DevTools no se empaqueta en el JAR de producción.
- **Primer administrador:** si no carga la semilla, defina `BOOTSTRAP_ADMIN_EMAIL` y
  `BOOTSTRAP_ADMIN_PASSWORD` para el primer arranque y retírelas después.

## 5. PWA (`frontend-pwa`)

```bash
cd frontend-pwa
npm install
npm run dev
```

Vite sirve la PWA en <http://localhost:5173> con HMR y redirige `/api` al backend en el puerto 8080, por lo
que no hace falta configurar CORS en local. Si el backend corre en otro puerto, defina
`VITE_DEV_API_PROXY_TARGET` (por ejemplo `http://localhost:8081`).

Reglas obligatorias para cualquier cambio en la PWA: [FE-DIR-001](rules/frontend-rules.md) (mobile-first
360–430 px, tokens de color AAA de `src/styles/tokens.ts`, áreas táctiles ≥ 44 px, fronteras de capas).

## 6. Datos de prueba

[`infra/postgres/seed/dev_test_data.sql`](../infra/postgres/seed/dev_test_data.sql) **vacía los datos
transaccionales** y crea un usuario por rol, dos asociaciones, anfitriones, experiencias y franjas
horarias. Conserva los catálogos gobernados por migraciones (roles, políticas de privacidad, categorías,
DIVIPOLA, comisiones).

El archivo no contiene contraseñas: recibe el hash Argon2id como variable de psql. Todas las cuentas
comparten la contraseña de prueba guardada en `infra/env/.env.test-users` (ignorado por Git).

1. Obtenga el hash Argon2id de esa contraseña; por ejemplo, regístrese en la PWA con ella y copie el valor
   de `auth.users.password_hash` de ese usuario.
2. Cargue la semilla (bash):

   ```bash
   docker exec -i integrate-postgres psql -U postgres -d integrate \
     -v ON_ERROR_STOP=1 -v pwhash='<hash argon2id>' < infra/postgres/seed/dev_test_data.sql
   ```

   En PowerShell, sustituya la redirección `<` por
   `Get-Content infra/postgres/seed/dev_test_data.sql -Raw | docker exec -i integrate-postgres psql ...`.

Cuentas creadas: `turista1…3@ejemplo.com`, `gestor1@ejemplo.com`, `gestor2@ejemplo.com`,
`anfitrion1…3@ejemplo.com` y `admin@ejemplo.com`. Qué puede hacer cada una: [manual de usuario](manual/README.md).

## 7. Pruebas y Quality Gate

### Backend

```bash
cd backend-api
./mvnw verify               # pruebas unitarias e integración (Testcontainers/PostgreSQL)
./mvnw verify -Pquality     # suite trazable al plan de calidad, incluye pruebas de no conformidad
```

Las pruebas marcadas `nonconformity` verifican el criterio literal del plan sobre comportamientos que hoy
difieren; solo corren con `-Pquality` y sus fallos son esperados y documentados.

### PWA — Quality Gate (Directiva 7 de FE-DIR-001)

Ningún cambio en la PWA se da por terminado sin estos cinco comandos en verde:

```bash
npm run typecheck
npm run lint
npm run format:check
npm test
npm run build
```

Extras: `npm run test:coverage` (cobertura) y `npm run quality:bundle` (presupuesto de tamaño del bundle).

### Carga y evidencias

- Pruebas k6 de RNF-02, RNF-08 y RNF-10: [qa/k6/README.md](../qa/k6/README.md). Se ejecutan **solo** contra un
  entorno desechable, nunca contra la base de desarrollo.
- Informe de evidencias: `node qa/scripts/build-evidence-report.mjs` regenera
  [docs/quality/evidencias-metricas.md](quality/evidencias-metricas.md). No lo edite a mano.

## 8. Flujo de trabajo

| Tema | Regla |
| :--- | :--- |
| Cambios de backend | Primero un ADR en `docs/architecture/adr/`, aprobado antes de escribir código ([flujo ADR](rules/adr-workflow.md)). |
| Git | Ramas, Conventional Commits, momentos de push y PR según [GIT-WF-001](rules/git-workflow.md). |
| Secretos | Lista de verificación pre-commit / pre-push de la [política de secretos](security/security_secrets_policy.md). |
| Grafo de conocimiento | Tras modificar código: `graphify update .` (ver `CLAUDE.md`). |

## 9. Problemas frecuentes

| Síntoma | Causa probable y solución |
| :--- | :--- |
| `docker compose` aborta con «Defina … en infra/env/.env.development» | Falta esa variable en `.env.development`. |
| El backend no conecta a PostgreSQL (`password authentication failed`) | `DATABASE_PASSWORD` ≠ `INTEGRATE_API_PASSWORD`, o cambió contraseñas sin borrar el volumen (`down -v`). |
| El backend no arranca y menciona `PAYMENT_PROVIDER` | La variable es obligatoria: use `MOCK` en local. |
| Error al firmar sesiones o vouchers | `JWT_SIGNING_KEY` / `VOUCHER_SIGNING_KEY` no es un PKCS#8 PEM en Base64 de una sola línea. |
| No llegan correos | Levante el perfil `mail` y revise <http://localhost:8025>. |
| El voucher PDF no se guarda | Levante el perfil `storage` (LocalStack). |
| La PWA responde 502 / `ECONNREFUSED` en `/api` | El backend no está corriendo en el puerto 8080. |
| No puedo iniciar sesión con las cuentas de prueba | La semilla no se ha cargado o se cargó con el hash de otra contraseña (sección 6). |
