// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

// Benchmark SAX implementation
public final class bench_SAX extends DefaultHandler{

   private static boolean PRINT;
   private static final StringBuilder sb = new StringBuilder(512);

   public static final void main(String[] args) throws Exception{
      long t0, t1;
      int count = Integer.parseInt(args[1]);
      System.out.println(sb.append("Running SAX benchmark, iterations = ").append(count));
      if("TRUE".equalsIgnoreCase(args[2])){
         PRINT = true;
         System.out.println("Debug output enabled");
      }

      t0 = System.nanoTime();
      SAXParserFactory factory = SAXParserFactory.newInstance();
      t1 = System.nanoTime();
      System.out.println("Factory: " + factory.getClass().getName());
      System.out.printf("Classload time: %.2f us\n", (t1 - t0) / (float)1000);
      SAXParser saxParser = factory.newSAXParser();
      bench_SAX testHandler = new bench_SAX();

      t0 = System.nanoTime();
      for(int xx = 0; xx < count; xx++){
         sb.setLength(0);
         saxParser.parse(args[0], testHandler);
      }

      t1 = System.nanoTime();
      if(PRINT)
         System.out.println(sb);
      System.out.printf("XML parsing time: %.2f us\n", (t1 - t0) / (float)(count * 1000));
   }

   @Override
   public final void characters(char[] ch, int start, int length){
      sb.append("\ncharacters: ").append(ch, start, length);
   }

   @Override
   public final void startDocument(){
      sb.append("\nstartDocument");
   }

   @Override
   public void startElement(String uri, String lName, String qName, Attributes attr){
      sb.append("\nstartElement: uri=").append(uri).append(", localName=").append(lName).append(", qName=").append(qName);
   }

   @Override
   public final void endElement(String uri, String lName, String qName){
      sb.append("\nendElement: uri=").append(uri).append(", localName=").append(lName).append(", qName=").append(qName);
   }
}
