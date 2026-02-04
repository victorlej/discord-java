@echo off
echo Checking javac version... > compile.log
javac -version >> compile.log 2>&1
echo. >> compile.log
echo Compiling... >> compile.log
javac -d bin -sourcepath src/main/java src/main/java/client/Main.java src/main/java/server/Server.java >> compile.log 2>&1
echo Done >> compile.log
type compile.log
