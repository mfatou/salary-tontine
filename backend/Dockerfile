# ---------------------------------------------------------------------------
# Backend SalaryTontine - build multi-etapes
# Etape 1 : compilation Maven. Etape 2 : image d'execution minimale.
# ---------------------------------------------------------------------------

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
COPY src ./src

# Le cache BuildKit conserve le depot Maven entre deux builds sans l'embarquer
# dans une couche d'image. Les tests sont joues separement par la CI et par
# ./mvnw test : les rejouer ici imposerait un daemon Docker dans le build.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# L'application ne s'execute pas en root.
RUN addgroup -S salarytontine && adduser -S -G salarytontine salarytontine

COPY --from=build /build/target/*.jar app.jar
RUN chown -R salarytontine:salarytontine /app

USER salarytontine
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
