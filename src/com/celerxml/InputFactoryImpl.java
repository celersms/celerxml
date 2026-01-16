// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025 - 2026
package com.celerxml;

import java.lang.ref.SoftReference;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.Reader;
import java.io.File;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.net.URL;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLReporter;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.util.XMLEventAllocator;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.Source;
import org.xml.sax.InputSource;

@SuppressWarnings("unchecked")
public final class InputFactoryImpl extends XMLInputFactory{

   private int flags;
   private bPN utf8T, lat1T, asciiT;
   private LinkedHashMap mURIs;
   private XMLEventAllocator alloc;
   private ShBuf rcclr;

   final String pubId, sysId, extEnc;
   cPN genTab;
   String enc, declVer, declEnc;
   XMLReporter rep;
   XMLResolver res;
   byte bStand;
   final static HashMap Code;
   final static ThreadLocal softR;

   static{
       softR = new ThreadLocal();
       HashMap pp = Code = new HashMap();
       pp.put(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
       pp.put(XMLInputFactory.IS_VALIDATING, Integer.valueOf(8));                   // DTD_VALIDATING
       pp.put(XMLInputFactory.IS_COALESCING, Integer.valueOf(2));                   // COALESCING
       pp.put(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Integer.valueOf(16)); // EXPAND_ENTITIES
       pp.put(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
       pp.put(XMLInputFactory.SUPPORT_DTD, Integer.valueOf(4));                     // DTD_AWARE
       pp.put(XMLInputFactory.REPORTER, null);
       pp.put(XMLInputFactory.RESOLVER, null);
       pp.put(XMLInputFactory.ALLOCATOR, null);
   }

   public InputFactoryImpl(){
      flags = 7957;
      pubId = sysId = extEnc = null;
   }

   InputFactoryImpl(String pubId, String sysId, String extEnc, InputFactoryImpl impl, boolean forceAutoClose){
      this.pubId = pubId;
      this.sysId = sysId;
      this.extEnc = extEnc;
      this.flags = impl.flags;
      this.utf8T = impl.utf8T;
      this.lat1T = impl.lat1T;
      this.asciiT = impl.asciiT;
      this.genTab = impl.genTab;
      this.rep = impl.rep;
      this.res = impl.res;
      this.mURIs = impl.mURIs;
      SoftReference ref = (SoftReference)softR.get();
      if(ref != null)
         rcclr = (ShBuf)ref.get();
      if(forceAutoClose)
         Code(8192, true);
   }

   @Override
   public final XMLStreamReader createXMLStreamReader(InputStream in) throws XMLStreamException{ return createXMLStreamReader(in, null); }

   @Override
   public final XMLStreamReader createXMLStreamReader(InputStream in, String enc) throws XMLStreamException{
      return new ReaderImpl(new bSrc(new InputFactoryImpl(null, null, enc, this, false), in).w());
   }

   @Override
   public final XMLStreamReader createXMLStreamReader(Reader reader) throws XMLStreamException{ return createXMLStreamReader(null, reader); }

   @Override
   public final XMLStreamReader createXMLStreamReader(String systemId, Reader reader) throws XMLStreamException{
      return new ReaderImpl(new InSrc(new InputFactoryImpl(null, systemId, null, this, false), reader).w());
   }

   @Override
   public final XMLStreamReader createXMLStreamReader(Source src) throws XMLStreamException{
      Reader r = null;
      InputStream in = null;
      String pubId = null, sysId = null, encoding = null;
      if(src instanceof StreamSource){
         StreamSource ss = (StreamSource)src;
         sysId = ss.getSystemId();
         pubId = ss.getPublicId();
         if((in = ss.getInputStream()) == null)
            r = ss.getReader();
      }else if(src instanceof SAXSource){
         SAXSource ss = (SAXSource)src;
         sysId = ss.getSystemId();
         InputSource isrc = ss.getInputSource();
         if(isrc != null){
            sysId = isrc.getSystemId();
            pubId = isrc.getPublicId();
            encoding = isrc.getEncoding();
            if((in = isrc.getByteStream()) == null)
               r = isrc.getCharacterStream();
         }
      }else
         throw new IllegalArgumentException("Unrecognized source");
      if(in != null)
         return new ReaderImpl(new bSrc(new InputFactoryImpl(pubId, sysId, encoding, this, false), in).w());
      if(r != null)
         return new ReaderImpl(new InSrc(new InputFactoryImpl(pubId, sysId, encoding, this, false), r).w());
      if(sysId == null || sysId.length() == 0)
         throw new XMLStreamException("Can't create reader");
      try{
         URL url;
         int ix = sysId.indexOf(':', 0);
         if(ix >= 3 && ix <= 8)
            url = new URL(sysId);
         else{
            String absId = new File(sysId).getAbsolutePath();
            char sep = File.separatorChar;
            if(sep != '/')
               absId = absId.replace(sep, '/');
            if((ix = absId.length()) > 0 && absId.charAt(0) != '/')
               absId = new StrB(1 + ix).a('/').a(absId).toString();
            url = new URL("file", "", absId);
         }
         if("file".equals(url.getProtocol())){
            String host = url.getHost();
            if(host == null || host.length() == 0)
               in = new FileInputStream(url.getPath());
         }
         if(in == null)
            in = url.openStream();
         return new ReaderImpl(new bSrc(new InputFactoryImpl(pubId, sysId, encoding, this, true), in).w());
      }catch(Exception ex){
         throw new XMLStreamException(ex);
      }
   }

   @Override
   public final XMLStreamReader createXMLStreamReader(String systemId, InputStream in) throws XMLStreamException{
      return new ReaderImpl(new bSrc(new InputFactoryImpl(null, systemId, null, this, false), in).w());
   }

   @Override
   public final Object getProperty(String name){ return getProperty(name, true); }

   @Override
   public final void setProperty(String name, Object value){
      Object ob = Code.get(name);
      if(ob == null || ob instanceof Boolean)
         return;
      if(!(ob instanceof Integer))
         throw new RuntimeException("Internal error");
      Code(((Integer)ob).intValue(), ((Boolean)value).booleanValue());
   }

   @Override
   public final XMLEventAllocator getEventAllocator(){ return alloc; }
    
   @Override
   public final XMLReporter getXMLReporter(){ return rep; }

   @Override
   public final XMLResolver getXMLResolver(){ return res; }

   @Override
   public final boolean isPropertySupported(String name){ return Code.containsKey(name); }

   @Override
   public final void setEventAllocator(XMLEventAllocator alloc){ this.alloc = alloc; }

   @Override
   public final void setXMLReporter(XMLReporter rep){ this.rep = rep; }

   @Override
   public final void setXMLResolver(XMLResolver res){ this.res = res; }

   final Object getProperty(String name, boolean isMandatory){
      Object ob = Code.get(name);
      if(ob == null){
         if(Code.containsKey(name))
            return null;
         if("http://java.sun.com/xml/stream/properties/implementation-name".equals(name))
            return "celerxml";
         if(isMandatory)
            throw new IllegalArgumentException("Unrecognized property");
         return null;
      }
      if(ob instanceof Boolean)
         return ((Boolean)ob).booleanValue();
      if(!(ob instanceof Integer))
         throw new RuntimeException("Unrecognized property type");
      return Code(((Integer)ob).intValue());
   }

   final boolean doTxt(){ return Code(2) || !Code(2048); }

   final char[] getCB1(){
      if(rcclr != null){
         char[] result = rcclr.getCB1();
         if(result != null)
            return result;
      }
      return new char[60];
   }

   final void setCB1(char[] buf){
      if(rcclr == null)
         rcclr = getSBuf();
      rcclr.cb1 = buf;
   }

   final char[] getCB2(){
      if(rcclr != null){
         char[] result = rcclr.getCB2();
         if(result != null)
            return result;
      }
      return new char[500];
   }

   final void setCB2(char[] buf){
      if(rcclr == null)
         rcclr = getSBuf();
      rcclr.cb2 = buf;
   }

   final char[] getCB3(){
      if(rcclr != null){
         char[] result = rcclr.getCB3();
         if(result != null)
            return result;
      }
      return new char[4096];
   }

   final void setCB3(char[] buf){
      if(rcclr == null)
         rcclr = getSBuf();
      rcclr.cb3 = buf;
   }

   final byte[] getBB(){
      if(rcclr != null){
         byte[] result = rcclr.getBB();
         if(result != null)
            return result;
      }
      return new byte[4096];
   }

   final void setBB(byte[] buf){
      if(rcclr == null)
         rcclr = getSBuf();
      rcclr.bb = buf;
   }

   private final ShBuf getSBuf(){
      ShBuf rcclr = new ShBuf();
      softR.set(new SoftReference(rcclr));
      return rcclr;
   }

   final bPN getSyms(){
      String enc;
      bPN tab;
      if((enc = this.enc) == InSrc.UTF8){
         if(utf8T == null)
            utf8T = new bPN();
         tab = utf8T;
      }else if(enc == InSrc.LAT1){
         if(lat1T == null)
            lat1T = new bPN();
         tab = lat1T;
      }else if(enc == InSrc.ASCII){
         if(asciiT == null)
            asciiT = new bPN();
         tab = asciiT;
      }else
         throw new Error(new StrB(28).a("Unknown encoding ").append(enc).toString());
      return new bPN(tab);
   }

   final boolean Code(int mask){ return (flags & mask) != 0; }

   final void Code(int mask, boolean state){
      if(state)
         flags |= mask;
      else
         flags &= ~mask;
   }

   final void Code(int ver, String xmlDeclEnc, String standalone){
      if(ver == 0x100)
         declVer = InSrc.V10;
      else if(ver == 0x110)
         declVer = InSrc.V11;
      else
         declVer = null;
      declEnc = xmlDeclEnc;
      bStand = standalone == InSrc.YES ? 1 : standalone == InSrc.NO ? (byte)2 : 0;
   }

   @SuppressWarnings("unchecked")
   synchronized final String Code(char[] buf, int len){
      String res;
      Key key = new Key(buf, len);
      if(mURIs == null)
         mURIs = new LRUMap();
      else if((res = (String)mURIs.get(key)) != null)
         return res;
      res = new String(buf, 0, len).intern();
      char[] newBuf = new char[len];
      System.arraycopy(key.mChars, 0, newBuf, 0, len);
      mURIs.put(new Key(newBuf, len, key.mHash), res);
      return res;
   }

   final void Code(bPN sym){
      if(enc == InSrc.UTF8)
         utf8T.Code(sym);
      else if(enc == InSrc.LAT1)
         lat1T.Code(sym);
      else if(enc == InSrc.ASCII)
         asciiT.Code(sym);
      else
         throw new Error(new StrB(28).a("Unknown encoding ").append(enc).toString());
   }

   final Chr Code(){
      if(enc == InSrc.UTF8)
         return Chr.sUtf8;
      if(enc == InSrc.LAT1)
         return Chr.getLat1();
      if(enc == InSrc.ASCII)
         return Chr.getAscii();
      throw new Error(new StrB(28).a("Unknown encoding ").append(enc).toString());
   }

   @Override
   public final XMLEventReader createFilteredReader(XMLEventReader reader, javax.xml.stream.EventFilter filter){ return null; }

   @Override
   public final XMLStreamReader createFilteredReader(XMLStreamReader reader, javax.xml.stream.StreamFilter filter){ return null; }

   @Override
   public final XMLEventReader createXMLEventReader(InputStream in){ return null; }

   @Override
   public final XMLEventReader createXMLEventReader(InputStream in, String enc){ return null; }

   @Override
   public final XMLEventReader createXMLEventReader(Reader reader){ return null; }

   @Override
   public final XMLEventReader createXMLEventReader(Source source){ return null; }

   @Override
   public final XMLEventReader createXMLEventReader(String systemId, InputStream in){ return null; }

   @Override
   public final XMLEventReader createXMLEventReader(String systemId, Reader r){ return null; }

   @Override
   public final XMLEventReader createXMLEventReader(XMLStreamReader sr){ return null; }

   final static class LRUMap extends LinkedHashMap{

      LRUMap(){ super(64, 0.7f, true); }

      @Override
      protected final boolean removeEldestEntry(Map.Entry e){ return size() >= 716; }
   }

   final static class Key{

      final char[] mChars;
      final int mLength, mHash;

      Key(char[] buffer, int len){
         mChars = buffer;
         mLength = len;
         if(len <= 8){
            int hash = buffer[0];
            for(int i = 1; i < len; ++i)
               hash = (hash * 31) + buffer[i];
            mHash = hash;
         }else{
            int ix, dist, hash = len ^ buffer[0], end = len - 4;
            ix = dist = 2;
            while(ix < end){
               hash = (hash * 31) + buffer[ix];
               ix += dist;
               ++dist;
            }
            mHash = (((hash * 31) ^ (buffer[end] << 2) + buffer[end + 1]) * 31) + (buffer[end + 2] << 2) ^ buffer[end + 3];
         }
      }

      Key(char[] buffer, int len, int hashCode){
         mChars = buffer;
         mLength = len;
         mHash = hashCode;
      }

      @Override
      public final int hashCode(){ return mHash; }

      @Override
      public final boolean equals(Object o){
         if(o == this)
            return true;
         if(o == null)
            return false;
         Key other = (Key)o;
         int len = mLength;
         if(other.mLength != len)
            return false;
         char[] c1 = mChars, c2 = other.mChars;
         for(int i = 0; i < len; ++i)
            if(c1[i] != c2[i])
               return false;
         return true;
      }
   }
}
