// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025 - 2026
package com.celerxml;

final class bPN{

   private int[] hsh;
   private Node[] clLst;
   private PN[] nams;
   private int Code, hashMsk, clCnt, clEnd;
   private boolean namSh, clLstSh, rehash;
   boolean hashSh;

   bPN(){
      hashMsk = 63;
      hsh = new int[64];
      nams = new PN[64];
      clLstSh = true;
   }

   bPN(bPN parent){
      Code = parent.Code;
      hashMsk = parent.hashMsk;
      hsh = parent.hsh;
      nams = parent.nams;
      clLst = parent.clLst;
      clCnt = parent.clCnt;
      clEnd = parent.clEnd;
      hashSh = namSh = clLstSh = true;
   }

   final PN find(int hash, int q1, int q2){
      int ix = hash & hashMsk, val = hsh[ix];
      PN pname;
      if(((val >> 8 ^ hash) << 8) == 0 && ((pname = nams[ix]) == null || pname.equals(q1, q2)))
         return pname;
      if((val &= 0xFF) != 0)
         for(Node curr = clLst[--val]; curr != null; curr = curr.nxt)
            if((pname = curr.Code).eq(hash, q1, q2))
               return pname;
      return null;
   }

   final PN find(int hash, int[] quads, int qlen){
      //if(qlen < 3)
      //   return find(hash, quads[0], qlen < 2 ? 0 : quads[1]);
      int ix = hash & hashMsk, val = hsh[ix];
      PN pname;
      if(((val >> 8 ^ hash) << 8) == 0 && ((pname = nams[ix]) == null || pname.equals(quads, qlen)))
         return pname;
      if((val &= 0xFF) != 0)
         for(Node curr = clLst[--val]; curr != null; curr = curr.nxt)
            if((pname = curr.Code).eq(hash, quads, qlen))
               return pname;
      return null;
   }

   final PN add(int hash, String ss, int clnIx, int[] quads, int qlen){
      PN symbol;
      if(qlen < 4){
         String pfx, ln;
         if(clnIx < 0){
            ln = ss = ss.intern();
            pfx = null;
         }else{
            pfx = ss.substring(0, clnIx).intern();
            ln = ss.substring(clnIx + 1).intern();
         }
         symbol = qlen == 3 ? new PN3(ss, pfx, ln, hash, quads) : qlen == 2 ? new PN2(ss, pfx, ln, hash, quads[0], quads[1]) : new PN1(ss, pfx, ln, hash, quads[0]);
      }else{
         final int[] buf = new int[qlen];
         System.arraycopy(quads, 0, buf, 0, qlen);
         symbol = clnIx < 0 ? new PNn(ss = ss.intern(), null, ss, hash, buf, qlen) : new PNn(ss, ss.substring(0, clnIx).intern(), ss.substring(clnIx + 1).intern(), hash, buf, qlen);
      }
      int xx;
      if(hashSh){
         System.arraycopy(hsh, 0, hsh = new int[xx = hsh.length], 0, xx); // CoW
         hashSh = false;
      }
      if(rehash){
         rehash = namSh = false;
         int[] oldMainHash = hsh;
         xx = oldMainHash.length;
         hsh = new int[xx + xx];
         hashMsk = xx + xx - 1;
         PN[] oldNames = nams;
         nams = new PN[xx + xx];
         PN symb;
         for(int i = 0; i < xx; ++i)
            if((symb = oldNames[i]) != null){
               int hh = symb.hashCode(), ix = hh & hashMsk;
               nams[ix] = symb;
               hsh[ix] = hh << 8;
            }
         if((xx = clEnd) != 0){
            clCnt = clEnd = 0;
            clLstSh = false;
            Node[] oldNodes = clLst;
            clLst = new Node[oldNodes.length];
            for(int i = 0; i < xx; ++i)
               for(Node curr = oldNodes[i]; curr != null; curr = curr.nxt){
                  int hh = (symb = curr.Code).hashCode(), ix = hh & hashMsk, val = hsh[ix];
                  if(nams[ix] == null){
                     hsh[ix] = hh << 8;
                     nams[ix] = symb;
                  }else{
                     ++clCnt;
                     int bucket = val & 0xFF;
                     if(bucket == 0)
                        hsh[ix] = val & ~0xFF | ((bucket = Code()) + 1);
                     else
                        --bucket;
                     clLst[bucket] = new Node(symb, clLst[bucket]);
                  }
               }
         }
      }
      int ix = hash & hashMsk;
      if(nams[ix] == null){
         hsh[ix] = hash << 8;
         if(namSh){
            System.arraycopy(nams, 0, nams = new PN[xx = nams.length], 0, xx); // CoW
            namSh = false;
         }
         nams[ix] = symbol;
      }else{
         if(clLstSh){
            if(clLst == null)
               clLst = new Node[32];
            else
               System.arraycopy(clLst, 0, clLst = new Node[xx = clLst.length], 0, xx); // CoW
            clLstSh = false;
         }
         ++clCnt;
         int entryValue = hsh[ix], bucket = entryValue & 0xFF;
         if(bucket == 0)
            hsh[ix] = entryValue & ~0xFF | ((bucket = Code()) + 1);
         else
            --bucket;
         clLst[bucket] = new Node(symbol, clLst[bucket]);
      }
      if(++Code > (ix = hsh.length) >> 1 && (clCnt >= (xx = ix >> 2) || Code > ix - xx))
         rehash = true;
      return symbol;
   }

   private final int Code(){
      int bucket, xx, len;
      final Node[] buckets = clLst;
      if((len = clEnd) <= 0xFE){
         if((bucket = clEnd++) >= (xx = buckets.length))
            System.arraycopy(buckets, 0, clLst = new Node[xx + xx], 0, xx);
         return bucket;
      }
      bucket = -1;
      xx = 0x7FFFFFFF;
      for(int i = 0; i < len; ++i){
         int cnt = 1;
         for(Node curr = buckets[i].nxt; curr != null; curr = curr.nxt)
            ++cnt;
         if(cnt < xx){
            bucket = i;
            if((xx = cnt) == 1)
               break;
         }
      }
      return bucket;
   }

   final void Code(bPN child){
      if(child.Code > Code){
         Code = child.Code;
         hashMsk = child.hashMsk;
         hsh = child.hsh;
         nams = child.nams;
         clLst = child.clLst;
         clCnt = child.clCnt;
         clEnd = child.clEnd;
         child.hashSh = child.namSh = child.clLstSh = true;
      }
   }
}
