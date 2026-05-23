FROM gradle:8-jdk21 AS build
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY api/build.gradle api/
COPY impl/build.gradle impl/
RUN gradle dependencies --no-daemon -q
COPY api/src/ api/src/
COPY impl/src/ impl/src/
RUN gradle :impl:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/impl/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
