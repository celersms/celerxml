// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025 - 2026
package com.celerxml;

final class SIterator implements java.util.Iterator{

   private final String val;
   private boolean done;

   SIterator(String val, boolean done){
      this.val = val;
      this.done = done;
   }

   @Override
   public final boolean hasNext(){ return !done; }

   @Override
   public final Object next(){
      if(done)
         throw new java.util.NoSuchElementException();
      done = true;
      return val;
   }

   @Override
   public final void remove(){ throw new UnsupportedOperationException(); }
}
