// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025 - 2026
package com.celerxml;

import javax.xml.XMLConstants;

final class NsB{

   final static NsB XML_B = new NsB("xml", XMLConstants.XML_NS_URI), XMLNS_B = new NsB("xmlns", XMLConstants.XMLNS_ATTRIBUTE_NS_URI);
   final String pfx;
   String Code;

   NsB(String pfx){ this.pfx = pfx; }

   private NsB(String pfx, String uri){
      this.pfx = pfx;
      Code = uri;
   }

   final boolean Code(){ return this == XML_B || this == XMLNS_B; }
}
