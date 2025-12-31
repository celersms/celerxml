// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025
package com.celerxml;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

public final class SAXParserFactoryImpl extends SAXParserFactory{

   private final InputFactoryImpl factory;

   public SAXParserFactoryImpl(){ factory = new InputFactoryImpl(); }

   public static final SAXParserFactoryImpl newInstance(){ return new SAXParserFactoryImpl(); }

   @Override
   public final SAXParser newSAXParser(){ return new SAXParserImpl(factory); }

   @Override
   public final boolean getFeature(String name) throws SAXNotRecognizedException{ return fixdFeat(name); }

   @Override
   public final void setFeature(String name, boolean enabled) throws SAXNotRecognizedException, SAXNotSupportedException{
      boolean ok = false;
      switch(stdFeat(name)){
         case 0x8BF31EFE: // "xml-1.1"
         case 0xFD674879: // "validation"
         case 0xF8A166F2: // "namespace-prefixes"
         case 0xFB5D98C8: // "external-general-entities"
         case 0x34A80EA7: // "external-parameter-entities"
            ok = !enabled;
            break;
         case 0xF86B0798: // "xmlns-uris"
         case 0x493DBBE2: // "use-locator2"
         case 0x282C40C8: // "is-standalone"
         case 0xE95C4675: // "use-attributes2"
         case 0x52F273A1: // "resolve-dtd-uris"
         case 0xB9AF50D4: // "string-interning"
         case 0x19CC6B08: // "use-entity-resolver2"
         case 0x15685653: // "lexical-handler/parameter-entities"
            return;
         case 0x09C75678: // "namespaces"
            ok = enabled;
            break;
         default:
            throw new SAXNotRecognizedException(new StrB(22 + name.length()).a("Unrecognized feature ").a(name).toString());
      }
      if(!ok)
         throw new SAXNotSupportedException(new StrB(31 + name.length()).a("Not supported setting ").a(name).a(" to ").a(enabled ? "true" : "false").toString());
   }

   @Override
   public final void setValidating(boolean value){
      if(value)
         throw new IllegalArgumentException("Validating mode not implemented");
   }

   private static final int stdFeat(String s){ return s.startsWith("http://xml.org/sax/features/") ? s.substring(28).hashCode() : 0; }

   static final boolean fixdFeat(String name) throws SAXNotRecognizedException{
      switch(stdFeat(name)){
         case 0x8BF31EFE: // "xml-1.1"
         case 0xFD674879: // "validation"
         case 0x52F273A1: // "resolve-dtd-uris"
         case 0xF8A166F2: // "namespace-prefixes"
         case 0xFB5D98C8: // "external-general-entities"
         case 0x34A80EA7: // "external-parameter-entities"
         case 0x7F1D40FA: // "unicode-normalization-checking"
            return false;
         case 0x282C40C8: // "is-standalone"
         case 0x15685653: // "lexical-handler/parameter-entities"
         case 0x09C75678: // "namespaces"
         case 0xB9AF50D4: // "string-interning"
         case 0xE95C4675: // "use-attributes2"
         case 0x493DBBE2: // "use-locator2"
         case 0x19CC6B08: // "use-entity-resolver2"
         case 0xF86B0798: // "xmlns-uris"
            return true;
         default:
            throw new SAXNotRecognizedException(new StrB(22 + name.length()).a("Unrecognized feature ").a(name).toString());
      }
   }
}
