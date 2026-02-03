# ==========================================
# 🐳 Dockerfile for Render / Heroku Deployment
# ==========================================

# 1️⃣ Build Stage
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy Maven Project Files
COPY pom.xml .
COPY src ./src

# Build the shaded jar (includes all dependencies)
# -DskipTests speeds up deployment
RUN mvn clean package -DskipTests

# 2️⃣ Runtime Stage (Lightweight Image)
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the built jar from build stage
# The shade plugin in pom.xml ensures CloudLauncher is the Main-Class
COPY --from=build /app/target/telegram-stock-bot-1.0.0.jar bot.jar

# 🌍 Environment Variables (Set these in Render Dashboard)
# ENV TELEGRAM_BOT_TOKEN=your_token_here
# ENV UPSTOX_ACCESS_TOKEN=your_token_here
# ENV PORT=8080

# Expose Port (Render sets $PORT automatically, we default to 8080)
EXPOSE 8080

# 🚀 Start Command
# Runs CloudLauncher which starts HTTP Server (for health check) + Telegram Bot
CMD ["java", "-jar", "bot.jar"]
