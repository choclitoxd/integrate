# Desplegar la PWA en Cloudflare Pages · lista de verificación

Notas para no repetir errores al publicar `integrate-demo.pages.dev`. No contiene secretos: los tokens viven en `infra/env/.env.cloudflare` (ignorado por Git).

## 1. Compilar para producción

Vite en modo producción **no lee** `.env.development.local`. La variable `VITE_API_BASE_URL` debe pasarse en el propio comando:

```bash
cd frontend-pwa
MSYS_NO_PATHCONV=1 VITE_API_BASE_URL=/api npm run build
```

- `MSYS_NO_PATHCONV=1` es obligatorio en **Git Bash (Windows)**. Sin él, `/api` se convierte en `C:/Program Files/Git/api` y la app muestra «VITE_API_BASE_URL debe ser una ruta…».
- Sin la variable, la app muestra «Configuración incompleta» en producción. Esto ya pasó: el sitio quedó roto tras un redespliegue.
- Antes de subir, confirma que el bundle no tiene la ruta de Git:

```bash
grep -c "Program Files" dist/assets/index-*.js   # debe dar 0
```

## 2. Publicar

```bash
export CLOUDFLARE_API_TOKEN=... CLOUDFLARE_ACCOUNT_ID=...   # desde infra/env/.env.cloudflare
WRANGLER_SEND_METRICS=false npx --yes wrangler pages deploy dist --project-name integrate-demo --branch main --commit-dirty=true
```

`--branch main` publica en producción. Cualquier otra rama genera una vista previa.

## 3. Proxy `/api` hacia el backend

La Pages Function `frontend-pwa/functions/api/[[path]].ts` reenvía `/api/*` al servidor. Su destino está en el secreto `API_ORIGIN` del proyecto:

```bash
printf '%s' 'https://2-31-111-240.sslip.io' | npx --yes wrangler pages secret put API_ORIGIN --project-name integrate-demo
```

- Cambiar el secreto **no** requiere recompilar; sí requiere un nuevo despliegue para que la función lo tome.
- Si el backend cambia de servidor (IP o dominio), actualiza el secreto y prueba `https://integrate-demo.pages.dev/api/v1/privacy-policies/current`.

## 4. Verificar después de publicar

1. Abrir `https://integrate-demo.pages.dev/` en una ventana de incógnito.
2. La pantalla debe mostrar **Experiencias comunitarias** con la tarjeta del catálogo, sin «Configuración incompleta».
3. En las herramientas de red, `catalog/departments`, `catalog/categories` y `experiences` deben responder **200** desde `integrate-demo.pages.dev/api`. Un 502 suele ser un backend dormido o caído; revisa `docker compose ps` en el servidor.
4. Abrir `https://integrate-demo.pages.dev/manual/` y comprobar que carga.

## 5. Desarrollo local

- `npm run dev` lee `frontend-pwa/.env.development.local` (copia de `.env.example`). `VITE_API_BASE_URL=/api` con `VITE_DEV_API_PROXY_TARGET=http://localhost:8080`.
- Si el servidor de desarrollo arrancó **antes** de crear ese archivo, reinícialo.
- Si el puerto 5173 está ocupado, Vite no lo cambia y el navegador integrado puede fallar. Cierra el proceso que lo usa.

## Errores vistos y su causa

| Mensaje o síntoma | Causa | Solución |
| :--- | :--- | :--- |
| «Configuración incompleta» en producción | Se compiló sin `VITE_API_BASE_URL` | Paso 1 |
| «se recibió "C:/Program Files/Git/api"» | Git Bash convirtió `/api` | `MSYS_NO_PATHCONV=1` |
| 502 en `/api/*` | Backend dormido o caído | Revisar el servidor, paso 4 |
| 404 en `/api/*` con HTML | Falta la función o el build es viejo | Redesplegar con `dist` actual |
