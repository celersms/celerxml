// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025
package com.celerxml;

final class PN2 extends PN{

   private final int quad1, quad2;

   PN2(String pname, String prefix, String ln, int hash, int quad1, int quad2){
      super(pname, prefix, ln, hash);
      this.quad1 = quad1;
      this.quad2 = quad2;
   }

   @Override
   final PN createBN(NsB nsb){
      PN2 newName = new PN2(pfxdName, pfx, ln, hash, quad1, quad2);
      newName.nsB = nsb;
      return newName;
   }

   @Override
   final boolean equals(int quad1, int quad2){ return quad1 == this.quad1 && quad2 == this.quad2; }

   @Override
   final boolean equals(int[] quads, int qlen){ return qlen == 2 && quads[0] == quad1 && quads[1] == quad2; }

   @Override
   final boolean hashEq(int h, int quad1, int quad2){ return h == hash && quad1 == this.quad1 && quad2 == this.quad2; }

   @Override
   final boolean hashEq(int h, int[] quads, int qlen){ return h == hash && qlen == 2 && quads[0] == quad1 && quads[1] == quad2; }

   @Override
   final int getQ(int idx){ return idx == 0 ? quad1 : quad2; }

   @Override
   final int sizeQ(){ return 2; }
}
