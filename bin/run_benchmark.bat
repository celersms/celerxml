@echo off
REM === CONFIG BEGIN ================================

REM The location of the XML processor jars
SET XML_JARS_PATH=lib

REM Override the XML processor (optional)
SET J_XML_OVERRIDE=-Djavax.xml.parsers.SAXParserFactory=com.celerxml.SAXParserFactoryImpl -Djavax.xml.stream.XMLInputFactory=com.celerxml.InputFactoryImpl

REM XML file to use for this benchmark
SET XML_FILE=files\large_xml_file.xml

REM How many times to repeat the benchmark?
SET COUNT=5000

REM === CONFIG END ==================================
TITLE Benchmarking the XML processor...
PUSHD "%~dp0\.."
IF EXIST "%XML_FILE%" GOTO XMLFOUND
ECHO File %XML_FILE% not found.
GOTO EXIT
:XMLFOUND
IF EXIST "%XML_JARS_PATH%\*.jar" GOTO JARSFOUND
ECHO No jars found in %XML_JARS_PATH%. Using the default JDK implementation...
SET XML_CP=-classpath "src\bench"
SET J_XML_OVERRIDE=
GOTO GETJDK
:JARSFOUND
PUSHD "%XML_JARS_PATH%"
SET XML_JARS_PATH=%CD%
POPD
SET XML_CP=-classpath "src\bench;%XML_JARS_PATH%\*"
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
"%JDK%\bin\javac" src\bench\bench_*.java
IF %ERRORLEVEL% NEQ 0 GOTO EXIT
"%JDK%\bin\java" %XML_CP% %J_XML_OVERRIDE% bench_SAX %XML_FILE% %COUNT% FALSE
IF %ERRORLEVEL% NEQ 0 GOTO EXIT
"%JDK%\bin\java" %XML_CP% %J_XML_OVERRIDE% bench_StAX %XML_FILE% %COUNT% FALSE
:EXIT
pause
POPD
ENDLOCAL
@echo on
