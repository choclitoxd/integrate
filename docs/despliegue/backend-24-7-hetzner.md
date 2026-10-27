# Backend 24/7 en Hetzner Cloud (ADR-0027)

Guía de cómo está desplegada la API para que no se duerma, y cómo operarla. La PWA sigue en Cloudflare Pages (ADR-0024) y la base de datos en Neon. No contiene secretos.

## Arquitectura

```
Usuario → Cloudflare Pages (PWA + Function /api/*) → https://<ip-con-guiones>.sslip.io → Caddy (VPS) → API Spring Boot → Neon
```

- **Servidor:** Hetzner Cloud `integrate-api`, plan CPX12 (1 vCPU, 2 GB RAM), Ubuntu 24.04, Falkenstein, con 2 GB de swap.
- **HTTPS:** Caddy obtiene y renueva solo el certificado de Let's Encrypt sobre `<ip-con-guiones>.sslip.io` (por ejemplo `2-31-111-240.sslip.io`). No hay dominio propio; si se compra uno, solo cambia `API_HOST`.
- **Seguridad:** acceso solo por llave SSH al usuario `integrate` (sin root ni contraseña), cortafuegos de Hetzner y `ufw` con solo los puertos 22, 80 y 443, `fail2ban` y actualizaciones automáticas. La API no publica puertos: solo Caddy.
- **Proxy de la PWA:** el secreto `API_ORIGIN` del proyecto `integrate-demo` en Cloudflare Pages apunta a esa URL HTTPS.

## Archivos

| Archivo | Para qué sirve |
| :--- | :--- |
| `infra/docker/compose.vps.yml` | API + Caddy, con reinicio automático y límite de memoria. |
| `infra/docker/Caddyfile` | Proxy inverso hacia la API. |
| `~/integrate/.env` (solo en el servidor, permisos 600) | Variables de la API y `API_HOST`. Se genera desde `infra/env/.env.demo`. Nunca va a Git. |

## Cómo se creó

1. Servidor y cortafuegos con la API de Hetzner (token en `infra/env/.env.hetzner`, ignorado por Git) y la llave SSH `integrate-vps`.
2. Configuración inicial con cloud-init: usuario `integrate`, Docker, swap, `ufw`, `fail2ban` y endurecimiento de SSH.
3. Imagen construida en el PC (`docker build -t integrate-api:vps backend-api`) y enviada con `docker save | gzip | ssh ... docker load`.
4. Se copian `compose.vps.yml`, `Caddyfile` y `.env` al servidor y se ejecuta `docker compose -f compose.vps.yml up -d`.
5. `wrangler pages secret put API_ORIGIN` y `wrangler pages deploy dist` para apuntar la PWA al servidor.

## Operación

```bash
ssh -i ~/.ssh/id_ed25519 integrate@<IP>
cd ~/integrate
docker compose -f compose.vps.yml ps
docker compose -f compose.vps.yml logs -f api
```

**Actualizar la API:** construir la imagen nueva en el PC, enviarla con `docker save | ssh ... docker load` y ejecutar `docker compose -f compose.vps.yml up -d api`.

**Memoria:** la JVM usa `-Xmx768m` y el contenedor tiene un tope de 1400 MB. Con una sola vCPU y 2 GB, vigile `docker stats` y el swap; si se queda corto, se sube a un plan con 4 GB desde el panel de Hetzner sin reinstalar.

## Riesgos asumidos

- Un solo servidor y sin alta disponibilidad: si cae, cae el backend. Conviene un monitor externo gratuito sobre `/api/v1/privacy-policies/current`.
- `sslip.io` es un servicio de terceros: si dejara de resolver, hay que cambiar `API_HOST` por un dominio propio.
- No debe haber dos backends escribiendo en la misma base de Neon al mismo tiempo (los procesos de fondo se duplicarían). SnapDeploy queda solo como plan B: si se usa, hay que apagar el del VPS.
