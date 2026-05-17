// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025 - 2026
package com.celerxml;

import java.io.InputStream;
import java.io.IOException;
import java.io.Reader;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

final class bSrc extends InSrc{

   private final InputStream is;
   private final byte[] bBuf;
   private int Code;
   private boolean big;

   bSrc(InputFactoryImpl impl, InputStream is){
      super(impl);
      this.is = is;
      bBuf = impl.getBB();
   }

   final XmlScanner w() throws XMLStreamException{
      try{
         final byte[] lbuf = bBuf;
         String normEnc = null;
         big = true;
         int yy, xx = 0;
bom:     if(Code(4)){
            switch(yy = lbuf[0] << 24 | (lbuf[1] & 0xFF) << 16 | (lbuf[2] & 0xFF) << 8 | lbuf[3] & 0xFF){
               case 0xFFFE0000:
                  big = false;
               case 0x0000FEFF:
                  Code = xx = 4;
                  break bom;
               case 0x3C000000: // '<'000000
                  big = false;
               case 0x0000003C: // 000000'<'
                  Code = 4;
                  break bom;
               case 0x3C003F00: // '<'00'?'00
                  big = false;
               case 0x003C003F: // 00'<'00'?'
                  Code = 2;
                  break bom;
               case 0x3C3F786D: // '<?xm'
                  Code = 1;
                  break bom;
               case 0x0000FFFE:
               case 0xFEFF0000:
               case 0x00003C00: // 0000'<'00
               case 0x003C0000: // 00'<'0000
                  throw new XMLStreamException("Unsupported endianness");
               case 0x4C6FA794: // EBCDIC
                  throw new XMLStreamException("Unsupported encoding");
            }
            int msw = yy >>> 16;
            if(msw == 0xFEFF || msw == 0xFFFE){
               Code = xx = 2;
               if(msw == 0xFFFE)
                  big = false;
            }else if((yy >>> 8) == 0xEFBBBF){ // UTF-8
               xx = 3;
               Code = 1;
            }
         }
         inRowOff = offset = xx;
         boolean ff;
         if(!(ff = Code > 0))
            Code = 1;
         boolean hasDecl = false;
         if(Code(6 * Code))
            if(Code == 1){
               if(lbuf[xx] == '<' && lbuf[xx + 1] == '?' && lbuf[xx + 2] == 'x' && lbuf[xx + 3] == 'm' && lbuf[xx + 4] == 'l' && (lbuf[xx + 5] & 0xFF) <= 0x20){
                  offset += 6;
                  hasDecl = true;
               }
            }else if(Code() == '<' && Code() == '?' && Code() == 'x' && Code() == 'm' && Code() == 'l' && Code() <= 0x20)
               hasDecl = true;
            else
               offset = xx;
         if(hasDecl){
            readDecl();
            if((normEnc = fnd) != null){
               normEnc = Code(normEnc);
               if(ff){
                  yy = Code;
                  ff = big;
                  if(normEnc == UTF8 || normEnc == LAT1 || normEnc == ASCII)
                     yy = 1;
                  else if(normEnc == UTF16)
                     yy = 2;
                  else if(normEnc == UTF16L){
                     yy = 2;
                     ff = false;
                  }else if(normEnc == UTF16B){
                     yy = 2;
                     ff = true;
                  }else if(normEnc == UTF32)
                     yy = 4;
                  else if(normEnc == UTF32L){
                     yy = 4;
                     ff = false;
                  }else if(normEnc == UTF32B){
                     yy = 4;
                     ff = true;
                  }
                  if(yy != Code || ff != big)
                     throw new XMLStreamException(new StrB(normEnc.length() + 90).a("Declared '").a(normEnc).a(ff ? "' is big" : "' is little").a(" endian, uses ").a(
                        (char)('0' + yy)).a(" bytes/char; differs from the actual ordering/encoding").toString(), loc());
               }
            }
         }
         if(normEnc == null)
            if(Code == 2)
               normEnc = big ? UTF16B : UTF16L;
            else if(Code == 4)
               normEnc = big ? UTF32B : UTF32L;
            else
               normEnc = UTF8;
         impl.enc = normEnc;
         impl.Code(mXmlVer, fnd, stand);
         if(normEnc == UTF8 || normEnc == LAT1 || normEnc == ASCII)
            return new Utf8Scanner(impl, is, lbuf, offset, inLen);
         Reader rdr;
         if(normEnc.startsWith(UTF32))
            rdr = new Utf32Reader(impl, is, lbuf, offset, inLen, big);
         else{
            if(normEnc == UTF16)
               normEnc = big ? UTF16B : UTF16L;
            rdr = new java.io.InputStreamReader(offset < inLen ? new Wrap(impl, is, lbuf, offset, inLen) : is, normEnc);
         }
         return new ReaderScanner(impl, rdr, impl.getCB3(), 0, 0);
      }catch(IOException ioe){
         throw new XMLStreamException(ioe);
      }finally{
         impl.setCB1(mKW);
      }
   }

   final void bck(){ offset -= Code; }

   final int nxt() throws IOException, XMLStreamException{
      return Code > 1 ? Code() : (offset < inLen ? bBuf[offset++] : nxtB()) & 0xFF;
   }

   final int afterWs() throws IOException, XMLStreamException{
      if(Code > 1){
         int c;
         while((c = Code()) <= 0x20)
            if(c == '\r' || c == '\n'){
               if(c == '\r' && Code() != '\n')
                  offset -= Code;
               ++inRow;
               inRowOff = offset;
            }
         offset -= Code;
         return Code();
      }
      byte b;
      while(((b = offset < inLen ? bBuf[offset++] : nxtB()) & 0xFF) <= 0x20){
         if(b == (byte)'\r' || b == (byte)'\n'){
            if(b == (byte)'\r' && (offset < inLen ? bBuf[offset++] : nxtB()) != (byte)'\n')
               --offset;
            ++inRow;
            inRowOff = offset;
         }else if(b == 0)
            thNull();
      }
      return (offset <= inLen ? bBuf[offset - 1] : nxtB()) & 0xFF;
   }

   final int isKW(String kw, int len) throws IOException, XMLStreamException{
      int c, i = 1;
      final boolean mb = Code > 1;
      while(i < len){
         if(mb)
            c = Code();
         else if((c = (offset < inLen ? bBuf[offset++] : nxtB()) & 0xFF) == 0)
            thNull();
         if(c != kw.charAt(i++))
            return c;
      }
      return 0;
   }

   final int qVal(char[] kw, int quote) throws IOException, XMLStreamException{
      int c, i = 0, len = kw.length;
      final boolean mb = Code > 1;
      while(i < len){
         if(mb){
            if((c = Code()) == '\r' || c == '\n'){
               if(c == '\r' && Code() != '\n')
                  offset -= Code;
               ++inRow;
               inRowOff = offset;
               c = '\n';
            }
         }else{
            if((c = (offset < inLen ? bBuf[offset++] : nxtB()) & 0xFF) == 0)
               thNull();
            if(c == '\r' || c == '\n'){
               if(c == '\r' && (offset < inLen ? bBuf[offset++] : nxtB()) != (byte)'\n')
                  --offset;
               ++inRow;
               inRowOff = offset;
               c = '\n';
            }
         }
         if(c == quote)
            return i;
         kw[i++] = (char)c;
      }
      return -1;
   }

   final Location loc(){ return new LocImpl(impl.pubId, impl.sysId, (offset - inRowOff) / Code, inRow, (proc + offset) / Code); }

   private final byte nxtB() throws IOException, XMLStreamException{
      proc += inLen;
      inRowOff -= inLen;
      offset = 0;
      if(is == null || (inLen = is.read(bBuf, 0, 4096)) < 1)
         throw new XMLStreamException("Unexpected EOI", loc());
      return bBuf[offset++];
   }

   private final boolean Code(int min) throws IOException{
      int count, got = inLen - offset;
      while(got < min){
         if(is == null || (count = is.read(bBuf, inLen, 4096 - inLen)) < 1)
            return false;
         inLen += count;
         got += count;
      }
      return true;
   }

   private final int Code() throws IOException, XMLStreamException{
      int b1 = (offset < inLen ? bBuf[offset++] : nxtB()) & 0xFF, b2 = (offset < inLen ? bBuf[offset++] : nxtB()) & 0xFF;
      if(Code == 2)
         b1 = big ? b1 << 8 | b2 : b2 << 8 | b1;
      else{
         int b3 = (offset < inLen ? bBuf[offset++] : nxtB()) & 0xFF, b4 = (offset < inLen ? bBuf[offset++] : nxtB()) & 0xFF;
         b1 = big ? b1 << 24 | b2 << 16 | b3 << 8 | b4 : b4 << 24 | b3 << 16 | b2 << 8 | b1;
      }
      if(b1 == 0)
         thNull();
      return b1;
   }
}
