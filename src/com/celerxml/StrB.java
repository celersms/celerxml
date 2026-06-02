// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025 - 2026
package com.celerxml;

// String builder replacement.
// The "a" methods do the same as "append", but without capacity checks.
// Useful for performance when the maximum size of the text is known in advance.
final class StrB{

   private char[] v;
   private int cap;

   public StrB(int capacity){ v = new char[capacity]; }

   final StrB append(String str){
      String lstr;
      if((lstr = str) == null)
         lstr = "null";
      int newCount, len;
      if((newCount = cap + (len = lstr.length())) > v.length)
         Code(newCount);
      lstr.getChars(0, len, v, cap);
      cap = newCount;
      return this;
   }

   final StrB a(String str){
      int len;
      str.getChars(0, len = str.length(), v, cap);
      cap += len;
      return this;
   }

   final StrB append(char[] str){
      int newCount, len;
      if((newCount = cap + (len = str.length)) > v.length)
         Code(newCount);
      System.arraycopy(str, 0, v, cap, len);
      cap = newCount;
      return this;
   }

   final StrB append(char[] str, int offset, int len){
      int newCount;
      if((newCount = cap + len) > v.length)
         Code(newCount);
      System.arraycopy(str, offset, v, cap, len);
      cap = newCount;
      return this;
   }

   final StrB append(char ch){
      int newCount;
      if((newCount = cap + 1) > v.length)
         Code(newCount);
      v[cap++] = ch;
      return this;
   }

   final StrB a(char ch){
      v[cap++] = ch;
      return this;
   }

   final StrB apos(int ii){
      int xx, jj = ii & 0x7FFFFFFF, len = jj <= 9 ? 1 : jj <= 99 ? 2 : jj <= 999 ? 3 : jj <= 9999 ? 4 : jj <= 99999 ? 5 : jj <= 999999 ? 6 : jj <= 9999999 ? 7 : jj <= 99999999 ? 8 : jj <= 999999999 ? 9 : 10;
      if((xx = cap + len) > v.length)
         Code(xx);
      cap = xx;
      char[] lval = v;
      while(len-- > 0){
         lval[--xx] = (char)(0x30 + jj % 10);
         jj /= 10;
      }
      return this;
   }

   private final void Code(int minCapacity){
      int newcapacity;
      if(minCapacity > (newcapacity = (v.length + 1) << 1))
         newcapacity = minCapacity;
      System.arraycopy(v, 0, v = new char[newcapacity], 0, cap);
   }

   public final String toString(){ return new String(v, 0, cap); }
}
