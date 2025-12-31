// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025
package com.celerxml;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Reader;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

final class ByteSrc extends InputSrc{

   private final InputStream is;
   private final byte[] bBuf;
   private int bytesXChr;
   private boolean bigEnd;

   ByteSrc(InputFactoryImpl impl, InputStream is){
      super(impl);
      this.is = is;
      bBuf = impl.getBB();
   }

   @Override
   final XmlScanner wrap() throws XMLStreamException{
      try{
         final byte[] lbuf = bBuf;
         String normEnc = null;
         bigEnd = true;
         if(load(4)){
bomblock:   do{
               int quartet = lbuf[offset] << 24 | (lbuf[offset + 1] & 0xFF) << 16 | (lbuf[offset + 2] & 0xFF) << 8 | lbuf[offset + 3] & 0xFF;
               switch(quartet){
                  case 0xFFFE0000:
                     bigEnd = false;
                  case 0x0000FEFF:
                     offset += 4;
                     bytesXChr = 4;
                     break bomblock;
                  case 0x0000FFFE:
                  case 0xFEFF0000:
                     throw new XMLStreamException("Unsupported endianness");
               }
               int msw = quartet >>> 16;
               if(msw == 0xFEFF){
                  offset += 2;
                  bytesXChr = 2;
                  break;
               }
               if(msw == 0xFFFE){
                  offset += 2;
                  bytesXChr = 2;
                  bigEnd = false;
                  break;
               }
               if((quartet >>> 8) == 0xEFBBBF){
                  offset += 3;
                  bytesXChr = 1;
                  break;
               }
               switch(quartet){
                  case 0x3C000000:
                     bigEnd = false;
                  case 0x0000003C:
                     bytesXChr = 4;
                     break bomblock;
                  case 0x3C003F00:
                     bigEnd = false;
                  case 0x003C003F:
                     bytesXChr = 2;
                     break bomblock;
                  case 0x3C3F786D:
                     bytesXChr = 1;
                     break bomblock;
                  case 0x00003C00:
                  case 0x003C0000:
                     throw new XMLStreamException("Unsupported endianness");
                  case 0x4C6FA794: // EBCDIC
                     throw new XMLStreamException("Unsupported encoding");
               }
            }while(false);
            inRowOff = offset;
         }
         boolean bSzFound;
         if(!(bSzFound = bytesXChr > 0))
            bytesXChr = 1;
         boolean hasDecl = false;
         if(load(6 * bytesXChr))
            if(bytesXChr == 1){
               if(lbuf[offset] == '<' && lbuf[offset + 1] == '?' && lbuf[offset + 2] == 'x' && lbuf[offset + 3] == 'm' && lbuf[offset + 4] == 'l' && (lbuf[offset + 5] & 0xFF) <= 0x20){
                  offset += 6;
                  hasDecl = true;
               }
            }else{
               int start = offset;
               if(nxtMB() == '<' && nxtMB() == '?' && nxtMB() == 'x' && nxtMB() == 'm' && nxtMB() == 'l' && nxtMB() <= 0x20)
                  hasDecl = true;
               else
                  offset = start;
            }
         if(hasDecl){
            readDecl();
            if((normEnc = foundEnc) != null){
               normEnc = normalize(normEnc);
               if(bSzFound){
                  int bpc = bytesXChr;
                  boolean isBig = bigEnd;
                  if(normEnc == UTF8 || normEnc == LATN1 || normEnc == ASCII)
                     bpc = 1;
                  else if(normEnc == UTF16)
                     bpc = 2;
                  else if(normEnc == UTF16L){
                     bpc = 2;
                     isBig = false;
                  }else if(normEnc == UTF16B){
                     bpc = 2;
                     isBig = true;
                  }else if(normEnc == UTF32)
                     bpc = 4;
                  else if(normEnc == UTF32L){
                     bpc = 4;
                     isBig = false;
                  }else if(normEnc == UTF32B){
                     bpc = 4;
                     isBig = true;
                  }
                  if(bpc != bytesXChr)
                     throw new XMLStreamException(new StrB(normEnc.length() + 63).a("Declared '").a(normEnc).a("' uses ").apos((char)('0' + bpc)).a(
                        " bytes/char; differs from the actual encoding").toString(), getLocation());
                  if(isBig != bigEnd)
                     throw new XMLStreamException(new StrB(normEnc.length() + 63).a("Declared '").a(normEnc).a(isBig ? "' is big" : "' is little").a(
                        " endian; differs from the actual ordering").toString(), getLocation());
               }
            }
         }
         if(normEnc == null)
            if(bytesXChr == 2)
               normEnc = bigEnd ? UTF16B : UTF16L;
            else if(bytesXChr == 4)
               normEnc = bigEnd ? UTF32B : UTF32L;
            else
               normEnc = UTF8;
         impl.enc = normEnc;
         impl.setXmlDeclInfo(mXmlVer, foundEnc, stand);
         if(normEnc == UTF8 || normEnc == LATN1 || normEnc == ASCII)
            return new Utf8Scanner(impl, is, lbuf, offset, inLen);
         Reader rdr;
         if(normEnc.startsWith(UTF32))
            rdr = new Utf32Reader(impl, is, lbuf, offset, inLen, bigEnd);
         else{
            if(normEnc == UTF16)
               normEnc = bigEnd ? UTF16B : UTF16L;
            rdr = new InputStreamReader(offset < inLen ? new Wrap(impl, is, lbuf, offset, inLen) : is, normEnc);
         }
         return new ReaderScanner(impl, rdr, impl.getCB3(), 0, 0);
      }catch(IOException ioe){
         throw new XMLStreamException(ioe);
      }finally{
         impl.setCB1(mKW);
      }
   }

   private final boolean load(int min) throws IOException{
      int count, got = inLen - offset;
      while(got < min){
         if((count = is == null ? -1 : is.read(bBuf, inLen, 4096 - inLen)) < 1)
            return false;
         inLen += count;
         got += count;
      }
      return true;
   }

   @Override
   final void pushback(){ offset -= bytesXChr; }

   @Override
   final int getNxt() throws IOException, XMLStreamException{
      return bytesXChr > 1 ? nxtMB() : ((offset < inLen) ? bBuf[offset++] : nxtB()) & 0xFF;
   }

   @Override
   final int nxtAfterWs() throws IOException, XMLStreamException{
      int c, count = 0;
      if(bytesXChr > 1)
         while(true){
            if((c = nxtMB()) > 0x20){
               offset -= bytesXChr;
               break;
            }
            if(c == '\r' || c == '\n'){
               if(c == '\r' && nxtMB() != '\n')
                  offset -= bytesXChr;
               ++inRow;
               inRowOff = offset;
            }else if(c == 0)
               throwNull();
            ++count;
         }
      else
         while(true){
            byte b = offset < inLen ? bBuf[offset++] : nxtB();
            if((b & 0xFF) > 0x20){
               --offset;
               break;
            }
            if(b == (byte)'\r' || b == (byte)'\n'){
               if(b == (byte)'\r' && (offset < inLen ? bBuf[offset++] : nxtB()) != (byte)'\n')
                  --offset;
               ++inRow;
               inRowOff = offset;
            }else if(b == 0)
               throwNull();
            ++count;
         }
      return bytesXChr > 1 ? nxtMB() : (offset < inLen ? bBuf[offset++] : nxtB()) & 0xFF;
   }

   @Override
   final int isKW(String kw) throws IOException, XMLStreamException{
      int c, len = kw.length();
      if(bytesXChr > 1){
         for(int ptr = 1; ptr < len; ++ptr){
            if((c = nxtMB()) == 0)
               throwNull();
            if(c != kw.charAt(ptr))
               return c;
         }
         return 0;
      }
      for(int ptr = 1; ptr < len; ++ptr){
         byte b = offset < inLen ? bBuf[offset++] : nxtB();
         if(b == 0)
            throwNull();
         if((b & 0xFF) != kw.charAt(ptr))
            return b & 0xFF;
      }
      return 0;
   }

   @Override
   final int qVal(char[] kw, int quote) throws IOException, XMLStreamException{
      int c, i = 0, len = kw.length;
      final boolean mb = bytesXChr > 1;
      while(i < len){
         if(mb){
            if((c = nxtMB()) ==  '\r' || c == '\n'){
               if(c == '\r' && nxtMB() != '\n')
                  offset -= bytesXChr;
               ++inRow;
               inRowOff = offset;
               c = '\n';
            }
         }else{
            byte b = offset < inLen ? bBuf[offset++] : nxtB();
            if(b == 0)
               throwNull();
            if(b == (byte)'\r' || b == (byte)'\n'){
               if(b == (byte)'\r' && (offset < inLen ? bBuf[offset++] : nxtB()) != (byte)'\n')
                  --offset;
               ++inRow;
               inRowOff = offset;
               b = (byte)'\n';
            }
            c = b & 0xFF;
         }
         if(c == quote)
            return i;
         kw[i++] = (char)c;
      }
      return -1;
   }

   @Override
   final Location getLocation(){ return new LocImpl(impl.pubId, impl.sysId, (offset - inRowOff) / bytesXChr, inRow, (processed + offset) / bytesXChr); }

   private final byte nxtB() throws IOException, XMLStreamException{
      if(offset >= inLen){
         processed += inLen;
         inRowOff -= inLen;
         offset = 0;
         if((inLen = is == null ? -1 : is.read(bBuf, 0, 4096)) < 1)
            throw new XMLStreamException("Unexpected end-of-input", getLocation());
      }
      return bBuf[offset++];
   }

   private final int nxtMB() throws IOException, XMLStreamException{
      byte b1 = offset < inLen ? bBuf[offset++] : nxtB(), b2 = offset < inLen ? bBuf[offset++] : nxtB();
      int c;
      if(bytesXChr == 2)
         c = bigEnd ? (b1 & 0xFF) << 8 | b2 & 0xFF : (b2 & 0xFF) << 8 | b1 & 0xFF;
      else{
         byte b3 = offset < inLen ? bBuf[offset++] : nxtB(), b4 = offset < inLen ? bBuf[offset++] : nxtB();
         c = bigEnd ? b1 << 24 | (b2 & 0xFF) << 16 | (b3 & 0xFF) << 8 | b4 & 0xFF : b4 << 24 | (b3 & 0xFF) << 16 | (b2 & 0xFF) << 8 | b1 & 0xFF;
      }
      if(c == 0)
         throwNull();
      return c;
   }
}
