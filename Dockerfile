# Use Maven image to build the project
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy project files
COPY pom.xml .
COPY src ./src

# Build the application (skip tests to speed up)
RUN mvn clean package -DskipTests

# Use a lightweight JRE image for running the app (Eclipse Temurin is recommended over OpenJDK)
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the built jar from the build stage
# The shade plugin creates the original jar and the shaded jar. Usually the shaded one replaces the original or has a suffix.
# By default, shade replaces the original artifact.
COPY --from=build /app/target/telegram-stock-bot-1.0.0.jar bot.jar

# Copy token file if it exists locally (optional, better to use ENV vars)
# COPY upstox_token.txt . 

# Expose port 8080 (standard for Render/Cloud Run)
EXPOSE 8080

# Run the application
# CloudLauncher is set as Main-Class in pom.xml, so -jar works directly
CMD ["java", "-jar", "bot.jar"]
