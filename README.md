# Overview

**CelerXML** is a lightweight implementation of the standard Java XML parsers: *Simple API for XML* (**SAX** and **SAX2**) and *Streaming API for XML* (**StAX**). It can be used as a drop-in replacement for other
XML processors, without recompiling the user Java application source code. The main advantage of CelerXML is performance. It was designed for maximum speed and efficiency. Another advantage is
compatibility with older Java versions. CelerXML can be used with Java 1.6 and later, while other XML processors typically require at least Java 8.  

CelerXML is free even for commercial use and redistribution of any kind, as long as all copyrights are preserved. The whole package is provided "AS IS". Check the [license](/LICENSE) file for additional information.  

# Installation

Just add `celerxml-1.0.0.jar` to the classpath. If the classpath contains other SAX or StAX processors, the following system properties can be used to select the current implementation:  

Configure the **SAX** processor to use the CelerXML implementation:  
```-Djavax.xml.parsers.SAXParserFactory=com.celerxml.SAXParserFactoryImpl```

Configure the **StAX** processor to use the CelerXML implementation:  
```-Djavax.xml.stream.XMLInputFactory=com.celerxml.InputFactoryImpl```

There are no dependencies. There is no need to recompile the user Java application to switch from the default SAX or StAX implementation to CelerXML.  
