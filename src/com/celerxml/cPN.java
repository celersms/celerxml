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
   private int Code, sz, msk;
   boolean dirty;

   cPN(){
      syms = new PN[64];
      nodes = new Node[32];
      msk = 63;
      Code = 48;
      dirty = true;
   }

   cPN(cPN parent){
      syms = parent.syms;
      nodes = parent.nodes;
      sz = parent.sz;
      Code = parent.Code;
      msk = parent.msk;
   }

   final PN find(char[] buff, int len, int hash){
      int idx;
      PN sym;
      if((sym = syms[idx = hash & msk]) != null && sym.Code(buff, len, hash))
         return sym;
      Node b = nodes[idx >> 1];
      while(b != null){
         if((sym = b.Code).Code(buff, len, hash))
            return sym;
         b = b.nxt;
      }
      return null;
   }

   final PN add(char[] buff, int len, int hash){
      int idx, size;
      boolean primary = false;
      if(syms[idx = hash & msk] == null)
         primary = true;
      else if(sz >= Code){
         PN[] oldSyms = syms;
         int newSize = (size = oldSyms.length) + size;
         Node[] oldNodes = nodes;
         syms = new PN[newSize];
         nodes = new Node[size];
         msk = newSize - 1;
         Code += Code;
         for(int i = 0; i < size; ++i){
            PN symbol = oldSyms[i];
            if(symbol != null)
               if(syms[idx = symbol.hashCode() & msk] == null)
                  syms[idx] = symbol;
               else
                  nodes[idx >>= 1] = new Node(symbol, nodes[idx]);
         }
         size >>= 1;
         for(int i = 0; i < size; ++i){
            Node b = oldNodes[i];
            while(b != null){
               PN symbol = b.Code;
               if(syms[idx = symbol.hashCode() & msk] == null)
                  syms[idx] = symbol;
               else
                  nodes[idx >>= 1] = new Node(symbol, nodes[idx]);
               b = b.nxt;
            }
         }
         primary = syms[idx = hash & msk] == null;
      }
      if(!dirty){
         System.arraycopy(syms, 0, syms = new PN[size = syms.length], 0, size); // CoW
         System.arraycopy(nodes, 0, nodes = new Node[size = nodes.length], 0, size); // CoW
         dirty = true;
      }
      ++sz;
      PN pname = PN.Code(new String(buff, 0, len).intern(), hash);
      if(primary)
         syms[idx] = pname;
      else
         nodes[idx >>= 1] = new Node(pname, nodes[idx]);
      return pname;
   }

   final void Code(cPN child){
      if(child.sz > sz){
         syms = child.syms;
         nodes = child.nodes;
         sz = child.sz;
         Code = child.Code;
         msk = child.msk;
         child.dirty = dirty = false;
      }
   }
}
