#!/bin/sh
# === CONFIG BEGIN ================================

# The JDK 6 or later installation path
JDK=/usr/java/jdk1.8.0_241

# The location of the XML processor jars
XML_JARS_PATH=lib

# Override the XML processor (optional)
J_XML_OVERRIDE="-Djavax.xml.parsers.SAXParserFactory=com.celerxml.SAXParserFactoryImpl -Djavax.xml.stream.XMLInputFactory=com.celerxml.InputFactoryImpl"

# === CONFIG END ==================================
cd "`dirname $0`/.."
JAVAC="$JDK/bin/javac"
if [ ! -x "$JAVAC" ]
then
  JAVAC="`eval ls -dt1 \"/opt/j*/bin/javac\" 2>/dev/null|head -1`"
  [ ! -x "$JAVAC" ] && JAVAC="`which javac`"
  [ ! -x "$JAVAC" ] && printf 'If a JDK v6 or later is installed, set the JDK environment variable to point to where the JDK is located.\n' && exit 1
fi
JAVA="${JAVAC%?}"
TOTAL_TESTS=0 TESTS_OK=0
CLSPTH=.
for j in $XML_JARS_PATH/*.jar
do
  [ -f "$j" ] || continue
  CLSPTH=$CLSPTH:../../$j
done
cd src/test
"$JAVAC" test_*.java
for f in test_*.class
do
  ff="${f%.*}"
  printf 'Testing %s ...\n' "$ff"
  let "TOTAL_TESTS++"
  "$JAVA" -classpath $CLSPTH $J_XML_OVERRIDE $ff || continue
  let "TESTS_OK++"
done
printf 'Tests passed: %d / %d\n' "$TESTS_OK" "$TOTAL_TESTS"
