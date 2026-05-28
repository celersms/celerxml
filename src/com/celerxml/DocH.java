// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025 - 2026
package com.celerxml;

import org.xml.sax.Locator;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.ContentHandler;

@SuppressWarnings("deprecation")
final class DocH implements ContentHandler, org.xml.sax.AttributeList{

   private final org.xml.sax.DocumentHandler Code;
   private Attributes attrs;

   DocH(org.xml.sax.DocumentHandler docH){ Code = docH; }

   public final void characters(char[] ch, int start, int len) throws SAXException{ Code.characters(ch, start, len); }
   public final void startDocument() throws SAXException{ Code.startDocument(); }
   public final void endDocument() throws SAXException{ Code.endDocument(); }
   public final void endElement(String uri, String lName, String qName) throws SAXException{ Code.endElement(qName == null ? lName : qName); }
   public final void ignorableWhitespace(char[] ch, int start, int len) throws SAXException{ Code.ignorableWhitespace(ch, start, len); }
   public final void processingInstruction(String tgt, String data) throws SAXException{ Code.processingInstruction(tgt, data); }
   public final void setDocumentLocator(Locator loc){ Code.setDocumentLocator(loc); }

   public final void startElement(String uri, String lName, String qName, Attributes attrs) throws SAXException{
      this.attrs = attrs;
      Code.startElement(qName == null ? lName : qName, this);
   }

   public final void skippedEntity(String n){ /* NOOP */ }
   public final void startPrefixMapping(String pfx, String uri){ /* NOOP */ }
   public final void endPrefixMapping(String pfx){ /* NOOP */ }
   public final int getLength(){ return attrs.getLength(); }

   public final String getName(int i){
      String n;
      return (n = attrs.getQName(i)) == null ? attrs.getLocalName(i) : n;
   }

   public final String getType(int i){ return attrs.getType(i); }
   public final String getType(String name){ return attrs.getType(name); }
   public final String getValue(int i){ return attrs.getValue(i); }
   public final String getValue(String name){ return attrs.getValue(name); }
}
