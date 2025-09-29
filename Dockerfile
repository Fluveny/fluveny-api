FROM openjdk:21-jdk-slim AS builder

WORKDIR /api

COPY .mvn/ .mvn
COPY mvnw pom.xml ./

RUN ./mvnw dependency:go-offline

COPY ./src ./src

RUN ./mvnw package -DskipTests

FROM azul/zulu-openjdk:21-jre-latest AS production

WORKDIR /api

COPY --from=builder /api/target/*.jar app.jar

EXPOSE 9099

ENTRYPOINT ["java", "-jar", "app.jar"]