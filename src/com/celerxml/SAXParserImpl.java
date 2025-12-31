// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025
package com.celerxml;

import java.io.File;
import java.io.Reader;
import java.io.InputStream;
import java.io.FileInputStream;
import java.net.URL;
import javax.xml.parsers.SAXParser;
import javax.xml.stream.XMLStreamException;
import org.xml.sax.Locator;
import org.xml.sax.XMLReader;
import org.xml.sax.Attributes;
import org.xml.sax.DTDHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.ErrorHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.SAXParseException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.ext.Attributes2;
import org.xml.sax.ext.DeclHandler;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.ext.Locator2;
import org.xml.sax.helpers.DefaultHandler;

@SuppressWarnings("deprecation")
final class SAXParserImpl extends SAXParser implements org.xml.sax.Parser, XMLReader, Attributes2, Locator2{

   private final InputFactoryImpl factory;
   private XmlScanner scan;
   private ContentHandler cntH;
   private ErrorHandler errH;
   private DTDHandler dtdH;
   private LexicalHandler lexH;
   private DeclHandler dclH;
   private EntityResolver eRes;
   private int attrc;

   SAXParserImpl(InputFactoryImpl factory){ this.factory = factory; }

   @Override
   public final org.xml.sax.Parser getParser(){ return this; }

   @Override
   public final XMLReader getXMLReader(){ return this; }

   @Override
   public Object getProperty(String name) throws SAXNotRecognizedException{
      switch(stdProp(name)){
         case 0xDB4F95F7: // "declaration-handler"
            return dclH;
         case 0x3698DB70: // "document-xml-version"
            return scan.impl.declVer;
         case 0x175D4BE1: // "lexical-handler"
            return lexH;
         case 0x407743AD: // "dom-node"
         case 0x8DDD0287: // "xml-string"
            return null;
         default:
            throw new SAXNotRecognizedException(new StrB(22 + name.length()).a("Unrecognized property ").a(name).toString());
      }
   }

   @Override
   public final void setProperty(String name, Object value) throws SAXNotRecognizedException{
      switch(stdProp(name)){
         case 0xDB4F95F7: // "declaration-handler"
            dclH = (DeclHandler)value;
            return;
         case 0x3698DB70: // "document-xml-version"
            scan.impl.declVer = value == null ? null : String.valueOf(value);
            return;
         case 0x175D4BE1: // "lexical-handler"
            lexH = (LexicalHandler)value;
         case 0x407743AD: // "dom-node"
         case 0x8DDD0287: // "xml-string"
            return;
         default:
            throw new SAXNotRecognizedException(new StrB(22 + name.length()).a("Unrecognized property ").a(name).toString());
      }
   }

   @Override
   public final void parse(InputSource is, org.xml.sax.HandlerBase hb) throws SAXException{
      if(hb != null){
         if(cntH == null)
            setDocumentHandler(hb);
         if(eRes == null)
            eRes = hb;
         if(errH == null)
            errH = hb;
         if(dtdH == null)
            dtdH = hb;
      }
      parse(is);
   }

   @Override
   public final void parse(InputSource is, DefaultHandler dh) throws SAXException{
      if(dh != null){
         if(cntH == null)
            cntH = dh;
         if(eRes == null)
            eRes = dh;
         if(errH == null)
            errH = dh;
         if(dtdH == null)
            dtdH = dh;
      }
      parse(is);
   }

   @Override
   public final ContentHandler getContentHandler(){ return cntH; }

   @Override
   public final DTDHandler getDTDHandler(){ return dtdH; }

   @Override
   public final EntityResolver getEntityResolver(){ return eRes; }

   @Override
   public final ErrorHandler getErrorHandler(){ return errH; }

   @Override
   public final boolean getFeature(String name) throws SAXNotRecognizedException{ return SAXParserFactoryImpl.fixdFeat(name); }

   @Override
   public final void setContentHandler(ContentHandler cntH){ this.cntH = cntH; }

   @Override
   public final void setDTDHandler(DTDHandler dtdH){ this.dtdH = dtdH; }

   @Override
   public final void setEntityResolver(EntityResolver eRes){ this.eRes = eRes; }

   @Override
   public final void setErrorHandler(ErrorHandler errH){ this.errH = errH; }

   @Override
   public final void setFeature(String name, boolean val){ /* NOOP */ }

   private static final int stdProp(String s){ return s.startsWith("http://xml.org/sax/properties/") ? s.substring(30).hashCode() : 0; }

   @Override
   public final void parse(InputSource input) throws SAXException{
      String str = input.getSystemId();
      InputFactoryImpl impl = new InputFactoryImpl(input.getPublicId(), str, input.getEncoding(), factory, false);
      impl.setF(256, false);
      InputStream is = null;
      Reader r = input.getCharacterStream();
      if(r == null && (is = input.getByteStream()) == null){
         if(str == null)
            throw new SAXException("Bad source: neither char or byte stream, nor system id");
         try{
            URL url;
            int ix = str.indexOf(':', 0);
            if(ix >= 3 && ix <= 8)
               url = new URL(str);
            else{
               String absPath = new File(str).getAbsolutePath();
               char sep = File.separatorChar;
               if(sep != '/')
                  absPath = absPath.replace(sep, '/');
               if((ix = absPath.length()) > 0 && absPath.charAt(0) != '/')
                  absPath = new StrB(1 + ix).a('/').a(absPath).toString();
               url = new URL("file", "", absPath);
            }
            if("file".equals(url.getProtocol()) && ((str = url.getHost()) == null || str.length() == 0))
               is = new FileInputStream(url.getPath());
            else
               is = url.openStream();
         }catch(Exception ex){
            throw new SAXException(ex);
         }
      }
      if(cntH != null){
         cntH.setDocumentLocator(this);
         cntH.startDocument();
      }
      try{
         scan = r != null ? new InputSrc(impl, r).wrap() : new ByteSrc(impl, is).wrap();
         int type;
         while((type = scan.nxtFromProlog(true)) != 1) // START_ELEMENT
            fireAuxEvent(type, false);
         attrc = scan.attrCount;
         scan.startElement(cntH, this);
         int depth = 1;
         while(true)
            if((type = scan.nxtFromTree()) == 1){ // START_ELEMENT
               attrc = scan.attrCount;
               scan.startElement(cntH, this);
               ++depth;
            }else if(type == 2){ // END_ELEMENT
               scan.endElement(cntH);
               if(--depth < 1)
                  break;
            }else if(type == 4) // CHARACTERS
               scan.chrEvents(cntH);
            else
               fireAuxEvent(type, true);
         while((type = scan.nxtFromProlog(false)) != -1) // EOI
            if(type != 6) // SPACE
               fireAuxEvent(type, false);
      }catch(XMLStreamException ex){
         SAXParseException se = new SAXParseException(ex.getMessage(), (Locator)this, ex);
         if(errH != null)
            errH.fatalError(se);
         throw se;
      }finally{
         if(cntH != null)
            cntH.endDocument();
         if(scan != null)
            try{
               scan.free();
            }catch(Exception ex){ /* NOOP */ }
         if(r != null)
            try{
               r.close();
            }catch(Exception ex){ /* NOOP */ }
         if(is != null)
            try{
               is.close();
            }catch(Exception ex){ /* NOOP */ }
      }
   }

   @Override
   public final void parse(String systemId) throws SAXException{ parse(new InputSource(systemId)); }

   private final void fireAuxEvent(int type, boolean inTree) throws SAXException, XMLStreamException{
      switch(type){
         case 5:  // COMMENT
            scan.commentEvent(lexH);
            break;
         case 12: // CDATA
            if(lexH != null){
               lexH.startCDATA();
               scan.chrEvents(cntH);
               lexH.endCDATA();
            }else
               scan.chrEvents(cntH);
            break;
         case 11: // DTD
            if(lexH != null){
               lexH.startDTD(scan.tokName.pfxdName, scan.dtdPubId, scan.dtdSysId);
               lexH.endDTD();
            }
            break;
         case 3:  // PROCESSING_INSTRUCTION
            scan.PIEvent(cntH);
            break;
         case 6:  // SPACE
            if(inTree)
               scan.spaceEvents(cntH);
            break;
         default:
            if(type == -1){ // EOI
               SAXParseException se = new SAXParseException(inTree ? "Unexpected EOI in tree" : "Unexpected EOI in prolog", (Locator)this);
               if(errH != null)
                  errH.fatalError(se);
               throw se;
            }
            throw new RuntimeException(new StrB(26).a("Unexpected type ").apos(type).toString());
      }
   }

   @Override
   public final void setDocumentHandler(org.xml.sax.DocumentHandler h){ cntH = new DocHandlerW(h); }

   @Override
   public final void setLocale(java.util.Locale locale){ /* NOOP */ }

   @Override
   public final int getIndex(String qName){ return scan.findIdx(null, qName); }

   @Override
   public final int getIndex(String uri, String lName){ return scan.findIdx(uri, lName); }

   @Override
   public final int getLength(){ return attrc; }

   @Override
   public final String getLocalName(int idx){ return idx < 0 || idx >= attrc ? null : scan.names[idx].ln; }

   @Override
   public final String getQName(int idx){ return idx < 0 || idx >= attrc ? null : scan.names[idx].pfxdName; }

   @Override
   public final String getType(int idx){ return idx < 0 || idx >= attrc ? null : "CDATA"; }

   @Override
   public final String getType(String qName){
      int ix = getIndex(qName);
      return ix < 0 ? null : "CDATA";
   }

   @Override
   public final String getType(String uri, String lName){
      int ix = getIndex(uri, lName);
      return ix < 0 ? null : "CDATA";
   }

   @Override
   public final String getURI(int idx){
      if(idx < 0 || idx >= attrc)
         return null;
      String uri = scan.names[idx].getNsUri();
      return uri == null ? "" : uri;
   }

   @Override
   public final String getValue(int idx){ return idx < 0 || idx >= attrc ? null : scan.getV(idx); }

   @Override
   public final String getValue(String qName){ return scan.getV(null, qName); }

   @Override
   public final String getValue(String uri, String lName){ return scan.getV(uri, lName); }

   @Override
   public final boolean isDeclared(int idx){ return false; }

   @Override
   public final boolean isDeclared(String qName){ return false; }

   @Override
   public final boolean isDeclared(String uri, String lName){ return false; }

   @Override
   public final boolean isSpecified(int idx){ return true; }

   @Override
   public final boolean isSpecified(String qName){ return true; }

   @Override
   public final boolean isSpecified(String uri, String lName){ return true; }

   @Override
   public final int getColumnNumber(){ return scan.getCol(); }

   @Override
   public final int getLineNumber(){ return scan.currRow + 1; }

   @Override
   public final String getPublicId(){ return scan.impl.pubId; }

   @Override
   public final String getSystemId(){ return scan.impl.sysId; }

   @Override
   public final String getEncoding(){
      InputFactoryImpl impl = scan.impl;
      String enc = impl.enc;
      if(enc == null && (enc = impl.declEnc) == null)
         enc = impl.extEnc;
      return enc;
   }

   @Override
   public final String getXMLVersion(){ return scan.impl.declVer; }

   @Override
   public boolean isNamespaceAware(){ return true; }

   @Override
   public boolean isValidating(){ return false; }

   final static class DocHandlerW implements ContentHandler, org.xml.sax.AttributeList{

      private final org.xml.sax.DocumentHandler docH;
      private Attributes attrs;

      DocHandlerW(org.xml.sax.DocumentHandler docH){ this.docH = docH; }

      @Override
      public final void characters(char[] ch, int start, int len) throws SAXException{ docH.characters(ch, start, len); }

      @Override
      public final void startDocument() throws SAXException{ docH.startDocument(); }

      @Override
      public final void endDocument() throws SAXException{ docH.endDocument(); }

      @Override
      public final void endElement(String uri, String lName, String qName) throws SAXException{ docH.endElement(qName == null ? lName : qName); }

      @Override
      public final void ignorableWhitespace(char[] ch, int start, int len) throws SAXException{ docH.ignorableWhitespace(ch, start, len); }

      @Override
      public final void processingInstruction(String tgt, String data) throws SAXException{ docH.processingInstruction(tgt, data); }

      @Override
      public final void setDocumentLocator(Locator loc){ docH.setDocumentLocator(loc); }

      @Override
      public final void startElement(String uri, String lName, String qName, Attributes attrs) throws SAXException{
         this.attrs = attrs;
         docH.startElement(qName == null ? lName : qName, this);
      }

      @Override
      public final void skippedEntity(String n){ /* NOOP */ }

      @Override
      public final void startPrefixMapping(String pfx, String uri){ /* NOOP */ }

      @Override
      public final void endPrefixMapping(String pfx){ /* NOOP */ }

      @Override
      public final int getLength(){ return attrs.getLength(); }

      @Override
      public final String getName(int i){
         String n = attrs.getQName(i);
         return n == null ? attrs.getLocalName(i) : n;
      }

      @Override
      public final String getType(int i){ return attrs.getType(i); }

      @Override
      public final String getType(String name){ return attrs.getType(name); }

      @Override
      public final String getValue(int i){ return attrs.getValue(i); }

      @Override
      public final String getValue(String name){ return attrs.getValue(name); }
   }
}
