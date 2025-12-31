// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025
package com.celerxml;

final class ChrPN{

   private PN[] syms;
   private Node[] buckets;
   private int thresh, sz, idxMsk;
   boolean dirty;

   ChrPN(){
      syms = new PN[64];
      buckets = new Node[32];
      idxMsk = 63;
      thresh = 48;
      dirty = true;
   }

   ChrPN(ChrPN parent){
      syms = parent.syms;
      buckets = parent.buckets;
      sz = parent.sz;
      thresh = parent.thresh;
      idxMsk = parent.idxMsk;
   }

   final void upd(ChrPN child){
      if(child.sz > sz){
         syms = child.syms;
         buckets = child.buckets;
         sz = child.sz;
         thresh = child.thresh;
         idxMsk = child.idxMsk;
         child.dirty = dirty = false;
      }
   }

   final PN find(char[] buff, int len, int hash){
      int index = hash & idxMsk;
      PN sym = syms[index];
      if(sym != null && sym.equalsPN(buff, len, hash))
         return sym;
      Node b = buckets[index >> 1];
      while(b != null){
         if((sym = b.mName).equalsPN(buff, len, hash))
            return sym;
         b = b.mNext;
      }
      return null;
   }

   final PN add(char[] buff, int len, int hash){
      int index = hash & idxMsk, size;
      boolean primary = false;
      if(syms[index] == null)
         primary = true;
      else if(sz >= thresh){
         size = syms.length;
         int idx, newSize = size + size;
         PN[] oldSyms = syms;
         Node[] oldNodes = buckets;
         syms = new PN[newSize];
         buckets = new Node[size];
         idxMsk = newSize - 1;
         thresh += thresh;
         for(int i = 0; i < size; ++i){
            PN symbol = oldSyms[i];
            if(symbol != null)
               if(syms[idx = symbol.hashCode() & idxMsk] == null)
                  syms[idx] = symbol;
               else{
                  int bix = idx >> 1;
                  buckets[bix] = new Node(symbol, buckets[bix]);
               }
         }
         size >>= 1;
         for(int i = 0; i < size; ++i){
            Node b = oldNodes[i];
            while(b != null){
               PN symbol = b.mName;
               if(syms[idx = symbol.hashCode() & idxMsk] == null)
                  syms[idx] = symbol;
               else{
                  int bix = idx >> 1;
                  buckets[bix] = new Node(symbol, buckets[bix]);
               }
               b = b.mNext;
            }
         }
         primary = syms[index = hash & idxMsk] == null;
      }
      if(!dirty){
         PN[] oldSyms = syms;
         System.arraycopy(oldSyms, 0, syms = new PN[size = oldSyms.length], 0, size); // CoW
         Node[] oldNodes = buckets;
         System.arraycopy(oldNodes, 0, buckets = new Node[size = oldNodes.length], 0, size); // CoW
         dirty = true;
      }
      ++sz;
      PN pname = PN.construct(new String(buff, 0, len).intern(), hash);
      if(primary)
         syms[index] = pname;
      else{
         int bix = index >> 1;
         buckets[bix] = new Node(pname, buckets[bix]);
      }
      return pname;
   }
}
