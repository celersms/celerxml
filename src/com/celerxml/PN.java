// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025
package com.celerxml;

import javax.xml.namespace.QName;

class PN{

   final String pfxdName, pfx, ln;
   NsB nsB;
   final int hash;

   PN(String pfxdName, String pfx, String ln, int hash){
      this.pfxdName = pfxdName;
      this.pfx = pfx;
      this.ln = ln;
      this.hash = hash;
   }

   final String getNsUri(){ return nsB == null ? null : nsB.uri; }

   final QName qName(){
      String pfx = this.pfx, uri = nsB == null ? null : nsB.uri;
      return new QName(uri == null ? "" : uri, ln, pfx == null ? "" : pfx);
   }

   final QName qName(NsB defNs){
      String uri, pfx = this.pfx;
      if(pfx == null)
         pfx = "";
      if(nsB != null && (uri = nsB.uri) != null)
         return new QName(uri, ln, pfx);
      return new QName((uri = defNs.uri) == null ? "" : uri, ln, pfx);
   }

   final boolean isBound(){ return nsB == null || nsB.uri != null; }

   final boolean boundEq(PN o){ return o != null && o.ln == ln && o.getNsUri() == getNsUri(); }

   final boolean boundEq(String nsUri, String ln){
      if(!this.ln.equals(ln))
         return false;
      String thisUri = getNsUri();
      return nsUri == null || nsUri.length() == 0 ? thisUri == null : nsUri.equals(thisUri);
   }

   @Override
   public final int hashCode(){ return hash; }

   @Override
   public final boolean equals(Object o){
      if(o == this)
         return true;
      if(!(o instanceof PN))
         return false;
      PN other = (PN)o;
      return other.pfx == pfx && other.ln == ln;
   }

   static final PN construct(String pname, int hash){
      int colonIx = pname.indexOf(':');
      if(colonIx < 0)
         return new PN(pname, null, pname, hash);
      return new PN(pname, pname.substring(0, colonIx).intern(), pname.substring(colonIx + 1).intern(), hash);
   }

   final boolean equalsPN(char[] buffer, int len, int hash){
      if(hash != this.hash)
         return false;
      String pname = pfxdName;
      int plen = pname.length();
      if(len != plen)
         return false;
      for(int i = 0; i < len; ++i)
         if(buffer[i] != pname.charAt(i))
            return false;
      return true;
   }

   PN createBN(NsB nsb){
      PN newName = new PN(pfxdName, pfx, ln, hash);
      newName.nsB = nsb;
      return newName;
   }

   int sizeQ(){ return 0; }

   int getQ(int idx){ return 0; }

   boolean equals(int quad1, int quad2){ return false; }

   boolean equals(int[] quads, int qlen){ return false; }

   boolean hashEq(int h, int quad1, int quad2){ return false; }

   boolean hashEq(int h, int[] quads, int qlen){ return false; }
}
