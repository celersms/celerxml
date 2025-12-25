@echo off
REM === CONFIG BEGIN ================================

REM The JDK 9 or later installation path
SET JDK9=\Tools\jdk-16.0.2

REM The JDK 6 or later installation path (optional)
SET JDK6=\Tools\jdk1.7

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
ECHO com.celerxml.InputFactoryImpl >classes\celerxml\META-INF\services\javax.xml.stream.XMLInputFactory
ECHO com.celerxml.SAXParserFactoryImpl >classes\celerxml\META-INF\services\javax.xml.parsers.SAXParserFactory
"%JDK6%\bin\javac" -source 6 -target 6 -classpath src -bootclasspath "%JDK6%\jre\lib\rt.jar" -d classes\celerxml src\com\celerxml\SAXParserFactoryImpl.java
IF %ERRORLEVEL% NEQ 0 GOTO EXIT
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
"%JDK6%\bin\jar" cMf lib\celerxml-1.0.0.jar -C classes\celerxml .
:EXIT
rd /s /q classes >nul
pause
POPD
@echo on
