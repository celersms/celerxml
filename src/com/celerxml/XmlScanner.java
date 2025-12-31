// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025
package com.celerxml;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.ContentHandler;
import org.xml.sax.ext.LexicalHandler;

abstract class XmlScanner implements NamespaceContext{

   final InputFactoryImpl impl;
   boolean incomplete, pending, emptyTag;
   int depth, currNsCount, attrCount, currSize, attrs, currToken, currRow, rowOff, bOrC, iniRawOff, startRow, startCol;
   char[] nameBuf, currSeg;
   PN tokName;
   NsD lastNs;
   final NsB defNs;
   private NsB[] nsBind;
   private PN[] nsCache;
   private ArrayList segments;
   private int bindingCnt, bindMiss, segSize, resultLen, hashSize, spillEnd;
   private char[] arr, vals;
   private String result, attrVals;
   private boolean indent, doRst;
   private int[] attrMap, offsets;
   PN[] names;
   String errMsg, dtdSysId, dtdPubId;
   final boolean coalescing, lazy;

   static final String EOI = "Unexpected end-of-input", CDATA = "CDATA[";
   private static final String NULL = "Illegal null argument";
   private static final char[] arr0 = new char[0];
   private static final String sIndSpaces = "\n                                 ", sIndTabs = "\n\t\t\t\t\t\t\t\t\t";
   private static final char[] sIndSpacesArray = sIndSpaces.toCharArray(), sIndTabsArray = sIndTabs.toCharArray();
   private static final String[] sIndSpacesStrings = new String[34], sIndTabsStrings = new String[10];
   private static final Iterator s1Empty = new SIterator(null, true);

   XmlScanner(InputFactoryImpl impl){
      this.impl = impl;
      coalescing = impl.getF(2);
      lazy = impl.getF(256);
      nameBuf = impl.getCB1();
      defNs = new NsB(null);
      startRow = startCol = -1;
      currToken = 7; // START_DOCUMENT
      doRst = true;
   }

   final void free() throws XMLStreamException{
      freeBufs();
      if(impl.getF(8192))
         try{
            close();
         }catch(IOException ioe){
            throw new XMLStreamException(ioe);
         }
   }

   void freeBufs(){
      if(currSeg != null){
         result = null;
         arr = null;
         if(segments != null && segments.size() > 0){
            segments.clear();
            segSize = 0;
         }
         impl.setCB2(currSeg);
         currSeg = null;
      }
      if(nameBuf != null){
         impl.setCB1(nameBuf);
         nameBuf = null;
      }
   }

   final boolean skipTok() throws XMLStreamException{
      incomplete = false;
      switch(currToken){
         case 3:  // PROCESSING_INSTRUCTION
            skipPI();
            break;
         case 4:  // CHARACTERS
            if(skipChars() || (coalescing && skipCTxt())){
               currToken = 9; // ENTITY_REFERENCE
               return true;
            }
            break;
         case 5:  // COMMENT
            skipComm();
            break;
         case 6:  // SPACE
            skipSpace();
            break;
         case 12: // CDATA
            skipCData();
            if(coalescing){
               skipCTxt();
               if(pending){
                  currToken = 9; // ENTITY_REFERENCE
                  return true;
               }
            }
            break;
         case 11: // DTD
            skipDTD();
      }
      return false;
   }

   final Location getLocation(){ return new LocImpl(impl.pubId, impl.sysId, startCol, startRow, iniRawOff); }

   final QName getQName(){ return tokName.qName(defNs); }

   final String getText() throws XMLStreamException{
      if(incomplete)
         endTok();
      if(result == null)
         if(arr != null)
            result = new String(arr);
         else{
            int segLen = segSize, currLen = currSize;
            if(segLen == 0)
               return result = currLen == 0 ? "" : new String(currSeg, 0, currLen);
            StrB sb = new StrB(segLen + currLen);
            if(segments != null)
               for(int i = 0, len = segments.size(); i < len; ++i)
                  sb.append((char[])segments.get(i));
            result = sb.append(currSeg, 0, currLen).toString();
         }
      return result;
   }

   final int getTextLength() throws XMLStreamException{
      if(incomplete)
         endTok();
      int size = currSize;
      return size < 0 ? resultLen : size + segSize;
   }

   private final char[] buildR(){
      if(result != null)
         return result.toCharArray();
      else{
         int size = currSize;
         if((size = size < 0 ? resultLen : size + segSize) < 1)
            return arr0;
         int offset = 0;
         char[] res = new char[size];
         for(int i = 0, len = segments.size(); i < len; ++i){
            char[] curr = (char[])segments.get(i);
            System.arraycopy(curr, 0, res, offset, size = curr.length);
            offset += size;
         }
         System.arraycopy(currSeg, 0, res, offset, currSize);
         return res;
      }
   }

   final char[] getTextCharacters() throws XMLStreamException{
      if(incomplete)
         endTok();
      char[] res = arr;
      if(segments == null || segments.size() == 0)
         return res != null ? res : currSeg;
      if(res == null)
         arr = res = buildR();
      return res;
   }

   final int getTextCharacters(int srcStart, char[] dst, int dstStart, int len) throws XMLStreamException{
      if(incomplete)
         endTok();
      int amount, totalAmount = 0;
      if(segments != null)
         for(int i = 0, segc = segments.size(); i < segc; ++i){
            char[] segment = (char[])segments.get(i);
            int segLen = segment.length;
            if((amount = segLen - srcStart) <= 0){
               srcStart -= segLen;
               continue;
            }
            if(amount >= len)
               amount = len;
            System.arraycopy(segment, srcStart, dst, dstStart, amount);
            totalAmount += amount;
            if((len -= amount) == 0)
               return totalAmount;
            dstStart += amount;
            srcStart = 0;
         }
      if(len > 0){
         if((amount = currSize - srcStart) > len)
            amount = len;
         if(amount > 0){
            System.arraycopy(currSeg, srcStart, dst, dstStart, amount);
            totalAmount += amount;
         }
      }
      return totalAmount;
   }

   final boolean isWS() throws XMLStreamException{
      if(incomplete)
         endTok();
      if(indent)
         return true;
      char[] buf = currSeg;
      for(int i = 0, len = currSize; i < len; ++i)
         if(buf[i] > 0x20)
            return false;
      if(segments != null)
         for(int i = 0, len = segments.size(); i < len; ++i){
            buf = (char[])segments.get(i);
            for(int j = 0, len2 = buf.length; j < len2; ++j)
               if(buf[j] > 0x20)
                  return false;
         }
      return true;
   }

   final int getNsCount(){
      if(currToken == 1) // START_ELEMENT
         return currNsCount;
      return lastNs == null ? 0 : lastNs.countLvl(depth);
   }

   final NsD findCurrNsDecl(int idx){
      NsD nsDecl = lastNs;
      int level = depth, count = idx;
      if(currToken == 1){ // START_ELEMENT
         count = currNsCount - 1 - idx;
         --level;
      }
      while(nsDecl != null && nsDecl.lvl == level){
         if(count == 0)
            return nsDecl;
         --count;
         nsDecl = nsDecl.prevD;
      }
      throw new IndexOutOfBoundsException(new StrB(32).a("Wrong namespace index ").apos(idx).toString());
   }

   @Override
   public String getNamespaceURI(String pfx){
      if(pfx == null)
         throw new IllegalArgumentException(NULL);
      if(pfx.length() == 0){
         String uri = defNs.uri;
         if(uri == null)
            uri = "";
         return uri;
      }
      if(pfx.equals(XMLConstants.XML_NS_PREFIX))
         return XMLConstants.XML_NS_URI;
      if(pfx.equals(XMLConstants.XMLNS_ATTRIBUTE))
         return XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
      NsD nsDecl = lastNs;
      while(nsDecl != null){
         if(nsDecl.hasPfx(pfx))
            return nsDecl.bind.uri;
         nsDecl = nsDecl.prevD;
      }
      return null;
   }

   @Override
   public final String getPrefix(String nsURI){
      if(nsURI == null)
         throw new IllegalArgumentException(NULL);
      if(nsURI.equals(XMLConstants.XML_NS_URI))
         return XMLConstants.XML_NS_PREFIX;
      if(nsURI.equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI))
         return XMLConstants.XMLNS_ATTRIBUTE;
      if(nsURI.equals(defNs.uri))
         return "";
      String pfx;
loop_pfx:
      for(NsD nsDecl = lastNs; nsDecl != null; nsDecl = nsDecl.prevD)
         if(nsDecl.hasNsURI(nsURI) && (pfx = nsDecl.bind.pfx) != null){
            for(NsD decl2 = lastNs; decl2 != nsDecl; decl2 = decl2.prevD)
               if(decl2.hasPfx(pfx))
                  continue loop_pfx;
            return pfx;
         }
      return null;
   }

   @Override
   @SuppressWarnings("unchecked")
   public final Iterator<String> getPrefixes(String nsURI){
      if(nsURI == null)
         throw new IllegalArgumentException(NULL);
      if(nsURI.equals(XMLConstants.XML_NS_URI))
         return new SIterator(XMLConstants.XML_NS_PREFIX, false);
      if(nsURI.equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI))
         return new SIterator(XMLConstants.XMLNS_ATTRIBUTE, false);
      ArrayList<String> l = null;
      if(nsURI.equals(defNs.uri))
         (l = new ArrayList<String>()).add("");
      String pfx;
loop_pfx:
      for(NsD nsDecl = lastNs; nsDecl != null; nsDecl = nsDecl.prevD)
         if(nsDecl.hasNsURI(nsURI) && (pfx = nsDecl.bind.pfx) != null){
            for(NsD decl2 = lastNs; decl2 != nsDecl; decl2 = decl2.prevD)
               if(decl2.hasPfx(pfx))
                  continue loop_pfx;
            if(l == null)
               l = new ArrayList<String>();
            l.add(pfx);
         }
      if(l == null)
         return s1Empty;
      if(l.size() == 1)
         return new SIterator(l.get(0), false);
      return l.iterator();
   }

   final PN bindName(PN name, String pfx){
      if(nsCache != null){
         PN cn = nsCache[name.pfxdName.hashCode() & 0x3F];
         if(cn != null && cn.pfxdName == name.pfxdName)
            return cn;
      }
      for(int i = 0, len = bindingCnt; i < len; ++i){
         NsB b = nsBind[i];
         if(b.pfx != pfx)
            continue;
         if(i > 0) {
            nsBind[i] = nsBind[i - 1];
            nsBind[i - 1] = b;
         }
         PN bn = name.createBN(b);
         if(nsCache == null){
            if(++bindMiss < 10)
               return bn;
             nsCache = new PN[0x40];
         }
         nsCache[bn.pfxdName.hashCode() & 0x3F] = bn;
         return bn;
      }
      if(pfx == "xml")
         return name.createBN(NsB.XML_B);
      ++bindMiss;
      NsB b = new NsB(pfx);
      if(bindingCnt == 0)
         nsBind = new NsB[16];
      else if(bindingCnt >= nsBind.length)
         nsBind = xpand(nsBind);
      nsBind[bindingCnt++] = b;
      return name.createBN(b);
   }

   final void bindNs(PN name, String uri) throws XMLStreamException{
      NsB ns;
      String pfx = name.pfx;
      if(pfx == null)
         ns = defNs;
      else{
         pfx = name.ln;
findOrCreate:
         {
            for(int i = 0, len = bindingCnt; i < len; ++i)
               if((ns = nsBind[i]).pfx == pfx){
                  if(i > 0){
                     nsBind[i] = nsBind[i - 1];
                     nsBind[i - 1] = ns;
                  }
                  break findOrCreate;
               }
            if(pfx == "xml")
               ns = NsB.XML_B;
            else if(pfx == "xmlns")
               ns = NsB.XMLNS_B;
            else{
               ns = new NsB(pfx);
               if(bindingCnt == 0)
                  nsBind = new NsB[16];
               else if(bindingCnt >= nsBind.length)
                  nsBind = xpand(nsBind);
               nsBind[bindingCnt++] = ns;
            }
         }
         if(pfx != null && ns.isImmutbl() && (pfx != "xml" || !uri.equals(XMLConstants.XML_NS_URI)))
            throwInputErr(new StrB(20 + pfx.length()).a("Can't rebind prefix ").a(pfx).toString());
      }
      if(!ns.isImmutbl()){
         if(uri == XMLConstants.XML_NS_URI)
            throwInputErr("Can't bind 'http://www.w3.org/XML/1998/namespace' to prefix other than 'xml'");
         if(uri == XMLConstants.XMLNS_ATTRIBUTE_NS_URI)
            throwInputErr("Can't bind 'http://www.w3.org/2000/xmlns/' to prefix other than 'xmlns'");
      }
      if(lastNs != null && lastNs.declared(pfx, depth))
         throwInputErr(pfx == null ? "Duplicate default namespace" : new StrB(24 + pfx.length()).a("Duplicate decl., prefix ").a(pfx).toString());
      lastNs = new NsD(ns, uri, lastNs, depth);
   }

   final void assertMore() throws XMLStreamException{
      if(!more())
         throwInputErr(EOI);
   }

   final char[] reset(){
      result = null;
      arr = null;
      indent = false;
      if(segments != null && segments.size() > 0){
         segments.clear();
         segSize = 0;
      }
      currSize = 0;
      if(currSeg == null)
         currSeg = impl.getCB2();
      return currSeg;
   }

   final void doIndent(int indCharCount, char indChar){
      if(segments != null && segments.size() > 0){
         segments.clear();
         segSize = 0;
      }
      currSize = -1;
      indent = true;
      String text;
      int strlen = resultLen = indCharCount + 1;
      if(indChar == '\t'){
         arr = sIndTabsArray;
         if((text = sIndTabsStrings[indCharCount]) == null)
            sIndTabsStrings[indCharCount] = text = sIndTabs.substring(0, strlen);
      }else{
         arr = sIndSpacesArray;
         if((text = sIndSpacesStrings[indCharCount]) == null)
            sIndSpacesStrings[indCharCount] = text = sIndSpaces.substring(0, strlen);
      }
      result = text;
   }

   @SuppressWarnings("unchecked")
   final char[] endSeg(){
      if(segments == null)
         segments = new ArrayList();
      segments.add(currSeg);
      int oldLen = currSeg.length;
      segSize += oldLen;
      char[] curr = new char[Math.min(oldLen + (oldLen < 8000 ? oldLen : oldLen >> 1), 0x40000)];
      currSize = 0;
      return currSeg = curr;
   }

   final char[] startNewV(PN attrName, int currOffset){
      int count;
      if(doRst){
         doRst = false;
         attrs = count = 0;
         attrVals = null;
         if(vals == null){
            names = new PN[12];
            vals = new char[120];
            offsets = new int[12];
         }
      }else{
         if((count = attrs) >= offsets.length){
            final int[] oldVal = offsets;
            final PN[] oldNames = names;
            final int oldLen = oldVal.length;
            offsets = new int[oldLen + oldLen];
            names = new PN[oldLen + oldLen];
            for(int i = 0; i < oldLen; ++i){
               offsets[i] = oldVal[i];
               names[i] = oldNames[i];
            }
         }
         offsets[count - 1] = currOffset;
      }
      names[count] = attrName;
      ++attrs;
      return vals;
   }

   final int endLastV(int endingOffset){
      if(doRst)
         return 0;
      doRst = true;
      int count = attrs;
      offsets[count - 1] = endingOffset;
      PN[] names = this.names;
      if(count < 3){
         hashSize = 0;
         if(count == 2 && names[0].boundEq(names[1])){
            noteDupAttr(0, 1);
            return -1;
         }
         return count;
      }
      int[] map = attrMap;
      int min = count + (count >> 2), hashCount = (min + 7) & ~7, mask = hashCount - 1; // next multiple of 8 (never 0)
      hashSize = hashCount;
      min = hashCount + (hashCount >> 4);
      if(map == null || map.length < min)
         map = new int[min];
      else
         for(int i = 0; i < hashCount; ++i)
            map[i] = 0;
      for(int i = 0; i < count; ++i){
         PN newName = names[i];
         int hash = newName.ln.hashCode(), index = hash & mask, oldNameIdx = map[index];
         if(oldNameIdx == 0)
            map[index] = i + 1;
         else{
            if(names[--oldNameIdx].boundEq(newName) && errMsg == null)
               noteDupAttr(oldNameIdx, i);
            if(hashCount + 1 >= map.length)
               map = xpand(map, 8);
            // for(int j = hashCount; j < spillIndex; j += 2)
            //   if(map[j] == hash && names[oldNameIdx = map[j + 1]].boundEq(newName)){
            //      if(errMsg == null)
            //         noteDupAttr(oldNameIdx, i);
            //      break;
            //   }
            map[hashCount++] = hash;
            map[hashCount++] = i;
         }
      }
      spillEnd = hashCount;
      attrMap = map;
      return errMsg == null ? count : -1;
   }

   final char[] xpand(){ return vals = xpand(vals); }

   static final int[] xpand(int[] arr, int more){
      int[] old = arr;
      int len = arr.length;
      System.arraycopy(old, 0, arr = new int[len + more], 0, len);
      return arr;
   }

   static final char[] xpand(char[] arr){
      char[] old = arr;
      int len = arr.length;
      System.arraycopy(old, 0, arr = new char[len + len], 0, len);
      return arr;
   }

   static final NsB[] xpand(NsB[] arr){
      NsB[] old = arr;
      int len = arr.length;
      System.arraycopy(old, 0, arr = new NsB[len + len], 0, len);
      return arr;
   }

   final String getV(int idx){
      int xx, yy;
      if(attrVals == null)
         attrVals = (xx = offsets[attrs - 1]) == 0 ? "" : new String(vals, 0, xx);
      if(idx == 0)
         return attrs == 1 ? attrVals : (xx = offsets[0]) == 0 ? "" : attrVals.substring(0, xx);
      return (yy = offsets[idx - 1]) == (xx = offsets[idx]) ? "" : attrVals.substring(yy, xx);
   }

   final String getV(String nsUri, String name){
      int ix = findIdx(nsUri, name);
      return ix >= 0 ? getV(ix) : null;
   }

   final int findIdx(String nsUri, String name){
      int xx = hashSize;
      if(xx < 1){
         for(int i = 0, len = attrs; i < len; ++i)
            if(names[i].boundEq(nsUri, name))
               return i;
         return -1;
      }
      int hash = name.hashCode(), ix = attrMap[hash & (xx - 1)];
      if(ix > 0){
         if(names[--ix].boundEq(nsUri, name))
            return ix;
         for(int len = spillEnd; xx < len; xx += 2)
            if(attrMap[xx] == hash && names[ix = attrMap[xx + 1]].boundEq(nsUri, name))
               return ix;
      }
      return -1;
   }

   final void chrEvents(ContentHandler h) throws XMLStreamException, SAXException{
      if(h != null){
         if(incomplete)
            endTok();
         if(arr != null)
            h.characters(arr, 0, resultLen);
         else{
            if(segments != null)
               for(int i = 0, len = segments.size(); i < len; ++i){
                  char[] ch = (char[])segments.get(i);
                  h.characters(ch, 0, ch.length);
               }
            if(currSize > 0)
               h.characters(currSeg, 0, currSize);
         }
      }
   }

   final void spaceEvents(ContentHandler h) throws XMLStreamException, SAXException{
      if(h != null){
         if(incomplete)
            endTok();
         if(arr != null)
            h.ignorableWhitespace(arr, 0, resultLen);
         else{
            if(segments != null)
               for(int i = 0, len = segments.size(); i < len; ++i){
                  char[] ch = (char[])segments.get(i);
                  h.ignorableWhitespace(ch, 0, ch.length);
               }
            if(currSize > 0)
               h.ignorableWhitespace(currSeg, 0, currSize);
         }
      }
   }

   final void PIEvent(ContentHandler h) throws XMLStreamException, SAXException{
      if(h != null){
         if(incomplete)
            endTok();
         h.processingInstruction(tokName.ln, getText());
      }
   }

   final void startElement(ContentHandler h, Attributes attrs) throws SAXException{
      if(h != null){
         NsD nsDecl = lastNs;
         int level = depth - 1;
         while(nsDecl != null && nsDecl.lvl == level){
            String pfx = nsDecl.bind.pfx;
            h.startPrefixMapping(pfx == null ? "" : pfx, nsDecl.bind.uri);
            nsDecl = nsDecl.prevD;
         }
         PN n = tokName;
         String uri = n.getNsUri();
         h.startElement(uri == null ? "" : uri, n.ln, n.pfxdName, attrs);
      }
   }

   final void endElement(ContentHandler h) throws SAXException{
      if(h != null){
         PN n = tokName;
         String uri = n.getNsUri();
         h.endElement(uri == null ? "" : uri, n.ln, n.pfxdName);
         NsD nsDecl = lastNs;
         int level = depth;
         while(nsDecl != null && nsDecl.lvl == level){
            String pfx = nsDecl.bind.pfx;
            h.endPrefixMapping(pfx == null ? "" : pfx);
            nsDecl = nsDecl.prevD;
         }
      }
   }

   final void commentEvent(LexicalHandler h) throws XMLStreamException, SAXException{
      if(h != null){
         if(incomplete)
            endTok();
         if(arr != null)
            h.comment(arr, 0, resultLen);
         else if(segments != null && segments.size() > 0){
            char[] ch = arr;
            if(ch == null)
               arr = ch = buildR();
            h.comment(ch, 0, ch.length);
         }else
            h.comment(currSeg, 0, currSize);
      }
   }

   abstract void close() throws IOException;

   abstract int nxtFromProlog(boolean isProlog) throws XMLStreamException;

   abstract int nxtFromTree() throws XMLStreamException;

   abstract void endTok() throws XMLStreamException;

   abstract Location getCurLoc();

   abstract boolean skipChars() throws XMLStreamException;

   abstract void skipCData() throws XMLStreamException;

   abstract void skipComm() throws XMLStreamException;

   abstract void skipPI() throws XMLStreamException;

   abstract void skipSpace() throws XMLStreamException;

   abstract void skipDTD() throws XMLStreamException;

   abstract boolean skipCTxt() throws XMLStreamException;

   abstract boolean more() throws XMLStreamException;

   abstract int getCol();

   private final void noteDupAttr(int idx1, int idx2){
      errMsg = new StrB(48).a("Duplicate '").append(names[idx1].toString()).append('\'').append('@').apos(idx1
         ).append(", '").append(names[idx2].toString()).append('\'').append('@').apos(idx2).toString();
   }

   final void throwInputErr(String msg) throws XMLStreamException{ throw new XMLStreamException(msg, getCurLoc()); }

   final void throwUnexpandEnt() throws XMLStreamException{ throwInputErr("Unexpanded ENTITY_REFERENCE"); }

   final void throwUnexpRoot(boolean isProlog, int ch) throws XMLStreamException{
      if((ch &= 0x7FFFF) == '/')
         throwInputErr(isProlog ? "Unexpected end element in prolog" : "Unexpected end element in epilog");
      if(ch < 32)
         throwUnexpChr(ch, isProlog ? ", unrecognized prolog directive" : ", unrecognized epilog directive");
      throwInputErr("Only one root element allowed");
   }
    
   final void throwPlogUnxpChr(boolean isProlog, int ch) throws XMLStreamException{
      throwUnexpChr(ch, isProlog ? " in prolog" : " in epilog");
   }

   final void throwInvNChr(int ch) throws XMLStreamException{
      throwInputErr(ch == (int)':' ? "At most one ':' allowed in elem./attr. names, none in PI target/entity"
        : new StrB(20).a("Name char ").apos(ch).toString());
   }

   final void throwInvChr(int ch) throws XMLStreamException{
      throwInputErr(new StrB(24).a("Invalid char ").apos(ch).toString());
   }

   final void throwNoPISpace(int ch) throws XMLStreamException{ throwUnexpChr(ch, ", not space or closing '?>'"); }

   final void throwHyphn() throws XMLStreamException{ throwInputErr("'--' in comment, missing '>'?"); }

   final void throwUnbPfx(PN name, boolean isAttr) throws XMLStreamException{
      throwInputErr(new StrB(48).a("Unbound prefix ").append(name.pfx).append(isAttr ? ", attribute " : ", element ").append(name.pfxdName).toString());
   }

   final void throwUnexpEnd(String name) throws XMLStreamException{
      throwInputErr(new StrB(24 + name.length()).a("Unexpected end tag, not ").a(name).toString());
   }

   final void throwCDataEnd() throws XMLStreamException{ throwInputErr("']]>' allowed only as the end marker of CDATA"); }

   final void throwUnexpChr(int ch, String msg) throws XMLStreamException{
      if(ch < 32 && ch != '\r' && ch != '\n' && ch != '\t')
         throwChr(ch);
      throwInputErr(new StrB(28 + msg.length()).a("Unexpected char. ").apos(ch).a(msg).toString());
   }

   final void throwChr(int ch) throws XMLStreamException{
      throwInputErr(new StrB(24).a("Illegal char. ").apos(ch).toString());
   }
}
