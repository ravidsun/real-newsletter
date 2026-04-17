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
# libgcc is required by Netty's native QUIC/HTTP3 library (netty-codec-classes-quic)
# which is pulled in transitively by Spring AI. Without it the JVM throws
# UnsatisfiedLinkError: libgcc_s.so.1: No such file or directory at startup.
RUN apk add --no-cache libgcc
WORKDIR /app

COPY --from=builder /workspace/target/real-newsletter-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

