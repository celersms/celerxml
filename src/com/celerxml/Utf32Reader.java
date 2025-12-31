// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025
package com.celerxml;

import java.io.Reader;
import java.io.InputStream;
import java.io.IOException;
import java.io.CharConversionException;

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

   @Override
   public final void close() throws IOException{
      InputStream in = mIn;
      if(in != null){
         mIn = null;
         freeBufs();
         in.close();
      }
   }

   @Override
   public final int read() throws IOException{
      if(mTmp == null)
         mTmp = new char[1];
      return read(mTmp, 0, 1) < 1 ? -1 : mTmp[0];
   }

   @Override
   public final int read(char[] cbuf, int start, int len) throws IOException{
      if(mBuf == null)
         return -1;
      if(len < 1)
         return len;
      len += start;
      int xx, yy, outPtr = start;
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
            if((xx = mIn.read(mBuf)) < 1){
               mLen = 0;
               if(xx < 0){
                  freeBufs();
                  return -1;
               }
               throw new IOException("Stream read 0");
            }
            mLen = xx;
         }
         while(mLen < 4){
            if((xx = mIn.read(mBuf, mLen, 4096 - mLen)) < 1){
               if(xx < 0){
                  freeBufs();
                  throw new CharConversionException(new StrB(48).a("EOF reading UTF32 char: got ").apos(mLen).a(" bytes, not 4").toString());
               }
               throw new IOException("Stream read 0");
            }
            mLen += xx;
         }
      }
      byte[] buf = mBuf;
parseUTF32:
      while(outPtr < len){
         yy = mPtr;
         xx = bigEnd ? buf[yy] << 24 | (buf[yy + 1] & 0xFF) << 16 | (buf[yy + 2] & 0xFF) << 8 | buf[yy + 3] & 0xFF
                     : buf[yy] & 0xFF | (buf[yy + 1] & 0xFF) << 8 | (buf[yy + 2] & 0xFF) << 16 | buf[yy + 3] << 24;
         mPtr += 4;
         if(xx >= 0xD800){
            if(xx > 0x10FFFF || xx < 0xE000 || xx == 0xFFFE || xx == 0xFFFF)
               throw new CharConversionException(new StrB(30).a("Invalid UTF32 char ").apos(xx).toString());
            if(xx > 0xFFFF){
               cbuf[outPtr++] = (char)(0xD800 + ((xx -= 0x10000) >> 10));
               xx = 0xDC00 | xx & 0x03FF;
               if(outPtr >= len){
                  cSurrgt = (char)xx;
                  break parseUTF32;
               }
            }
         }
         cbuf[outPtr++] = (char)xx;
         if(mPtr >= mLen)
            break parseUTF32;
      }
      return len = outPtr - start;
   }

   private final void freeBufs(){
      if(mBuf != null){
         impl.setBB(mBuf);
         mBuf = null;
      }
   }
}
