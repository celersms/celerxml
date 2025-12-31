// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025
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
import javax.xml.stream.EventFilter;
import javax.xml.stream.StreamFilter;
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
   private BytePN utf8Tab, lat1Tab, asciiTab;
   private LinkedHashMap mURIs;
   private XMLEventAllocator alloc;
   private ShBuf recycler;

   final String pubId, sysId, extEnc;
   ChrPN genTab;
   String enc, declVer, declEnc;
   XMLReporter rep;
   XMLResolver res;
   byte bStand;
   final static HashMap sProps;
   final static ThreadLocal softRef;

   static{
       softRef = new ThreadLocal();
       HashMap pp = sProps = new HashMap();
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
      this.utf8Tab = impl.utf8Tab;
      this.lat1Tab = impl.lat1Tab;
      this.asciiTab = impl.asciiTab;
      this.genTab = impl.genTab;
      this.rep = impl.rep;
      this.res = impl.res;
      this.mURIs = impl.mURIs;
      SoftReference ref = (SoftReference)softRef.get();
      if(ref != null)
         recycler = (ShBuf)ref.get();
      if(forceAutoClose)
         setF(8192, true);
   }

   @Override
   public final XMLStreamReader createXMLStreamReader(InputStream in) throws XMLStreamException{ return createXMLStreamReader(in, null); }

   @Override
   public final XMLStreamReader createXMLStreamReader(InputStream in, String enc) throws XMLStreamException{
      return new ReaderImpl(new ByteSrc(new InputFactoryImpl(null, null, enc, this, false), in).wrap());
   }

   @Override
   public final XMLStreamReader createXMLStreamReader(Reader reader) throws XMLStreamException{ return createXMLStreamReader(null, reader); }

   @Override
   public final XMLStreamReader createXMLStreamReader(String systemId, Reader reader) throws XMLStreamException{
      return new ReaderImpl(new InputSrc(new InputFactoryImpl(null, systemId, null, this, false), reader).wrap());
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
         return new ReaderImpl(new ByteSrc(new InputFactoryImpl(pubId, sysId, encoding, this, false), in).wrap());
      if(r != null)
         return new ReaderImpl(new InputSrc(new InputFactoryImpl(pubId, sysId, encoding, this, false), r).wrap());
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
         return new ReaderImpl(new ByteSrc(new InputFactoryImpl(pubId, sysId, encoding, this, true), in).wrap());
      }catch(Exception ex){
         throw new XMLStreamException(ex);
      }
   }

   @Override
   public final XMLStreamReader createXMLStreamReader(String systemId, InputStream in) throws XMLStreamException{
      return new ReaderImpl(new ByteSrc(new InputFactoryImpl(null, systemId, null, this, false), in).wrap());
   }

   @Override
   public final Object getProperty(String name){ return getProperty(name, true); }

   @Override
   public final void setProperty(String name, Object value){
      Object ob = sProps.get(name);
      if(ob == null || ob instanceof Boolean)
         return;
      if(!(ob instanceof Integer))
         throw new RuntimeException("Internal error");
      setF(((Integer)ob).intValue(), ((Boolean)value).booleanValue());
   }

   @Override
   public final XMLEventAllocator getEventAllocator(){ return alloc; }
    
   @Override
   public final XMLReporter getXMLReporter(){ return rep; }

   @Override
   public final XMLResolver getXMLResolver(){ return res; }

   @Override
   public final boolean isPropertySupported(String name){ return sProps.containsKey(name); }

   @Override
   public final void setEventAllocator(XMLEventAllocator alloc){ this.alloc = alloc; }

   @Override
   public final void setXMLReporter(XMLReporter rep){ this.rep = rep; }

   @Override
   public final void setXMLResolver(XMLResolver res){ this.res = res; }

   final void setXmlDeclInfo(int ver, String xmlDeclEnc, String standalone){
      if(ver == 0x100)
         declVer = InputSrc.V10;
      else if(ver == 0x110)
         declVer = InputSrc.V11;
      else
         declVer = null;
      declEnc = xmlDeclEnc;
      bStand = standalone == InputSrc.YES ? 1 : standalone == InputSrc.NO ? (byte)2 : 0;
   }

   final Object getProperty(String name, boolean isMandatory){
      Object ob = sProps.get(name);
      if(ob == null){
         if(sProps.containsKey(name))
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
      return getF(((Integer)ob).intValue());
   }

   final boolean getF(int mask){ return (flags & mask) != 0; }

   final void setF(int mask, boolean state){
      if(state)
         flags |= mask;
      else
         flags &= ~mask;
   }

   final boolean doTxt(){ return getF(2) || !getF(2048); }

   @SuppressWarnings("unchecked")
   synchronized final String cacheURI(char[] buf, int len){
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

   final char[] getCB1(){
      if(recycler != null){
         char[] result = recycler.getCB1();
         if(result != null)
            return result;
      }
      return new char[60];
   }

   final void setCB1(char[] buf){
      if(recycler == null)
         recycler = getShBuf();
      recycler.cb1 = buf;
   }

   final char[] getCB2(){
      if(recycler != null){
         char[] result = recycler.getCB2();
         if(result != null)
            return result;
      }
      return new char[500];
   }

   final void setCB2(char[] buf){
      if(recycler == null)
         recycler = getShBuf();
      recycler.cb2 = buf;
   }

   final char[] getCB3(){
      if(recycler != null){
         char[] result = recycler.getCB3();
         if(result != null)
            return result;
      }
      return new char[4096];
   }

   final void setCB3(char[] buf){
      if(recycler == null)
         recycler = getShBuf();
      recycler.cb3 = buf;
   }

   final byte[] getBB(){
      if(recycler != null){
         byte[] result = recycler.getBB();
         if(result != null)
            return result;
      }
      return new byte[4096];
   }

   final void setBB(byte[] buf){
      if(recycler == null)
         recycler = getShBuf();
      recycler.bb = buf;
   }

   private final ShBuf getShBuf(){
      ShBuf recycler = new ShBuf();
      softRef.set(new SoftReference(recycler));
      return recycler;
   }

   final BytePN getBBSyms(){
      String enc;
      BytePN tab;
      if((enc = this.enc) == InputSrc.UTF8){
         if(utf8Tab == null)
            utf8Tab = new BytePN();
         tab = utf8Tab;
      }else if(enc == InputSrc.LATN1){
         if(lat1Tab == null)
            lat1Tab = new BytePN();
         tab = lat1Tab;
      }else if(enc == InputSrc.ASCII){
         if(asciiTab == null)
            asciiTab = new BytePN();
         tab = asciiTab;
      }else
         throw new Error(new StrB(28).a("Unknown encoding ").append(enc).toString());
      return new BytePN(tab);
   }

   final void updBBSyms(BytePN sym){
      if(enc == InputSrc.UTF8)
         utf8Tab.upd(sym);
      else if(enc == InputSrc.LATN1)
         lat1Tab.upd(sym);
      else if(enc == InputSrc.ASCII)
         asciiTab.upd(sym);
      else
         throw new Error(new StrB(28).a("Unknown encoding ").append(enc).toString());
   }

   final Chr getChr(){
      if(enc == InputSrc.UTF8)
         return Chr.getUtf8();
      if(enc == InputSrc.LATN1)
         return Chr.getLat1();
      if(enc == InputSrc.ASCII)
         return Chr.getAscii();
      throw new Error(new StrB(28).a("Unknown encoding ").append(enc).toString());
   }

   @Override
   public final XMLEventReader createFilteredReader(XMLEventReader reader, EventFilter filter){ return null; }

   @Override
   public final XMLStreamReader createFilteredReader(XMLStreamReader reader, StreamFilter filter){ return null; }

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
