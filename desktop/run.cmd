@echo off
rem Masaustu uygulamayi gelistirme modunda baslatir. Sunucu once baslatilmis olmali (server\run-dev.cmd).
rem Farkli sunucu icin: set GPS_SERVER_URL=http://sunucu:8080 ve sonra run.cmd
rem Projeler\.tools\jdk (JDK 25) varsa o kullanilir; sistemdeki JAVA_HOME Java 17 olabilir.
if exist "%~dp0..\..\.tools\jdk\bin\java.exe" set "JAVA_HOME=%~dp0..\..\.tools\jdk"
set "MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT %MAVEN_OPTS%"
cd /d "%~dp0"
call mvnw.cmd -B -q javafx:run
