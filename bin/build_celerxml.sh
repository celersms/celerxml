#!/bin/sh
# === CONFIG BEGIN ================================

# The JDK 9 or later installation path
JDK9=/usr/lib/jvm/java-11-openjdk

# The JDK 6 or later installation path (optional)
JDK6=/usr/java/jdk1.8.0_241

# Current CelerXML version
LIB_VER=1.0.3

# === CONFIG END ==================================
cd "`dirname $0`/.."

if [ "x$JDK9" = x ] || [ ! -x "$JDK9/bin/javac" ]
then
  printf 'If a JDK v9 or later is installed, set the JDK9 environment variable to point to where the JDK is located.\n' && exit 1
fi
[ ! -x "$JDK6/bin/javac" ] && JDK6="$JDK9"
[ ! -f "$JDK6/jre/lib/rt.jar" ] && printf '%s/jre/lib/rt.jar not found\n' "$JDK6" && exit 1
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

# Create the bundle for Maven Central
MVN_BUNDLE=mvn/com/celersms/celerxml/$LIB_VER
rm -rf mvn 2>/dev/null
mkdir -p $MVN_BUNDLE 2>/dev/null
cp lib/celerxml-${LIB_VER}.jar ${MVN_BUNDLE}/celerxml-${LIB_VER}.jar 2>/dev/null
cat <<EOF >${MVN_BUNDLE}/celerxml-${LIB_VER}.pom
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org">
 <modelVersion>4.0.0</modelVersion>
 <groupId>com.celersms</groupId>
 <artifactId>celerxml</artifactId>
 <version>${LIB_VER}</version>
 <packaging>jar</packaging>
 <name>CelerXML</name>
 <description>Lightweight open-source Java library implementing the standard XML parsers: SAX, SAX2, StAX.</description>
 <url>https://www.celersms.com/CelerXML.htm</url>
 <licenses>
  <license>
   <name>BSD 3-Clause</name>
   <url>https://github.com/celersms/celerxml/blob/main/LICENSE</url>
  </license>
 </licenses>
 <developers>
  <developer>
   <name>Victor Celer</name>
   <email>admin@celersms.com</email>
   <organization>CelerSMS</organization>
   <organizationUrl>https://www.celersms.com</organizationUrl>
  </developer>
 </developers>
 <scm>
  <connection>scm:git:https://github.com/celersms/celerxml.git</connection>
  <developerConnection>scm:git:https://github.com/celersms/celerxml.git</developerConnection>
  <url>https://github.com/celersms/celerxml</url>
 </scm>
</project>
EOF
"$JDK6/bin/jar" cMf ${MVN_BUNDLE}/celerxml-${LIB_VER}-sources.jar -C src com
"$JDK6/bin/jar" cMf ${MVN_BUNDLE}/celerxml-${LIB_VER}-javadoc.jar -C .github documentation.htm
