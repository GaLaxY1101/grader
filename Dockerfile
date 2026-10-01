# Grader backend (Spring Boot). Built and started by compose.server.yaml.

# ── Build ────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw \
    && ./mvnw -q -B dependency:go-offline

COPY src src
RUN ./mvnw -q -B package -DskipTests \
    && cp target/grader-*.jar app.jar

# ── Runtime ──────────────────────────────────────────────────
FROM eclipse-temurin:25-jre

# Docker CLI only (no daemon): the backend starts AI test sandboxes on the host
# Docker daemon through the mounted /var/run/docker.sock.
COPY --from=docker:29-cli /usr/local/bin/docker /usr/local/bin/docker

WORKDIR /app
COPY --from=build /build/app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
