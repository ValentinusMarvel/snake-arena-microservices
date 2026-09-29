# ==============================================================================
# Stage 1: Build stage with Eclipse Temurin JDK 21
# ==============================================================================
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

# Copy Maven wrapper and POM for dependency layer caching
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x ./mvnw
RUN ./mvnw dependency:go-offline -B || true

# Copy source code and static assets, build production artifact
COPY src src
RUN ./mvnw clean package -DskipTests

# ==============================================================================
# Stage 2: Minimal hardened runtime with Eclipse Temurin JRE 21
# ==============================================================================
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Create dedicated non-root application user
RUN groupadd -g 1001 appgroup && \
    useradd -u 1001 -g appgroup -s /bin/sh appuser && \
    chown -R appuser:appgroup /app

# Copy executable jar from builder stage
COPY --from=builder --chown=appuser:appgroup /workspace/target/*.jar app.jar

USER 1001

EXPOSE 8082

# Liveness probe via Spring Boot Actuator
HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=3 \
  CMD curl -f http://localhost:8082/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
