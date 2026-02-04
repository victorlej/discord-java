@echo off
setlocal

echo [1/4] Checking environment...
if not exist "lib" mkdir "lib"
if not exist "bin" mkdir "bin"

echo [2/4] Downloading dependencies (if missing)...
if not exist "lib\sqlite-jdbc.jar" (
    echo Downloading sqlite-jdbc-3.42.0.0.jar...
    powershell -Command "Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.42.0.0/sqlite-jdbc-3.42.0.0.jar' -OutFile 'lib\sqlite-jdbc.jar'"
)

echo [3/4] Compiling...
javac -d bin -encoding UTF-8 -cp "lib\sqlite-jdbc.jar" -sourcepath src/main/java src/main/java/client/Main.java src/main/java/server/Server.java
if %ERRORLEVEL% NEQ 0 (
    echo Compilation failed!
    pause
    exit /b %ERRORLEVEL%
)

echo [4/4] Launching...
echo Starting Server (Background)...
start "Discord Server" java -cp "bin;lib\sqlite-jdbc.jar" server.Server

echo Starting Client...
start "Discord Client" java -cp "bin;lib\sqlite-jdbc.jar" client.Main

echo Done. You can close this window if strict necessary, but the Server runs in a separate window.
