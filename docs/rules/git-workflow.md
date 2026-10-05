# PROTOCOLO DE FLUJO DE TRABAJO EN GIT Y GOBERNANZA DE CAMBIOS

| Metadato | Detalle |
| :--- | :--- |
| **Código Normativo** | `GIT-WF-001` |
| **Estado** | Activo / Obligatorio |
| **Ámbito** | Todo desarrollo en `integrate` (Backend, Frontend, Infraestructura, Documentación) |
| **Relación de Dependencia** | Complementario a `SEC-DIR-004` (Gestión de Secretos) y `ADR-WF-001` (Workflow ADR) |

---

## 1. Ciclo de Vida y Estados de un Cambio en Git

Cada cambio en el proyecto transita obligatoriamente por 5 estados secuenciales:

```
[1. Working Directory] ──> [2. Staging Area] ──> [3. Commit Local] ──> [4. Push Remoto] ──> [5. PR & Merge]
   (En desarrollo)           (git add selectivo)   (Criterios OK)        (Hito verificado)     (Revisión / CI)
```

1. **Estado 1: Working Directory (Modificado / Sin seguimiento):**
   - El código se está escribiendo localmente para atender una única subtarea del backlog.
   - En este estado el código puede estar incompleto mientras se ajusta la implementación.

2. **Estado 2: Staging Area (`git add` selectivo):**
   - Se seleccionan **única y exclusivamente** los archivos que pertenecen a la subtarea resuelta.
   - Queda terminantemente prohibido ejecutar `git add .` a ciegas.

3. **Estado 3: Commit Local (`git commit`):**
   - Se congela una unidad atómica de trabajo funcional que **compila y supera los criterios de aceptación**.

4. **Estado 4: Push Remoto (`git push`):**
   - Se envían los commits verificados al repositorio remoto en GitHub.

5. **Estado 5: Pull Request & Merge (Integración continua):**
   - En ramas de feature, se somete a revisión del Tech Lead y a los checks automáticos de CI (Gitleaks, compilación y tests).

---

## 2. ¿Cuándo Hacer Commit? (Reglas de Compromiso Local)

### Regla de Oro: 1 Subtarea Técnica Aprobada = 1 Commit Atómico
Un commit se realiza **únicamente** cuando se cumplan simultáneamente las siguientes 4 condiciones:

1. **Criterios de Aceptación Cumplidos:** La subtarea técnica fue probada localmente (compilación limpia con `./mvnw compile`, tests o smoke test de Docker).
2. **Revisión del Tech Lead:** Se confirmó que el diseño técnico cumple los requerimientos de la arquitectura y la tarea fue marcada como `[x]` en `docs/status/PROJECT_STATUS.md`.
3. **Checklist de Secretos (SEC-DIR-004) Verificado:** Ningún archivo `.env`, `.env.development`, certificado, token o clave privada está en el área de preparación (`staging`).
4. **Co-Commit de Documentación:** Si la tarea incluyó un ADR o actualizó el estado en `PROJECT_STATUS.md`, estos archivos deben formar parte del mismo commit para mantener la trazabilidad histórica.

> [!CAUTION]
> **Prohibido comitear código roto:** No se deben realizar commits de "salvaguarda" con errores de compilación o dependencias rotas en las ramas compartidas.

---

## 3. ¿Cuándo Hacer Push y Subir Cambios a GitHub? (Política por Fases)

> [!IMPORTANT]
> **Directiva Mandatoria de Entrega:** La subida de cambios remota (`git push`) hacia GitHub se realiza **exclusivamente al culminar cada FASE COMPLETA del Roadmap**, no por cada subtarea aislada.

### Ciclo Operativo por Fases:
1. **Durante la Fase (Trabajo Local Atómico):**
   - A medida que se finaliza y aprueba cada subtarea (`T-X.1`, `T-X.2`, etc.), se realiza un **commit local atómico**.
   - Los commits se van acumulando en tu rama local asegurando un historial limpio y detallado.
2. **Al Culminar la Fase (Hito de Push y Entrega):**
   - Cuando todas las subtareas de la Fase estén marcadas como `[x]` en `docs/status/PROJECT_STATUS.md`.
   - Se ejecuta la verificación integral (Definition of Done de la Fase):
     * Backend: `./mvnw.cmd test` en verde (todas las pruebas pasan).
     * Frontend: compilación y linter sin errores (`npm run build`).
     * Infraestructura: contenedores arrancando de forma reproducible.
     * Checklist SEC-DIR-004 validado (cero secretos).
   - Se realiza el `git push` formal del paquete completo de la Fase a GitHub:
     ```powershell
     # Verificación previa
     git status
     # Subida oficial de la Fase
     git push origin <rama-de-fase-o-main>
     ```
   - Se crea el tag o Pull Request correspondiente a la Fase entregada (ej. `release/phase-0-foundation`, `release/phase-1-mvp`).

---

## 4. ¿Qué Esperar en Cada Cambio? (Estructura y Anatomía del Diff)

Cada cambio debe ser predecible, legible y auditable.

### 4.1 Formato Estandarizado de Mensajes (Conventional Commits)
Los mensajes de commit deben seguir la estructura:
```text
<tipo>(<ámbito>): [ID-Tarea] <descripción en imperativo y minúsculas>
```

- **Tipos permitidos:**
  - `feat`: Nueva funcionalidad o código funcional (ej. `feat(backend): [T-0.4] configurar clase de entrada y perfiles yaml`).
  - `chore`: Tareas de infraestructura, dependencias o configuración (ej. `chore(infra): [T-0.1] estructurar docker-compose y variables`).
  - `docs`: Documentación y ADRs (ej. `docs(adr): [T-0.3] redactar ADR-0002 para stack de backend`).
  - `test`: Incorporación o ajuste de pruebas (ej. `test(backend): [T-0.5] agregar pruebas de integración jpa`).
  - `fix`: Corrección de un defecto o bug.
  - `refactor`: Refactorización de código sin cambio de comportamiento externo.

### 4.2 Anatomía del Diff: Qué SÍ y Qué NO debe contener un commit

| Permitido en el Commit ✅ | Estrictamente Prohibido ❌ |
| :--- | :--- |
| Archivos fuente de la tarea (`.java`, `.yml`, `.sql`, `.ts`) | Archivos `.env`, `.env.development` o con secretos (SEC-DIR-004) |
| Documentos ADR aceptados (`docs/architecture/adr/`) | Directorios de compilación (`target/`, `node_modules/`, `build/`) |
| Estado del proyecto actualizado (`docs/status/PROJECT_STATUS.md`) | Archivos locales del IDE (`.idea/`, `.vscode/workspace.json`, `.DS_Store`) |
| Archivos `.env.example` con placeholders descriptivos | Claves privadas (`.pem`, `.key`, `id_rsa`) |
| Scripts de inicialización o wrappers (`mvnw`, `docker-compose.yml`) | Archivos de log (`*.log`) o volcados de bases de datos |

---

## 5. Estrategia de Ramificación (Branching Model)

Para el desarrollo del proyecto se utiliza **GitHub Flow con ramas de feature cortas**:

- **`main`:** Rama protegida de producción. Refleja el estado estable y desplegable.
- **`develop` (o tronco central de desarrollo):** Rama de integración de las fases en curso.
- **`feature/<id-tarea>-<nombre-corto>`:** Ramas de trabajo para cada requerimiento (ej. `feature/T-0.4-backend-bootstrap`).
- **`hotfix/<nombre>`:** Correcciones críticas sobre producción.

### Flujo de Integración:
1. Crear rama desde `develop`: `git checkout -b feature/T-0.4-backend-bootstrap develop`
2. Desarrollar la subtarea, validar aceptación localmente y actualizar `PROJECT_STATUS.md`.
3. Comitear siguiendo Conventional Commits: `git commit -m "feat(backend): [T-0.4] ..."`
4. Hacer push: `git push -u origin feature/T-0.4-backend-bootstrap`
5. Abrir Pull Request hacia `develop` para revisión y validación de checks automáticos.
