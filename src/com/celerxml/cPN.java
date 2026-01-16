// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025 - 2026
package com.celerxml;

final class cPN{

   private PN[] syms;
   private Node[] nodes;
   private int Code, sz, idxMsk;
   boolean dirty;

   cPN(){
      syms = new PN[64];
      nodes = new Node[32];
      idxMsk = 63;
      Code = 48;
      dirty = true;
   }

   cPN(cPN parent){
      syms = parent.syms;
      nodes = parent.nodes;
      sz = parent.sz;
      Code = parent.Code;
      idxMsk = parent.idxMsk;
   }

   final PN find(char[] buff, int len, int hash){
      int index = hash & idxMsk;
      PN sym = syms[index];
      if(sym != null && sym.Code(buff, len, hash))
         return sym;
      Node b = nodes[index >> 1];
      while(b != null){
         if((sym = b.Code).Code(buff, len, hash))
            return sym;
         b = b.nxt;
      }
      return null;
   }

   final PN add(char[] buff, int len, int hash){
      int index = hash & idxMsk, size;
      boolean primary = false;
      if(syms[index] == null)
         primary = true;
      else if(sz >= Code){
         size = syms.length;
         int idx, newSize = size + size;
         PN[] oldSyms = syms;
         Node[] oldNodes = nodes;
         syms = new PN[newSize];
         nodes = new Node[size];
         idxMsk = newSize - 1;
         Code += Code;
         for(int i = 0; i < size; ++i){
            PN symbol = oldSyms[i];
            if(symbol != null)
               if(syms[idx = symbol.hashCode() & idxMsk] == null)
                  syms[idx] = symbol;
               else{
                  int bix = idx >> 1;
                  nodes[bix] = new Node(symbol, nodes[bix]);
               }
         }
         size >>= 1;
         for(int i = 0; i < size; ++i){
            Node b = oldNodes[i];
            while(b != null){
               PN symbol = b.Code;
               if(syms[idx = symbol.hashCode() & idxMsk] == null)
                  syms[idx] = symbol;
               else{
                  int bix = idx >> 1;
                  nodes[bix] = new Node(symbol, nodes[bix]);
               }
               b = b.nxt;
            }
         }
         primary = syms[index = hash & idxMsk] == null;
      }
      if(!dirty){
         PN[] oldSyms = syms;
         System.arraycopy(oldSyms, 0, syms = new PN[size = oldSyms.length], 0, size); // CoW
         Node[] oldNodes = nodes;
         System.arraycopy(oldNodes, 0, nodes = new Node[size = oldNodes.length], 0, size); // CoW
         dirty = true;
      }
      ++sz;
      PN pname = PN.Code(new String(buff, 0, len).intern(), hash);
      if(primary)
         syms[index] = pname;
      else{
         int bix = index >> 1;
         nodes[bix] = new Node(pname, nodes[bix]);
      }
      return pname;
   }

   final void Code(cPN child){
      if(child.sz > sz){
         syms = child.syms;
         nodes = child.nodes;
         sz = child.sz;
         Code = child.Code;
         idxMsk = child.idxMsk;
         child.dirty = dirty = false;
      }
   }
}
