# =============================================================================
# Intégrate · Imagen del backend para el despliegue de demostración (ADR-0024 §2)
# Contexto de build: backend-api/  ->  docker build -t integrate-api backend-api
# SEC-DIR-004: la imagen no contiene secretos; todo llega por variables de entorno
# al arrancar (Koyeb Secrets) y el fail-fast de application.yml aborta si falta alguno.
# =============================================================================

# --- Etapa 1: compilación (imagen oficial de Maven con JDK 21) ---------------
# No se usa ./mvnw: en Linux el wrapper descarga el .tar.gz y su distributionSha256Sum es la del
# .zip, así que la validación falla siempre. La versión de Maven no afecta al resultado del build.
FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /workspace

# Dependencias primero: la capa se reutiliza mientras pom.xml no cambie.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

# Las pruebas corren en la suite de calidad (ADR-0019), no en la imagen.
COPY src/ src/
RUN mvn -B -q -DskipTests package && cp target/*.jar app.jar

# --- Etapa 2: ejecución (solo JRE, usuario sin privilegios) ------------------
FROM eclipse-temurin:21-jre-noble
WORKDIR /app

RUN groupadd --system --gid 10001 integrate \
    && useradd --system --uid 10001 --gid integrate --no-create-home integrate
COPY --from=build --chown=integrate:integrate /workspace/app.jar app.jar
USER integrate

# Instancia Free de Koyeb: 512 MB. Sobrescribible desde el servicio.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k" \
    SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
