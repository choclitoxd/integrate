## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
- On this machine `graphify.exe` is blocked by Windows Application Control. Run every `graphify <cmd>` as `& "C:\Users\victx\AppData\Roaming\uv\tools\graphifyy\Scripts\python.exe" -m graphify <cmd>`.
- To build the graph from scratch, use the project skill `/graphify` (in `.claude/skills/graphify`).

## Seguridad y Gobernanza de Secretos (Código & GitHub)

- **Condición de Consulta Obligatoria:** **ÚNICAMENTE** cuando la tarea involucre **crear, modificar o revisar código fuente**, archivos de configuración, scripts de automatización o flujos de **GitHub** (commits, Pull Requests, GitHub Actions, workflows), es **OBLIGATORIO** consultar y adherirse a la directiva en [`docs/security/security_secrets_policy.md`](file:///c:/Users/victx/OneDrive/Documentos/U/Ing3/Proyecto/integrate/docs/security/security_secrets_policy.md).
- **Condición de Exclusión:** Si la consulta o tarea actual NO implica creación o edición de código fuente ni interacción con Git/GitHub (e.g., consultas conceptuales, arquitectura de alto nivel o revisión teórica de requerimientos), **NO** se debe cargar ni procesar esta regla.
- **Enfoque Prioritario en GitHub:** Prohibición absoluta de credenciales o tokens en código y workflows; uso mandatorio de GitHub Encrypted Secrets / Environments; respeto estricto a las exclusiones en `.gitignore` y cumplimiento de la lista de verificación pre-commit / pre-push.

## Flujo de Trabajo y Arquitectura

Para cualquier petición de desarrollo, nueva funcionalidad, módulo o cambio estructural en el backend, se debe seguir estrictamente el flujo definido en `@docs/rules/adr-workflow.md`, generando primero el documento ADR correspondiente y esperando la confirmación y aceptación expresa del desarrollador antes de producir código.
Para cualquier desarrollo, creación de componentes, vistas o interacción con la PWA cliente, es **OBLIGATORIO** adherirse a la directiva de frontend en [`docs/rules/frontend-rules.md`](file:///c:/Users/victx/OneDrive/Documentos/U/Ing3/Proyecto/integrate/docs/rules/frontend-rules.md) (diseño Mobile-First 360–430 px, tokens de color certificados para contraste solar WCAG AAA, áreas táctiles $\ge 44 \times 44$ px, fronteras arquitectónicas de capas y superación obligatoria de los 5 comandos del Quality Gate).
Toda interacción con Git, commits y subida de cambios debe regirse estrictamente por `@docs/rules/git-workflow.md` y la política de secretos `@docs/security/security_secrets_policy.md`.

## Precedencia sobre skills de diseño (Impeccable y similares)

La skill `impeccable` (`.claude/skills/impeccable/`) y cualquier otra skill de diseño (`frontend-design`, `make-interfaces-feel-better`, etc.) son **subordinadas** a `docs/rules/frontend-rules.md` (`FE-DIR-001`). Ante cualquier conflicto gana FE-DIR-001, sin excepción. Para Impeccable, FE-DIR-001 cuenta como el "brief fijado" que su propia regla "The brief wins" obliga a respetar. En concreto:

- **Color:** `colorize`, `bolder`, `overdrive`, `delight`, `live`, `generate` y `new-work` solo pueden usar tokens existentes de `src/styles/tokens.ts` / `@theme`. Prohibido introducir hex en línea o clases Tailwind no mapeadas. Un token nuevo o modificado exige actualizar `tokens.ts`, `index.css` y `tokens.test.ts` y que pase el test de contraste; si la propuesta lo requiere, se pide confirmación antes.
- **Contraste:** el umbral de Impeccable (≥ 4.5:1) **no** aplica. Texto principal y secundario ≥ 7:1 (AAA); terciario/placeholder ≥ 4.5:1; bordes e indicadores ≥ 3:1.
- **Foco:** no se re-tematiza el anillo de foco "desde la paleta". Se conserva el doble anillo `--color-focus` (3 px) + `--color-focus-halo` (6 px).
- **Viewport:** se diseña y verifica primero en 320–480 px. Los layouts anchos solo existen en las vistas de escritorio dedicadas de ADR-0020 (`DesktopShell`, `pages/desktop/`); las pasadas "desktop + mobile" de Impeccable no autorizan ensanchar la vista móvil. Áreas táctiles ≥ 44×44 px, separación ≥ 8 px, acciones primarias en barra inferior fija.
- **Motion:** toda animación de `animate`/`delight`/`overdrive` respeta `prefers-reduced-motion`.
- **Mundo visual:** prohibido "reemplazar el mundo visual" (redesign / `new-work` / `document` sobre DESIGN.md) o cambiar tipografías sin aprobación expresa. Las fuentes nuevas deben ser auto-hospedadas y aprobadas antes de añadirse.
- **Arquitectura y datos:** se respetan las fronteras de capas, `createHttpClient`/`ApiError`, la semántica (`h1` único, landmarks, `lang="es-CO"`, `SkipLink`) y las reglas de sesión de FE-DIR-001 aunque el comando sea solo "visual".
- **Cierre:** el "verificar en pasadas acotadas y parar" de Impeccable no sustituye el Quality Gate. Ningún trabajo hecho con Impeccable se da por terminado sin los 5 comandos de la Directiva 7 en verde.
- **Herramientas de Impeccable:** su launcher (`scripts/impeccable`) descarga un binario la primera vez. No se ejecuta sin consentimiento explícito del desarrollador en esa sesión; si no lo hay, se usa su modo "Launcher unavailable" (leer PRODUCT.md / DESIGN.md directamente). Tampoco se activan `hooks on`, el modo `live` ni ediciones de CSP sin pedirlo. Si `init` o `document` escriben PRODUCT.md / DESIGN.md, deben incluir estas restricciones como compromisos fijos.


## task-observer (activation)

Before the first tool call of any session — and before writing or
proposing a plan, not merely before executing one — invoke the
task-observer skill AND execute its Session Start Protocol (storage
check, frontmatter scan, review trigger). Loading the skill and running
the protocol are separate steps; a session that loads the file and stops
has activated nothing. Any turn that will involve a tool call counts; do
not classify the session as "too simple" from its opening message.

Select skills on the DECISION the request is about, not on the artefact it
arrived as. Name what the user is deciding, then match the installed skill
descriptions against that — a request handed over as a file to review
still needs the skill whose description names its subject.

After completing each task, list the observation records written this
session in the same turn and report a one-line summary from that listing
(ids and titles, or "none logged and why") — never from memory of
writing them.

Loading a skill is not complete until you have queried the observation
log for OPEN observations naming it and read their bodies
(`find "<WORKSPACE>/skill-observations/observation-log" -maxdepth 1 -name '*.md' -exec grep -l "skill:.*<skill-name>" {} +`,
or the PowerShell equivalent `Select-String`). Apply their insights to the
current work; editing the skill file waits for the review ("Log, don't act"
in SKILL.md).

The task-observer workspace for this project is:
  C:\Users\victx\.claude\projects\C--Users-victx-OneDrive-Documentos-U-Ing3-Proyecto-integrate
  (from WSL bash: /mnt/c/Users/victx/.claude/projects/C--Users-victx-OneDrive-Documentos-U-Ing3-Proyecto-integrate)
Every path the skill uses derives from that root and nothing else:
  <WORKSPACE>/skill-observations/observation-log/   (the log)
  <WORKSPACE>/skill-observations/cross-cutting-principles.md
  <WORKSPACE>/skill-updates/                        (staging root)
  <WORKSPACE>/skill-updates/PENDING.md              (staging manifest)
Never resolve any of them from the current working directory, and never
place the workspace inside `.claude/skills/`. The skill's snippets are bash;
on this machine there is no Git Bash, so run them via WSL `bash` with the
/mnt/c path, or translate them to PowerShell.

