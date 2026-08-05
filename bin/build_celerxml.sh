#!/bin/sh
# === CONFIG BEGIN ================================

# The JDK 9 or later installation path
JDK9=/Tools/jdk-16.0.2

# The JDK 6 or later installation path (optional)
JDK6=/Tools/jdk1.8.0_202

# Current CelerXML version
LIB_VER=1.0.3

# === CONFIG END ==================================
cd "`dirname $0`/.."

if [ "x$JDK9" = x ] || [ ! -x "$JDK9/bin/java" ]
then
  printf 'If a JDK v9 or later is installed, set the JDK9 environment variable to point to where the JDK is located.\n' && exit 1
fi
[ ! -x "$JDK6/bin/java" ] && JDK6="$JDK9"
[ ! -x "$JDK6/jre/lib/rt.jar" ] && printf '%s/jre/lib/rt.jar not found\n' "$JDK6" && exit 1
rm -rf classes 2>/dev/null
mkdir -p classes/celerxml/META-INF/services 2>/dev/null

# Include the services
printf 'com.celerxml.InputFactoryImpl\n' >classes/celerxml/META-INF/services/javax.xml.stream.XMLInputFactory
printf 'com.celerxml.SAXParserFactoryImpl\n' >classes/celerxml/META-INF/services/javax.xml.parsers.SAXParserFactory

# Compile the source code
"$JDK6/bin/javac" -source 6 -target 6 -classpath src -bootclasspath "$JDK6/jre/lib/rt.jar" -d classes/celerxml src/com/celerxml/SAXParserFactoryImpl.java || exit 1

# Create the module info for Java 9+
printf 'module celerxml{\n   requires transitive java.xml;\n   exports com.celerxml;\n   provides javax.xml.stream.XMLInputFactory with com.celerxml.InputFactoryImpl;\n   provides javax.xml.parsers.SAXParserFactory with com.celerxml.SAXParserFactoryImpl;\n}' >module-info.java
"$JDK9/bin/javac" --release 9 -d classes/celerxml -g:none module-info.java
rm -f module-info.java >/dev/null 2>&1

# Run the optimizer
"$JDK6/bin/javac" src/optimizer/bc*.java || exit 1
"$JDK6/bin/java" -classpath src/optimizer bcClipLinesRet classes/celerxml <src/optimizer/bcClipLinesRet.txt || exit 1

# Create the jar
"$JDK6/bin/jar" cMf lib/celerxml-${LIB_VER}.jar -C classes/celerxml .
