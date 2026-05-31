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
public final class StrB{

   private char[] val;
   private int count;

   public StrB(int capacity){ val = new char[capacity]; }

   final StrB append(String str){
      String lstr;
      if((lstr = str) == null)
         lstr = "null";
      int newCount, len = lstr.length();
      if((newCount = count + len) > val.length)
         Code(newCount);
      lstr.getChars(0, len, val, count);
      count = newCount;
      return this;
   }

   final StrB a(String str){
      int len = str.length();
      str.getChars(0, len, val, count);
      count += len;
      return this;
   }

   final StrB append(char[] str){
      int newCount, len = str.length;
      if((newCount = count + len) > val.length)
         Code(newCount);
      System.arraycopy(str, 0, val, count, len);
      count = newCount;
      return this;
   }

   final StrB append(char[] str, int offset, int len){
      int newCount;
      if((newCount = count + len) > val.length)
         Code(newCount);
      System.arraycopy(str, offset, val, count, len);
      count = newCount;
      return this;
   }

   final StrB append(char ch){
      int newCount;
      if((newCount = count + 1) > val.length)
         Code(newCount);
      val[count++] = ch;
      return this;
   }

   final StrB a(char ch){
      val[count++] = ch;
      return this;
   }

   final StrB apos(int ii){
      int xx, jj = ii & 0x7FFFFFFF, len = jj <= 9 ? 1 : jj <= 99 ? 2 : jj <= 999 ? 3 : jj <= 9999 ? 4 : jj <= 99999 ? 5 : jj <= 999999 ? 6 : jj <= 9999999 ? 7 : jj <= 99999999 ? 8 : jj <= 999999999 ? 9 : 10;
      if((xx = count + len) > val.length)
         Code(xx);
      count = xx;
      char[] lval = val;
      while(len-- > 0){
         lval[--xx] = (char)(0x30 + jj % 10);
         jj /= 10;
      }
      return this;
   }

   private final void Code(int minCapacity){
      int newcapacity;
      if(minCapacity > (newcapacity = (val.length + 1) << 1))
         newcapacity = minCapacity;
      System.arraycopy(val, 0, val = new char[newcapacity], 0, count);
   }

   public final String toString(){ return new String(val, 0, count); }
}
