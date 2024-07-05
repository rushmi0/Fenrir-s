# Stage 1: Build the application
FROM gradle:jdk21 AS build
WORKDIR /home/app
COPY . .
RUN gradle build

# Stage 2: Run the application
FROM amazoncorretto:21.0.3
WORKDIR /app
COPY --from=build /app/build/libs/fenrir-s-0.1-all-optimized.jar fenrir-s-0.1-all-optimized.jar
EXPOSE 6724
ENTRYPOINT ["java", "-jar", "fenrir-s-0.1-all-optimized.jar"]
