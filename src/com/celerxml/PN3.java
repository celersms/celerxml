// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025
package com.celerxml;

final class PN3 extends PN{

   private final int quad1, quad2, quad3;

   PN3(String pname, String prefix, String ln, int hash, int[] quads){
      super(pname, prefix, ln, hash);
      quad1 = quads[0];
      quad2 = quads[1];
      quad3 = quads[2];
   }

   private PN3(String pname, String prefix, String ln, int hash, int quad1, int quad2, int quad3){
      super(pname, prefix, ln, hash);
      this.quad1 = quad1;
      this.quad2 = quad2;
      this.quad3 = quad3;
   }

   @Override
   final PN createBN(NsB nsb){
      PN3 newName = new PN3(pfxdName, pfx, ln, hash, quad1, quad2, quad3);
      newName.nsB = nsb;
      return newName;
   }

   @Override
   final boolean equals(int quad1, int quad2){ return false; }

   @Override
   final boolean equals(int[] quads, int qlen){ return qlen == 3 && quads[0] == quad1 && quads[1] == quad2 && quads[2] == quad3; }

   @Override
   final boolean hashEq(int h, int quad1, int quad2){ return false; }

   @Override
   final boolean hashEq(int h, int[] quads, int qlen){ return h == hash && qlen == 3 && quads[0] == quad1 && quads[1] == quad2 && quads[2] == quad3; }

   @Override
   final int getQ(int idx){ return idx < 2 ? (idx == 0 ? quad1 : quad2) : quad3; }

   @Override
   final int sizeQ(){ return 3; }
}
