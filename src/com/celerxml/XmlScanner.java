// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025 - 2026
package com.celerxml;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import javax.xml.XMLConstants;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import org.xml.sax.SAXException;
import org.xml.sax.ContentHandler;

abstract class XmlScanner implements javax.xml.namespace.NamespaceContext{

   final InputFactoryImpl impl;
   boolean inc, pend, empty;
   int depth, nsCnt, attrCount, currSize, attrs, currToken, currRow, rowOff, bOrC, iniRawOff, startRow, startCol, inPtr;
   char[] nameBuf, currSeg;
   PN tokName;
   NsD lastNs;
   final NsB defNs;
   private NsB[] nsBind;
   private PN[] nsCache;
   private ArrayList segments;
   private int bCnt, bindMiss, segSize, rLen, hashSize, spillEnd;
   private char[] arr, vals;
   private String result, attrV;
   private boolean indent, doRst;
   private int[] attrMap, offsets;
   PN[] names;
   String err, dtdSys, dtdPub;
   final boolean cls, lazy;

   static final String EOI = "Unexpected EOI", CDATA = "CDATA[";
   private static final char[] arr0 = new char[0], indWS = "\n                                 ".toCharArray(), indTAB = "\n\t\t\t\t\t\t\t\t\t".toCharArray();
   private static final String[] arrWS = new String[34], arrTAB = new String[10];
   private static final Iterator s1Empty = new SIterator(null, true);

   XmlScanner(InputFactoryImpl impl){
      cls = impl.Code(2);
      lazy = impl.Code(256);
      nameBuf = (this.impl = impl).getCB1();
      defNs = new NsB(null);
      startRow = startCol = -1;
      currToken = 7; // START_DOCUMENT
      doRst = true;
   }

   final void free() throws XMLStreamException{
      Code();
      if(impl.Code(8192))
         try{
            close();
         }catch(IOException ioe){
            throw new XMLStreamException(ioe);
         }
   }

   final boolean skipTok() throws XMLStreamException{
      inc = false;
      switch(currToken){
         case 3:  // PROCESSING_INSTRUCTION
            skipPI();
            return false;
         case 4:  // CHARACTERS
            if(skipChars() || (cls && skipCTxt())){
               currToken = 9; // ENTITY_REFERENCE
               return true;
            }
            break;
         case 5:  // COMMENT
            skipComm();
            return false;
         case 6:  // SPACE
            skipWS();
            return false;
         case 12: // CDATA
            skipCData();
            if(cls){
               skipCTxt();
               if(pend){
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

   final String getText() throws XMLStreamException{
      if(inc)
         endTok();
      if(result == null)
         if(arr != null)
            result = new String(arr);
         else{
            int segLen, currLen = currSize;
            if((segLen = segSize) == 0)
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
      if(inc)
         endTok();
      return currSize < 0 ? rLen : currSize + segSize;
   }

   private final char[] buildR(){
      if(result != null)
         return result.toCharArray();
      int size;
      if((size = currSize < 0 ? rLen : currSize + segSize) < 1)
         return arr0;
      int offset = 0;
      char[] curr, res = new char[size];
      for(int i = 0, len = segments.size(); i < len; ++i){
         System.arraycopy(curr = (char[])segments.get(i), 0, res, offset, size = curr.length);
         offset += size;
      }
      System.arraycopy(currSeg, 0, res, offset, currSize);
      return res;
   }

   final char[] getTextCharacters() throws XMLStreamException{
      if(inc)
         endTok();
      char[] res = arr;
      if(segments == null || segments.size() == 0)
         return res != null ? res : currSeg;
      if(res == null)
         arr = res = buildR();
      return res;
   }

   final int getTextCharacters(int srcStart, char[] dst, int dstStart, int len) throws XMLStreamException{
      if(inc)
         endTok();
      int amount, totalAmount = 0, segLen;
      if(segments != null)
         for(int i = 0, segc = segments.size(); i < segc; ++i){
            char[] segment = (char[])segments.get(i);
            if((amount = (segLen = segment.length) - srcStart) <= 0){
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
      if(inc)
         endTok();
      if(indent)
         return true;
      char[] buf = currSeg;
      for(int i = 0, len = currSize; i < len; ++i)
         if(buf[i] > 0x20)
            return false;
      if(segments != null)
         for(int i = 0, len = segments.size(); i < len; ++i)
            for(int j = 0, len2 = (buf = (char[])segments.get(i)).length; j < len2; ++j)
               if(buf[j] > 0x20)
                  return false;
      return true;
   }

   final int getNC(){ return currToken == 1 ? nsCnt : (lastNs == null ? 0 : lastNs.Code(depth)); } // START_ELEMENT

   final NsD findCurrNsDecl(int idx){
      NsD nsDecl = lastNs;
      int level = depth, count = idx;
      if(currToken == 1){ // START_ELEMENT
         count = nsCnt - 1 - idx;
         --level;
      }
      while(nsDecl != null && nsDecl.lvl == level){
         if(count-- == 0)
            return nsDecl;
         nsDecl = nsDecl.prvD;
      }
      throw new IndexOutOfBoundsException(new StrB(32).a("Wrong namespace index ").apos(idx).toString());
   }

   public String getNamespaceURI(String pfx){
      if(pfx == null)
         throw new IllegalArgumentException("Illegal null argument");
      if(pfx.length() == 0){
         String uri;
         return (uri = defNs.Code) == null ? "" : uri;
      }
      if(pfx.equals(XMLConstants.XML_NS_PREFIX))
         return XMLConstants.XML_NS_URI;
      if(pfx.equals(XMLConstants.XMLNS_ATTRIBUTE))
         return XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
      for(NsD nsDecl = lastNs; nsDecl != null; nsDecl = nsDecl.prvD)
         if(pfx.equals(nsDecl.bind.pfx))
            return nsDecl.bind.Code;
      return null;
   }

   public final String getPrefix(String nsURI){
      if(nsURI == null)
         throw new IllegalArgumentException("Illegal null argument");
      if(nsURI.equals(XMLConstants.XML_NS_URI))
         return XMLConstants.XML_NS_PREFIX;
      if(nsURI.equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI))
         return XMLConstants.XMLNS_ATTRIBUTE;
      if(nsURI.equals(defNs.Code))
         return "";
      String pfx;
loop_pfx:
      for(NsD nsDecl = lastNs; nsDecl != null; nsDecl = nsDecl.prvD)
         if(nsURI.equals(nsDecl.bind.Code) && (pfx = nsDecl.bind.pfx) != null){
            for(NsD decl2 = lastNs; decl2 != nsDecl; decl2 = decl2.prvD)
               if(pfx.equals(decl2.bind.pfx))
                  continue loop_pfx;
            return pfx;
         }
      return null;
   }

   @SuppressWarnings("unchecked")
   public final Iterator getPrefixes(String nsURI){
      if(nsURI == null)
         throw new IllegalArgumentException("Illegal null argument");
      if(nsURI.equals(XMLConstants.XML_NS_URI))
         return new SIterator(XMLConstants.XML_NS_PREFIX, false);
      if(nsURI.equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI))
         return new SIterator(XMLConstants.XMLNS_ATTRIBUTE, false);
      ArrayList larr = null;
      if(nsURI.equals(defNs.Code))
         (larr = new ArrayList()).add("");
      String pfx;
loop_pfx:
      for(NsD nsDecl = lastNs; nsDecl != null; nsDecl = nsDecl.prvD)
         if(nsURI.equals(nsDecl.bind.Code) && (pfx = nsDecl.bind.pfx) != null){
            for(NsD decl2 = lastNs; decl2 != nsDecl; decl2 = decl2.prvD)
               if(pfx.equals(decl2.bind.pfx))
                  continue loop_pfx;
            if(larr == null)
               larr = new ArrayList();
            larr.add(pfx);
         }
      if(larr == null)
         return s1Empty;
      return larr.size() == 1 ? new SIterator((String)larr.get(0), false) : larr.iterator();
   }

   final PN bindName(PN name, String pfx){
      if(nsCache != null){
         PN cn;
         if((cn = nsCache[name.Code.hashCode() & 0x3F]) != null && cn.Code == name.Code)
            return cn;
      }
      for(int i = 0, len = bCnt; i < len; ++i){
         NsB b;
         if((b = nsBind[i]).pfx != pfx)
            continue;
         if(i > 0) {
            nsBind[i] = nsBind[i - 1];
            nsBind[i - 1] = b;
         }
         PN cn = name.Code(b);
         if(nsCache == null){
            if(++bindMiss < 10)
               return cn;
             nsCache = new PN[0x40];
         }
         return nsCache[cn.Code.hashCode() & 0x3F] = cn;
      }
      if(pfx == "xml")
         return name.Code(NsB.XML);
      ++bindMiss;
      if(bCnt == 0)
         nsBind = new NsB[16];
      else if(bCnt >= nsBind.length)
         nsBind = xpand(nsBind);
      return name.Code(nsBind[bCnt++] = new NsB(pfx));
   }

   final void bindNs(PN name, String uri) throws XMLStreamException{
      NsB ns = defNs;
      String pfx;
      if((pfx = name.pfx) != null){
         pfx = name.ln;
findOrCreate:
         {
            for(int i = 0, len = bCnt; i < len; ++i)
               if((ns = nsBind[i]).pfx == pfx){
                  if(i > 0){
                     nsBind[i] = nsBind[i - 1];
                     nsBind[i - 1] = ns;
                  }
                  break findOrCreate;
               }
            if(pfx == "xml")
               ns = NsB.XML;
            else if(pfx == "xmlns")
               ns = NsB.XMLNS;
            else{
               ns = new NsB(pfx);
               if(bCnt == 0)
                  nsBind = new NsB[16];
               else if(bCnt >= nsBind.length)
                  nsBind = xpand(nsBind);
               nsBind[bCnt++] = ns;
            }
         }
         if(pfx != null && ns.Code() && (pfx != "xml" || !uri.equals(XMLConstants.XML_NS_URI)))
            thInErr(new StrB(20 + pfx.length()).a("Can't rebind prefix ").a(pfx).toString());
      }
      if(!ns.Code()){
         if(uri == XMLConstants.XML_NS_URI)
            thInErr("Can't bind 'http://www.w3.org/XML/1998/namespace' to prefix other than 'xml'");
         if(uri == XMLConstants.XMLNS_ATTRIBUTE_NS_URI)
            thInErr("Can't bind 'http://www.w3.org/2000/xmlns/' to prefix other than 'xmlns'");
      }
      if(lastNs != null && lastNs.Code(pfx, depth))
         thInErr(pfx == null ? "Duplicate default namespace" : new StrB(24 + pfx.length()).a("Duplicate decl., prefix ").a(pfx).toString());
      lastNs = new NsD(ns, uri, lastNs, depth);
   }

   final void assertMore() throws XMLStreamException{
      if(!more())
         thInErr(EOI);
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

   final void indent(int indCharCount, char indChar){
      if(segments != null && segments.size() > 0){
         segments.clear();
         segSize = 0;
      }
      currSize = -1;
      indent = true;
      String text;
      int strlen = rLen = indCharCount + 1;
      if(indChar == '\t'){
         arr = indTAB;
         if((text = arrTAB[indCharCount]) == null)
            arrTAB[indCharCount] = text = "\n\t\t\t\t\t\t\t\t\t".substring(0, strlen);
      }else{
         arr = indWS;
         if((text = arrWS[indCharCount]) == null)
            arrWS[indCharCount] = text = "\n                                 ".substring(0, strlen);
      }
      result = text;
   }

   @SuppressWarnings("unchecked")
   final char[] endSeg(){
      if(segments == null)
         segments = new ArrayList();
      segments.add(currSeg);
      int oldLen;
      segSize += oldLen = currSeg.length;
      currSize = 0;
      return currSeg = new char[Math.min(oldLen + (oldLen < 8000 ? oldLen : oldLen >> 1), 0x40000)];
   }

   final char[] startNewV(PN attrName, int currOffset){
      int count;
      if(doRst){
         doRst = false;
         attrs = count = 0;
         attrV = null;
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
      int count;
      offsets[(count = attrs) - 1] = endingOffset;
      PN[] names = this.names;
      if(count < 3){
         hashSize = 0;
         if(count == 2 && names[0].Code(names[1])){
            dupAttr(0, 1);
            return -1;
         }
         return count;
      }
      int[] map;
      int min = count + (count >> 2), hashCount = (min + 7) & ~7, mask = hashCount - 1; // next multiple of 8 (never 0)
      hashSize = hashCount;
      min = hashCount + (hashCount >> 4);
      if((map = attrMap) == null || map.length < min)
         map = new int[min];
      else
         for(int i = 0; i < hashCount; ++i)
            map[i] = 0;
      for(int i = 0; i < count; ++i){
         PN newName = names[i];
         int hash = newName.ln.hashCode(), index = hash & mask, oldNameIdx;
         if((oldNameIdx = map[index]) == 0)
            map[index] = i + 1;
         else{
            if(names[--oldNameIdx].Code(newName) && err == null)
               dupAttr(oldNameIdx, i);
            if(hashCount + 1 >= map.length)
               map = xpand(map, 8);
            // for(int j = hashCount; j < spillIndex; j += 2)
            //   if(map[j] == hash && names[oldNameIdx = map[j + 1]].Code(newName)){
            //      if(err == null)
            //         dupAttr(oldNameIdx, i);
            //      break;
            //   }
            map[hashCount++] = hash;
            map[hashCount++] = i;
         }
      }
      spillEnd = hashCount;
      attrMap = map;
      return err == null ? count : -1;
   }

   final char[] xpand(){ return vals = xpand(vals); }

   static final int[] xpand(int[] arr, int more){
      int len;
      System.arraycopy(arr, 0, arr = new int[(len = arr.length) + more], 0, len);
      return arr;
   }

   static final char[] xpand(char[] arr){
      int len;
      System.arraycopy(arr, 0, arr = new char[(len = arr.length) + len], 0, len);
      return arr;
   }

   static final NsB[] xpand(NsB[] arr){
      int len;
      System.arraycopy(arr, 0, arr = new NsB[(len = arr.length) + len], 0, len);
      return arr;
   }

   final String getV(int idx){
      int xx, yy;
      if(attrV == null)
         attrV = (xx = offsets[attrs - 1]) == 0 ? "" : new String(vals, 0, xx);
      return idx == 0 ? (attrs == 1 ? attrV : (xx = offsets[0]) == 0 ? "" : attrV.substring(0, xx)) : (yy = offsets[idx - 1]) == (xx = offsets[idx]) ? "" : attrV.substring(yy, xx);
   }

   final String getV(String nsUri, String name){
      int ix;
      return (ix = findIdx(nsUri, name)) >= 0 ? getV(ix) : null;
   }

   final int findIdx(String nsUri, String name){
      int xx;
      if((xx = hashSize) < 1){
         for(int i = 0, len = attrs; i < len; ++i)
            if(names[i].Code(nsUri, name))
               return i;
         return -1;
      }
      int hash = name.hashCode(), ix;
      if((ix = attrMap[hash & (xx - 1)]) > 0){
         if(names[--ix].Code(nsUri, name))
            return ix;
         for(int len = spillEnd; xx < len; xx += 2)
            if(attrMap[xx] == hash && names[ix = attrMap[xx + 1]].Code(nsUri, name))
               return ix;
      }
      return -1;
   }

   final void chrEvts(ContentHandler h) throws XMLStreamException, SAXException{
      if(h != null){
         if(inc)
            endTok();
         if(arr != null)
            h.characters(arr, 0, rLen);
         else{
            if(segments != null)
               for(int i = 0, len = segments.size(); i < len; ++i){
                  char[] ch;
                  h.characters(ch = (char[])segments.get(i), 0, ch.length);
               }
            if(currSize > 0)
               h.characters(currSeg, 0, currSize);
         }
      }
   }

   final void spaceEvts(ContentHandler h) throws XMLStreamException, SAXException{
      if(h != null){
         if(inc)
            endTok();
         if(arr != null)
            h.ignorableWhitespace(arr, 0, rLen);
         else{
            if(segments != null)
               for(int i = 0, len = segments.size(); i < len; ++i){
                  char[] ch;
                  h.ignorableWhitespace(ch = (char[])segments.get(i), 0, ch.length);
               }
            if(currSize > 0)
               h.ignorableWhitespace(currSeg, 0, currSize);
         }
      }
   }

   final void PIEvent(ContentHandler h) throws XMLStreamException, SAXException{
      if(h != null){
         if(inc)
            endTok();
         h.processingInstruction(tokName.ln, getText());
      }
   }

   final void startElement(ContentHandler h, org.xml.sax.Attributes attrs) throws SAXException{
      if(h != null){
         String ss;
         int level = depth - 1;
         for(NsD nsDecl = lastNs; nsDecl != null && nsDecl.lvl == level; nsDecl = nsDecl.prvD)
            h.startPrefixMapping((ss = nsDecl.bind.pfx) == null ? "" : ss, nsDecl.bind.Code);
         PN n;
         h.startElement((ss = (n = tokName).getNsUri()) == null ? "" : ss, n.ln, n.Code, attrs);
      }
   }

   final void endElement(ContentHandler h) throws SAXException{
      if(h != null){
         PN n;
         String ss;
         h.endElement((ss = (n = tokName).getNsUri()) == null ? "" : ss, n.ln, n.Code);
         int level = depth;
         for(NsD nsDecl = lastNs; nsDecl != null && nsDecl.lvl == level; nsDecl = nsDecl.prvD)
            h.endPrefixMapping((ss = nsDecl.bind.pfx) == null ? "" : ss);
      }
   }

   final void commentEvent(org.xml.sax.ext.LexicalHandler h) throws XMLStreamException, SAXException{
      if(h != null){
         if(inc)
            endTok();
         if(arr != null)
            h.comment(arr, 0, rLen);
         else if(segments != null && segments.size() > 0)
            h.comment(arr = buildR(), 0, arr.length);
         else
            h.comment(currSeg, 0, currSize);
      }
   }

   void Code(){
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

   abstract void close() throws IOException;

   abstract int nxtFromProlog(boolean isProlog) throws XMLStreamException;

   abstract int nxtFromTree() throws XMLStreamException;

   abstract void endTok() throws XMLStreamException;

   abstract Location loc();

   abstract boolean skipChars() throws XMLStreamException;

   abstract void skipCData() throws XMLStreamException;

   abstract void skipComm() throws XMLStreamException;

   abstract void skipPI() throws XMLStreamException;

   abstract void skipWS() throws XMLStreamException;

   abstract void skipDTD() throws XMLStreamException;

   abstract boolean skipCTxt() throws XMLStreamException;

   abstract boolean more() throws XMLStreamException;

   final int col(){ return inPtr - iniRawOff; }

   private final void dupAttr(int idx1, int idx2){
      err = new StrB(48).a("Duplicate '").append(names[idx1].toString()).append('\'').append('@').apos(idx1
         ).append(", '").append(names[idx2].toString()).append('\'').append('@').apos(idx2).toString();
   }

   final void thInErr(String msg) throws XMLStreamException{ throw new XMLStreamException(msg, loc()); }

   final void thEnt() throws XMLStreamException{ thInErr("Unexpanded ENTITY_REF"); }

   final void thRoot(boolean isProlog, int ch) throws XMLStreamException{
      if((ch &= 0x7FFFF) == '/')
         thInErr(isProlog ? "Unexpected end element in prolog" : "Unexpected end element in epilog");
      if(ch < 32)
         thUnxp(ch, isProlog ? ", unrecognized prolog directive" : ", unrecognized epilog directive");
      thInErr("Only one root element allowed");
   }
    
   final void thPlogUnxpCh(boolean isProl, int ch) throws XMLStreamException{ thUnxp(ch, isProl ? " in prolog" : " in epilog"); }

   final void thInvNCh(int ch) throws XMLStreamException{
      thInErr(ch == (int)':' ? "Single ':' allowed in elem./attr. names, none in PI target/entity"
        : new StrB(20).a("Name char ").apos(ch).toString());
   }

   final void thInvC(int ch) throws XMLStreamException{ thInErr(new StrB(24).a("Invalid char ").apos(ch).toString()); }

   final void thNoPISp(int ch) throws XMLStreamException{ thUnxp(ch, ", not space or closing '?>'"); }

   final void thHyph() throws XMLStreamException{ thInErr("'--' in comment"); }

   final void thUnbPfx(PN name, boolean isAttr) throws XMLStreamException{
      thInErr(new StrB(48).a("Unbound prefix ").append(name.pfx).append(isAttr ? ", attribute " : ", element ").append(name.Code).toString());
   }

   final void thUnxp(String n) throws XMLStreamException{ thInErr(new StrB(24 + n.length()).a("Unexpected end tag, not ").a(n).toString()); }

   final void thCDEnd() throws XMLStreamException{ thInErr("']]>' must only close CDATA"); }

   final void thUnxp(int ch, String msg) throws XMLStreamException{
      if(ch < 32 && ch != '\r' && ch != '\n' && ch != '\t')
         thC(ch);
      thInErr(new StrB(24 + msg.length()).a("Unexpected ").apos(ch).a(msg).toString());
   }

   final void thC(int ch) throws XMLStreamException{ thInErr(new StrB(24).a("Illegal char ").apos(ch).toString()); }
}
