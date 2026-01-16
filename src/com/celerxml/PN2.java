// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025 - 2026
package com.celerxml;

final class PN2 extends PN{

   private final int q1, q2;

   PN2(String pname, String prefix, String ln, int hash, int q1, int q2){
      super(pname, prefix, ln, hash);
      this.q1 = q1;
      this.q2 = q2;
   }

   @Override
   final PN Code(NsB nsb){
      PN2 newName = new PN2(Code, pfx, ln, hash, q1, q2);
      newName.nsB = nsb;
      return newName;
   }

   @Override
   final boolean equals(int q1, int q2){ return q1 == this.q1 && q2 == this.q2; }

   @Override
   final boolean equals(int[] quads, int qlen){ return qlen == 2 && quads[0] == q1 && quads[1] == q2; }

   @Override
   final boolean eq(int h, int q1, int q2){ return h == hash && q1 == this.q1 && q2 == this.q2; }

   @Override
   final boolean eq(int h, int[] quads, int qlen){ return h == hash && qlen == 2 && quads[0] == q1 && quads[1] == q2; }

   @Override
   final int Code(int idx){ return idx == 0 ? q1 : q2; }

   @Override
   final int size(){ return 2; }
}
