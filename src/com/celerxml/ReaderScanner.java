// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025 - 2026
package com.celerxml;

import java.io.Reader;
import java.io.IOException;
import javax.xml.stream.XMLStreamException;

final class ReaderScanner extends XmlScanner{

   private final static Chr Code = Chr.getLat1();
   private Reader in;
   private char[] buf;
   private int inPtr, end, cTmp;
   private final cPN syms;
   private Node curr;

   ReaderScanner(InputFactoryImpl impl, Reader in, char[] buf, int inPtr, int end){
      super(impl);
      this.in = in;
      if(impl.genTab == null)
         impl.genTab = new cPN();
      syms = new cPN(impl.genTab);
      this.buf = buf;
      this.inPtr = inPtr;
      this.end = end;
   }

   @Override
   final boolean more() throws XMLStreamException{
      inPtr = 0;
      if(in == null){
         end = 0;
         return false;
      }
      bOrC += end;
      rowOff -= end;
      try{
         int count = in.read(buf, 0, 4096);
         if(count < 1){
            end = 0;
            if(count == 0)
               thInErr("Reader returned 0");
            return false;
         }
         end = count;
         return true;
      }catch(IOException ioe){
         throw new XMLStreamException(ioe);
      }
   }

   private final char chkSurrgCh(char chr) throws XMLStreamException{
      if(inPtr >= end)
         assertMore();
      int val = ((chr - 0xD800) << 10) + 0x10000;
      if(chr >= 0xDC00 || (chr = buf[inPtr++]) < 0xDC00 || chr >= 0xE000)
         thSurr(chr);
      if(val > 0x10FFFF)
         thInvC(val);
      return chr;
   }

   @Override
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

   @Override
   final int nxtFromProlog(boolean isProlog) throws XMLStreamException{
      if(incompl)
         skipTok();
      iniRawOff = bOrC + inPtr;
      startRow = currRow;
      startCol = inPtr - rowOff;
      char c;
      while(true){
         if(inPtr >= end && !more()){
            iniRawOff = bOrC;
            startCol = -rowOff;
            return -1;
         }
         switch(c = buf[inPtr++]){
            case '<':
               if(inPtr >= end)
                  assertMore();
               if((c = buf[inPtr++]) == '!')
                  return doPrologDecl(isProlog);
               if(c == '?')
                  return doPIStart();
               if(c == '/' || !isProlog)
                  thRoot(isProlog, c);
               return startElem(c);
            case ' ':
            case '\t':
               continue;
            case '\r':
               if(inPtr >= end && !more()){
                  rowOff = startCol = 0;
                  iniRawOff = bOrC;
                  startRow = ++currRow;
                  return -1;
               }
               if(buf[inPtr] == '\n')
                  ++inPtr;
            case '\n':
               rowOff = inPtr;
               ++currRow;
               break;
            default:
               thPlogUnxpCh(isProlog, c);
         }
      }
   }

   @Override
   final int nxtFromTree() throws XMLStreamException{
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
            lastNs = lastNs.Code();
      }else if(pending){
         pending = false;
         reset();
         return currToken = 9; // ENTITY_REFERENCE
      }
      iniRawOff = bOrC + inPtr;
      startRow = currRow;
      startCol = inPtr - rowOff;
      if(inPtr >= end && !more()){
         iniRawOff = bOrC;
         startCol = -rowOff;
         return -1;
      }
      int c = buf[inPtr];
      if(c == '<'){
         if(++inPtr >= end && !more())
            thInErr(EOI);
         if((c = buf[inPtr++]) == '!')
            return commOrCdataStart();
         if(c == '?')
            return doPIStart();
         if(c == '/')
            return doEndE();
         return startElem((char)c);
      }
      if(c == '&'){
         ++inPtr;
         if((c = -entInTxt()) == 0)
            return currToken = 9; // ENTITY_REFERENCE
      }
      cTmp = c;
      if(lazy)
         incompl = true;
      else
         endC();
      return currToken = 4; // CHARACTERS
   }

   private final int doPrologDecl(boolean isProlog) throws XMLStreamException{
      if(inPtr >= end)
         assertMore();
      char c = buf[inPtr++];
      if(c == '-'){
         if(inPtr >= end)
            assertMore();
         if((c = buf[inPtr++]) == '-'){
            if(lazy)
               incompl = true;
            else
               endComm();
            return currToken = 5; // COMMENT
         }
      }else if(isProlog && c == 'D'){
         doDtdStart();
         if(!lazy && incompl){
            endDTD();
            incompl = false;
         }
         return 11; // DTD
      }
      incompl = true;
      currToken = 4; // CHARACTERS
      thPlogUnxpCh(isProlog, c);
      return 0;
   }

   private final int doDtdStart() throws XMLStreamException{
      Code("DOCTYPE");
      char q, c;
      tokName = parsePN(Code(true));
      if((c = Code(false)) == 'P'){
         Code("PUBLIC");
         q = Code(true);
         char[] outputBuffer = nameBuf;
         int outPtr = 0;
         final byte[] TYPES = Chr.Code;
         boolean addSpace = false;
         while(true){
            if(inPtr >= end)
               assertMore();
            if((c = buf[inPtr++]) == q)
               break;
            if(c > 0xFF || TYPES[c] != 1) // PUBID_OK
               thUnxp(c, " in public ID");
            if(c <= 0x20){
               addSpace = true;
               continue;
            }
            if(addSpace){
               if(outPtr >= outputBuffer.length){
                  outputBuffer = endSeg();
                  outPtr = 0;
               }
               outputBuffer[outPtr++] = ' ';
               addSpace = false;
            }
            if(outPtr >= outputBuffer.length)
                nameBuf = outputBuffer = xpand(outputBuffer);
            outputBuffer[outPtr++] = c;
         }
         dtdPubId = new String(outputBuffer, 0, outPtr);
         dtdSysId = Code(Code(true));
         c = Code(false);
      }else if(c == 'S'){
         Code("SYSTEM");
         dtdPubId = null;
         dtdSysId = Code(Code(true));
         c = Code(false);
      }else
         dtdPubId = dtdSysId = null;
      if((incompl = c == '[') || c == '>')
         return currToken = 11; // DTD
      thUnxp(c, ", expected '[' or '>'");
      return 0;
   }

   private final int commOrCdataStart() throws XMLStreamException{
      if(inPtr >= end)
         assertMore();
      char c = buf[inPtr++];
      if(c == '-'){
         if(inPtr >= end)
            assertMore();
         if(buf[inPtr++] != '-')
            thInErr("Expected '-'");
         if(lazy)
            incompl = true;
         else
            endComm();
         return currToken = 5; // COMMENT
      }
      if(c == '['){
         currToken = 12; // CDATA
         for(int i = 0; i < 6; ++i){
            if(inPtr >= end)
               assertMore();
            if(buf[inPtr++] != CDATA.charAt(i))
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
      tokName = parsePN(buf[inPtr++]);
      String ln = tokName.ln;
      if(ln.length() == 3 && ln.equalsIgnoreCase("xml") && tokName.pfx == null)
         thInErr("Target 'xml' reserved");
      if(inPtr >= end)
         assertMore();
      char c = buf[inPtr++];
      if(c <= 0x20){
         while(true){
            if(c == '\n'){
               rowOff = inPtr;
               ++currRow;
            }else if(c == '\r'){
               if(inPtr >= end)
                  assertMore();
               if(buf[inPtr] == '\n')
                  ++inPtr;
               rowOff = inPtr;
               ++currRow;
            }else if(c != ' ' && c != '\t')
               thC(c);
            if(inPtr >= end)
               assertMore();
            if((c = buf[inPtr]) > 0x20)
               break;
            ++inPtr;
         }
         if(lazy)
            incompl = true;
         else
            endPI();
      }else{
         if(c != (int)'?')
            thNoPISp(c);
         if(inPtr >= end)
            assertMore();
         if((c = buf[inPtr++]) != '>')
            thNoPISp(c);
         reset();
         incompl = false;
      }
      return 3; // PROCESSING_INSTRUCTION
   }

   private final int chrEnt() throws XMLStreamException{
      if(inPtr >= end)
         assertMore();
      char c = buf[inPtr++];
      int value = 0;
      if(c == 'x')
         while(true){
            if(inPtr >= end)
               assertMore();
            if((c = buf[inPtr++]) == ';')
               break;
            if((c = (char)((c | 0x20) - 0x30)) > 9) // to lowercase
               c -= 0x27;
            if(c < 0 || c > 0xF)
               thUnxp(c, ", not a hex digit [0-9a-fA-F]");
            value = value << 4 | c;
         }
      else
         while(c != ';'){
            if(c < '0' || c > '9')
               thUnxp(c, ", not a decimal number");
            value = value * 10 + c - '0';
            if(inPtr >= end)
               assertMore();
            c = buf[inPtr++];
         }
      if((value >= 0xD800 && value < 0xE000) || value == 0 || value == 0xFFFE || value == 0xFFFF)
         thInvC(value);
      return value;
   }

   private final int startElem(char c) throws XMLStreamException{
      currToken = 1; // START_ELEMENT
      nsCnt = 0;
      PN elemName = parsePN(c);
      String prefix = elemName.pfx;
      boolean allBound = true;
      if(prefix != null)
         allBound = (elemName = bindName(elemName, prefix)).isBound();
      tokName = elemName;
      curr = new Node(elemName, curr);
      int xx = 0;
      while(true){
         if(inPtr >= end)
            assertMore();
         if((c = buf[inPtr++]) <= 0x20)
            do{
               if(c == '\n'){
                  rowOff = inPtr;
                  ++currRow;
               }else if(c == '\r'){
                  if(inPtr >= end)
                     assertMore();
                  if(buf[inPtr] == '\n')
                     ++inPtr;
                  rowOff = inPtr;
                  ++currRow;
               }else if(c != ' ' && c != '\t')
                  thC(c);
               if(inPtr >= end)
                  assertMore();
            }while((c = buf[inPtr++]) <= 0x20);
         else if(c != '/' && c != '>')
            thUnxp(c, ", not space or '>' or '/>'");
         if(c == '/'){
            if(inPtr >= end)
               assertMore();
            if((c = buf[inPtr++]) != '>')
               thUnxp(c, ", not '>'");
            empty = true;
            break;
         }else if(c == '>'){
            empty = false;
            break;
         }else if(c == '<')
            thInErr("Unexpected '<'");
         PN attrName = parsePN(c);
         prefix = attrName.pfx;
         boolean isNsDecl = true;
         if(prefix == null)
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
            if((c = buf[inPtr++]) > 0x20)
               break;
            if(c == '\n'){
               rowOff = inPtr;
               ++currRow;
            }else if(c == '\r'){
               if(inPtr >= end)
                  assertMore();
               if(buf[inPtr] == '\n')
                  ++inPtr;
               rowOff = inPtr;
               ++currRow;
            }else if(c != ' ' && c != '\t')
               thC(c);
         }
         if(c != '=')
            thUnxp(c, ", not '='");
         while(true){
            if(inPtr >= end)
               assertMore();
            if((c = buf[inPtr++]) > 0x20)
               break;
            if(c == '\n'){
               rowOff = inPtr;
               ++currRow;
            }else if(c == '\r'){
               if(inPtr >= end)
                  assertMore();
               if(buf[inPtr] == '\n')
                  ++inPtr;
               rowOff = inPtr;
               ++currRow;
            }else if(c != ' ' && c != '\t')
               thC(c);
         }
         if(c != '"' && c != '\'')
            thUnxp(c, ", not a quote");
         if(isNsDecl){
            Code(attrName, c);
            ++nsCnt;
         }else
            xx = Code(xx, c, attrName);
      }
      if((xx = endLastV(xx)) < 0)
         thInErr(errMsg);
      attrCount = xx;
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

   private final int Code(int attrPtr, char quote, PN attrName) throws XMLStreamException{
      final byte[] TYPES = Code.ATT;
      char[] attrBuffer = startNewV(attrName, attrPtr);
      char c = 0;
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
               if((c = buf[ptr++]) >= 0xD800 || (c <= 0xFF && TYPES[c] != 0)){
                  adv = false;
                  break;
               }
               attrBuffer[attrPtr++] = c;
            }
         }while(adv);
         inPtr = ptr;
         if(c <= 0xFF)
            switch(TYPES[c]){
               case 2:  // WS_CR
                  if(ptr >= end)
                     assertMore();
                  if(buf[inPtr] == '\n')
                     ++inPtr;
               case 3:  // WS_LF
                  rowOff = inPtr;
                  ++currRow;
               case 8:  // WS_TAB
                  c = ' ';
                  break;
               case 10: // AMP
                  if((ptr = entInTxt()) == 0)
                     thEnt();
                  if((ptr >> 16) != 0){
                     attrBuffer[attrPtr++] = (char)(0xD800 | (ptr -= 0x10000) >> 10);
                     ptr = 0xDC00 | ptr & 0x3FF;
                     if(attrPtr >= attrBuffer.length)
                        attrBuffer = xpand();
                  }
                  c = (char)ptr;
                  break;
               case 14: // ATTR_QUOTE
                  if(c == quote)
                     return attrPtr;
                  break;
               case 1:  // INVALID
                  thC(c);
               case 9:  // LT
                  thUnxp(c, " in attribute value");
            }
         else if(c >= 0xD800 && c < 0xE000){
            char d = chkSurrgCh(c);
            attrBuffer[attrPtr++] = c;
            if(attrPtr >= attrBuffer.length)
               attrBuffer = xpand();
            c = d;
         }else if(c >= 0xFFFE)
            thC(c);
         attrBuffer[attrPtr++] = c;
      }
   }

   private final void Code(PN name, char quote) throws XMLStreamException{
      int attrPtr = 0;
      char[] attrBuffer = nameBuf;
      while(true){
         if(inPtr >= end)
            assertMore();
         char c = buf[inPtr++];
         if(c == quote){
            bindNs(name, attrPtr == 0 ? "" : impl.Code(attrBuffer, attrPtr));
            return;
         }
         if(c == '&'){
            int d = entInTxt();
            if(d == 0)
               thEnt();
            if((d >> 16) != 0){
               if(attrPtr >= attrBuffer.length)
                  nameBuf = attrBuffer = xpand(attrBuffer);
               attrBuffer[attrPtr++] = (char)(0xD800 | (d -= 0x10000) >> 10);
               d = 0xDC00 | d & 0x3FF;
            }
            c = (char)d;
         }else if(c == '<')
            thUnxp(c, " in attribute value");
         else if(c < 0x20){
            if(c == '\n'){
               rowOff = inPtr;
               ++currRow;
            }else if(c == '\r'){
               if(inPtr >= end)
                  assertMore();
               if(buf[inPtr] == '\n')
                  ++inPtr;
               rowOff = inPtr;
               ++currRow;
               c = '\n';
            }else if(c != '\t')
               thC(c);
         }
         if(attrPtr >= attrBuffer.length)
            nameBuf = attrBuffer = xpand(attrBuffer);
         attrBuffer[attrPtr++] = c;
      }
   }

   private final int doEndE() throws XMLStreamException{
      --depth;
      currToken = 2; // END_ELEMENT
      tokName = curr.Code;
      String pname = tokName.Code;
      int i = 0, len = pname.length();
      do{
         if(inPtr >= end)
            assertMore();
         if(buf[inPtr++] != pname.charAt(i))
            thUnexpEnd(pname);
      }while(++i < len);
      if(inPtr >= end)
         assertMore();
      char c = buf[inPtr++];
      if(c <= ' ')
         c = Code(false);
      else if(c == ':' || Chr.is10N(c))
         thUnexpEnd(pname);
      if(c != '>')
         thUnxp(c, ", not space or closing '>'");
      return 2; // END_ELEMENT
   }

   private final int entInTxt() throws XMLStreamException{
      if(inPtr >= end)
         assertMore();
      char c = buf[inPtr++];
      if(c == '#')
         return chrEnt();
      char[] cbuf = nameBuf;
      int cix = 0;
      if(c == 'a'){
         if(inPtr >= end)
            assertMore();
         cbuf[cix++] = c;
         if((c = buf[inPtr++]) == 'm'){
            if(inPtr >= end)
               assertMore();
            cbuf[cix++] = c;
            if((c = buf[inPtr++]) == 'p'){
               if(inPtr >= end)
                  assertMore();
               cbuf[cix++] = c;
               if((c = buf[inPtr++]) == ';')
                  return '&';
            }
         }else if(c == 'p'){
            if(inPtr >= end)
               assertMore();
            cbuf[cix++] = c;
            if((c = buf[inPtr++]) == 'o'){
               if(inPtr >= end)
                  assertMore();
               cbuf[cix++] = c;
               if((c = buf[inPtr++]) == 's'){
                  if(inPtr >= end)
                     assertMore();
                  cbuf[cix++] = c;
                  if((c = buf[inPtr++]) == ';')
                     return '\'';
               }
            }
         }
      }else if(c == 'l'){
         if(inPtr >= end)
            assertMore();
         cbuf[cix++] = c;
         if((c = buf[inPtr++]) == 't'){
            if(inPtr >= end)
               assertMore();
            cbuf[cix++] = c;
            if((c = buf[inPtr++]) == ';')
               return '<';
         }
      }else if(c == 'g'){
         if(inPtr >= end)
            assertMore();
         cbuf[cix++] = c;
         if((c = buf[inPtr++]) == 't'){
            if(inPtr >= end)
               assertMore();
            cbuf[cix++] = c;
            if((c = buf[inPtr++]) == ';')
               return '>';
         }
      }else if(c == 'q'){
         if(inPtr >= end)
            assertMore();
         cbuf[cix++] = c;
         if((c = buf[inPtr++]) == 'u'){
            if(inPtr >= end)
               assertMore();
            cbuf[cix++] = c;
            if((c = buf[inPtr++]) == 'o'){
               if(inPtr >= end)
                  assertMore();
               cbuf[cix++] = c;
               if((c = buf[inPtr++]) == 't'){
                  if(inPtr >= end)
                     assertMore();
                  cbuf[cix++] = c;
                  if((c = buf[inPtr++]) == ';')
                     return '"';
               }
            }
         }
      }
      final byte[] TYPES = Code.NAM;
      while(c != ';'){
         boolean ok = false;
         if(c <= 0xFF)
            switch(TYPES[c]){
               case 0:  // NAME_NONE
               case 1:  // NAME_COLON
               case 2:  // NAME_NONFIRST
                  ok = cix > 0;
                  break;
               case 3:  // NAME_ANY
                  ok = true;
            }
         else if(c < 0xE000){
            if(c >= 0xDC00)
               thSurr(c);
            if(inPtr >= end)
               assertMore();
            char sec = buf[inPtr++];
            if(sec < 0xDC00 || sec >= 0xE000)
               thSurr(sec);
            int value = ((c - 0xD800) << 10) + 0x10000;
            if(value > 0x10FFFF)
               thInvC(value);
            if(cix >= cbuf.length)
               nameBuf = cbuf = xpand(cbuf);
            cbuf[cix++] = c;
            c = buf[inPtr - 1];
            ok = Chr.is10N(value);
         }else if(c >= 0xFFFE)
            thC(c);
         else
            ok = true;
         if(!ok)
            thInvNCh(c);
         if(cix >= cbuf.length)
            nameBuf = cbuf = xpand(cbuf);
         cbuf[cix++] = c;
         if(inPtr >= end)
            assertMore();
         c = buf[inPtr++];
      }
      if(impl.Code(16))
         thInErr("Entity ref. in entity expanding mode");
      String pname = new String(cbuf, 0, cix);
      tokName = new PN(pname, null, pname, 0);
      return 0;
   }

   private final void endComm() throws XMLStreamException{
      final byte[] TYPES = Code.OTH;
      final char[] inputBuffer = buf;
      char[] outputBuffer = reset();
      int outPtr = 0;
      char c = 0;
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
               if((c = inputBuffer[ptr++]) >= 0xD800 || (c <= 0xFF && TYPES[c] != 0)){
                  adv = false;
                  break;
               }
               outputBuffer[outPtr++] = c;
            }
         }while(adv);
         inPtr = ptr;
         if(c <= 0xFF)
            switch(TYPES[c]){
               case 2:  // WS_CR
                  if(ptr >= end)
                     assertMore();
                  if(inputBuffer[inPtr] == '\n')
                     ++inPtr;
                  c = '\n';
               case 3:  // WS_LF
                  rowOff = inPtr;
                  ++currRow;
                  break;
               case 13: // HYPHEN
                  if(ptr >= end)
                     assertMore();
                  if(buf[inPtr] == '-'){
                     ++inPtr;
                     if(inPtr >= end)
                        assertMore();
                     if(buf[inPtr++] != '>')
                        thHyph();
                     currSize = outPtr;
                     return;
                  }
                  break;
               case 1:  // INVALID
                  thC(c);
            }
         else if(c >= 0xD800 && c < 0xE000){
            char d = chkSurrgCh(c);
            outputBuffer[outPtr++] = c;
            if(outPtr >= outputBuffer.length) {
               outputBuffer = endSeg();
               outPtr = 0;
            }
            c = d;
         }else if(c >= 0xFFFE)
            thC(c);
         outputBuffer[outPtr++] = c;
      }
   }

   private final void endPI() throws XMLStreamException{
      final byte[] TYPES = Code.OTH;
      final char[] inputBuffer = buf;
      char[] outputBuffer = reset();
      int outPtr = 0;
      char c = 0;
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
               if((c = inputBuffer[ptr++]) >= 0xD800 || (c <= 0xFF && TYPES[c] != 0)){
                  adv = false;
                  break;
               }
               outputBuffer[outPtr++] = c;
            }
         }while(adv);
         inPtr = ptr;
         if(c <= 0xFF)
            switch(TYPES[c]){
               case 2:  // WS_CR
                  if(ptr >= end)
                     assertMore();
                  if(inputBuffer[inPtr] == '\n')
                     ++inPtr;
                  c = '\n';
               case 3:  // WS_LF
                  rowOff = inPtr;
                  ++currRow;
                  break;
               case 12: // QMARK
                  if(ptr >= end)
                     assertMore();
                  if(buf[inPtr] == '>'){
                     ++inPtr;
                     currSize = outPtr;
                     return;
                  }
            }
         else if(c >= 0xD800 && c < 0xE000){
            char d = chkSurrgCh(c);
            outputBuffer[outPtr++] = c;
            if(outPtr >= outputBuffer.length){
               outputBuffer = endSeg();
               outPtr = 0;
            }
            c = d;
         }else if(c >= 0xFFFE)
            thC(c);
         outputBuffer[outPtr++] = c;
      }
   }

   private final void endDTD() throws XMLStreamException{
      char[] outBuf = reset();
      int outPtr = 0, quoteChar = 0;
      final byte[] TYPES = Code.DTD;
      char c = 0;
      while(true){
         int ptr = inPtr;
         boolean adv = true;
         do{
            if(ptr >= end){
               assertMore();
               ptr = 0;
            }
            if(outPtr >= outBuf.length){
               outBuf = endSeg();
               outPtr = 0;
            }
            int max = end, max2 = ptr + outBuf.length - outPtr;
            if(max2 < max)
               max = max2;
            while(ptr < max){
               if((c = buf[ptr++]) >= 0xD800 || (c <= 0xFF && TYPES[c] != 0)){
                  adv = false;
                  break;
               }
               outBuf[outPtr++] = c;
            }
         }while(adv);
         inPtr = ptr;
         if(c <= 0xFF)
            switch(TYPES[c]){
               case 2:  // WS_CR
                  if(ptr >= end)
                     assertMore();
                  if(buf[inPtr] == '\n')
                     ++inPtr;
                  c = '\n';
               case 3:  // WS_LF
                  rowOff = inPtr;
                  ++currRow;
                  break;
               case 8:  // DTD_QUOTE
                  if(quoteChar == 0)
                     quoteChar = c;
                  else if(quoteChar == c)
                     quoteChar = 0;
                  break;
               case 9:  // LT
                  adv = true;
                  break;
               case 10: // DTD_GT
                  if(quoteChar == 0)
                     adv = false;
                  break;
               case 11: // RBRACKET
                  if(!adv && quoteChar == 0){
                     currSize = outPtr;
                     if((c = Code(false)) != '>')
                        thUnxp(c, ", not '>' after internal subset");
                     return;
                  }
                  break;
               case 1:  // INVALID
                  thC(c);
            }
         else if(c >= 0xD800 && c < 0xE000){
            outBuf[outPtr++] = c;
            c = chkSurrgCh(c);
            if(outPtr >= outBuf.length){
               outBuf = endSeg();
               outPtr = 0;
            }
         }else if(c >= 0xFFFE)
            thC(c);
         outBuf[outPtr++] = c;
      }
   }

   @Override
   final void skipDTD() throws XMLStreamException{
      int quoteChar = 0;
      final byte[] TYPES = Code.DTD;
      char c = 0;
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
               if((c = buf[ptr++]) >= 0xD800 || (c <= 0xFF && TYPES[c] != 0)){
                  adv = false;
                  break;
               }
         }while(adv);
         inPtr = ptr;
         if(c <= 0xFF)
            switch(TYPES[c]){
               case 2:  // WS_CR
                  if(ptr >= end)
                     assertMore();
                  if(buf[inPtr] == '\n')
                     ++inPtr;
               case 3:  // WS_LF
                  rowOff = inPtr;
                  ++currRow;
                  continue;
               case 8:  // DTD_QUOTE
                  if(quoteChar == 0)
                     quoteChar = c;
                  else if(quoteChar == c)
                     quoteChar = 0;
                  continue;
               case 9:  // LT
                  if(!adv)
                     adv = true;
                  continue;
               case 10: // DTD_GT
                  if(quoteChar == 0)
                     adv = false;
                  continue;
               case 11: // RBRACKET
                  if(!adv && quoteChar == 0){
                     if((c = Code(false)) != '>')
                        thUnxp(c, ", not '>' after internal subset");
                     return;
                  }
                  continue;
               case 1:  // INVALID
                  thC(c);
            }
         else if(c >= 0xD800 && c < 0xE000)
            chkSurrgCh(c);
         else if(c >= 0xFFFE)
            thC(c);
      }
   }

   private final void endCData() throws XMLStreamException{
      final byte[] TYPES = Code.OTH;
      final char[] inputBuffer = buf;
      char[] outputBuffer = reset();
      int outPtr = 0;
      char c = 0;
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
               if((c = inputBuffer[ptr++]) >= 0xD800 || (c <= 0xFF && TYPES[c] != 0)){
                  adv = false;
                  break;
               }
               outputBuffer[outPtr++] = c;
            }
         }while(adv);
         inPtr = ptr;
         if(c <= 0xFF)
            switch(TYPES[c]){
               case 2:  // WS_CR
                  if(ptr >= end)
                     assertMore();
                  if(inputBuffer[inPtr] == '\n')
                     ++inPtr;
                  c = '\n';
               case 3:  // WS_LF
                  rowOff = inPtr;
                  ++currRow;
                  break;
               case 11: // RBRACKET
                  int count = 0;
                  char d;
                  do{
                     if(inPtr >= end)
                        assertMore();
                     if((d = buf[inPtr]) != ']')
                        break;
                     ++inPtr;
                     ++count;
                  }while(true);
                  boolean ok = d == '>' && count >= 1;
                  if(ok)
                     --count;
                  for(; count > 0; --count){
                     outputBuffer[outPtr++] = ']';
                     if(outPtr >= outputBuffer.length){
                        outputBuffer = endSeg();
                        outPtr = 0;
                     }
                  }
                  if(ok){
                     ++inPtr;
                     currSize = outPtr;
                     if(coalescing && !pending)
                        endClsTxt();
                     return;
                  }
                  break;
               case 1:  // INVALID
                  thC(c);
            }
         else if(c >= 0xD800 && c < 0xE000){
            char d = chkSurrgCh(c);
            outputBuffer[outPtr++] = c;
            if(outPtr >= outputBuffer.length){
               outputBuffer = endSeg();
               outPtr = 0;
            }
            c = d;
         }else if(c >= 0xFFFE)
            thC(c);
         outputBuffer[outPtr++] = c;
      }
   }

   private final void endC() throws XMLStreamException{
      char[] outputBuffer;
      int outPtr = 0, t = cTmp;
      if(t < 0){
         outputBuffer = reset();
         if(((t = -t) >> 16) != 0){
            outputBuffer[outPtr++] = (char)(0xD800 | (t -= 0x10000) >> 10);
            t = 0xDC00 | t & 0x3FF;
         }
         outputBuffer[outPtr++] = (char)t;
      }else if(t == '\r' || t == '\n'){
         ++inPtr;
         if((outPtr = inTree((char)t)) < 0)
            return;
         outputBuffer = currSeg;
      }else
         outputBuffer = reset();
      final byte[] TYPES = Code.TXT;
      final char[] inputBuffer = buf;
      char c = 0;
outl: while(true){
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
               if((c = inputBuffer[ptr++]) >= 0xD800 || (c <= 0xFF && TYPES[c] != 0)){
                  adv = false;
                  break;
               }
               outputBuffer[outPtr++] = c;
            }
         }while(adv);
         inPtr = ptr;
         if(c <= 0xFF)
            switch(TYPES[c]){
               case 2:  // WS_CR
                  if(ptr >= end)
                     assertMore();
                  if(inputBuffer[inPtr] == '\n')
                     ++inPtr;
                  c = '\n';
               case 3:  // WS_LF
                  rowOff = inPtr;
                  ++currRow;
                  break;
               case 9:  // LT
                  --inPtr;
                  break outl;
               case 10: // AMP
                  int d = entInTxt();
                  if(d == 0){
                     pending = true;
                     break outl;
                  }
                  if((d >> 16) != 0){
                     outputBuffer[outPtr++] = (char)(0xD800 | (d -= 0x10000) >> 10);
                     if(outPtr >= outputBuffer.length){
                        outputBuffer = endSeg();
                        outPtr = 0;
                     }
                     d = 0xDC00 | d & 0x3FF;
                  }
                  c = (char)d;
                  break;
               case 11: // RBRACKET
                  int count = 1;
                  while(true){
                     if(inPtr >= end)
                        assertMore();
                     if((c = inputBuffer[inPtr]) != ']')
                        break;
                     ++inPtr;
                     ++count;
                  }
                  if(c == '>' && count > 1)
                     thCDEnd();
                  while(count > 1){
                     outputBuffer[outPtr++] = ']';
                     if(outPtr >= outputBuffer.length){
                        outputBuffer = endSeg();
                        outPtr = 0;
                     }
                     --count;
                  }
                  c = ']';
                  break;
               case 1:  // INVALID
                  thC(c);
            }
         else if(c >= 0xD800 && c < 0xE000){
            char d = chkSurrgCh(c);
            outputBuffer[outPtr++] = c;
            if(outPtr >= outputBuffer.length){
               outputBuffer = endSeg();
               outPtr = 0;
            }
            c = d;
         }else if(c >= 0xFFFE)
            thC(c);
         outputBuffer[outPtr++] = c;
      }
      currSize = outPtr;
      if(coalescing && !pending)
         endClsTxt();
   }

   private final void endWS() throws XMLStreamException{
      char tmp = (char)cTmp;
      char[] outputBuffer;
      int outPtr = 1;
      if(tmp == '\r' || tmp == '\n'){
         if((outPtr = prolog(tmp)) < 0)
            return;
         outputBuffer = currSeg;
      }else
         (outputBuffer = reset())[0] = tmp;
      int ptr = inPtr;
      while(true){
         if(ptr >= end){
            if(!more())
               break;
            ptr = 0;
         }
         char c = buf[ptr];
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
            if(buf[ptr] == '\n')
               ++ptr;
            rowOff = ptr;
            ++currRow;
            c = '\n';
         }else if(c != ' ' && c != '\t'){
            inPtr = ptr;
            thC(c);
         }
         if(outPtr >= outputBuffer.length){
            outputBuffer = endSeg();
            outPtr = 0;
         }
         outputBuffer[outPtr++] = c;
      }
      inPtr = ptr;
      currSize = outPtr;
   }

   private final void endClsTxt() throws XMLStreamException{
      while(true){
         if(inPtr >= end && !more())
            return;
         if(buf[inPtr] == '<'){
            if((inPtr + 3 >= end && !loadNRet()) || buf[inPtr + 1] != '!' || buf[inPtr + 2] != '[')
               return;
            inPtr += 3;
            for(int i = 0; i < 6; ++i){
               if(inPtr >= end)
                  assertMore();
               if(buf[inPtr++] != CDATA.charAt(i))
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

   private final void endClsCData() throws XMLStreamException{
      final byte[] TYPES = Code.OTH;
      final char[] inputBuffer = buf;
      char[] outputBuffer = currSeg;
      int outPtr = currSize;
      char c = 0;
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
               if((c = inputBuffer[ptr++]) >= 0xD800 || (c <= 0xFF && TYPES[c] != 0)){
                  adv = false;
                  break;
               }
               outputBuffer[outPtr++] = c;
            }
         }while(adv);
         inPtr = ptr;
         if(c <= 0xFF)
            switch(TYPES[c]){
               case 2:  // WS_CR
                  if(ptr >= end)
                     assertMore();
                  if(inputBuffer[inPtr] == '\n')
                     ++inPtr;
                  c = '\n';
               case 3:  // WS_LF
                  rowOff = inPtr;
                  ++currRow;
                  break;
               case 11: // RBRACKET
                  int count = 0;
                  char d;
                  do{
                     if(inPtr >= end)
                        assertMore();
                     if((d = buf[inPtr]) != ']')
                        break;
                     ++inPtr;
                     ++count;
                  }while(true);
                  boolean ok = d == '>' && count >= 1;
                  if(ok)
                     --count;
                  for(; count > 0; --count){
                     outputBuffer[outPtr++] = ']';
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
            }
         else if(c >= 0xD800 && c < 0xE000){
            char d = chkSurrgCh(c);
            outputBuffer[outPtr++] = c;
            if(outPtr >= outputBuffer.length){
               outputBuffer = endSeg();
               outPtr = 0;
            }
            c = d;
         }else if(c >= 0xFFFE)
            thC(c);
         outputBuffer[outPtr++] = c;
      }
   }

   private final void endClsC() throws XMLStreamException{
      final byte[] TYPES = Code.TXT;
      final char[] inputBuffer = buf;
      char[] outputBuffer = currSeg;
      int outPtr = currSize;
      char c = 0;
outl: while(true){
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
               if((c = inputBuffer[ptr++]) >= 0xD800 || (c <= 0xFF && TYPES[c] != 0)){
                  adv = false;
                  break;
               }
               outputBuffer[outPtr++] = c;
            }
         }while(adv);
         inPtr = ptr;
         if(c <= 0xFF)
            switch(TYPES[c]){
               case 2:  // WS_CR
                  if(ptr >= end)
                     assertMore();
                  if(inputBuffer[inPtr] == '\n')
                     ++inPtr;
                  c = '\n';
               case 3:  // WS_LF
                  rowOff = inPtr;
                  ++currRow;
                  break;
               case 9:  // LT
                  --inPtr;
                  break outl;
               case 10: // AMP
                  int d = entInTxt();
                  if(d == 0){
                     pending = true;
                     break outl;
                  }
                  if((d >> 16) != 0){
                     outputBuffer[outPtr++] = (char)(0xD800 | (d -= 0x10000) >> 10);
                     if(outPtr >= outputBuffer.length){
                        outputBuffer = endSeg();
                        outPtr = 0;
                     }
                     d = 0xDC00 | d & 0x3FF;
                  }
                  c = (char)d;
                  break;
               case 11: // RBRACKET
                  int count = 1;
                  while(true){
                     if(inPtr >= end)
                        assertMore();
                     if((c = inputBuffer[inPtr]) != ']')
                        break;
                     ++inPtr;
                     ++count;
                  }
                  if(c == '>' && count > 1)
                     thCDEnd();
                  while(count > 1){
                     outputBuffer[outPtr++] = ']';
                     if(outPtr >= outputBuffer.length){
                        outputBuffer = endSeg();
                        outPtr = 0;
                     }
                     --count;
                  }
                  c = ']';
                  break;
               case 1:  // INVALID
                  thC(c);
            }
         else if(c >= 0xD800 && c < 0xE000){
            char d = chkSurrgCh(c);
            outputBuffer[outPtr++] = c;
            if(outPtr >= outputBuffer.length){
               outputBuffer = endSeg();
               outPtr = 0;
            }
            c = d;
         }else if(c >= 0xFFFE)
            thC(c);
         outputBuffer[outPtr++] = c;
      }
      currSize = outPtr;
   }

   @Override
   final boolean skipCTxt() throws XMLStreamException{
      while(true){
         if(inPtr >= end && !more())
            return false;
         if(buf[inPtr] == '<'){
            if((inPtr + 3 >= end && !loadNRet()) || buf[inPtr + 1] != '!' || buf[inPtr + 2] != '[')
               return false;
            inPtr += 3;
            for(int i = 0; i < 6; ++i){
               if(inPtr >= end)
                  assertMore();
               if(buf[inPtr++] != CDATA.charAt(i))
                  thInErr("Expected '[CDATA['");
            }
            skipCData();
         }else if(skipChars())
            return true;
      }
   }

   @Override
   final void skipComm() throws XMLStreamException{
      final byte[] TYPES = Code.OTH;
      final char[] inputBuffer = buf;
      char c = 0;
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
               if((c = inputBuffer[ptr++]) >= 0xD800 || (c <= 0xFF && TYPES[c] != 0)){
                  adv = false;
                  break;
               }
         }while(adv);
         inPtr = ptr;
         if(c <= 0xFF)
            switch(TYPES[c]){
               case 2:  // WS_CR
                  if(ptr >= max)
                     assertMore();
                  if(inputBuffer[inPtr] == '\n')
                     ++inPtr;
               case 3:  // WS_LF
                  rowOff = inPtr;
                  ++currRow;
                  continue;
               case 13: // HYPHEN
                  if(ptr >= max)
                     assertMore();
                  if(buf[inPtr] == '-'){
                     ++inPtr;
                     if(inPtr >= end)
                        assertMore();
                     if(buf[inPtr++] != '>')
                        thHyph();
                     return;
                  }
                  continue;
               case 1:  // INVALID
                  thC(c);
            }
      }
   }

   @Override
   final void skipPI() throws XMLStreamException{
      final byte[] TYPES = Code.OTH;
      final char[] inputBuffer = buf;
      char c = 0;
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
               if((c = inputBuffer[ptr++]) >= 0xD800 || (c <= 0xFF && TYPES[c] != 0)){
                  adv = false;
                  break;
               }
         }while(adv);
         inPtr = ptr;
         if(c <= 0xFF)
            switch(TYPES[c]){
               case 2:  // WS_CR
                  if(ptr >= max)
                     assertMore();
                  if(inputBuffer[inPtr] == '\n')
                     ++inPtr;
               case 3:  // WS_LF
                  rowOff = inPtr;
                  ++currRow;
                  continue;
               case 12: // QMARK
                  if(ptr >= max)
                     assertMore();
                  if(buf[inPtr] == '>'){
                     ++inPtr;
                     return;
                  }
            }
         else if(c >= 0xD800 && c < 0xE000)
            chkSurrgCh(c);
         else if(c >= 0xFFFE)
            thC(c);
      }
   }

   @Override
   final boolean skipChars() throws XMLStreamException{
      final byte[] TYPES = Code.TXT;
      final char[] inputBuffer = buf;
      char c = 0;
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
               if((c = inputBuffer[ptr++]) >= 0xD800 || (c <= 0xFF && TYPES[c] != 0)){
                  adv = false;
                  break;
               }
         }while(adv);
         inPtr = ptr;
         if(c <= 0xFF)
            switch(TYPES[c]){
               case 2:  // WS_CR
                  if(ptr >= max)
                     assertMore();
                  if(inputBuffer[inPtr] == '\n')
                     ++inPtr;
               case 3:  // WS_LF
                  rowOff = inPtr;
                  ++currRow;
                  continue;
               case 9:  // LT
                  --inPtr;
                  return false;
               case 10: // AMP
                  if(entInTxt() == 0)
                      return true;
                  continue;
               case 11: // RBRACKET
                  boolean ncount = false;
                  while(true){
                     if(inPtr >= end)
                        assertMore();
                     if((c = inputBuffer[inPtr]) != ']')
                        break;
                     ++inPtr;
                     ncount = true;
                  }
                  if(c == '>' && ncount)
                     thCDEnd();
                  continue;
               case 1:  // INVALID
                  thC(c);
            }
         else if(c >= 0xD800 && c < 0xE000)
            chkSurrgCh(c);
         else if(c >= 0xFFFE)
            thC(c);
      }
   }

   @Override
   final void skipCData() throws XMLStreamException{
      final byte[] TYPES = Code.OTH;
      final char[] inputBuffer = buf;
      char c = 0;
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
               if((c = inputBuffer[ptr++]) >= 0xD800 || (c <= 0xFF && TYPES[c] != 0)){
                  adv = false;
                  break;
               }
         }while(adv);
         inPtr = ptr;
         if(c <= 0xFF)
            switch(TYPES[c]){
               case 2:  // WS_CR
                  if(ptr >= max)
                     assertMore();
                  if(inputBuffer[inPtr] == '\n')
                     ++inPtr;
               case 3:  // WS_LF
                  rowOff = inPtr;
                  ++currRow;
                  continue;
               case 11: // RBRACKET
                  int count = 0;
                  do{
                     if(inPtr >= end)
                        assertMore();
                     ++count;
                  }while((c = buf[inPtr++]) == ']');
                  if(c == '>'){
                     if(count > 1)
                        return;
                  }else
                     --inPtr;
                  continue;
               case 1:  // INVALID
                  thC(c);
            }
         else if(c >= 0xD800 && c < 0xE000)
            chkSurrgCh(c);
         else if(c >= 0xFFFE)
            thC(c);
      }
   }

   @Override
   final void skipWS() throws XMLStreamException{
      int ptr = inPtr;
      while(true){
         if(ptr >= end){
            if(!more())
               break;
            ptr = 0;
         }
         char c = buf[ptr];
         if(c > ' ')
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
            if(buf[ptr] == '\n')
               ++ptr;
            rowOff = ptr;
            ++currRow;
         }else if(c != ' ' && c != '\t'){
            inPtr = ptr;
            thC(c);
         }
      }
      inPtr = ptr;
   }

   private final char Code(boolean reqd) throws XMLStreamException{
      if(inPtr >= end)
         assertMore();
      char c = buf[inPtr++];
      if(c > 0x20){
         if(!reqd)
            return c;
         thUnxp(c, ", expected white space");
      }
      do{
         if(c == '\n'){
            rowOff = inPtr;
            ++currRow;
         }else if(c == '\r'){
            if(inPtr >= end)
               assertMore();
            if(buf[inPtr] == '\n')
               ++inPtr;
            rowOff = inPtr;
            ++currRow;
         }else if(c != ' ' && c != '\t')
            thC(c);
         if(inPtr >= end)
            assertMore();
      }while((c = buf[inPtr++]) <= 0x20);
      return c;
   }

   private final void Code(String kw) throws XMLStreamException{
      for(int i = 1, len = kw.length(); i < len; ++i){
         if(inPtr >= end)
            assertMore();
         char c = buf[inPtr++];
         if(c != kw.charAt(i))
            thUnxp(c, new StrB(18).a(", expected ").a(kw).toString());
      }
   }

   private final int inTree(char c) throws XMLStreamException{
      if(c == '\r'){
         if(inPtr >= end && !more()){
            indent(0, ' ');
            return -1;
         }
         if(buf[inPtr] == '\n')
            ++inPtr;
      }
      rowOff = inPtr;
      ++currRow;
      if(inPtr >= end)
         assertMore();
      if((c = buf[inPtr]) != ' ' && c != '\t'){
         if(c == '<' && inPtr + 1 < end && buf[inPtr + 1] != '!'){
            indent(0, ' ');
            return -1;
         }
         reset()[0] = '\n';
         return currSize = 1;
      }
      ++inPtr;
      int count = 1, max = c == ' ' ? 32 : 8;
      while(count <= max){
         if(inPtr >= end)
            assertMore();
         char c2 = buf[inPtr];
         if(c2 != c){
            if(c2 == '<' && inPtr + 1 < end && buf[inPtr + 1] != '!'){
               indent(count, c);
               return -1;
            }
            break;
         }
         ++inPtr;
         ++count;
      }
      final char[] outputBuffer = reset();
      outputBuffer[0] = '\n';
      for(int i = 1; i <= count; ++i)
         outputBuffer[i] = c;
      return currSize = ++count;
   }

   private final int prolog(char c) throws XMLStreamException{
      if(c == '\r'){
         if(inPtr >= end && !more()){
            indent(0, ' ');
            return -1;
         }
         if(buf[inPtr] == '\n')
            ++inPtr;
      }
      rowOff = inPtr;
      ++currRow;
      if(inPtr >= end && !more()){
         indent(0, ' ');
         return -1;
      }
      if((c = buf[inPtr]) != ' ' && c != '\t'){
         if(c == '<'){
            indent(0, ' ');
            return -1;
         }
         reset()[0] = '\n';
         return currSize = 1;
      }
      int count = 1, max = c == ' ' ? 32 : 8;
      while((++inPtr < end || more()) && buf[inPtr] == c)
         if(++count >= max){
            char[] outputBuffer = reset();
            outputBuffer[0] = '\n';
            for(int i = 1; i <= count; ++i)
               outputBuffer[i] = c;
            ++inPtr;
            return currSize = ++count;
         }
      indent(count, c);
      return -1;
   }

   private final PN parsePN(char c) throws XMLStreamException{
      if(c < 'A')
         thUnxp(c, ", not a name start char");
      char[] nameBuffer = nameBuf;
      nameBuffer[0] = c;
      int hash = c, ptr = 1;
      while(true){
         if(inPtr >= end)
            assertMore();
         if((c = buf[inPtr]) < 45 || (c > 58 && c < 65) || c == 47){
            PN n = syms.find(nameBuffer, ptr, hash);
            if(n == null)
               n = Code(nameBuffer, ptr, hash);
            return n;
         }
         ++inPtr;
         if(ptr >= nameBuffer.length)
            nameBuf = nameBuffer = xpand(nameBuffer);
         nameBuffer[ptr++] = c;
         hash = hash * 31 + c;
      }
   }

   private final PN Code(char[] nameBuffer, int nameLen, int hash) throws XMLStreamException{
      char c = nameBuffer[0];
      int namePtr = 1, last_colon = -1;
      if(c < 0xD800 || c >= 0xE000){
         if(!Chr.is10NS(c))
            thInvNCh(c);
      }else{
         if(nameLen == 1)
            thSurr(c);
         Code(c, nameBuffer[1]);
         ++namePtr;
      }
      for(; namePtr < nameLen; ++namePtr)
         if((c = nameBuffer[namePtr]) < 0xD800 || c >= 0xE000){
            if(c == ':'){
               if(last_colon >= 0)
                  thInErr("Multiple ':'");
               last_colon = namePtr;
            }else if(!Chr.is10N(c))
               thInvNCh(c);
         }else{
            if(namePtr + 1 >= nameLen)
               thSurr(c);
            Code(c, nameBuffer[namePtr + 1]);
         }
      return syms.add(nameBuffer, nameLen, hash);
   }

   private final String Code(char quote) throws XMLStreamException{
      char[] outputBuffer = nameBuf;
      int outPtr = 0;
      final byte[] TYPES = Code.ATT;
      while(true){
         if(inPtr >= end)
            assertMore();
         char c = buf[inPtr++];
         if(c == quote)
            return new String(outputBuffer, 0, outPtr);
         switch(TYPES[c]){
            case 2: // WS_CR
               if(inPtr >= end)
                  assertMore();
               if(buf[inPtr] == '\n')
                  ++inPtr;
               c = '\n';
            case 3: // WS_LF
               rowOff = inPtr;
               ++currRow;
               break;
            case 1: // INVALID
               thC(c);
         }
         if(outPtr >= outputBuffer.length)
            nameBuf = outputBuffer = xpand(outputBuffer);
         outputBuffer[outPtr++] = c;
      }
   }

   private final void Code(char chr, char sec) throws XMLStreamException{
      int val = ((chr - 0xD800) << 10) + 0x10000;
      if(chr >= 0xDC00 || (chr = sec) < 0xDC00 || chr >= 0xE000)
         thSurr(chr);
      if(val > 0x10FFFF)
         thInvC(val);
   }

   private final void thSurr(char ch) throws XMLStreamException{ thInErr(new StrB(23).a("Invalid surrogate ").apos((int)ch).toString()); }

   @Override
   final javax.xml.stream.Location loc(){ return new LocImpl(impl.pubId, impl.sysId, inPtr - rowOff, currRow, bOrC + inPtr); }

   private final boolean loadNRet() throws XMLStreamException{
      if(in == null)
         return false;
      bOrC += inPtr;
      rowOff -= inPtr;
      int xx = end - inPtr;
      System.arraycopy(buf, inPtr, buf, 0, xx);
      inPtr = 0;
      end = xx;
      try{
         do
            if((xx = in.read(buf, end, 4096 - end)) < 1){
               if(xx == 0)
                  thInErr("Reader returned 0");
               return false;
            }
         while((end += xx) < 3);
         return true;
      }catch(IOException ioe){
         throw new XMLStreamException(ioe);
      }
   }

   @Override
   final void close() throws IOException{
      if(in != null){
         in.close();
         in = null;
      }
   }

   @Override
   final int col(){ return inPtr - iniRawOff; }

   @Override
   final void Code(){
      super.Code();
      if(syms.dirty)
         impl.genTab.Code(syms);
      if(buf != null){
         impl.setCB3(buf);
         buf = null;
      }
   }
}
