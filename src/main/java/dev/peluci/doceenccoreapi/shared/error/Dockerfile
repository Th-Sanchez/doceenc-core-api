# syntax=docker/dockerfile:1

# ---- build stage: compiles the jar and unpacks it for fast startup ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

# Resolve dependencies first so this layer is cached until the pom changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Tests run in CI (they need Docker for Testcontainers), not while building the image.
RUN ./mvnw -B -q -DskipTests package \
 && java -Djarmode=tools -jar target/*.jar extract --destination extracted

# ---- runtime stage ----
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN useradd --system --uid 1001 --no-create-home app
# Dependencies change rarely, the application jar on every build: separate layers.
COPY --from=build /build/extracted/lib/ lib/
COPY --from=build /build/extracted/app.jar app.jar
USER app

ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["java", \
  "-XX:+UseSerialGC", \
  "-XX:TieredStopAtLevel=1", \
  "-XX:MaxRAMPercentage=65", \
  "-Xss512k", \
  "-jar", "app.jar"]