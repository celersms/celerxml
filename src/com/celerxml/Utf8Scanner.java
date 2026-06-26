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
   private int end, cTmp;
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

   private final void ln(){
      rowOff = inPtr;
      ++currRow;
   }

   final boolean more() throws XMLStreamException{
      bOrC += end;
      rowOff -= end;
      inPtr = 0;
      try{
         if(in == null || (end = in.read(inBuf, 0, 4096)) < 1){
            end = 0;
            return false;
         }
         return true;
      }catch(IOException ioe){
         throw new XMLStreamException(ioe);
      }
   }

   private final PN Code(int hash, int[] quads, int qlen, int lastQuadBytes) throws XMLStreamException{
      int lastQuad = 0, byteLen = (qlen << 2) + lastQuadBytes;
      if(lastQuadBytes < 4)
         quads[qlen] = (lastQuad = quads[qlen]) << ((4 - lastQuadBytes) << 3);
      int ch, ix = 1, needed = 1, cix = 0;
      boolean ok;
      char[] cbuf = nameBuf;
      final byte[] TYPES = chrT.NAM;
      switch(TYPES[ch = quads[0] >>> 24]){
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
               thErr(EOI);
            int q, ch2;
            if(((ch2 = (q = quads[0]) >> 16) & 0xC0) != 0x80 || needed > 1 && ((ch2 = q >> 8) & 0xC0) != 0x80 || needed > 2 && ((ch2 = q) & 0xC0) != 0x80)
               badUTF(ch2 & 0xFF);
            ok = Chr.is10NS(ch = ch << 6 | ch2 & 0x3F);
            if(needed > 2){
               cbuf[0] = (char)(0xD800 + ((ch -= 0x10000) >> 10));
               cix = 1;
               ch = 0xDC00 | ch & 0x3FF;
            }
      }
      if(!ok)
         thErr(ch);
      cbuf[cix++] = (char)ch;
      int lastColon = -1;
      while(ix < byteLen){
         switch(TYPES[ch = (quads[ix >> 2] >> ((ix++ & 3 ^ 3) << 3)) & 0xFF]){
            case 0:  // NAME_NONE
            case 4:  // MULTIBYTE_N
               ok = false;
               break;
            case 1:  // NAME_COLON
               if(lastColon >= 0)
                  thErr("Multiple ':'");
               lastColon = cix;
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
                  thErr(EOI);
               int ch2;
               if(((ch2 = quads[ix >> 2] >> ((ix++ & 3 ^ 3) << 3)) & 0xC0) != 0x80 || needed > 1 && ((ch2 = quads[ix >> 2] >> ((ix++ & 3 ^ 3) << 3)) & 0xC0) != 0x80
                     || needed > 2 && ((ch2 = quads[ix >> 2] >> ((ix++ & 3 ^ 3) << 3)) & 0xC0) != 0x80)
                  badUTF(ch2);
               ok = Chr.is10N(ch = ch << 6 | ch2 & 0x3F);
               if(needed > 2){
                  if(cix >= cbuf.length)
                     nameBuf = cbuf = xpand(cbuf);
                  cbuf[cix++] = (char)(0xD800 + ((ch -= 0x10000) >> 10));
                  ch = 0xDC00 | ch & 0x3FF;
               }
         }
         if(!ok)
            thErr(ch);
         if(cix >= cbuf.length)
            nameBuf = cbuf = xpand(cbuf);
         cbuf[cix++] = (char)ch;
      }
      if(lastQuadBytes < 4)
         quads[qlen] = lastQuad;
      return syms.add(hash, new String(cbuf, 0, cix), lastColon, quads, qlen + 1);
   }

   final int nxtFromProlog(boolean isProlog) throws XMLStreamException{
      if(inc)
         skipTok();
      iniRawOff = bOrC + inPtr;
      startRow = currRow;
      startCol = inPtr - rowOff;
      while(true){
         if(inPtr >= end && !more()){
            iniRawOff = bOrC;
            startRow = currRow;
            startCol = -rowOff;
            return -1;
         }
         int c;
         switch(c = inBuf[inPtr++] & 0xFF){
            case '<':
               if(inPtr >= end)
                  assertMore();
               byte b;
               if((b = inBuf[inPtr++]) == (byte)'!')
                  return doPrologDecl(isProlog);
               if(b == (byte)'?')
                  return doPIStart();
               if(b == (byte)'/' || !isProlog)
                  thUnxp(b, isProlog);
               return startElem(b);
            case '\r':
               if(inPtr >= end && !more()){
                  iniRawOff = bOrC + (rowOff = inPtr);
                  startRow = ++currRow;
                  startCol = 0;
                  return -1;
               }
               if(inBuf[inPtr] == (byte)'\n')
                  ++inPtr;
            case '\n':
               ln();
            case 0x20:
            case '\t':
               continue;
            default:
               thUnxp(isProlog, decChr((byte)c));
         }
      }
   }

   final int nxtFromTree() throws XMLStreamException{
      if(inc){
         if(skipTok()){
            reset();
            return currTok = 9; // ENTITY_REFERENCE
         }
      }else if(currTok == 1){ // START_ELEMENT
         if(empty){
            --depth;
            return currTok = 2; // END_ELEMENT
         }
      }else if(currTok == 2){ // END_ELEMENT
         curr = curr.nxt;
         while(lastNs != null && lastNs.lvl >= depth)
            lastNs = lastNs.Code();
      }else if(pend){
         pend = false;
         reset();
         return currTok = 9; // ENTITY_REFERENCE
      }
      iniRawOff = bOrC + inPtr;
      startRow = currRow;
      startCol = inPtr - rowOff;
      if(inPtr >= end && !more()){
         iniRawOff = bOrC;
         startCol = -rowOff;
         return -1;
      }
      byte b;
      if((b = inBuf[inPtr]) == (byte)'<'){
         if((b = ++inPtr < end ? inBuf[inPtr++] : loadOne()) == (byte)'!')
            return commOrCdataStart();
         if(b == (byte)'?')
            return doPIStart();
         return b == (byte)'/' ? doEndE() : startElem(b);
      }
      int i;
      if((i = b & 0xFF) == '&'){
         ++inPtr;
         if((i = -entInTxt()) == 0)
            return currTok = 9; // ENTITY_REFERENCE
      }
      cTmp = i;
      if(lazy)
         inc = true;
      else
         endC();
      return currTok = 4; // CHARACTERS
   }

   private final int doPrologDecl(boolean isProlog) throws XMLStreamException{
      if(inPtr >= end)
         assertMore();
      byte b;
      if((b = inBuf[inPtr++]) == (byte)'-'){
         if(inPtr >= end)
            assertMore();
         if((b = inBuf[inPtr++]) == (byte)'-'){
            if(lazy)
               inc = true;
            else
               endComm();
            return currTok = 5; // COMMENT
         }
      }else if(b == (byte)'D' && isProlog){
         doDtdStart();
         if(!lazy && inc){
            endDTD();
            inc = false;
         }
         return 11; // DTD
      }
      inc = true;
      currTok = 4; // CHARACTERS
      thUnxp(isProlog, decChr(b));
      return 0;
   }

   private final int doDtdStart() throws XMLStreamException{
      Code("DOCTYPE");
      tokName = parsePN(Code(true));
      byte q, b;
      if((b = Code(false)) == (byte)'P'){
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
            int c;
            if(TYPES[c = b & 0xFF] != 1) // PUBID_OK
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
         dtdPub = new String(outputBuffer, 0, outPtr);
         dtdSys = Code(Code(true));
         b = Code(false);
      }else if(b == (byte)'S'){
         Code("SYSTEM");
         dtdPub = null;
         dtdSys = Code(Code(true));
         b = Code(false);
      }else
         dtdPub = dtdSys = null;
      if(b == (byte)'>'){
         inc = false;
         return currTok = 11; // DTD
      }
      if(b != (byte)'[')
         thUnxp(decChr(b), ", expected '[' or '>'");
      inc = true;
      return currTok = 11; // DTD
   }

   private final int commOrCdataStart() throws XMLStreamException{
      if(inPtr >= end)
         assertMore();
      byte b;
      if((b = inBuf[inPtr++]) == (byte)'-'){
         if(inPtr >= end)
            assertMore();
         if(inBuf[inPtr++] != (byte)'-')
            thErr("Expected '-'");
         if(lazy)
            inc = true;
         else
            endComm();
         return currTok = 5; // COMMENT
      }
      if(b == (byte)'['){
         currTok = 12; // CDATA
         for(int i = 0; i < 6; ++i){
            if(inPtr >= end)
               assertMore();
            if(inBuf[inPtr++] != (byte)CDATA.charAt(i))
               thErr("Expected '[CDATA['");
         }
         if(lazy)
            inc = true;
         else
            endCData();
         return 12; // CDATA
      }
      thErr("Expected '-' or '[CDATA['");
      return 0;
   }

   private final int doPIStart() throws XMLStreamException{
      currTok = 3; // PROCESSING_INSTRUCTION
      if(inPtr >= end)
         assertMore();
      byte b;
      tokName = parsePN(b = inBuf[inPtr++]);
      String ln;
      if((ln = tokName.ln).length() == 3 && ln.equalsIgnoreCase("xml") && tokName.pfx == null)
         thErr("Target 'xml' reserved");
      if(inPtr >= end)
         assertMore();
      int c;
      if((c = inBuf[inPtr++] & 0xFF) <= 0x20){
         while(true){
            if(c == '\n')
               ln();
            else if(c == '\r'){
               if(inPtr >= end)
                  assertMore();
               if(inBuf[inPtr] == (byte)'\n')
                  ++inPtr;
               ln();
            }else if(c != 0x20 && c != '\t')
               thC(c);
            if(inPtr >= end)
               assertMore();
            if((c = inBuf[inPtr] & 0xFF) > 0x20)
               break;
            ++inPtr;
         }
         if(lazy)
            inc = true;
         else
            endPI();
      }else{
         if(c != '?')
            thUnxp(decChr((byte)c));
         if(inPtr >= end)
            assertMore();
         if((b = inBuf[inPtr++]) != (byte)'>')
            thUnxp(decChr(b));
         reset();
         inc = false;
      }
      return 3; // PROCESSING_INSTRUCTION
   }

   private final int chrEnt() throws XMLStreamException{
      if(inPtr >= end)
         assertMore();
      byte b;
      int c, value = 0;
      if((b = inBuf[inPtr++]) == (byte)'x')
         while(true){
            if(inPtr >= end)
               assertMore();
            if((b = inBuf[inPtr++]) == (byte)';')
               break;
            if((c = (((int)b | 0x20) - 0x30)) > 9) // to lowercase
               c -= 0x27;
            if(c < 0 || c > 0xF)
               thUnxp(decChr(b), ", not a hex digit");
            value = value << 4 | c;
         }
      else
         while(b != (byte)';'){
            if(b < (byte)'0' || b > (byte)'9')
               thUnxp(decChr(b), ", not a dec digit");
            value = value * 10 + b - '0';
            if(inPtr >= end)
               assertMore();
            b = inBuf[inPtr++];
         }
      if((value >= 0xD800 && value < 0xE000) || value == 0 || value == 0xFFFE || value == 0xFFFF)
         thC(value);
      return value;
   }

   private final int doEndE() throws XMLStreamException{
      --depth;
      currTok = 2; // END_ELEMENT
      tokName = curr.Code;
      int size, ptr;
      if(end - (ptr = inPtr) < ((size = tokName.len()) << 2) + 1)
         return doEndESlow(size);
      final byte[] buf = inBuf;
      --size;
      for(int qix = 0; qix < size; ++qix)
         if((buf[ptr++] << 24 | (buf[ptr++] & 0xFF) << 16 | (buf[ptr++] & 0xFF) << 8 | buf[ptr++] & 0xFF) != tokName.Code(qix)){
            inPtr = ptr;
            thUnxp(tokName.Code);
         }
      int lQ, q;
      if((q = buf[ptr++] & 0xFF) != (lQ = tokName.Code(size)) && (q = q << 8 | buf[ptr++] & 0xFF) != lQ && (q = q << 8 | buf[ptr++] & 0xFF) != lQ && (q << 8 | buf[ptr++] & 0xFF) != lQ){
         inPtr = ptr;
         thUnxp(tokName.Code);
      }
      q = buf[ptr] & 0xFF;
      inPtr = ptr + 1;
      while(q <= 0x20){
         if(q == '\n')
            ln();
         else if(q == '\r'){
            byte b;
            if((b = inPtr < end ? buf[inPtr++] : loadOne()) != (byte)'\n'){
               rowOff = inPtr - 1;
               ++currRow;
               q = b & 0xFF;
               continue;
            }
            ln();
         }else if(q != 0x20 && q != '\t')
            thC(q);
         q = (inPtr < end ? buf[inPtr++] : loadOne()) & 0xFF;
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
            thUnxp(tokName.Code);
      }
      int lQ = tokName.Code(size), q = 0, i = 0;
      while(true){
         if(inPtr >= end)
            assertMore();
         if((q = q << 8 | inBuf[inPtr++] & 0xFF) == lQ){
            if(inPtr >= end)
               assertMore();
            q = inBuf[inPtr++];
            while(q <= 0x20){
               if(q == '\n')
                  ln();
               else if(q == '\r'){
                  byte b;
                  if((b = inPtr < end ? inBuf[inPtr++] : loadOne()) != (byte)'\n'){
                     rowOff = inPtr - 1;
                     ++currRow;
                     q = b & 0xFF;
                     continue;
                  }
                  ln();
               }else if(q != 0x20 && q != '\t')
                  thC(q);
               q = (inPtr < end ? inBuf[inPtr++] : loadOne()) & 0xFF;
            }
            if(q != '>')
               thUnxp(decChr((byte)q), ", not space or closing '>'");
            return 2; // END_ELEMENT
         }
         if(++i > 3)
            thUnxp(tokName.Code);
      }
   }

   private final PN parsePN(byte b) throws XMLStreamException{
      if(end - inPtr < 8)
         return parsePNSlow(b);
      int q, i2;
      if((q = b & 0xFF) < 'A')
         thUnxp(q, ", not a name start");
      final byte[] buf = inBuf;
      if((i2 = buf[inPtr++] & 0xFF) < 45 || (i2 > 58 && i2 < 65) || i2 == 47)
         return findPName(q, 1);
      q = q << 8 | i2;
      if((i2 = buf[inPtr++] & 0xFF) < 45 || (i2 > 58 && i2 < 65) || i2 == 47)
         return findPName(q, 2);
      q = q << 8 | i2;
      if((i2 = buf[inPtr++] & 0xFF) < 45 || (i2 > 58 && i2 < 65) || i2 == 47)
         return findPName(q, 3);
      q = q << 8 | i2;
      if((i2 = buf[inPtr++] & 0xFF) < 45 || (i2 > 58 && i2 < 65) || i2 == 47)
         return findPName(q, 4);
      int q2 = i2;
      if((i2 = buf[inPtr++] & 0xFF) < 45 || (i2 > 58 && i2 < 65) || i2 == 47)
         return findPName(q, q2, 1);
      q2 = q2 << 8 | i2;
      if((i2 = buf[inPtr++] & 0xFF) < 45 || (i2 > 58 && i2 < 65) || i2 == 47)
         return findPName(q, q2, 2);
      q2 = q2 << 8 | i2;
      if((i2 = buf[inPtr++] & 0xFF) < 45 || (i2 > 58 && i2 < 65) || i2 == 47)
         return findPName(q, q2, 3);
      q2 = q2 << 8 | i2;
      if((i2 = buf[inPtr++] & 0xFF) < 45 || (i2 > 58 && i2 < 65) || i2 == 47)
         return findPName(q, q2, 4);
      int[] quads = qBuf;
      quads[0] = q;
      quads[1] = q2;
      q = 2;
      while(true){
         if(inPtr >= end)
            assertMore();
         if((q2 = buf[inPtr++] & 0xFF) < 45 || (q2 > 58 && q2 < 65) || q2 == 47)
            return findPName(i2, quads, q, 1);
         for(int xx = 2; xx <= 4; xx++){
            i2 = i2 << 8 | q2;
            if((q2 = (inPtr < end ? buf[inPtr++] : loadOne()) & 0xFF) < 45 || (q2 > 58 && q2 < 65) || q2 == 47)
               return findPName(i2, quads, q, xx);
         }
         int len;
         if(q >= (len = quads.length))
            System.arraycopy(quads, 0, qBuf = quads = new int[len + len], 0, len);
         quads[q++] = i2;
         i2 = q2;
      }
   }

   private final PN parsePNSlow(byte b) throws XMLStreamException{
      int q;
      if((q = b & 0xFF) < 'A')
         thUnxp(q, ", not a name start");
      int[] quads = qBuf;
      int qix = 0, firstQuad = 0;
      while(true){
         if(inPtr >= end)
            assertMore();
         int i2, len;
         if((i2 = inBuf[inPtr++] & 0xFF) < 45 || (i2 > 58 && i2 < 65) || i2 == 47)
            return findPName(q, 1, firstQuad, qix, quads);
         for(int xx = 2; xx <= 4; xx++){
            q = q << 8 | i2;
            if((i2 = (inPtr < end ? inBuf[inPtr++] : loadOne()) & 0xFF) < 45 || (i2 > 58 && i2 < 65) || i2 == 47)
               return findPName(q, xx, firstQuad, qix, quads);
         }
         if(qix == 0)
            firstQuad = q;
         else if(qix == 1){
            quads[0] = firstQuad;
            quads[1] = q;
         }else{
            if(qix >= (len = quads.length))
               System.arraycopy(quads, 0, qBuf = quads = new int[len + len], 0, len);
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
      PN name;
      if((name = syms.find(hash ^= hash >>> 8, onlyQuad, 0)) == null){
         qBuf[0] = onlyQuad;
         name = Code(hash, qBuf, 0, lastByteCount);
      }
      return name;
   }

   private final PN findPName(int firstQuad, int secondQuad, int lastByteCount) throws XMLStreamException{
      --inPtr;
      int hash = firstQuad * 31 + secondQuad;
      hash ^= hash >>> 16;
      PN name;
      if((name = syms.find(hash ^= hash >>> 8, firstQuad, secondQuad)) == null){
         qBuf[0] = firstQuad;
         qBuf[1] = secondQuad;
         name = Code(hash, qBuf, 1, lastByteCount);
      }
      return name;
   }

   private final PN findPName(int lastQuad, int[] quads, int qlen, int lastByteCount) throws XMLStreamException{
      --inPtr;
      int ll;
      if(qlen >= (ll = quads.length))
         System.arraycopy(quads, 0, qBuf = quads = new int[ll + ll], 0, ll);
      quads[qlen++] = lastQuad;
      ll = quads[0];
      for(int i = 1; i < qlen; ++i)
         ll = ll * 31 + quads[i];
      ll ^= ll >>> 16;
      PN name;
      if((name = syms.find(ll ^= ll >>> 8, quads, qlen)) == null)
         name = Code(ll, quads, qlen - 1, lastByteCount);
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
      byte b;
      if(((b = inBuf[inPtr++]) & 0xFF) > 0x20){
         if(!reqd)
            return b;
         thUnxp(decChr(b), ", expected white space");
      }
      do{
         if(b == (byte)'\n')
            ln();
         else if(b == (byte)'\r'){
            if(inPtr >= end)
               assertMore();
            if(inBuf[inPtr] == (byte)'\n')
               ++inPtr;
            ln();
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
         byte b;
         if((b = inBuf[inPtr++]) != (byte)kw.charAt(i))
            thUnxp(decChr(b), new StrB(18).a(", expected ").a(kw).toString());
      }
   }

   private final int inTree(int c) throws XMLStreamException{
      if(c == '\r'){
         if(inPtr >= end && !more()){
            indent(0, (char)0);
            return -1;
         }
         if(inBuf[inPtr] == (byte)'\n')
            ++inPtr;
      }
      ln();
      if(inPtr >= end)
         assertMore();
      byte b;
      if((b = inBuf[inPtr]) != (byte)' ' && b != 9){
         if(b == (byte)'<' && inPtr + 1 < end && inBuf[inPtr + 1] != (byte)'!'){
            indent(0, (char)0);
            return -1;
         }
         reset()[0] = '\n';
         return currSz = 1;
      }
      ++inPtr;
      int count = 0, max = b == (byte)' ' ? 32 : 8;
      while(++count <= max){
         if(inPtr >= end)
            assertMore();
         byte b2;
         if((b2 = inBuf[inPtr]) != b){
            if(b2 == (byte)'<' && inPtr + 1 < end && inBuf[inPtr + 1] != (byte)'!'){
               indent(count, (char)b);
               return -1;
            }
            break;
         }
         ++inPtr;
      }
      final char[] outBuf = reset();
      outBuf[0] = '\n';
      for(int i = 1; i <= count; ++i)
         outBuf[i] = (char)b;
      return currSz = ++count;
   }

   private final int prolog(int c) throws XMLStreamException{
      if(c == '\r'){
         if(inPtr >= end && !more()){
            indent(0, (char)0);
            return -1;
         }
         if(inBuf[inPtr] == (byte)'\n')
            ++inPtr;
      }
      ln();
      if(inPtr >= end && !more()){
         indent(0, (char)0);
         return -1;
      }
      byte b;
      if((b = inBuf[inPtr]) != (byte)' ' && b != 9){
         if(b == (byte)'<'){
            indent(0, (char)0);
            return -1;
         }
         reset()[0] = '\n';
         return currSz = 1;
      }
      int count = 1, max = b == (byte)' ' ? 32 : 8;
      while((++inPtr < end || more()) && inBuf[inPtr] == b)
         if(++count >= max){
            char[] outBuf = reset();
            outBuf[0] = '\n';
            for(int i = 1; i <= count; ++i)
                outBuf[i] = (char)b;
            return currSz = ++count;
         }
      indent(count, (char)b);
      return -1;
   }

   private final int nxtB() throws XMLStreamException{
      if(inPtr >= end && !more())
         thErr(EOI);
      int d;
      if(((d = inBuf[inPtr++]) & 0xC0) != 0x80)
         badUTF(d);
      return d & 0x3F;
   }

   private final byte loadOne() throws XMLStreamException{
      if(!more())
         thErr(EOI);
      return inBuf[inPtr++];
   }

   private final boolean loadNRet() throws XMLStreamException{
      bOrC += inPtr;
      rowOff -= inPtr;
      System.arraycopy(inBuf, inPtr, inBuf, 0, end -= inPtr);
      inPtr = 0;
      try{
         do{
            int xx;
            if(in == null || (xx = in.read(inBuf, end, 4096 - end)) < 1)
               return false;
            end += xx;
         }while(end < 3);
         return true;
      }catch(IOException ioe){
         throw new XMLStreamException(ioe);
      }
   }

   final void endTok() throws XMLStreamException{
      inc = false;
      switch(currTok){
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
      currTok = 1; // START_ELEMENT
      nsCnt = 0;
      PN elemName;
      String prefix;
      boolean allBound = true;
      if((prefix = (elemName = parsePN(b)).pfx) != null){
         elemName = bindName(elemName, prefix);
         allBound = elemName.isBound();
      }
      tokName = elemName;
      curr = new Node(elemName, curr);
      final byte[] buf = inBuf;
      int attrPtr = 0;
      while(true){
         if(inPtr >= end)
            assertMore();
         int c;
         if((c = (b = buf[inPtr++]) & 0xFF) <= 0x20)
            do{
               if(c == '\n')
                  ln();
               else if(c == '\r'){
                  if(inPtr >= end)
                     assertMore();
                  if(buf[inPtr] == (byte)'\n')
                     ++inPtr;
                  ln();
               }else if(c != 0x20 && c != '\t')
                  thC(c);
               if(inPtr >= end)
                  assertMore();
            }while((c = (b = buf[inPtr++]) & 0xFF) <= 0x20);
         else if(c != '/' && c != '>')
            thUnxp(decChr(b), ", not space or '>' or '/>'");
         if(c == '/'){
            if(inPtr >= end)
               assertMore();
            if((b = buf[inPtr++]) != (byte)'>')
               thUnxp(decChr(b), ", not '>'");
            empty = true;
            break;
         }
         if(c == '>'){
            empty = false;
            break;
         }
         if(c == '<')
            thErr("Unexpected '<'");
         boolean isNsDecl = true;
         PN attrName;
         if((prefix = (attrName = parsePN(b)).pfx) == null)
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
            if((c = (b = buf[inPtr++]) & 0xFF) > 0x20)
               break;
            if(c == '\n')
               ln();
            else if(c == '\r'){
               if(inPtr >= end)
                  assertMore();
               if(buf[inPtr] == (byte)'\n')
                  ++inPtr;
               ln();
            }else if(c != 0x20 && c != '\t')
               thC(c);
         }
         if(c != '=')
            thUnxp(decChr(b), ", not '='");
         while(true){
            if(inPtr >= end)
               assertMore();
            if((c = (b = buf[inPtr++]) & 0xFF) > 0x20)
               break;
            if(c == '\n')
               ln();
            else if(c == '\r'){
               if(inPtr >= end)
                  assertMore();
               if(buf[inPtr] == (byte)'\n')
                  ++inPtr;
               ln();
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
      int act;
      if((act = endLastV(attrPtr)) < 0)
         thErr(err);
      attrCnt = act;
      ++depth;
      if(!allBound){
         if(!elemName.isBound())
            thUnb(tokName, false);
         for(int i = 0, len = attrCnt; i < len; ++i){
            PN attrName;
            if(!(attrName = names[i]).isBound())
               thUnb(attrName, true);
         }
      }
      return 1; // START_ELEMENT
   }

   private final int Code(int attrPtr, byte quoteByte, PN attrName) throws XMLStreamException{
      char[] attrBuffer = startNewV(attrName, attrPtr);
      final byte[] TYPES = chrT.ATT;
      int c = 0;
      while(true){
         int ptr = inPtr;
adv:     while(true){
            if(ptr >= end){
               assertMore();
               ptr = 0;
            }
            if(attrPtr >= attrBuffer.length)
               attrBuffer = vals = xpand(vals);
            int max, max2;
            if((max2 = ptr + attrBuffer.length - attrPtr) < (max = end))
               max = max2;
            while(ptr < max){
               if(TYPES[c = inBuf[ptr++] & 0xFF] != 0)
                  break adv;
               attrBuffer[attrPtr++] = (char)c;
            }
         }
         inPtr = ptr;
         switch(TYPES[c]){
            case 2:  // WS_CR
               if(ptr >= end)
                  assertMore();
               if(inBuf[inPtr] == (byte)'\n')
                  ++inPtr;
            case 3:  // WS_LF
               ln();
            case 8:  // WS_TAB
               c = 0x20;
               break;
            case 5:  // MULTIBYTE_2
               c = dec2(c);
               break;
            case 6:  // MULTIBYTE_3
               c = dec3(c);
               break;
            case 7:  // MULTIBYTE_4
               attrBuffer[attrPtr++] = (char)(0xD800 | (c = dec4(c)) >> 10);
               c = 0xDC00 | c & 0x3FF;
               if(attrPtr >= attrBuffer.length)
                  attrBuffer = vals = xpand(vals);
               break;
            case 10: // AMP
               if((c = entInTxt()) == 0)
                  thC();
               if((c >> 16) != 0){
                  attrBuffer[attrPtr++] = (char)(0xD800 | (c -= 0x10000) >> 10);
                  c = 0xDC00 | c & 0x3FF;
                  if(attrPtr >= attrBuffer.length)
                     attrBuffer = vals = xpand(vals);
               }
               break;
            case 14: // ATTR_QUOTE
               if(c == quoteByte)
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
         byte b;
         if((b = inBuf[inPtr++]) == quoteByte){
            bindNs(name, attrPtr == 0 ? "" : impl.Code(attrBuffer, attrPtr));
            return;
         }
         if(b == (byte)'&'){
            if((c = entInTxt()) == 0)
               thC();
            if((c >> 16) != 0){
               if(attrPtr >= attrBuffer.length)
                  nameBuf = attrBuffer = xpand(attrBuffer);
               attrBuffer[attrPtr++] = (char)(0xD800 | (c -= 0x10000) >> 10);
               c = 0xDC00 | (c & 0x3FF);
            }
         }else if(b == (byte)'<')
            thUnxp(b, " in attribute value");
         else if((c = b & 0xFF) < 0x20){
            if(c == '\n')
               ln();
            else if(c == '\r'){
               if(inPtr >= end)
                  assertMore();
               if(inBuf[inPtr] == (byte)'\n')
                  ++inPtr;
               ln();
            }else if(c != '\t')
               thC(c);
         }else if(c > 0x7F && (c = dec(c, inPtr)) < 0){
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
      byte b;
      if((b = inBuf[inPtr++]) == (byte)'#')
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
         int c;
         switch(TYPES[c = b & 0xFF]){
            case 0:  // NAME_NONE
            case 1:  // NAME_COLON
            case 2:  // NAME_NONFIRST
               ok = cix > 0;
               break;
            case 3:  // NAME_ANY
               ok = true;
               break;
            case 5:  // MULTIBYTE_2
               ok = Chr.is10NS(c = dec2(c));
               break;
            case 6:  // MULTIBYTE_3
               ok = Chr.is10NS(c = dec3(c));
               break;
            case 7:  // MULTIBYTE_4
               ok = Chr.is10NS(c = dec4(c));
               if(ok){
                  if(cix >= cbuf.length)
                     nameBuf = cbuf = xpand(cbuf);
                  cbuf[cix++] = (char)(0xD800 | (c -= 0x10000) >> 10);
                  c = 0xDC00 | c & 0x3FF;
               }
         }
         if(!ok)
            thErr(c);
         if(cix >= cbuf.length)
            nameBuf = cbuf = xpand(cbuf);
         cbuf[cix++] = (char)c;
         if(inPtr >= end)
            assertMore();
         b = inBuf[inPtr++];
      }
      if(impl.Code(16))
         thErr("Entity ref. in entity expanding mode");
      String pname;
      tokName = new PN(pname = new String(cbuf, 0, cix), null, pname, 0);
      return 0;
   }

   private final String Code(int quote) throws XMLStreamException{
      final byte[] TYPES = chrT.ATT;
      char[] outputBuffer = nameBuf;
      int outPtr = 0;
      while(true){
         if(inPtr >= end)
            assertMore();
         int c;
         if((c = inBuf[inPtr++] & 0xFF) == quote)
            return new String(outputBuffer, 0, outPtr);
         switch(TYPES[c]){
            case 2:  // WS_CR
               if(inPtr >= end)
                  assertMore();
               if(inBuf[inPtr] == (byte)'\n')
                  ++inPtr;
               c = '\n';
            case 3:  // WS_LF
               ln();
               break;
            case 5:  // MULTIBYTE_2
               c = dec2(c);
               break;
            case 6:  // MULTIBYTE_3
               c = dec3(c);
               break;
            case 7:  // MULTIBYTE_4
               if(outPtr >= outputBuffer.length){
                  outputBuffer = endSeg();
                  outPtr = 0;
               }
               outputBuffer[outPtr++] = (char)(0xD800 | (c = dec4(c)) >> 10);
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
adv:     while(true){
            if(ptr >= max){
               assertMore();
               ptr = 0;
               max = end;
            }
            while(ptr < max)
               if(TYPES[c = inputBuffer[ptr++] & 0xFF] != 0)
                  break adv;
         }
         inPtr = ptr;
         switch(TYPES[c]){
            case 2:  // WS_CR
               if(ptr >= end)
                  assertMore();
               if(inputBuffer[inPtr] == (byte)'\n')
                  ++inPtr;
            case 3:  // WS_LF
               ln();
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
                  thUnxp();
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
adv:     while(true){
            if(ptr >= max){
               assertMore();
               ptr = 0;
               max = end;
            }
            while(ptr < max)
               if(TYPES[c = inputBuffer[ptr++] & 0xFF] != 0)
                  break adv;
         }
         inPtr = ptr;
         switch(TYPES[c]){
            case 2:  // WS_CR
               if(ptr >= end)
                  assertMore();
               if(inputBuffer[inPtr] == (byte)'\n')
                  ++inPtr;
            case 3:  // WS_LF
               ln();
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
                     thErr();
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
adv:     while(true){
            if(ptr >= max){
               assertMore();
               ptr = 0;
               max = end;
            }
            while(ptr < max)
               if(TYPES[c = inputBuffer[ptr++] & 0xFF] != 0)
                  break adv;
         }
         inPtr = ptr;
         switch(TYPES[c]){
            case 2:  // WS_CR
               if(ptr >= end)
                  assertMore();
               if(inputBuffer[inPtr] == (byte)'\n')
                  ++inPtr;
            case 3:  // WS_LF
               ln();
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
adv:     while(true){
            if(ptr >= max){
               assertMore();
               ptr = 0;
               max = end;
            }
            while(ptr < max)
               if(TYPES[c = inputBuffer[ptr++] & 0xFF] != 0)
                  break adv;
         }
         inPtr = ptr;
         switch(TYPES[c]){
            case 2:  // WS_CR
               if(ptr >= end)
                  assertMore();
               if(inputBuffer[inPtr] == (byte)'\n')
                  ++inPtr;
            case 3:  // WS_LF
               ln();
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
         int c;
         if((c = inputBuffer[ptr] & 0xFF) > 0x20)
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
      int c;
      if(((c = inBuf[inPtr++]) & 0xC0) != 0x80)
         badUTF(c);
   }

   private final void skipUTF_3(int c) throws XMLStreamException{
      if(inPtr >= end)
         assertMore();
      int d, e;
      if(((d = inBuf[inPtr++]) & 0xC0) != 0x80)
         badUTF(d);
      if(inPtr >= end)
         assertMore();
      if(((e = inBuf[inPtr++]) & 0xC0) != 0x80)
         badUTF(e);
      if((c &= 0xF) >= 0xD && (((c = (c << 6 | d & 0x3F) << 6 | e & 0x3F) >= 0xD800 && c < 0xE000) || c == 0xFFFE || c == 0xFFFF))
         thC(c);
   }

   private final void skipUTF_4() throws XMLStreamException{
      int d;
      if(inPtr + 4 > end){
         if(inPtr >= end)
            assertMore();
         if(((d = inBuf[inPtr++]) & 0xC0) != 0x80)
            badUTF(d);
         if(inPtr >= end)
            assertMore();
         if(((d = inBuf[inPtr++]) & 0xC0) != 0x80)
            badUTF(d);
         if(inPtr >= end)
            assertMore();
      }else if(((d = inBuf[inPtr++]) & 0xC0) != 0x80 || ((d = inBuf[inPtr++]) & 0xC0) != 0x80)
         badUTF(d);
      if(((d = inBuf[inPtr++]) & 0xC0) != 0x80)
         badUTF(d);
   }

   private final void endCData() throws XMLStreamException{
      final byte[] TYPES = chrT.OTH, buf = inBuf;
      char[] outputBuffer = reset();
      int outPtr = 0, c = 0;
      while(true){
         int ptr = inPtr;
adv:     while(true){
            if(ptr >= end){
               assertMore();
               ptr = 0;
            }
            if(outPtr >= outputBuffer.length){
               outputBuffer = endSeg();
               outPtr = 0;
            }
            int max, max2;
            if((max2 = ptr + outputBuffer.length - outPtr) < (max = end))
               max = max2;
            while(ptr < max){
               if(TYPES[c = buf[ptr++] & 0xFF] != 0)
                  break adv;
               outputBuffer[outPtr++] = (char)c;
            }
         }
         inPtr = ptr;
         switch(TYPES[c]){
            case 2:  // WS_CR
               if(ptr >= end)
                  assertMore();
               if(buf[inPtr] == (byte)'\n')
                  ++inPtr;
               c = '\n';
            case 3:  // WS_LF
               ln();
               break;
            case 5:  // MULTIBYTE_2
               c = dec2(c);
               break;
            case 6:  // MULTIBYTE_3
               c = dec3(c);
               break;
            case 7:  // MULTIBYTE_4
               outputBuffer[outPtr++] = (char)(0xD800 | (c = dec4(c)) >> 10);
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
               boolean ok;
               if(ok = b == (byte)'>' && count >= 1)
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
                  currSz = outPtr;
                  if(cls && !pend)
                     endClsTxt();
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
      final byte[] TYPES = chrT.TXT, buf = inBuf;
      while(true){
         int ptr = inPtr;
adv:     while(true){
            if(ptr >= end){
               assertMore();
               ptr = 0;
            }
            if(outPtr >= outputBuffer.length){
               outputBuffer = endSeg();
               outPtr = 0;
            }
            int max, max2;
            if((max2 = ptr + outputBuffer.length - outPtr) < (max = end))
               max = max2;
            while(ptr < max){
               if(TYPES[c = buf[ptr++] & 0xFF] != 0)
                  break adv;
               outputBuffer[outPtr++] = (char)c;
            }
         }
         inPtr = ptr;
         switch(TYPES[c]){
            case 2:  // WS_CR
               if(ptr >= end)
                  assertMore();
               if(buf[inPtr] == (byte)'\n')
                  ++inPtr;
               c = '\n';
            case 3:  // WS_LF
               ln();
               break;
            case 5:  // MULTIBYTE_2
               c = dec2(c);
               break;
            case 6:  // MULTIBYTE_3
               c = end - ptr >= 2 ? dec3f(c) : dec3(c);
               break;
            case 7:  // MULTIBYTE_4
               outputBuffer[outPtr++] = (char)(0xD800 | (c = dec4(c)) >> 10);
               if(outPtr >= outputBuffer.length){
                  outputBuffer = endSeg();
                  outPtr = 0;
               }
               c = 0xDC00 | c & 0x3FF;
               break;
            case 9:  // LT
               --inPtr;
               currSz = outPtr;
               if(cls && !pend)
                  endClsTxt();
               return;
            case 10: // AMP
               if((c = entInTxt()) == 0){
                  pend = true;
                  currSz = outPtr;
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
               int count = 0;
               byte b;
               while(true){
                  if(inPtr >= end)
                     assertMore();
                  if((b = buf[inPtr]) != (byte)']')
                     break;
                  ++inPtr;
                  ++count;
               }
               if(b == (byte)'>' && count > 0)
                  thUnxp();
               while(count > 0){
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
      final byte[] TYPES = chrT.OTH, buf = inBuf;
      char[] outputBuffer = reset();
      int outPtr = 0, c = 0;
      while(true){
         int ptr = inPtr;
adv:     while(true){
            if(ptr >= end){
               assertMore();
               ptr = 0;
            }
            if(outPtr >= outputBuffer.length){
               outputBuffer = endSeg();
               outPtr = 0;
            }
            int max, max2;
            if((max2 = ptr + outputBuffer.length - outPtr) < (max = end))
               max = max2;
            while(ptr < max){
               if(TYPES[c = buf[ptr++] & 0xFF] != 0)
                  break adv;
               outputBuffer[outPtr++] = (char)c;
            }
         }
         inPtr = ptr;
         switch(TYPES[c]){
            case 2:  // WS_CR
               if(ptr >= end)
                  assertMore();
               if(buf[inPtr] == (byte)'\n')
                  ++inPtr;
               c = '\n';
            case 3:  // WS_LF
               ln();
               break;
            case 5:  // MULTIBYTE_2
               c = dec2(c);
               break;
            case 6:  // MULTIBYTE_3
               c = dec3(c);
               break;
            case 7:  // MULTIBYTE_4
               outputBuffer[outPtr++] = (char)(0xD800 | (c = dec4(c)) >> 10);
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
                     thErr();
                  currSz = outPtr;
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
adv:     while(true){
            if(ptr >= end){
               assertMore();
               ptr = 0;
            }
            if(outPtr >= outputBuffer.length){
               outputBuffer = endSeg();
               outPtr = 0;
            }
            int max, max2;
            if((max2 = ptr + outputBuffer.length - outPtr) < (max = end))
               max = max2;
            while(ptr < max){
               if(TYPES[c = inBuf[ptr++] & 0xFF] != 0)
                  break adv;
               outputBuffer[outPtr++] = (char)c;
            }
         }
         inPtr = ptr;
         switch(TYPES[c]){
            case 2:  // WS_CR
               if(ptr >= end)
                  assertMore();
               if(inBuf[inPtr] == (byte)'\n')
                  ++inPtr;
               c = '\n';
            case 3:  // WS_LF
               ln();
               break;
            case 5:  // MULTIBYTE_2
               c = dec2(c);
               break;
            case 6:  // MULTIBYTE_3
               c = dec3(c);
               break;
            case 7:  // MULTIBYTE_4
               outputBuffer[outPtr++] = (char)(0xD800 | (c = dec4(c)) >> 10);
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
                  currSz = outPtr;
                  byte b;
                  if((b = Code(false)) != (byte)'>')
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
adv:     while(true){
            if(ptr >= end){
               assertMore();
               ptr = 0;
            }
            int max = end;
            while(ptr < max)
               if(TYPES[c = inBuf[ptr++] & 0xFF] != 0)
                  break adv;
         }
         inPtr = ptr;
         switch(TYPES[c]){
            case 2:  // WS_CR
               if(ptr >= end)
                  assertMore();
               if(inBuf[inPtr] == (byte)'\n')
                  ++inPtr;
               c = '\n';
            case 3:  // WS_LF
               ln();
               continue;
            case 5:  // MULTIBYTE_2
               c = dec2(c);
               continue;
            case 6:  // MULTIBYTE_3
               c = dec3(c);
               continue;
            case 7:  // MULTIBYTE_4
               c = dec4(c);
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
                  byte b;
                  if((b = Code(false)) != (byte)'>')
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
      final byte[] TYPES = chrT.OTH, buf = inBuf;
      char[] outputBuffer = reset();
      int c = 0, outPtr = 0;
      while(true){
         int ptr = inPtr;
adv:     while(true){
            if(ptr >= end){
               assertMore();
               ptr = 0;
            }
            if(outPtr >= outputBuffer.length){
               outputBuffer = endSeg();
               outPtr = 0;
            }
            int max, max2;
            if((max2 = ptr + outputBuffer.length - outPtr) < (max = end))
               max = max2;
            while(ptr < max){
               if(TYPES[c = buf[ptr++] & 0xFF] != 0)
                  break adv;
               outputBuffer[outPtr++] = (char)c;
            }
         }
         inPtr = ptr;
         switch(TYPES[c]){
            case 2:  // WS_CR
               if(ptr >= end)
                  assertMore();
               if(buf[inPtr] == (byte)'\n')
                  ++inPtr;
               c = '\n';
            case 3:  // WS_LF
               ln();
               break;
            case 5:  // MULTIBYTE_2
               c = dec2(c);
               break;
            case 6:  // MULTIBYTE_3
               c = dec3(c);
               break;
            case 7:  // MULTIBYTE_4
               outputBuffer[outPtr++] = (char)(0xD800 | (c = dec4(c)) >> 10);
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
                  currSz = outPtr;
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
      int tmp, outPtr;
      char[] outputBuffer;
      if((tmp = cTmp) == '\r' || tmp == '\n'){
         if((outPtr = prolog(tmp)) < 0)
            return;
         outputBuffer = currSeg;
      }else{
         (outputBuffer = reset())[0] = (char)tmp;
         outPtr = 1;
      }
      int ptr = inPtr;
      while(true){
         if(ptr >= end){
            if(!more())
               break;
            ptr = 0;
         }
         int c;
         if((c = inBuf[ptr] & 0xFF) > 0x20)
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
      currSz = outPtr;
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
                  thErr("Expected '[CDATA['");
            }
            endClsCData();
         }else{
            endClsC();
            if(pend)
               return;
         }
      }
   }

   private final void endClsC() throws XMLStreamException{
      final byte[] TYPES = chrT.TXT, buf = inBuf;
      char[] outputBuffer = currSeg;
      int c = 0, outPtr = currSz;
      while(true){
         int ptr = inPtr;
adv:     while(true){
            if(ptr >= end){
               assertMore();
               ptr = 0;
            }
            if(outPtr >= outputBuffer.length){
               outputBuffer = endSeg();
               outPtr = 0;
            }
            int max, max2;
            if((max2 = ptr + outputBuffer.length - outPtr) < (max = end))
               max = max2;
            while(ptr < max){
               if(TYPES[c = buf[ptr++] & 0xFF] != 0)
                  break adv;
               outputBuffer[outPtr++] = (char)c;
            }
         }
         inPtr = ptr;
         switch(TYPES[c]){
            case 2:  // WS_CR
               if(ptr >= end)
                  assertMore();
               if(buf[inPtr] == (byte)'\n')
                  ++inPtr;
               c = '\n';
            case 3:  // WS_LF
               ln();
               break;
            case 5:  // MULTIBYTE_2
               c = dec2(c);
               break;
            case 6:  // MULTIBYTE_3
               c = end - ptr >= 2 ? dec3f(c) : dec3(c);
               break;
            case 7:  // MULTIBYTE_4
               outputBuffer[outPtr++] = (char)(0xD800 | (c = dec4(c)) >> 10);
               if(outPtr >= outputBuffer.length){
                  outputBuffer = endSeg();
                  outPtr = 0;
               }
               c = 0xDC00 | c & 0x3FF;
               break;
            case 9:  // LT
               --inPtr;
               currSz = outPtr;
               return;
            case 10: // AMP
               if((c = entInTxt()) == 0){
                  pend = true;
                  currSz = outPtr;
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
               int count = 0;
               byte b;
               while(true){
                  if(inPtr >= end)
                     assertMore();
                  if((b = buf[inPtr]) != (byte)']')
                     break;
                  ++inPtr;
                  ++count;
               }
               if(b == (byte)'>' && count > 0)
                  thUnxp();
               while(count > 0){
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
      final byte[] TYPES = chrT.OTH, buf = inBuf;
      char[] outputBuffer = currSeg;
      int c = 0, outPtr = currSz;
      while(true){
         int ptr = inPtr;
adv:     while(true){
            if(ptr >= end){
               assertMore();
               ptr = 0;
            }
            if(outPtr >= outputBuffer.length){
               outputBuffer = endSeg();
               outPtr = 0;
            }
            int max, max2;
            if((max2 = ptr + outputBuffer.length - outPtr) < (max = end))
               max = max2;
            while(ptr < max){
               if(TYPES[c = buf[ptr++] & 0xFF] != 0)
                  break adv;
               outputBuffer[outPtr++] = (char)c;
            }
         }
         inPtr = ptr;
         switch(TYPES[c]){
            case 2:  // WS_CR
               if(ptr >= end)
                  assertMore();
               if(buf[inPtr] == (byte)'\n')
                  ++inPtr;
               c = '\n';
            case 3:  // WS_LF
               ln();
               break;
            case 5:  // MULTIBYTE_2
               c = dec2(c);
               break;
            case 6:  // MULTIBYTE_3
               c = dec3(c);
               break;
            case 7:  // MULTIBYTE_4
               outputBuffer[outPtr++] = (char)(0xD800 | (c = dec4(c)) >> 10);
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
               boolean ok;
               if(ok = b == (byte)'>' && count >= 1)
                  --count;
               while(count-- > 0){
                  outputBuffer[outPtr++] = ']';
                  if(outPtr >= outputBuffer.length){
                     outputBuffer = endSeg();
                     outPtr = 0;
                  }
               }
               if(ok){
                  ++inPtr;
                  currSz = outPtr;
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
                  thErr("Expected '[CDATA['");
            }
            skipCData();
         }else if(skipChars())
            return true;
      }
   }

   private final int dec(int c, int ptr) throws XMLStreamException{
      int needed = 0;
      if((c & 0xE0) == 0xC0)
         c &= 0x1F;
      else if((c & 0xF0) == 0xE0){
         c &= 0xF;
         needed = 1;
      }else if((c & 0xF8) == 0xF0){
         c &= 7;
         needed = 2;
      }else
         badUTF(c);
      if(ptr >= end){
         assertMore();
         ptr = 0;
      }
      int d;
      if(((d = inBuf[ptr++]) & 0xC0) != 0x80){
         inPtr = ptr;
         badUTF(d);
      }
      c = c << 6 | d & 0x3F;
      if(needed > 0){
         if(ptr >= end){
            assertMore();
            ptr = 0;
         }
         if(((d = inBuf[ptr++]) & 0xC0) != 0x80){
            inPtr = ptr;
            badUTF(d);
         }
         c = c << 6 | d & 0x3F;
         if(needed > 1){
            if(ptr >= end){
               assertMore();
               ptr = 0;
            }
            if(((d = inBuf[ptr++]) & 0xC0) != 0x80){
               inPtr = ptr;
               badUTF(d);
            }
            c = -(c << 6 | d & 0x3F);
         }
      }
      inPtr = ptr;
      return c;
   }

   private final int dec2(int c) throws XMLStreamException{
      if(inPtr >= end)
         assertMore();
      int d;
      if(((d = inBuf[inPtr++]) & 0xC0) != 0x80)
         badUTF(d);
      return (c & 0x1F) << 6 | d & 0x3F;
   }

   private final int dec3(int c1) throws XMLStreamException{
      if(inPtr >= end)
         assertMore();
      int d;
      if(((d = inBuf[inPtr++]) & 0xC0) != 0x80)
         badUTF(d);
      int c = (c1 &= 0xF) << 6 | d & 0x3F;
      if(inPtr >= end)
         assertMore();
      if(((d = inBuf[inPtr++]) & 0xC0) != 0x80)
         badUTF(d);
      c = c << 6 | d & 0x3F;
      if(c1 >= 0xD && ((c >= 0xD800 && c < 0xE000) || c == 0xFFFE || c == 0xFFFF))
         thC(c);
      return c;
   }

   private final int dec3f(int c1) throws XMLStreamException{
      int d = inBuf[inPtr++], c = (c1 &= 0xF) << 6 | d & 0x3F;
      if((d & 0xC0) != 0x80 || ((d = inBuf[inPtr++]) & 0xC0) != 0x80)
         badUTF(d);
      c = c << 6 | d & 0x3F;
      if(c1 >= 0xD && ((c >= 0xD800 && c < 0xE000) || c == 0xFFFE || c == 0xFFFF))
         thC(c);
      return c;
   }

   private final int dec4(int c) throws XMLStreamException{
      if(inPtr >= end)
         assertMore();
      int d;
      if(((d = inBuf[inPtr++]) & 0xC0) != 0x80)
         badUTF(d);
      c = (c & 7) << 6 | d & 0x3F;
      if(inPtr >= end)
         assertMore();
      if(((d = inBuf[inPtr++]) & 0xC0) != 0x80)
         badUTF(d);
      c = c << 6 | d & 0x3F;
      if(inPtr >= end)
         assertMore();
      if(((d = inBuf[inPtr++]) & 0xC0) != 0x80)
         badUTF(d);
      return (c << 6 | d & 0x3F) - 0x10000;
   }

   private final int decChr(byte b) throws XMLStreamException{
      int c, needed = 0;
      if((c = b) >= 0)
         return c;
      if((c & 0xE0) == 0xC0)        // 2 bytes
         c &= 0x1F;
      else if((c & 0xF0) == 0xE0){  // 3 bytes
         c &= 0xF;
         needed = 1;
      }else if((c & 0xF8) == 0xF0){ // 4 bytes
         c &= 7;
         needed = 2;
      }else
         badUTF(c);
      c = c << 6 | nxtB();
      if(needed > 0){
         c = c << 6 | nxtB();
         if(needed > 1)
            c = c << 6 | nxtB();
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

   private final void badUTF(int mask) throws XMLStreamException{ thErr(new StrB(12).a("Bad UTF8 ").apos(mask & 0xFF).toString()); }
}
