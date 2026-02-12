@echo off
echo === Checking Java ===
javac -version
echo ERRORLEVEL=%ERRORLEVEL%
echo.
echo === Compiling ===
javac -d bin -encoding UTF-8 -cp "lib\sqlite-jdbc.jar" -sourcepath src/main/java src/main/java/client/Main.java src/main/java/server/Server.java
echo ERRORLEVEL=%ERRORLEVEL%
echo.
echo === Done ===
