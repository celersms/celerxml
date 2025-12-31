// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025
package com.celerxml;

final class BytePN{

   private int[] mHash;
   private Node[] collLst;
   private PN[] mNames;
   private int count, hashMsk, collCount, collEnd;
   private boolean mainNameSh, collLstSh, rehash;
   boolean hashSh;

   BytePN(){
      hashMsk = 63;
      mHash = new int[64];
      mNames = new PN[64];
      collLstSh = true;
   }

   BytePN(BytePN parent){
      count = parent.count;
      hashMsk = parent.hashMsk;
      mHash = parent.mHash;
      mNames = parent.mNames;
      collLst = parent.collLst;
      collCount = parent.collCount;
      collEnd = parent.collEnd;
      hashSh = mainNameSh = collLstSh = true;
   }

   final void upd(BytePN child){
      if(child.count > count){
         count = child.count;
         hashMsk = child.hashMsk;
         mHash = child.mHash;
         mNames = child.mNames;
         collLst = child.collLst;
         collCount = child.collCount;
         collEnd = child.collEnd;
         child.hashSh = child.mainNameSh = child.collLstSh = true;
      }
   }

   final PN find(int hash, int q1, int q2){
      int ix = hash & hashMsk, val = mHash[ix];
      PN pname;
      if(((val >> 8 ^ hash) << 8) == 0 && ((pname = mNames[ix]) == null || pname.equals(q1, q2)))
         return pname;
      if((val &= 0xFF) != 0)
         for(Node curr = collLst[--val]; curr != null; curr = curr.mNext)
            if((pname = curr.mName).hashEq(hash, q1, q2))
               return pname;
      return null;
   }

   final PN find(int hash, int[] quads, int qlen){
      if(qlen < 3)
         return find(hash, quads[0], qlen < 2 ? 0 : quads[1]);
      int ix = hash & hashMsk, val = mHash[ix];
      PN pname;
      if(((val >> 8 ^ hash) << 8) == 0 && ((pname = mNames[ix]) == null || pname.equals(quads, qlen)))
         return pname;
      if((val &= 0xFF) != 0)
         for(Node curr = collLst[--val]; curr != null; curr = curr.mNext)
            if((pname = curr.mName).hashEq(hash, quads, qlen))
               return pname;
      return null;
   }

   final PN add(int hash, String symbolStr, int colonIx, int[] quads, int qlen){
      PN symbol;
      if(qlen < 4){
         String pfx, ln;
         if(colonIx < 0){
            symbolStr = symbolStr.intern();
            pfx = null;
            ln = symbolStr;
         }else{
            pfx = symbolStr.substring(0, colonIx).intern();
            ln = symbolStr.substring(colonIx + 1).intern();
         }
         if(qlen == 3)
            symbol = new PN3(symbolStr, pfx, ln, hash, quads);
         else if(qlen == 2)
            symbol = new PN2(symbolStr, pfx, ln, hash, quads[0], quads[1]);
         else
            symbol = new PN1(symbolStr, pfx, ln, hash, quads[0]);
      }else{
         final int[] buf = new int[qlen];
         for(int i = 0; i < qlen; ++i)
            buf[i] = quads[i];
         if(colonIx < 0){
            symbolStr = symbolStr.intern();
            symbol = new PNn(symbolStr, null, symbolStr, hash, buf, qlen);
         }else
            symbol = new PNn(symbolStr, symbolStr.substring(0, colonIx).intern(), symbolStr.substring(colonIx + 1).intern(), hash, buf, qlen);
      }
      int xx;
      if(hashSh){
         int[] old = mHash;
         System.arraycopy(old, 0, mHash = new int[xx = old.length], 0, xx); // CoW
         hashSh = false;
      }
      if(rehash){
         rehash = mainNameSh = false;
         int[] oldMainHash = mHash;
         xx = oldMainHash.length;
         mHash = new int[xx + xx];
         hashMsk = xx + xx - 1;
         PN[] oldNames = mNames;
         mNames = new PN[xx + xx];
         PN symb;
         for(int i = 0; i < xx; ++i)
            if((symb = oldNames[i]) != null){
               int hh = symb.hashCode(), ix = hh & hashMsk;
               mNames[ix] = symb;
               mHash[ix] = hh << 8;
            }
         if((xx = collEnd) != 0){
            collCount = collEnd = 0;
            collLstSh = false;
            Node[] oldNodes = collLst;
            collLst = new Node[oldNodes.length];
            for(int i = 0; i < xx; ++i)
               for(Node curr = oldNodes[i]; curr != null; curr = curr.mNext){
                  int hh = (symb = curr.mName).hashCode(), ix = hh & hashMsk, val = mHash[ix];
                  if(mNames[ix] == null){
                     mHash[ix] = hh << 8;
                     mNames[ix] = symb;
                  }else{
                     ++collCount;
                     int bucket = val & 0xFF;
                     if(bucket == 0)
                        mHash[ix] = val & ~0xFF | ((bucket = findB()) + 1);
                     else
                        --bucket;
                     collLst[bucket] = new Node(symb, collLst[bucket]);
                  }
               }
         }
      }
      int ix = hash & hashMsk;
      if(mNames[ix] == null){
         mHash[ix] = hash << 8;
         if(mainNameSh){
            PN[] old = mNames;
            System.arraycopy(old, 0, mNames = new PN[xx = old.length], 0, xx); // CoW
            mainNameSh = false;
         }
         mNames[ix] = symbol;
      }else{
         if(collLstSh){
            Node[] old = collLst;
            if(old == null)
               collLst = new Node[32];
            else
               System.arraycopy(old, 0, collLst = new Node[xx = old.length], 0, xx); // CoW
            collLstSh = false;
         }
         ++collCount;
         int entryValue = mHash[ix], bucket = entryValue & 0xFF;
         if(bucket == 0)
            mHash[ix] = entryValue & ~0xFF | ((bucket = findB()) + 1);
         else
            --bucket;
         collLst[bucket] = new Node(symbol, collLst[bucket]);
      }
      if(++count > (ix = mHash.length) >> 1 && (collCount >= (xx = ix >> 2) || count > ix - xx))
         rehash = true;
      return symbol;
   }

   private final int findB(){
      int bucket, xx, len = collEnd;
      final Node[] buckets = collLst;
      if(len <= 0xFE){
         if((bucket = collEnd++) >= (xx = buckets.length))
            System.arraycopy(buckets, 0, collLst = new Node[xx + xx], 0, xx);
         return bucket;
      }
      bucket = -1;
      xx = 0x7FFFFFFF;
      for(int i = 0; i < len; ++i){
         int cnt = 1;
         for(Node curr = buckets[i].mNext; curr != null; curr = curr.mNext)
            ++cnt;
         if(cnt < xx){
            bucket = i;
            if((xx = cnt) == 1)
               break;
         }
      }
      return bucket;
   }
}
