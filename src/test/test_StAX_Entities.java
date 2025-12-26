// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025

import java.io.StringReader;
import java.io.ByteArrayInputStream;
import java.util.Iterator;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamConstants;

// Test the StAX parser including XML entities
public final class test_StAX_Entities{

   private static final StringBuilder sb = new StringBuilder();

   private static final String test_xml =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<test>\n" +
      "   <message>Text containing entities: &lt;&gt;&apos;&amp;&quot;</message>\n" +
      "</test>";

   private static final String expected_result =
      "<?xml version='1.0' encoding='UTF-8' standalone='no'?><:test>\n" +
      "   <:message>Text containing entities: <>'&\"</:message>\n" +
      "</:test>";

   public static final void main(String[] args) throws Exception{
      XMLStreamReader reader;
      boolean reader_ok = false, stream_ok = false;
      XMLInputFactory factory = XMLInputFactory.newInstance();

      // Test using a Reader source
      reader = factory.createXMLStreamReader(null, new StringReader(test_xml));
      while(reader.hasNext()){
         printEvent(reader);
         reader.next();
      }
      reader.close();
      if(sb.toString().equals(expected_result))
         reader_ok = true;
      else
         System.out.println("Reader test: NOK");

      // Test using an InputStream source
      sb.setLength(0);
      reader = factory.createXMLStreamReader(new ByteArrayInputStream(test_xml.getBytes()), null);
      while(reader.hasNext()){
         printEvent(reader);
         reader.next();
      }
      reader.close();
      if(sb.toString().equals(expected_result))
         stream_ok = true;
      else
         System.out.println("Stream test: NOK");

      if(!reader_ok || !stream_ok)
         System.exit(-1);
   }

   private static final void printEvent(XMLStreamReader reader){
      StringBuilder lsb = sb;
      switch(reader.getEventType()){
         case XMLStreamConstants.START_ELEMENT:
            lsb.append('<');
            printName(reader);
            printNamespaces(reader);
            printAttributes(reader);
            lsb.append('>');
            break;
         case XMLStreamConstants.END_ELEMENT:
            lsb.append("</");
            printName(reader);
            lsb.append('>');
            break;
         case XMLStreamConstants.SPACE:
         case XMLStreamConstants.CHARACTERS:
            lsb.append(reader.getTextCharacters(), reader.getTextStart(), reader.getTextLength());
            break;
         case XMLStreamConstants.PROCESSING_INSTRUCTION:
            lsb.append("<?");
            if(reader.hasText())
               lsb.append(reader.getText());
            lsb.append("?>");
            break;
         case XMLStreamConstants.CDATA:
            lsb.append("<![CDATA[").append(reader.getTextCharacters(), reader.getTextStart(), reader.getTextLength()).append("]]>");
            break;
         case XMLStreamConstants.COMMENT:
            lsb.append("<!--");
            if(reader.hasText())
               lsb.append(reader.getText());
            lsb.append("-->");
            break;
         case XMLStreamConstants.ENTITY_REFERENCE:
            lsb.append(reader.getLocalName()).append('=');
            if(reader.hasText())
               lsb.append('[').append(reader.getText()).append(']');
            break;
         case XMLStreamConstants.START_DOCUMENT:
            lsb.append("<?xml version='").append(reader.getVersion()).append("' encoding='").append(reader.getCharacterEncodingScheme()).append(
               "' standalone='").append(reader.isStandalone() ? "yes'?>" : "no'?>");
            break;
      }
   }

   private static final void printName(XMLStreamReader reader){
      if(reader.hasName())
         printName(reader.getPrefix(), reader.getNamespaceURI(), reader.getLocalName());
   }

   private static final void printName(String prefix, String uri, String localName){
      StringBuilder lsb = sb;
      if(uri != null && !"".equals(uri))
         lsb.append("['").append(uri).append("']:");
      if(prefix != null)
         lsb.append(prefix).append(':');
      if(localName != null)
         lsb.append(localName);
   }

   private static final void printAttributes(XMLStreamReader reader){
      for(int i = 0, c = reader.getAttributeCount(); i < c; i++)
         printAttribute(reader, i);
   }

   private static final void printAttribute(XMLStreamReader reader, int idx){
      StringBuilder lsb = sb;
      lsb.append(' ');
      printName(reader.getAttributePrefix(idx), reader.getAttributeNamespace(idx), reader.getAttributeLocalName(idx));
      lsb.append("='").append(reader.getAttributeValue(idx)).append('\'');
   }

   private static final void printNamespaces(XMLStreamReader reader){
      for(int i = 0, c = reader.getNamespaceCount(); i < c; i++)
         printNamespace(reader, i);
   }

   private static final void printNamespace(XMLStreamReader reader, int idx){
      String prefix = reader.getNamespacePrefix(idx);
      String uri = reader.getNamespaceURI(idx);
      StringBuilder lsb = sb;
      if(prefix == null)
         lsb.append(" xmlns='").append(uri).append('\'');
      else
         lsb.append(" xmlns:").append(prefix).append("='").append(uri).append('\'');
   }
}
