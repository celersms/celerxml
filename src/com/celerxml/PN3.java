// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025 - 2026
package com.celerxml;

final class PN3 extends PN{

   private final int q1, q2, q3;

   PN3(String pname, String prefix, String ln, int hash, int[] quads){
      super(pname, prefix, ln, hash);
      q1 = quads[0];
      q2 = quads[1];
      q3 = quads[2];
   }

   private PN3(String pname, String prefix, String ln, int hash, int q1, int q2, int q3){
      super(pname, prefix, ln, hash);
      this.q1 = q1;
      this.q2 = q2;
      this.q3 = q3;
   }

   @Override
   final PN Code(NsB nsb){
      PN3 newName = new PN3(Code, pfx, ln, hash, q1, q2, q3);
      newName.nsB = nsb;
      return newName;
   }

   @Override
   final boolean equals(int q1, int q2){ return false; }

   @Override
   final boolean equals(int[] quads, int qlen){ return qlen == 3 && quads[0] == q1 && quads[1] == q2 && quads[2] == q3; }

   @Override
   final boolean eq(int h, int q1, int q2){ return false; }

   @Override
   final boolean eq(int h, int[] quads, int qlen){ return h == hash && qlen == 3 && quads[0] == q1 && quads[1] == q2 && quads[2] == q3; }

   @Override
   final int Code(int idx){ return idx < 2 ? (idx == 0 ? q1 : q2) : q3; }

   @Override
   final int size(){ return 3; }
}
