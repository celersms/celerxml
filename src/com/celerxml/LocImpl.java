// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025
package com.celerxml;

import javax.xml.stream.Location;

final class LocImpl implements Location{

   final private String pubId, sysId;
   final private int col, row, off;

   // Translate 0-based values to 1-based
   LocImpl(String pubId, String sysId, int col, int row, int off){
      this.pubId = pubId;
      this.sysId = sysId;
      this.off = off < 0 ? 0x7FFFFFFF : off;
      this.col = col + 1;
      this.row = row + 1;
   }

   @Override
   public final int getLineNumber(){ return row; }

   @Override
   public final int getColumnNumber(){ return col; }

   @Override
   public final int getCharacterOffset(){ return off; }

   @Override
   public final String getPublicId(){ return pubId; }

   @Override
   public final String getSystemId(){ return sysId; }
}
