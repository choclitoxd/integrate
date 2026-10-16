# DIRECTIVAS DE ARQUITECTURA FRONTEND, DISEÑO ACCESIBLE Y GOBERNANZA TÉCNICA (PWA)

| Metadato | Detalle |
| :--- | :--- |
| **Código Normativo** | `FE-DIR-001` |
| **Estado** | Activo / Obligatorio |
| **Ámbito** | Todo desarrollo, interfaz, componente o integración en `frontend-pwa/` |
| **Relación de Dependencia** | Complementario a `ADR-0004` (Stack Frontend), `RNF-07` / `NTC 5854` (Accesibilidad), `RNF-04` (Offline), `GIT-WF-001` y `SEC-DIR-004` (Secretos) |

---

## 1. Visión y Principio Rector

La aplicación cliente de **Intégrate** es una **Progressive Web App (PWA) Mobile-First**, diseñada prioritariamente para operar en **smartphones en entornos rurales de Colombia** (senderos de conservación, agroturismo y reservas comunitarias). Debe responder con máxima confiabilidad tanto a turistas en planificación urbana como a anfitriones y Gestores de Enlace Comunitario bajo condiciones de **luz solar directa, conectividad intermitente o nula, y pantallas táctiles de gama media/baja**.

Todo código generado para el frontend debe ceñirse estrictamente a las 7 directivas obligatorias de este documento.

---

## 2. Las 7 Directivas Obligatorias de Frontend

### Directiva 1: Enfoque Mobile-First con Vista de Escritorio Dedicada (enmendada por ADR-0020)
1. **Viewport Objetivo:** Toda vista y flujo debe concebirse primero para anchos de pantalla entre **320 px y 480 px** (factor de forma de smartphones, incluidos los de gama baja y el reflow de WCAG 1.4.10), verificada sin scroll horizontal en 320, 360, 390, 412 y 430 px.
2. **Factor de Forma y Escritorio:** La vista se elige con `useFormFactor()` (`shared/lib`), por capacidades y no por User-Agent: `desktop` ⇔ `(min-width: 1024px) and (hover: hover) and (pointer: fine)`; tablets y todo lo demás reciben la vista móvil. En `desktop` la app usa `DesktopShell` (contenedor `max-w-6xl`) y, donde exista, la vista de escritorio dedicada (`features/<f>/pages/desktop/`, cargada con `import()` diferido para que el móvil no la descargue). Sin variante de escritorio, la vista móvil se muestra centrada en `max-w-md`. Las URLs son las mismas en ambos factores de forma.
3. **Objetivos Táctiles (Target Size - WCAG 2.5.5 / 2.5.8):**
   - Todo botón, enlace, input o control interactivo debe tener un área táctil mínima de **$\ge 44 \times 44\text{ px}$** (`min-h-11 min-w-11` o con padding compensatorio).
   - Separación mínima entre elementos táctiles interactivos de $\ge 8\text{ px}$ para evitar pulsaciones erróneas en campo.
4. **Zona del Pulgar (Thumb Zone):** En la vista móvil, las acciones transaccionales primarias (ej. botón "Reservar", "Confirmar Asistencia", "Escanear QR") deben ubicarse en la parte inferior accesible con una sola mano mediante barras fijas (*sticky bottom bar* o `bottom-0`). En la vista de escritorio van en un panel lateral fijo (*sticky aside*) visible sin scroll.

---

### Directiva 2: Accesibilidad Universal y Tokens de Contraste Solar (WCAG 2.1 AA/AAA y NTC 5854)
1. **Prohibición Absoluta de Colores Arbitrarios:**
   - Queda **terminantemente prohibido** utilizar códigos hexadecimales en línea (`#...`) o clases ad-hoc no estandarizadas (ej. `bg-green-500`, `text-gray-400`).
   - Todo color de fondo, texto, borde o estado interactivo DEBE provenir de los tokens certificados en [`src/styles/tokens.ts`](file:///c:/Users/victx/OneDrive/Documentos/U/Ing3/Proyecto/integrate/frontend-pwa/src/styles/tokens.ts) y definidos en `@theme` de [`src/styles/index.css`](file:///c:/Users/victx/OneDrive/Documentos/U/Ing3/Proyecto/integrate/frontend-pwa/src/styles/index.css).
2. **Umbrales Obligatorios de Contraste:**
   - **Texto Principal (`text-ink` sobre canvas/superficie):** Debe cumplir **WCAG AAA ($\ge 7.0:1$)** (actualmente $17.74:1$), garantizando legibilidad bajo luz solar directa en campo rural.
   - **Texto Secundario (`text-ink-muted`):** Debe cumplir **WCAG AAA ($\ge 7.0:1$)** (actualmente $10.31:1$).
   - **Texto Terciario / Placeholders (`text-ink-subtle`):** Mínimo **$\ge 4.5:1$ (AA)**.
   - **Bordes e Indicadores UI (WCAG SC 1.4.11):** Mínimo **$\ge 3.0:1$** contra el fondo (`border-border`, `border-success`, `border-danger`).
3. **Foco Visible Doble Anillo (WCAG 2.4.7 y 2.4.11):**
   - Prohibido suprimir el foco con `outline-none` sin proveer el foco accesible.
   - Todo elemento interactivo enfocado por teclado debe presentar el anillo doble visible: contorno de 3 px en `--color-focus` (`#ffdd00`) con halo exterior de 6 px en `--color-focus-halo` (`#111827`), asegurando visibilidad sobre fondos claros u oscuros.
4. **Semántica HTML y Lectores de Pantalla:**
   - Encabezado único `<h1>` por página con jerarquía lógica estricta (`<h2>`, `<h3>`).
   - Landmarks semánticos obligatorios (`header`, `nav`, `main`, `footer`).
   - Idioma fijado en `lang="es-CO"` y presencia del componente [`SkipLink`](file:///c:/Users/victx/OneDrive/Documentos/U/Ing3/Proyecto/integrate/frontend-pwa/src/shared/ui/SkipLink.tsx) como primer elemento enfocable.
   - Respeto mandatorio de `prefers-reduced-motion` para desactivar transiciones y animaciones bruscas.

---

### Directiva 3: Arquitectura Modular y Fronteras Estrictas (Feature-Sliced Lite)
La estructura de carpetas en `src/` obedece a un desacoplamiento estricto auditado automáticamente por ESLint (`no-restricted-imports`):

```text
frontend-pwa/src/
├── app/        Router (routes.tsx), layouts por rol, páginas de error y 404
├── features/   Módulos de negocio (home, catalog, booking, redemption, etc.)
├── shared/     Capa compartida transversal
│   ├── api/    Cliente HTTP base y tipos ProblemDetail (RFC 9457)
│   ├── config/ Lectura y validación fail-fast de variables de entorno
│   ├── ui/     Componentes UI reutilizables y accesibles (Button, SkipLink)
│   └── lib/    Utilidades puras
└── styles/     Tokens de diseño Tailwind (@theme) y tests de contraste
```

**Reglas de Importación No Negociables:**
- `shared/` **NUNCA** puede importar de `features/` ni de `app/`.
- Una feature **NUNCA** puede importar de `app/`.
- Prohibidas las importaciones profundas hacia el interior de otra feature: la comunicación entre features se realiza **únicamente a través del archivo de barril público `index.ts`** (`@/features/<modulo>`).
- Prohibida la lógica de negocio pesada o llamadas HTTP dispersas dentro de componentes de presentación simples.

---

### Directiva 4: Seguridad, Cero Secretos y Manejo de Sesión (SEC-DIR-004)
1. **Tokens de Acceso JWT Únicamente en Memoria:**
   - El token de acceso (expiración corta de 15 minutos / 900 s) se mantiene **exclusivamente en memoria** dentro del estado de la aplicación.
   - **Queda estrictamente prohibido** persistir el token de acceso en `localStorage`, `sessionStorage` o IndexedDB, eliminando la superficie de extracción por vulnerabilidades XSS.
2. **Tokens de Refresco Seguros:**
   - El refresh token viaja únicamente en una cookie gestionada por el navegador (`HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`). El cliente debe invocar la API con `credentials: 'include'`.
3. **Variables de Entorno `VITE_*` Públicas:**
   - Toda variable que comience por `VITE_` es pública y se empaqueta en el bundle del cliente.
   - **Terminantemente prohibido** colocar contraseñas, secretos de HMAC, llaves privadas RSA/Ed25519 o credenciales de pasarela en archivos `.env` del frontend.
4. **Protección de Datos Sensibles (Habeas Data - Ley 1581 / Ley 1448):**
   - Datos de condición de víctima o historial médico/EPS no deben persistirse en texto plano en la caché local sin cifrado o anonimización controlada.

---

### Directiva 5: Consumo HTTP Estandarizado y Manejo de Errores (RFC 9457)
1. **Cliente HTTP Unificado:**
   - Prohibido utilizar `fetch` nativo directamente en componentes o añadir librerías no aprobadas (como Axios).
   - Todas las llamadas al backend se ejecutan a través de [`createHttpClient`](file:///c:/Users/victx/OneDrive/Documentos/U/Ing3/Proyecto/integrate/frontend-pwa/src/shared/api/httpClient.ts).
2. **Traducción de Errores Tipados:**
   - Toda respuesta HTTP con código 4xx o 5xx se traduce a [`ApiError`](file:///c:/Users/victx/OneDrive/Documentos/U/Ing3/Proyecto/integrate/frontend-pwa/src/shared/api/errors.ts) cumpliendo la especificación **RFC 9457 Problem Details** (`status`, `code`, `title`, `traceId`, `invalidParams`).
   - Los fallos de conectividad física sin respuesta del servidor se traducen a [`NetworkError`](file:///c:/Users/victx/OneDrive/Documentos/U/Ing3/Proyecto/integrate/frontend-pwa/src/shared/api/errors.ts).
3. **Trazabilidad y Experiencia de Usuario:**
   - Ante fallos, la interfaz debe mostrar un mensaje amigable y perceptible, junto con el **`traceId`** (código de seguimiento) para facilitar soporte y auditoría técnica.
   - Prohibido mostrar stacktraces, códigos de error en inglés no traducidos o payloads crudos de base de datos al usuario final.

---

### Directiva 6: Preparación para Operación Offline-First y Resiliencia en Campo
1. **Sensibilidad al Estado de Red:**
   - Toda vista con capacidades transaccionales (especialmente el módulo del Gestor de Enlace) debe reflejar claramente su estado de conexión (*Online*, *Desconectado / Local*, *Sincronización Pendiente*).
2. **Validación Preventiva y Optimista:**
   - Los formularios deben validar exhaustivamente en el cliente antes de encolar solicitudes para almacenamiento diferido en IndexedDB (`IntegrateOfflineDB`).
3. **Respuesta Multimodal en Campo:**
   - La lectura y validación de vouchers QR en smartphones de campo debe proveer respuesta multimodal accesible: cambio visual de color (éxito/peligro), texto accesible para lector de pantalla y confirmación por vibración háptica (`navigator.vibrate`) cuando el hardware lo soporte.

---

### Directiva 7: Protocolo de Calidad Pre-Commit (Quality Gate Obligatorio)
Antes de dar por concluida cualquier tarea técnica o realizar un commit en Git que afecte a `frontend-pwa/`, **es obligatorio ejecutar y verificar que pasen con 0 errores los 5 controles del Quality Gate**:

```bash
# 1. Comprobación estricta de tipos de TypeScript
npm run typecheck

# 2. Análisis estático de ESLint (tipos, hooks, jsx-a11y-x strict y reglas de capas)
npm run lint

# 3. Verificación de formato Prettier
npm run format:check

# 4. Pruebas unitarias y de contraste de color con Vitest
npm test

# 5. Compilación del bundle de producción sin advertencias
npm run build
```

> [!CAUTION]
> Si cualquiera de estos 5 comandos falla o arroja una sola advertencia (`--max-warnings 0`), el cambio **no está listo** y debe ser corregido antes de presentar la solución o solicitar revisión.

---

## 3. Matriz de Cumplimiento Rápido para Desarrolladores

| Aspecto | ¿Qué está permitido? | ¿Qué está PROHIBIDO? |
| :--- | :--- | :--- |
| **Diseño / CSS** | Tokens certificados (`bg-brand`, `text-ink`, `border-border`) | Hexadecimales arbitrarios (`#22c55e`, `#3b82f6`) o clases Tailwind no mapeadas a `@theme` |
| **Viewport** | Mobile-First (320–480 px); en `desktop` (`useFormFactor`) `DesktopShell` y vistas `pages/desktop/` diferidas | Layouts anchos en la vista móvil, scroll horizontal a 320 px, o detección por User-Agent |
| **Accesibilidad** | Foco doble anillo `:focus-visible`, contraste AAA $\ge 7:1$, `min-h-11` | `outline-none` sin reemplazo, fuentes pequeñas de bajo contraste, botones táctiles $< 44\text{ px}$ |
| **Arquitectura** | Importar de `@/features/<feature>` por su `index.ts` | Importaciones profundas `@/features/a/internal/File` o `shared` importando de `features` |
| **Seguridad** | Access token en memoria, refresh token en cookie HttpOnly | Guardar tokens en `localStorage`/IndexedDB; poner secretos en `.env` |
| **Peticiones HTTP** | `createHttpClient` con tipos `ProblemDetail` y `ApiError` | Invocar `fetch` directo en componentes o instalar Axios |
| **Cierre de Tarea** | Ejecutar los 5 comandos de verificación en limpio | Dejar errores de linter, advertencias de tipo o tests rotos |
