// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025 - 2026
package com.celerxml;

final class PNn extends PN{

   private final int[] q;
   private final int len;

   PNn(String pname, String prefix, String ln, int hash, int[] q, int len){
      super(pname, prefix, ln, hash);
      this.q = q;
      this.len = len;
   }

   @Override
   final PN Code(NsB nsb){
      PNn newName = new PNn(Code, pfx, ln, hash, q, len);
      newName.nsB = nsb;
      return newName;
   }

   @Override
   final boolean equals(int q1, int q2){ return len < 3 ? (len == 1 ? q[0] == q1 && q2 == 0 : q[0] == q1 && q[1] == q2) : false; }

   @Override
   final boolean equals(int[] q, int len){
      if(len == this.len){
         for(int i = 0; i < len; ++i)
            if(q[i] != this.q[i])
               return false;
         return true;
      }
      return false;
   }

   @Override
   final boolean eq(int h, int q1, int q2){
      return h == hash && len < 3 ? (len == 1 ? q[0] == q1 && q2 == 0 : q[0] == q1 && q[1] == q2) : false;
   }

   @Override
   final boolean eq(int h, int[] q, int len){
      if(h == hash && len == this.len){
         for(int i = 0; i < len; ++i)
            if(q[i] != this.q[i])
               return false;
         return true;
      }
      return false;
   }

   @Override
   final int Code(int idx){ return idx < len ? q[idx] : 0; }

   @Override
   final int size(){ return len; }
}
