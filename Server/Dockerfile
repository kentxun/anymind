FROM maven:3.9.6-eclipse-temurin-8 AS build
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:8-jre
WORKDIR /app
COPY --from=build /app/target/*.jar /app/app.jar

ENV STORAGE_ROOT=/data
ENV SERVER_PORT=8080

EXPOSE 8080
VOLUME ["/data"]

ENTRYPOINT ["sh", "-c", "java -jar /app/app.jar --storage.root=${STORAGE_ROOT} --server.port=${SERVER_PORT}"]
