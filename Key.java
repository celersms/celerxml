// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025 - 2026
package com.celerxml;

final class Key{

   final char[] mCh;
   final int mLen, mHsh;

   Key(char[] buffer, int len){
      mCh = buffer;
      mLen = len;
      if(len <= 8){
         int hash = buffer[0];
         for(int i = 1; i < len; ++i)
            hash = hash * 31 + buffer[i];
         mHsh = hash;
      }else{
         int ix, dist = ix = 2, hash = len ^ buffer[0], end = len - 4;
         while(ix < end){
            hash = hash * 31 + buffer[ix];
            ix += dist++;
         }
         mHsh = (((hash * 31) ^ (buffer[end] << 2) + buffer[end + 1]) * 31) + (buffer[end + 2] << 2) ^ buffer[end + 3];
      }
   }

   Key(char[] buffer, int len, int hashCode){
      mCh = buffer;
      mLen = len;
      mHsh = hashCode;
   }

   public final int hashCode(){ return mHsh; }

   public final boolean equals(Object o){
      if(o == this)
         return true;
      if(o == null)
         return false;
      Key other = (Key)o;
      int len = mLen;
      if(other.mLen != len)
         return false;
      final char[] c1 = mCh, c2 = other.mCh;
      for(int i = 0; i < len; ++i)
         if(c1[i] != c2[i])
            return false;
      return true;
   }
}
