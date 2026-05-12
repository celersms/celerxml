// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025 - 2026

import java.io.StringReader;
import java.io.ByteArrayInputStream;
import java.util.Iterator;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamConstants;

// Test the StAX parser using various byte order marks (BOM) and encodings
public final class test_StAX_BOM{

   private static final StringBuilder sb = new StringBuilder();
   private static XMLInputFactory factory = XMLInputFactory.newInstance();
   private static final String enc_pat = "${ENCODING}";

   private static final String test_xml =
      "<?xml version=\"1.0\" encoding=\"${ENCODING}\"?>\n" +
      "<books>\n" +
      "   <book url=\"https://www.celersms.com/doc/XM_file_format.pdf\" isbn=\"978-958-53602-0-4\">\n" +
      "      <title lang=\"EN\">The Unofficial XM File Format Specification</title>\n" +
      "      <author>Kame&#241;ar, Vladimir</author>\n" +
      "   </book>\n" +
      "   <book url=\"https://www.celersms.com/doc/Demoscene.pdf\" isbn=\"978-958-53602-1-1\">\n" +
      "      <title lang=\"ES\">Demoscene, el Arte Digital</title>\n" +
      "      <author>Kame&#241;ar, Vladimir</author>\n" +
      "   </book>\n" +
      "   <book url=\"https://www.celersms.com/doc/La_Historia_de_un_Byte.pdf\" isbn=\"978-958-53602-2-8\">\n" +
      "      <title lang=\"ES\">La Historia de un Byte</title>\n" +
      "      <author>Galuscenko, Dmitry</author>\n" +
      "   </book>\n" +
      "   <book url=\"https://www.celersms.com/doc/La_Pregunta.pdf\" isbn=\"978-958-53602-3-5\">\n" +
      "      <title lang=\"ES\">La Pregunta</title>\n" +
      "      <author>Galuscenko, Dmitry</author>\n" +
      "   </book>\n" +
      "</books>";

   private static final String expected_result =
      "<?xml version='1.0' encoding='${ENCODING}' standalone='no'?>" +
      "<:books>" +
      "\n   <:book :url='https://www.celersms.com/doc/XM_file_format.pdf' :isbn='978-958-53602-0-4'>" +
      "\n      <:title :lang='EN'>The Unofficial XM File Format Specification</:title>" +
      "\n      <:author>Kame\u00f1ar, Vladimir</:author>" +
      "\n   </:book>" +
      "\n   <:book :url='https://www.celersms.com/doc/Demoscene.pdf' :isbn='978-958-53602-1-1'>" +
      "\n      <:title :lang='ES'>Demoscene, el Arte Digital</:title>" +
      "\n      <:author>Kame\u00f1ar, Vladimir</:author>" +
      "\n   </:book>" +
      "\n   <:book :url='https://www.celersms.com/doc/La_Historia_de_un_Byte.pdf' :isbn='978-958-53602-2-8'>" +
      "\n      <:title :lang='ES'>La Historia de un Byte</:title>" +
      "\n      <:author>Galuscenko, Dmitry</:author>" +
      "\n   </:book>" +
      "\n   <:book :url='https://www.celersms.com/doc/La_Pregunta.pdf' :isbn='978-958-53602-3-5'>" +
      "\n      <:title :lang='ES'>La Pregunta</:title>" +
      "\n      <:author>Galuscenko, Dmitry</:author>" +
      "\n   </:book>" +
      "\n</:books>";

   public static final void main(String[] args) throws Exception{
      String enc;
      boolean nok = false;
      byte[] buf, bxml;
      int l;

      // BOM FFFE0000, UTF-32
      enc = "UTF-32"; // 4-byte, little endian
      bxml = test_xml.replace(enc_pat, enc).getBytes();
      l = bxml.length;
      buf = new byte[l * 4 + 4];
      buf[0] = (byte)0xFF;
      buf[1] = (byte)0xFE;
      for(int i = 0, j = 4; i < l; i++, j += 4)
         buf[j] = bxml[i];
      if(!doTest(buf, enc)){
         nok = true;
         System.out.println("BOM FFFE0000, UTF-32: NOK");
      }

      // BOM FFFE0000, UTF-32LE
      enc = "UTF-32LE"; // 4-byte, little endian
      bxml = test_xml.replace(enc_pat, enc).getBytes();
      l = bxml.length;
      buf = new byte[l * 4 + 4];
      buf[0] = (byte)0xFF;
      buf[1] = (byte)0xFE;
      for(int i = 0, j = 4; i < l; i++, j += 4)
         buf[j] = bxml[i];
      if(!doTest(buf, enc)){
         nok = true;
         System.out.println("BOM FFFE0000, UTF-32LE: NOK");
      }

      // BOM 0000FEFF, UTF-32BE
      enc = "UTF-32BE"; // 4-byte, big endian
      bxml = test_xml.replace(enc_pat, enc).getBytes();
      l = bxml.length;
      buf = new byte[l * 4 + 4];
      buf[2] = (byte)0xFE;
      buf[3] = (byte)0xFF;
      for(int i = 0, j = 4; i < l; i++, j += 4)
         buf[j + 3] = bxml[i];
      if(!doTest(buf, enc)){
         nok = true;
         System.out.println("BOM 0000FEFF, UTF-32BE: NOK");
      }

      // BOM FFFE, UTF-16
      enc = "UTF-16"; // 2-byte, little endian
      bxml = test_xml.replace(enc_pat, enc).getBytes();
      l = bxml.length;
      buf = new byte[l * 2 + 2];
      buf[0] = (byte)0xFF;
      buf[1] = (byte)0xFE;
      for(int i = 0, j = 2; i < l; i++, j += 2)
         buf[j] = bxml[i];
      if(!doTest(buf, enc)){
         nok = true;
         System.out.println("BOM FFFE, UTF-16: NOK");
      }

      // BOM FFFE, UTF-16LE
      enc = "UTF-16LE"; // 2-byte, little endian
      bxml = test_xml.replace(enc_pat, enc).getBytes();
      l = bxml.length;
      buf = new byte[l * 2 + 2];
      buf[0] = (byte)0xFF;
      buf[1] = (byte)0xFE;
      for(int i = 0, j = 2; i < l; i++, j += 2)
         buf[j] = bxml[i];
      if(!doTest(buf, enc)){
         nok = true;
         System.out.println("BOM FFFE, UTF-16LE: NOK");
      }

      // BOM FEFF, UTF-16BE
      enc = "UTF-16BE"; // 2-byte, big endian
      bxml = test_xml.replace(enc_pat, enc).getBytes();
      l = bxml.length;
      buf = new byte[l * 2 + 2];
      buf[0] = (byte)0xFE;
      buf[1] = (byte)0xFF;
      for(int i = 0, j = 2; i < l; i++, j += 2)
         buf[j + 1] = bxml[i];
      if(!doTest(buf, enc)){
         nok = true;
         System.out.println("BOM FFFE, UTF-16BE: NOK");
      }

      // BOM EFBBBF, UTF-8
      enc = "UTF-8"; // 1-byte
      bxml = test_xml.replace(enc_pat, enc).getBytes();
      l = bxml.length;
      buf = new byte[l + 3];
      buf[0] = (byte)0xEF;
      buf[1] = (byte)0xBB;
      buf[2] = (byte)0xBF;
      for(int i = 0, j = 3; i < l;)
         buf[j++] = bxml[i++];
      if(!doTest(buf, enc)){
         nok = true;
         System.out.println("BOM EFBBBF, UTF-8: NOK");
      }

      // No BOM, UTF-32
      enc = "UTF-32"; // 4-byte, little endian
      bxml = test_xml.replace(enc_pat, enc).getBytes();
      l = bxml.length;
      buf = new byte[l * 4];
      for(int i = 0, j = 0; i < l; i++, j += 4)
         buf[j] = bxml[i];
      if(!doTest(buf, enc)){
         nok = true;
         System.out.println("No BOM, UTF-32: NOK");
      }

      // No BOM, UTF-32LE
      enc = "UTF-32LE"; // 4-byte, little endian
      bxml = test_xml.replace(enc_pat, enc).getBytes();
      l = bxml.length;
      buf = new byte[l * 4];
      for(int i = 0, j = 0; i < l; i++, j += 4)
         buf[j] = bxml[i];
      if(!doTest(buf, enc)){
         nok = true;
         System.out.println("No BOM, UTF-32LE: NOK");
      }

      // No BOM, UTF-32BE
      enc = "UTF-32BE"; // 4-byte, big endian
      bxml = test_xml.replace(enc_pat, enc).getBytes();
      l = bxml.length;
      buf = new byte[l * 4];
      for(int i = 0, j = 3; i < l; i++, j += 4)
         buf[j] = bxml[i];
      if(!doTest(buf, enc)){
         nok = true;
         System.out.println("No BOM, UTF-32BE: NOK");
      }

      // No BOM, UTF-16
      enc = "UTF-16"; // 2-byte, little endian
      bxml = test_xml.replace(enc_pat, enc).getBytes();
      l = bxml.length;
      buf = new byte[l * 2];
      for(int i = 0, j = 0; i < l; i++, j += 2)
         buf[j] = bxml[i];
      if(!doTest(buf, enc)){
         nok = true;
         System.out.println("No BOM, UTF-16: NOK");
      }

      // No BOM, UTF-16LE
      enc = "UTF-16LE"; // 2-byte, little endian
      bxml = test_xml.replace(enc_pat, enc).getBytes();
      l = bxml.length;
      buf = new byte[l * 2];
      for(int i = 0, j = 0; i < l; i++, j += 2)
         buf[j] = bxml[i];
      if(!doTest(buf, enc)){
         nok = true;
         System.out.println("No BOM, UTF-16LE: NOK");
      }

      // No BOM, UTF-16BE
      enc = "UTF-16BE"; // 2-byte, big endian
      bxml = test_xml.replace(enc_pat, enc).getBytes();
      l = bxml.length;
      buf = new byte[l * 2];
      for(int i = 0, j = 1; i < l; i++, j += 2)
         buf[j] = bxml[i];
      if(!doTest(buf, enc)){
         nok = true;
         System.out.println("No BOM, UTF-16BE: NOK");
      }

      if(nok)
         System.exit(-1);
   }

   private static final boolean doTest(byte[] buf, String enc){
      sb.setLength(0);
      try{
         XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(buf), null);
         while(reader.hasNext()){
            printEvent(reader);
            reader.next();
         }
         reader.close();
      }catch(Exception ex){
         ex.printStackTrace();
         return false;
      }
      return sb.toString().equals(expected_result.replace(enc_pat, enc));
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
