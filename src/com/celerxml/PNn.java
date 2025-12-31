// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025
package com.celerxml;

final class PNn extends PN{

   private final int[] quads;
   private final int qLen;

   PNn(String pname, String prefix, String ln, int hash, int[] quads, int qLen){
      super(pname, prefix, ln, hash);
      this.quads = quads;
      this.qLen = qLen;
   }

   @Override
   final PN createBN(NsB nsb){
      PNn newName = new PNn(pfxdName, pfx, ln, hash, quads, qLen);
      newName.nsB = nsb;
      return newName;
   }

   @Override
   final boolean equals(int quad1, int quad2){ return qLen < 3 ? (qLen == 1 ? quads[0] == quad1 && quad2 == 0 : quads[0] == quad1 && quads[1] == quad2) : false; }

   @Override
   final boolean equals(int[] quads, int qLen){
      if(qLen == this.qLen){
         for(int i = 0; i < qLen; ++i)
            if(quads[i] != this.quads[i])
               return false;
         return true;
      }
      return false;
   }

   @Override
   final boolean hashEq(int h, int quad1, int quad2){
      return h == hash && qLen < 3 ? (qLen == 1 ? quads[0] == quad1 && quad2 == 0 : quads[0] == quad1 && quads[1] == quad2) : false;
   }

   @Override
   final boolean hashEq(int h, int[] quads, int qLen){
      if(h == hash && qLen == this.qLen){
         for(int i = 0; i < qLen; ++i)
            if(quads[i] != this.quads[i])
               return false;
         return true;
      }
      return false;
   }

   @Override
   final int getQ(int idx){ return idx < qLen ? quads[idx] : 0; }

   @Override
   final int sizeQ(){ return qLen; }
}
