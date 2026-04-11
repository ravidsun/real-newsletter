# ---------------------------------------------------------------------------
# Dockerfile – Real Newsletter Spring Boot application
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY pom.xml .
COPY src src

# Download dependencies first (layer-cached) then build without running tests.
RUN ./mvnw -B dependency:go-offline -q 2>/dev/null || true
RUN apk add --no-cache maven && \
    mvn -B clean package -DskipTests -q

# ── Runtime image ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /workspace/target/real-newsletter-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

