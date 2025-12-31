// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025
package com.celerxml;

import java.util.Collections;
import java.util.NoSuchElementException;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

class ReaderImpl implements XMLStreamReader{

   private static final int TEXT = 1 << 4 | 1 << 12 | 1 << 6 | 1 << 9;
   private static final int TXT2 = 1 << 4 | 1 << 12 | 1 << 6 | 1 << 5 | 1 << 11 | 1 << 9;
   private static final int TXT3 = 1 << 4 | 1 << 12 | 1 << 6 | 1 << 5;

   final XmlScanner scan;
   private PN curN, rootN;
   int curTok, numAttr, curState;
   final boolean bTxt;

   ReaderImpl(XmlScanner scan){
      this.scan = scan;
      curTok = 7; // START_DOCUMENT
      bTxt = scan.impl.doTxt();
   }

   @Override
   public final String getCharacterEncodingScheme(){ return scan.impl.declEnc; }

   @Override
   public final String getEncoding(){ return scan.impl.enc; }

   @Override
   public String getVersion(){ return scan.impl.declVer; }

   @Override
   public final boolean isStandalone(){ return scan.impl.bStand == 1; }

   @Override
   public final boolean standaloneSet(){ return scan.impl.bStand != 0; }

   @Override
   public Object getProperty(String p){
      return "javax.xml.stream.entities".equals(p) || "javax.xml.stream.notations".equals(p) ? Collections.EMPTY_LIST : scan.impl.getProperty(p, false);
   }

   @Override
   public final int getAttributeCount(){ return numAttr; }

   @Override
   public final String getAttributeLocalName(int idx){
      if(idx >= numAttr || idx < 0)
         throw new IllegalArgumentException("Index out of bounds");
      return scan.names[idx].ln;
   }

   @Override
   public final QName getAttributeName(int idx){
      if(idx >= numAttr || idx < 0)
         throw new IllegalArgumentException("Index out of bounds");
      return scan.names[idx].qName();
   }

   @Override
   public final String getAttributeNamespace(int idx){
      if(idx >= numAttr || idx < 0)
         throw new IllegalArgumentException("Index out of bounds");
      String pattr = scan.names[idx].getNsUri();
      return pattr == null ? "" : pattr;
   }

   @Override
   public final String getAttributePrefix(int idx){
      if(idx >= numAttr || idx < 0)
          throw new IllegalArgumentException("Index out of bounds");
      String pattr = scan.names[idx].pfx;
      return pattr == null ? "" : pattr;
   }

   @Override
   public final String getAttributeType(int idx){
      if(idx >= numAttr)
         throw new IllegalArgumentException("Index out of bounds");
      return "CDATA";
   }

   @Override
   public final String getAttributeValue(int idx){
      if(idx >= numAttr || idx < 0)
         throw new IllegalArgumentException("Index out of bounds");
      return scan.getV(idx);
   }

   @Override
   public final String getAttributeValue(String nsURI, String name){
      if(scan.attrCount < 1)
         return null;
      return scan.getV(nsURI, name);
   }

   @Override
   public final String getElementText() throws XMLStreamException{
      int type, len;
      while((type = next()) == 5 || type == 3); // COMMENT | PROCESSING_INSTRUCTION
      if(type == 2) // END_ELEMENT
         return "";
      if((1 << type & TEXT) == 0)
         throw new IllegalStateException("Not text");
      String text = scan.getText(), cache = null;
      StrB sb = null;
      boolean acc = false;
      while((type = next()) != 2) // END_ELEMENT
         if((1 << type & TEXT) != 0){
            if(!acc){
               acc = true;
               if(text.length() > 0)
                  cache = text;
            }
            String ss = getText();
            if((len = ss.length()) > 0){
               if(cache != null){
                  sb = new StrB(cache.length() + len).a(cache);
                  cache = null;
               }
               if(sb != null)
                  sb.append(text);
               else
                  cache = text;
            }
         }else if(type != 5 && type != 3) // COMMENT | PROCESSING_INSTRUCTION
            throw new IllegalStateException("Not text");
      if(acc){
         text = "";
         if(cache != null)
            text = cache;
         else if(sb != null)
            text = sb.toString();
      }
      return text;
   }

   @Override
   public final int getEventType(){ return curTok == 12 && bTxt ? 4 : curTok; } // CDATA --> CHARACTERS

   @Override
   public final String getLocalName(){
      if(curTok <= 2 || curTok == 9) // END_ELEMENT | ENTITY_REFERENCE
         return curN.ln;
      throw new IllegalStateException("Not START_ELEMENT/END_ELEMENT/ENTITY_REFERENCE");
   }

   @Override
   public final QName getName(){
      if(curTok > 2) // END_ELEMENT
         throw new IllegalStateException("Not START_ELEMENT/END_ELEMENT");
      return scan.getQName();
   }

   @Override
   public final NamespaceContext getNamespaceContext(){ return scan; }

   @Override
   public final int getNamespaceCount(){
      if(curTok > 2) // END_ELEMENT
         throw new IllegalStateException("Not START_ELEMENT/END_ELEMENT");
      return scan.getNsCount();
   }

   @Override
   public final String getNamespacePrefix(int idx){
      if(curTok > 2) // END_ELEMENT
         throw new IllegalStateException("Not START_ELEMENT/END_ELEMENT");
      String pfx = scan.findCurrNsDecl(idx).bind.pfx;
      return pfx == null ? "" : pfx;
   }

   @Override
   public final String getNamespaceURI(){
      if(curTok > 2) // END_ELEMENT
         throw new IllegalStateException("Not START_ELEMENT/END_ELEMENT");
      String uri = scan.tokName.getNsUri();
      if(uri == null)
         uri = scan.defNs.uri;
      return uri == null ? "" : uri;
   }

   @Override
   public final String getNamespaceURI(int idx){
      if(curTok > 2) // END_ELEMENT
         throw new IllegalStateException("Not START_ELEMENT/END_ELEMENT");
      String uri = scan.findCurrNsDecl(idx).bind.uri;
      return uri == null ? "" : uri;
   }

   @Override
   public final String getNamespaceURI(String pfx){
      if(curTok > 2) // END_ELEMENT
         throw new IllegalStateException("Not START_ELEMENT/END_ELEMENT");
      return scan.getNamespaceURI(pfx);
   }

   @Override
   public final String getPIData(){
      if(curTok != 3) // PROCESSING_INSTRUCTION
         throw new IllegalStateException("Not PROCESSING_INSTRUCTION");
      try{
         return scan.getText();
      }catch(XMLStreamException ex){
         throw new RuntimeException(ex);
      }
   }

   @Override
   public final String getPITarget(){
      if(curTok != 3) // PROCESSING_INSTRUCTION
         throw new IllegalStateException("Not PROCESSING_INSTRUCTION");
      return curN.ln;
   }

   @Override
   public final String getPrefix(){
      if(curTok > 2) // END_ELEMENT
         throw new IllegalStateException("Not START_ELEMENT/END_ELEMENT");
      String pfx = curN.pfx;
      return pfx == null ? "" : pfx;
   }

   @Override
   public final String getText(){
      if((1 << curTok & TXT2) == 0)
         throw new IllegalStateException("Not text");
      try{
         return scan.getText();
      }catch(XMLStreamException ex){
         throw new RuntimeException(ex);
      }
   }

   @Override
   public final char[] getTextCharacters(){
      if((1 << curTok & TXT3) == 0)
         throw new IllegalStateException("Not text");
      try{
         return scan.getTextCharacters();
      }catch(XMLStreamException ex){
         throw new RuntimeException(ex);
      }
   }

   @Override
   public final int getTextCharacters(int srcStart, char[] target, int targetStart, int len){
      if((1 << curTok & TXT3) == 0)
         throw new IllegalStateException("Not text");
      try{
         return scan.getTextCharacters(srcStart, target, targetStart, len);
      }catch(XMLStreamException ex){
         throw new RuntimeException(ex);
      }
   }

   @Override
   public final int getTextLength(){
      if((1 << curTok & TXT3) == 0)
         throw new IllegalStateException("Not text");
      try{
         return scan.getTextLength();
      }catch(XMLStreamException ex){
         throw new RuntimeException(ex);
      }
   }

   @Override
   public final int getTextStart(){ return 0; }

   @Override
   public final boolean hasName(){ return curTok <= 2; } // END_ELEMENT

   @Override
   public final boolean hasNext(){ return curTok != 8; } // END_DOCUMENT

   @Override
   public final boolean hasText(){ return (1 << curTok & TXT2) != 0; }

   @Override
   public final boolean isAttributeSpecified(int idx){ return true; }

   @Override
   public final boolean isCharacters(){ return getEventType() == 4; } // CHARACTERS

   @Override
   public final boolean isEndElement(){ return curTok == 2; } // END_ELEMENT

   @Override
   public final boolean isStartElement(){ return curTok == 1; }

   @Override
   public final boolean isWhiteSpace(){
      if(curTok == 4 || curTok == 12) // CHARACTERS | CDATA
         try{
            return scan.isWS();
         }catch(XMLStreamException ex){
            throw new RuntimeException(ex);
         }
      return curTok == 6; // SPACE
   }
    
   @Override
   public final void require(int type, String nsUri, String localName) throws XMLStreamException{
      int curr = curTok;
      if(curr != type && curr == 12 && bTxt) // CDATA
         curr = 4; // CHARACTERS
      if(type != curr)
         throw new XMLStreamException("Unexpected type", scan.getCurLoc());
      if(localName != null){
         if(curr != 1 && curr != 2 && curr != 9) // END_ELEMENT | ENTITY_REFERENCE
            throw new XMLStreamException("Token not START_ELEMENT, END_ELEMENT or ENTITY_REFERENCE", scan.getCurLoc());
         if(!localName.equals(getLocalName()))
            throw new XMLStreamException("Unexpected local name", scan.getCurLoc());
      }
      if(nsUri != null){
         if(curr != 1 && curr != 2) // END_ELEMENT
            throw new XMLStreamException("Token not START_ELEMENT or END_ELEMENT", scan.getCurLoc());
         String uri = getNamespaceURI();
         if(nsUri.length() == 0){
            if(uri != null && uri.length() > 0)
               throw new XMLStreamException("Expected empty namespace", scan.getCurLoc());
         }else if(!nsUri.equals(uri))
            throw new XMLStreamException("Unexpected namespace", scan.getCurLoc());
      }
   }

   @Override
   public final int next() throws XMLStreamException{
      int type;
      if(curState == 1){
         if((type = scan.nxtFromTree()) == -1){
            curTok = 8; // END_DOCUMENT
            throw new XMLStreamException("Unexpected End-of-input", scan.getCurLoc());
         }
         curTok = type;
         if(type == 12){ // CDATA
            if(bTxt)
               return 4; // CHARACTERS
         }else{
            curN = scan.tokName;
            if(type == 2 && scan.depth == 0) // END_ELEMENT
               curState = 2;
            if(type == 1)
               numAttr = scan.attrCount;
         }
         return type;
      }
      if(curState == 0){
         if((type = scan.nxtFromProlog(true)) == 1){
            curState = 1;
            numAttr = scan.attrCount;
         }else if(type == 11){ // DTD
            if(rootN != null)
               throw new XMLStreamException("Duplicate DOCTYPE", scan.getCurLoc());
            rootN = scan.tokName;
         }
      }else if(curState == 2)
         type = scan.nxtFromProlog(false);
      else
         throw new NoSuchElementException();
      if(type < 0){
         close();
         return 8; // END_DOCUMENT
      }
      curN = scan.tokName;
      return curTok = type;
   }

   @Override
   public final int nextTag() throws XMLStreamException{
      int nxt;
      while(true)
         switch(nxt = next()){
            case 4:  // CHARACTERS
            case 12: // CDATA
               if(!isWhiteSpace())
                  throw new XMLStreamException("Non-whitespace", scan.getCurLoc());
            case 3:  // PROCESSING_INSTRUCTION
            case 5:  // COMMENT
            case 6:  // SPACE
               continue;
            case 1:  // START_ELEMENT
            case 2:  // END_ELEMENT
               return nxt;
            default:
               throw new XMLStreamException("Unexpected event", scan.getCurLoc());
         }
   }

   @Override
   public final void close() throws XMLStreamException{
      curState = 3;
      curTok = 8; // END_DOCUMENT
      scan.free();
   }

   @Override
   public final Location getLocation(){ return scan.getLocation(); }
}
