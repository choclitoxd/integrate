# Guía de pruebas del ciclo de vida de cada usuario · computador y celular

Prueba de aceptación de punta a punta de Intégrate, **rol por rol**, desde que la cuenta se crea hasta que la persona
sale o pierde el acceso. Cada paso se hace **dos veces**: en un **computador (🖥)** y en un **celular real (📱)**, porque
la aplicación se ve y se comporta distinto en cada uno. Cada paso dice qué hay que hacer, **qué debe ver** la persona,
y, si algo falla, **por qué probablemente falló**.

Complementa la suite automática ([evidencias-metricas.md](evidencias-metricas.md)): aquí se prueba lo que ve y hace una
persona real, con su teléfono, su señal y su navegador.

> **Versión:** 30 de octubre de 2026 · Fase 2.2 (Proceso 3 offline del Gestor incluido). Reemplaza al guion del 18 de
> octubre: ya existen la recuperación de contraseña, «Mis experiencias» y el portal offline del Gestor.

## Contenido

1. [Cómo usar esta guía](#1-cómo-usar-esta-guía)
2. [Preparación](#2-preparación)
3. [Qué debe tener y qué debe ver cada rol](#3-qué-debe-tener-y-qué-debe-ver-cada-rol)
4. [Visitante (sin cuenta)](#4-visitante-sin-cuenta)
5. [Turista](#5-turista)
6. [Administrador](#6-administrador)
7. [Gestor de Enlace](#7-gestor-de-enlace)
8. [Anfitrión comunitario](#8-anfitrión-comunitario)
9. [Ciclo completo encadenado](#9-ciclo-completo-encadenado)
10. [Control de acceso cruzado](#10-control-de-acceso-cruzado)
11. [Por qué falla: del mensaje a la causa](#11-por-qué-falla-del-mensaje-a-la-causa)
12. [Registro de errores y cierre](#12-registro-de-errores-y-cierre)
13. [Límites conocidos de esta versión](#13-límites-conocidos-de-esta-versión)

---

## 1. Cómo usar esta guía

### 1.1 Cómo se marca cada paso

Cada paso es una fila con dos casillas, una por dispositivo:

| Columna | Qué poner |
|---|---|
| 🖥 | `✔` si en el computador pasó exactamente lo de «Debe ver». `✘` si no. `—` si el paso no aplica al computador. |
| 📱 | Igual, en el celular. |
| **Si falla, por qué** | **No se llena:** es la causa probable que la guía ya trae. Úsela para clasificar el error. |

Un paso es `✔` **solo si pasó todo lo de «Debe ver»**, no si «más o menos funcionó». Si hay una diferencia (un texto
distinto, un botón que no responde, algo cortado), es `✘`.

### 1.2 Qué hacer cuando un paso falla (`✘`)

1. **No lo corrija ni lo repita hasta que funcione.** Termine el bloque y reporte; el primer intento es la evidencia.
2. **Tome una captura** de la pantalla completa (en el celular: botón de captura del teléfono).
3. Si la pantalla muestra un **«Código de seguimiento»** (una cadena de 32 letras y números), **cópielo**: con él se
   encuentra en el log del backend la petición exacta que falló.
4. Anote una fila en la [sección 12](#12-registro-de-errores-y-cierre): el paso, el dispositivo, **qué vio** (texto
   literal) y **por qué cree que pasó**, usando la columna «Si falla, por qué» y la [sección 11](#11-por-qué-falla-del-mensaje-a-la-causa).
5. Clasifique el error: **defecto de la app** (lo que se ve no es lo que el documento dice que debe verse), **dato o
   cuenta de prueba** (la cuenta no tenía el rol o el dato que el paso necesita), **entorno** (red, certificado,
   servidor dormido) o **expectativa del documento** (la guía está desactualizada).

### 1.3 Diferencia esperada entre 🖥 y 📱

No son errores:

| Tema | 🖥 Computador (ancho ≥ 1024 px) | 📱 Celular (360–430 px) |
|---|---|---|
| Catálogo, detalle y franjas | Vista ancha: filtros en columna lateral y experiencias en cuadrícula de tres columnas. | Una columna; **Filtros** abre una hoja inferior. |
| Resto de pantallas (reserva, pago, voucher, cuenta, ingreso, portal del Gestor) | Una columna centrada de ancho de celular, con espacio vacío a los lados. | Una columna a todo el ancho. |
| Menú | Barra de navegación de la aplicación. | Barra inferior con íconos. |
| Botón principal | Dentro del contenido, justo después de los datos que confirma (no es una barra fija al fondo). | Igual. |
| Cámara del lector QR | Normalmente no hay cámara trasera: se prueba el campo «pegar el token». | Cámara trasera. |
| Aviso de instalación | El navegador puede ofrecer «Instalar». | «Agregar a la pantalla de inicio». |

---

## 2. Preparación

### 2.1 Dónde probar (elija **uno** y úselo en todo el recorrido)

| Entorno | Dirección | Cuándo usarlo |
|---|---|---|
| **Pruebas** (recomendado) | `https://pruebas.integrate-demo.pages.dev` | Prueba completa desde el celular con HTTPS real, correos reales y base propia. **No** use producción. |
| Local | `http://localhost:5173` (computador) y `https://<IP-del-equipo>:5173` (celular, con `npm run dev:https`) | Cuando va a mirar logs o la base de datos. |

> **Nunca pruebe en producción** (`https://integrate-demo.pages.dev`): hay usuarios reales.
> **La base del entorno de pruebas es una copia de la de producción** con personas reales: no modifique, suspenda ni
> escriba a ninguna cuenta que no sea de prueba.

**Por qué el celular necesita HTTPS:** la cookie de sesión es `Secure`. Por `http://<IP>` el celular no la guarda y la
sesión **se pierde al recargar o a los 15 minutos** (esto se ve como «me sacó de la sesión» y es un problema del
entorno, no de la app). En local use `npm run dev:https` (ver [guía de desarrollo](../guia-desarrollo.md#probar-desde-un-celular-https-de-desarrollo)):
el celular avisa que el certificado no es de confianza; se acepta una vez.

### 2.2 Dispositivos

| Dispositivo | Requisitos |
|---|---|
| 🖥 Computador | Chrome o Edge actualizado, ventana **maximizada (≥ 1280 px)**. Abra las herramientas de desarrollador (F12) para ver errores en la pestaña *Consola* y *Network*. |
| 📱 Celular **real** | iPhone (Safari o Chrome; **en iPhone, Chrome usa el mismo motor que Safari**) o Android (Chrome). **El modo de dispositivo del computador no sustituye al celular real**: no reproduce el teclado, la cámara, el modo avión ni los fallos propios de Safari. |
| 📱 Pantalla del celular | Brillo alto y orientación vertical. Para el paso del QR use un segundo dispositivo que muestre el voucher. |

### 2.3 Cuentas

Las contraseñas **no están en este documento ni deben copiarse en él, en capturas ni en Jira.**

| Entorno | Cuentas | Dónde está la contraseña |
|---|---|---|
| Pruebas | `tester1` a `tester5@integrate-demo.test` (turistas), `tester.anfitrion@…` (anfitrión), `tester.gestor@…` (gestor), `tester.admin@…` (administrador); todos `@integrate-demo.test` | `infra/env/.env.testers.txt` (ignorado por Git). Se generan con `python infra/scripts/crear-testers.py`. |
| Local | `turista1…3`, `gestor1…2`, `anfitrion1…3`, `admin` `@ejemplo.com` (semilla `infra/postgres/seed/dev_test_data.sql`) | `infra/env/.env.test-users` y la hoja `infra/env/.env.test-users.tabla.md` (ignorados por Git). |
| Cuentas nuevas | Las que cree durante la prueba: `qa.<rol>.<AAAAMMDD>@<dominio>` | Una contraseña propia de **12 o más caracteres**. |

**Dominios ficticios (`@integrate-demo.test`, `@ejemplo.com`) no reciben correo.** Para los pasos que exigen leer un
correo (recuperar contraseña, voucher) use **un buzón real suyo** en el entorno de pruebas, o Mailpit
(`http://localhost:8025`) en local.

### 2.4 Ayudante de PowerShell para lo que aún no tiene pantalla

El Administrador, la creación de experiencias del Anfitrión y la **cancelación de reservas** no tienen pantallas en la
PWA: se prueban por la API. Pegue esto una vez por sesión de PowerShell (la contraseña se pide oculta y no se guarda):

```powershell
$Base = 'https://pruebas.integrate-demo.pages.dev/api/v1'   # local: 'http://localhost:8080/api/v1'
function Login([string]$email) {
  $pw = Read-Host "Contraseña de $email" -AsSecureString
  $plain = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($pw))
  $r = Invoke-RestMethod -Method Post "$Base/auth/login" -ContentType 'application/json' -Body (@{ email = $email; password = $plain } | ConvertTo-Json)
  "id: $($r.user.id)  roles: $($r.user.roles -join ', ')"; return $r.accessToken
}
function Api([string]$method, [string]$path, [string]$token, $body = $null) {
  $h = @{}; if ($token) { $h.Authorization = "Bearer $token" }
  $json = if ($body) { $body | ConvertTo-Json -Depth 6 } else { $null }
  try {
    $r = Invoke-WebRequest -UseBasicParsing -Method $method "$Base$path" -Headers $h -ContentType 'application/json' -Body $json
    [pscustomobject]@{ Status = [int]$r.StatusCode; Body = if ($r.Content) { $r.Content | ConvertFrom-Json } }
  } catch {
    $resp = $_.Exception.Response; $text = (New-Object IO.StreamReader($resp.GetResponseStream())).ReadToEnd()
    [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = if ($text) { $text | ConvertFrom-Json } }
  }
}
```

Uso: `$adm = Login 'tester.admin@integrate-demo.test'` y luego `Api POST '/admin/associations' $adm @{ ... }`.
Revise siempre `Status` y, si es error, `Body.code` y `Body.traceId`.

---

## 3. Qué debe tener y qué debe ver cada rol

Cada persona debe **tener** (roles, datos y permisos) y **ver** (menú y pantallas) esto. Si algo difiere, es un error
en el paso de «primer ingreso» del rol.

| | Visitante | Turista | Administrador | Gestor de Enlace | Anfitrión (enrolado) |
|---|---|---|---|---|---|
| **Cómo se crea** | No tiene cuenta. | Se registra solo (`/registro`). | El equipo lo configura (variables de arranque); nadie se registra como administrador. | El Administrador **acredita a un usuario existente** (por API). | Su Gestor lo **enrola** (3 pasos + PDF) y le entrega un código; el anfitrión **activa** su cuenta. |
| **Roles que debe tener** | — | `Turista` | `Administrador` y `Turista` | `Gestor de Enlace` y `Turista` | **Solo** `Anfitrión comunitario` |
| **Se ve en «Mi cuenta» como** | — | «Turista» | «Turista · Administrador» | «Turista · Gestor de Enlace» | «Anfitrión comunitario» |
| **Menú** | Explorar · Ingresar | Explorar · **Mis experiencias** · Mi cuenta | Explorar · **Mis experiencias** · Mi cuenta | Explorar · Mis experiencias · **Gestor** · Mi cuenta. Dentro del portal: Jornada · Validar QR · Anfitriones · Mi cuenta | Explorar · Mi cuenta (**sin** Mis experiencias ni Gestor) |
| **Autorizaciones de datos** | — | Datos personales (obligatoria) y salud (se pide al reservar) | Datos personales (y salud si reserva) | Datos personales (y salud si reserva) | Datos personales y **condición de víctima** (las deja el enrolamiento); salud solo si reservara |
| **Puede** | Explorar, filtrar, ver detalle y franjas. | Todo lo del visitante + reservar, pagar, voucher, Mis experiencias, cancelar (API). | Todo lo del turista + moderar experiencias, acreditar gestores, crear asociaciones y anfitriones, consultar auditoría (API). | Todo lo del turista + enrolar anfitriones, emitir códigos, descargar el padrón, validar QR sin red, canje manual y sincronizar. | Ingresar, explorar, ver su cuenta; crear y gestionar sus experiencias y franjas (API). |
| **No debe poder** | Reservar, ver cuenta. | Entrar a `/gestor`; llamar a rutas de admin, host o gestor. | Entrar a `/gestor`; alterar la bitácora de auditoría. | Aprobar experiencias o acreditar gestores; ver datos de víctima. | **Reservar** (no es turista); entrar a `/gestor`; ver datos de víctima. |

> **Confirme en el primer ingreso de cada rol** que «Mi cuenta» muestra exactamente los roles de la fila «Se ve en».
> Un rol de más o de menos es un defecto de **datos** (cuenta mal creada) o de **permisos**, y bloquea el resto del bloque.

---

## 4. Visitante (sin cuenta)

Cierre la sesión primero. En 📱 use una pestaña nueva; en 🖥 una ventana de incógnito.

| # | Acción | Debe ver | 🖥 | 📱 | Si falla, por qué |
|---|---|---|---|---|---|
| V-01 | Abrir la dirección de la aplicación. | Catálogo con las experiencias aprobadas y la barra **Explorar · Ingresar**. En 🖥, vista ancha con filtros laterales. | ✔ | ✔ | «Configuración incompleta»: la PWA se compiló sin `VITE_API_BASE_URL`. Sin experiencias: la API está dormida (primer acceso del entorno de pruebas, hasta ~1 min) o la base no tiene datos. |
| V-02 | Pulsar **Tab** una vez (🖥) / conectar teclado (📱). | El primer foco es «Saltar al contenido principal», con anillo amarillo de doble borde. | ✔ | — | Falta de `SkipLink`: defecto de accesibilidad (FE-DIR-001). |
| V-03 | Buscar por una palabra del título y tocar la lupa. | Solo experiencias que coinciden. | ✔ | ✔ | Resultados sin filtrar: la búsqueda no llega al servidor (revise *Network*). |
| V-04 | Buscar `zzzz`. | Mensaje de «sin resultados», sin errores en consola. | ✔ | ✔ | Pantalla en blanco: error no controlado en el catálogo. |
| V-05 | Filtros → Departamento. | **Municipio** se habilita con los municipios de ese departamento. | ✔ | ✔ | 📱: el panel se ve **casi vacío** (solo el título y los botones): defecto conocido de Safari/iOS con la altura de la hoja, ya corregido; si reaparece, hay una regresión en `BottomSheet`. |
| V-06 | Elegir Municipio, Categoría e Intensidad «Alta» → **Ver resultados**. | Los resultados respetan los tres filtros y aparecen como filtros activos. **Limpiar** los quita. | ✔ | ✔ | Filtro ignorado: parámetro mal enviado a la API. |
| V-07 | Abrir una experiencia. | Descripción, **punto de encuentro con mapa** y enlace, indicaciones, datos del recorrido y precio por persona. **No** aparece documento, correo, teléfono ni condición de víctima del anfitrión. | ✔ | ✔ | Mapa vacío: el geocodificador no respondió (el sitio usa el de respaldo). Dato personal visible: **defecto de seguridad (alta)**. |
| V-08 | Pulsar **Atrás**. | Vuelve al catálogo con filtros y posición. | ✔ | ✔ | Filtros perdidos: estado de la URL no conservado. |
| V-09 | **Ver fechas** → Personas con **+** hasta el tope. | No pasa de 10 personas; los días agotados están tachados. | ✔ | ✔ | Permite más de 10: validación de cliente rota (el servidor igual lo rechazaría). |
| V-10 | Elegir día y franja → **Reservar**. | Lleva a **Ingresar**; la dirección conserva el destino (`volver=`). | ✔ | ✔ | Cae en el catálogo sin pedir ingreso: la guarda de rutas no actuó. |
| V-11 | Abrir `/no-existe`. | «Página no encontrada» con botón para volver. | ✔ | ✔ | Pantalla en blanco: falta la ruta comodín. |
| V-12 | Abrir `/gestor` y `/cuenta` sin sesión. | Pide ingresar (no muestra el portal ni la cuenta). | ✔ | ✔ | Muestra el panel: **defecto de seguridad (alta)**. |
| V-13 | Cambiar el ancho de la ventana (🖥) entre 1280 y 390 px; girar el celular (📱). | Sin desplazamiento horizontal; botones de al menos 44 px; el diseño cambia entre vista ancha y de celular. | ✔ | ✔ | Desplazamiento horizontal: algún elemento con ancho fijo. |

---

## 5. Turista

Recorrido: **crear cuenta → primer ingreso → recuperar contraseña → explorar → reservar → pagar → voucher → Mis
experiencias → cancelar → salir**. Use correos y documentos nuevos en el registro.

### 5.1 Crear la cuenta

| # | Acción | Debe ver | 🖥 | 📱 | Si falla, por qué |
|---|---|---|---|---|---|
| T-01 | **Ingresar** → **Crea una gratis**. | «Crea tu cuenta»: nombre, tipo y número de documento, celular **con indicativo por país**, correo, contraseña y «Autorización de datos». | ✔ | ✔ | Falta el indicativo: regresión de T-1.14b. |
| T-02 | **Crear cuenta** sin llenar nada. | Cada campo obligatorio con su mensaje; el foco va al primer error; no se envía. | ✔ | ✔ | Se envía: validación de cliente rota. 📱: el teclado tapa el error → la vista no se desplaza al campo. |
| T-03 | Correo sin `@`; contraseña de 8 caracteres; documento CC con letras. | Un error claro por cada campo (correo no válido, mínimo 12 caracteres, solo números). | ✔ | ✔ | Mensaje en inglés o genérico: el texto del servidor no está traducido. |
| T-04 | Todo bien **sin** marcar «Mis datos personales». | No deja crear la cuenta (autorización obligatoria). | ✔ | ✔ | Crea la cuenta sin consentimiento: **defecto legal (alta)**, Ley 1581. |
| T-05 | Todo bien con «Mis datos personales» (sin salud) → **Crear cuenta**. | Cuenta creada y sesión abierta: lo lleva a la app con **Mi cuenta** en el menú. | ✔ | ✔ | 409/«ya existe»: el correo o el documento ya está registrado (use uno nuevo). 422: algún campo no cumple el formato que pide el servidor. |
| T-06 | **Cerrar sesión** y repetir el registro con el **mismo correo** (o el mismo documento). | Aviso de que la cuenta ya existe; no se crea un duplicado. | ✔ | ✔ | Crea dos cuentas: falta la restricción de unicidad. |

### 5.2 Primer ingreso y sesión

| # | Acción | Debe ver | 🖥 | 📱 | Si falla, por qué |
|---|---|---|---|---|---|
| T-10 | **Mi cuenta** recién creada. | Nombre, correo, rol **«Turista»**; autorización de datos personales «Autorizado» y la de salud «Sin autorizar» con el botón **Autorizar datos de salud para reservar**. | ✔ | ✔ | Rol de más o de menos: cuenta creada con roles incorrectos. |
| T-11 | Menú del turista. | Explorar · **Mis experiencias** · Mi cuenta. | ✔ | ✔ | Falta Mis experiencias: la sesión no trae `ROLE_TOURIST`. |
| T-12 | Ingresar con la contraseña **incorrecta**. | «El correo o la contraseña no coinciden», sin decir cuál falló. | ✔ | ✔ | Dice «contraseña incorrecta»: revela qué correos existen (**seguridad**). |
| T-13 | Ingresar con un correo que **no existe**. | El **mismo** mensaje que T-12. | ✔ | ✔ | Mensaje distinto: ídem. |
| T-14 | Ingresar bien. | Entra; si venía de una reserva, vuelve a ella. | ✔ | ✔ | Se queda en Ingresar sin error: la API no responde (revise *Network*). «Sin conexión»: el proxy `/api` no llega al backend. |
| T-15 | Recargar la página (F5 / deslizar para actualizar). | **Sigue con la sesión abierta**, sin pedir la contraseña. | ✔ | ✔ | Pide ingresar otra vez: 📱 en `http://<IP>` la cookie `Secure` no se guarda (**entorno**: use HTTPS). En HTTPS: la cookie de refresh no viaja (`Path=/api/v1/auth`, `SameSite=Strict`) → defecto. |
| T-16 | Herramientas de desarrollador → *Application* → Local/Session Storage e IndexedDB (🖥). | **Ningún token de acceso** guardado. | ✔ | — | Token en Storage: **defecto de seguridad (alta)**, FE-DIR-001 D4. |
| T-17 | Con una cuenta **nueva** (no las de la semilla): 5 contraseñas incorrectas seguidas; luego la correcta. | Al quinto intento, aviso de cuenta bloqueada temporalmente; la correcta tampoco entra durante el bloqueo. | ✔ | ✔ | Nunca bloquea: no se cuentan los fallos. Bloquea una cuenta de prueba compartida: use una cuenta propia. |
| T-18 | Dejar la sesión abierta **más de 15 minutos** y navegar. | La sesión se renueva sola; nada se interrumpe. | ✔ | ✔ | Redirige a Ingresar a los ~15 min: la renovación falla (cookie no guardada, ver T-15). |

### 5.3 Recuperar la contraseña

Use una cuenta con **correo real suyo** (los dominios ficticios no reciben correo).

| # | Acción | Debe ver | 🖥 | 📱 | Si falla, por qué |
|---|---|---|---|---|---|
| T-20 | En **Ingresar**, tocar «¿Olvidaste tu contraseña?». | «**Recupera tu contraseña**» con el campo Correo y **Enviar enlace**. | ✔ | ✔ | No existe el enlace: regresión de T-1.13b. |
| T-21 | Enviar con un correo registrado. | «**Revisa tu correo**»: «Si el correo está registrado, te enviamos un enlace válido por 15 minutos.» | ✔ | ✔ | Error 5xx: el correo no se pudo encolar (SMTP o `FRONTEND_BASE_URL`). |
| T-22 | Repetir con un correo que **no existe**. | **El mismo mensaje** y **no** llega correo. | ✔ | ✔ | Mensaje distinto: revela qué cuentas existen (**seguridad**). |
| T-23 | Abrir el correo recibido. | Llega en pocos minutos, con un enlace a `/restablecer-contrasena#token=…`. | ✘ | ✘ | No llega: spam; `MAIL_FROM`/SMTP mal configurado; o la cuenta tiene correo sintético (`@comunidad.integrate.co`, ver H-30). |
| T-24 | Abrir el enlace. | «**Crea tu nueva contraseña**» con dos campos. | ✔ | ✔ | «Página no encontrada»: el enlace apunta a otro dominio (`FRONTEND_BASE_URL` equivocado). |
| T-25 | Las dos contraseñas distintas, o de menos de 12. | Error en el campo; no se guarda. | ✔ | ✔ | Guarda igual: validación de cliente rota. |
| T-26 | Guardar una contraseña válida. | Lo lleva a Ingresar con el aviso «**Tu contraseña cambió. Ingresa con la nueva.**» | ✘ | ✘ | Sigue en el formulario con error 401: el token venció (15 min) o ya se usó. |
| T-27 | Ingresar con la contraseña **vieja** y luego con la **nueva**. | La vieja falla; la nueva entra. Las sesiones abiertas en otros dispositivos se cierran. | ✘ | ✘ | La vieja sigue sirviendo: **defecto de seguridad (alta)**. |
| T-28 | Abrir otra vez el **mismo enlace**. | «El enlace venció o ya se usó. Pide uno nuevo: dura 15 minutos.» | ✔ | ✔ | Permite reusarlo: **defecto de seguridad (alta)**. |
| T-29 | Revisar el correo. | Llega el aviso «contraseña cambiada». | ✘ | ✘ | No llega: la cola de correo (`outbox`) no se despacha. |

### 5.4 Explorar y elegir una franja

| # | Acción | Debe ver | 🖥 | 📱 | Si falla, por qué |
|---|---|---|---|---|---|
| T-30 | Catálogo con sesión iniciada. | Las mismas experiencias que el visitante; precios en pesos con separador de miles. | ✔ | ✔ | Precio sin formato: falla de `formatCop`. |
| T-31 | Detalle → **Ver fechas**. | Selector de **personas** (tope 10), calendario con días con cupo y días tachados, lista de **franjas** con cupos y el **total**. | ✔ | ✔ | Sin franjas: la experiencia no tiene franjas abiertas futuras (dato). |
| T-32 | Elegir 2 personas, un día y una franja. | El total es precio × personas; el botón **Reservar** queda dentro del contenido, no como barra fija. | ✔ | ✔ | Total incorrecto: defecto de cálculo (RNF de adecuación). |
| T-33 | **Reservar**. | Lleva a «Datos de los viajeros», con el resumen de la franja. | ✔ | ✔ | Vuelve a Ingresar: la sesión se perdió (ver T-15, T-18). |

### 5.5 Datos de los viajeros y apartar cupos

Use primero una cuenta **sin** autorización de salud (la de T-05).

| # | Acción | Debe ver | 🖥 | 📱 | Si falla, por qué |
|---|---|---|---|---|---|
| T-40 | Entrar al formulario. | Una tarjeta por viajero; el **titular** con su nombre; un aviso azul: te pediremos la autorización de salud al apartar. | ✔ | ✔ | Pregunta el documento aunque la cuenta ya lo tiene: precarga parcial conocida (solo nombre). |
| T-41 | **Apartar cupos** con todo vacío. | Foco en el primer campo con error; ningún envío a la API. | ✔ | ✔ | 📱 «no pasa nada»: el error quedó fuera de pantalla o tapado por el teclado → debe desplazarse solo al aviso. |
| T-42 | EPS vacía del viajero 2; mismo documento en los dos viajeros; contacto de emergencia con celular `123`. | Un error por campo: «Este documento ya está en otro viajero del grupo», celular inválido, etc. | ✔ | ✔ | El servidor rechaza con 422 lo que el cliente dejó pasar: la validación de cliente quedó desalineada. |
| T-43 | Llenar todo bien → **Apartar cupos**. | Abre «**Autorización necesaria**» (datos de salud y contacto de emergencia) con casilla y botones **Ahora no** / **Acepto**. | ✔ | ✔ | 📱 No se abre o se ve cortado: altura de la hoja en Safari (ver V-05). Reservó sin preguntar: defecto de consentimiento. |
| T-44 | Marcar la casilla → **Acepto**. | Continúa **sin volver a llenar** el formulario y pasa a «Paga tu reserva». Mi cuenta: salud «Autorizado». | ✔ | ✔ | Pierde lo escrito: se limpió el estado al abrir el diálogo. |
| T-45 | Con **Atrás** y datos escritos, o recargando. | Avisa que los datos se perderán; tras recargar, los datos de viajeros **no** reaparecen. | ✔ | ✔ | Reaparecen: se guardaron en el teléfono (**viola FE-DIR-001 D4**). |
| T-46 | Tocar el botón dos veces rápido. | Una sola reserva (no dos). | ✔ | ✔ | Dos reservas: falla la idempotencia. |
| T-47 | Intentar apartar más cupos de los que quedan. | «Solo quedan N cupos en esta franja» (o «ya no está disponible») **a la vista**, con enlace para elegir otra franja. | ✔ | ✔ | El aviso existe pero no se ve: debe desplazarse hasta él. 404: la franja ya no existe (otro usuario la cerró o se reinició la base). |

### 5.6 Pagar

| # | Acción | Debe ver | 🖥 | 📱 | Si falla, por qué |
|---|---|---|---|---|---|
| T-50 | Pantalla «Paga tu reserva». | **Contador de 15 minutos** que baja, desglose, «El 85 % va a la asociación comunitaria» y métodos **PSE · Tarjeta · Nequi · Daviplata**. | ✔ | ✔ | Contador en 00:00 al llegar: el reloj del teléfono desfasado no debe importar (usa el del servidor); si ocurre, defecto. |
| T-51 | En otro dispositivo, abrir la misma franja como visitante. | Los cupos **ya descuentan** lo apartado. | ✔ | ✔ | No descuenta: el bloqueo de aforo no se aplicó. |
| T-52 | Elegir **PSE** → **Pagar** → **Simular pago aprobado** (solo entornos con la pasarela de pruebas). | «**¡Reserva confirmada!**» con **QR**, código de contingencia de 8 caracteres y ficha de viaje. | ✔ | ✔ | 404 «No encontramos lo que buscas» al simular: el simulador está apagado en el servidor (`PAYMENT_SIMULATOR_ENABLED=false`) pero el panel se muestra. |
| T-53 | Otra reserva → **Simular pago rechazado**. | Informa el rechazo y ofrece volver a reservar; los cupos **vuelven** al catálogo. | ✔ | ✔ | Los cupos quedan bloqueados 15 min: el rechazo no los libera. |
| T-54 | Otra reserva y **no pagar**: esperar. | En el último minuto, el aviso «Último minuto…»; a las 00:00 la reserva **vence** y ofrece reservar de nuevo. | ✔ | ✔ | 📱 El contador deja de verse al bajar hasta «Pagar»: conocido (sin barra fija). |
| T-55 | Pasado el vencimiento, intentar pagar volviendo atrás. | No permite pagar; muestra «Tiempo para pagar vencido». Los cupos están libres en el catálogo (el barrido puede tardar hasta 1 min). | ✘ | ✘ | Permite pagar una reserva vencida: **defecto grave** (cobro sin cupo). |

### 5.7 Voucher y Mis experiencias

| # | Acción | Debe ver | 🖥 | 📱 | Si falla, por qué |
|---|---|---|---|---|---|
| T-60 | **Mostrar QR a pantalla completa**. | QR grande, nítido, que se cierra con el botón o Esc. | ✔ | ✔ | QR cortado: tamaño fijo mayor que la pantalla. |
| T-61 | Leer el QR con otro celular. | Texto largo de tres partes separadas por puntos (token firmado). **No** contiene nombres ni EPS. | ✔ | ✔ | Contiene datos personales: **defecto de privacidad**. |
| T-62 | **Descargar voucher (PDF/A)**. | Descarga un PDF con título, fecha, punto de encuentro, titular, cupos, QR y código de contingencia. | ✔ | ✔ | 📱 iPhone: abre en el visor sin descargar (normal). «En emisión»: el PDF aún se genera; **Volver a consultar**. |
| T-63 | Revisar el correo. | Llega **un solo** correo del voucher, a nombre del titular. | ✘ | ✘ | Dos correos: duplicidad de eventos. Ninguno: cola de correo detenida. |
| T-64 | **Mis experiencias**. | Pestañas **Próximas** y **Realizadas**; cada reserva con su estado (Pendiente de pago, Confirmada, Realizada, Vencida, Cancelada…) y botón «Ver voucher y QR» o «Completar el pago». | ✔ | ✔ | Reserva faltante: la lista no se refrescó (recargue). |
| T-65 | Abrir la URL de una reserva (`/reservas/<id>`) con **otro** turista. | «No encontramos lo que buscas»: no se ve la reserva ajena. | ✘ | ✘ | La muestra: **defecto de seguridad (alta)**. |

### 5.8 Cancelar una reserva (solo por API)

**La PWA no tiene botón de cancelar.** Se prueba la regla de las 72 horas por la API; después se verifica en la PWA.
Cree **dos reservas confirmadas** del mismo turista: una en una franja con **más de 72 horas** de anticipación y otra
con **72 horas o menos** (pídale al Anfitrión de pruebas una franja para mañana; ver H-24).

```powershell
$tur = Login 'tester1@integrate-demo.test'
Api POST "/bookings/<id-de-la-reserva>/cancel" $tur @{ reason = 'Cambio de planes' }
```

| # | Acción | Debe ver | 🖥 | 📱 | Si falla, por qué |
|---|---|---|---|---|---|
| T-70 | Cancelar la de **más de 72 h**. | `Status` 200, `policyApplied` = `EARLY_REFUND`, reembolso del **85 %** (`touristRefundPct`), `slotsReleased` = cupos. | ✘ | — | 409: la reserva no está `CONFIRMED` o la franja ya empezó. 404: no es del turista. |
| T-71 | En la PWA: **Mis experiencias**. | Estado «**Cancelada con reembolso**»; el voucher ya **no sirve** (revocado); los cupos volvieron al catálogo. | ✔ | ✔ | Sigue «Confirmada»: recargue; si persiste, la cancelación no se reflejó. |
| T-72 | Cancelar la de **72 h o menos** (72 h exactas incluidas). | `Status` 200, `policyApplied` = `LATE_NO_REFUND`, **sin reembolso**. | ✔ | — | Aplica reembolso: error en el cálculo de Δt (zona horaria o umbral). |
| T-73 | En la PWA: ver esa reserva. | «**Cancelada sin reembolso**»; los cupos **no** vuelven al catálogo. | ✔ | ✔ | Los cupos vuelven: regla de retención mal aplicada. |
| T-74 | Cancelar de nuevo la misma reserva, o la de otro turista, o una vencida. | 409 (ya cancelada) / 404 (ajena) respectivamente; nunca un segundo reembolso. | ✔ | — | Segundo reembolso: **defecto financiero (alta)**. |

### 5.9 Mi cuenta y salida

| # | Acción | Debe ver | 🖥 | 📱 | Si falla, por qué |
|---|---|---|---|---|---|
| T-80 | **Mi cuenta**. | Perfil, roles y autorizaciones. Las tarjetas «Autorizado» no son botones. | ✔ | ✔ | — |
| T-81 | **Cerrar sesión**. | Vuelve al catálogo; el menú muestra **Ingresar**. Los datos locales del Gestor (si los hubo) se limpian. | ✔ | ✔ | «No fue posible cerrar la sesión»: la API no respondió; reintente. |
| T-82 | Recargar tras cerrar sesión; pulsar «Atrás» del navegador. | Sigue sin sesión; no reaparece ninguna pantalla privada. | ✔ | ✔ | Reaparece «Mi cuenta»: la cookie de refresh no se revocó. |

---

## 6. Administrador

El Administrador **no se registra**: el equipo lo configura al arrancar el sistema (`BOOTSTRAP_ADMIN_*`). En la PWA solo
tiene las pantallas de un turista; lo propio de su rol se hace por API.

### 6.1 En la PWA

| # | Acción | Debe ver | 🖥 | 📱 | Si falla, por qué |
|---|---|---|---|---|---|
| A-01 | Ingresar como administrador. | Entra; el menú es el de un turista (Explorar · Mis experiencias · Mi cuenta). | ✘ | ✘ | Credenciales: la cuenta no existe en este entorno (la semilla o `crear-testers.py` no se cargó). |
| A-02 | **Mi cuenta**. | «Turista · **Administrador**». Si le pide **renovar el consentimiento**, apruébelo: es el comportamiento de una cuenta creada por arranque, sin consentimiento previo. | ✔ | ✔ | Falta «Administrador»: rol no asignado → no podrá hacer A-10 en adelante. |
| A-03 | Abrir `/gestor`. | «**Esta sección no está disponible para tu cuenta**» y **Ir al inicio**. | ✔ | ✔ | Entra al portal: **defecto de permisos (alta)**. |
| A-04 | Reservar y pagar como en 5.4–5.6. | Funciona igual que para un turista. | ✔ | ✔ | — |

### 6.2 Por API: asociaciones y anfitriones

```powershell
$adm = Login 'tester.admin@integrate-demo.test'
```

| # | Acción | Debe ver | 🖥 | Si falla, por qué |
|---|---|---|---|---|
| A-10 | Crear asociación: `$a = Api POST '/admin/associations' $adm @{ nit='900123456-1'; rntNumber='123456'; legalName='Asociación QA'; associationType='ASOCIACION_VICTIMAS'; municipalityCode='05001'; address='Vereda QA'; contactEmail='qa@ejemplo.com'; contactPhone='+573001234567' }` | `Status` 201 con `id`. | ✔ | 403: el token no tiene `ROLE_ADMIN` (vuelva a ingresar). 409: el NIT ya existe. 422: formato de NIT/RNT/municipio. |
| A-11 | Repetir A-10 con el mismo NIT. | `Status` 409. | ✔ | Crea dos: falta la restricción de unicidad. |
| A-12 | `Api GET '/admin/associations' $adm`. | La asociación nueva aparece en la lista. | ✔ | — |
| A-13 | Convertir a un turista en anfitrión: `Api POST '/admin/host-profiles' $adm @{ userId='<id>'; associationId=$a.Body.id }` (el id lo imprime `Login`). | `Status` 201. Al volver a ingresar, ese usuario tiene **Turista y Anfitrión**. | ✔ | 404: el usuario no existe. 409: ya es anfitrión. |

### 6.3 Por API: ciclo de vida de un Gestor de Enlace

| # | Acción | Debe ver | 🖥 | Si falla, por qué |
|---|---|---|---|---|
| A-20 | Registre un turista nuevo en la PWA (futuro gestor), obtenga su id con `Login` y acredítelo: `$g = Api POST '/admin/link-managers' $adm @{ userId='<id>'; associationId=$a.Body.id; accreditedOn=(Get-Date -Format 'yyyy-MM-dd') }` | `Status` 201, acreditación activa. | ✔ | 404: usuario inexistente. 409: ya acreditado. |
| A-21 | Con `accreditedOn` en el **futuro**. | `Status` 422. | ✔ | Acepta fecha futura: validación faltante. |
| A-22 | **El gestor cierra sesión y vuelve a ingresar.** | Ahora ve **Gestor** en el menú y puede abrir `/gestor`. | ✔ | No aparece: el rol llega en el token solo desde el **siguiente** ingreso o renovación. |
| A-23 | Suspender: `Api POST "/admin/link-managers/$($g.Body.linkManagerId)/suspend" $adm`. Luego el gestor intenta **enrolar** (G-20). | La suspensión da 200; el enrolamiento falla con 403 («gestor no activo»). | ✔ | El gestor sigue enrolando: la suspensión no se aplica. |
| A-24 | **Reactivar** (`/reinstate`) y volver a enrolar. | Vuelve a poder enrolar. | ✔ | — |
| A-25 | (Al final) **Revocar** (`/revoke`). | El gestor ya no puede enrolar ni descargar el padrón. | ✔ | — |

### 6.4 Por API: moderar experiencias (incluye el punto de encuentro)

Requiere una experiencia en revisión (ver H-22).

| # | Acción | Debe ver | 🖥 | Si falla, por qué |
|---|---|---|---|---|
| A-30 | `Api GET '/admin/experiences?status=PENDING_REVIEW' $adm`. | La experiencia enviada por el anfitrión, con `meetingPointCheck`. | ✔ | Vacía: el anfitrión no la envió (H-22). |
| A-31 | Rechazar **sin** motivo: `Api POST "/admin/experiences/<id>/reject" $adm`. | `Status` 422: el motivo es obligatorio. | ✔ | Acepta sin motivo: validación faltante. |
| A-32 | Rechazar **con** motivo: `... reject $adm @{ reason='Falta el punto de encuentro exacto' }`. | 200; estado «rechazada»; **no** aparece en el catálogo. | ✔ | — |
| A-33 | Aprobar una experiencia con el punto de encuentro **dudoso** (`MISMATCH`). | `Status` 409 `CATALOG_MEETING_POINT_MISMATCH`: la aprobación queda bloqueada. | ✔ | Aprueba: el autómata de verificación (ADR-0030) no se aplicó. |
| A-34 | Confirmarlo: `Api POST "/admin/experiences/<id>/meeting-point/confirm" $adm`; luego aprobar. | Confirmar da 200 (desde `REVIEW` o `MISMATCH`); aprobar da 200 y la experiencia **aparece en el catálogo público** con sus franjas. | ✔ | 409 al confirmar: el punto no estaba en un estado confirmable. |
| A-35 | Aprobar otra vez la misma. | `Status` 409 (transición no válida). | ✔ | — |
| A-36 | **Suspender** con motivo. | La experiencia **desaparece** del catálogo y sus franjas futuras dejan de reservarse. | ✔ | Sigue visible: las franjas no se cerraron. |
| A-37 | **Reactivar**. | Vuelve al catálogo con sus franjas reabiertas. | ✔ | — |

### 6.5 Por API: auditoría

| # | Acción | Debe ver | 🖥 | Si falla, por qué |
|---|---|---|---|---|
| A-40 | `Api GET '/admin/audit?size=10' $adm`. | `items` más recientes primero, con módulo, acción y severidad. Los datos sensibles **nunca en claro**. | ✔ | 403: no es administrador. Datos en claro: **defecto de privacidad (alta)**. |
| A-41 | Filtrar: `?module=booking&severity=INFO`. | Solo entradas de ese módulo y severidad. | ✔ | Ignora filtros: parámetro mal enviado. |
| A-42 | `Api GET '/admin/audit/verify' $adm`. | La cadena de la bitácora es **íntegra** (sin eslabones rotos). | ✔ | Eslabón roto: alguien alteró la bitácora (**incidente**). |
| A-43 | Repetir A-40. | La consulta anterior aparece **como entrada de auditoría** (`AUDIT_QUERIED`). | ✔ | No queda registrada: falta la auditoría de la consulta. |
| A-44 | Como **turista**: `Api GET '/admin/audit' $tur`. | `Status` 403. | ✔ | 200: **defecto de permisos (alta)**. |

---

## 7. Gestor de Enlace

El Gestor se **acredita** (lo hace el Administrador, A-20). Necesita una asociación y, para el bloque offline, **una
reserva confirmada de hoy** en una experiencia de **su** asociación.

### 7.1 Primer ingreso y portal

| # | Acción | Debe ver | 🖥 | 📱 | Si falla, por qué |
|---|---|---|---|---|---|
| G-01 | Ingresar como gestor **después** de ser acreditado. | Menú con **Gestor**. **Mi cuenta**: «Turista · Gestor de Enlace». | ✔ | ✔ | No ve Gestor: la acreditación es posterior al token; cierre sesión y vuelva a ingresar. |
| G-02 | Tocar **Gestor**. | «**Panel de jornada**» con el indicador **En línea**, «Descargar padrón de hoy», «Actualizar revocaciones» y los accesos: Validar QR en campo · Contingencia manual · Cola de sincronización · Enrolar anfitrión · Mis anfitriones. | ✔ | ✔ | «Sin acceso»: la cuenta no tiene `ROLE_LINK_MANAGER`. |
| G-03 | Menú del portal. | **Jornada · Validar QR · Anfitriones · Mi cuenta**. | ✔ | ✔ | — |

### 7.2 Enrolar un anfitrión

| # | Acción | Debe ver | 🖥 | 📱 | Si falla, por qué |
|---|---|---|---|---|---|
| G-20 | **Enrolar anfitrión** → avanzar sin datos. | Errores en nombre, documento y celular; no avanza. | ✔ | ✔ | Avanza vacío: validación rota. |
| G-21 | Paso 1 con documento y celular válidos y **sin correo**. | Avanza: el correo es opcional. | ✔ | ✔ | — |
| G-22 | Paso 2: condición de víctima **Sí**. | Pide los datos de víctima (RUV y hecho victimizante opcionales). | ✔ | ✔ | — |
| G-23 | Paso 3 sin las dos autorizaciones. | No deja registrar. | ✔ | ✔ | Registra sin consentimiento: **defecto legal (alta)**. |
| G-24 | Cargar una **imagen** como formato de consentimiento. | Rechaza: solo PDF (también un archivo que solo cambia la extensión a `.pdf`: se verifica la firma `%PDF-`, ADR-0038 §3). | ✔ | ✔ | Acepta la imagen: validación de tipo faltante. |
| G-25 | Cargar un PDF de **más de 5 MB**. | Rechaza por tamaño. | ✔ | ✔ | 📱 No abre el selector: permisos de archivos del navegador. |
| G-26 | Un PDF pequeño, ambas autorizaciones → **Registrar anfitrión**. | «**Anfitrión registrado**», estado **Pendiente de activación** y un correo de acceso terminado en `@comunidad.integrate.co`. | ✔ | ✔ | 403 «gestor no activo»: acreditación suspendida (A-23). 422 `ENROLLMENT_DOCUMENT_INVALID`: el PDF no existe, no es PDF, pesa > 5 MB o **ya se usó**. |
| G-27 | Enrolar a la misma persona (mismo documento). | Error de persona ya existente (409). | ✔ | ✔ | Crea un duplicado: falta la restricción. |
| G-28 | Reusar el **mismo PDF** en otro enrolamiento. | Rechaza: el formato firmado ya respalda otro consentimiento. | ✔ | ✔ | Lo acepta: la unicidad del PDF no se aplica. |

### 7.3 Código de activación y Mis anfitriones

| # | Acción | Debe ver | 🖥 | 📱 | Si falla, por qué |
|---|---|---|---|---|---|
| G-30 | **Emitir código de activación**. | Código `XXXXX-XXXXX` y fecha de vencimiento (**72 h**). **Anótelo**: no se vuelve a mostrar. | ✔ | ✔ | Sin código: 403/404 (la persona no es de su jurisdicción). |
| G-31 | Recargar la página. | El código **ya no se ve**. | ✔ | ✔ | Se ve: el código se está guardando en pantalla o en el teléfono. |
| G-32 | **Anfitriones** → la misma persona → emitir **otro** código. | Código nuevo; el primero **deja de servir** (se prueba en H-03). | ✔ | ✔ | — |
| G-33 | Lista **Mis anfitriones**. | Documento **enmascarado** (solo los últimos 4 dígitos); el correo de ingreso es **opaco** (`h-xxxxxxxxxx@comunidad.integrate.co`, sin documento; las cuentas heredadas se muestran enmascaradas, ADR-0038 §1); ningún dato de víctima; estado Pendiente o Activo. | ✘ | ✘ | Documento completo o víctima visible: **defecto de privacidad (alta)**. |
| G-34 | Después de H-04 (el anfitrión se activa), recargar la lista. | La persona aparece **Activa**. | ✔ | ✔ | Sigue Pendiente: la lista no se refrescó o la activación no se completó. |

### 7.4 Jornada sin red: padrón, validar QR, contingencia y cola

Preparación: el Anfitrión (o el Administrador) crea una franja **de hoy, a una hora aún por venir**, y un turista reserva
y paga 2 cupos (5.5–5.6). El voucher del turista debe poder mostrarse en **otro dispositivo**.

| # | Acción | Debe ver | 🖥 | 📱 | Si falla, por qué |
|---|---|---|---|---|---|
| G-40 | En el panel, **Descargar padrón de hoy** (con red). | «Descargando padrón…» y después «Padrón en dispositivo» con el número de reservas; «Por sincronizar: 0». | ✔ | ✔ | «No se pudo descargar el padrón»: sin red, o la reserva no es de **su** asociación, o no es de hoy. Padrón en 0: no hay reservas confirmadas hoy en su jurisdicción. |
| G-41 | **Actualizar revocaciones**. | Confirma la actualización de la lista de revocación. | ✔ | ✔ | Error en línea: la API de revocaciones no respondió. |
| G-42 | Activar el **modo avión** (📱) o *Network → Offline* (🖥). | El indicador cambia a «**Sin conexión · Modo campo**». La sesión del gestor **sigue** (no lo saca). | ✔ | ✔ | Lo saca a Ingresar: la sesión no sobrevive sin red (ADR-0036). |
| G-43 | **Validar QR**: apuntar la cámara trasera al voucher del turista. | Respuesta **multimodal**: color de éxito, texto («verificada») y **vibración** del teléfono; registra el ingreso **sin red**. | — | — | «No se pudo acceder a la cámara trasera»: permiso denegado en el navegador (ajustes del sitio). iPhone sin HTTPS: el navegador **no** da cámara por HTTP. «Este navegador no puede leer QR»: navegador sin la API de detección; use el campo de pegar token. |
| G-44 | 🖥 (sin cámara): copiar el token del QR y usar **Verificar firma y registrar ingreso**. | Mismo resultado de validación. | ✔ | — | Firma inválida: el token está incompleto (copie las tres partes). |
| G-45 | Escanear **otra vez el mismo voucher**. | Rechazo visible: «ya canjeado» (rojo, texto y vibración distinta). | ✔ | ✔ | Lo acepta de nuevo: **defecto financiero (alta)**, RNF-18 (doble canje). |
| G-46 | Escanear un QR **alterado** o de **otro** sistema. | Rechazo por firma inválida. | ✔ | ✔ | Lo acepta: **defecto de seguridad (alta)**. |
| G-47 | **Contingencia manual**: escribir el código corto de 8 caracteres de **otro** voucher. | «Validando código…» y registra la asistencia; el voucher figura «Consumido en este teléfono». | ✔ | ✔ | Código no encontrado: el voucher no está en el padrón descargado (no es de hoy / otra asociación). |
| G-48 | En contingencia, **Buscar por titular, documento o código**. | Lista del padrón con «Disponible» o «Consumido en este teléfono». | ✔ | ✔ | Búsqueda vacía: padrón no descargado (G-40). |
| G-49 | **Cola de sincronización**, aún sin red. | «**Sincronizar ahora (N)**» con las asistencias pendientes y su estado «Pendiente de envío». | ✔ | ✔ | La cola dice 0: las validaciones no se guardaron en el teléfono (almacenamiento lleno o bloqueado). |
| G-50 | **Cerrar la app por completo y volver a abrirla** sin red. | El padrón y la cola **siguen ahí** (están en el teléfono). | ✔ | ✔ | Se perdieron: el navegador limpió los datos (modo privado, almacenamiento no persistente). |
| G-51 | Desactivar el modo avión → **Sincronizar ahora**. | «Sincronizando…» y luego las asistencias figuran **sincronizadas**; «Por sincronizar: 0». | ✔ | ✔ | «Error permanente» o «Rechazado por servidor»: la reserva ya se canceló o expiró. «Conflicto conciliado»: la reserva se canceló mientras estaba sin red; **debe** proteger el 85 % de la comunidad (ADR-0033). |
| G-52 | Como el **turista**: **Mis experiencias** → Realizadas. | La reserva figura «**Realizada**» (y su voucher «usado»). | ✔ | ✔ | Sigue «Confirmada»: la sincronización no cambió el estado. |
| G-53 | Como **administrador**: `Api GET '/admin/audit?module=offline' $adm`. | Entradas de la validación y los accesos al padrón, **sin** datos personales en claro. | ✔ | — | Sin entradas: no se auditó la operación offline. |
| G-54 | Instalar la app: «Agregar a la pantalla de inicio» (📱) / «Instalar» (🖥). | Se instala con su ícono y abre **a pantalla completa**. | — | — | No ofrece instalar: falta el manifiesto o HTTPS. |

### 7.5 Pérdida de acceso

| # | Acción | Debe ver | 🖥 | 📱 | Si falla, por qué |
|---|---|---|---|---|---|
| G-60 | Tras A-23 (suspensión), el gestor recarga el portal e intenta **descargar el padrón**. | Se le niega (403) y no puede validar ni enrolar. | ✔ | ✔ | Sigue operando: el estado de la acreditación no se verifica en cada llamada. |
| G-61 | Tras A-25 (revocación), cerrar sesión y volver a ingresar. | La revocación **quita el rol Gestor** (ADR-0038 §2; la suspensión lo conserva). El menú **ya no muestra Gestor**; `/gestor` da «Sin acceso». | ✘ | ✘ | Conserva el menú: el rol sigue en el token (esperado hasta el próximo ingreso o renovación). |

---

## 8. Anfitrión comunitario

El Anfitrión **no se registra**: su Gestor lo **enrola** (G-26) y le entrega un código (G-30). Quien ya es turista puede ser
convertido por el Administrador (A-13); en ese caso conserva el rol de turista.

### 8.1 Activar la cuenta

Use el **segundo** código (G-32) y el correo de acceso mostrado al enrolar. En 📱, pida el código al gestor **en persona**.

| # | Acción | Debe ver | 🖥 | 📱 | Si falla, por qué |
|---|---|---|---|---|---|
| H-01 | Abrir `/activar`. | «Activa tu cuenta»: correo, código de activación, nueva contraseña y repetirla. | ✔ | ✔ | «Página no encontrada»: dirección mal escrita. |
| H-02 | Contraseña de 8 caracteres; contraseñas distintas. | Error por campo; no se activa. | ✔ | ✔ | Activa con clave débil: política de contraseña no aplicada. |
| H-03 | Con el **primer** código (ya reemplazado). | «El código no es válido, venció o ya se usó. Pide uno nuevo a tu gestor.» | ✔ | ✔ | Lo acepta: el código anterior debía invalidarse (**seguridad**). |
| H-04 | Con el segundo código y una contraseña válida → **Activar cuenta**. | «**Cuenta activada**» y botón **Ingresar**. | ✔ | ✔ | Mismo mensaje de código inválido: el código expiró (72 h) o se mezcló con otro. |
| H-05 | Volver a usar el mismo código. | Mismo error de «ya se usó». | ✔ | ✔ | Lo acepta: el código no es de un solo uso. |

### 8.2 Primer ingreso

| # | Acción | Debe ver | 🖥 | 📱 | Si falla, por qué |
|---|---|---|---|---|---|
| H-10 | **Ingresar** con el correo de acceso y la nueva contraseña. | Entra. | ✔ | ✔ | 401: la contraseña de activación no coincide. |
| H-11 | **Mi cuenta**. | Rol «**Anfitrión comunitario**» (**solo ese**); autorizaciones de datos personales y **condición de víctima** «Autorizado»; salud «Sin autorizar». | ✔ | ✔ | Aparece «Turista»: se creó con el rol equivocado. La víctima no aparece: el enrolamiento no guardó la autorización. |
| H-12 | Menú. | **Explorar · Mi cuenta**: sin Mis experiencias ni Gestor. | ✔ | ✔ | Ve Mis experiencias: tiene rol de turista (revise cómo se creó). |
| H-13 | Abrir `/gestor`. | «Esta sección no está disponible para tu cuenta». | ✔ | ✔ | Entra: **defecto de permisos (alta)**. |
| H-14 | Abrir una experiencia → **Ver fechas** → **Reservar**. | Un anfitrión **sin** rol de turista **no puede reservar**: ve «Esta sección no está disponible para tu cuenta» al abrir el formulario de reserva. | ✔ | ✔ | Reserva sin ser turista: **defecto de permisos**. Si la guía de usuario dice que «puede reservar», la guía está desactualizada. |

### 8.3 Sus experiencias (por API)

```powershell
$host1 = Login 'tester.anfitrion@integrate-demo.test'
```

| # | Acción | Debe ver | 🖥 | Si falla, por qué |
|---|---|---|---|---|
| H-20 | Crear la propuesta: `$e = Api POST '/host/experiences' $host1 @{ title='Sendero QA de la memoria'; description='Recorrido guiado por la comunidad.'; safetyGuidelines='Llevar agua y botas.'; physicalIntensity='MEDIUM'; durationMinutes=240; municipalityCode='05001'; vereda='La Esperanza'; meetingPoint='Parque principal, Medellín'; latitude=6.25; longitude=-75.56; maxCapacityPerSlot=8; unitPriceCop=45000; categoryCodes=@('SENDERISMO_MEMORIA') }` | `Status` 201, **borrador**; **no** aparece en el catálogo público. | ✔ | 403: no es anfitrión. 422: algún campo (categoría, municipio, formato del precio). |
| H-21 | Verificar el punto de encuentro: `Api POST "/host/experiences/$($e.Body.id)/meeting-point/check" $host1`. | `Status` 200 y `meetingPointCheck` actualizado (`MATCH`, `REVIEW`, `MISMATCH` o `UNVERIFIED`). | ✔ | Se queda en `VERIFICANDO`: el geocodificador externo tardó (reintente). Un servicio caído deja `UNVERIFIED`, nunca error. |
| H-22 | Crear una franja futura: `Api POST "/host/experiences/$($e.Body.id)/schedules" $host1 @{ schedules=@(@{ localStart='<AAAA-MM-DD>T07:00:00'; localEnd='<AAAA-MM-DD>T12:00:00'; totalSlots=4 }) }` y **enviar a revisión**: `Api POST "/host/experiences/$($e.Body.id)/submit" $host1`. | Franja `201`; envío `200`, estado «en revisión». Pasa a la cola del Administrador (A-30). | ✔ | 422 en la franja: fecha no futura o mal formato. 409: se cruza con otra franja. |
| H-23 | Franja con `totalSlots=9` (la capacidad es 8). | `Status` 422 (capacidad de carga ecológica). | ✔ | Acepta 9: **defecto de negocio (alta)**, RNF-24. |
| H-24 | Crear una franja para **mañana** (≤ 72 h). | `Status` 201; sirve para probar la cancelación sin reembolso (T-72). | ✔ | — |
| H-25 | Tras el rechazo del Administrador (A-32), corregir con `Api PUT "/host/experiences/<id>" $host1 @{ ...mismo cuerpo con meetingPoint corregido... }` y volver a enviar. | Vuelve a «en revisión». | ✔ | 409: solo se editan borradores y rechazadas. |
| H-26 | Tras A-34 (aprobada), ver el catálogo como visitante. | La experiencia aparece con su franja reservable. | ✔ | No aparece: sigue en revisión o con punto dudoso. |
| H-27 | Reducir la capacidad a 3 teniendo una franja abierta de 4. | `Status` 422: no puede quedar por debajo de una franja abierta. | ✘ | Acepta: inconsistencia de aforo. |
| H-28 | Ver una experiencia de **otro** anfitrión: `Api GET '/host/experiences/<id ajeno>' $host1`. | `Status` 404. | ✔ | 200: **defecto de privacidad (alta)**. |
| H-29 | Cancelar una franja con reservas: `Api POST '/host/schedules/<id>/cancel' $host1 @{ reason='Lluvias' }`. | `Status` 200; deja de ser reservable. **Anote** qué pasa con las reservas ya confirmadas (la regla de reembolso a los turistas afectados es de la política de cancelación; verifique en *Mis experiencias* del turista). | ✘ | — |

### 8.4 Recuperar el acceso

| # | Acción | Debe ver | 🖥 | 📱 | Si falla, por qué |
|---|---|---|---|---|---|
| H-30 | El anfitrión con correo **sintético** (`@comunidad.integrate.co`) olvida su contraseña. | **No** recibe correo (no existe el buzón). Debe acudir a su Gestor, quien **emite un código nuevo** (G-30) y se repite H-04. | ✔ | ✔ | Recibe un correo: el correo sintético no debería poder recibirlos. |
| H-31 | Anfitrión con correo **real**: repetir 5.3 (T-20 a T-29). | Igual que un turista. | ✘ | ✘ | — |

---

## 9. Ciclo completo encadenado

Un solo recorrido con datos nuevos que une a los cuatro roles. Marque `✔` solo si todo el paso funciona **sin tocar la
base de datos**. Hágalo **una vez desde el computador y otra desde el celular** (en 📱 solo los pasos con pantalla).

| # | Rol | Acción | Debe pasar | 🖥 | 📱 |
|---|---|---|---|---|---|
| C-01 | Administrador | Crear una asociación (A-10). | 201. | ✔ | — |
| C-02 | Turista nuevo | Registrarse (5.1). | Cuenta activa. | ✔ | ✔ |
| C-03 | Administrador | Acreditar a esa persona como gestor (A-20). | 201; al reingresar ve **Gestor**. | ✔ | ✔ |
| C-04 | Gestor | Enrolar un anfitrión con PDF y emitir su código (G-26, G-30). | Pendiente de activación + código. | ✔ | ✔ |
| C-05 | Anfitrión | Activar la cuenta e ingresar (H-04, H-10). | Rol «Anfitrión comunitario». | ✔ | ✔ |
| C-06 | Anfitrión | Crear experiencia, verificar punto, crear una franja de 3 cupos **de hoy** y enviar a revisión (H-20 a H-22). | En revisión. | ✔ | — |
| C-07 | Administrador | Aprobarla (A-34). | Visible en el catálogo. | ✔ | ✔ |
| C-08 | Turista A | Reservar 2 cupos y pagar. | Voucher con QR; correo recibido. | ✘ | ✘ |
| C-09 | Turista B | Intentar reservar 2 cupos en la misma franja. | Solo queda 1: lo avisa y no permite 2. | ✔ | ✔ |
| C-10 | Turista B | Reservar 1 cupo y no pagar. | A los 15 min el cupo vuelve a estar libre. | ✔ | ✔ |
| C-11 | Gestor | Descargar el padrón, validar el QR del turista A sin red y sincronizar (G-40 a G-51). | La reserva queda «**Realizada**» para el turista A. | ✔ | ✔ |
| C-12 | Administrador | Consultar la auditoría del recorrido (A-40, A-42). | Entradas de todos los pasos; cadena **íntegra**. | ✔ | — |
| C-13 | Turista A | Cancelar una **segunda** reserva con más de 72 h (T-70). | Reembolso del 85 %. | ✔ | — |

---

## 10. Control de acceso cruzado

Con el token de cada rol, llame a lo que **no** le corresponde. Es lo que más importa para la seguridad.

| # | Quién | Acción | Debe responder | 🖥 | Si falla, por qué |
|---|---|---|---|---|---|
| X-01 | Sin token | `Api GET '/admin/associations' $null` | 401. | ✔ | 200: **defecto de seguridad (alta)**. |
| X-02 | Turista | `Api GET '/admin/associations' $tur` | 403. | ✔ | ídem |
| X-03 | Turista | `Api POST '/host/experiences' $tur @{...}` | 403. | ✔ | ídem |
| X-04 | Turista | `Api GET '/gestor/hosts' $tur` | 403. | ✔ | ídem |
| X-05 | Turista | `Api GET '/offline/roster' $tur` | 403. | ✔ | ídem |
| X-06 | Anfitrión (sin turista) | `Api POST '/bookings/lock' $host1 @{...}` | 403. | ✔ | Reserva: el anfitrión tiene rol de turista. |
| X-07 | Gestor | `Api POST '/admin/experiences/<id>/approve' $ges` | 403. | ✔ | Aprueba: **defecto de permisos (alta)**. |
| X-08 | Gestor | `Api GET '/admin/audit' $ges` | 403. | ✔ | Ve la auditoría: **defecto (alta)**: es exclusiva del Administrador. |
| X-09 | Cualquiera | Token con una letra cambiada en el medio. | 401. | ✔ | Lo acepta: firma no verificada. |
| X-10 | Cualquiera | Token vencido (más de 15 min sin renovar). | 401 por API; en la PWA se renueva solo. | ✔ | — |
| X-11 | Turista | Cancelar la reserva de **otro** turista. | 404 (no revela que existe). | ✔ | 200/403: filtración de existencia (**seguridad**). |

---

## 11. Por qué falla: del mensaje a la causa

Cuando la pantalla muestra un error, esta tabla lleva del **mensaje** a la **causa más probable** y a **dónde mirar**.
Todos los errores de la API incluyen un **Código de seguimiento** (`traceId`): búsquelo en el log del backend.

| Lo que ve el usuario | Estado HTTP | Causa más probable | Dónde mirar / qué hacer |
|---|---|---|---|
| «**Sin conexión**» / «Revisa tu señal» | — (sin respuesta) | La PWA no llega a la API: sin red, proxy `/api` caído, backend dormido o certificado no aceptado en 📱. | Abra la dirección de la API en el navegador; en el entorno de pruebas, el primer acceso tarda hasta ~1 min. |
| «El correo o la contraseña no coinciden» | 401 | Credenciales incorrectas **o** la cuenta no existe en este entorno. | Confirme que la cuenta está cargada (semilla o `crear-testers.py`) y la contraseña del archivo correcto. |
| Lo saca a **Ingresar** por sorpresa | 401 en la renovación | La cookie de sesión no se guardó (📱 por HTTP/IP, no `Secure`), o pasaron 15 min sin poder renovar. | Use HTTPS (`npm run dev:https` o el entorno de pruebas). |
| «**Esta sección no está disponible para tu cuenta**» | 403 | El rol de la cuenta no incluye esa sección. | Compare los roles de «Mi cuenta» con la [sección 3](#3-qué-debe-tener-y-qué-debe-ver-cada-rol). Un rol nuevo llega solo **al volver a ingresar**. |
| «**No encontramos lo que buscas**» | 404 | El registro **ya no existe** (franja, reserva u experiencia borradas o de otra base), es **de otra persona**, o el simulador de pagos está apagado. | El Código de seguimiento muestra la ruta en el log. Recargue el catálogo y vuelva a elegir la franja. |
| «Solo quedan N cupos» / «Esta franja ya no está disponible» | 409 | Otro turista apartó los cupos o la franja se cerró. | Es lo esperado bajo concurrencia; elija otra franja. |
| Campo con error en rojo | 422 | El dato no cumple el formato (documento, celular, contraseña de menos de 12). | Lea el mensaje el campo; si el cliente lo dejó pasar, es desalineación cliente/servidor. |
| «Autorización necesaria» al reservar | 428 | Falta la autorización de datos de salud. | Marque la casilla y **Acepto**: es el comportamiento esperado. |
| «Hay mucha demanda en esta franja, reintentando…» | 503 | Contención de cupos (tiempo de espera del bloqueo). | Debe reintentar solo una vez; si persiste, la base está sobrecargada. |
| «El servicio no responde» | 5xx | Error del servidor. | Log del backend por `traceId`. |
| «El código no es válido, venció o ya se usó» | 401 | Código de activación incorrecto, de otro correo, vencido (72 h), ya usado o **reemplazado** por uno nuevo. | El gestor emite un código nuevo. |
| «El enlace venció o ya se usó» | 401 | El enlace de restablecer dura 15 min y es de un solo uso. | Pedir otro desde «¿Olvidaste tu contraseña?». |
| No llega un correo | — | Dominio ficticio (`@integrate-demo.test`, `@ejemplo.com`), spam, SMTP mal configurado o la cola de correo no despacha. | Local: Mailpit (`:8025`). Pruebas: un buzón **real** del probador. |
| «No se pudo acceder a la cámara trasera» | — | Permiso de cámara denegado, o la página se abrió por **HTTP** (el navegador no da cámara). | Ajustes del sitio → Cámara → Permitir; use HTTPS. |
| El panel de filtros o un diálogo se ve **casi vacío** en 📱 | — | Altura de la hoja inferior calculada mal en Safari/iOS. | Es el defecto ya corregido en `BottomSheet`; si reaparece es una regresión. |
| «No pasa nada» al tocar **Apartar cupos** | — | La respuesta de error quedó **fuera de la pantalla** (arriba o tapada por el teclado). | Debe desplazarse sola al aviso; anote si no lo hace. |
| Cupos que no vuelven al catálogo tras un vencimiento | — | El worker de expiración aún no corrió (barrido de hasta 1 min). | Espere un minuto y recargue. |

### Cómo clasificar el error

| Tipo | Señal | Quién lo resuelve |
|---|---|---|
| **Defecto de la app** | El comportamiento difiere de «Debe ver» con una cuenta y datos correctos, en el entorno correcto. | Desarrollo; se registra en Jira. |
| **Dato o cuenta de prueba** | La cuenta no tiene el rol, la autorización o la reserva que el paso necesita. | El probador, recreando el dato. |
| **Entorno** | Solo falla en 📱 por HTTP, sin red, certificado, servidor dormido o correo ficticio. | Quien administra el entorno. |
| **Expectativa del documento** | La app hace algo razonable y distinto de lo que dice la guía. | Quien mantiene la guía. |

---

## 12. Registro de errores y cierre

### 12.1 Encabezado de la ejecución

| Campo | Valor |
|---|---|
| Fecha y hora | `2026-10-30 06:30–08:35 COT (America/Bogota)` |
| Probador | `Ejecución automatizada QA (API + Chrome CDP escritorio y móvil)` |
| Entorno (pruebas / local) y dirección | `Pruebas — https://pruebas.integrate-demo.pages.dev (API https://pruebas.integrate-demo.pages.dev/api/v1)` |
| Rama y commit (`git rev-parse --short HEAD`) | `main @ 7538b4f` |
| 🖥 Navegador y versión | `Google Chrome Headless (CDP) — viewport 1280×800 px` |
| 📱 Teléfono, sistema y navegador | `Vista móvil emulada en Chrome CDP (390×844 px, DPR 2, touch=true); pendientes en teléfono físico real G-43 (cámara óptica trasera) y G-54 (instalación nativa en pantalla de inicio)` |
| Pasos `✔` / `✘` / no ejecutados | `🖥: 160 ✔ / 15 ✘ / 2 no ejecutados (G-43, G-54) · 📱: 108 ✔ / 12 ✘ / 2 no ejecutados en hardware físico (G-43, G-54)` |

### 12.2 Errores encontrados

Una fila por error. **«Por qué ocurrió»** es la causa probable (use las columnas «Si falla, por qué» y la sección 11).

| Error | Paso | 🖥 / 📱 | Qué vio (texto literal y Código de seguimiento) | Por qué ocurrió (causa) | Tipo | Severidad |
|---|---|---|---|---|---|---|
| E-01 | A-01 / §2.3 | 🖥 / 📱 | Al intentar ingresar con `tester.admin@integrate-demo.test` (y las demás cuentas de `infra/env/.env.testers.txt`): `HTTP 401 Unauthorized`, `code: "AUTH_INVALID_CREDENTIALS"`, `detail: "Invalid email or password"`, `traceId: "4c7bbab5b19c4d00b2f500c1104d201f"`. | `infra/scripts/crear-testers.py` apunta por defecto a producción (`https://integrate-demo.pages.dev`) y `infra/postgres/seed/import_testers.sql` no se había aplicado a la base del entorno `pruebas`. El administrador inicial de arranque (`BOOTSTRAP_ADMIN_EMAIL` en `infra/env/.env.demo.pruebas`) sí existía (`HTTP 200`, `traceId: "3a0209156f5d4d3da907a2fd412cb2cc"`), y se aprovisionaron las cuentas `tester1..5`, `tester.anfitrion` y `tester.gestor` en `pruebas` para el resto del recorrido. | Dato o cuenta de prueba | Media |
| E-02 | G-61 / A-25 | 🖥 / 📱 | Tras `POST /api/v1/admin/link-managers/312607e8-b17d-4059-a232-abc144c8abfc/revoke` (`HTTP 200`, `accreditationStatus: "REVOKED"`), cerrar sesión y volver a ingresar (`POST /api/v1/auth/login`), el usuario sigue recibiendo `roles: ["ROLE_TOURIST", "ROLE_LINK_MANAGER"]`, sigue viendo **Gestor** en el menú y puede abrir `/gestor` (aunque las llamadas a `/offline/roster` o `/gestor/uploads` son rechazadas con `HTTP 403`, `code: "ENROLLMENT_MANAGER_NOT_ACTIVE"`). | En `LinkManagerAdminServiceImpl.changeStatus`, al pasar la acreditación a `REVOKED` solo se actualiza `link_manager_profiles.accreditation_status` (`profile.moveTo(target)`), pero no se revoca el rol `RoleCode.LINK_MANAGER` en `auth.user_roles` (a diferencia de `accredit()`, que sí invoca `roleAssignmentService.grant(..., RoleCode.LINK_MANAGER)`). | Defecto de la app — **corregido en ADR-0038 §2; repetir G-61** | Media |
| E-03 | G-33 | 🖥 / 📱 | En `GET /api/v1/gestor/hosts` y en la pantalla `/gestor/anfitriones` (`HostsPage.tsx`), `docNumberLast4` aparece enmascarado (`CC ••••1142`) y no se exponen datos de condición de víctima (`victimData`), pero en anfitriones enrolados sin correo personal (`G-21`) la tarjeta muestra en claro el correo sintético `cc2510301142@comunidad.integrate.co (interno)`, que contiene el número de documento completo (`2510301142`). | `AssistedEnrollmentServiceImpl` construye el correo sintético concatenando el tipo y número de documento completo (`cc<docNumber>@comunidad.integrate.co`), y `HostsPage.tsx` lo imprime sin enmascarar cuando `syntheticEmail == true`, anulando el enmascaramiento de `docNumberLast4`. | Defecto de la app — **corregido en ADR-0038 §1; repetir G-21 y G-33** | Media |
| E-04 | G-24 (API) | 🖥 | En la interfaz PWA (`DocumentUpload.tsx`), `pdfProblem(file)` sí rechaza imágenes (`✔`), pero al subir bytes PNG directamente a la URL prefirmada de S3 con cabecera `Content-Type: application/pdf` y llamar `POST /api/v1/auth/register-assisted`, el backend respondió `HTTP 201 Created`. | En `S3DocumentStorage.java`, la validación del archivo subido a S3 ejecuta `headObject` y comprueba únicamente los metadatos `contentType == "application/pdf"` y `contentLength <= 5242880`, sin inspeccionar la cabecera mágica `%PDF-` del objeto. | Defecto de la app — **corregido en ADR-0038 §3; repetir G-24 por API** | Baja |
| E-05 | H-29 | 🖥 | Al ejecutar `POST /api/v1/host/schedules/5893e5ac-b19a-442a-8ce5-547c78743d5b/cancel` sobre una franja con reserva confirmada (`471c378c-9264-423a-9925-5adb7dc4beff`), la API respondió `HTTP 409 Conflict`, `code: "CATALOG_SCHEDULE_CONFLICT"`, `detail: "The schedule has bookings and cannot be cancelled yet"`, `traceId: "21ec0e79462f40a0958e575490b5508b"`. La reserva confirmada del turista permaneció en estado `CONFIRMED`. | Según `docs/api/openapi.yaml` (línea 830), en Fase 2 el endpoint `/host/schedules/{id}/cancel` solo permite cancelar franjas futuras sin cupos vendidos (`soldSlots == 0`) y bloquea con `409` las franjas con reservas hasta la Fase 3. La guía esperaba `Status 200`. | Expectativa del documento | Baja |
| E-06 | T-70 / T-72 | 🖥 | En `POST /api/v1/bookings/ebcadb50-2934-4dec-b24e-0315cb2d6083/cancel` (`> 72 h`), la API devolvió `HTTP 200`, `policyApplied: "EARLY_REFUND"`, `touristRefundPct: 85`, `slotsReleased: true` (booleano) en lugar de `slotsReleased: 2` (entero); en `<= 72 h` devolvió `slotsReleased: false`. Los 2 cupos sí se liberaron o retuvieron correctamente en la disponibilidad del catálogo. | El DTO de respuesta de cancelación define `slotsReleased` como `boolean` (`true`/`false`), mientras que la columna «Debe ver» de `T-70` indica `slotsReleased = cupos`. | Expectativa del documento | Baja |
| E-07 | H-27 | 🖥 | Al ejecutar `PUT /api/v1/host/experiences/641bbe78-90dc-4f1a-9d82-bb1ec71dfb48` con `maxCapacityPerSlot: 3` inmediatamente después de `H-26` (experiencia ya `APPROVED`), la API respondió `HTTP 409 Conflict` (`code: "CATALOG_INVALID_STATUS_TRANSITION"`). Al probar la misma reducción sobre una experiencia en estado `DRAFT` con franja abierta de 4 cupos, respondió `HTTP 422` (`code: "VALIDATION_FAILED"`). | `HostExperienceServiceImpl.update` verifica primero `experience.getStatus().isEditableByHost()` (solo `DRAFT` y `REJECTED` son editables) antes de validar `maxCapacityPerSlot < maxOpen`. En la tabla §8.3, `H-27` está ubicado después de que la experiencia fue aprobada en `A-34`/`H-26`. | Expectativa del documento | Baja |
| E-08 | T-55 / T-65 | 🖥 / 📱 | En `T-55`, la pantalla de reserva vencida muestra `«El tiempo para pagar terminó»` (la guía dice `«Tiempo para pagar vencido»`). En `T-65`, al abrir `/reservas/<id>` ajeno o inexistente, `BookingStatusPage.tsx` muestra `«No encontramos esta reserva»` (la guía dice `«No encontramos lo que buscas»`). | Diferencias menores de redacción literal entre los textos de `BookingStatusPage.tsx` y la columna «Debe ver» del guion. | Expectativa del documento | Baja |
| E-09 | T-23, T-26, T-27, T-29, T-63, H-31, C-08 | 🖥 / 📱 | Las llamadas `POST /api/v1/auth/forgot-password` responden `HTTP 202 Accepted` y la emisión de vouchers genera el token QR y el PDF (`HTTP 200`), pero no se envían correos electrónicos salientes en el entorno `pruebas`. | En `infra/env/.env.demo.pruebas` está configurado deliberadamente `INTEGRATE_NOTIFICATION_DISPATCH_ENABLED=false` («Envío de correos apagado: la rama copia usuarios reales») para evitar despachar correos a cuentas reales clonadas en la base de pruebas. | Entorno | Baja |

**Severidad:** *Alta* bloquea el flujo o expone datos (sobreventa, acceso indebido, dato sensible visible, doble cobro o
doble canje); *Media* tiene alternativa pero el resultado es incorrecto; *Baja* es de texto, estilo o mensaje. Los
errores se registran luego en el gestor de incidencias (Jira), que alimenta las métricas «Defectos por módulo» y
«MTTR» del Plan de Gestión de la Calidad (§3.3).

### 12.3 Resultado por rol

| Rol | Pasos 🖥 `✔`/total | Pasos 📱 `✔`/total | ¿Aprobado? (todos `✔`, sin errores *Alta*) |
|---|---|---|---|
| Visitante | `13 / 13` | `12 / 12` | **Sí** (`0` errores *Alta*) |
| Turista | `49 / 57` | `46 / 53` | **Con observaciones** (`0` errores *Alta*; `5` pasos bloqueados por correo deshabilitado en entorno `pruebas` [`E-09`] y `3` diferencias de texto/tipo en el documento [`E-06`, `E-08`]) |
| Administrador | `26 / 27` | `3 / 4` | **Con observaciones** (`0` errores *Alta*; `A-01` falló inicialmente con `tester.admin` por semilla no corrida en `pruebas` [`E-01`], completado con cuenta bootstrap admin) |
| Gestor de Enlace | `30 / 32` (`+2 —`) | `28 / 30` (`+2 —`) | **No** (`0` errores *Alta*, pero `2` defectos de la app de severidad *Media*: `E-02` en `G-61` y `E-03` en `G-33`; `G-43` y `G-54` requieren teléfono físico) |
| Anfitrión | `19 / 22` | `11 / 12` | **Con observaciones** (`0` errores *Alta*; `2` expectativas del documento en `H-27`/`H-29` [`E-05`, `E-07`] y `1` de entorno en `H-31` [`E-09`]) |
| Ciclo encadenado | `12 / 13` | `8 / 9` | **Con observaciones** (flujo completo encadenado operativo de punta a punta; solo envío de correo en `C-08` apagado por `E-09`) |
| Control de acceso | `11 / 11` | `—` | **Sí** (`11/11` bloqueos `401`/`403`/`404` verificados; `0` errores) |

---

## 13. Límites conocidos de esta versión

No son defectos nuevos; el paso correspondiente los menciona.

| Límite | Dónde afecta | Estado |
|---|---|---|
| El Administrador y el Anfitrión **no tienen pantallas** de su rol (moderar, crear experiencias, auditoría). | Secciones 6 y 8.3 | Se prueban por API. |
| **No hay botón para cancelar** una reserva en la PWA. | T-70 a T-74 | Solo por API (ADR-0034). |
| En computador, solo catálogo, detalle y franjas tienen vista ancha; el resto es una columna de celular centrada. | 1.3 | ADR-0020. |
| Los datos del titular se precargan solo en parte (nombre). | T-40 | Mejora pendiente. |
| La política de privacidad vigente puede ser un borrador de demostración. | T-05, T-43 | Debe publicarse la real antes de sumar personas reales (Ley 1581). |
| Las no conformidades del Plan de Calidad: coordenadas exactas en el detalle público, documento y celular de la cuenta sin cifrar, EPS y contacto fuera del PDF del voucher. | V-07, T-62 | Ver [evidencias-metricas.md](evidencias-metricas.md). |
| Cuentas con **dominio ficticio** no reciben correo. | T-20 a T-29, T-63 | Use un buzón real en el entorno de pruebas. |
