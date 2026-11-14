FROM eclipse-temurin:25-jdk AS builder

WORKDIR /api

COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -ntp dependency:go-offline

COPY src ./src
RUN ./mvnw -B -ntp package -DskipTests

FROM eclipse-temurin:25-jre AS production

LABEL org.opencontainers.image.source="https://github.com/Fluveny/fluveny-api"

RUN groupadd --system app && useradd --system --gid app --no-create-home app

WORKDIR /api

COPY --from=builder /api/target/*.jar app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
