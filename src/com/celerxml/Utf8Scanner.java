// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025 - 2026
package com.celerxml;

import java.io.InputStream;
import java.io.IOException;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.Location;

public final class Utf8Scanner extends XmlScanner{

   private final Chr chrT;
   private InputStream in;
   private byte[] inBuf;
   private int inPtr, end, cTmp;
   private final bPN syms;
   private Node curr;
   private int[] qBuf = new int[32];

   public Utf8Scanner(InputFactoryImpl impl, InputStream in, byte[] inBuf, int inPtr, int end){
      super(impl);
      this.in = in;
      this.inBuf = inBuf;
      this.inPtr = inPtr;
      this.end = end;
      chrT = impl.Code();
      syms = impl.getSyms();
   }

   private final void markLF(){
      rowOff = inPtr;
      ++currRow;
   }

   final boolean more() throws XMLStreamException{
      bOrC += end;
      rowOff -= end;
      inPtr = 0;
      if(in == null){
         end = 0;
         return false;
      }
      try{
         int count = in.read(inBuf, 0, 4096);
         if(count < 1){
            end = 0;
            if(count == 0)
               thInErr("InputStream returned 0");
            return false;
         }
         end = count;
         return true;
      }catch(IOException ioe){
         throw new XMLStreamException(ioe);
      }
   }

   private final void setStartLoc(){
      iniRawOff = bOrC + inPtr;
      startRow = currRow;
      startCol = inPtr - rowOff;
   }

   private final PN Code(int hash, int[] quads, int qlen, int lastQuadBytes) throws XMLStreamException{
      int lastQuad = 0, byteLen = (qlen << 2) - 4 + lastQuadBytes;
      if(lastQuadBytes < 4){
         lastQuad = quads[qlen - 1];
         quads[qlen - 1] = lastQuad << ((4 - lastQuadBytes) << 3);
      }
      int ch = quads[0] >>> 24, ix = 1, needed = 1, cix = 0;
      boolean ok;
      char[] cbuf = nameBuf;
      final byte[] TYPES = chrT.NAM;
      switch(TYPES[ch]){
         case 0:  // NAME_NONE
         case 1:  // NAME_COLON
         case 2:  // NAME_NONFIRST
         case 4:  // MULTIBYTE_N
            ok = false;
            break;
         case 3:  // NAME_ANY
            ok = true;
            break;
         default:
            if((ch & 0xE0) == 0xC0)        // 2 bytes
               ch &= 0x1F;
            else if((ch & 0xF0) == 0xE0){  // 3 bytes
               ch &= 0xF;
               needed = 2;
            }else if((ch & 0xF8) == 0xF0){ // 4 bytes
               ch &= 7;
               needed = 3;
            }else                          // 5+ bytes not valid
               badUTF(ch);
            if((ix += needed) > byteLen)
               thInErr(EOI);
            int q = quads[0], ch2 = q >> 16 & 0xFF;
            if((ch2 & 0xC0) != 0x80 || (needed > 1 && (((ch2 = q >> 8 & 0xFF) & 0xC0) != 0x80 || (needed > 2 && ((ch2 = q & 0xFF) & 0xC0) != 0x80))))
               badUTF(ch2);
            ok = Chr.is10NS(ch = ch << 6 | ch2 & 0x3F);
            if(needed > 2){
               cbuf[cix++] = (char)(0xD800 + ((ch -= 0x10000) >> 10));
               ch = 0xDC00 | ch & 0x3FF;
            }
      }
      if(!ok)
         thInvNCh(ch);
      cbuf[cix++] = (char)ch;
      int last_colon = -1;
      while(ix < byteLen){
         switch(TYPES[ch = (quads[ix >> 2] >> ((ix++ & 3 ^ 3) << 3)) & 0xFF]){
            case 0:  // NAME_NONE
            case 4:  // MULTIBYTE_N
               ok = false;
               break;
            case 1:  // NAME_COLON
               if(last_colon >= 0)
                  thInErr("Multiple ':'");
               last_colon = cix;
            case 2:  // NAME_NONFIRST
            case 3:  // NAME_ANY
               ok = true;
               break;
            default:
               if((ch & 0xE0) == 0xC0){       // 2 bytes
                  ch &= 0x1F;
                  needed = 1;
               }else if((ch & 0xF0) == 0xE0){ // 3 bytes
                  ch &= 0xF;
                  needed = 2;
               }else if((ch & 0xF8) == 0xF0){ // 4 bytes
                  ch &= 7;
                  needed = 3;
               }else                          // 5+ bytes not valid
                  badUTF(ch);
               if(ix + needed > byteLen)
                  thInErr(EOI);
               int ch2 = quads[ix >> 2] >> ((ix++ & 3 ^ 3) << 3);
               if((ch2 & 0xC0) != 0x80)
                  badUTF(ch2);
               if(needed > 1){
                  if(((ch2 = quads[ix >> 2] >> ((ix++ & 3 ^ 3) << 3)) & 0xC0) != 0x80)
                     badUTF(ch2);
                  if(needed > 2){
                     ch2 = quads[ix >> 2] >> ((ix & 3 ^ 3) << 3);
                     ++ix;
                     if((ch2 & 0xC0) != 0x80)
                        badUTF(ch2);
                  }
               }
               ok = Chr.is10N(ch = ch << 6 | ch2 & 0x3F);
               if(needed > 2){
                  if(cix >= cbuf.length)
                     nameBuf = cbuf = xpand(cbuf);
                  cbuf[cix++] = (char)(0xD800 + ((ch -= 0x10000) >> 10));
                  ch = 0xDC00 | ch & 0x3FF;
               }
         }
         if(!ok)
            thInvNCh(ch);
         if(cix >= cbuf.length)
            nameBuf = cbuf = xpand(cbuf);
         cbuf[cix++] = (char)ch;
      }
      String baseName = new String(cbuf, 0, cix);
      if(lastQuadBytes < 4)
         quads[qlen - 1] = lastQuad;
      return syms.add(hash, baseName, last_colon, quads, qlen);
   }

   public final int nxtFromProlog(boolean isProlog) throws XMLStreamException{
      if(incompl)
         skipTok();
      setStartLoc();
      while(true){
         if(inPtr >= end && !more()){
            setStartLoc();        
            return -1;
         }
         int c = inBuf[inPtr++] & 0xFF;
         switch(c){
            case '<':
               if(inPtr >= end)
                  assertMore();
               byte b = inBuf[inPtr++];
               if(b == (byte)'!')
                  return doPrologDecl(isProlog);
               if(b == (byte)'?')
                  return doPIStart();
               if(b == (byte)'/' || !isProlog)
                  thRoot(isProlog, b);
               return startElem(b);
            case 0x20:
            case '\t':
               continue;
            case '\r':
               if(inPtr >= end && !more()){
                  markLF();
                  setStartLoc();        
                  return -1;
               }
               if(inBuf[inPtr] == (byte)'\n')
                  ++inPtr;
            case '\n':
               markLF();
               continue;
            default:
               thPlogUnxpCh(isProlog, decChr((byte)c));
         }
      }
   }

   public final int nxtFromTree() throws XMLStreamException{
      if(incompl){
         if(skipTok()){
            reset();
            return currToken = 9; // ENTITY_REFERENCE
         }
      }else if(currToken == 1){ // START_ELEMENT
         if(empty){
            --depth;
            return currToken = 2; // END_ELEMENT
         }
      }else if(currToken == 2){ // END_ELEMENT
         curr = curr.nxt;
         while(lastNs != null && lastNs.lvl >= depth)
            lastNs =lastNs.Code();
      }else if(pending){
         pending = false;
         reset();
         return currToken = 9; // ENTITY_REFERENCE
      }
      setStartLoc();        
      if(inPtr >= end && !more()){
         setStartLoc();        
         return -1;
      }
      byte b = inBuf[inPtr];
      if(b == (byte)'<'){
         if((b = ++inPtr < end ? inBuf[inPtr++] : loadOne()) == (byte)'!')
             return commOrCdataStart();
         if(b == (byte)'?')
             return doPIStart();
         if(b == (byte)'/')
             return doEndE();
         return startElem(b);
      }
      if(b == (byte)'&'){
         ++inPtr;
         int i = entInTxt();
         if(i == 0)
            return currToken = 9; // ENTITY_REFERENCE
         cTmp = -i;
      }else
         cTmp = (int)b & 0xFF;
      if(lazy)
         incompl = true;
      else
         endC();
      return currToken = 4; // CHARACTERS
   }

   private final int doPrologDecl(boolean isProlog) throws XMLStreamException{
      if(inPtr >= end)
         assertMore();
      byte b = inBuf[inPtr++];
      if(b == (byte)'-'){
         if(inPtr >= end)
            assertMore();
         if((b = inBuf[inPtr++]) == (byte)'-'){
            if(lazy)
               incompl = true;
            else
               endComm();
            return currToken = 5; // COMMENT
         }
      }else if(b == (byte)'D' && isProlog){
         doDtdStart();
         if(!lazy && incompl){
            endDTD();
            incompl = false;
         }
         return 11; // DTD
      }
      incompl = true;
      currToken = 4; // CHARACTERS
      thPlogUnxpCh(isProlog, decChr(b));
      return 0;
   }

   private final int doDtdStart() throws XMLStreamException{
      Code("DOCTYPE");
      tokName = parsePN(Code(true));
      byte q, b = Code(false);
      if(b == (byte)'P'){
         Code("PUBLIC");
         q = Code(true);
         char[] outputBuffer = nameBuf;
         int outPtr = 0;
         final byte[] TYPES = Chr.Code;
         boolean addSpace = false;
         while(true){
            if(inPtr >= end)
               assertMore();
            if((b = inBuf[inPtr++]) == q)
               break;
            int c = (int)b & 0xFF;
            if(TYPES[c] != 1) // PUBID_OK
               thUnxp(c, " in public ID");
            if(c <= 0x20){
               addSpace = true;
               continue;
            }
            if(addSpace){
               if(outPtr >= outputBuffer.length)
                  nameBuf = outputBuffer = xpand(outputBuffer);
               outputBuffer[outPtr++] = ' ';
               addSpace = false;
            }
            if(outPtr >= outputBuffer.length)
               nameBuf = outputBuffer = xpand(outputBuffer);
            outputBuffer[outPtr++] = (char)c;
         }
         dtdPubId = new String(outputBuffer, 0, outPtr);
         dtdSysId = Code(Code(true));
         b = Code(false);
      }else if(b == (byte)'S'){
         Code("SYSTEM");
         dtdPubId = null;
         dtdSysId = Code(Code(true));
         b = Code(false);
      }else
         dtdPubId = dtdSysId = null;
      if(b == (byte)'>'){
         incompl = false;
         return currToken = 11; // DTD
      }
      if(b != (byte)'[')
         thUnxp(decChr(b), ", expected '[' or '>'");
      incompl = true;
      return currToken = 11; // DTD
   }

   private final int commOrCdataStart() throws XMLStreamException{
      if(inPtr >= end)
         assertMore();
      byte b = inBuf[inPtr++];
      if(b == (byte)'-'){
         if(inPtr >= end)
            assertMore();
         if(inBuf[inPtr++] != (byte)'-')
            thInErr("Expected '-'");
         if(lazy)
            incompl = true;
         else
            endComm();
         return currToken = 5; // COMMENT
      }
      if(b == (byte)'['){
         currToken = 12; // CDATA
         for(int i = 0; i < 6; ++i){
            if(inPtr >= end)
               assertMore();
            if(inBuf[inPtr++] != (byte)CDATA.charAt(i))
               thInErr("Expected '[CDATA['");
         }
         if(lazy)
            incompl = true;
         else
            endCData();
         return 12; // CDATA
      }
      thInErr("Expected '-' or '[CDATA['");
      return 0;
   }

   private final int doPIStart() throws XMLStreamException{
      currToken = 3; // PROCESSING_INSTRUCTION
      if(inPtr >= end)
         assertMore();
      byte b = inBuf[inPtr++];
      tokName = parsePN(b);
      String ln = tokName.ln;
      if(ln.length() == 3 && ln.equalsIgnoreCase("xml") && tokName.pfx == null)
         thInErr("Target 'xml' reserved");
      if(inPtr >= end)
         assertMore();
      int c = (int)inBuf[inPtr++] & 0xFF;
      if(c <= 0x20){
         while(true){
            if(c == '\n')
               markLF();
            else if(c == '\r'){
               if(inPtr >= end)
                  assertMore();
               if(inBuf[inPtr] == (byte)'\n')
                  ++inPtr;
               markLF();
            }else if(c != 0x20 && c != '\t')
               thC(c);
            if(inPtr >= end)
               assertMore();
            if((c = (int)inBuf[inPtr] & 0xFF) > 0x20)
               break;
            ++inPtr;
         }
         if(lazy)
            incompl = true;
         else
            endPI();
      }else{
         if(c != '?')
            thNoPISp(decChr((byte)c));
         if(inPtr >= end)
            assertMore();
         if((b = inBuf[inPtr++]) != (byte)'>')
            thNoPISp(decChr(b));
         reset();
         incompl = false;
      }
      return 3; // PROCESSING_INSTRUCTION
   }

   private final int chrEnt() throws XMLStreamException{
      if(inPtr >= end)
         assertMore();
      byte b = inBuf[inPtr++];
      int c, value = 0;
      if(b == (byte)'x')
         while(true){
            if(inPtr >= end)
               assertMore();
            if((b = inBuf[inPtr++]) == (byte)';')
               break;
            if((c = (((int)b | 0x20) - 0x30)) > 9) // to lowercase
               c -= 0x27;
            if(c < 0 || c > 0xF)
               thUnxp(decChr(b), ", not a hex digit [0-9a-fA-F]");
            value = value << 4 | c;
         }
      else
         while(b != (byte)';'){
            if(b < (byte)'0' || b > (byte)'9')
               thUnxp(decChr(b), ", not a decimal number");
            value = value * 10 + b - '0';
            if(inPtr >= end)
               assertMore();
            b = inBuf[inPtr++];
         }
      if((value >= 0xD800 && value < 0xE000) || value == 0 || value == 0xFFFE || value == 0xFFFF)
         thInvC(value);
      return value;
   }

   private final int doEndE() throws XMLStreamException{
      --depth;
      currToken = 2; // END_ELEMENT
      tokName = curr.Code;
      int size = tokName.size(), ptr = inPtr;
      if(end - ptr < (size << 2) + 1)
         return doEndESlow(size);
      byte[] buf = inBuf;
      --size;
      for(int qix = 0; qix < size; ++qix)
         if((buf[ptr++] << 24 | (buf[ptr++] & 0xFF) << 16 | (buf[ptr++] & 0xFF) << 8 | buf[ptr++] & 0xFF) != tokName.Code(qix)){
            inPtr = ptr;
            thUnexpEnd(tokName.Code);
         }
      int lastQ = tokName.Code(size), q = buf[ptr++] & 0xFF;
      if(q != lastQ && (q = q << 8 | buf[ptr++] & 0xFF) != lastQ && (q = q << 8 | buf[ptr++] & 0xFF) != lastQ && (q << 8 | buf[ptr++] & 0xFF) != lastQ){
         inPtr = ptr;
         thUnexpEnd(tokName.Code);
      }
      q = inBuf[ptr] & 0xFF;
      inPtr = ptr + 1;
      while(q <= 0x20){
         if(q == '\n')
            markLF();
         else if(q == '\r'){
            byte b = inPtr < end ? inBuf[inPtr++] : loadOne();
            if(b != (byte)'\n'){
               rowOff = inPtr - 1;
               ++currRow;
               q = (int)b & 0xFF;
               continue;
            }
            markLF();
         }else if(q != 0x20 && q != '\t')
            thC(q);
         q = (int)(inPtr < end ? inBuf[inPtr++] : loadOne()) & 0xFF;
      }
      if(q != '>')
         thUnxp(decChr((byte)q), ", not space or closing '>'");
      return 2; // END_ELEMENT
   }

   private final int doEndESlow(int size) throws XMLStreamException{
      --size;
      for(int qix = 0; qix < size; ++qix){
         int q = 0;
         for(int i = 0; i < 4; ++i){
            if(inPtr >= end)
               assertMore();
            q = q << 8 | inBuf[inPtr++] & 0xFF;
         }
         if(q != tokName.Code(qix))
            thUnexpEnd(tokName.Code);
      }
      int lastQ = tokName.Code(size), q = 0, i = 0;
      while(true){
         if(inPtr >= end)
            assertMore();
         if((q = q << 8 | inBuf[inPtr++] & 0xFF) == lastQ){
            if(inPtr >= end)
               assertMore();
            q = inBuf[inPtr++];
            while(q <= 0x20){
               if(q == '\n')
                  markLF();
               else if(q == '\r'){
                  byte b = inPtr < end ? inBuf[inPtr++] : loadOne();
                  if(b != (byte)'\n'){
                     rowOff = inPtr - 1;
                     ++currRow;
                     q = (int)b & 0xFF;
                     continue;
                  }
                  markLF();
               }else if(q != 0x20 && q != '\t')
                  thC(q);
               q = (int)(inPtr < end ? inBuf[inPtr++] : loadOne()) & 0xFF;
            }
            if(q != '>')
               thUnxp(decChr((byte)q), ", not space or closing '>'");
            return 2; // END_ELEMENT
         }
         if(++i > 3)
            thUnexpEnd(tokName.Code);
      }
   }

   private final PN parsePN(byte b) throws XMLStreamException{
      if(end - inPtr < 8)
         return parsePNSlow(b);
      int q = b & 0xFF;
      if(q < 'A')
         thUnxp(q, ", not a name start char");
      int i2 = (int)inBuf[inPtr++] & 0xFF;
      if(i2 < 45 || (i2 > 58 && i2 < 65) || i2 == 47)
         return findPName(q, 1);
      q = q << 8 | i2;
      if((i2 = (int)inBuf[inPtr++] & 0xFF) < 45 || (i2 > 58 && i2 < 65) || i2 == 47)
         return findPName(q, 2);
      q = q << 8 | i2;
      if((i2 = (int)inBuf[inPtr++] & 0xFF) < 45 || (i2 > 58 && i2 < 65) || i2 == 47)
         return findPName(q, 3);
      q = q << 8 | i2;
      if((i2 = (int)inBuf[inPtr++] & 0xFF) < 45 || (i2 > 58 && i2 < 65) || i2 == 47)
         return findPName(q, 4);
      int q2 = i2;
      if((i2 = (int)inBuf[inPtr++] & 0xFF) < 45 || (i2 > 58 && i2 < 65) || i2 == 47)
         return findPName(q, q2, 1);
      q2 = q2 << 8 | i2;
      if((i2 = (int)inBuf[inPtr++] & 0xFF) < 45 || (i2 > 58 && i2 < 65) || i2 == 47)
         return findPName(q, q2, 2);
      q2 = q2 << 8 | i2;
      if((i2 = (int)inBuf[inPtr++] & 0xFF) < 45 || (i2 > 58 && i2 < 65) || i2 == 47)
         return findPName(q, q2, 3);
      q2 = q2 << 8 | i2;
      if((i2 = (int)inBuf[inPtr++] & 0xFF) < 45 || (i2 > 58 && i2 < 65) || i2 == 47)
         return findPName(q, q2, 4);
      int[] quads = qBuf;
      quads[0] = q;
      quads[1] = q2;
      return parsePNLong(i2, quads);
   }

   private final PN parsePNLong(int q, int[] quads) throws XMLStreamException{
      int qix = 2;
      while(true){
         if(inPtr >= end)
            assertMore();
         int i2 = inBuf[inPtr++] & 0xFF;
         if(i2 < 45 || (i2 > 58 && i2 < 65) || i2 == 47)
            return findPName(q, quads, qix, 1);
         q = q << 8 | i2;
         if((i2 = (int)(inPtr < end ? inBuf[inPtr++] : loadOne()) & 0xFF) < 45 || (i2 > 58 && i2 < 65) || i2 == 47)
            return findPName(q, quads, qix, 2);
         q = q << 8 | i2;
         if((i2 = (int)(inPtr < end ? inBuf[inPtr++] : loadOne()) & 0xFF) < 45 || (i2 > 58 && i2 < 65) || i2 == 47)
            return findPName(q, quads, qix, 3);
         q = q << 8 | i2;
         if((i2 = (int)(inPtr < end ? inBuf[inPtr++] : loadOne()) & 0xFF) < 45 || (i2 > 58 && i2 < 65) || i2 == 47)
            return findPName(q, quads, qix, 4);
         if(qix >= quads.length)
            qBuf = quads = xpand(quads, quads.length);
         quads[qix++] = q;
         q = i2;
      }
   }

   private final PN parsePNSlow(byte b) throws XMLStreamException{
      int q = b & 0xFF;
      if(q < 'A')
         thUnxp(q, ", not a name start char");
      int[] quads = qBuf;
      int qix = 0, firstQuad = 0;
      while(true){
         if(inPtr >= end)
            assertMore();
         int i2 = inBuf[inPtr++] & 0xFF;
         if(i2 < 45 || (i2 > 58 && i2 < 65) || i2 == 47)
            return findPName(q, 1, firstQuad, qix, quads);
         q = q << 8 | i2;
         if((i2 = (int)(inPtr < end ? inBuf[inPtr++] : loadOne()) & 0xFF) < 45 || (i2 > 58 && i2 < 65) || i2 == 47)
            return findPName(q, 2, firstQuad, qix, quads);
         q = q << 8 | i2;
         if((i2 = (int)(inPtr < end ? inBuf[inPtr++] : loadOne()) & 0xFF) < 45 || (i2 > 58 && i2 < 65) || i2 == 47)
            return findPName(q, 3, firstQuad, qix, quads);
         q = q << 8 | i2;
         if((i2 = (int)(inPtr < end ? inBuf[inPtr++] : loadOne()) & 0xFF) < 45 || (i2 > 58 && i2 < 65) || i2 == 47)
            return findPName(q, 4, firstQuad, qix, quads);
         if(qix == 0)
            firstQuad = q;
         else if(qix == 1){
            quads[0] = firstQuad;
            quads[1] = q;
         }else{
            if(qix >= quads.length)
               qBuf = quads = xpand(quads, quads.length);
            quads[qix] = q;
         }
         ++qix;
         q = i2;
      }
   }

   private final PN findPName(int onlyQuad, int lastByteCount) throws XMLStreamException{
      --inPtr;
      int hash = onlyQuad * 31;
      hash ^= hash >>> 16;
      hash ^= hash >>> 8;
      PN name = syms.find(hash, onlyQuad, 0);
      if(name == null){
         qBuf[0] = onlyQuad;
         name = Code(hash, qBuf, 1, lastByteCount);
      }
      return name;
   }

   private final PN findPName(int firstQuad, int secondQuad, int lastByteCount) throws XMLStreamException{
      --inPtr;
      int hash = firstQuad * 31 + secondQuad;
      hash ^= hash >>> 16;
      hash ^= hash >>> 8;
      PN name = syms.find(hash, firstQuad, secondQuad);
      if(name == null){
         qBuf[0] = firstQuad;
         qBuf[1] = secondQuad;
         name = Code(hash, qBuf, 2, lastByteCount);
      }
      return name;
   }

   private final PN findPName(int lastQuad, int[] quads, int qlen, int lastByteCount) throws XMLStreamException{
      --inPtr;
      if(qlen >= quads.length)
         qBuf = quads = xpand(quads, quads.length);
      quads[qlen++] = lastQuad;
      int hash = quads[0];
      for(int i = 1; i < qlen; ++i)
         hash = hash * 31 + quads[i];
      hash ^= hash >>> 16;
      hash ^= hash >>> 8;
      PN name = syms.find(hash, quads, qlen);
      if(name == null)
         name = Code(hash, quads, qlen, lastByteCount);
      return name;
   }

   private final PN findPName(int lastQuad, int lastByteCount, int firstQuad, int qlen, int[] quads) throws XMLStreamException{
      if(qlen <= 1)
         return qlen == 0 ? findPName(lastQuad, lastByteCount) : findPName(firstQuad, lastQuad, lastByteCount);
      return findPName(lastQuad, quads, qlen, lastByteCount);
   }

   private final byte Code(boolean reqd) throws XMLStreamException{
      if(inPtr >= end)
         assertMore();
      byte b = inBuf[inPtr++];
      if((b & 0xFF) > 0x20){
         if(!reqd)
            return b;
         thUnxp(decChr(b), ", expected white space");
      }
      do{
         if(b == (byte)'\n')
            markLF();
         else if(b == (byte)'\r'){
            if(inPtr >= end)
               assertMore();
            if(inBuf[inPtr] == (byte)'\n')
               ++inPtr;
            markLF();
         }else if(b != (byte)' ' && b != 9)
            thC(b);
         if(inPtr >= end)
            assertMore();
      }while(((b = inBuf[inPtr++]) & 0xFF) <= 0x20);
      return b;
   }

   private final void Code(String kw) throws XMLStreamException{
      for(int i = 1, len = kw.length(); i < len; ++i){
         if(inPtr >= end)
            assertMore();
         byte b = inBuf[inPtr++];
         if(b != (byte)kw.charAt(i))
            thUnxp(decChr(b), new StrB(18).a(", expected ").a(kw).toString());
      }
   }

   private final int inTree(int c) throws XMLStreamException{
      if(c == '\r'){
         if(inPtr >= end && !more()){
            indent(0, ' ');
            return -1;
         }
         if(inBuf[inPtr] == (byte)'\n')
            ++inPtr;
      }
      markLF();
      if(inPtr >= end)
         assertMore();
      byte b = inBuf[inPtr];
      if(b != (byte)' ' && b != 9){
         if(b == (byte)'<' && inPtr + 1 < end && inBuf[inPtr + 1] != (byte)'!'){
            indent(0, ' ');
            return -1;
         }
         reset()[0] = '\n';
         return currSize = 1;
      }
      ++inPtr;
      int count = 0, max = b == (byte)' ' ? 32 : 8;
      while(++count <= max){
         if(inPtr >= end)
            assertMore();
         byte b2 = inBuf[inPtr];
         if(b2 != b){
            if(b2 == (byte)'<' && inPtr + 1 < end && inBuf[inPtr + 1] != (byte)'!'){
               indent(count, (char)b);
               return -1;
            }
            break;
         }
         ++inPtr;
      }
      char[] outBuf = reset();
      outBuf[0] = '\n';
      char ind = (char)b;
      for(int i = 1; i <= count; ++i)
         outBuf[i] = ind;
      return currSize = ++count;
   }

   private final int prolog(int c) throws XMLStreamException{
      if(c == '\r'){
         if(inPtr >= end && !more()){
            indent(0, ' ');
            return -1;
         }
         if(inBuf[inPtr] == (byte)'\n')
            ++inPtr;
      }
      markLF();
      if(inPtr >= end && !more()){
         indent(0, ' ');
         return -1;
      }
      byte b = inBuf[inPtr];
      if(b != (byte)' ' && b != 9){
         if(b == (byte)'<'){
            indent(0, ' ');
            return -1;
         }
         reset()[0] = '\n';
         return currSize = 1;
      }
      int count = 1, max = b == (byte)' ' ? 32 : 8;
      while((++inPtr < end || more()) && inBuf[inPtr] == b)
         if(++count >= max){
            char[] outBuf = reset();
            outBuf[0] = '\n';
            char ind = (char)b;
            for(int i = 1; i <= count; ++i)
                outBuf[i] = ind;
            return currSize = ++count;
         }
      indent(count, (char)b);
      return -1;
   }

   private final byte nxtB() throws XMLStreamException{
      if(inPtr >= end && !more())
         thInErr(EOI);
      return inBuf[inPtr++];
   }

   private final byte loadOne() throws XMLStreamException{
      if(!more())
         thInErr(EOI);
      return inBuf[inPtr++];
   }

   private final boolean loadNRet() throws XMLStreamException{
      if(in == null)
         return false;
      bOrC += inPtr;
      rowOff -= inPtr;
      int xx = end - inPtr;
      System.arraycopy(inBuf, inPtr, inBuf, 0, xx);
      inPtr = 0;
      end = xx;
      try{
         do{
            if((xx = in.read(inBuf, end, 4096 - end)) < 1){
               if(xx == 0)
                  thInErr("InputStream returned 0");
               return false;
            }
            end += xx;
         }while(end < 3);
         return true;
      }catch(IOException ioe){
         throw new XMLStreamException(ioe);
      }
   }

   final void endTok() throws XMLStreamException{
      incompl = false;
      switch(currToken){
         case 3:  // PROCESSING_INSTRUCTION
            endPI();
            return;
         case 4:  // CHARACTERS
            endC();
            return;
         case 5:  // COMMENT
            endComm();
            return;
         case 6:  // SPACE
            endWS();
            return;
         case 11: // DTD
            endDTD();
            return;
         case 12: // CDATA
            endCData();
      }
   }

   private final int startElem(byte b) throws XMLStreamException{
      currToken = 1; // START_ELEMENT
      nsCnt = 0;
      PN elemName = parsePN(b);
      String prefix = elemName.pfx;
      boolean allBound = true;
      if(prefix != null){
         elemName = bindName(elemName, prefix);
         allBound = elemName.isBound();
      }
      tokName = elemName;
      curr = new Node(elemName, curr);
      final byte[] inputBuffer = inBuf;
      int attrPtr = 0;
      while(true){
         if(inPtr >= end)
            assertMore();
         int c = (int)(b = inputBuffer[inPtr++]) & 0xFF;
         if(c <= 0x20)
            do{
               if(c == '\n')
                  markLF();
               else if(c == '\r'){
                  if(inPtr >= end)
                     assertMore();
                  if(inputBuffer[inPtr] == (byte)'\n')
                     ++inPtr;
                  markLF();
               }else if(c != 0x20 && c != '\t')
                  thC(c);
               if(inPtr >= end)
                  assertMore();
            }while((c = (int)(b = inputBuffer[inPtr++]) & 0xFF) <= 0x20);
         else if(c != '/' && c != '>')
            thUnxp(decChr(b), ", not space or '>' or '/>'");
         if(c == '/'){
            if(inPtr >= end)
               assertMore();
            if((b = inputBuffer[inPtr++]) != (byte)'>')
               thUnxp(decChr(b), ", not '>'");
            empty = true;
            break;
         }
         if(c == '>'){
            empty = false;
            break;
         }
         if(c == '<')
            thInErr("Unexpected '<'");
         PN attrName = parsePN(b);
         boolean isNsDecl = true;
         if((prefix = attrName.pfx) == null)
            isNsDecl = attrName.ln == "xmlns";
         else if(prefix != "xmlns"){
            attrName = bindName(attrName, prefix);
            if(allBound)
               allBound = attrName.isBound();
            isNsDecl = false;
         }
         while(true){
            if(inPtr >= end)
               assertMore();
            if((c = (int)(b = inputBuffer[inPtr++]) & 0xFF) > 0x20)
               break;
            if(c == '\n')
               markLF();
            else if(c == '\r'){
               if(inPtr >= end)
                  assertMore();
               if(inputBuffer[inPtr] == (byte)'\n')
                  ++inPtr;
               markLF();
            }else if(c != 0x20 && c != '\t')
               thC(c);
         }
         if(c != '=')
            thUnxp(decChr(b), ", not '='");
         while(true){
            if(inPtr >= end)
               assertMore();
            if((c = (int)(b = inputBuffer[inPtr++]) & 0xFF) > 0x20)
               break;
            if(c == '\n')
               markLF();
            else if(c == '\r'){
               if(inPtr >= end)
                  assertMore();
               if(inputBuffer[inPtr] == (byte)'\n')
                  ++inPtr;
               markLF();
            }else if(c != 0x20 && c != '\t')
               thC(c);
         }
         if(c != '"' && c != '\'')
            thUnxp(decChr(b), ", not a quote");
         if(isNsDecl){
            Code(attrName, b);
            ++nsCnt;
         }else
            attrPtr = Code(attrPtr, b, attrName);
      }
      int act = endLastV(attrPtr);
      if(act < 0)
         thInErr(err);
      attrCount = act;
      ++depth;
      if(!allBound){
         if(!elemName.isBound())
            thUnbPfx(tokName, false);
         for(int i = 0, len = attrCount; i < len; ++i){
            PN attrName = names[i];
            if(!attrName.isBound())
               thUnbPfx(attrName, true);
         }
      }
      return 1; // START_ELEMENT
   }

   private final int Code(int attrPtr, byte quoteByte, PN attrName) throws XMLStreamException{
      char[] attrBuffer = startNewV(attrName, attrPtr);
      final byte[] TYPES = chrT.ATT;
      final int quoteChar = (int)quoteByte;
      int c = 0;
      while(true){
         int ptr = inPtr;
         boolean adv = true;
         do{
            if(ptr >= end){
               assertMore();
               ptr = 0;
            }
            if(attrPtr >= attrBuffer.length)
               attrBuffer = xpand();
            int max = end, max2 = ptr + attrBuffer.length - attrPtr;
            if(max2 < max)
               max = max2;
            while(ptr < max){
               if(TYPES[c = (int)inBuf[ptr++] & 0xFF] != 0){
                  adv = false;
                  break;
               }
               attrBuffer[attrPtr++] = (char)c;
            }
         }while(adv);
         inPtr = ptr;
         switch(TYPES[c]){
            case 2:  // WS_CR
               if(ptr >= end)
                  assertMore();
               if(inBuf[inPtr] == (byte)'\n')
                  ++inPtr;
            case 3:  // WS_LF
               markLF();
            case 8:  // WS_TAB
               c = 0x20;
               break;
            case 5:  // MULTIBYTE_2
               c = decUTF_2(c);
               break;
            case 6:  // MULTIBYTE_3
               c = decUTF_3(c);
               break;
            case 7:  // MULTIBYTE_4
               attrBuffer[attrPtr++] = (char)(0xD800 | (c = decUTF_4(c)) >> 10);
               c = 0xDC00 | c & 0x3FF;
               if(attrPtr >= attrBuffer.length)
                  attrBuffer = xpand();
               break;
            case 10: // AMP
               if((c = entInTxt()) == 0)
                  thEnt();
               if((c >> 16) != 0){
                  attrBuffer[attrPtr++] = (char)(0xD800 | (c -= 0x10000) >> 10);
                  c = 0xDC00 | c & 0x3FF;
                  if(attrPtr >= attrBuffer.length)
                     attrBuffer = xpand();
               }
               break;
            case 14: // ATTR_QUOTE
               if(c == quoteChar)
                  return attrPtr;
               break;
            case 1:  // INVALID
               thC(c);
            case 4:  // MULTIBYTE_N
               badUTF(c);
            case 9:  // LT
               thUnxp(c, " in attribute value");
         }
         attrBuffer[attrPtr++] = (char)c;
      }
   }

   private final void Code(PN name, byte quoteByte) throws XMLStreamException{
      int attrPtr = 0, c = 0;
      char[] attrBuffer = nameBuf;
      while(true){
         if(inPtr >= end)
            assertMore();
         byte b = inBuf[inPtr++];
         if(b == quoteByte){
            bindNs(name, attrPtr == 0 ? "" : impl.Code(attrBuffer, attrPtr));
            return;
         }
         if(b == (byte)'&'){
            if((c = entInTxt()) == 0)
               thEnt();
            if((c >> 16) != 0){
               if(attrPtr >= attrBuffer.length)
                  nameBuf = attrBuffer = xpand(attrBuffer);
               attrBuffer[attrPtr++] = (char)(0xD800 | (c -= 0x10000) >> 10);
               c = 0xDC00 | (c & 0x3FF);
            }
         }else if(b == (byte)'<')
            thUnxp(b, " in attribute value");
         else if((c = (int)b & 0xFF) < 0x20){
            if(c == '\n')
               markLF();
            else if(c == '\r'){
               if(inPtr >= end)
                  assertMore();
               if(inBuf[inPtr] == (byte)'\n')
                  ++inPtr;
               markLF();
            }else if(c != '\t')
               thC(c);
         }else if(c > 0x7F && (c = decMB(c, inPtr)) < 0){
            if(attrPtr >= attrBuffer.length)
               nameBuf = attrBuffer = xpand(attrBuffer);
            attrBuffer[attrPtr++] = (char)(0xD800 | (c = -c - 0x10000) >> 10);
            c = 0xDC00 | c & 0x3FF;
         }
         if(attrPtr >= attrBuffer.length)
            nameBuf = attrBuffer = xpand(attrBuffer);
         attrBuffer[attrPtr++] = (char)c;
      }
   }

   private final int entInTxt() throws XMLStreamException{
      if(inPtr >= end)
         assertMore();
      byte b = inBuf[inPtr++];
      if(b == (byte)'#')
         return chrEnt();
      char[] cbuf = nameBuf;
      int cix = 0;
      if(b == (byte)'a'){
         cbuf[cix++] = (char)b;
         if((b = inPtr < end ? inBuf[inPtr++] : loadOne()) == (byte)'m'){
            cbuf[cix++] = (char)b;
            if((b = inPtr < end ? inBuf[inPtr++] : loadOne()) == (byte)'p'){
               cbuf[cix++] = (char)b;
               if((b = inPtr < end ? inBuf[inPtr++] : loadOne()) == (byte)';')
                  return '&';
            }
         }else if(b == (byte)'p'){
            cbuf[cix++] = (char)b;
            if((b = inPtr < end ? inBuf[inPtr++] : loadOne()) == (byte)'o'){
               cbuf[cix++] = (char)b;
               if((b = inPtr < end ? inBuf[inPtr++] : loadOne()) == (byte)'s'){
                  cbuf[cix++] = (char)b;
                  if((b = inPtr < end ? inBuf[inPtr++] : loadOne()) == (byte)';')
                     return '\'';
               }
            }
         }
      }else if(b == (byte)'l'){
         cbuf[cix++] = (char)b;
         if((b = inPtr < end ? inBuf[inPtr++] : loadOne()) == (byte)'t'){
            cbuf[cix++] = (char)b;
            if((b = inPtr < end ? inBuf[inPtr++] : loadOne()) == (byte)';')
               return '<';
         }
      }else if(b == (byte)'g'){
         cbuf[cix++] = (char)b;
         if((b = inPtr < end ? inBuf[inPtr++] : loadOne()) == (byte)'t'){
            cbuf[cix++] = (char)b;
            if((b = inPtr < end ? inBuf[inPtr++] : loadOne()) == (byte)';')
               return '>';
         }
      }else if(b == (byte)'q'){
         cbuf[cix++] = (char)b;
         if((b = inPtr < end ? inBuf[inPtr++] : loadOne()) == (byte)'u'){
            cbuf[cix++] = (char)b;
            if((b = inPtr < end ? inBuf[inPtr++] : loadOne()) == (byte)'o'){
               cbuf[cix++] = (char)b;
               if((b = inPtr < end ? inBuf[inPtr++] : loadOne()) == (byte)'t'){
                  cbuf[cix++] = (char)b;
                  if((b = inPtr < end ? inBuf[inPtr++] : loadOne()) == (byte)';')
                     return '"';
               }
            }
         }
      }
      final byte[] TYPES = chrT.NAM;
      while(b != (byte)';'){
         boolean ok = false;
         int c = (int)b & 0xFF;
         switch(TYPES[c]){
            case 0:  // NAME_NONE
            case 1:  // NAME_COLON
            case 2:  // NAME_NONFIRST
               ok = cix > 0;
               break;
            case 3:  // NAME_ANY
               ok = true;
               break;
            case 5:  // MULTIBYTE_2
               ok = Chr.is10NS(c = decUTF_2(c));
               break;
            case 6:  // MULTIBYTE_3
               ok = Chr.is10NS(c = decUTF_3(c));
               break;
            case 7:  // MULTIBYTE_4
               ok = Chr.is10NS(c = decUTF_4(c));
               if(ok){
                  if(cix >= cbuf.length)
                     nameBuf = cbuf = xpand(cbuf);
                  cbuf[cix++] = (char)(0xD800 | (c -= 0x10000) >> 10);
                  c = 0xDC00 | c & 0x3FF;
               }
         }
         if(!ok)
            thInvNCh(c);
         if(cix >= cbuf.length)
            nameBuf = cbuf = xpand(cbuf);
         cbuf[cix++] = (char)c;
         if(inPtr >= end)
            assertMore();
         b = inBuf[inPtr++];
      }
      if(impl.Code(16))
         thInErr("Entity ref. in entity expanding mode");
      String pname = new String(cbuf, 0, cix);
      tokName = new PN(pname, null, pname, 0);
      return 0;
   }

   private final String Code(int quote) throws XMLStreamException{
      final byte[] TYPES = chrT.ATT;
      char[] outputBuffer = nameBuf;
      int outPtr = 0;
      while(true){
         if(inPtr >= end)
            assertMore();
         int c = (int)inBuf[inPtr++] & 0xFF;
         if(c == quote)
            return new String(outputBuffer, 0, outPtr);
         switch(TYPES[c]){
            case 2:  // WS_CR
               if(inPtr >= end)
                  assertMore();
               if(inBuf[inPtr] == (byte)'\n')
                  ++inPtr;
               c = '\n';
            case 3:  // WS_LF
               markLF();
               break;
            case 5:  // MULTIBYTE_2
               c = decUTF_2(c);
               break;
            case 6:  // MULTIBYTE_3
               c = decUTF_3(c);
               break;
            case 7:  // MULTIBYTE_4
               c = decUTF_4(c);
               if(outPtr >= outputBuffer.length){
                  outputBuffer = endSeg();
                  outPtr = 0;
               }
               outputBuffer[outPtr++] = (char)(0xD800 | c >> 10);
               c = 0xDC00 | c & 0x3FF;
               break;
            case 1:  // INVALID
               thC(c);
            case 4:  // MULTIBYTE_N
               badUTF(c);
         }
         if(outPtr >= outputBuffer.length)
            nameBuf = outputBuffer = xpand(outputBuffer);
         outputBuffer[outPtr++] = (char)c;
      }
   }

   final boolean skipChars() throws XMLStreamException{
      final byte[] TYPES = chrT.TXT, inputBuffer = inBuf;
      int c = 0;
      while(true){
         int ptr = inPtr, max = end;
         boolean adv = true;
         do{
            if(ptr >= max){
               assertMore();
               ptr = 0;
               max = end;
            }
            while(ptr < max)
               if(TYPES[c = (int)inputBuffer[ptr++] & 0xFF] != 0){
                  adv = false;
                  break;
               }
         }while(adv);
         inPtr = ptr;
         switch(TYPES[c]){
            case 2:  // WS_CR
               if(ptr >= end)
                  assertMore();
               if(inputBuffer[inPtr] == (byte)'\n')
                  ++inPtr;
            case 3:  // WS_LF
               markLF();
               continue;
            case 5:  // MULTIBYTE_2
               skipUTF_2();
               continue;
            case 6:  // MULTIBYTE_3
               skipUTF_3(c);
               continue;
            case 7:  // MULTIBYTE_4
               skipUTF_4();
               continue;
            case 9:  // LT
               --inPtr;
               return false;
            case 10: // AMP
               if(entInTxt() == 0)
                  return true;
               continue;
            case 11: // RBRACKET
               int count = 1;
               byte b;
               while(true){
                  if(inPtr >= end)
                     assertMore();
                  if((b = inputBuffer[inPtr]) != (byte)']')
                     break;
                  ++inPtr;
                  ++count;
               }
               if(b == (byte)'>' && count > 1)
                  thCDEnd();
               continue;
            case 1:  // INVALID
               thC(c);
            case 4:  // MULTIBYTE_N
               badUTF(c);
         }
      }
   }

   final void skipComm() throws XMLStreamException{
      final byte[] TYPES = chrT.OTH, inputBuffer = inBuf;
      int c = 0;
      while(true){
         int ptr = inPtr, max = end;
         boolean adv = true;
         do{
            if(ptr >= max){
               assertMore();
               ptr = 0;
               max = end;
            }
            while(ptr < max)
               if(TYPES[c = (int)inputBuffer[ptr++] & 0xFF] != 0){
                  adv = false;
                  break;
               }
         }while(adv);
         inPtr = ptr;
         switch(TYPES[c]){
            case 2:  // WS_CR
               if(ptr >= end)
                  assertMore();
               if(inputBuffer[inPtr] == (byte)'\n')
                  ++inPtr;
            case 3:  // WS_LF
               markLF();
               continue;
            case 5:  // MULTIBYTE_2
               skipUTF_2();
               continue;
            case 6:  // MULTIBYTE_3
               skipUTF_3(c);
               continue;
            case 7:  // MULTIBYTE_4
               skipUTF_4();
               continue;
            case 13: // HYPHEN
               if(ptr >= end)
                  assertMore();
               if(inBuf[inPtr] == (byte)'-'){
                  if(++inPtr >= end)
                     assertMore();
                  if(inBuf[inPtr++] != (byte)'>')
                     thHyph();
                  return;
               }
               continue;
            case 1:  // INVALID
               thC(c);
            case 4:  // MULTIBYTE_N
               badUTF(c);
         }
      }
   }

   final void skipCData() throws XMLStreamException{
      final byte[] TYPES = chrT.OTH, inputBuffer = inBuf;
      int c = 0;
      while(true){
         int ptr = inPtr, max = end;
         boolean adv = true;
         do{
            if(ptr >= max){
               assertMore();
               ptr = 0;
               max = end;
            }
            while(ptr < max)
               if(TYPES[c = (int)inputBuffer[ptr++] & 0xFF] != 0){
                  adv = false;
                  break;
               }
         }while(adv);
         inPtr = ptr;
         switch(TYPES[c]){
            case 2:  // WS_CR
               if(ptr >= end)
                  assertMore();
               if(inputBuffer[inPtr] == (byte)'\n')
                  ++inPtr;
            case 3:  // WS_LF
               markLF();
               continue;
            case 5:  // MULTIBYTE_2
               skipUTF_2();
               continue;
            case 6:  // MULTIBYTE_3
               skipUTF_3(c);
               continue;
            case 7:  // MULTIBYTE_4
               skipUTF_4();
               continue;
            case 11: // RBRACKET
               int count = 0;
               byte b;
               do{
                  if(inPtr >= end)
                     assertMore();
                  ++count;
               }while((b = inBuf[inPtr++]) == (byte)']');
               if(b == (byte)'>'){
                  if(count > 1)
                     return;
               }else
                  --inPtr;
               continue;
            case 1:  // INVALID
               thC(c);
            case 4:  // MULTIBYTE_N
               badUTF(c);
         }
      }
   }

   final void skipPI() throws XMLStreamException{
      final byte[] TYPES = chrT.OTH, inputBuffer = inBuf;
      int c = 0;
      while(true){
         int ptr = inPtr, max = end;
         boolean adv = true;
         do{
            if(ptr >= max){
               assertMore();
               ptr = 0;
               max = end;
            }
            while(ptr < max)
               if(TYPES[c = (int)inputBuffer[ptr++] & 0xFF] != 0){
                  adv = false;
                  break;
               }
         }while(adv);
         inPtr = ptr;
         switch(TYPES[c]){
            case 2:  // WS_CR
               if(ptr >= end)
                  assertMore();
               if(inputBuffer[inPtr] == (byte)'\n')
                  ++inPtr;
            case 3:  // WS_LF
               markLF();
               continue;
            case 5:  // MULTIBYTE_2
               skipUTF_2();
               continue;
            case 6:  // MULTIBYTE_3
               skipUTF_3(c);
               continue;
            case 7:  // MULTIBYTE_4
               skipUTF_4();
               continue;
            case 12: // QMARK
               if(ptr >= end)
                  assertMore();
               if(inBuf[inPtr] == (byte)'>'){
                  ++inPtr;
                  return;
               }
               continue;
            case 1:  // INVALID
               thC(c);
            case 4:  // MULTIBYTE_N
               badUTF(c);
         }
      }
   }

   final void skipWS() throws XMLStreamException{
      final byte[] inputBuffer = inBuf;
      int ptr = inPtr;
      while(true){
         if(ptr >= end){
            if(!more())
               break;
            ptr = 0;
         }
         int c = (int)inputBuffer[ptr] & 0xFF;
         if(c > 0x20)
            break;
         ++ptr;
         if(c == '\n'){
            rowOff = ptr;
            ++currRow;
         }else if(c == '\r'){
            if(ptr >= end){
               if(!more())
                  break;
               ptr = 0;
            }
            if(inputBuffer[ptr] == (byte)'\n')
               ++ptr;
            rowOff = ptr;
            ++currRow;
         }else if(c != 0x20 && c != '\t'){
            inPtr = ptr;
            thC(c);
         }
      }
      inPtr = ptr;
   }

   private final void skipUTF_2() throws XMLStreamException{
      if(inPtr >= end)
         assertMore();
      int c = (int)inBuf[inPtr++];
      if((c & 0xC0) != 0x80)
         badUTF(c);
   }

   private final void skipUTF_3(int c) throws XMLStreamException{
      if(inPtr >= end)
         assertMore();
      int d = (int)inBuf[inPtr++];
      if((d & 0xC0) != 0x80)
         badUTF(d);
      if(inPtr >= end)
         assertMore();
      int e = (int)inBuf[inPtr++];
      if((e & 0xC0) != 0x80)
         badUTF(e);
      if((c &= 0xF) >= 0xD && (((c = (c << 6 | d & 0x3F) << 6 | e & 0x3F) >= 0xD800 && c < 0xE000) || c == 0xFFFE || c == 0xFFFF))
         thC(c);
   }

   private final void skipUTF_4() throws XMLStreamException{
      int d;
      if(inPtr + 4 > end){
         if(inPtr >= end)
            assertMore();
         if(((d = (int)inBuf[inPtr++]) & 0xC0) != 0x80)
            badUTF(d);
         if(inPtr >= end)
            assertMore();
         if(((d = (int)inBuf[inPtr++]) & 0xC0) != 0x80)
            badUTF(d);
         if(inPtr >= end)
            assertMore();
      }else{
         if(((d = (int)inBuf[inPtr++]) & 0xC0) != 0x80)
            badUTF(d);
         if(((d = (int)inBuf[inPtr++]) & 0xC0) != 0x80)
            badUTF(d);
      }
      if(((d = (int)inBuf[inPtr++]) & 0xC0) != 0x80)
         badUTF(d);
   }

   private final void endCData() throws XMLStreamException{
      final byte[] TYPES = chrT.OTH, inputBuffer = inBuf;
      char[] outputBuffer = reset();
      int outPtr = 0, c = 0;
      while(true){
         int ptr = inPtr;
         boolean adv = true;
         do{
            if(ptr >= end){
               assertMore();
               ptr = 0;
            }
            if(outPtr >= outputBuffer.length){
               outputBuffer = endSeg();
               outPtr = 0;
            }
            int max = end, max2 = ptr + outputBuffer.length - outPtr;
            if(max2 < max)
               max = max2;
            while(ptr < max){
               if(TYPES[c = (int)inputBuffer[ptr++] & 0xFF] != 0){
                  adv = false;
                  break;
               }
               outputBuffer[outPtr++] = (char)c;
            }
         }while(adv);
         inPtr = ptr;
         switch(TYPES[c]){
            case 2:  // WS_CR
               if(ptr >= end)
                  assertMore();
               if(inputBuffer[inPtr] == (byte)'\n')
                  ++inPtr;
               c = '\n';
            case 3:  // WS_LF
               markLF();
               break;
            case 5:  // MULTIBYTE_2
               c = decUTF_2(c);
               break;
            case 6:  // MULTIBYTE_3
               c = decUTF_3(c);
               break;
            case 7:  // MULTIBYTE_4
               outputBuffer[outPtr++] = (char)(0xD800 | (c = decUTF_4(c)) >> 10);
               if(outPtr >= outputBuffer.length){
                  outputBuffer = endSeg();
                  outPtr = 0;
               }
               c = 0xDC00 | c & 0x3FF;
               break;
            case 11: // RBRACKET
               int count = 0;
               byte b;
               do{
                  if(inPtr >= end)
                     assertMore();
                  if((b = inBuf[inPtr]) != (byte)']')
                     break;
                  ++inPtr;
                  ++count;
               }while(true);
               boolean ok = b == (byte)'>' && count >= 1;
               if(ok)
                  --count;
               while(count > 0){
                  --count;
                  outputBuffer[outPtr++] = ']';
                  if(outPtr >= outputBuffer.length){
                     outputBuffer = endSeg();
                     outPtr = 0;
                  }
               }
               if(ok){
                  ++inPtr;
                  currSize = outPtr;
                  if(cls && !pending)
                     endClsTxt();
                  return;
               }
               break;
            case 1:  // INVALID
               thC(c);
            case 4:  // MULTIBYTE_N
               badUTF(c);
         }
         outputBuffer[outPtr++] = (char) c;
      }
   }

   private final void endC() throws XMLStreamException{
      int outPtr, c = cTmp;
      char[] outputBuffer;
      if(c < 0){
         outputBuffer = reset();
         outPtr = 0;
         if(((c = -c) >> 16) != 0){
            outputBuffer[outPtr++] = (char)(0xD800 | (c -= 0x10000) >> 10);
            c = 0xDC00 | c & 0x3FF;
         }
         outputBuffer[outPtr++] = (char)c;
      }else if(c == '\r' || c == '\n'){
         ++inPtr;
         if((outPtr = inTree(c)) < 0)
            return;
         outputBuffer = currSeg;
      }else{
         outputBuffer = reset();
         outPtr = 0;
      }
      final byte[] TYPES = chrT.TXT, inputBuffer = inBuf;
      while(true){
         int ptr = inPtr;
         boolean adv = true;
         do{
            if(ptr >= end){
               assertMore();
               ptr = 0;
            }
            if(outPtr >= outputBuffer.length){
               outputBuffer = endSeg();
               outPtr = 0;
            }
            int max = end, max2 = ptr + outputBuffer.length - outPtr;
            if(max2 < max)
               max = max2;
            while(ptr < max){
               if(TYPES[c = (int)inputBuffer[ptr++] & 0xFF] != 0){
                  adv = false;
                  break;
               }
               outputBuffer[outPtr++] = (char)c;
            }
         }while(adv);
         inPtr = ptr;
         switch(TYPES[c]){
            case 2:  // WS_CR
               if(ptr >= end)
                  assertMore();
               if(inputBuffer[inPtr] == (byte)'\n')
                  ++inPtr;
               c = '\n';
            case 3:  // WS_LF
               markLF();
               break;
            case 5:  // MULTIBYTE_2
               c = decUTF_2(c);
               break;
            case 6:  // MULTIBYTE_3
               c = end - ptr >= 2 ? decUTF_3f(c) : decUTF_3(c);
               break;
            case 7:  // MULTIBYTE_4
               outputBuffer[outPtr++] = (char)(0xD800 | (c = decUTF_4(c)) >> 10);
               if(outPtr >= outputBuffer.length){
                  outputBuffer = endSeg();
                  outPtr = 0;
               }
               c = 0xDC00 | c & 0x3FF;
               break;
            case 9:  // LT
               --inPtr;
               currSize = outPtr;
               if(cls && !pending)
                  endClsTxt();
               return;
            case 10: // AMP
               if((c = entInTxt()) == 0){
                  pending = true;
                  currSize = outPtr;
                  // if(cls && !pending)
                  //    endClsTxt();
                  return;
               }
               if((c >> 16) != 0){
                  outputBuffer[outPtr++] = (char)(0xD800 | (c -= 0x10000) >> 10);
                  if(outPtr >= outputBuffer.length){
                     outputBuffer = endSeg();
                     outPtr = 0;
                  }
                  c = 0xDC00 | c & 0x3FF;
               }
               break;
            case 11: // RBRACKET
               int count = 1;
               byte b;
               while(true){
                  if(inPtr >= end)
                     assertMore();
                  if((b = inputBuffer[inPtr]) != (byte)']')
                     break;
                  ++inPtr;
                  ++count;
               }
               if(b == (byte)'>' && count > 1)
                  thCDEnd();
               while(count > 1){
                  outputBuffer[outPtr++] = ']';
                  if(outPtr >= outputBuffer.length){
                     outputBuffer = endSeg();
                     outPtr = 0;
                  }
                  --count;
               }
               break;
            case 1:  // INVALID
               thC(c);
            case 4:  // MULTIBYTE_N
               badUTF(c);
         }
         outputBuffer[outPtr++] = (char)c;
      }
   }

   private final void endComm() throws XMLStreamException{
      final byte[] TYPES = chrT.OTH, inputBuffer = inBuf;
      char[] outputBuffer = reset();
      int outPtr = 0, c = 0;
      while(true){
         int ptr = inPtr;
         boolean adv = true;
         do{
            if(ptr >= end){
               assertMore();
               ptr = 0;
            }
            if(outPtr >= outputBuffer.length){
               outputBuffer = endSeg();
               outPtr = 0;
            }
            int max = end, max2 = ptr + outputBuffer.length - outPtr;
            if(max2 < max)
               max = max2;
            while(ptr < max){
               if(TYPES[c = (int)inputBuffer[ptr++] & 0xFF] != 0){
                  adv = false;
                  break;
               }
               outputBuffer[outPtr++] = (char)c;
            }
         }while(adv);
         inPtr = ptr;
         switch(TYPES[c]){
            case 2:  // WS_CR
               if(ptr >= end)
                  assertMore();
               if(inputBuffer[inPtr] == (byte)'\n')
                  ++inPtr;
               c = '\n';
            case 3:  // WS_LF
               markLF();
               break;
            case 5:  // MULTIBYTE_2
               c = decUTF_2(c);
               break;
            case 6:  // MULTIBYTE_3
               c = decUTF_3(c);
               break;
            case 7:  // MULTIBYTE_4
               outputBuffer[outPtr++] = (char)(0xD800 | (c = decUTF_4(c)) >> 10);
               if(outPtr >= outputBuffer.length) {
                  outputBuffer = endSeg();
                  outPtr = 0;
               }
               c = 0xDC00 | c & 0x3FF;
               break;
            case 13: // HYPHEN
               if(ptr >= end)
                  assertMore();
               if(inBuf[inPtr] == (byte)'-'){
                  if(++inPtr >= end)
                     assertMore();
                  if(inBuf[inPtr++] != (byte)'>')
                     thHyph();
                  currSize = outPtr;
                  return;
               }
               break;
            case 1:  // INVALID
               thC(c);
            case 4:  // MULTIBYTE_N
               badUTF(c);
         }
         outputBuffer[outPtr++] = (char)c;
      }
   }

   private final void endDTD() throws XMLStreamException{
      char[] outputBuffer = reset();
      int outPtr = 0, quoteChar = 0, c = 0;
      final byte[] TYPES = chrT.DTD;
      boolean inDecl = false;
      while(true){
         int ptr = inPtr;
         boolean adv = true;
         do{
            if(ptr >= end){
               assertMore();
               ptr = 0;
            }
            if(outPtr >= outputBuffer.length){
               outputBuffer = endSeg();
               outPtr = 0;
            }
            int max = end, max2 = ptr + outputBuffer.length - outPtr;
            if(max2 < max)
               max = max2;
            while(ptr < max){
               if(TYPES[c = (int)inBuf[ptr++] & 0xFF] != 0){
                  adv = false;
                  break;
               }
               outputBuffer[outPtr++] = (char)c;
            }
         }while(adv);
         inPtr = ptr;
         switch(TYPES[c]){
            case 2:  // WS_CR
               if(ptr >= end)
                  assertMore();
               if(inBuf[inPtr] == (byte)'\n')
                  ++inPtr;
               c = '\n';
            case 3:  // WS_LF
               markLF();
               break;
            case 5:  // MULTIBYTE_2
               c = decUTF_2(c);
               break;
            case 6:  // MULTIBYTE_3
               c = decUTF_3(c);
               break;
            case 7:  // MULTIBYTE_4
               outputBuffer[outPtr++] = (char)(0xD800 | (c = decUTF_4(c)) >> 10);
               c = 0xDC00 | c & 0x3FF;
               if(outPtr >= outputBuffer.length){
                  outputBuffer = endSeg();
                  outPtr = 0;
               }
               break;
            case 8:  // DTD_QUOTE
               if(quoteChar == 0)
                  quoteChar = c;
               else if(quoteChar == c)
                  quoteChar = 0;
               break;
            case 9:  // LT
               inDecl = true;
               break;
            case 10: // DTD_GT
               if(quoteChar == 0)
                  inDecl = false;
               break;
            case 11: // RBRACKET
               if(!inDecl && quoteChar == 0){
                  currSize = outPtr;
                  byte b = Code(false);
                  if(b != (byte)'>')
                     thUnxp(decChr(b), ", not '>' after internal subset");
                  return;
               }
               break;
            case 1:  // INVALID
               thC(c);
            case 4:  // MULTIBYTE_N
               badUTF(c);
         }
         outputBuffer[outPtr++] = (char)c;
      }
   }

   final void skipDTD() throws XMLStreamException{
      int outPtr = 0, quoteChar = 0, c = 0;
      final byte[] TYPES = chrT.DTD;
      boolean inDecl = false;
      while(true){
         int ptr = inPtr;
         boolean adv = true;
         do{
            if(ptr >= end){
               assertMore();
               ptr = 0;
            }
            int max = end;
            while(ptr < max)
               if(TYPES[c = (int)inBuf[ptr++] & 0xFF] != 0){
                  adv = false;
                  break;
               }
         }while(adv);
         inPtr = ptr;
         switch(TYPES[c]){
            case 2:  // WS_CR
               if(ptr >= end)
                  assertMore();
               if(inBuf[inPtr] == (byte)'\n')
                  ++inPtr;
               c = '\n';
            case 3:  // WS_LF
               markLF();
               continue;
            case 5:  // MULTIBYTE_2
               c = decUTF_2(c);
               continue;
            case 6:  // MULTIBYTE_3
               c = decUTF_3(c);
               continue;
            case 7:  // MULTIBYTE_4
               c = decUTF_4(c);
               continue;
            case 8:  // DTD_QUOTE
               if(quoteChar == 0)
                  quoteChar = c;
               else if(quoteChar == c)
                  quoteChar = 0;
               continue;
            case 9:  // LT
               inDecl = true;
               continue;
            case 10: // DTD_GT
               if(quoteChar == 0)
                  inDecl = false;
               continue;
            case 11: // RBRACKET
               if(!inDecl && quoteChar == 0){
                  byte b = Code(false);
                  if(b != (byte)'>')
                     thUnxp(decChr(b), ", not '>' after internal subset");
               }
               continue;
            case 1:  // INVALID
               thC(c);
            case 4:  // MULTIBYTE_N
               badUTF(c);
         }
      }
   }

   private final void endPI() throws XMLStreamException{
      final byte[] TYPES = chrT.OTH, inputBuffer = inBuf;
      char[] outputBuffer = reset();
      int c = 0, outPtr = 0;
      while(true){
         int ptr = inPtr;
         boolean adv = true;
         do{
            if(ptr >= end){
               assertMore();
               ptr = 0;
            }
            if(outPtr >= outputBuffer.length){
               outputBuffer = endSeg();
               outPtr = 0;
            }
            int max = end, max2 = ptr + outputBuffer.length - outPtr;
            if(max2 < max)
               max = max2;
            while(ptr < max){
               if(TYPES[c = (int)inputBuffer[ptr++] & 0xFF] != 0){
                  adv = false;
                  break;
               }
               outputBuffer[outPtr++] = (char)c;
            }
         }while(adv);
         inPtr = ptr;
         switch(TYPES[c]){
            case 2:  // WS_CR
               if(ptr >= end)
                  assertMore();
               if(inputBuffer[inPtr] == (byte)'\n')
                  ++inPtr;
               c = '\n';
            case 3:  // WS_LF
               markLF();
               break;
            case 5:  // MULTIBYTE_2
               c = decUTF_2(c);
               break;
            case 6:  // MULTIBYTE_3
               c = decUTF_3(c);
               break;
            case 7:  // MULTIBYTE_4
               outputBuffer[outPtr++] = (char)(0xD800 | (c = decUTF_4(c)) >> 10);
               if(outPtr >= outputBuffer.length){
                  outputBuffer = endSeg();
                  outPtr = 0;
               }
               c = 0xDC00 | c & 0x3FF;
               break;
            case 12: // QMARK
               if(ptr >= end)
                  assertMore();
               if(inBuf[inPtr] == (byte)'>'){
                  ++inPtr;
                  currSize = outPtr;
                  return;
               }
               break;
            case 1:  // INVALID
               thC(c);
            case 4:  // MULTIBYTE_N
               badUTF(c);
         }
         outputBuffer[outPtr++] = (char)c;
      }
   }

   private final void endWS() throws XMLStreamException{
      int tmp = cTmp;
      char[] outputBuffer;
      int outPtr;
      if(tmp == '\r' || tmp == '\n'){
         if((outPtr = prolog(tmp)) < 0)
            return;
         outputBuffer = currSeg;
      }else{
         outputBuffer = reset();
         outputBuffer[0] = (char)tmp;
         outPtr = 1;
      }
      int ptr = inPtr;
      while(true){
         if(ptr >= end){
            if(!more())
               break;
            ptr = 0;
         }
         int c = (int)inBuf[ptr] & 0xFF;
         if(c > 0x20)
            break;
         ++ptr;
         if(c == '\n'){
            rowOff = ptr;
            ++currRow;
         }else if(c == '\r'){
            if(ptr >= end){
               if(!more()){
                  if(outPtr >= outputBuffer.length){
                     outputBuffer = endSeg();
                     outPtr = 0;
                  }
                  outputBuffer[outPtr++] = '\n';
                  break;
               }
               ptr = 0;
            }
            if(inBuf[ptr] == (byte)'\n')
               ++ptr;
            rowOff = ptr;
            ++currRow;
            c = '\n';
         }else if(c != 0x20 && c != '\t'){
            inPtr = ptr;
            thC(c);
         }
         if(outPtr >= outputBuffer.length){
            outputBuffer = endSeg();
            outPtr = 0;
         }
         outputBuffer[outPtr++] = (char)c;
      }
      inPtr = ptr;
      currSize = outPtr;
   }

   private final void endClsTxt() throws XMLStreamException{
      while(true){
         if(inPtr >= end && !more())
            return;
         if(inBuf[inPtr] == (byte)'<'){
            if((inPtr + 3 >= end && !loadNRet()) || inBuf[inPtr + 1] != (byte)'!' || inBuf[inPtr + 2] != (byte)'[')
               return;
            inPtr += 3;
            for(int i = 0; i < 6; ++i){
               if(inPtr >= end)
                  assertMore();
               if(inBuf[inPtr++] != (byte)CDATA.charAt(i))
                  thInErr("Expected '[CDATA['");
            }
            endClsCData();
         }else{
            endClsC();
            if(pending)
               return;
         }
      }
   }

   private final void endClsC() throws XMLStreamException{
      final byte[] TYPES = chrT.TXT, inputBuffer = inBuf;
      char[] outputBuffer = currSeg;
      int c = 0, outPtr = currSize;
      while(true){
         int ptr = inPtr;
         boolean adv = true;
         do{
            if(ptr >= end){
               assertMore();
               ptr = 0;
            }
            if(outPtr >= outputBuffer.length){
               outputBuffer = endSeg();
               outPtr = 0;
            }
            int max = end, max2 = ptr + outputBuffer.length - outPtr;
            if(max2 < max)
               max = max2;
            while(ptr < max){
               if(TYPES[c = (int)inputBuffer[ptr++] & 0xFF] != 0){
                  adv = false;
                  break;
               }
               outputBuffer[outPtr++] = (char)c;
            }
         }while(adv);
         inPtr = ptr;
         switch(TYPES[c]){
            case 2:  // WS_CR
               if(ptr >= end)
                  assertMore();
               if(inputBuffer[inPtr] == (byte)'\n')
                  ++inPtr;
               c = (int)'\n';
            case 3:  // WS_LF
               markLF();
               break;
            case 5:  // MULTIBYTE_2
               c = decUTF_2(c);
               break;
            case 6:  // MULTIBYTE_3
               c = end - ptr >= 2 ? decUTF_3f(c) : decUTF_3(c);
               break;
            case 7:  // MULTIBYTE_4
               outputBuffer[outPtr++] = (char)(0xD800 | (c = decUTF_4(c)) >> 10);
               if(outPtr >= outputBuffer.length){
                  outputBuffer = endSeg();
                  outPtr = 0;
               }
               c = 0xDC00 | c & 0x3FF;
               break;
            case 9:  // LT
               --inPtr;
               currSize = outPtr;
               return;
            case 10: // AMP
               if((c = entInTxt()) == 0){
                  pending = true;
                  currSize = outPtr;
                  return;
               }
               if((c >> 16) != 0){
                  outputBuffer[outPtr++] = (char)(0xD800 | (c -= 0x10000) >> 10);
                  if(outPtr >= outputBuffer.length){
                     outputBuffer = endSeg();
                     outPtr = 0;
                  }
                  c = 0xDC00 | c & 0x3FF;
               }
               break;
            case 11: // RBRACKET
               int count = 1;
               byte b;
               while(true){
                  if(inPtr >= end)
                     assertMore();
                  if((b = inputBuffer[inPtr]) != (byte)']')
                     break;
                  ++inPtr;
                  ++count;
               }
               if(b == (byte)'>' && count > 1)
                  thCDEnd();
               while(count > 1){
                  outputBuffer[outPtr++] = ']';
                  if(outPtr >= outputBuffer.length){
                     outputBuffer = endSeg();
                     outPtr = 0;
                  }
                  --count;
               }
               break;
            case 1:  // INVALID
               thC(c);
            case 4:  // MULTIBYTE_N
               badUTF(c);
         }
         outputBuffer[outPtr++] = (char)c;
      }
   }

   private final void endClsCData() throws XMLStreamException{
      final byte[] TYPES = chrT.OTH, inputBuffer = inBuf;
      char[] outputBuffer = currSeg;
      int c = 0, outPtr = currSize;
      while(true){
         int ptr = inPtr;
         boolean adv = true;
         do{
            if(ptr >= end){
               assertMore();
               ptr = 0;
            }
            if(outPtr >= outputBuffer.length){
               outputBuffer = endSeg();
               outPtr = 0;
            }
            int max = end, max2 = ptr + outputBuffer.length - outPtr;
            if(max2 < max)
               max = max2;
            while(ptr < max){
               if(TYPES[c = (int)inputBuffer[ptr++] & 0xFF] != 0){
                  adv = false;
                  break;
               }
               outputBuffer[outPtr++] = (char)c;
            }
         }while(adv);
         inPtr = ptr;
         switch(TYPES[c]){
            case 2:  // WS_CR
               if(ptr >= end)
                  assertMore();
               if(inputBuffer[inPtr] == (byte)'\n')
                  ++inPtr;
               c = '\n';
            case 3:  // WS_LF
               markLF();
               break;
            case 5:  // MULTIBYTE_2
               c = decUTF_2(c);
               break;
            case 6:  // MULTIBYTE_3
               c = decUTF_3(c);
               break;
            case 7:  // MULTIBYTE_4
               c = decUTF_4(c);
               outputBuffer[outPtr++] = (char)(0xD800 | c >> 10);
               if(outPtr >= outputBuffer.length){
                  outputBuffer = endSeg();
                  outPtr = 0;
               }
               c = 0xDC00 | c & 0x3FF;
               break;
            case 11: // RBRACKET
               int count = 0;
               byte b;
               do{
                  if(inPtr >= end)
                     assertMore();
                  if((b = inBuf[inPtr]) != (byte)']')
                     break;
                  ++inPtr;
                  ++count;
               }while(true);
               boolean ok = b == (byte)'>' && count >= 1;
               if(ok)
                  --count;
               while(count > 0){
                  outputBuffer[outPtr++] = ']';
                  --count;
                  if(outPtr >= outputBuffer.length){
                     outputBuffer = endSeg();
                     outPtr = 0;
                  }
               }
               if(ok){
                  ++inPtr;
                  currSize = outPtr;
                  return;
               }
               break;
            case 1:  // INVALID
               thC(c);
            case 4:  // MULTIBYTE_N
               badUTF(c);
         }
         outputBuffer[outPtr++] = (char)c;
      }
   }

   final boolean skipCTxt() throws XMLStreamException{
      while(true){
         if(inPtr >= end && !more())
            return false;
         if(inBuf[inPtr] == (byte)'<'){
            if((inPtr + 3 >= end && !loadNRet()) || inBuf[inPtr + 1] != (byte)'!' || inBuf[inPtr + 2] != (byte)'[')
               return false;
            inPtr += 3;
            for(int i = 0; i < 6; ++i){
               if(inPtr >= end)
                  assertMore();
               if(inBuf[inPtr++] != (byte)CDATA.charAt(i))
                  thInErr("Expected '[CDATA['");
            }
            skipCData();
         }else if(skipChars())
            return true;
      }
   }

   private final int decMB(int c, int ptr) throws XMLStreamException{
      int needed = 1;
      if((c & 0xE0) == 0xC0)
         c &= 0x1F;
      else if((c & 0xF0) == 0xE0){
         c &= 0xF;
         needed = 2;
      }else if((c & 0xF8) == 0xF0){
         c &= 7;
         needed = 3;
      }else
         badUTF(c);
      if(ptr >= end){
         assertMore();
         ptr = 0;
      }
      int d = (int)inBuf[ptr++];
      if((d & 0xC0) != 0x80){
         inPtr = ptr;
         badUTF(d);
      }
      c = c << 6 | d & 0x3F;
      if(needed > 1){
         if(ptr >= end){
            assertMore();
            ptr = 0;
         }
         if(((d = (int)inBuf[ptr++]) & 0xC0) != 0x80){
            inPtr = ptr;
            badUTF(d);
         }
         c = c << 6 | d & 0x3F;
         if(needed > 2){
            if(ptr >= end){
               assertMore();
               ptr = 0;
            }
            if(((d = (int)inBuf[ptr++]) & 0xC0) != 0x80){
               inPtr = ptr;
               badUTF(d);
            }
            c = -(c << 6 | d & 0x3F);
         }
      }
      inPtr = ptr;
      return c;
   }

   private final int decUTF_2(int c) throws XMLStreamException{
      if(inPtr >= end)
         assertMore();
      int d = (int)inBuf[inPtr++];
      if((d & 0xC0) != 0x80)
         badUTF(d);
      return (c & 0x1F) << 6 | d & 0x3F;
   }

   private final int decUTF_3(int c1) throws XMLStreamException{
      if(inPtr >= end)
         assertMore();
      int d = (int)inBuf[inPtr++];
      if((d & 0xC0) != 0x80)
         badUTF(d);
      int c = (c1 &= 0xF) << 6 | d & 0x3F;
      if(inPtr >= end)
         assertMore();
      if(((d = (int)inBuf[inPtr++]) & 0xC0) != 0x80)
         badUTF(d);
      c = c << 6 | d & 0x3F;
      if(c1 >= 0xD && ((c >= 0xD800 && c < 0xE000) || c == 0xFFFE || c == 0xFFFF))
         thC(c);
      return c;
   }

   private final int decUTF_3f(int c1) throws XMLStreamException{
      int d = (int)inBuf[inPtr++];
      if((d & 0xC0) != 0x80)
         badUTF(d);
      int c = (c1 &= 0xF) << 6 | d & 0x3F;
      if(((d = (int)inBuf[inPtr++]) & 0xC0) != 0x80)
         badUTF(d);
      c = c << 6 | d & 0x3F;
      if(c1 >= 0xD && ((c >= 0xD800 && c < 0xE000) || c == 0xFFFE || c == 0xFFFF))
         thC(c);
      return c;
   }

   private final int decUTF_4(int c) throws XMLStreamException{
      if(inPtr >= end)
         assertMore();
      int d = (int)inBuf[inPtr++];
      if((d & 0xC0) != 0x80)
         badUTF(d);
      c = (c & 7) << 6 | d & 0x3F;
      if(inPtr >= end)
         assertMore();
      if(((d = (int)inBuf[inPtr++]) & 0xC0) != 0x80)
         badUTF(d);
      c = c << 6 | d & 0x3F;
      if(inPtr >= end)
         assertMore();
      if(((d = (int)inBuf[inPtr++]) & 0xC0) != 0x80)
         badUTF(d);
      return (c << 6 | d & 0x3F) - 0x10000;
   }

   private final int decChr(byte b) throws XMLStreamException{
      int d, c = (int)b;
      if(c >= 0)
         return c;
      int needed = 1;
      if((c & 0xE0) == 0xC0)        // 2 bytes
         c &= 0x1F;
      else if((c & 0xF0) == 0xE0){  // 3 bytes
         c &= 0xF;
         needed = 2;
      }else if((c & 0xF8) == 0xF0){ // 4 bytes
         c &= 7;
         needed = 3;
      }else
         badUTF(c);
      if(((d = nxtB()) & 0xC0) != 0x80)
         badUTF(d);
      c = c << 6 | d & 0x3F;
      if(needed > 1){
         if(((d = nxtB()) & 0xC0) != 0x80)
            badUTF(d);
         c = c << 6 | d & 0x3F;
         if(needed > 2){
            if(((d = nxtB()) & 0xC0) != 0x80)
               badUTF(d);
            c = c << 6 | d & 0x3F;
         }
      }
      return c;
   }

   public Location loc(){ return new LocImpl(impl.pubId, impl.sysId, inPtr - rowOff, currRow, bOrC + inPtr); }

   final void close() throws IOException{
      if(in != null){
         in.close();
         in = null;
      }
   }

   final void Code(){
      super.Code();
      if(!syms.hashSh)
         impl.Code(syms);
      if(inBuf != null){
         impl.setBB(inBuf);
         inBuf = null;
      }
   }

   private final void badUTF(int mask) throws XMLStreamException{ thInErr(new StrB(12).a("Bad UTF8 ").apos(mask & 0xFF).toString()); }

   final int col(){ return inPtr - iniRawOff; }
}
