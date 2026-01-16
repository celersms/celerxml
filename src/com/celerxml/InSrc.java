// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025 - 2026
package com.celerxml;

import java.io.Reader;
import java.io.IOException;
import javax.xml.stream.Location;
import javax.xml.stream.XMLReporter;
import javax.xml.stream.XMLStreamException;

class InSrc{

   static final String V10 = "1.0", V11 = "1.1", YES = "yes", NO = "no", ASCII = "US-ASCII", LAT1 = "ISO-8859-1";
   static final String UTF8 = "UTF-8", UTF16 = "UTF-16", UTF16B = "UTF-16BE", UTF16L = "UTF-16LE", UTF32 = "UTF-32", UTF32B = "UTF-32BE", UTF32L = "UTF-32LE";

   int proc, inRow, inRowOff, offset, inLen, mXmlVer;
   final InputFactoryImpl impl;
   String fnd, stand;
   final char[] mKW;
   private final Reader r;
   private final char[] Code;

   InSrc(InputFactoryImpl impl){
      this.impl = impl;
      this.r = null;
      mKW = impl.getCB1();
      Code = null;
   }

   InSrc(InputFactoryImpl impl, Reader r){
      this.impl = impl;
      this.r = r;
      mKW = impl.getCB1();
      Code = impl.getCB3();
   }

   final void readDecl() throws IOException, XMLStreamException{
      int val = 0, len, ch = afterWs();
      if(ch != 'v')
         thUnxp(ch, "; expected keyword 'version'");
      if((ch = isKW("version")) != 0)
         thUnxp(ch, "version");
      if((len = qVal(mKW, handleEq())) == 3 && mKW[0] == '1' && mKW[1] == '.')
         if((ch = mKW[2]) == '0')
            val = 0x100; // V10
         else if(ch == '1')
            val = 0x110; // V11
      if(val == 0)
         thPsAttr("version", V10, V11);
      mXmlVer = val;
      if((ch = getWsOrQMark()) == 'e'){
         if((ch = isKW("encoding")) != 0)
            thUnxp(ch, "encoding");
         if((len = qVal(mKW, handleEq())) == 0)
            thPsAttr("encoding", null, null);
         fnd = len < 0 ? new String(mKW) : new String(mKW, 0, len);
         ch = getWsOrQMark();
      }
      if(ch == 's'){
         if((ch = isKW("standalone")) != 0)
            thUnxp(ch, "standalone");
         if((len = qVal(mKW, handleEq())) == 2 && mKW[0] == 'n' && mKW[1] == 'o')
            stand = NO;
         else if(len == 3 && mKW[0] == 'y' && mKW[1] == 'e' && mKW[2] == 's')
            stand = YES;
         else
            thPsAttr("standalone", YES, NO);
         ch = getWsOrQMark();
      }
      if(ch != '?' || (ch = nxt()) != '>')
         thUnxp(ch, ", expected '?>'");
   }

   private final int handleEq() throws IOException, XMLStreamException{
      int ch = afterWs();
      if(ch != '=')
         thUnxp(ch, ", expected '='");
      if((ch = afterWs()) != '"' && ch != '\'')
         thUnxp(ch, ", expected a quote");
      return ch;
   }

   private final int getWsOrQMark() throws IOException, XMLStreamException{
      int ch = nxt();
      if(ch == '?')
         return ch;
      if(ch > 0x20)
         thUnxp(ch, ", expected either '?' or white space");
      if(ch == '\n' || ch == '\r')
         bck();
      return afterWs();
   }

   XmlScanner w() throws XMLStreamException{
      try{
         if(r != null)
            while(inLen < 7){
               int count = r.read(Code, inLen, 4096 - inLen);
               if(count < 1)
                  break;
               inLen += count;
            }
         String normEnc = null;
         if(inLen - offset >= 7){
            char c = Code[offset];
            if(c == (char)0xFEFF)
               c = Code[++offset];
            if(c == '<'){
               if(Code[offset + 1] == '?' && Code[offset + 2] == 'x' && Code[offset + 3] == 'm' && Code[offset + 4] == 'l' && Code[offset + 5] <= 0x20){
                  offset += 6;
                  readDecl();
                  if(fnd != null){
                     normEnc = Code(fnd);
                     String extEnc = impl.extEnc;
                     if(extEnc != null && normEnc != null && !extEnc.equalsIgnoreCase(normEnc)){
                        XMLReporter rep = impl.rep;
                        if(rep != null)
                           rep.report("Inconsistent encoding", "xml declaration", this, loc());
                     }
                  }
               }
            }else if(c == 0xEF)
               throw new XMLStreamException("First char 0xEF not valid in xml document");
         }
         impl.enc = normEnc;
         impl.Code(mXmlVer, fnd, stand);
         return new ReaderScanner(impl, r, Code, offset, inLen);
      }catch(IOException ioe){
         throw new XMLStreamException(ioe);
      }finally{
         impl.setCB1(mKW);
      }
   }

   void bck(){ --offset; }

   int nxt() throws IOException, XMLStreamException{ return offset < inLen ? Code[offset++] : Code(); }

   int afterWs() throws IOException, XMLStreamException{
      while(true){
         char c = offset < inLen ? Code[offset++] : Code();
         if(c > 0x20)
            return c;
         if(c == '\r' || c == '\n'){
            if(c == '\r' && (offset < inLen ? Code[offset++] : Code()) != '\n')
               --offset;
            ++inRow;
            inRowOff = offset;
         }else if(c == 0)
            thNull();
      }
   }

   int isKW(String kw) throws IOException, XMLStreamException{
      for(int ptr = 1, len = kw.length(); ptr < len; ++ptr){
         char c = offset < inLen ? Code[offset++] : Code();
         if(c == 0)
            thNull();
         if(c != kw.charAt(ptr))
            return c;
      }
      return 0;
   }

   int qVal(char[] kw, int quoteChar) throws IOException, XMLStreamException{
      int i = 0, len = kw.length;
      while(true){
         char c = offset < inLen ? Code[offset++] : Code();
         if(c == '\r' || c == '\n'){
            if(c == '\r' && (offset < inLen ? Code[offset++] : Code()) != '\n')
               --offset;
            ++inRow;
            inRowOff = offset;
         }else if(c == 0)
            thNull();
         if(c == quoteChar)
            return i < len ? i : -1;
         if(i < len)
            kw[i++] = c;
      }
   }

   Location loc(){ return new LocImpl(impl.pubId, impl.sysId, offset - inRowOff, inRow, proc + offset); }

   static final String Code(String enc){
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
            if(Code(enc, "ASCII", off, len, false))
               return ASCII;
         case 'c':
            break;
         case 'e':
            if(Code(enc, "EBCDIC", off, len, true))
               return "EBCDIC";
            break;
         case 'i':
            if(Code(enc, LAT1, off, len, false) || Code(enc, "ISO-Latin1", off, len, false))
               return LAT1;
            if(Code(enc, "ISO-10646", off, len, true)){
               if(Code(enc, "UCS-Basic", off = enc.indexOf("10646") + 5, len, false))
                  return ASCII;
               if(Code(enc, "Unicode-Latin1", off, len, false))
                  return LAT1;
               if(Code(enc, "UCS-2", off, len, false))
                  return UTF16;
               if(Code(enc, "UCS-4", off, len, false))
                  return UTF32;
               if(Code(enc, "UTF-1", off, len, false) || Code(enc, "J-1", off, len, false) || Code(enc, ASCII, off, len, false))
                  return ASCII;
            }
            break;
         case 'j':
            if(Code(enc, "JIS_Encoding", off, len, false))
               return "Shift_JIS";
            break;
         case 's':
            if(Code(enc, "Shift_JIS", off, len, false))
               return "Shift_JIS";
            break;
         case 'u':
            if(len > off + 1)
               switch(enc.charAt(off + 1) | 0x20){ // to lowercase
                  case 'c':
                     if(Code(enc, "UCS-2", off, len, false))
                        return UTF16;
                     if(Code(enc, "UCS-4", off, len, false))
                        return UTF32;
                     break;
                  case 'n':
                     if(Code(enc, "Unicode", off, len, false))
                        return UTF16;
                     if(Code(enc, "UnicodeAscii", off, len, false))
                        return LAT1;
                     if(Code(enc, "UnicodeAscii", off, len, false))
                        return ASCII;
                     break;
                  case 's':
                     if(Code(enc, ASCII, off, len, false))
                        return ASCII;
                     break;
                  case 't': 
                     if(Code(enc, UTF8, off, len, false))
                        return UTF8;
                     if(Code(enc, UTF16B, off, len, false))
                        return UTF16B;
                     if(Code(enc, UTF16L, off, len, false))
                        return UTF16L;
                     if(Code(enc, UTF16, off, len, false))
                        return UTF16;
                     if(Code(enc, UTF32B, off, len, false))
                        return UTF32B;
                     if(Code(enc, UTF32L, off, len, false))
                        return UTF32L;
                     if(Code(enc, UTF32, off, len, false))
                        return UTF32;
                     if(Code(enc, "UTF", off, len, false))
                        return UTF16;
            }
      }
      return enc;
   }

   private static final boolean Code(String enc, String equ, int i1, int len1, boolean starts){
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

   private final char Code() throws IOException, XMLStreamException{
      if(offset >= inLen){
         proc += inLen;
         inRowOff -= inLen;
         offset = 0;
         if(r == null || (inLen = r.read(Code, 0, 4096)) < 1)
            throw new XMLStreamException("Unexpected end-of-input", loc());
      }
      return Code[offset++];
   }

   final void thNull() throws XMLStreamException{ throw new XMLStreamException("Null in InputStream", loc()); }

   final void thUnxp(int ch, String msg) throws XMLStreamException{
      throw new XMLStreamException(new StrB(26 + msg.length()).a("Unexpected char ").apos(ch).a(msg).toString(), loc());
   }

   private final void thPsAttr(String name, String val1, String val2) throws XMLStreamException{
      StrB sb = new StrB(56 + name.length()).a("Missing XML pseudo-attribute '").a(name).a('\'');
      if(val1 != null)
         sb.a(", expected '").a(val1).a("' or '").a(val2).a('\'');
      throw new XMLStreamException(sb.toString(), loc());
   }
}
