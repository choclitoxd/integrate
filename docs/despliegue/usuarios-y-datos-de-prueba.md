# Usuarios y datos de la demostración

Cómo se inicializa la base de la demo desplegada (Neon + SnapDeploy + Cloudflare Pages) y qué cuentas y datos quedan para probar **todos los roles** de la aplicación. Este documento no contiene contraseñas ni datos personales: las credenciales viven solo en archivos locales ignorados por Git (SEC-DIR-004).

## 1. Reiniciar la base desde cero

1. **Borrar los esquemas** de la aplicación en Neon (los roles y las extensiones no se tocan):
   `DROP SCHEMA IF EXISTS notification, audit, offline, voucher, payments, booking, catalog, auth, common CASCADE`.
2. **Reiniciar el backend** (`bash infra/scripts/snapdeploy.sh env` y luego `wake`). Al arrancar, Flyway aplica las 14 migraciones (V1–V14) y recrea las extensiones.
3. **Cargar la política de privacidad vigente** (sin ella nadie puede registrarse): `infra/postgres/seed/demo_privacy_policy.sql` (versión `v0.1`, idempotente). Se carga con `psql` leyendo el archivo en binario.
4. **Crear el ADMIN inicial** con las variables `BOOTSTRAP_ADMIN_*` de `infra/env/.env.demo`: se registra por la API y se le concede el rol por SQL. La app no tiene pantalla para otorgar ADMIN.

## 2. Cuentas de prueba (testers) · todos los roles

Se crean con `python infra/scripts/crear-testers.py`. Las contraseñas se generan al azar y se escriben solo en `infra/env/.env.testers.txt`, que no se sube a Git. Los correos `@integrate-demo.test` son ficticios: no reciben mensajes y no sirven para recuperar la contraseña.

| Rol | Cuentas | Qué permite probar |
| :--- | :--- | :--- |
| **TOURIST** (turista) | `tester1` a `tester5@integrate-demo.test` | Catálogo, reserva de una franja, pago en modo MOCK, «Mis experiencias», historial, voucher en PDF y cancelación. |
| **HOST** (anfitrión) | `tester.anfitrion@integrate-demo.test` | Crear experiencias, enviarlas a revisión, gestionar franjas y cupos, ver sus reservas. |
| **LINK_MANAGER** (gestor de enlace) | `tester.gestor@integrate-demo.test` | Gestionar la asociación y a sus anfitriones; ver la dispersión del 85 %. |
| **ADMIN** (administrador) | `tester.admin@integrate-demo.test` | Aprobar experiencias, crear asociaciones, asignar roles, auditoría y reportes. Acceso total. |

El anfitrión y el gestor de prueba pertenecen a la asociación «Asociación de Prueba Integrate» (Armenia, 63001). Los roles nuevos aparecen tras **cerrar sesión y volver a entrar**.

## 3. Usuarios reales y datos de ejemplo

Las personas reales se capturan con el formulario `infra/forms/formulario-usuarios-reales.html` (no envía nada: descarga un `usuarios-reales.json`) y se cargan con `python infra/scripts/crear-usuarios.py --file <archivo> --apply --experiencia`:

- Crea las asociaciones, registra a cada persona con una **contraseña temporal aleatoria que nunca se guarda ni se imprime** y le asigna su rol (gestor de enlace, anfitrión).
- Envía a cada persona un correo de «crear contraseña» (Brevo). Cada una elige su propia clave.
- Con `--experiencia` crea, a nombre de cada anfitrión nuevo, la experiencia de ejemplo «Caminata ecológica y cata de café» (baja intensidad, 180 min, 8 cupos, 60 000 COP), la envía a revisión, la aprueba el ADMIN y abre 3 franjas en los próximos días.
- El informe `*.resultado.json` queda junto al archivo de entrada, sin contraseñas.

El rol **ADMIN** de una persona real solo se concede cuando su correo es permanente; no se otorga a correos desechables.

## 4. Flujo completo para probar

1. El turista (`tester1`) abre la experiencia de ejemplo, elige una franja, reserva y paga en modo MOCK.
2. Descarga el voucher y lo ve en «Mis experiencias».
3. El anfitrión ve la reserva; el gestor de enlace ve la dispersión del 85 %.
4. El ADMIN revisa la auditoría y puede aprobar nuevas experiencias.

## 5. Limpieza al terminar

Borrar de `infra/env/` los archivos locales con credenciales o datos personales (`.env.testers.txt`, `.env.usuarios-reales*.json`, `.env.snapdeploy`, `.env.cloudflare`) y revocar los tokens de SnapDeploy y Cloudflare.
