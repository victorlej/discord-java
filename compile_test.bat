@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-23
set PATH=%JAVA_HOME%\bin;%PATH%

echo === Java version ===
javac -version

echo === Compiling ===
javac -d bin -encoding UTF-8 -cp "lib\sqlite-jdbc.jar" -sourcepath src/main/java src/main/java/client/Main.java src/main/java/server/Server.java 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo COMPILATION FAILED [exit=%ERRORLEVEL%]
) else (
    echo SUCCESS
)
