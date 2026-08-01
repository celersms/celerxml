// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025 - 2026
package com.celerxml;

import java.io.Reader;
import java.io.InputStream;
import java.io.IOException;

final class Utf32Reader extends Reader{

   private final InputFactoryImpl impl;
   private InputStream mIn;
   private byte[] mBuf;
   private char[] mTmp;
   private int mPtr, mLen;
   private char cSurrgt;
   private final boolean bigEnd;

   Utf32Reader(InputFactoryImpl impl, InputStream mIn, byte[] mBuf, int mPtr, int mLen, boolean bigEnd){
      this.impl = impl;
      this.mIn = mIn;
      this.mBuf = mBuf;
      this.mPtr = mPtr;
      this.mLen = mLen;
      this.bigEnd = bigEnd;
   }

   public final void close() throws IOException{
      InputStream in;
      if((in = mIn) != null){
         mIn = null;
         Code();
         in.close();
      }
   }

   public final int read() throws IOException{
      if(mTmp == null)
         mTmp = new char[1];
      return read(mTmp, 0, 1) < 1 ? -1 : mTmp[0];
   }

   public final int read(char[] cbuf, int start, int len) throws IOException{
      if(mBuf == null)
         return -1;
      if(len < 1)
         return len;
      len += start;
      int yy, outPtr = start;
      if(cSurrgt != 0){
         cbuf[outPtr++] = cSurrgt;
         cSurrgt = 0;
      }else if((yy = mLen - mPtr) < 4){
         if(yy > 0){
            if(mPtr > 0){
               for(int i = 0; i < yy; ++i)
                  mBuf[i] = mBuf[mPtr + i];
               mPtr = 0;
            }
            mLen = yy;
         }else{
            mPtr = 0;
            if((yy = mIn.read(mBuf)) < 1){
               mLen = 0;
               if(yy < 0){
                  Code();
                  return -1;
               }
               throw new IOException("Stream read 0");
            }
            mLen = yy;
         }
         while(mLen < 4){
            if((yy = mIn.read(mBuf, mLen, 4096 - mLen)) < 1){
               if(yy < 0){
                  Code();
                  throw new IOException(new StrB(32).a("Expected 4 bytes in UTF32, got ").a((char)('0' + mLen)).toString());
               }
               throw new IOException("Stream read 0");
            }
            mLen += yy;
         }
      }
      final byte[] buf = mBuf;
      while(outPtr < len){
         yy = mPtr;
         yy = bigEnd ? buf[yy] << 24 | (buf[yy + 1] & 0xFF) << 16 | (buf[yy + 2] & 0xFF) << 8 | buf[yy + 3] & 0xFF
                     : buf[yy] & 0xFF | (buf[yy + 1] & 0xFF) << 8 | (buf[yy + 2] & 0xFF) << 16 | buf[yy + 3] << 24;
         mPtr += 4;
         if(yy >= 0xD800){
            if(yy > 0x10FFFF || yy < 0xE000 || yy == 0xFFFE || yy == 0xFFFF)
               throw new IOException(new StrB(30).a("Invalid UTF32 char ").apos(yy).toString());
            if(yy > 0xFFFF){
               cbuf[outPtr++] = (char)(0xD800 + ((yy -= 0x10000) >> 10));
               yy = 0xDC00 | yy & 0x03FF;
               if(outPtr >= len){
                  cSurrgt = (char)yy;
                  break;
               }
            }
         }
         cbuf[outPtr++] = (char)yy;
         if(mPtr >= mLen)
            break;
      }
      return outPtr - start;
   }

   private final void Code(){
      if(mBuf != null){
         impl.setBB(mBuf);
         mBuf = null;
      }
   }
}
