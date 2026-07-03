# ================================
# Stage 1 — Build
# ================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy pom.xml first — Docker caches this layer separately.
# If only source code changes (not dependencies), Maven doesn't
# re-download the internet on every build. Saves ~2 minutes.
COPY pom.xml .
RUN ./mvnw dependency:go-offline -q 2>/dev/null || true

COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw

# Now copy source — this layer rebuilds on every code change
COPY src ./src

# Build the fat JAR, skip tests (tests need DB running)
RUN ./mvnw clean package -DskipTests -q

# ================================
# Stage 2 — Runtime (JRE only)
# ================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Security best practice — never run as root inside a container
RUN addgroup -S app && adduser -S app -G app
USER app

# Copy only the built JAR from Stage 1 — not the entire Maven cache,
# not the source code, not the JDK. Keeps image small (~200MB vs ~600MB)
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]