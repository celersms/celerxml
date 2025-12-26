// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025

import java.io.StringReader;
import java.io.ByteArrayInputStream;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

// Test the SAX parser using a simple XML
public final class test_SAX_Simple extends DefaultHandler{

   private static final StringBuilder sb = new StringBuilder();

   private static final String test_xml =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<websites>\n" +
      "   <website url=\"https://www.celersms.com/\" ssl=\"true\">\n" +
      "      <name>CelerSMS</name>\n" +
      "      <category>Tools, Articles</category>\n" +
      "      <since>2019</since>\n" +
      "   </website>\n" +
      "   <website url=\"https://ufmod.sourceforge.io/\" ssl=\"true\">\n" +
      "      <name>uFMOD</name>\n" +
      "      <category>Assembly, Open-Source</category>\n" +
      "      <since>2005</since>\n" +
      "   </website>\n" +
      "   <website url=\"https://implib.sourceforge.io/\" ssl=\"true\">\n" +
      "      <name>ImpLib SDK</name>\n" +
      "      <category>Assembly, Open-Source</category>\n" +
      "      <since>2006</since>\n" +
      "   </website>\n" +
      "</websites>";

   private static final String expected_result =
      "xml:<:websites:websites>\n" +
      "   <:website:website {:url=https://www.celersms.com/} {:ssl=true}>\n" +
      "      <:name:name>CelerSMS</:name:name>\n" +
      "      <:category:category>Tools, Articles</:category:category>\n" +
      "      <:since:since>2019</:since:since>\n" +
      "   </:website:website>\n" +
      "   <:website:website {:url=https://ufmod.sourceforge.io/} {:ssl=true}>\n" +
      "      <:name:name>uFMOD</:name:name>\n" +
      "      <:category:category>Assembly, Open-Source</:category:category>\n" +
      "      <:since:since>2005</:since:since>\n" +
      "   </:website:website>\n" +
      "   <:website:website {:url=https://implib.sourceforge.io/} {:ssl=true}>\n" +
      "      <:name:name>ImpLib SDK</:name:name>\n" +
      "      <:category:category>Assembly, Open-Source</:category:category>\n" +
      "      <:since:since>2006</:since:since>\n" +
      "   </:website:website>\n" +
      "</:websites:websites>";

   public static final void main(String[] args) throws Exception{
      boolean reader_ok = false, stream_ok = false;
      SAXParserFactory factory = SAXParserFactory.newInstance();
      SAXParser saxParser = factory.newSAXParser();
      test_SAX_Simple testHandler = new test_SAX_Simple();

      // Test using a Reader source
      saxParser.parse(new InputSource(new StringReader(test_xml)), testHandler);
      if(sb.toString().equals(expected_result))
         reader_ok = true;
      else
         System.out.println("Reader test: NOK");

      // Test using an InputStream source
      sb.setLength(0);
      saxParser.parse(new ByteArrayInputStream(test_xml.getBytes()), testHandler);
      if(sb.toString().equals(expected_result))
         stream_ok = true;
      else
         System.out.println("Stream test: NOK");

      if(!reader_ok || !stream_ok)
         System.exit(-1);
   }

   @Override
   public final void characters(char[] ch, int start, int length){
      sb.append(ch, start, length);
   }

   @Override
   public final void startDocument(){
      sb.append("xml:");
   }

   @Override
   public void startElement(String uri, String lName, String qName, Attributes attr){
      sb.append('<').append(uri).append(':').append(lName).append(':').append(qName);
      for(int i = 0, len = attr.getLength(); i < len; i++)
         sb.append(' ').append('{').append(attr.getURI(i)).append(':').append(attr.getQName(i)).append('=').append(attr.getValue(i)).append('}');
      sb.append('>');
   }

   @Override
   public final void endElement(String uri, String lName, String qName){
      sb.append('<').append('/').append(uri).append(':').append(lName).append(':').append(qName).append('>');
   }
}
