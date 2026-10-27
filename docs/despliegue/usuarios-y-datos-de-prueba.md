# Usuarios y datos de la demostración

Cómo se inicializa la base de la demo desplegada (Neon + SnapDeploy + Cloudflare Pages) y qué cuentas y datos quedan para probar **todos los roles** de la aplicación. Este documento no contiene contraseñas ni datos personales: las credenciales viven solo en archivos locales ignorados por Git (SEC-DIR-004).

## 1. Reiniciar la base desde cero

1. **Borrar los esquemas** de la aplicación en Neon (los roles y las extensiones no se tocan):
   `DROP SCHEMA IF EXISTS notification, audit, offline, voucher, payments, booking, catalog, auth, common CASCADE`.
2. **Reiniciar el backend** (`bash infra/scripts/snapdeploy.sh env` y luego `wake`). Al arrancar, Flyway aplica las 14 migraciones (V1–V14) y recrea las extensiones.
3. **Cargar la política de privacidad vigente** (sin ella nadie puede registrarse): `infra/postgres/seed/demo_privacy_policy.sql` (versión `v0.1`, idempotente). Se carga con `psql` leyendo el archivo en binario.
4. **Crear el ADMIN inicial** con las variables `BOOTSTRAP_ADMIN_*` de `infra/env/.env.demo`: se registra por la API y se le concede el rol por SQL. La app no tiene pantalla para otorgar ADMIN.

## 2. Cuentas de prueba (testers) · todos los roles

Se crean con `python infra/scripts/crear-testers.py`. Las contraseñas se generan al azar y se escriben solo en `infra/env/.env.testers.txt`, que no se sube a Git. Los correos `@integrate-demo.test` son ficticios: no reciben mensajes, así que no sirven para recuperar la contraseña. Si un tester la olvida, el equipo la restablece. La contraseña de `tester2` fue cambiada durante la prueba de recuperación: la vigente está en `.env.testers.txt`.

| Rol | Cuentas | Qué permite probar |
| :--- | :--- | :--- |
| **TOURIST** (turista) | `tester1` a `tester5@integrate-demo.test` | Catálogo, reserva de una franja, pago en modo MOCK con «Simular pago aprobado», «Mis experiencias», voucher en PDF y cancelación. |
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

## 3b. Cuentas reales creadas (estado al 2026-10-27)

Ninguna contraseña de estas cuentas está guardada en el proyecto: la clave temporal se genera al azar y solo sirve para crear la cuenta. Para entrar, cada persona pide un enlace nuevo desde **«¿Olvidaste tu contraseña?»** en `/ingresar`. El enlace vale 15 minutos y de un solo uso (ADR-0014); los enlaces anteriores ya vencieron.

| Rol | Estado |
| :--- | :--- |
| Gestor de enlace | Cuenta creada. Debe pedir un enlace nuevo para entrar. |
| Anfitrión | Cuenta creada. Debe pedir un enlace nuevo para entrar. Tiene aprobada la experiencia «Caminata ecológica y cata de café» con 3 franjas. |
| Administrador (persona real) | Pendiente: el correo entregado era desechable; se creará cuando envíe uno permanente. |

Nombres y correos de estas personas no se publican en el repositorio (es público): están solo en los archivos locales indicados abajo.

Los datos completos de cada persona están en `infra/env/.env.usuarios-reales.json` (y `…parte1.json`), ignorados por Git.

## 4. Flujo completo para probar

1. **Turista (`tester1`):** elige «Caminata ecológica y cata de café», una franja y pulsa «Reservar». Llena los datos (documento, EPS y contacto de emergencia), acepta el consentimiento de datos de salud y pulsa «Apartar cupos».
2. **Pago:** en «Paga tu reserva» pulsa «Pagar» y luego «Simular pago aprobado» (panel «Pasarela de pruebas», ADR-0029). La reserva queda confirmada con su voucher, que se descarga en PDF/A y aparece en «Mis experiencias».
3. **Cupos:** los cupos apartados sin pagar se liberan a los 15 minutos (worker de expiración).
4. **Anfitrión y gestor:** el anfitrión ve la reserva; el gestor de enlace ve la dispersión del 85 % y su jornada.
5. **ADMIN:** revisa la auditoría y aprueba experiencias nuevas.
6. **Recuperación de contraseña (ADR-0014):** «¿Olvidaste tu contraseña?» envía un enlace de un solo uso al correo de la cuenta. Con los correos `@integrate-demo.test` no se puede completar la prueba: se necesita una cuenta con correo real.

## 5. Limpieza al terminar

Borrar de `infra/env/` los archivos locales con credenciales o datos personales (`.env.testers.txt`, `.env.usuarios-reales*.json`, `.env.snapdeploy`, `.env.cloudflare`) y revocar los tokens de SnapDeploy y Cloudflare.
