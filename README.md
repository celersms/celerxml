# Overview

**CelerXML** is a lightweight implementation of the [Java Streaming API for XML (StAX)](https://docs.oracle.com/javase/tutorial/jaxp/stax/why.html). It can be used as a drop-in replacement for other
StAX processors, without recompiling the user Java application source code. The main advantage of CelerXML is high performance. It was designed for maximum speed and efficiency. Another advantage is
compatibility with older Java versions. CelerXML can be used with Java 1.6 and later, while other StAX processors typically require at least Java 8.  

CelerXML is free even for commercial use and redistribution of any kind, as long as all copyrights are preserved. The whole package is provided "AS IS". Check the [license](/LICENSE) file for additional information.  

# Installation

Just add the CelerXML jar (`celerxml-X.Y.Z.jar`) to the classpath. If the classpath contains other StAX processors, the following system property can be used to select the current implementation:  

```-Djavax.xml.stream.XMLInputFactory=com.celerxml.InputFactoryImpl```

That's it. There are no dependencies. There is no need to recompile the user Java application to switch from the default StAX implementation to CelerXML.  
