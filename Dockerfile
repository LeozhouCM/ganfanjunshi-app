FROM gradle:8.11-jdk17 AS build
WORKDIR /app
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY server ./server
RUN gradle :server:installDist --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/server/build/install/server /app
EXPOSE 8081
CMD ["/app/bin/server"]
