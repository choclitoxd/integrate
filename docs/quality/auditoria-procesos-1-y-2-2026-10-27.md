# Auditoría del sitio desplegado · Procesos 1 y 2

| Dato | Valor |
| :--- | :--- |
| Fecha | 2026-10-27 |
| Sitio auditado | https://integrate-demo.pages.dev (PWA en Cloudflare Pages) y API en Hetzner (`https://2-31-111-240.sslip.io`) |
| Alcance | **Proceso 1:** ingreso, registro, recuperación de contraseña, consentimiento, portal del gestor y enrolamiento asistido. **Proceso 2:** catálogo, detalle, franjas, datos de viajeros, pago, voucher y «Mis experiencias». |
| Dispositivos | Celular 375 × 812 (emulado) y escritorio 1366 × 800 |
| Cuentas usadas | `tester1@integrate-demo.test` (turista) y `tester.gestor@integrate-demo.test` (gestor) |
| Norma de referencia | `docs/rules/frontend-rules.md` (FE-DIR-001: contraste AAA, áreas táctiles ≥ 44 px, móvil primero) y WCAG 2.2 |

## Resumen

La base de accesibilidad es muy buena: **ninguna de las 14 pantallas revisadas tiene errores de axe-core** (WCAG 2.2 AA, contraste AAA y buenas prácticas), no hay desbordamiento horizontal a 375 px y todos los campos tienen etiqueta. Los problemas están en **funciones que no existen o no funcionan en el despliegue**, en **seguridad de cabeceras** y en **la experiencia de escritorio**.

| Severidad | Cantidad | Lo más importante |
| :--- | :---: | :--- |
| Crítica | 2 | No se puede recuperar la contraseña; no se puede completar el pago desde la PWA. |
| Alta | 7 | Sin cabeceras de seguridad, código fuente público, sin caché, no es instalable, carga lenta, build frágil, roles sin pantallas. |
| Media | 8 | Saltos de diseño, escritorio en columna de celular, formularios que no precargan datos, mensajes de error confusos. |
| Baja | 10 | Detalles de texto, iconos y datos de prueba. |

## Método

1. Recorrido manual de cada pantalla en celular y escritorio, con capturas.
2. axe-core 4.10.2 en cada pantalla, con las etiquetas `wcag2a`, `wcag2aa`, `wcag21aa`, `wcag22aa`, `wcag2aaa` y `best-practice`.
3. Script propio en cada pantalla: desbordamiento horizontal, áreas táctiles menores de 44 px, jerarquía de encabezados, campos sin etiqueta, `autocomplete` e `inputmode`.
4. Métricas del navegador: LCP, CLS, tiempos de las llamadas a la API.
5. `curl` sobre cabeceras HTTP, caché, mapas de código y archivos de PWA.
6. Flujo completo de reserva con la cuenta de prueba: bloqueo de cupo, consentimiento, pago y voucher.

---

## Hallazgos críticos

### C1. No existe la recuperación de contraseña en la PWA

> **Resuelto el 2026-10-27** (T-1.13b, enmienda de ADR-0014): pantallas `/recuperar-contrasena` y `/restablecer-contrasena` y enlace en `/ingresar`, verificadas en la demo de punta a punta.

- **Qué pasa:** la pantalla de ingreso no tiene el enlace «¿Olvidaste tu contraseña?». `/recuperar-contrasena` muestra «Página no encontrada». El correo que envía el backend lleva a `/restablecer-contrasena#token=…`, que tampoco existe.
- **Impacto:** Víctor y Sebastián recibieron ese correo para crear su contraseña y **no pueden entrar**. Cualquier persona que olvide su clave queda bloqueada.
- **Causa:** la tarea `T-1.13b` sigue pendiente en `docs/status/PROJECT_STATUS.md`; el backend (`T-1.13a`) sí está hecho.
- **Arreglo:** implementar `T-1.13b` (enlace en `/ingresar` y vistas `/recuperar-contrasena` y `/restablecer-contrasena`). Mientras tanto, no enviar más correos de restablecimiento a personas reales.
- **Dispositivo:** ambos.

### C2. El pago no se puede completar desde la PWA en la demo

> **Resuelto el 2026-10-27** (ADR-0029): simulador habilitado por `PAYMENT_SIMULATOR_ENABLED` con guarda de proveedor `MOCK`; en la demo, «Simular pago aprobado» responde 200 y llega a «¡Reserva confirmada!».

- **Qué pasa:** en «Paga tu reserva» aparece el panel «Pasarela de pruebas (entorno de desarrollo)». Al pulsar «Simular pago aprobado», la PWA llama a `POST /api/v1/dev/payments/{id}/simulate` y recibe **404**. La pantalla muestra «No encontramos lo que buscas».
- **Causa:** `DevPaymentSimulatorController` solo existe con los perfiles `dev` y `test` (`@Profile({"dev","test"})`). El despliegue corre sin esos perfiles, pero el panel se sigue mostrando.
- **Impacto:** **ningún turista ni tester puede terminar una reserva**. El cupo queda apartado 15 minutos y se libera. Para seguir la auditoría se aprobó el pago con el webhook firmado desde fuera de la PWA.
- **Arreglo (requiere ADR):** habilitar el simulador en la demo mediante una propiedad explícita (por ejemplo `integrate.payment.simulator.enabled=true`, solo con `PAYMENT_PROVIDER=MOCK`), o bien ocultar el panel cuando el simulador no está disponible y explicar cómo pagar.
- **Dispositivo:** ambos.

---

## Hallazgos de prioridad alta

### A1. Faltan cabeceras de seguridad en la PWA

- El HTML no envía `Content-Security-Policy`, `Strict-Transport-Security`, `X-Frame-Options` / `frame-ancestors` ni `Permissions-Policy`. Sí envía `X-Content-Type-Options: nosniff` y `Referrer-Policy`.
- **Riesgo:** la app puede incrustarse en otro sitio (clickjacking) y no hay una política que limite scripts inyectados.
- **Arreglo:** archivo `frontend-pwa/public/_headers` de Cloudflare Pages con CSP, HSTS, `frame-ancestors 'none'` y `Permissions-Policy`. Requiere ADR, porque cambia la política de seguridad.
- La API directa en Caddy tampoco envía HSTS; se agrega con `header Strict-Transport-Security` en el `Caddyfile`.

### A2. El código fuente se publica con los mapas

- `vite.config.ts:52` tiene `build: { sourcemap: true }`. `https://integrate-demo.pages.dev/assets/index-*.js.map` responde 200 con 1,5 MB.
- **Arreglo:** `sourcemap: 'hidden'` (se generan para depurar pero no se referencian) o no subir los `.map` a Pages.

### A3. Los archivos con hash no se guardan en caché

- `/assets/index-*.js` responde `Cache-Control: public, max-age=0, must-revalidate`. Cada visita vuelve a validar todos los archivos.
- **Arreglo:** en `_headers`, `/assets/*` con `Cache-Control: public, max-age=31536000, immutable`.

### A4. Carga lenta del contenido

| Medida | Valor | Meta |
| :--- | :--- | :--- |
| LCP del catálogo (celular) | 4,3 s | < 2,5 s |
| Detalle de la experiencia (API) | 1,26 s | < 0,5 s |
| Categorías (API) | 0,53 s | < 0,3 s |
| Apartar cupos (`/bookings/lock`) | 2,6 s | < 1 s percibido |
| TTFB del HTML | 87 ms | Bien |

- **Causa probable:** el contenido espera a la API, que pasa por Cloudflare → servidor en Alemania → Neon. Si Neon estaba suspendido, el primer acceso suma segundos.
- **Arreglo:** acercar las regiones (Neon y el servidor en la misma región), guardar en caché las respuestas del catálogo y las categorías, y precargar el detalle al tocar la tarjeta.

### A5. La PWA no es instalable

- No hay `manifest.webmanifest` ni service worker. `/manifest.webmanifest`, `/sw.js` y `/robots.txt` devuelven el `index.html` (200, `text/html`) por la regla de SPA.
- **Impacto:** no se puede «Agregar a la pantalla de inicio» ni funciona sin conexión, que es una promesa del proyecto para zonas rurales.
- **Arreglo (requiere ADR):** `vite-plugin-pwa` con manifest, iconos y caché del catálogo; un `robots.txt` real.

### A6. El build de producción se rompe con facilidad

- La PWA necesita `VITE_API_BASE_URL` al compilar. Sin ella el sitio muestra «Configuración incompleta», y en Git Bash `/api` se convierte en `C:/Program Files/Git/api`. Ambas cosas pasaron hoy en producción.
- **Arreglo:** versionar `frontend-pwa/.env.production` con `VITE_API_BASE_URL=/api` (con una excepción en `.gitignore`), o un valor por defecto `/api` en producción. Ver `docs/despliegue/despliegue-pwa-cloudflare.md`.

### A7. Los roles de anfitrión y administrador no tienen pantallas

- El router solo tiene rutas de turista y de gestor. No hay pantallas para que un anfitrión cree experiencias ni para que un administrador las apruebe.
- **Impacto:** las cuentas `tester.anfitrion` y `tester.admin` solo sirven por la API. La descripción de la experiencia de ejemplo dice «puede editarla desde su cuenta», lo cual hoy no es cierto.
- **Arreglo:** dejarlo explícito en `docs/despliegue/usuarios-y-datos-de-prueba.md` y planear esas pantallas en la fase siguiente.

---

## Hallazgos de prioridad media

| ID | Hallazgo | Dónde | Arreglo |
| :--- | :--- | :--- | :--- |
| M1 | **CLS 0,154** en el detalle al recargar: el contenedor principal salta cuando el esqueleto se cambia por el contenido. | Detalle, celular | Dar al esqueleto la misma altura que el contenido real. |
| M2 | En escritorio, reserva, pago, confirmación, «Mis experiencias», «Mi cuenta», ingreso y portal del gestor se ven como **una columna de celular centrada** con mucho espacio vacío. Solo catálogo, detalle y franjas tienen vista de escritorio (ADR-0020). | Escritorio | Vistas de dos columnas: resumen fijo a la derecha en el pago, QR y ficha lado a lado en el voucher, cuadrícula en «Mis experiencias». |
| M3 | Los **datos del titular no se precargan**: el formulario de viajeros pide otra vez el documento aunque la cuenta ya lo tiene. Solo precarga el nombre. | Datos de los viajeros | Precargar documento y celular del perfil. |
| M4 | **3 s de esqueleto en blanco** entre «Acepto» y «Paga tu reserva» mientras se apartan los cupos. | Reserva | Mensaje de progreso: «Apartando tus cupos…». |
| M5 | **El mensaje de error del pago es engañoso**: «No encontramos lo que buscas» ante el fallo del simulador. | Pago | Mensaje específico de pago con siguiente paso. |
| M6 | **La cuenta regresiva deja de verse** al bajar hasta «Pagar» (no hay barra fija, por FE-DIR-001). | Pago, celular | Repetir el tiempo restante junto al botón «Pagar». |
| M7 | **«Mi cuenta» no permite** editar datos, cambiar la contraseña ni revocar autorizaciones. Las tarjetas «Autorizado» tienen borde de botón pero no hacen nada. | Mi cuenta | Agregar esas acciones o quitar el aspecto de botón. |
| M8 | La **política de privacidad vigente** se llama «borrador de demostración, sin validez legal», y ya hay **usuarios reales** registrados. | Registro y consentimiento | Publicar una política real antes de seguir sumando personas reales (Ley 1581). |

---

## Hallazgos de prioridad baja

| ID | Hallazgo | Arreglo |
| :--- | :--- | :--- |
| B1 | «Duración 3 h» e «Ida y vuelta 3 h» usan el mismo icono de reloj y repiten el mismo dato. | Ocultar «ida y vuelta» cuando es igual a la duración, o usar otro icono. |
| B2 | «ARMENIA» en mayúsculas en «Mis experiencias», frente a «Armenia, Quindío» en el resto. | Mostrar municipio y departamento con el mismo formato. |
| B3 | El campo EPS muestra la flecha nativa de `datalist` (▼), distinta del resto de listas. | Unificar el estilo. |
| B4 | Texto del consentimiento: «…de quienes viajan. solo para…» (minúscula tras punto). | Corregir el texto. |
| B5 | El selector de mes se corta: «Octubre – noviembre de …» a 375 px. | Abreviar («oct – nov 2026»). |
| B6 | Las tarjetas y el detalle **no tienen fotos**. En turismo, la imagen es lo primero que se mira. | Fotos con `srcset`, `width`/`height` y carga diferida. |
| B7 | `public/manual.html` (1,3 MB) se publica además de `/manual/`. | Borrar el duplicado. |
| B8 | El «Panel de jornada» anuncia «Pronto descargarás aquí el padrón…», una función que aún no existe. | Quitar o marcar como «próximamente». |
| B9 | Datos de prueba: la asociación real tiene nombre de persona y dirección de prueba (`bbasadqwee`). | Pedir los datos correctos. |
| B10 | El enlace «Saltar al contenido» mide 32 × 24 px cuando aparece con el teclado. | Subir a 44 px de alto. |

Nota: axe marcó una vez un contraste de 2,83 en `/registro`, pero no se repitió al volver a medir (probablemente una transición). No se cuenta como hallazgo.

---

## Lo que está bien

- **Accesibilidad:** 0 errores de axe-core en 14 pantallas (WCAG 2.2 AA y contraste AAA). `lang="es-CO"`, un solo `h1` por pantalla, enlace para saltar al contenido, landmarks, etiquetas en todos los campos.
- **Formularios:** `autocomplete` correcto (`username`, `current-password`, `new-password`, `name`, `tel-national`, `email`), `inputmode` numérico y de teléfono, errores por campo y foco en el primer error.
- **Celular:** sin desbordamiento horizontal a 375 px, áreas táctiles ≥ 44 px en todos los controles y navegación inferior clara por rol.
- **Proceso 2:** cupos en tiempo real, cuenta regresiva de 15 minutos, desglose del 85 % para la comunidad, voucher con QR, código de contingencia y PDF.
- **Proceso 1:** el portal del gestor y el formulario de enrolamiento en tres pasos son claros y avisan del estado de conexión.
- **Peso:** el JS principal pesa 285 KB (88 KB comprimido) y el HTML responde en 87 ms.
- **API:** respuestas `application/problem+json`, `no-store` y `X-Frame-Options: DENY`.

---

## Orden recomendado

1. **C1 y C2:** sin ellos no se puede entrar ni pagar. Son lo primero que verá un evaluador.
2. **A6 y A2:** son cambios de una línea que evitan romper el sitio y dejar el código expuesto.
3. **A1 y A3:** un solo archivo `_headers` (con ADR).
4. **A4, M1 y M4:** que la app se sienta rápida en celular.
5. **A5:** hacerla instalable y con caché sin conexión.
6. **M2:** vistas de escritorio para el pago, el voucher y las listas.
7. Resto de medios y bajos.

## Datos que dejó la auditoría

- Reserva **confirmada** de `tester1` para el viernes 30 de octubre, 8:00 a. m. (`38e00b41-70cb-4039-b470-084a93d555a3`). Consume 1 de los 8 cupos de esa franja.
- El pago se aprobó con el webhook firmado (`evt-audit-1`) porque el simulador de la PWA no responde (C2).
- El correo del voucher salió hacia `tester1@integrate-demo.test`, un correo ficticio.
