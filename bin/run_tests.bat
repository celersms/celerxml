@echo off
VERIFY errors 2>nul
SETLOCAL ENABLEEXTENSIONS
IF ERRORLEVEL 1 GOTO :EOF
REM === CONFIG BEGIN ================================

REM The location of the XML processor jars
SET XML_JARS_PATH=lib

REM Override the XML processor (optional)
SET J_XML_OVERRIDE=-Djavax.xml.parsers.SAXParserFactory=com.celerxml.SAXParserFactoryImpl -Djavax.xml.stream.XMLInputFactory=com.celerxml.InputFactoryImpl

REM === CONFIG END ==================================
TITLE Testing the XML processor...
PUSHD "%~dp0\.."
IF EXIST "%XML_JARS_PATH%\*.jar" GOTO JARSFOUND
ECHO No jars found in %XML_JARS_PATH%. Using the default JDK implementation...
SET XML_CP=-classpath "."
SET J_XML_OVERRIDE=
GOTO GETJDK
:JARSFOUND
PUSHD "%XML_JARS_PATH%"
SET XML_JARS_PATH=%CD%
POPD
SET XML_CP=-classpath ".;%XML_JARS_PATH%\*"
:GETJDK
SET JDK=%JDK_HOME%
IF EXIST "%JDK%\bin\javac.exe" GOTO JDKFOUND
SET JDK=%JAVA_HOME%
IF EXIST "%JDK%\bin\javac.exe" GOTO JDKFOUND
FOR /f "tokens=2*" %%i IN ('reg query "HKEY_LOCAL_MACHINE\SOFTWARE\JavaSoft\Java Development Kit" /s 2^>nul ^| find "JavaHome"') DO SET JDK=%%j
IF EXIST "%JDK%\bin\javac.exe" GOTO JDKFOUND
FOR /f "tokens=2*" %%i IN ('reg query "HKEY_LOCAL_MACHINE\SOFTWARE\Wow6432Node\JavaSoft\Java Development Kit" /s 2^>nul ^| find "JavaHome"') DO SET JDK=%%j
IF EXIST "%JDK%\bin\javac.exe" GOTO JDKFOUND
ECHO If a JDK is installed, set the JDK_HOME environment variable to point to where the JDK is located.
GOTO EXIT
:JDKFOUND

SET /a TOTAL_TESTS=0
SET /a TESTS_OK=0
FOR /f "delims=" %%i IN ('dir /A-D /B /S src\test\test_*.java 2^>NUL ^| findstr /E /R ".*.java" ^|sort') DO CALL :TST "%%i"
ECHO Tests passed: %TESTS_OK% / %TOTAL_TESTS%
GOTO EXIT

:TST
 SET /a TOTAL_TESTS+=1
 ECHO Testing %~n1 ...
 "%JDK%\bin\javac" %1
 IF %ERRORLEVEL% NEQ 0 GOTO :EOF
 CD "%~dp1"
 "%JDK%\bin\java" %XML_CP% %J_XML_OVERRIDE% %~n1
 IF %ERRORLEVEL% NEQ 0 GOTO :EOF
 SET /a TESTS_OK+=1
 GOTO :EOF

:EXIT
pause
POPD
ENDLOCAL
@echo on
