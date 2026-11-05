@echo off
rem Windows kurulum dosyasi (MSI) uretir: dist\GuvenliPaylasim-<surum>.msi
rem Gerekenler: JDK 25 (jpackage) ve WiX 3.14 (Projeler\.tools\wix3 ya da PATH'te).
rem Kurulum kullanici basinadir: yonetici yetkisi istemez. Upgrade UUID sabittir; yeni surum eskisinin yerine gecer.
rem Urun adi Turkce karakterlidir; WiX icin Turkce kod sayfasi (1254) src\main\jpackage\MsiInstallerStrings_en.wxl'dedir.
rem Bu dosya UTF-8 kaydedilir; chcp 65001 Turkce karakterlerin dogru okunmasini saglar.
setlocal
chcp 65001 >nul
rem IntelliJ'nin indirdigi JDK 25 (%USERPROFILE%\.jdks) de kullanilir; Projeler\.tools\jdk varsa o onceliklidir.
for /d %%d in ("%USERPROFILE%\.jdks\*25*") do if exist "%%d\bin\java.exe" set "JAVA_HOME=%%d"
if exist "%~dp0..\..\.tools\jdk\bin\java.exe" set "JAVA_HOME=%~dp0..\..\.tools\jdk"
if exist "%~dp0..\..\.tools\wix3\candle.exe" set "PATH=%~dp0..\..\.tools\wix3;%PATH%"
set "MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT %MAVEN_OPTS%"
cd /d "%~dp0"

call "%~dp0mvnw.cmd" -B -q clean package -DskipTests || exit /b 1
for /f "usebackq delims=" %%v in (`call "%~dp0mvnw.cmd" -q help:evaluate -Dexpression^=project.version -DforceStdout`) do set "VERSION=%%v"
if "%VERSION%"=="" exit /b 1

copy /y "target\desktop-%VERSION%.jar" "target\libs\" >nul
if exist dist rmdir /s /q dist

rem jpackage'in varsayilan modul kumesi + jdk.crypto.mscapi. Launcher Windows-ROOT sertifika deposunu kullanir;
rem bu modul olmadan HTTPS istemcisi kurulamaz ve uygulama "Failed to launch JVM" ile acilmaz.
set "MODULES=java.base,java.compiler,java.datatransfer,java.xml,java.prefs,java.desktop,java.instrument,java.logging,java.management,java.security.sasl,java.naming,java.rmi,java.management.rmi,java.net.http,java.scripting,java.security.jgss,java.smartcardio,java.transaction.xa,java.sql,java.sql.rowset,java.xml.crypto,jdk.accessibility,jdk.internal.jvmstat,jdk.attach,jdk.internal.opt,jdk.zipfs,jdk.compiler,jdk.dynalink,jdk.httpserver,jdk.incubator.vector,jdk.internal.ed,jdk.internal.le,jdk.internal.md,jdk.jartool,jdk.javadoc,jdk.management,jdk.management.agent,jdk.jconsole,jdk.jdwp.agent,jdk.jdi,jdk.jfr,jdk.jshell,jdk.jsobject,jdk.management.jfr,jdk.net,jdk.nio.mapmode,jdk.sctp,jdk.security.auth,jdk.security.jgss,jdk.unsupported,jdk.unsupported.desktop,jdk.xml.dom,jdk.crypto.mscapi"

"%JAVA_HOME%\bin\jpackage" --type msi ^
  --name "Güvenli Paylaşım" ^
  --app-version %VERSION% ^
  --vendor "Bilgi İşlem" ^
  --description "Kurum içi uçtan uca şifreli belge paylaşımı" ^
  --input target\libs ^
  --main-jar desktop-%VERSION%.jar ^
  --main-class com.desktop.Launcher ^
  --add-modules %MODULES% ^
  --java-options "--enable-native-access=ALL-UNNAMED" ^
  --win-per-user-install ^
  --win-menu --win-menu-group "Güvenli Paylaşım" ^
  --win-shortcut ^
  --win-upgrade-uuid 6d1f3c2a-8b4e-4f7a-9c21-5e0b7a3d9f14 ^
  --icon src\main\jpackage\GuvenliPaylasim.ico ^
  --resource-dir src\main\jpackage ^
  --dest dist || exit /b 1

rem GitHub'a yuklenen dosya adi ASCII kalir (release.json bu adi kullanir).
move /y "dist\Güvenli Paylaşım-%VERSION%.msi" "dist\GuvenliPaylasim-%VERSION%.msi" >nul || exit /b 1
echo Kurulum dosyasi: %~dp0dist\GuvenliPaylasim-%VERSION%.msi
