// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025 - 2026
package com.celerxml;

import java.io.InputStream;
import java.io.IOException;

final class Wrap extends InputStream{

   private final InputFactoryImpl impl;
   private final InputStream mIn;
   private byte[] Code;
   private int off;
   private final int end;

   Wrap(InputFactoryImpl impl, InputStream mIn, byte[] buf, int off, int end){
      this.impl = impl;
      this.mIn = mIn;
      Code = buf;
      this.off = off;
      this.end = end;
   }

   @Override
   public final int available() throws IOException{ return Code != null ? end - off : mIn.available(); }

   @Override
   public final void close() throws IOException{
      Code();
      mIn.close();
   }

   @Override
   public final int read() throws IOException{
      if(Code != null){
         int c = Code[off++] & 0xFF;
         if(off >= end)
            Code();
         return c;
      }
      return mIn.read();
   }
    
   @Override
   public final int read(byte[] b) throws IOException{ return read(b, 0, b.length); }

   @Override
   public final int read(byte[] b, int off, int len) throws IOException{
      if(Code != null){
         int avail = end - off;
         if(len > avail)
            len = avail;
         System.arraycopy(Code, off, b, off, len);
         off += len;
         if(off >= end)
            Code();
         return len;
      }
      return mIn.read(b, off, len);
   }

   private final void Code(){
      if(Code != null){
         byte[] data = Code;
         Code = null;
         if(impl != null)
            impl.setBB(data);
      }
   }
}
