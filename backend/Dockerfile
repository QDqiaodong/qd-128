ARG DOCKER_REGISTRY=docker.io

FROM ${DOCKER_REGISTRY}/maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

COPY pom.xml .
COPY settings.xml .
RUN mvn dependency:go-offline -B -s settings.xml

COPY src ./src
RUN mvn clean package -DskipTests

FROM ${DOCKER_REGISTRY}/eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /app/target/locker-management-1.0.0.jar .

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "locker-management-1.0.0.jar"]
