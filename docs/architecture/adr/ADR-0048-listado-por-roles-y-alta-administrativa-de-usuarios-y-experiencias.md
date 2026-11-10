# ADR-0048: Listado paginado de cuentas por rol, alta administrativa de usuarios y experiencias, y acciones solo-icono en móvil

- **Estado:** Aceptado (2026-11-10) — aprobado por el desarrollador.
- **Proyecto / Módulo:** Intégrate · `backend-api/` (módulos `auth`, `catalog` y `audit`), `docs/api/` (`openapi.yaml`, `endpoints-index.md`) y `frontend-pwa/` (`features/admin`)
- **Fecha:** 2026-11-10
- **Trazabilidad:** Enmienda y amplía ADR-0006 (§3–§4), ADR-0007 (§2), ADR-0042 (§2), ADR-0043 (§1) y ADR-0047.

## Contexto

1. **Listado inicial vacío en Personas (`Todos` / `Turistas`):** Actualmente en `AdminPeopleController` existen listados paginados para gestores (`GET /api/v1/admin/link-managers`) y anfitriones (`GET /api/v1/admin/host-profiles`), pero para cuentas generales solo existe `GET /api/v1/admin/users/search` (búsqueda puntual por correo o documento exacto). Como consecuencia, al abrir la vista de usuarios en `/admin/personas`, la tabla aparece vacía y no carga las cuentas existentes. Además, el selector de la sección necesita rotularse explícitamente como **Roles** con las opciones **`Todos · Turistas · Gestores de enlace · Anfitriones`** (reemplazando la etiqueta genérica «Usuarios» por «Turistas» y colocando «Todos» al inicio).
2. **Creación de nuevos registros limitada solo a Asociaciones:** En la consola de administración solo `/admin/asociaciones` cuenta con botón de creación (`+ Nueva asociación`). Faltan los flujos y botones de creación con ventana emergente (`BottomSheet`) en **`Experiencias`** (`+ Nueva experiencia`) y en **`Personas`** (`+ Nuevo usuario`, `+ Nuevo gestor` y `+ Nuevo anfitrión`).
3. **Densidad visual en móvil:** En pantallas móviles (`< 640 px`), los botones de acción dentro de las tablas ocupan demasiado espacio horizontal cuando muestran icono + texto simultáneamente.

## Decisión

1. **Listado paginado de cuentas por rol (`GET /api/v1/admin/users`) y selector `Roles` (`Todos · Turistas · Gestores de enlace · Anfitriones`):**
   - Se expone en `AdminPeopleController` (`@PreAuthorize("hasRole('ADMIN')")`) el endpoint `GET /api/v1/admin/users?role={RoleCode}&page={p}&size={s}` con `Cache-Control: no-store`.
   - Devuelve `PageResponse<AdminUserResponse>` con las cuentas activas/abiertas ordenadas de la más reciente a la más antigua, filtrando opcionalmente por rol vigente (`TOURIST`, `HOST`, `LINK_MANAGER`, `ADMIN`), sin exponer documento completo ni celular, y registrando el acceso sensible `ADMIN_USER_LIST` en la bitácora (`SensitiveAccessRecorder`).
   - En `/admin/personas` (`PeoplePage`), el bloque superior se encabeza con la etiqueta **`Roles`** y presenta las opciones **`Todos`**, **`Turistas`**, **`Gestores de enlace`** y **`Anfitriones`**. Al abrir **`Todos`** o **`Turistas`**, la tabla consulta automáticamente `GET /api/v1/admin/users` (con `role=TOURIST` cuando está en `Turistas`, o sin filtro cuando está en `Todos`), manteniendo a la derecha el buscador unificado por correo, cédula o texto.

2. **Alta administrativa de usuarios, gestores, anfitriones y experiencias en ventana emergente (`BottomSheet`):**
   - **Nuevo usuario (`POST /api/v1/admin/users`):** Se expone en `AdminPeopleController` el endpoint `POST /api/v1/admin/users` (`@PreAuthorize("hasRole('ADMIN')")`) que permite al administrador registrar una cuenta (`fullName`, `email`, `phone`, `docType`, `docNumber`, `password`, y rol inicial opcional: `TOURIST`, `HOST` o `LINK_MANAGER` con `associationId`), registrando `USER_CREATED_BY_ADMIN` en la bitácora.
   - **Nueva experiencia (`POST /api/v1/admin/experiences`):** Se expone en `AdminCatalogController` el endpoint `POST /api/v1/admin/experiences` (`@PreAuthorize("hasRole('ADMIN')")`) que recibe `AdminExperienceCreateRequest` (`hostId` + `ExperienceRequest`), crea la experiencia para el anfitrión indicado y registra `EXPERIENCE_CREATED_BY_ADMIN` en la bitácora.
   - **Botones de creación en encabezados (`PageHeader`):**
     - En `/admin/experiencias`: botón **`+ Nueva experiencia`** que abre un `BottomSheet` con selector de anfitrión y formulario de experiencia.
     - En `/admin/personas`: botón contextual en el encabezado (**`+ Nuevo usuario`** en `Todos`/`Turistas`, **`+ Nuevo gestor`** en `Gestores de enlace`, y **`+ Nuevo anfitrión`** en `Anfitriones`) que abre su respectivo `BottomSheet`.

3. **Botones de acción solo-icono en vista móvil (`< sm:`):**
   - En los botones de acción de las celdas de tablas administrativas (`Gestionar`, `Suspender`, `Reactivar`, `Revocar`, `Ver`, `Convertir en anfitrión`, `Acreditar como gestor`) y botones de creación de encabezado, el texto visible se envuelve en `<span className="hidden sm:inline">…</span>`, mostrando en celular únicamente el icono con área táctil mínima de $44 \times 44\text{ px}$ y `aria-label` descriptivo completo.

## Alternativas Descartadas

- **Mantener `Personas -> Todos / Turistas` solo bajo búsqueda puntual:** impide al administrador explorar el padrón de turistas y usuarios registrados al abrir la pantalla.
- **Reutilizar `POST /api/v1/auth/register` desde la consola del administrador:** ese endpoint público exige el flujo de auto-registro del turista e inicia sesión con la cuenta creada, reemplazando la sesión activa del administrador.

## Consecuencias

- **Positivas:** Todas las pestañas de `Personas` (`Todos`, `Turistas`, `Gestores de enlace`, `Anfitriones`) cargan datos reales al instante; todos los módulos administrativos permiten dar de alta nuevos registros desde ventanas emergentes consistentes; las tablas en móvil ganan espacio horizontal mostrando solo iconos accesibles.
- **Negativas / Trade-offs:** Se añaden 3 endpoints administrativos (`GET /api/v1/admin/users`, `POST /api/v1/admin/users`, `POST /api/v1/admin/experiences`) documentados en `openapi.yaml` y `endpoints-index.md` y auditados en `audit.audit_trail`.

## Referencias

- [`AdminPeopleController.java`](file:///c:/Users/victx/OneDrive/Documentos/U/Ing3/Proyecto/integrate/backend-api/src/main/java/com/integrate/modules/catalog/controller/AdminPeopleController.java)
- [`AdminCatalogController.java`](file:///c:/Users/victx/OneDrive/Documentos/U/Ing3/Proyecto/integrate/backend-api/src/main/java/com/integrate/modules/catalog/controller/AdminCatalogController.java)
- [`PeoplePage.tsx`](file:///c:/Users/victx/OneDrive/Documentos/U/Ing3/Proyecto/integrate/frontend-pwa/src/features/admin/pages/PeoplePage.tsx)
- [`ExperiencesPage.tsx`](file:///c:/Users/victx/OneDrive/Documentos/U/Ing3/Proyecto/integrate/frontend-pwa/src/features/admin/pages/ExperiencesPage.tsx)

