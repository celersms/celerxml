// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025
package com.celerxml;

import java.io.Reader;
import java.io.IOException;
import javax.xml.stream.Location;
import javax.xml.stream.XMLReporter;
import javax.xml.stream.XMLStreamException;

class InputSrc{

   static final String V10 = "1.0";
   static final String V11 = "1.1";
   static final String YES = "yes";
   static final String NO = "no";
   static final String ASCII = "US-ASCII";
   static final String LATN1 = "ISO-8859-1";
   static final String UTF8 = "UTF-8";
   static final String UTF16 = "UTF-16";
   static final String UTF16B = "UTF-16BE";
   static final String UTF16L = "UTF-16LE";
   static final String UTF32 = "UTF-32";
   static final String UTF32B = "UTF-32BE";
   static final String UTF32L = "UTF-32LE";

   int processed, inRow, inRowOff, offset, inLen, mXmlVer;
   final InputFactoryImpl impl;
   String foundEnc, stand;
   final char[] mKW;
   private final Reader r;
   private final char[] buff;

   InputSrc(InputFactoryImpl impl){
      this.impl = impl;
      this.r = null;
      mKW = impl.getCB1();
      buff = null;
   }

   InputSrc(InputFactoryImpl impl, Reader r){
      this.impl = impl;
      this.r = r;
      mKW = impl.getCB1();
      buff = impl.getCB3();
   }

   final void readDecl() throws IOException, XMLStreamException{
      int val = 0, len, ch = nxtAfterWs();
      if(ch != 'v')
         throwUnexpChr(ch, "; expected keyword 'version'");
      if((ch = isKW("version")) != 0)
         throwUnexpChr(ch, "version");
      if((len = qVal(mKW, handleEq())) == 3 && mKW[0] == '1' && mKW[1] == '.')
         if((ch = mKW[2]) == '0')
            val = 0x100; // V10
         else if(ch == '1')
            val = 0x110; // V11
      if(val == 0)
         throwPseudoAttr("version", V10, V11);
      mXmlVer = val;
      if((ch = getWsOrQMark()) == 'e'){
         if((ch = isKW("encoding")) != 0)
            throwUnexpChr(ch, "encoding");
         if((len = qVal(mKW, handleEq())) == 0)
            throwPseudoAttr("encoding", null, null);
         foundEnc = len < 0 ? new String(mKW) : new String(mKW, 0, len);
         ch = getWsOrQMark();
      }
      if(ch == 's'){
         if((ch = isKW("standalone")) != 0)
            throwUnexpChr(ch, "standalone");
         if((len = qVal(mKW, handleEq())) == 2 && mKW[0] == 'n' && mKW[1] == 'o')
            stand = NO;
         else if(len == 3 && mKW[0] == 'y' && mKW[1] == 'e' && mKW[2] == 's')
            stand = YES;
         else
            throwPseudoAttr("standalone", YES, NO);
         ch = getWsOrQMark();
      }
      if(ch != '?' || (ch = getNxt()) != '>')
         throwUnexpChr(ch, ", expected '?>'");
   }

   private final int handleEq() throws IOException, XMLStreamException{
      int ch = nxtAfterWs();
      if(ch != '=')
         throwUnexpChr(ch, ", expected '='");
      if((ch = nxtAfterWs()) != '"' && ch != '\'')
         throwUnexpChr(ch, ", expected a quote");
      return ch;
   }

   private final int getWsOrQMark() throws IOException, XMLStreamException{
      int ch = getNxt();
      if(ch == '?')
         return ch;
      if(ch > 0x20)
         throwUnexpChr(ch, ", expected either '?' or white space");
      if(ch == '\n' || ch == '\r')
         pushback();
      return nxtAfterWs();
   }

   XmlScanner wrap() throws XMLStreamException{
      try{
         if(r != null)
            while(inLen < 7){
               int count = r.read(buff, inLen, 4096 - inLen);
               if(count < 1)
                  break;
               inLen += count;
            }
         String normEnc = null;
         if(inLen - offset >= 7){
            char c = buff[offset];
            if(c == (char)0xFEFF)
               c = buff[++offset];
            if(c == '<'){
               if(buff[offset + 1] == '?' && buff[offset + 2] == 'x' && buff[offset + 3] == 'm' && buff[offset + 4] == 'l' && buff[offset + 5] <= 0x20){
                  offset += 6;
                  readDecl();
                  if(foundEnc != null){
                     normEnc = normalize(foundEnc);
                     String extEnc = impl.extEnc;
                     if(extEnc != null && normEnc != null && !extEnc.equalsIgnoreCase(normEnc)){
                        XMLReporter rep = impl.rep;
                        if(rep != null)
                           rep.report("Inconsistent encoding", "xml declaration", this, getLocation());
                     }
                  }
               }
            }else if(c == 0xEF)
               throw new XMLStreamException("First char 0xEF not valid in xml document");
         }
         impl.enc = normEnc;
         impl.setXmlDeclInfo(mXmlVer, foundEnc, stand);
         return new ReaderScanner(impl, r, buff, offset, inLen);
      }catch(IOException ioe){
         throw new XMLStreamException(ioe);
      }finally{
         impl.setCB1(mKW);
      }
   }

   void pushback(){ --offset; }

   int getNxt() throws IOException, XMLStreamException{ return offset < inLen ? buff[offset++] : nxtC(); }

   int nxtAfterWs() throws IOException, XMLStreamException{
      while(true){
         char c = offset < inLen ? buff[offset++] : nxtC();
         if(c > 0x20)
            return c;
         if(c == '\r' || c == '\n'){
            if(c == '\r' && (offset < inLen ? buff[offset++] : nxtC()) != '\n')
               --offset;
            ++inRow;
            inRowOff = offset;
         }else if(c == 0)
            throwNull();
      }
   }

   int isKW(String kw) throws IOException, XMLStreamException{
      for(int ptr = 1, len = kw.length(); ptr < len; ++ptr){
         char c = offset < inLen ? buff[offset++] : nxtC();
         if(c == 0)
            throwNull();
         if(c != kw.charAt(ptr))
            return c;
      }
      return 0;
   }

   int qVal(char[] kw, int quoteChar) throws IOException, XMLStreamException{
      int i = 0, len = kw.length;
      while(true){
         char c = offset < inLen ? buff[offset++] : nxtC();
         if(c == '\r' || c == '\n'){
            if(c == '\r' && (offset < inLen ? buff[offset++] : nxtC()) != '\n')
               --offset;
            ++inRow;
            inRowOff = offset;
         }else if(c == 0)
            throwNull();
         if(c == quoteChar)
            return i < len ? i : -1;
         if(i < len)
            kw[i++] = c;
      }
   }

   Location getLocation(){ return new LocImpl(impl.pubId, impl.sysId, offset - inRowOff, inRow, processed + offset); }

   static final String normalize(String enc){
      int len = enc.length();
      if(len < 3)
         return enc;
      int off = 0, ch = enc.charAt(0) | 0x20; // to lowercase
      if(ch == 'c' && (enc.charAt(1) | 0x20) == 's'){ // to lowercase
         ch = enc.charAt(2) | 0x20; // to lowercase
         off = 2;
      }
      switch(ch){
         case 'a':
            if(eqEnc(enc, "ASCII", off, len, false))
               return ASCII;
         case 'c':
            break;
         case 'e':
            if(eqEnc(enc, "EBCDIC", off, len, true))
               return "EBCDIC";
            break;
         case 'i':
            if(eqEnc(enc, LATN1, off, len, false) || eqEnc(enc, "ISO-Latin1", off, len, false))
               return LATN1;
            if(eqEnc(enc, "ISO-10646", off, len, true)){
               if(eqEnc(enc, "UCS-Basic", off = enc.indexOf("10646") + 5, len, false))
                  return ASCII;
               if(eqEnc(enc, "Unicode-Latin1", off, len, false))
                  return LATN1;
               if(eqEnc(enc, "UCS-2", off, len, false))
                  return UTF16;
               if(eqEnc(enc, "UCS-4", off, len, false))
                  return UTF32;
               if(eqEnc(enc, "UTF-1", off, len, false) || eqEnc(enc, "J-1", off, len, false) || eqEnc(enc, ASCII, off, len, false))
                  return ASCII;
            }
            break;
         case 'j':
            if(eqEnc(enc, "JIS_Encoding", off, len, false))
               return "Shift_JIS";
            break;
         case 's':
            if(eqEnc(enc, "Shift_JIS", off, len, false))
               return "Shift_JIS";
            break;
         case 'u':
            if(len > off + 1)
               switch(enc.charAt(off + 1) | 0x20){ // to lowercase
                  case 'c':
                     if(eqEnc(enc, "UCS-2", off, len, false))
                        return UTF16;
                     if(eqEnc(enc, "UCS-4", off, len, false))
                        return UTF32;
                     break;
                  case 'n':
                     if(eqEnc(enc, "Unicode", off, len, false))
                        return UTF16;
                     if(eqEnc(enc, "UnicodeAscii", off, len, false))
                        return LATN1;
                     if(eqEnc(enc, "UnicodeAscii", off, len, false))
                        return ASCII;
                     break;
                  case 's':
                     if(eqEnc(enc, ASCII, off, len, false))
                        return ASCII;
                     break;
                  case 't': 
                     if(eqEnc(enc, UTF8, off, len, false))
                        return UTF8;
                     if(eqEnc(enc, UTF16B, off, len, false))
                        return UTF16B;
                     if(eqEnc(enc, UTF16L, off, len, false))
                        return UTF16L;
                     if(eqEnc(enc, UTF16, off, len, false))
                        return UTF16;
                     if(eqEnc(enc, UTF32B, off, len, false))
                        return UTF32B;
                     if(eqEnc(enc, UTF32L, off, len, false))
                        return UTF32L;
                     if(eqEnc(enc, UTF32, off, len, false))
                        return UTF32;
                     if(eqEnc(enc, "UTF", off, len, false))
                        return UTF16;
            }
      }
      return enc;
   }

   private static final boolean eqEnc(String enc, String equ, int i1, int len1, boolean starts){
      int c1, c2, len2 = equ.length(), i2 = 0, cend = 0x10000;
      while(i1 < len1 || i2 < len2){
         c1 = c2 = cend;
         while(i1 < len1 && ((c1 = enc.charAt(i1++)) <= 0x2D || c1 == '_' ))
            c1 = cend;
         while(i2 < len2 && ((c2 = equ.charAt(i2++)) <= 0x2D || c2 == '_' ))
            c2 = cend;
         if(c1 != c2){
            if(c2 == cend)
               return starts;
            if(c1 == cend || (c1 | 0x20) != (c2 | 0x20))
               return false;
         }
      }
      return true; 
   }

   private final char nxtC() throws IOException, XMLStreamException{
      if(offset >= inLen){
         processed += inLen;
         inRowOff -= inLen;
         offset = 0;
         if(r == null || (inLen = r.read(buff, 0, 4096)) < 1)
            throw new XMLStreamException("Unexpected end-of-input", getLocation());
      }
      return buff[offset++];
   }

   final void throwNull() throws XMLStreamException{
      throw new XMLStreamException("Null in input stream", getLocation());
   }

   final void throwUnexpChr(int ch, String msg) throws XMLStreamException{
      throw new XMLStreamException(new StrB(26 + msg.length()).a("Unexpected char ").apos(ch).a(msg).toString(), getLocation());
   }

   private final void throwPseudoAttr(String name, String val1, String val2) throws XMLStreamException{
      StrB sb = new StrB(56 + name.length()).a("Missing XML pseudo-attribute '").a(name).a('\'');
      if(val1 != null)
         sb.a(", expected '").a(val1).a("' or '").a(val2).a('\'');
      throw new XMLStreamException(sb.toString(), getLocation());
   }
}
