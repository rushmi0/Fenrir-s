FROM gradle:jdk21 AS build
WORKDIR /app
COPY . .
#ENV DATABASE_URL jdbc:postgresql://relay-db:5432/nostr
RUN gradle build --no-daemon

FROM amazoncorretto:21.0.2-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar application.jar
EXPOSE 6724
ENTRYPOINT ["java", "-jar", "application.jar"]
