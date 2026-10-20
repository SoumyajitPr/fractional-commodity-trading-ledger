# ---- build stage: compile and package with Maven (no local Java/Maven needed) ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Download dependencies first so they are cached between builds.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# ---- runtime stage: small JRE image ----
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 1001 appuser
COPY --from=build /workspace/target/fractional-commodity-ledger.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
