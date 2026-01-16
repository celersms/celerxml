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
public final class StrB implements CharSequence{

   private char[] val;
   private int count;

   public StrB(int capacity){ val = new char[capacity]; }

   public final int length(){ return count; }

   public final void ensureCapacity(int minCapacity){
      if(minCapacity > val.length)
         Code(minCapacity);
   }

   public final StrB append(CharSequence seq){
      if(seq instanceof String)
         return append((String)seq);
      if(seq instanceof StrB){
         StrB sb = (StrB)seq;
         int newCount, len = sb.count;
         if((newCount = count + len) > val.length)
            Code(newCount);
         System.arraycopy(sb.val, 0, val, count, len);
         count = newCount;
      }
      return this;
   }

   public final StrB append(String str){
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

   public final StrB a(String str){
      int len = str.length();
      str.getChars(0, len, val, count);
      count += len;
      return this;
   }

   public final StrB append(char[] str){
      int newCount, len = str.length;
      if((newCount = count + len) > val.length)
         Code(newCount);
      System.arraycopy(str, 0, val, count, len);
      count = newCount;
      return this;
   }

   public final StrB append(char[] str, int offset, int len){
      int newCount;
      if((newCount = count + len) > val.length)
         Code(newCount);
      System.arraycopy(str, offset, val, count, len);
      count = newCount;
      return this;
   }

   public final StrB a(char[] str){
      int len = str.length;
      System.arraycopy(str, 0, val, count, len);
      count += len;
      return this;
   }

   public final StrB append(byte[] str){
      int newCount, ii = 0, jj = count, len = str.length;
      if((newCount = jj + len) > val.length)
         Code(newCount);
      char[] lval = val;
      while(ii < len)
         lval[jj++] = (char)(str[ii++] & 0xFF);
      count = jj;
      return this;
   }

   public final StrB append(char ch){
      int newCount;
      if((newCount = count + 1) > val.length)
         Code(newCount);
      val[count++] = ch;
      return this;
   }

   public final StrB a(char ch){
      val[count++] = ch;
      return this;
   }

   public final StrB append(int ii){
      if(ii == 0x80000000)
         return append("-2147483648");
      int xx, len, jj, sgnlen = 0;
      if((jj = ii) < 0){
         sgnlen = 1;
         jj = -jj;
      }
      len = jj <= 9 ? 1 : jj <= 99 ? 2 : jj <= 999 ? 3 : jj <= 9999 ? 4 : jj <= 99999 ? 5 : jj <= 999999 ? 6 : jj <= 9999999 ? 7 : jj <= 99999999 ? 8 : jj <= 999999999 ? 9 : 10; // NOSONAR
      if((xx = count + sgnlen + len) > val.length)
         Code(xx);
      count = xx;
      char[] lval = val;
      while(len-- > 0){
         lval[--xx] = (char)(0x30 + jj % 10);
         jj /= 10;
      }
      if(sgnlen != 0)
         lval[--xx] = '-';
      return this;
   }

   public final StrB apos(int ii){
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
      char[] newval = new char[newcapacity];
      System.arraycopy(val, 0, newval, 0, count);
      val = newval;
   }

   public final void setLength(int newLength){ count = newLength; }

   public final char charAt(int index){ return val[index]; }

   public final char[] getChars(){ return val; }

   public final void setCharAt(int index, char ch){ val[index] = ch; }

   public final String substring(int start){ return new String(val, start, count - start); }

   public final String substring(int start, int end){ return new String(val, start, end - start); }

   public final String toString(){ return new String(val, 0, count); }

   public final CharSequence subSequence(int start, int end){ return null; }
}
