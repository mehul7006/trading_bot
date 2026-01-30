@echo off
echo 🤖 Starting Telegram Stock Bot...

REM Check if Java is installed
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Java is not installed. Please install Java 11 or higher.
    pause
    exit /b 1
)

echo ☕ Java is installed

REM Check if Maven is installed
mvn -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Maven is not installed. Please install Maven 3.6 or higher.
    pause
    exit /b 1
)

echo 📦 Maven is installed

REM Build the project
echo 🔨 Building the project...
mvn clean compile

if %errorlevel% neq 0 (
    echo ❌ Build failed. Please check the error messages above.
    pause
    exit /b 1
)

echo ✅ Build successful!

REM Run the bot
echo 🚀 Starting the bot...
mvn exec:java -Dexec.mainClass="com.stockbot.TelegramStockBot"

pause