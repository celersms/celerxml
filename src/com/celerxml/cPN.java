// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025 - 2026
package com.celerxml;

final class cPN{

   private PN[] syms;
   private Node[] nn;
   private int Code, sz, msk;
   boolean dirty;

   cPN(){
      syms = new PN[64];
      nn = new Node[32];
      msk = 63;
      Code = 48;
      dirty = true;
   }

   cPN(cPN parent){
      syms = parent.syms;
      nn = parent.nn;
      sz = parent.sz;
      Code = parent.Code;
      msk = parent.msk;
   }

   final PN find(char[] buff, int len, int hash){
      int idx;
      PN sym;
      if((sym = syms[idx = hash & msk]) != null && sym.Code(buff, len, hash))
         return sym;
      Node b = nn[idx >> 1];
      while(b != null){
         if((sym = b.Code).Code(buff, len, hash))
            return sym;
         b = b.nxt;
      }
      return null;
   }

   final PN add(char[] buff, int len, int hash){
      int idx, xx;
      boolean primary = false;
      if(syms[idx = hash & msk] == null)
         primary = true;
      else if(sz >= Code){
         PN[] oldSyms = syms;
         Node[] oldNodes = nn;
         syms = new PN[idx = (xx = oldSyms.length) + xx];
         nn = new Node[xx];
         msk = idx - 1;
         Code <<= 1;
         for(int i = 0; i < xx; ++i){
            PN symbol = oldSyms[i];
            if(symbol != null)
               if(syms[idx = symbol.hashCode() & msk] == null)
                  syms[idx] = symbol;
               else
                  nn[idx >>= 1] = new Node(symbol, nn[idx]);
         }
         xx >>= 1;
         for(int i = 0; i < xx; ++i){
            Node b = oldNodes[i];
            while(b != null){
               PN symbol = b.Code;
               if(syms[idx = symbol.hashCode() & msk] == null)
                  syms[idx] = symbol;
               else
                  nn[idx >>= 1] = new Node(symbol, nn[idx]);
               b = b.nxt;
            }
         }
         primary = syms[idx = hash & msk] == null;
      }
      if(!dirty){
         System.arraycopy(syms, 0, syms = new PN[xx = syms.length], 0, xx); // CoW
         System.arraycopy(nn, 0, nn = new Node[xx = nn.length], 0, xx); // CoW
         dirty = true;
      }
      ++sz;
      String sname = new String(buff, 0, len).intern();
      PN pname = (xx = sname.indexOf(':')) < 0 ? new PN(sname, null, sname, hash) : new PN(sname, sname.substring(0, xx).intern(), sname.substring(xx + 1).intern(), hash);
      if(primary)
         syms[idx] = pname;
      else
         nn[idx >>= 1] = new Node(pname, nn[idx]);
      return pname;
   }

   final void Code(cPN child){
      if(child.sz > sz){
         syms = child.syms;
         nn = child.nn;
         sz = child.sz;
         Code = child.Code;
         msk = child.msk;
         child.dirty = dirty = false;
      }
   }
}
