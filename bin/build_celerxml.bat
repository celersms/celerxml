@echo off
REM === CONFIG BEGIN ================================

REM The JDK 9 or later installation path
SET JDK9=\Tools\jdk-16.0.2

REM The JDK 6 or later installation path (optional)
SET JDK6=\Tools\jdk1.8.0_202

REM Current CelerXML version
SET LIB_VER=1.0.1

REM === CONFIG END ==================================
TITLE Rebuilding CelerXML...
PUSHD "%~dp0\.."
IF EXIST "%JDK9%\bin\javac.exe" GOTO JDK9FOUND
SET JDK9=%JDK_HOME%
IF EXIST "%JDK9%\bin\javac.exe" GOTO JDK9FOUND
SET JDK9=%JAVA_HOME%
IF EXIST "%JDK9%\bin\javac.exe" GOTO JDK9FOUND
FOR /f "tokens=2*" %%i IN ('reg query "HKEY_LOCAL_MACHINE\SOFTWARE\JavaSoft\Java Development Kit" /s 2^>nul ^| find "JavaHome"') DO SET JDK9=%%j
IF EXIST "%JDK9%\bin\javac.exe" GOTO JDK9FOUND
FOR /f "tokens=2*" %%i IN ('reg query "HKEY_LOCAL_MACHINE\SOFTWARE\Wow6432Node\JavaSoft\Java Development Kit" /s 2^>nul ^| find "JavaHome"') DO SET JDK9=%%j
IF EXIST "%JDK9%\bin\javac.exe" GOTO JDK9FOUND
ECHO If a JDK v9 or later is installed, set the JDK9 environment variable to point to where the JDK is located.
GOTO EXIT
:JDK9FOUND
IF EXIST "%JDK6%\bin\javac.exe" GOTO JDK6FOUND
SET JDK6=%JDK9%
:JDK6FOUND
IF EXIST "%JDK6%\jre\lib\rt.jar" GOTO RT6FOUND
ECHO %JDK6%\jre\lib\rt.jar not found
GOTO EXIT
:RT6FOUND
rd /s /q classes >nul 2>nul
mkdir classes\celerxml\META-INF\services 2>nul

REM Include the services
ECHO com.celerxml.InputFactoryImpl >classes\celerxml\META-INF\services\javax.xml.stream.XMLInputFactory
ECHO com.celerxml.SAXParserFactoryImpl >classes\celerxml\META-INF\services\javax.xml.parsers.SAXParserFactory

REM Compile the source code
"%JDK6%\bin\javac" -source 6 -target 6 -classpath src -bootclasspath "%JDK6%\jre\lib\rt.jar" -d classes\celerxml src\com\celerxml\SAXParserFactoryImpl.java
IF %ERRORLEVEL% NEQ 0 GOTO EXIT

REM Create the module info for Java 9+
(
ECHO module celerxml{
ECHO    requires transitive java.xml;
ECHO    exports com.celerxml;
ECHO    provides javax.xml.stream.XMLInputFactory with com.celerxml.InputFactoryImpl;
ECHO    provides javax.xml.parsers.SAXParserFactory with com.celerxml.SAXParserFactoryImpl;
ECHO }
) >module-info.java
"%JDK9%\bin\javac" --release 9 -d classes\celerxml -g:none module-info.java
del module-info.java /q >nul 2>nul

REM Run the optimizer
"%JDK6%\bin\javac" src\optimizer\bc*.java
IF %ERRORLEVEL% NEQ 0 GOTO EXIT
"%JDK6%\bin\java" -classpath src\optimizer bcClipLinesRet classes\celerxml <src\optimizer\bcClipLinesRet.txt
IF %ERRORLEVEL% NEQ 0 GOTO EXIT

REM Create the jar
"%JDK6%\bin\jar" cMf lib\celerxml-%LIB_VER%.jar -C classes\celerxml .

REM Create the bundle for Maven Central
SET MVN_BUNDLE=mvn\com\celersms\celerxml\%LIB_VER%
rd /s /q mvn >nul 2>nul
mkdir %MVN_BUNDLE%
copy /Y /B lib\celerxml-%LIB_VER%.jar %MVN_BUNDLE%\celerxml-%LIB_VER%.jar 2>nul
(
ECHO ^<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org"^>
ECHO  ^<modelVersion^>4.0.0^</modelVersion^>
ECHO  ^<groupId^>com.celersms^</groupId^>
ECHO  ^<artifactId^>celerxml^</artifactId^>
ECHO  ^<version^>%LIB_VER%^</version^>
ECHO  ^<packaging^>jar^</packaging^>
ECHO  ^<name^>CelerXML^</name^>
ECHO  ^<description^>Lightweight open-source Java library implementing the standard XML parsers: SAX, SAX2, StAX.^</description^>
ECHO  ^<url^>https://www.celersms.com/CelerXML.htm^</url^>
ECHO  ^<licenses^>
ECHO   ^<license^>
ECHO    ^<name^>BSD 3-Clause^</name^>
ECHO    ^<url^>https://github.com/celersms/celerxml/blob/main/LICENSE^</url^>
ECHO   ^</license^>
ECHO  ^</licenses^>
ECHO  ^<developers^>
ECHO   ^<developer^>
ECHO    ^<name^>Victor Celer^</name^>
ECHO    ^<email^>admin@celersms.com^</email^>
ECHO    ^<organization^>CelerSMS^</organization^>
ECHO    ^<organizationUrl^>https://www.celersms.com^</organizationUrl^>
ECHO   ^</developer^>
ECHO  ^</developers^>
ECHO  ^<scm^>
ECHO   ^<connection^>scm:git:https://github.com/celersms/celerxml.git^</connection^>
ECHO   ^<developerConnection^>scm:git:https://github.com/celersms/celerxml.git^</developerConnection^>
ECHO   ^<url^>https://github.com/celersms/celerxml^</url^>
ECHO  ^</scm^>
ECHO ^</project^>
) >%MVN_BUNDLE%\celerxml-%LIB_VER%.pom
"%JDK6%\bin\jar" cMf %MVN_BUNDLE%\celerxml-%LIB_VER%-sources.jar -C src com
"%JDK6%\bin\jar" cMf %MVN_BUNDLE%\celerxml-%LIB_VER%-javadoc.jar documentation.htm

REM Sign the files and package the Maven bundle
ECHO.
SET /P GPG_PWD=Enter GPG passphrase: 
FOR %%F IN (celerxml-%LIB_VER%.jar celerxml-%LIB_VER%.pom celerxml-%LIB_VER%-sources.jar celerxml-%LIB_VER%-javadoc.jar) DO CALL :SGN %%F
"%JDK6%\bin\jar" cMf lib\celerxml-%LIB_VER%-bundle.zip -C mvn .

:EXIT
rd /s /q classes mvn >nul
pause
POPD
@echo on
GOTO :EOF

:SGN
echo %GPG_PWD%|gpg --batch --pinentry-mode loopback --passphrase-fd 0 --yes --detach-sign --armor -o %MVN_BUNDLE%\%1.asc %MVN_BUNDLE%\%1
@certutil -hashfile %MVN_BUNDLE%\%1 MD5  | findstr /v ":" >%MVN_BUNDLE%\%1.md5
@certutil -hashfile %MVN_BUNDLE%\%1 SHA1 | findstr /v ":" >%MVN_BUNDLE%\%1.sha1
