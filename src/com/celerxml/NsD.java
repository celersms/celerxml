// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025
package com.celerxml;

final class NsD{

   final NsB bind;
   final NsD prevD;
   final int lvl;
   private final String prevURI;

   NsD(NsB bind, String newURI, NsD prevD, int lvl){
      this.bind = bind;
      this.prevD = prevD;
      prevURI = bind.uri;
      bind.uri = newURI;
      this.lvl = lvl;
   }

   final boolean hasPfx(String pfx){ return pfx.equals(bind.pfx); }

   final boolean hasNsURI(String uri){ return uri.equals(bind.uri); }

   final NsD unbind(){
      bind.uri = prevURI;
      return prevD;
   }

   final boolean declared(String prefix, int lvl){
      if(this.lvl >= lvl){
         if(prefix == bind.pfx)
            return true;
         NsD prev = prevD;
         while(prev != null && prev.lvl >= lvl){
            if(prefix == prev.bind.pfx)
               return true;
            prev = prev.prevD;
         }
      }
      return false;
   }

   final int countLvl(int lvl){
      int count = 0;
      NsD prev = this;
      while(prev != null && prev.lvl == lvl){
         ++count;
         prev = prev.prevD;
      }
      return count;
   }
}
