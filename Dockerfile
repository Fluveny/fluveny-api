FROM eclipse-temurin:25-jdk AS builder

WORKDIR /api

COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -ntp dependency:go-offline

COPY src ./src
# O JSON Schema entra no classpath do jar (pom.xml), para a publicação validar o pacote.
COPY content/schema ./content/schema
RUN ./mvnw -B -ntp package -DskipTests

FROM eclipse-temurin:25-jre AS production

LABEL org.opencontainers.image.source="https://github.com/Fluveny/fluveny-api"

RUN groupadd --system app && useradd --system --gid app --no-create-home app

WORKDIR /api

COPY --from=builder /api/target/*.jar app.jar
# Só para o carregador do perfil local (história 1.7): em homologação e produção o
# conteúdo chega pelo job "Publicar conteúdo".
COPY content/units ./content/units
COPY content/media.json ./content/media.json

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
