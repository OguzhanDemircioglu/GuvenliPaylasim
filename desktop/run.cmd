@echo off
rem Masaustu uygulamayi gelistirme modunda baslatir. Sunucu once baslatilmis olmali (server\run-dev.cmd).
rem Farkli sunucu icin: set GPS_SERVER_URL=http://sunucu:8080 ve sonra run.cmd
rem Kurulu surumun varsayilani demo sunucusudur (Railway); gelistirmede verilmezse yerel sunucu kullanilir.
if not defined GPS_SERVER_URL set "GPS_SERVER_URL=http://localhost:8080"
rem Projeler\.tools\jdk (JDK 25) varsa o kullanilir; sistemdeki JAVA_HOME Java 17 olabilir.
rem IntelliJ'nin indirdigi JDK 25 (%USERPROFILE%\.jdks) de kullanilir; Projeler\.tools\jdk varsa o onceliklidir.
for /d %%d in ("%USERPROFILE%\.jdks\*25*") do if exist "%%d\bin\java.exe" set "JAVA_HOME=%%d"
if exist "%~dp0..\..\.tools\jdk\bin\java.exe" set "JAVA_HOME=%~dp0..\..\.tools\jdk"
set "MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT %MAVEN_OPTS%"
cd /d "%~dp0"
call "%~dp0mvnw.cmd" -B -q javafx:run
