// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025
package com.celerxml;

import java.io.Reader;
import java.io.IOException;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.Location;

final class ReaderScanner extends XmlScanner{

   private final static Chr chrTypes = Chr.getLat1();
   private Reader in;
   private char[] inBuf;
   private int inPtr, end, cTmp;
   private final ChrPN syms;
   private Node curr;

   ReaderScanner(InputFactoryImpl impl, Reader in, char[] inBuf, int inPtr, int end){
      super(impl);
      this.in = in;
      if(impl.genTab == null)
         impl.genTab = new ChrPN();
      syms = new ChrPN(impl.genTab);
      this.inBuf = inBuf;
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
         int count = in.read(inBuf, 0, 4096);
         if(count < 1){
            end = 0;
            if(count == 0)
               throwInputErr("Reader returned 0");
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
      if(chr >= 0xDC00 || (chr = inBuf[inPtr++]) < 0xDC00 || chr >= 0xE000)
         throwInvalidSurrogate(chr);
      if(val > 0x10FFFF)
         throwInvChr(val);
      return chr;
   }

   @Override
   final void endTok() throws XMLStreamException{
      incomplete = false;
      switch(currToken){
         case 3:  // PROCESSING_INSTRUCTION
            endPI();
            return;
         case 4:  // CHARACTERS
            endChars();
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
      if(incomplete)
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
         switch(c = inBuf[inPtr++]){
            case '<':
               if(inPtr >= end)
                  assertMore();
               if((c = inBuf[inPtr++]) == '!')
                  return doPrologDecl(isProlog);
               if(c == '?')
                  return handlePIStart();
               if(c == '/' || !isProlog)
                  throwUnexpRoot(isProlog, c);
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
               if(inBuf[inPtr] == '\n')
                  ++inPtr;
            case '\n':
               rowOff = inPtr;
               ++currRow;
               break;
            default:
               throwPlogUnxpChr(isProlog, c);
         }
      }
   }

   @Override
   final int nxtFromTree() throws XMLStreamException{
      if(incomplete){
         if(skipTok()){
            reset();
            return currToken = 9; // ENTITY_REFERENCE
         }
      }else if(currToken == 1){ // START_ELEMENT
         if(emptyTag){
            --depth;
            return currToken = 2; // END_ELEMENT
         }
      }else if(currToken == 2){ // END_ELEMENT
         curr = curr.mNext;
         while(lastNs != null && lastNs.lvl >= depth)
            lastNs = lastNs.unbind();
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
      int c = inBuf[inPtr];
      if(c == '<'){
         if(++inPtr >= end && !more())
            throwInputErr(EOI);
         if((c = inBuf[inPtr++]) == '!')
            return commOrCdataStart();
         if(c == '?')
            return handlePIStart();
         if(c == '/')
            return handleEndElement();
         return startElem((char)c);
      }
      if(c == '&'){
         ++inPtr;
         if((c = -entInTxt()) == 0)
            return currToken = 9; // ENTITY_REFERENCE
      }
      cTmp = c;
      if(lazy)
         incomplete = true;
      else
         endChars();
      return currToken = 4; // CHARACTERS
   }

   private final int doPrologDecl(boolean isProlog) throws XMLStreamException{
      if(inPtr >= end)
         assertMore();
      char c = inBuf[inPtr++];
      if(c == '-'){
         if(inPtr >= end)
            assertMore();
         if((c = inBuf[inPtr++]) == '-'){
            if(lazy)
               incomplete = true;
            else
               endComm();
            return currToken = 5; // COMMENT
         }
      }else if(isProlog && c == 'D'){
         handleDtdStart();
         if(!lazy && incomplete){
            endDTD();
            incomplete = false;
         }
         return 11; // DTD
      }
      incomplete = true;
      currToken = 4; // CHARACTERS
      throwPlogUnxpChr(isProlog, c);
      return 0;
   }

   private final int handleDtdStart() throws XMLStreamException{
      matchKW("DOCTYPE");
      char q, c;
      tokName = parsePName(skipInternalWs(true));
      if((c = skipInternalWs(false)) == 'P'){
         matchKW("PUBLIC");
         q = skipInternalWs(true);
         char[] outputBuffer = nameBuf;
         int outPtr = 0;
         final byte[] TYPES = Chr.PUB;
         boolean addSpace = false;
         while(true){
            if(inPtr >= end)
               assertMore();
            if((c = inBuf[inPtr++]) == q)
               break;
            if(c > 0xFF || TYPES[c] != 1) // PUBID_OK
               throwUnexpChr(c, " in public identifier");
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
         dtdSysId = parseSystemId(skipInternalWs(true));
         c = skipInternalWs(false);
      }else if(c == 'S'){
         matchKW("SYSTEM");
         dtdPubId = null;
         dtdSysId = parseSystemId(skipInternalWs(true));
         c = skipInternalWs(false);
      }else
         dtdPubId = dtdSysId = null;
      if((incomplete = c == '[') || c == '>')
         return currToken = 11; // DTD
      throwUnexpChr(c, ", expected '[' or '>'");
      return 0;
   }

   private final int commOrCdataStart() throws XMLStreamException{
      if(inPtr >= end)
         assertMore();
      char c = inBuf[inPtr++];
      if(c == '-'){
         if(inPtr >= end)
            assertMore();
         if(inBuf[inPtr++] != '-')
            throwInputErr("Expected '-'");
         if(lazy)
            incomplete = true;
         else
            endComm();
         return currToken = 5; // COMMENT
      }
      if(c == '['){
         currToken = 12; // CDATA
         for(int i = 0; i < 6; ++i){
            if(inPtr >= end)
               assertMore();
            if(inBuf[inPtr++] != CDATA.charAt(i))
               throwInputErr("Expected '[CDATA['");
         }
         if(lazy)
            incomplete = true;
         else
            endCData();
         return 12; // CDATA
      }
      throwInputErr("Expected '-' or '[CDATA['");
      return 0;
   }

   private final int handlePIStart() throws XMLStreamException{
      currToken = 3; // PROCESSING_INSTRUCTION
      if(inPtr >= end)
         assertMore();
      tokName = parsePName(inBuf[inPtr++]);
      String ln = tokName.ln;
      if(ln.length() == 3 && ln.equalsIgnoreCase("xml") && tokName.pfx == null)
         throwInputErr("Target 'xml' reserved");
      if(inPtr >= end)
         assertMore();
      char c = inBuf[inPtr++];
      if(c <= 0x20){
         while(true){
            if(c == '\n'){
               rowOff = inPtr;
               ++currRow;
            }else if(c == '\r'){
               if(inPtr >= end)
                  assertMore();
               if(inBuf[inPtr] == '\n')
                  ++inPtr;
               rowOff = inPtr;
               ++currRow;
            }else if(c != ' ' && c != '\t')
               throwChr(c);
            if(inPtr >= end)
               assertMore();
            if((c = inBuf[inPtr]) > 0x20)
               break;
            ++inPtr;
         }
         if(lazy)
            incomplete = true;
         else
            endPI();
      }else{
         if(c != (int)'?')
            throwNoPISpace(c);
         if(inPtr >= end)
            assertMore();
         if((c = inBuf[inPtr++]) != '>')
            throwNoPISpace(c);
         reset();
         incomplete = false;
      }
      return 3; // PROCESSING_INSTRUCTION
   }

   private final int chrEnt() throws XMLStreamException{
      if(inPtr >= end)
         assertMore();
      char c = inBuf[inPtr++];
      int value = 0;
      if(c == 'x')
         while(true){
            if(inPtr >= end)
               assertMore();
            if((c = inBuf[inPtr++]) == ';')
               break;
            if((c = (char)((c | 0x20) - 0x30)) > 9) // to lowercase
               c -= 0x27;
            if(c < 0 || c > 0xF)
               throwUnexpChr(c, ", not a hex digit [0-9a-fA-F]");
            value = value << 4 | c;
         }
      else
         while(c != ';'){
            if(c < '0' || c > '9')
               throwUnexpChr(c, ", not a decimal number");
            value = value * 10 + c - '0';
            if(inPtr >= end)
               assertMore();
            c = inBuf[inPtr++];
         }
      if((value >= 0xD800 && value < 0xE000) || value == 0 || value == 0xFFFE || value == 0xFFFF)
         throwInvChr(value);
      return value;
   }

   private final int startElem(char c) throws XMLStreamException{
      currToken = 1; // START_ELEMENT
      currNsCount = 0;
      PN elemName = parsePName(c);
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
         if((c = inBuf[inPtr++]) <= 0x20)
            do{
               if(c == '\n'){
                  rowOff = inPtr;
                  ++currRow;
               }else if(c == '\r'){
                  if(inPtr >= end)
                     assertMore();
                  if(inBuf[inPtr] == '\n')
                     ++inPtr;
                  rowOff = inPtr;
                  ++currRow;
               }else if(c != ' ' && c != '\t')
                  throwChr(c);
               if(inPtr >= end)
                  assertMore();
            }while((c = inBuf[inPtr++]) <= 0x20);
         else if(c != '/' && c != '>')
            throwUnexpChr(c, ", not space or '>' or '/>'");
         if(c == '/'){
            if(inPtr >= end)
               assertMore();
            if((c = inBuf[inPtr++]) != '>')
               throwUnexpChr(c, ", not '>'");
            emptyTag = true;
            break;
         }else if(c == '>'){
            emptyTag = false;
            break;
         }else if(c == '<')
            throwInputErr("Unexpected '<'");
         PN attrName = parsePName(c);
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
            if((c = inBuf[inPtr++]) > 0x20)
               break;
            if(c == '\n'){
               rowOff = inPtr;
               ++currRow;
            }else if(c == '\r'){
               if(inPtr >= end)
                  assertMore();
               if(inBuf[inPtr] == '\n')
                  ++inPtr;
               rowOff = inPtr;
               ++currRow;
            }else if(c != ' ' && c != '\t')
               throwChr(c);
         }
         if(c != '=')
            throwUnexpChr(c, ", not '='");
         while(true){
            if(inPtr >= end)
               assertMore();
            if((c = inBuf[inPtr++]) > 0x20)
               break;
            if(c == '\n'){
               rowOff = inPtr;
               ++currRow;
            }else if(c == '\r'){
               if(inPtr >= end)
                  assertMore();
               if(inBuf[inPtr] == '\n')
                  ++inPtr;
               rowOff = inPtr;
               ++currRow;
            }else if(c != ' ' && c != '\t')
               throwChr(c);
         }
         if(c != '"' && c != '\'')
            throwUnexpChr(c, ", not a quote");
         if(isNsDecl){
            handleNsDecl(attrName, c);
            ++currNsCount;
         }else
            xx = collectValue(xx, c, attrName);
      }
      if((xx = endLastV(xx)) < 0)
         throwInputErr(errMsg);
      attrCount = xx;
      ++depth;
      if(!allBound){
         if(!elemName.isBound())
            throwUnbPfx(tokName, false);
         for(int i = 0, len = attrCount; i < len; ++i){
            PN attrName = names[i];
            if(!attrName.isBound())
               throwUnbPfx(attrName, true);
         }
      }
      return 1; // START_ELEMENT
   }

   private final int collectValue(int attrPtr, char quote, PN attrName) throws XMLStreamException{
      final byte[] TYPES = chrTypes.ATT;
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
               if((c = inBuf[ptr++]) >= 0xD800 || (c <= 0xFF && TYPES[c] != 0)){
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
                  if(inBuf[inPtr] == '\n')
                     ++inPtr;
               case 3:  // WS_LF
                  rowOff = inPtr;
                  ++currRow;
               case 8:  // WS_TAB
                  c = ' ';
                  break;
               case 10: // AMP
                  if((ptr = entInTxt()) == 0)
                     throwUnexpandEnt();
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
                  throwChr(c);
               case 9:  // LT
                  throwUnexpChr(c, " in attribute value");
            }
         else if(c >= 0xD800 && c < 0xE000){
            char d = chkSurrgCh(c);
            attrBuffer[attrPtr++] = c;
            if(attrPtr >= attrBuffer.length)
               attrBuffer = xpand();
            c = d;
         }else if(c >= 0xFFFE)
            throwChr(c);
         attrBuffer[attrPtr++] = c;
      }
   }

   private final void handleNsDecl(PN name, char quote) throws XMLStreamException{
      int attrPtr = 0;
      char[] attrBuffer = nameBuf;
      while(true){
         if(inPtr >= end)
            assertMore();
         char c = inBuf[inPtr++];
         if(c == quote){
            bindNs(name, attrPtr == 0 ? "" : impl.cacheURI(attrBuffer, attrPtr));
            return;
         }
         if(c == '&'){
            int d = entInTxt();
            if(d == 0)
               throwUnexpandEnt();
            if((d >> 16) != 0){
               if(attrPtr >= attrBuffer.length)
                  nameBuf = attrBuffer = xpand(attrBuffer);
               attrBuffer[attrPtr++] = (char)(0xD800 | (d -= 0x10000) >> 10);
               d = 0xDC00 | d & 0x3FF;
            }
            c = (char)d;
         }else if(c == '<')
            throwUnexpChr(c, " in attribute value");
         else if(c < 0x20){
            if(c == '\n'){
               rowOff = inPtr;
               ++currRow;
            }else if(c == '\r'){
               if(inPtr >= end)
                  assertMore();
               if(inBuf[inPtr] == '\n')
                  ++inPtr;
               rowOff = inPtr;
               ++currRow;
               c = '\n';
            }else if(c != '\t')
               throwChr(c);
         }
         if(attrPtr >= attrBuffer.length)
            nameBuf = attrBuffer = xpand(attrBuffer);
         attrBuffer[attrPtr++] = c;
      }
   }

   private final int handleEndElement() throws XMLStreamException{
      --depth;
      currToken = 2; // END_ELEMENT
      tokName = curr.mName;
      String pname = tokName.pfxdName;
      int i = 0, len = pname.length();
      do{
         if(inPtr >= end)
            assertMore();
         if(inBuf[inPtr++] != pname.charAt(i))
            throwUnexpEnd(pname);
      }while(++i < len);
      if(inPtr >= end)
         assertMore();
      char c = inBuf[inPtr++];
      if(c <= ' ')
         c = skipInternalWs(false);
      else if(c == ':' || Chr.is10N(c))
         throwUnexpEnd(pname);
      if(c != '>')
         throwUnexpChr(c, ", not space or closing '>'");
      return 2; // END_ELEMENT
   }

   private final int entInTxt() throws XMLStreamException{
      if(inPtr >= end)
         assertMore();
      char c = inBuf[inPtr++];
      if(c == '#')
         return chrEnt();
      char[] cbuf = nameBuf;
      int cix = 0;
      if(c == 'a'){
         if(inPtr >= end)
            assertMore();
         cbuf[cix++] = c;
         if((c = inBuf[inPtr++]) == 'm'){
            if(inPtr >= end)
               assertMore();
            cbuf[cix++] = c;
            if((c = inBuf[inPtr++]) == 'p'){
               if(inPtr >= end)
                  assertMore();
               cbuf[cix++] = c;
               if((c = inBuf[inPtr++]) == ';')
                  return '&';
            }
         }else if(c == 'p'){
            if(inPtr >= end)
               assertMore();
            cbuf[cix++] = c;
            if((c = inBuf[inPtr++]) == 'o'){
               if(inPtr >= end)
                  assertMore();
               cbuf[cix++] = c;
               if((c = inBuf[inPtr++]) == 's'){
                  if(inPtr >= end)
                     assertMore();
                  cbuf[cix++] = c;
                  if((c = inBuf[inPtr++]) == ';')
                     return '\'';
               }
            }
         }
      }else if(c == 'l'){
         if(inPtr >= end)
            assertMore();
         cbuf[cix++] = c;
         if((c = inBuf[inPtr++]) == 't'){
            if(inPtr >= end)
               assertMore();
            cbuf[cix++] = c;
            if((c = inBuf[inPtr++]) == ';')
               return '<';
         }
      }else if(c == 'g'){
         if(inPtr >= end)
            assertMore();
         cbuf[cix++] = c;
         if((c = inBuf[inPtr++]) == 't'){
            if(inPtr >= end)
               assertMore();
            cbuf[cix++] = c;
            if((c = inBuf[inPtr++]) == ';')
               return '>';
         }
      }else if(c == 'q'){
         if(inPtr >= end)
            assertMore();
         cbuf[cix++] = c;
         if((c = inBuf[inPtr++]) == 'u'){
            if(inPtr >= end)
               assertMore();
            cbuf[cix++] = c;
            if((c = inBuf[inPtr++]) == 'o'){
               if(inPtr >= end)
                  assertMore();
               cbuf[cix++] = c;
               if((c = inBuf[inPtr++]) == 't'){
                  if(inPtr >= end)
                     assertMore();
                  cbuf[cix++] = c;
                  if((c = inBuf[inPtr++]) == ';')
                     return '"';
               }
            }
         }
      }
      final byte[] TYPES = chrTypes.NAM;
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
               throwInvalidSurrogate(c);
            if(inPtr >= end)
               assertMore();
            char sec = inBuf[inPtr++];
            if(sec < 0xDC00 || sec >= 0xE000)
               throwInvalidSurrogate(sec);
            int value = ((c - 0xD800) << 10) + 0x10000;
            if(value > 0x10FFFF)
               throwInvChr(value);
            if(cix >= cbuf.length)
               nameBuf = cbuf = xpand(cbuf);
            cbuf[cix++] = c;
            c = inBuf[inPtr - 1];
            ok = Chr.is10N(value);
         }else if(c >= 0xFFFE)
            throwChr(c);
         else
            ok = true;
         if(!ok)
            throwInvNChr(c);
         if(cix >= cbuf.length)
            nameBuf = cbuf = xpand(cbuf);
         cbuf[cix++] = c;
         if(inPtr >= end)
            assertMore();
         c = inBuf[inPtr++];
      }
      if(impl.getF(16))
         throwInputErr("Entity reference in entity expanding mode");
      String pname = new String(cbuf, 0, cix);
      tokName = new PN(pname, null, pname, 0);
      return 0;
   }

   private final void endComm() throws XMLStreamException{
      final byte[] TYPES = chrTypes.OTH;
      final char[] inputBuffer = inBuf;
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
                  if(inBuf[inPtr] == '-'){
                     ++inPtr;
                     if(inPtr >= end)
                        assertMore();
                     if(inBuf[inPtr++] != '>')
                        throwHyphn();
                     currSize = outPtr;
                     return;
                  }
                  break;
               case 1:  // INVALID
                  throwChr(c);
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
            throwChr(c);
         outputBuffer[outPtr++] = c;
      }
   }

   private final void endPI() throws XMLStreamException{
      final byte[] TYPES = chrTypes.OTH;
      final char[] inputBuffer = inBuf;
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
                  if(inBuf[inPtr] == '>'){
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
            throwChr(c);
         outputBuffer[outPtr++] = c;
      }
   }

   private final void endDTD() throws XMLStreamException{
      char[] outBuf = reset();
      int outPtr = 0, quoteChar = 0;
      final byte[] TYPES = chrTypes.DTD;
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
               if((c = inBuf[ptr++]) >= 0xD800 || (c <= 0xFF && TYPES[c] != 0)){
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
                  if(inBuf[inPtr] == '\n')
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
                     if((c = skipInternalWs(false)) != '>')
                        throwUnexpChr(c, ", not '>' after internal subset");
                     return;
                  }
                  break;
               case 1:  // INVALID
                  throwChr(c);
            }
         else if(c >= 0xD800 && c < 0xE000){
            outBuf[outPtr++] = c;
            c = chkSurrgCh(c);
            if(outPtr >= outBuf.length){
               outBuf = endSeg();
               outPtr = 0;
            }
         }else if(c >= 0xFFFE)
            throwChr(c);
         outBuf[outPtr++] = c;
      }
   }

   @Override
   final void skipDTD() throws XMLStreamException{
      int quoteChar = 0;
      final byte[] TYPES = chrTypes.DTD;
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
               if((c = inBuf[ptr++]) >= 0xD800 || (c <= 0xFF && TYPES[c] != 0)){
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
                  if(inBuf[inPtr] == '\n')
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
                     if((c = skipInternalWs(false)) != '>')
                        throwUnexpChr(c, ", not '>' after internal subset");
                     return;
                  }
                  continue;
               case 1:  // INVALID
                  throwChr(c);
            }
         else if(c >= 0xD800 && c < 0xE000)
            chkSurrgCh(c);
         else if(c >= 0xFFFE)
            throwChr(c);
      }
   }

   private final void endCData() throws XMLStreamException{
      final byte[] TYPES = chrTypes.OTH;
      final char[] inputBuffer = inBuf;
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
                     if((d = inBuf[inPtr]) != ']')
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
                  throwChr(c);
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
            throwChr(c);
         outputBuffer[outPtr++] = c;
      }
   }

   private final void endChars() throws XMLStreamException{
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
         if((outPtr = inTreeIndent((char)t)) < 0)
            return;
         outputBuffer = currSeg;
      }else
         outputBuffer = reset();
      final byte[] TYPES = chrTypes.TXT;
      final char[] inputBuffer = inBuf;
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
                     throwCDataEnd();
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
                  throwChr(c);
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
            throwChr(c);
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
         if((outPtr = prologIndent(tmp)) < 0)
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
         char c = inBuf[ptr];
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
            if(inBuf[ptr] == '\n')
               ++ptr;
            rowOff = ptr;
            ++currRow;
            c = '\n';
         }else if(c != ' ' && c != '\t'){
            inPtr = ptr;
            throwChr(c);
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
         if(inBuf[inPtr] == '<'){
            if((inPtr + 3 >= end && !loadNRet()) || inBuf[inPtr + 1] != '!' || inBuf[inPtr + 2] != '[')
               return;
            inPtr += 3;
            for(int i = 0; i < 6; ++i){
               if(inPtr >= end)
                  assertMore();
               if(inBuf[inPtr++] != CDATA.charAt(i))
                  throwInputErr("Expected '[CDATA['");
            }
            endClsCData();
         }else{
            endClsChars();
            if(pending)
               return;
         }
      }
   }

   private final void endClsCData() throws XMLStreamException{
      final byte[] TYPES = chrTypes.OTH;
      final char[] inputBuffer = inBuf;
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
                     if((d = inBuf[inPtr]) != ']')
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
                  throwChr(c);
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
            throwChr(c);
         outputBuffer[outPtr++] = c;
      }
   }

   private final void endClsChars() throws XMLStreamException{
      final byte[] TYPES = chrTypes.TXT;
      final char[] inputBuffer = inBuf;
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
                     throwCDataEnd();
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
                  throwChr(c);
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
            throwChr(c);
         outputBuffer[outPtr++] = c;
      }
      currSize = outPtr;
   }

   @Override
   final boolean skipCTxt() throws XMLStreamException{
      while(true){
         if(inPtr >= end && !more())
            return false;
         if(inBuf[inPtr] == '<'){
            if((inPtr + 3 >= end && !loadNRet()) || inBuf[inPtr + 1] != '!' || inBuf[inPtr + 2] != '[')
               return false;
            inPtr += 3;
            for(int i = 0; i < 6; ++i){
               if(inPtr >= end)
                  assertMore();
               if(inBuf[inPtr++] != CDATA.charAt(i))
                  throwInputErr("Expected '[CDATA['");
            }
            skipCData();
         }else if(skipChars())
            return true;
      }
   }

   @Override
   final void skipComm() throws XMLStreamException{
      final byte[] TYPES = chrTypes.OTH;
      final char[] inputBuffer = inBuf;
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
                  if(inBuf[inPtr] == '-'){
                     ++inPtr;
                     if(inPtr >= end)
                        assertMore();
                     if(inBuf[inPtr++] != '>')
                        throwHyphn();
                     return;
                  }
                  continue;
               case 1:  // INVALID
                  throwChr(c);
            }
      }
   }

   @Override
   final void skipPI() throws XMLStreamException{
      final byte[] TYPES = chrTypes.OTH;
      final char[] inputBuffer = inBuf;
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
                  if(inBuf[inPtr] == '>'){
                     ++inPtr;
                     return;
                  }
            }
         else if(c >= 0xD800 && c < 0xE000)
            chkSurrgCh(c);
         else if(c >= 0xFFFE)
            throwChr(c);
      }
   }

   @Override
   final boolean skipChars() throws XMLStreamException{
      final byte[] TYPES = chrTypes.TXT;
      final char[] inputBuffer = inBuf;
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
                     throwCDataEnd();
                  continue;
               case 1:  // INVALID
                  throwChr(c);
            }
         else if(c >= 0xD800 && c < 0xE000)
            chkSurrgCh(c);
         else if(c >= 0xFFFE)
            throwChr(c);
      }
   }

   @Override
   final void skipCData() throws XMLStreamException{
      final byte[] TYPES = chrTypes.OTH;
      final char[] inputBuffer = inBuf;
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
                  }while((c = inBuf[inPtr++]) == ']');
                  if(c == '>'){
                     if(count > 1)
                        return;
                  }else
                     --inPtr;
                  continue;
               case 1:  // INVALID
                  throwChr(c);
            }
         else if(c >= 0xD800 && c < 0xE000)
            chkSurrgCh(c);
         else if(c >= 0xFFFE)
            throwChr(c);
      }
   }

   @Override
   final void skipSpace() throws XMLStreamException{
      int ptr = inPtr;
      while(true){
         if(ptr >= end){
            if(!more())
               break;
            ptr = 0;
         }
         char c = inBuf[ptr];
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
            if(inBuf[ptr] == '\n')
               ++ptr;
            rowOff = ptr;
            ++currRow;
         }else if(c != ' ' && c != '\t'){
            inPtr = ptr;
            throwChr(c);
         }
      }
      inPtr = ptr;
   }

   private final char skipInternalWs(boolean reqd) throws XMLStreamException{
      if(inPtr >= end)
         assertMore();
      char c = inBuf[inPtr++];
      if(c > 0x20){
         if(!reqd)
            return c;
         throwUnexpChr(c, ", expected white space");
      }
      do{
         if(c == '\n'){
            rowOff = inPtr;
            ++currRow;
         }else if(c == '\r'){
            if(inPtr >= end)
               assertMore();
            if(inBuf[inPtr] == '\n')
               ++inPtr;
            rowOff = inPtr;
            ++currRow;
         }else if(c != ' ' && c != '\t')
            throwChr(c);
         if(inPtr >= end)
            assertMore();
      }while((c = inBuf[inPtr++]) <= 0x20);
      return c;
   }

   private final void matchKW(String kw) throws XMLStreamException{
      for(int i = 1, len = kw.length(); i < len; ++i){
         if(inPtr >= end)
            assertMore();
         char c = inBuf[inPtr++];
         if(c != kw.charAt(i))
            throwUnexpChr(c, new StrB(18).a(", expected ").a(kw).toString());
      }
   }

   private final int inTreeIndent(char c) throws XMLStreamException{
      if(c == '\r'){
         if(inPtr >= end && !more()){
            doIndent(0, ' ');
            return -1;
         }
         if(inBuf[inPtr] == '\n')
            ++inPtr;
      }
      rowOff = inPtr;
      ++currRow;
      if(inPtr >= end)
         assertMore();
      if((c = inBuf[inPtr]) != ' ' && c != '\t'){
         if(c == '<' && inPtr + 1 < end && inBuf[inPtr + 1] != '!'){
            doIndent(0, ' ');
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
         char c2 = inBuf[inPtr];
         if(c2 != c){
            if(c2 == '<' && inPtr + 1 < end && inBuf[inPtr + 1] != '!'){
               doIndent(count, c);
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

   private final int prologIndent(char c) throws XMLStreamException{
      if(c == '\r'){
         if(inPtr >= end && !more()){
            doIndent(0, ' ');
            return -1;
         }
         if(inBuf[inPtr] == '\n')
            ++inPtr;
      }
      rowOff = inPtr;
      ++currRow;
      if(inPtr >= end && !more()){
         doIndent(0, ' ');
         return -1;
      }
      if((c = inBuf[inPtr]) != ' ' && c != '\t'){
         if(c == '<'){
            doIndent(0, ' ');
            return -1;
         }
         reset()[0] = '\n';
         return currSize = 1;
      }
      int count = 1, max = c == ' ' ? 32 : 8;
      while((++inPtr < end || more()) && inBuf[inPtr] == c)
         if(++count >= max){
            char[] outputBuffer = reset();
            outputBuffer[0] = '\n';
            for(int i = 1; i <= count; ++i)
               outputBuffer[i] = c;
            ++inPtr;
            return currSize = ++count;
         }
      doIndent(count, c);
      return -1;
   }

   private final PN parsePName(char c) throws XMLStreamException{
      if(c < 'A')
         throwUnexpChr(c, ", not a name start char");
      char[] nameBuffer = nameBuf;
      nameBuffer[0] = c;
      int hash = c, ptr = 1;
      while(true){
         if(inPtr >= end)
            assertMore();
         if((c = inBuf[inPtr]) < 45 || (c > 58 && c < 65) || c == 47){
            PN n = syms.find(nameBuffer, ptr, hash);
            if(n == null)
               n = addPName(nameBuffer, ptr, hash);
            return n;
         }
         ++inPtr;
         if(ptr >= nameBuffer.length)
            nameBuf = nameBuffer = xpand(nameBuffer);
         nameBuffer[ptr++] = c;
         hash = hash * 31 + c;
      }
   }

   private final PN addPName(char[] nameBuffer, int nameLen, int hash) throws XMLStreamException{
      char c = nameBuffer[0];
      int namePtr = 1, last_colon = -1;
      if(c < 0xD800 || c >= 0xE000){
         if(!Chr.is10NS(c))
            throwInvNChr(c);
      }else{
         if(nameLen == 1)
            throwInvalidSurrogate(c);
         chkSurrgNameCh(c, nameBuffer[1]);
         ++namePtr;
      }
      for(; namePtr < nameLen; ++namePtr)
         if((c = nameBuffer[namePtr]) < 0xD800 || c >= 0xE000){
            if(c == ':'){
               if(last_colon >= 0)
                  throwInputErr("Multiple colons");
               last_colon = namePtr;
            }else if(!Chr.is10N(c))
               throwInvNChr(c);
         }else{
            if(namePtr + 1 >= nameLen)
               throwInvalidSurrogate(c);
            chkSurrgNameCh(c, nameBuffer[namePtr + 1]);
         }
      return syms.add(nameBuffer, nameLen, hash);
   }

   private final String parseSystemId(char quote) throws XMLStreamException{
      char[] outputBuffer = nameBuf;
      int outPtr = 0;
      final byte[] TYPES = chrTypes.ATT;
      while(true){
         if(inPtr >= end)
            assertMore();
         char c = inBuf[inPtr++];
         if(c == quote)
            return new String(outputBuffer, 0, outPtr);
         switch(TYPES[c]){
            case 2: // WS_CR
               if(inPtr >= end)
                  assertMore();
               if(inBuf[inPtr] == '\n')
                  ++inPtr;
               c = '\n';
            case 3: // WS_LF
               rowOff = inPtr;
               ++currRow;
               break;
            case 1: // INVALID
               throwChr(c);
         }
         if(outPtr >= outputBuffer.length)
            nameBuf = outputBuffer = xpand(outputBuffer);
         outputBuffer[outPtr++] = c;
      }
   }

   private final void chkSurrgNameCh(char chr, char sec) throws XMLStreamException{
      int val = ((chr - 0xD800) << 10) + 0x10000;
      if(chr >= 0xDC00 || (chr = sec) < 0xDC00 || chr >= 0xE000)
         throwInvalidSurrogate(chr);
      if(val > 0x10FFFF)
         throwInvChr(val);
   }

   private final void throwInvalidSurrogate(char ch) throws XMLStreamException{
      throwInputErr(new StrB(28).a("Invalid surrogate char ").apos((int)ch).toString());
   }

   @Override
   final Location getCurLoc(){
      return new LocImpl(impl.pubId, impl.sysId, inPtr - rowOff, currRow, bOrC + inPtr);
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
         do
            if((xx = in.read(inBuf, end, 4096 - end)) < 1){
               if(xx == 0)
                  throwInputErr("Reader returned 0");
               return false;
            }
         while((end += xx) < 3);
         return true;
      }catch(IOException ioe){
         throw new XMLStreamException(ioe);
      }
   }

   @Override
   final void freeBufs(){
      super.freeBufs();
      if(syms.dirty)
         impl.genTab.upd(syms);
      if(inBuf != null){
         impl.setCB3(inBuf);
         inBuf = null;
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
   final int getCol(){ return inPtr - iniRawOff; }
}
