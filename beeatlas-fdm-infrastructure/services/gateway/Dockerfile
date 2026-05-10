# Многоступенчатая сборка
# Этап 1: Сборка приложения
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

RUN apt-get update && \
    apt-get install -y maven

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN useradd -m -d /app -s /bin/bash -u 1000 appuser && \
    chown -R appuser:appuser /app

COPY --from=build --chown=appuser:appuser /app/target/fdm-gateway-*.jar app.jar
USER appuser
EXPOSE 8080 8090 10260
ENTRYPOINT ["java", "-jar", "app.jar"]

