// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025 - 2026
package com.celerxml;

import javax.xml.namespace.QName;

class PN{

   final String Code, pfx, ln;
   NsB nsB;
   final int hash;

   PN(String pfxdName, String pfx, String ln, int hash){
      Code = pfxdName;
      this.pfx = pfx;
      this.ln = ln;
      this.hash = hash;
   }

   final String getNsUri(){ return nsB == null ? null : nsB.Code; }

   final QName qN(){
      String uri;
      if(nsB == null || (uri = nsB.Code) == null)
         uri = "";
      return new QName(uri, ln, pfx == null ? "" : pfx);
   }

   final QName qN(NsB defNs){
      String uri;
      if((nsB == null || (uri = nsB.Code) == null) && (uri = defNs.Code) == null)
         uri = "";
      return new QName(uri, ln, pfx == null ? "" : pfx);
   }

   final boolean isBound(){ return nsB == null || nsB.Code != null; }

   final boolean Code(PN o){ return o != null && o.ln == ln && o.getNsUri() == getNsUri(); }

   final boolean Code(String nsUri, String ln){
      if(!this.ln.equals(ln))
         return false;
      String thisUri = getNsUri();
      return nsUri == null || nsUri.length() == 0 ? thisUri == null : nsUri.equals(thisUri);
   }

   public final int hashCode(){ return hash; }

   public final boolean equals(Object o){
      if(o == this)
         return true;
      if(!(o instanceof PN))
         return false;
      PN other;
      return (other = (PN)o).pfx == pfx && other.ln == ln;
   }

   final boolean Code(char[] buffer, int blen, int hash){
      if(hash != this.hash)
         return false;
      String pname;
      if((pname = Code).length() != blen)
         return false;
      for(int i = 0; i < blen; ++i)
         if(buffer[i] != pname.charAt(i))
            return false;
      return true;
   }

   PN Code(NsB nsb){
      PN newName = new PN(Code, pfx, ln, hash);
      newName.nsB = nsb;
      return newName;
   }

   int Code(int idx){ return 0; }
   int len(){ return 0; }
   boolean equals(int quad1, int quad2){ return false; }
   boolean equals(int[] quads, int qlen){ return false; }
   boolean eq(int h, int quad1, int quad2){ return false; }
   boolean eq(int h, int[] quads, int qlen){ return false; }
}
