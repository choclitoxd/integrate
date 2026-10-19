# Guion de pruebas manuales · Ciclo de vida de cada usuario

Prueba de aceptación de punta a punta de Intégrate, rol por rol, para comprobar que no hay fallas antes de cerrar la
Fase 1. Complementa la suite automática ([evidencias-metricas.md](evidencias-metricas.md)): aquí se prueba lo que ve
y hace una persona real.

Cada paso tiene una **acción**, el **resultado esperado** y una casilla. Si el resultado real difiere aunque sea un
poco, márquelo ✘ y registre el defecto (sección 9). No corrija sobre la marcha: termine el bloque y reporte.

> **Alcance de la Fase 1.** Turista, Gestor de Enlace y la activación del Anfitrión tienen pantallas. Las tareas del
> **Administrador** y la gestión de experiencias del **Anfitrión** todavía no tienen pantallas: se prueban por la API
> con los comandos de la sección 0.4. Lo marcado **[Brecha conocida]** ya se sabe que falla; se prueba para confirmar
> que sigue igual.

## Contenido

0. [Preparación](#0-preparación)
1. [Visitante sin cuenta](#1-visitante-sin-cuenta)
2. [Turista](#2-turista)
3. [Administrador](#3-administrador)
4. [Gestor de Enlace](#4-gestor-de-enlace)
5. [Anfitrión comunitario](#5-anfitrión-comunitario)
6. [Ciclo completo encadenado](#6-ciclo-completo-encadenado)
7. [Control de acceso cruzado](#7-control-de-acceso-cruzado)
8. [Verificaciones finales en correo y base de datos](#8-verificaciones-finales-en-correo-y-base-de-datos)
9. [Registro de resultados y defectos](#9-registro-de-resultados-y-defectos)

---

## 0. Preparación

### 0.1 Entorno

| # | Acción | Resultado esperado | ✔/✘ |
|---|---|---|---|
| 0.1.1 | Levantar la infraestructura completa: `docker compose --env-file ../env/.env.development --profile full up -d` (desde `infra/docker/`). | `docker compose ps` muestra `integrate-postgres`, `integrate-localstack` e `integrate-mailpit` en *healthy*. | ☐ |
| 0.1.2 | Cargar la semilla de datos de prueba ([guía de desarrollo §6](../guia-desarrollo.md#6-datos-de-prueba)). | Sin errores de `psql`. Existen `turista1…3`, `gestor1…2`, `anfitrion1…3` y `admin` `@ejemplo.com`. | ☐ |
| 0.1.3 | Arrancar el backend con perfil `dev` y anotar el puerto (8080 por defecto). | En el log aparece `Started IntegrateApplication`. | ☐ |
| 0.1.4 | Arrancar la PWA (`npm run dev` en `frontend-pwa`). | `http://localhost:5173` abre el catálogo. | ☐ |
| 0.1.5 | Abrir Mailpit en `http://localhost:8025` y vaciar el buzón. | Buzón vacío. | ☐ |

### 0.2 Navegadores

- **Navegador A** (normal) y **Navegador B** (ventana de incógnito o perfil distinto): permiten tener dos usuarios a la vez.
- Herramientas de desarrollador (F12) → modo dispositivo **375 × 812** (celular). Las pruebas de escritorio se indican aparte (1280 px).

### 0.3 Datos que usará

- Usuarios y contraseña de las cuentas de la semilla: `infra/env/.env.test-users.tabla.md` (local, ignorado por Git; la contraseña está también en `infra/env/.env.test-users`). **No la copie en este guion, en el reporte ni en Jira.**
- Para cuentas nuevas use correos únicos, por ejemplo `qa.turista.<fecha>@ejemplo.com`, y contraseñas de 12 caracteres o más.
- Un PDF pequeño (menos de 1 MB), un PDF de más de 5 MB y una imagen `.jpg`, para el enrolamiento.

### 0.4 Comandos de API (Administrador y Anfitrión)

Abra PowerShell y pegue estas funciones una sola vez por sesión. Piden la contraseña sin mostrarla y no la guardan
en ningún archivo.

```powershell
$api = 'http://localhost:8080/api/v1'   # cambie el puerto si el backend usa otro
function Login([string]$email) {
  $pw = Read-Host "Contraseña de $email" -AsSecureString
  $plain = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($pw))
  $r = Invoke-RestMethod -Method Post "$api/auth/login" -ContentType 'application/json' -Body (@{ email = $email; password = $plain } | ConvertTo-Json)
  "id: $($r.user.id)  roles: $($r.user.roles -join ', ')"; return $r.accessToken
}
function Api([string]$method, [string]$path, [string]$token, $body = $null) {
  $h = @{}; if ($token) { $h.Authorization = "Bearer $token" }
  $json = if ($body) { $body | ConvertTo-Json -Depth 6 } else { $null }
  try {
    $r = Invoke-WebRequest -UseBasicParsing -Method $method "$api$path" -Headers $h -ContentType 'application/json' -Body $json
    [pscustomobject]@{ Status = [int]$r.StatusCode; Body = if ($r.Content) { $r.Content | ConvertFrom-Json } }
  } catch {
    $resp = $_.Exception.Response; $text = (New-Object IO.StreamReader($resp.GetResponseStream())).ReadToEnd()
    [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = if ($text) { $text | ConvertFrom-Json } }
  }
}
```

Uso: `$adm = Login 'admin@ejemplo.com'` y luego `Api POST '/admin/associations' $adm @{ ... }`. Revise siempre
`Status` y, si es un error, `Body.code` y `Body.traceId`.

---

## 1. Visitante sin cuenta

Navegador A, celular (375 px), **sin** haber ingresado.

| # | Acción | Resultado esperado | ✔/✘ |
|---|---|---|---|
| 1.1 | Abrir `http://localhost:5173`. | Catálogo con las experiencias aprobadas de la semilla. La barra inferior muestra *Explorar* e *Ingresar*. | ☐ |
| 1.2 | Pulsar **Tab** una vez. | El primer foco es «Saltar al contenido», con anillo amarillo de doble borde visible. | ☐ |
| 1.3 | Buscar por una palabra del título de una experiencia y tocar la lupa. | Solo aparecen experiencias que coinciden. | ☐ |
| 1.4 | Buscar un texto que no existe (`zzzz`). | Mensaje de «sin resultados», sin errores en la consola. | ☐ |
| 1.5 | **Filtros** → elegir Departamento. | El selector *Municipio* se habilita con los municipios de ese departamento. | ☐ |
| 1.6 | Elegir Municipio, Categoría e Intensidad «Alta» → **Ver resultados**. | Los resultados respetan todos los filtros; aparecen los filtros activos. | ☐ |
| 1.7 | **Limpiar**. | Vuelve el catálogo completo. | ☐ |
| 1.8 | Abrir una experiencia. | Detalle con descripción, punto de encuentro con enlace a Google Maps, indicaciones, datos del recorrido y precio por persona. **No** aparece el documento, correo, teléfono ni la condición de víctima del anfitrión. | ☐ |
| 1.9 | Tocar el enlace del punto de encuentro. | Abre Google Maps en una pestaña nueva. | ☐ |
| 1.10 | **Atrás** en la barra superior. | Vuelve al catálogo conservando filtros y posición. | ☐ |
| 1.11 | En el detalle, **Ver fechas** → Personas con **+** hasta el máximo. | No permite pasar de 10. Los días agotados aparecen tachados. | ☐ |
| 1.12 | Elegir día y franja → **Reservar**. | Lleva a *Ingresar*. La URL conserva el destino (`volver=`). | ☐ |
| 1.13 | Abrir `http://localhost:5173/no-existe`. | Página «no encontrada» con un botón para volver. | ☐ |
| 1.14 | Abrir `http://localhost:5173/gestor` sin sesión. | Pide ingresar; no muestra el panel del gestor. | ☐ |
| 1.15 | Cambiar el dispositivo a 1280 px de ancho. | Vista de escritorio: filtros en una columna lateral y cuadrícula de experiencias. Sin desplazamiento horizontal. | ☐ |
| 1.16 | Volver a 375 px y recargar. | Vista móvil; sin desplazamiento horizontal; botones de 44 px o más. | ☐ |

---

## 2. Turista

### 2.1 Registro

Navegador A. Use un correo nuevo.

| # | Acción | Resultado esperado | ✔/✘ |
|---|---|---|---|
| 2.1.1 | **Ingresar** → **Crea una gratis** → **Crear cuenta** sin llenar nada. | Cada campo obligatorio se marca con su mensaje; el foco va al primer error; no se envía nada. | ☐ |
| 2.1.2 | Correo sin `@`. | «Correo no válido» o equivalente en el campo. | ☐ |
| 2.1.3 | Contraseña de 8 caracteres. | Error: mínimo 12 caracteres. | ☐ |
| 2.1.4 | Tipo de documento CC con letras en el número. | Error: ese tipo de documento solo lleva números. | ☐ |
| 2.1.5 | Celular sin indicativo (`3001234567`). | Se normaliza a `+57…` o se pide el formato con indicativo. | ☐ |
| 2.1.6 | Llenar todo bien **sin** marcar «Mis datos personales». | No deja crear la cuenta: la autorización de datos personales es obligatoria. | ☐ |
| 2.1.7 | Marcar solo «Mis datos personales» (sin salud) → **Crear cuenta**. | Cuenta creada; entra de una vez. *Mi cuenta* muestra «Turista». | ☐ |
| 2.1.8 | **Cerrar sesión** y repetir el registro con el **mismo correo** (o el mismo documento). | Error de cuenta ya existente (409); no se crea un duplicado. | ☐ |

### 2.2 Ingreso y sesión

| # | Acción | Resultado esperado | ✔/✘ |
|---|---|---|---|
| 2.2.1 | Ingresar con contraseña incorrecta. | «El correo o la contraseña no coinciden», sin decir cuál falló. | ☐ |
| 2.2.2 | Ingresar con un correo que no existe. | El mismo mensaje que 2.2.1 (no revela qué correos existen). | ☐ |
| 2.2.3 | Ingresar bien. | Entra; la barra muestra *Mi cuenta*. | ☐ |
| 2.2.4 | Recargar la página (F5). | Sigue con la sesión abierta (se renueva con la cookie, sin pedir la contraseña). | ☐ |
| 2.2.5 | Abrir la app en una segunda pestaña. | También aparece con la sesión abierta. | ☐ |
| 2.2.6 | F12 → *Application* → Local Storage, Session Storage e IndexedDB. | **Ningún** token de acceso guardado (solo vive en memoria). | ☐ |
| 2.2.7 | Con **otra** cuenta nueva (no use las de la semilla): fallar la contraseña 5 veces seguidas. | Al quinto intento la cuenta queda bloqueada temporalmente; un sexto intento con la contraseña **correcta** tampoco entra durante el bloqueo. | ☐ |

### 2.3 Reserva con datos de salud pendientes

Use la cuenta de 2.1.7 (sin autorización de salud).

| # | Acción | Resultado esperado | ✔/✘ |
|---|---|---|---|
| 2.3.1 | Elegir experiencia → fecha → franja con 2 personas → **Reservar**. | Formulario *Datos de los viajeros*; el viajero 1 viene precargado con su nombre. | ☐ |
| 2.3.2 | Llenar todo y **Apartar cupos**. | Pide autorizar los datos de salud antes de continuar (diálogo de consentimiento). | ☐ |
| 2.3.3 | Autorizar. | Continúa y aparta los cupos sin volver a llenar el formulario. | ☐ |

### 2.4 Validaciones del formulario de viajeros

Turista `turista1@ejemplo.com` (ya tiene autorización de salud). 2 personas.

| # | Acción | Resultado esperado | ✔/✘ |
|---|---|---|---|
| 2.4.1 | **Apartar cupos** con el formulario vacío. | El foco va al primer campo con error; no se llama a la API. | ☐ |
| 2.4.2 | Dejar vacía la EPS del viajero 2. | Error en ese campo; no se envía. | ☐ |
| 2.4.3 | Poner el mismo documento en los dos viajeros. | «Este documento ya está en otro viajero del grupo». | ☐ |
| 2.4.4 | Contacto de emergencia sin celular, o con un celular inválido (`123`). | Error en el celular; no se envía. | ☐ |
| 2.4.5 | Con datos escritos, tocar **Atrás**. | Advierte que hay datos sin enviar antes de salir. | ☐ |
| 2.4.6 | Recargar la página con datos escritos. | Los datos de viajeros **no** reaparecen (no se guardan en el teléfono). | ☐ |

### 2.5 Pago aprobado y voucher

| # | Acción | Resultado esperado | ✔/✘ |
|---|---|---|---|
| 2.5.1 | Llenar bien y **Apartar cupos** (tocar el botón dos veces rápido). | Una sola reserva; pasa a *Paga tu reserva* con contador cerca de 15:00. | ☐ |
| 2.5.2 | Revisar el resumen. | Total = precio × personas; indica que el 85 % va a la asociación. | ☐ |
| 2.5.3 | En el Navegador B, abrir la misma franja como visitante. | Los cupos disponibles bajaron en el número de personas apartadas. | ☐ |
| 2.5.4 | Elegir **PSE** → **Pagar** → **Simular pago aprobado**. | «¡Reserva confirmada!» con código QR y código de contingencia de 8 caracteres. | ☐ |
| 2.5.5 | **Mostrar QR a pantalla completa**. | QR grande y legible; se cierra con el botón o con Esc. | ☐ |
| 2.5.6 | Leer el QR con la cámara de un celular. | Muestra un texto largo de tres partes separadas por puntos (token firmado). No contiene nombres ni EPS. | ☐ |
| 2.5.7 | **Descargar voucher (PDF/A)**. | Descarga un PDF con título de la experiencia, fecha, punto de encuentro, titular, cupos, QR y código de contingencia. | ☐ |
| 2.5.8 | Revisar la ficha de viaje bajo el voucher. | Experiencia, fecha y hora, punto de encuentro, asociación, viajeros y valor pagado. | ☐ |
| 2.5.9 | Mailpit. | Llegó el correo de la reserva confirmada con el voucher, a nombre del turista. | ☐ |
| 2.5.10 | Copiar la URL de la reserva (`/reservas/<id>`), cerrar sesión, ingresar como `turista2` y abrir esa URL. | No muestra la reserva ajena (no encontrada). | ☐ |

### 2.6 Pago rechazado

| # | Acción | Resultado esperado | ✔/✘ |
|---|---|---|---|
| 2.6.1 | Hacer otra reserva → **Pagar** → **Simular pago rechazado**. | Informa el rechazo y ofrece volver a reservar. | ☐ |
| 2.6.2 | Navegador B: ver la franja. | Los cupos de la reserva rechazada volvieron a estar disponibles. | ☐ |

### 2.7 Vencimiento de los 15 minutos

| # | Acción | Resultado esperado | ✔/✘ |
|---|---|---|---|
| 2.7.1 | Apartar cupos y **no** pagar. Esperar. | En el último minuto aparece el aviso «Último minuto…». | ☐ |
| 2.7.2 | Dejar que llegue a 00:00. | Muestra que la reserva venció y ofrece volver a reservar. | ☐ |
| 2.7.3 | Esperar el siguiente barrido del worker (hasta 1 min) y ver la franja en el Navegador B. | Los cupos se liberaron. | ☐ |
| 2.7.4 | Intentar pagar la reserva vencida (volver atrás a la pantalla de pago). | No permite pagarla; muestra el estado «vencida». | ☐ |

### 2.8 Cupos agotados y concurrencia

Elija una franja con pocos cupos (por ejemplo 2).

| # | Acción | Resultado esperado | ✔/✘ |
|---|---|---|---|
| 2.8.1 | Navegador A (`turista1`) y Navegador B (`turista2`) llegan los dos a *Datos de los viajeros* pidiendo todos los cupos restantes. | Ambos ven el formulario. | ☐ |
| 2.8.2 | Tocar **Apartar cupos** en los dos casi al mismo tiempo. | Solo uno aparta los cupos; el otro recibe «quedan N cupos» o «agotado». Nunca se apartan más cupos que el aforo. | ☐ |
| 2.8.3 | Ver la franja. | Aparece agotada (día tachado si no quedan franjas libres). | ☐ |

### 2.9 Mi cuenta y salida

| # | Acción | Resultado esperado | ✔/✘ |
|---|---|---|---|
| 2.9.1 | **Mi cuenta**. | Nombre, correo, rol «Turista» y estado de cada autorización. | ☐ |
| 2.9.2 | Con una cuenta sin salud: **Autorizar datos de salud para reservar**. | El estado cambia a «Autorizado». | ☐ |
| 2.9.3 | **Cerrar sesión**. | Vuelve al catálogo; la barra muestra *Ingresar*. | ☐ |
| 2.9.4 | Recargar después de cerrar sesión. | Sigue sin sesión (la cookie de renovación quedó revocada). | ☐ |

### 2.10 Recuperar contraseña

| # | Acción | Resultado esperado | ✔/✘ |
|---|---|---|---|
| 2.10.1 | En *Ingresar*, buscar «¿Olvidaste tu contraseña?». | **[Brecha conocida]** La PWA no tiene esa opción. | ☐ |
| 2.10.2 | Por API: `Api POST '/auth/forgot-password' $null @{ email = '<correo de 2.1.7>' }`. | `Status` 202. Llega un correo a Mailpit con el enlace de restablecimiento. | ☐ |
| 2.10.3 | Repetir 2.10.2 con un correo que no existe. | También 202 y **no** llega correo (no revela qué cuentas existen). | ☐ |
| 2.10.4 | Abrir el enlace del correo. | **[Brecha conocida]** Abre `/restablecer-contrasena`, que la PWA no tiene: muestra «no encontrada». | ☐ |
| 2.10.5 | Tomar el token del enlace (lo que va después de `#token=`) y llamar `Api POST '/auth/reset-password' $null @{ token = '<token>'; newPassword = '<nueva clave de 12+>' }`. | `Status` 204. Llega a Mailpit el aviso de contraseña cambiada. | ☐ |
| 2.10.6 | Ingresar con la contraseña vieja y luego con la nueva. | La vieja falla; la nueva entra. Las sesiones abiertas antes del cambio se cierran al renovarse. | ☐ |
| 2.10.7 | Repetir 2.10.5 con el mismo token. | `Status` 401: el token es de un solo uso. | ☐ |

---

## 3. Administrador

El Administrador no tiene pantallas propias en la Fase 1. Primero se prueba su ingreso en la PWA y después sus
tareas por la API (sección 0.4).

### 3.1 En la PWA

| # | Acción | Resultado esperado | ✔/✘ |
|---|---|---|---|
| 3.1.1 | Ingresar con `admin@ejemplo.com`. | Entra; *Mi cuenta* muestra «Turista · Administrador». | ☐ |
| 3.1.2 | Abrir `/gestor`. | «Esta sección no está disponible para tu cuenta» y **Ir al inicio**. | ☐ |
| 3.1.3 | Reservar y pagar una experiencia como en 2.5. | Funciona igual que para un turista. | ☐ |

### 3.2 Por la API: asociaciones y anfitriones

```powershell
$adm = Login 'admin@ejemplo.com'
```

| # | Acción | Resultado esperado | ✔/✘ |
|---|---|---|---|
| 3.2.1 | Crear una asociación: `$a = Api POST '/admin/associations' $adm @{ nit = '900123456-1'; rntNumber = '123456'; legalName = 'Asociación QA'; associationType = 'ASOCIACION_VICTIMAS'; municipalityCode = '05001'; address = 'Vereda QA'; contactEmail = 'qa@ejemplo.com'; contactPhone = '+573001234567' }`. Guarde `$a.Body.id`. | `Status` 201. | ☐ |
| 3.2.2 | Repetir 3.2.1 con el mismo NIT. | `Status` 409. | ☐ |
| 3.2.3 | `Api GET '/admin/associations' $adm`. | La asociación nueva aparece en la lista. | ☐ |
| 3.2.4 | Registrar en la PWA un turista nuevo (será anfitrión directo) y obtener su id con `$h = Login '<su correo>'` (la función imprime `id` y roles). Luego `Api POST '/admin/host-profiles' $adm @{ userId = '<id>'; associationId = $a.Body.id }`. | `Status` 201. Al volver a ingresar, ese usuario tiene el rol «Anfitrión comunitario». | ☐ |

### 3.3 Por la API: acreditar Gestores de Enlace

| # | Acción | Resultado esperado | ✔/✘ |
|---|---|---|---|
| 3.3.1 | Registrar en la PWA otro turista nuevo (futuro gestor), obtener su id con `Login` y acreditarlo: `$g = Api POST '/admin/link-managers' $adm @{ userId = '<id>'; associationId = $a.Body.id; accreditedOn = (Get-Date -Format 'yyyy-MM-dd') }`. | `Status` 201 con `accreditationStatus` activo. | ☐ |
| 3.3.2 | Acreditar con `accreditedOn` en el futuro. | `Status` 422. | ☐ |
| 3.3.3 | Ingresar en la PWA con el nuevo gestor (cerrar sesión y volver a entrar). | Ve *Jornada* y *Anfitriones*. | ☐ |
| 3.3.4 | Suspender: `Api POST "/admin/link-managers/$($g.Body.linkManagerId)/suspend" $adm`. Luego, como ese gestor, intentar enrolar un anfitrión (sección 4.2). | Suspensión `Status` 200. El enrolamiento queda bloqueado (403 «gestor no activo»). | ☐ |
| 3.3.5 | Reactivar: `.../reinstate`. Volver a enrolar. | Vuelve a poder enrolar. | ☐ |
| 3.3.6 | (Opcional, al final) Revocar: `.../revoke`. | El gestor ya no puede enrolar. | ☐ |

### 3.4 Por la API: moderación de experiencias

Necesita una experiencia en revisión (sección 5.3 o 6).

| # | Acción | Resultado esperado | ✔/✘ |
|---|---|---|---|
| 3.4.1 | Cola de revisión: `Api GET '/admin/experiences?status=PENDING_REVIEW' $adm`. | Aparece la experiencia enviada por el anfitrión. | ☐ |
| 3.4.2 | Rechazar sin motivo: `Api POST "/admin/experiences/<id>/reject" $adm`. | `Status` 422 (el motivo es obligatorio). | ☐ |
| 3.4.3 | Rechazar con motivo: `... reject $adm @{ reason = 'Falta el punto de encuentro exacto' }`. | `Status` 200; estado «rechazada». No aparece en el catálogo. | ☐ |
| 3.4.4 | (El anfitrión la corrige y la reenvía, 5.3.6.) Aprobar: `.../approve`. | `Status` 200; aparece en el catálogo público. | ☐ |
| 3.4.5 | Aprobar otra vez la misma. | `Status` 409 (transición no válida). | ☐ |
| 3.4.6 | Suspender con motivo: `.../suspend` `@{ reason = 'Revisión de seguridad' }`. | Desaparece del catálogo y sus franjas futuras dejan de ser reservables. | ☐ |
| 3.4.7 | Reactivar: `.../reinstate`. | Vuelve al catálogo con sus franjas. | ☐ |

---

## 4. Gestor de Enlace

Navegador A con `gestor1@ejemplo.com`.

### 4.1 Portal

| # | Acción | Resultado esperado | ✔/✘ |
|---|---|---|---|
| 4.1.1 | Ingresar. | Barra inferior: *Jornada*, *Anfitriones*, *Mi cuenta*. *Mi cuenta* muestra «Turista · Gestor de Enlace». | ☐ |
| 4.1.2 | *Jornada*. | Tarjetas **Enrolar anfitrión** y **Mis anfitriones**. | ☐ |
| 4.1.3 | F12 → *Network* → **Offline**. | El indicador cambia de «En línea» a sin conexión. | ☐ |
| 4.1.4 | Volver a **No throttling**. | El indicador vuelve a «En línea». | ☐ |

### 4.2 Enrolar un anfitrión

| # | Acción | Resultado esperado | ✔/✘ |
|---|---|---|---|
| 4.2.1 | **Enrolar anfitrión** → avanzar sin datos. | Errores en nombre, documento y celular; no avanza. | ☐ |
| 4.2.2 | Paso 1 con documento y celular válidos y **sin correo**. | Avanza; el correo es opcional. | ☐ |
| 4.2.3 | Paso 2: condición de víctima **Sí**. | Pide los datos de víctima (RUV y hecho victimizante opcionales, soporte opcional). | ☐ |
| 4.2.4 | Paso 3 sin marcar las dos autorizaciones. | No deja registrar. | ☐ |
| 4.2.5 | Cargar una imagen `.jpg` como formato de consentimiento. | La rechaza: solo PDF. | ☐ |
| 4.2.6 | Cargar un PDF de más de 5 MB. | Lo rechaza por tamaño. | ☐ |
| 4.2.7 | Cargar el PDF pequeño, marcar ambas autorizaciones → **Registrar anfitrión**. | «Anfitrión registrado», estado **Pendiente de activación**, correo de acceso terminado en `@comunidad.integrate.co`. | ☐ |
| 4.2.8 | Enrolar de nuevo a la misma persona (mismo documento). | Error: la persona ya existe (409). | ☐ |
| 4.2.9 | Enrolar a otra persona **con** correo propio y condición de víctima **No**. | Registrada con ese correo. | ☐ |
| 4.2.10 | Intentar reusar el mismo PDF del paso 4.2.7 en un enrolamiento nuevo. | Lo rechaza: el formato firmado ya respalda otro consentimiento. | ☐ |

### 4.3 Código de activación

| # | Acción | Resultado esperado | ✔/✘ |
|---|---|---|---|
| 4.3.1 | **Emitir código de activación**. | Código `XXXXX-XXXXX`, vence en 72 horas. **Anótelo**; no se vuelve a mostrar. | ☐ |
| 4.3.2 | Recargar la página. | El código ya no se ve. | ☐ |
| 4.3.3 | *Anfitriones* → la misma persona → **Emitir código de activación** de nuevo. Anotar el segundo código. | Nuevo código; el primero deja de servir (se prueba en 5.1.3). | ☐ |
| 4.3.4 | Revisar la lista *Mis anfitriones*. | Documento enmascarado (solo los últimos 4 dígitos); ningún dato de víctima visible. | ☐ |

### 4.4 El gestor también es turista

| # | Acción | Resultado esperado | ✔/✘ |
|---|---|---|---|
| 4.4.1 | *Explorar* → reservar y pagar como en 2.5. | Funciona igual. | ☐ |

---

## 5. Anfitrión comunitario

### 5.1 Activación (persona enrolada en 4.2.7)

Navegador B, sin sesión.

| # | Acción | Resultado esperado | ✔/✘ |
|---|---|---|---|
| 5.1.1 | Abrir `/activar` → correo de acceso, **primer** código (4.3.1), contraseña nueva. | «El código no es válido, venció o ya se usó…» (fue reemplazado en 4.3.3). | ☐ |
| 5.1.2 | Con el segundo código y una contraseña de 8 caracteres. | Error: mínimo 12 caracteres. | ☐ |
| 5.1.3 | Con el segundo código, contraseña de 12+ y confirmación distinta. | Error: las contraseñas no coinciden. | ☐ |
| 5.1.4 | Con el segundo código y contraseña válida → **Activar cuenta**. | «Cuenta activada» y botón **Ingresar**. | ☐ |
| 5.1.5 | Volver a usar el mismo código. | Error: ya se usó. | ☐ |
| 5.1.6 | **Ingresar** con el correo de acceso y la nueva contraseña. | Entra; *Mi cuenta* muestra «Anfitrión comunitario» y las autorizaciones de datos personales y de condición de víctima. | ☐ |
| 5.1.7 | Navegador A (gestor) → *Anfitriones*. | Esa persona aparece **Activa**. | ☐ |
| 5.1.8 | Como anfitrión, abrir `/gestor`. | «Esta sección no está disponible para tu cuenta». | ☐ |

### 5.2 Anfitrión que quiere viajar

| # | Acción | Resultado esperado | ✔/✘ |
|---|---|---|---|
| 5.2.1 | Intentar reservar una experiencia. | Pide autorizar datos de salud. | ☐ |
| 5.2.2 | Autorizar y terminar la reserva. | Funciona. Si el rol de turista no viene con el enrolamiento, anote el comportamiento real. | ☐ |

### 5.3 Proponer experiencias (por la API)

Use `anfitrion1@ejemplo.com` o el anfitrión de 3.2.4: `$host1 = Login '<correo>'`.

| # | Acción | Resultado esperado | ✔/✘ |
|---|---|---|---|
| 5.3.1 | Crear la propuesta: `$e = Api POST '/host/experiences' $host1 @{ title = 'Sendero QA de la memoria'; description = 'Recorrido guiado por la comunidad.'; safetyGuidelines = 'Llevar agua y botas.'; physicalIntensity = 'MEDIUM'; durationMinutes = 240; municipalityCode = '05001'; vereda = 'La Esperanza'; meetingPoint = 'Parque principal'; latitude = 6.25; longitude = -75.56; maxCapacityPerSlot = 8; unitPriceCop = 45000; categoryCodes = @('SENDERISMO_MEMORIA') }`. | `Status` 201, estado borrador. **No** aparece en el catálogo público. | ☐ |
| 5.3.2 | Crear franjas: `Api POST "/host/experiences/$($e.Body.id)/schedules" $host1 @{ schedules = @(@{ localStart = '<AAAA-MM-DD>T07:00:00'; localEnd = '<AAAA-MM-DD>T12:00:00'; totalSlots = 4 }) }` con una fecha futura. | `Status` 201. | ☐ |
| 5.3.3 | Franja con `totalSlots = 9` (más que la capacidad 8). | `Status` 422 (capacidad de carga). | ☐ |
| 5.3.4 | Franja que se cruza en horario con la de 5.3.2. | `Status` 409 (conflicto de franjas). | ☐ |
| 5.3.5 | Enviar a revisión: `Api POST "/host/experiences/$($e.Body.id)/submit" $host1`. | `Status` 200, estado «en revisión». Seguir con 3.4.1 a 3.4.3 (el administrador la rechaza). | ☐ |
| 5.3.6 | Tras el rechazo, editar con `Api PUT "/host/experiences/$($e.Body.id)" $host1 @{ ...mismo cuerpo con meetingPoint corregido... }` y volver a enviar (5.3.5). | Vuelve a «en revisión». Seguir con 3.4.4 (aprobación). | ☐ |
| 5.3.7 | Ya aprobada, como visitante abrir el catálogo. | La experiencia aparece con su franja reservable. | ☐ |
| 5.3.8 | Bajar la capacidad a 3 (`PUT` con `maxCapacityPerSlot = 3`) teniendo una franja de 4 abierta. | `Status` 422 o 409: no puede quedar por debajo de una franja abierta. | ☐ |
| 5.3.9 | Ver una experiencia de **otro** anfitrión: `Api GET '/host/experiences/<id ajeno>' $host1`. | `Status` 404. | ☐ |
| 5.3.10 | Cancelar la franja: `Api POST '/host/schedules/<idFranja>/cancel' $host1 @{ reason = 'Lluvias' }`. | `Status` 200; en la PWA la franja deja de aparecer o figura como no reservable. | ☐ |

---

## 6. Ciclo completo encadenado

Recorrido único que une a los cuatro roles con datos nuevos, en este orden. Marque ✔ solo si todo el paso
funciona sin intervenir la base de datos.

| # | Rol | Acción | Resultado esperado | ✔/✘ |
|---|---|---|---|---|
| 6.1 | Admin | Crear asociación (3.2.1). | 201. | ☐ |
| 6.2 | Turista nuevo | Registrarse en la PWA. | Cuenta activa. | ☐ |
| 6.3 | Admin | Acreditar a esa persona como gestor de la asociación (3.3.1). | 201; al reingresar ve *Jornada*. | ☐ |
| 6.4 | Gestor | Enrolar un anfitrión con PDF y emitir su código (4.2.7, 4.3.1). | Pendiente de activación + código. | ☐ |
| 6.5 | Anfitrión | Activar la cuenta e ingresar (5.1.4, 5.1.6). | Rol «Anfitrión comunitario». | ☐ |
| 6.6 | Anfitrión | Crear experiencia y una franja de 3 cupos, y enviarla a revisión (5.3.1, 5.3.2, 5.3.5). | En revisión. | ☐ |
| 6.7 | Admin | Aprobarla (3.4.4). | Visible en el catálogo. | ☐ |
| 6.8 | Turista A | Reservar 2 cupos y pagar (aprobado). | Voucher con QR; correo en Mailpit. | ☐ |
| 6.9 | Turista B | Intentar reservar 2 cupos en la misma franja. | Solo queda 1 cupo: lo informa y no permite 2. | ☐ |
| 6.10 | Turista B | Reservar 1 cupo y no pagar hasta que venza. | A los 15 min el cupo vuelve a estar libre. | ☐ |
| 6.11 | Anfitrión | Cancelar la franja (5.3.10). | Deja de ser reservable. **Anote** qué pasa con la reserva confirmada del Turista A (la política de cancelación es de la Fase 2). | ☐ |

---

## 7. Control de acceso cruzado

Con el token de cada rol, llamar a lo que **no** le corresponde.

| # | Quién | Acción | Resultado esperado | ✔/✘ |
|---|---|---|---|---|
| 7.1 | Sin token | `Api GET '/admin/associations' $null` | 401. | ☐ |
| 7.2 | Turista | `Api GET '/admin/associations' $tur` | 403. | ☐ |
| 7.3 | Turista | `Api POST '/host/experiences' $tur @{ ... }` | 403. | ☐ |
| 7.4 | Turista | `Api GET '/gestor/hosts' $tur` | 403. | ☐ |
| 7.5 | Anfitrión sin rol turista | `Api POST '/bookings/lock' ...` | 403. | ☐ |
| 7.6 | Gestor | `Api POST '/admin/experiences/<id>/approve' $ges` | 403. | ☐ |
| 7.7 | Cualquiera | Token con una letra cambiada en el medio. | 401. | ☐ |
| 7.8 | Cualquiera | Esperar 16 minutos sin usar la PWA y luego navegar. | La sesión se renueva sola (cookie); por API, el token viejo responde 401. | ☐ |

---

## 8. Verificaciones finales en correo y base de datos

| # | Acción | Resultado esperado | ✔/✘ |
|---|---|---|---|
| 8.1 | Mailpit: revisar todos los correos de la prueba. | Un correo por voucher emitido (ninguno duplicado), recuperación y aviso de contraseña cambiada. Ninguno trae EPS ni datos de víctima. | ☐ |
| 8.2 | `docker exec integrate-postgres psql -U postgres -d integrate -c "SELECT * FROM booking.v_schedule_slot_reconciliation WHERE has_drift"` | 0 filas: ninguna franja con más cupos vendidos que su aforo. | ☐ |
| 8.3 | `... -c "SELECT * FROM audit.verify_chain()"` | 0 filas: la bitácora de auditoría no fue alterada. | ☐ |
| 8.4 | `... -c "SELECT count(*) FROM auth.users u WHERE NOT EXISTS (SELECT 1 FROM auth.habeas_data_consents c WHERE c.user_id = u.id)"` | 0, salvo el administrador inicial si se creó por *bootstrap* **[Brecha conocida, RNF-06]**. | ☐ |
| 8.5 | `... -c "SELECT victim_condition_enc FROM catalog.host_profiles WHERE victim_condition_enc IS NOT NULL LIMIT 1"` | Bytes cifrados, no texto legible. | ☐ |
| 8.6 | Revisar el log del backend. | Sin `ERROR` inesperados; cada error HTTP tiene su `traceId`. | ☐ |

---

## 9. Registro de resultados y defectos

### 9.1 Encabezado de la ejecución

| Campo | Valor |
|---|---|
| Fecha y hora | |
| Probador | |
| Rama y commit (`git rev-parse --short HEAD`) | |
| Navegador y versión | |
| Puerto del backend | |
| Total de pasos ✔ / ✘ / no ejecutados | |

### 9.2 Plantilla de defecto

Un defecto por fila. Se registra después en el gestor de incidencias (Jira), que alimenta las métricas «Defectos por
módulo» y «MTTR» del Plan de Gestión de la Calidad (§3.3).

| ID | Paso | Rol | Resultado esperado | Resultado real | `traceId` / captura | Severidad (Alta/Media/Baja) |
|---|---|---|---|---|---|---|
| DEF-01 | | | | | | |

**Severidad:** *Alta* bloquea el flujo o expone datos (sobreventa, acceso indebido, dato sensible visible); *Media*
tiene alternativa pero el resultado es incorrecto; *Baja* es de texto, estilo o mensaje.

### 9.3 Brechas conocidas (no son defectos nuevos)

| Brecha | Paso | Referencia |
|---|---|---|
| La PWA no tiene «¿Olvidaste tu contraseña?» ni la pantalla `/restablecer-contrasena` del enlace del correo. | 2.10.1, 2.10.4 | ADR-0014 (solo backend) |
| El Administrador y la gestión de experiencias del Anfitrión no tienen pantallas. | 3, 5.3 | Manual de usuario §2 |
| El detalle público muestra coordenadas exactas. | 1.8 | No conformidad RNF-01 |
| La cuenta del administrador inicial nace sin consentimiento registrado. | 8.4 | No conformidad RNF-06 |
| El PDF del voucher no trae EPS ni contacto de emergencia. | 2.5.7 | No conformidad RNF-23 (decisión de ADR-0010) |
| Validación offline del QR, contingencia y cancelación con reembolso. | 6.11 | Fase 2 (T-2.1 a T-2.13) |
