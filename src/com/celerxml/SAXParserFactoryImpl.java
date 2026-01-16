// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025 - 2026
package com.celerxml;

import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

public final class SAXParserFactoryImpl extends javax.xml.parsers.SAXParserFactory{

   private final InputFactoryImpl Code;

   public SAXParserFactoryImpl(){ Code = new InputFactoryImpl(); }

   public static final SAXParserFactoryImpl newInstance(){ return new SAXParserFactoryImpl(); }

   @Override
   public final javax.xml.parsers.SAXParser newSAXParser(){ return new SAXParserImpl(Code); }

   @Override
   public final boolean getFeature(String n) throws SAXNotRecognizedException{ return fix(n); }

   @Override
   public final void setFeature(String n, boolean enabled) throws SAXNotRecognizedException, SAXNotSupportedException{
      boolean ok = false;
      switch(Code(n)){
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
            throw new SAXNotRecognizedException(new StrB(22 + n.length()).a("Unrecognized feature ").a(n).toString());
      }
      if(!ok)
         throw new SAXNotSupportedException(new StrB(29 + n.length()).a("Unsupported setting ").a(n).a(enabled ? " to true" : " to false").toString());
   }

   @Override
   public final void setValidating(boolean value){
      if(value)
         throw new IllegalArgumentException("Validating mode not supported");
   }

   static final boolean fix(String name) throws SAXNotRecognizedException{
      switch(Code(name)){
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

   private static final int Code(String s){ return s.startsWith("http://xml.org/sax/features/") ? s.substring(28).hashCode() : 0; }
}
