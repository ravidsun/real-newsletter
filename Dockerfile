# ---------------------------------------------------------------------------
# Dockerfile – Real Newsletter Spring Boot application
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY pom.xml .

# Install Maven and pre-download all dependencies using only pom.xml.
# This layer is cached as long as pom.xml doesn't change, so source edits
# don't trigger a full re-download.
RUN apk add --no-cache maven && \
    mvn -B dependency:go-offline -q

# Now copy source and build. Cache miss here only rebuilds the JAR, not deps.
COPY src src
RUN mvn -B clean package -DskipTests -q

# ── Runtime image ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
# libgcc is required by Netty's native QUIC/HTTP3 library (netty-codec-classes-quic)
# which is pulled in transitively by Spring AI. Without it the JVM throws
# UnsatisfiedLinkError: libgcc_s.so.1: No such file or directory at startup.
RUN apk add --no-cache libgcc
WORKDIR /app

COPY --from=builder /workspace/target/real-newsletter-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=dev"]

