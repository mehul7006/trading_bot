# ── Stage 1: Build ──────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy Maven descriptor first (layer cache)
COPY pom.xml .

# Copy web-app source (node_modules excluded via .dockerignore)
COPY web-app ./web-app

# Copy Java source
COPY src ./src

# Build everything: frontend-maven-plugin downloads Node, builds React,
# then Maven compiles Java and shades all into one fat JAR.
RUN mvn clean package -DskipTests

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/target/telegram-stock-bot-1.0.0.jar bot.jar

EXPOSE 8080

CMD ["java", "-jar", "bot.jar"]
