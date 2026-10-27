# Entorno de pruebas y promoción a producción (ADR-0031)

Cómo se prueba un cambio antes de llevarlo a producción. No contiene secretos: las credenciales viven en archivos locales ignorados por Git (`infra/env/.env.*`).

## Piezas

| | Pruebas | Producción |
| :--- | :--- | :--- |
| Backend | SnapDeploy (contenedor `integrate-0da6e`), detenido mientras no se prueba | Hetzner Cloud `integrate-api`, 24/7 |
| Base de datos | Rama `pruebas` de Neon (copia de producción) | Rama principal de Neon |
| PWA | `https://pruebas.integrate-demo.pages.dev` | `https://integrate-demo.pages.dev` |
| Rama de Git | `pruebas` | `main` |
| Variables | `infra/env/.env.demo.pruebas` | `infra/env/.env.demo` (en el servidor: `~/integrate/.env`) |
| Correos | **Apagados** (`INTEGRATE_NOTIFICATION_DISPATCH_ENABLED=false`) | Brevo |

La rama de Neon copia usuarios reales: trátela como dato sensible. Si se envejece, se restablece desde producción en el panel de Neon («Reset from parent»), con nadie probando.

## Flujo

```
cambio → rama `pruebas` → SnapDeploy + PWA de vista previa → verificar → merge a `main` → Hetzner + PWA de producción → verificar
```

1. **Probar:** empuje el cambio a la rama `pruebas`; SnapDeploy la reconstruye. Despierte el contenedor (`bash infra/scripts/snapdeploy.sh wake`) y publique la vista previa (`bash infra/scripts/pages.sh deploy pruebas`).
2. **Comprobar el aislamiento antes de confiar en el entorno** (ver el apartado siguiente).
3. **Verificar** el cambio en `pruebas.integrate-demo.pages.dev`. Al terminar, deténgalo (`POST …/containers/<id>/stop` o desde el panel).
4. **Promover:** fusione a `main` y ejecute `bash infra/scripts/hetzner.sh deploy` y `bash infra/scripts/pages.sh deploy produccion`. Si algo sale mal: `bash infra/scripts/hetzner.sh rollback`.

## Comprobar el aislamiento (obligatorio tras cambiar variables)

SnapDeploy **aplica las variables solo al construir un despliegue nuevo**: `snapdeploy.sh env` las guarda, pero un contenedor detenido que se despierta arranca con las que tenía al construirse. El 2026-10-27 esto hizo que el entorno de pruebas arrancara contra la base de **producción** durante unos minutos (se detuvo y se limpió: un solo usuario de prueba y un correo a una dirección ficticia, sin reservas ni pagos tocados).

Por eso, antes de usar el entorno de pruebas:

1. Despierte el contenedor y lea el log: `bash infra/scripts/snapdeploy.sh logs 400 | grep -oE "ep-[a-z]+-[a-z]+-[a-z0-9]+" | sort -u` debe mostrar **solo el host de la rama** (`ep-orange-frog-…`), nunca el de producción (`ep-calm-firefly-…`). Si aparece el de producción, deténgalo de inmediato.
2. Registre un usuario en la PWA de pruebas y compruebe que **no** puede iniciar sesión en producción (debe dar 401).
3. Pida un restablecimiento de contraseña y compruebe en la bitácora de la rama que el correo queda `PENDING` y no pasa a `SENT`.

## Cambiar la rama que sigue SnapDeploy

`snapdeploy.sh link --branch pruebas` enlaza el repositorio a esa rama y lanza una construcción nueva con las variables guardadas. Si el contenedor ya tiene un repositorio enlazado, SnapDeploy responde 400 y **no permite desenlazarlo con llaves de API**: hay que hacerlo en su panel (web o móvil) y volver a ejecutar el comando. Mientras tanto, el contenedor sigue ligado a `main` (cada push a `main` lo reconstruye).

## Lista de cierre (la hace el desarrollador)

- [ ] Crear el rol `integrate_api` en la rama `pruebas` y reemplazar el provisional `integrate_owner` en `.env.demo.pruebas`.
- [ ] Monitor externo gratuito (por ejemplo UptimeRobot) sobre `https://2-31-111-240.sslip.io/api/v1/privacy-policies/current`, cada 5 minutos.
- [ ] Decidir si se borra el servidor duplicado `integrate-server` de Hetzner (apagado, pero se cobra).
- [ ] Al terminar el proyecto: revocar los tokens de Hetzner, SnapDeploy y Cloudflare; regenerar la llave de Google Maps; rotar la contraseña de la rama de Neon. Todos pasaron por el chat.
- [ ] Administrador pendiente: crear la cuenta cuando haya un correo permanente.
