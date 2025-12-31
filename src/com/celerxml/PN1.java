// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025
package com.celerxml;

final class PN1 extends PN{

   private final int quad;

   PN1(String pname, String prefix, String ln, int hash, int quad){
      super(pname, prefix, ln, hash);
      this.quad = quad;
   }

   @Override
   final PN createBN(NsB nsb){
      PN1 newName = new PN1(pfxdName, pfx, ln, hash, quad);
      newName.nsB = nsb;
      return newName;
   }

   @Override
   final boolean equals(int quad1, int quad2){ return quad1 == quad && quad2 == 0; }

   @Override
   final boolean equals(int[] quads, int qlen){ return qlen == 1 && quads[0] == quad; }

   @Override
   final boolean hashEq(int h, int quad1, int quad2){ return h == hash && quad1 == quad && quad2 == 0; }

   @Override
   final boolean hashEq(int h, int[] quads, int qlen){ return h == hash && qlen == 1 && quads[0] == quad; }

   @Override
   final int getQ(int idx){ return idx == 0 ? quad : 0; }

   @Override
   final int sizeQ(){ return 1; }
}
