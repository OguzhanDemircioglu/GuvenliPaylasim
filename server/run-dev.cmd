@echo off
rem Sunucuyu dev profiliyle baslatir (H2, demo birimleri ve kullanicilari). http://localhost:8080
rem Projeler\.tools\jdk (JDK 25) varsa o kullanilir; sistemdeki JAVA_HOME Java 17 olabilir.
if exist "%~dp0..\..\.tools\jdk\bin\java.exe" set "JAVA_HOME=%~dp0..\..\.tools\jdk"
rem Kurum proxy'si HTTPS'i kendi sertifikasiyla imzaliyor; Maven bagimliliklari icin Windows sertifika deposu.
set "MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT %MAVEN_OPTS%"
cd /d "%~dp0"
call "%~dp0mvnw.cmd" -B spring-boot:run -Dspring-boot.run.profiles=dev
