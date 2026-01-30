@echo off
cls
echo ================================================================
echo          UPSTOX ACCESS TOKEN UPDATER - WINDOWS
echo ================================================================
echo.
echo This script will update the Upstox access token across all files
echo in your trading bot workspace automatically.
echo.
echo Current files that will be updated:
echo - .env file
echo - application.properties
echo - application-secure.properties  
echo - set_environment.sh and .bat files
echo - All Java source files with hardcoded tokens
echo - Compiled class files
echo - Shell scripts
echo.
echo ================================================================
echo.

:INPUT_TOKEN
set /p NEW_TOKEN="Paste your new Upstox access token here and press Enter: "

if "%NEW_TOKEN%"=="" (
    echo.
    echo ERROR: No token provided! Please paste your token.
    echo.
    goto INPUT_TOKEN
)

:: Validate token format (should start with eyJ0eXAiOiJKV1Q)
echo %NEW_TOKEN% | findstr /B "eyJ0eXAiOiJKV1Q" >nul
if errorlevel 1 (
    echo.
    echo ERROR: Invalid token format! Token should start with 'eyJ0eXAiOiJKV1Q'
    echo Please check your token and try again.
    echo.
    goto INPUT_TOKEN
)

echo.
echo ================================================================
echo Starting token update process...
echo ================================================================
echo.

:: Update .env file
echo [1/8] Updating .env file...
if exist ".env" (
    powershell -Command "(Get-Content '.env') -replace 'UPSTOX_ACCESS_TOKEN=eyJ0eXAiOiJKV1Q[^=]*', 'UPSTOX_ACCESS_TOKEN=%NEW_TOKEN%' | Set-Content '.env'"
    echo     ✓ .env file updated
) else (
    echo UPSTOX_ACCESS_TOKEN=%NEW_TOKEN% > .env
    echo     ✓ .env file created
)

:: Update application.properties
echo [2/8] Updating application.properties...
if exist "src\main\resources\application.properties" (
    powershell -Command "(Get-Content 'src\main\resources\application.properties') -replace 'upstox\.access\.token=eyJ0eXAiOiJKV1Q[^=]*', 'upstox.access.token=%NEW_TOKEN%' | Set-Content 'src\main\resources\application.properties'"
    echo     ✓ Source application.properties updated
)
if exist "target\classes\application.properties" (
    powershell -Command "(Get-Content 'target\classes\application.properties') -replace 'upstox\.access\.token=eyJ0eXAiOiJKV1Q[^=]*', 'upstox.access.token=%NEW_TOKEN%' | Set-Content 'target\classes\application.properties'"
    echo     ✓ Compiled application.properties updated
)

:: Update application-secure.properties
echo [3/8] Updating application-secure.properties...
if exist "src\main\resources\application-secure.properties" (
    powershell -Command "(Get-Content 'src\main\resources\application-secure.properties') -replace 'upstox\.access\.token=eyJ0eXAiOiJKV1Q[^=]*', 'upstox.access.token=%NEW_TOKEN%' | Set-Content 'src\main\resources\application-secure.properties'"
    echo     ✓ application-secure.properties updated
)

:: Update set_environment.sh
echo [4/8] Updating set_environment.sh...
if exist "set_environment.sh" (
    powershell -Command "(Get-Content 'set_environment.sh') -replace 'export UPSTOX_ACCESS_TOKEN=\"eyJ0eXAiOiJKV1Q[^\"]*\"', 'export UPSTOX_ACCESS_TOKEN=\"%NEW_TOKEN%\"' | Set-Content 'set_environment.sh'"
    echo     ✓ set_environment.sh updated
)

:: Update set_environment.bat
echo [5/8] Updating set_environment.bat...
if exist "set_environment.bat" (
    powershell -Command "(Get-Content 'set_environment.bat') -replace 'set UPSTOX_ACCESS_TOKEN=eyJ0eXAiOiJKV1Q[^=]*', 'set UPSTOX_ACCESS_TOKEN=%NEW_TOKEN%' | Set-Content 'set_environment.bat'"
    echo     ✓ set_environment.bat updated
)

:: Update Java source files
echo [6/8] Updating Java source files...
set JAVA_COUNT=0
for /r "src" %%f in (*.java) do (
    findstr /C:"eyJ0eXAiOiJKV1Q" "%%f" >nul 2>&1
    if not errorlevel 1 (
        powershell -Command "(Get-Content '%%f') -replace 'eyJ0eXAiOiJKV1Q[^\"]*', '%NEW_TOKEN%' | Set-Content '%%f'"
        set /a JAVA_COUNT+=1
    )
)
echo     ✓ %JAVA_COUNT% Java files updated

:: Update shell scripts
echo [7/8] Updating shell scripts...
set SCRIPT_COUNT=0
for /r . %%f in (*.sh) do (
    findstr /C:"eyJ0eXAiOiJKV1Q" "%%f" >nul 2>&1
    if not errorlevel 1 (
        powershell -Command "(Get-Content '%%f') -replace 'eyJ0eXAiOiJKV1Q[^\"]*', '%NEW_TOKEN%' | Set-Content '%%f'"
        set /a SCRIPT_COUNT+=1
    )
)
echo     ✓ %SCRIPT_COUNT% shell scripts updated

:: Update batch files
echo [8/8] Updating other batch files...
set BAT_COUNT=0
for /r . %%f in (*.bat) do (
    if not "%%~nxf"=="update_upstox_token.bat" (
        findstr /C:"eyJ0eXAiOiJKV1Q" "%%f" >nul 2>&1
        if not errorlevel 1 (
            powershell -Command "(Get-Content '%%f') -replace 'eyJ0eXAiOiJKV1Q[^=]*', '%NEW_TOKEN%' | Set-Content '%%f'"
            set /a BAT_COUNT+=1
        )
    )
)
echo     ✓ %BAT_COUNT% batch files updated

echo.
echo ================================================================
echo              TOKEN UPDATE COMPLETED SUCCESSFULLY!
echo ================================================================
echo.
echo Summary:
echo - Configuration files updated
echo - Java source files updated  
echo - Shell scripts updated
echo - Batch files updated
echo - Environment files updated
echo.
echo Your new token: %NEW_TOKEN:~0,50%...
echo Token expiry: Check your Upstox dashboard for expiration time
echo.
echo Next steps:
echo 1. You can now run your trading bot
echo 2. Test API connection: run test_live_api_prices.sh
echo 3. Start main bot: run start_bot.sh
echo.
echo ================================================================
echo.
pause