# DIRECTIVA DE SEGURIDAD Y GESTIÓN DE SECRETOS (SEC-DIR-004)
## Estándar Obligatorio para Código Fuente, Repositorios Git y GitHub

| Metadato | Detalle |
| :--- | :--- |
| **Código Normativo** | `SEC-DIR-004-SECRETS-MGMT` |
| **Estado** | Activo / Bloqueante (*Zero-Tolerance*) |
| **Ámbito** | Desarrollo, CI/CD, Repositorios Git y GitHub |
| **Fecha de Efectividad** | Inmediata |

---

## 1. Principio Fundamental: Tolerancia Cero al "Hardcoding"

Queda **estrictamente prohibido** incrustar o versionar cualquier credencial, llave de API, token de autenticación (JWT, OAuth), contraseña de base de datos, certificado o llave criptográfica privada en:
- Código fuente (Java, TypeScript, Python, etc.).
- Archivos de configuración versionados (`application.yml`, `package.json`, `docker-compose.yml`, etc.).
- Comentarios de código o mensajes de commit.

> [!CAUTION]
> **Axioma de Compromiso Inmediato:** Todo secreto que toque un commit de Git (local o remoto) se considera **inmediatamente vulnerado y público**. Ejecutar `git revert`, un nuevo commit eliminando la clave o alterar el archivo en un commit posterior **NO** remueve la clave del historial de Git ni elimina el riesgo de exfiltración.

---

## 2. Gestión de Entornos Locales y `.gitignore`

Toda configuración sensible debe desacoplarse del código siguiendo los principios de **The Twelve-Factor App**:

### 2.1 Configuración Mandatoria en `.gitignore`
El archivo `.gitignore` raíz debe contener de manera inmutable:
```gitignore
# ==============================================================================
# SEGURIDAD Y GESTIÓN DE SECRETOS (SEC-DIR-004)
# ==============================================================================
.env
.env.*
!.env.example
*.pem
*.key
*.p12
*.jks
*.keystore
id_rsa*
secrets/
credentials.json
```

### 2.2 Plantillas `.env.example`
- Solo se versionará un archivo `.env.example`.
- Debe contener la lista completa de variables requeridas por el sistema con valores dummy descriptivos.
- **Prohibido incluir valores reales o credenciales funcionales de prueba.**

```bash
# .env.example (VÁLIDO)
DATABASE_URL=postgresql://localhost:5432/integrate_db
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=YOUR_STRONG_PASSWORD_HERE
JWT_SECRET_KEY=PLACEHOLDER_MINIMUM_256_BITS_RANDOM_SECRET
AWS_S3_BUCKET_NAME=my-bucket-dev
```

---

## 3. Gobernanza de Secretos en GitHub y CI/CD

El uso de GitHub como plataforma central exige la aplicación estricta de las siguientes medidas:

### 3.1 GitHub Encrypted Secrets y Environments
1. **Separación por Entornos:**
   - Utilizar **GitHub Environments** (`development`, `staging`, `production`) con reglas de protección de ramas y revisores obligatorios para desplegar a producción.
   - Configurar los secretos sensibles a nivel de *Environment Secret*, no de *Repository Secret* global, a menos que sean compartidos.
2. **Enmascaramiento Obligatorio (*Masking*):**
   - GitHub Actions enmascara automáticamente los secretos definidos en `secrets.*`. Sin embargo, está prohibido transformar secretos a formatos que evadan el filtro (por ejemplo, `base64`, `json_encode` o concatenaciones que se impriman en stdout/stderr).
3. **Mínimo Privilegio en Workflows (`GITHUB_TOKEN`):**
   - Declarar permisos mínimos explícitos en los archivos de workflow:
     ```yaml
     permissions:
       contents: read
       pull-requests: write
     ```

### 3.2 GitHub Secret Scanning y Push Protection
- Todo repositorio de la organización debe tener habilitado **Secret Scanning** y **Push Protection** en los ajustes de seguridad de GitHub.
- Si Push Protection bloquea un `git push`, **queda prohibido saltarse la advertencia (*bypass*)**. Se debe remover el secreto del commit antes de volver a enviar.

### 3.3 Barrido en GitHub Actions (PR Gatekeeper)
Todo Pull Request ejecutará un job bloqueante de detección de secretos mediante **Gitleaks**:

```yaml
# .github/workflows/secret-scan.yml
name: Security Secret Scan

on:
  pull_request:
    branches: [ main, develop ]

jobs:
  gitleaks:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - name: Gitleaks Action
        uses: gitleaks/gitleaks-action@v2
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

---

## 4. Secretos en Entornos de Producción

En producción está prohibido el uso de archivos `.env`.
- **Gestores Centralizados:** Se utilizará **HashiCorp Vault**, **AWS Secrets Manager** o **AWS Systems Manager Parameter Store** (tipo `SecureString`).
- **Inyección en Tiempo de Ejecución:** Los contenedores y servicios obtendrán las credenciales en memoria al momento del arranque.
- **Fail-Fast:** La aplicación validará la presencia de todas las variables críticas durante el bootstrap; si falta alguna variable obligatoria, el proceso se abortará de inmediato.

---

## 5. Ejemplos de Implementación: Prohibido vs. Permitido

### 5.1 Frontend / TypeScript
```typescript
// ❌ PROHIBIDO: Token quemado en el cliente o fallback inseguro
const API_TOKEN = process.env.VITE_API_TOKEN || "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...";

// ✅ PERMITIDO: Variables de entorno validadas sin fallbacks sensibles
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;
if (!API_BASE_URL) {
  throw new Error("VITE_API_BASE_URL no está configurada en las variables de entorno.");
}
```

### 5.2 Backend / Spring Boot (Java 21)
```yaml
# ❌ PROHIBIDO (application.yml): Credenciales fijas
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/integrate_db
    username: admin
    password: PasswordReal123! # GRAVE

# ✅ PERMITIDO (application.yml): Referencia a variables de entorno
spring:
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/integrate_db}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
```

### 5.3 GitHub Actions Workflow
```yaml
# ❌ PROHIBIDO: Token hardcodeado en el workflow
steps:
  - run: curl -H "Authorization: Bearer my-production-api-token" https://api.prod.com/deploy

# ✅ PERMITIDO: Referencia a GitHub Encrypted Secrets
steps:
  - run: curl -H "Authorization: Bearer ${{ secrets.PROD_API_TOKEN }}" https://api.prod.com/deploy
```

---

## 6. Procedimiento de Mitigación Inmediata ante Fugas (SOP-SEC-01)

Si un secreto llega a ser comiteado o subido a GitHub accidentalmente, se debe ejecutar el siguiente protocolo sin dilación:

```
[1. REVOCAR Y ROTAR] ──> [2. AUDITAR ACCESOS] ──> [3. PURGAR HISTORIAL GIT] ──> [4. RE-CONFIGURAR GITHUB]
```

1. **Paso 1: Revocación Inmediata en el Proveedor (Prioridad 0)**
   - No pierda tiempo intentando borrar el commit primero. Los bots rastrean GitHub en segundos.
   - Acceda a la consola del emisor (AWS, Stripe, base de datos) y **revoque la credencial de inmediato**.
   - Genere una nueva credencial y colóquela en el gestor de secretos o en GitHub Secrets.

2. **Paso 2: Auditoría de Logs**
   - Verifique los registros de auditoría (*Audit Logs* / CloudTrail / accesos a BD) desde la hora del commit hasta la revocación para constatar que no existió uso no autorizado.

3. **Paso 3: Purgado del Historial Git**
   - Utilice `git-filter-repo` (o BFG Repo-Cleaner) para extirpar la referencia del historial:
     ```bash
     pip install git-filter-repo
     git-filter-repo --invert-paths --path .env --force
     git push origin --force --all
     git push origin --force --tags
     ```

4. **Paso 4: Notificación**
   - Notificar al Tech Lead y registrar el incidente para el análisis de causa raíz.

---

## 7. Lista de Verificación Pre-Commit y Pre-Push

Antes de hacer `git commit` o `git push` hacia GitHub, cada ingeniero debe validar:

- [ ] **1. `git status` & `git diff --cached`:** ¿Revisé cada línea staged antes de comitear?
- [ ] **2. Cero `.env` en Staging:** ¿Verifiqué que ningún archivo `.env` o credencial local esté en el commit?
- [ ] **3. Inyección vía Entorno:** ¿Todas las llaves y credenciales provienen de variables de entorno o GitHub Secrets?
- [ ] **4. Comentarios Limpios:** ¿Se eliminaron comentarios con tokens de depuración o usuarios de test?
- [ ] **5. Plantilla al Día:** ¿Se agregó el placeholder en `.env.example` si se creó una nueva variable?
- [ ] **6. Gitleaks / Secret Scanner:** ¿Corrió el hook de pre-commit sin detectar entropía sospechosa?
