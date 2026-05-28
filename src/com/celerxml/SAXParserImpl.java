// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025 - 2026
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
import org.xml.sax.DTDHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.ErrorHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.SAXParseException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.ext.DeclHandler;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.DefaultHandler;

@SuppressWarnings("deprecation")
final class SAXParserImpl extends SAXParser implements org.xml.sax.Parser, XMLReader, org.xml.sax.ext.Attributes2, org.xml.sax.ext.Locator2{

   private final InputFactoryImpl Code;
   private XmlScanner scan;
   private ContentHandler cntH;
   private ErrorHandler errH;
   private DTDHandler dtdH;
   private LexicalHandler lexH;
   private DeclHandler dclH;
   private EntityResolver eRes;
   private int attrc;

   SAXParserImpl(InputFactoryImpl factory){ Code = factory; }
   public final org.xml.sax.Parser getParser(){ return this; }
   public final XMLReader getXMLReader(){ return this; }

   public Object getProperty(String name) throws SAXNotRecognizedException{
      switch(Code(name)){
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

   public final void setProperty(String name, Object value) throws SAXNotRecognizedException{
      switch(Code(name)){
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

   public final ContentHandler getContentHandler(){ return cntH; }
   public final DTDHandler getDTDHandler(){ return dtdH; }
   public final EntityResolver getEntityResolver(){ return eRes; }
   public final ErrorHandler getErrorHandler(){ return errH; }
   public final boolean getFeature(String name) throws SAXNotRecognizedException{ return SAXParserFactoryImpl.fix(name); }
   public final void setContentHandler(ContentHandler cntH){ this.cntH = cntH; }
   public final void setDTDHandler(DTDHandler dtdH){ this.dtdH = dtdH; }
   public final void setEntityResolver(EntityResolver eRes){ this.eRes = eRes; }
   public final void setErrorHandler(ErrorHandler errH){ this.errH = errH; }
   public final void setFeature(String name, boolean val){ /* NOOP */ }

   public final void parse(InputSource input) throws SAXException{
      String str;
      InputFactoryImpl impl;
      (impl = new InputFactoryImpl(input.getPublicId(), str = input.getSystemId(), input.getEncoding(), Code, false)).Code(256, false);
      InputStream is = null;
      Reader r;
      if((r = input.getCharacterStream()) == null && (is = input.getByteStream()) == null){
         if(str == null)
            throw new SAXException("Source neither char or byte stream, nor system id");
         try{
            URL url;
            int ix;
            if((ix = str.indexOf(':', 0)) >= 3 && ix <= 8)
               url = new URL(str);
            else{
               String absPath = new File(str).getAbsolutePath();
               char sep;
               if((sep = File.separatorChar) != '/')
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
         final XmlScanner scan = this.scan = (r != null ? new InSrc(impl, r) : new bSrc(impl, is)).w();
         int type;
         while((type = scan.nxtFromProlog(true)) != 1) // START_ELEMENT
            if(type != 6) // SPACE
               Code(type);
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
               scan.chrEvts(cntH);
            else
               Code(type);
         while((type = scan.nxtFromProlog(false)) != -1) // EOI
            if(type != 6) // SPACE
               Code(type);
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

   public final void parse(String systemId) throws SAXException{ parse(new InputSource(systemId)); }

   private final void Code(int type) throws SAXException, XMLStreamException{
      switch(type){
         case 5:  // COMMENT
            scan.commentEvent(lexH);
            return;
         case 12: // CDATA
            if(lexH != null){
               lexH.startCDATA();
               scan.chrEvts(cntH);
               lexH.endCDATA();
            }else
               scan.chrEvts(cntH);
            return;
         case 11: // DTD
            if(lexH != null){
               lexH.startDTD(scan.tokName.Code, scan.dtdPub, scan.dtdSys);
               lexH.endDTD();
            }
            return;
         case 3:  // PROCESSING_INSTRUCTION
            scan.PIEvent(cntH);
            return;
         case 6:  // SPACE
            scan.spaceEvts(cntH);
            return;
      }
      if(type == -1){ // EOI
         SAXParseException se = new SAXParseException("Unexpected EOI", (Locator)this);
         if(errH != null)
            errH.fatalError(se);
         throw se;
      }
      throw new RuntimeException(new StrB(26).a("Unexpected type ").apos(type).toString());
   }

   public final void setDocumentHandler(org.xml.sax.DocumentHandler h){ cntH = new DocH(h); }
   public final void setLocale(java.util.Locale locale){ /* NOOP */ }
   public final int getIndex(String qName){ return scan.findIdx(null, qName); }
   public final int getIndex(String uri, String lName){ return scan.findIdx(uri, lName); }
   public final int getLength(){ return attrc; }
   public final String getLocalName(int idx){ return idx < 0 || idx >= attrc ? null : scan.names[idx].ln; }
   public final String getQName(int idx){ return idx < 0 || idx >= attrc ? null : scan.names[idx].Code; }
   public final String getType(int idx){ return idx < 0 || idx >= attrc ? null : "CDATA"; }
   public final String getType(String qName){ return getIndex(qName) < 0 ? null : "CDATA"; }
   public final String getType(String uri, String lName){ return getIndex(uri, lName) < 0 ? null : "CDATA"; }

   public final String getURI(int idx){
      if(idx < 0 || idx >= attrc)
         return null;
      String uri;
      return (uri = scan.names[idx].getNsUri()) == null ? "" : uri;
   }

   public final String getValue(int idx){ return idx < 0 || idx >= attrc ? null : scan.getV(idx); }
   public final String getValue(String qName){ return scan.getV(null, qName); }
   public final String getValue(String uri, String lName){ return scan.getV(uri, lName); }
   public final boolean isDeclared(int idx){ return false; }
   public final boolean isDeclared(String qName){ return false; }
   public final boolean isDeclared(String uri, String lName){ return false; }
   public final boolean isSpecified(int idx){ return true; }
   public final boolean isSpecified(String qName){ return true; }
   public final boolean isSpecified(String uri, String lName){ return true; }
   public final int getColumnNumber(){ return scan.col(); }
   public final int getLineNumber(){ return scan.currRow + 1; }
   public final String getPublicId(){ return scan.impl.pubId; }
   public final String getSystemId(){ return scan.impl.sysId; }

   public final String getEncoding(){
      InputFactoryImpl impl;
      String enc;
      if((enc = (impl = scan.impl).enc) == null && (enc = impl.declEnc) == null)
         enc = impl.extEnc;
      return enc;
   }

   public final String getXMLVersion(){ return scan.impl.declVer; }
   public boolean isNamespaceAware(){ return true; }
   public boolean isValidating(){ return false; }
   private static final int Code(String s){ return s.startsWith("http://xml.org/sax/properties/") ? s.substring(30).hashCode() : 0; }
}
