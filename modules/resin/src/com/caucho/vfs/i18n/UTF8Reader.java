/*
 * Copyright (c) 1998-2004 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Resin Open Source is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Resin Open Source; if not, write to the
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.vfs.i18n;

import java.io.*;
import java.net.*;
import java.util.*;

import com.caucho.util.*;
import com.caucho.vfs.*;

/**
 * Implements an encoding reader for UTF8.
 */
public class UTF8Reader extends EncodingReader {
  private InputStream is;

  /**
   * Null-arg constructor for instantiation by com.caucho.vfs.Encoding only.
   */
  public UTF8Reader()
  {
  }

  /**
   * Create a UTF-8 reader based on the readStream.
   */
  private UTF8Reader(InputStream is)
  {
    this.is = is;
  }

  /**
   * Create a UTF-8 reader based on the readStream.
   *
   * @param is the input stream providing the bytes.
   * @param javaEncoding the JDK name for the encoding.
   *
   * @return the UTF-8 reader.
   */
  public Reader create(InputStream is, String javaEncoding)
  {
    return new UTF8Reader(is);
  }

  /**
   * Reads into a character buffer using the correct encoding.
   */
  public int read()
    throws IOException
  {
    int ch1 = is.read();

    if (ch1 < 0x80) {
      return ch1;
    }
    if ((ch1 & 0xe0) == 0xc0) {
      int ch2 = is.read();
      if (ch2 < 0)
        throw new EOFException("unexpected end of file in utf8 character");
      else if ((ch2 & 0xc0) != 0x80)
        throw new CharConversionException("illegal utf8 encoding at " + ch1 + " " + ch2);
      
      return ((ch1 & 0x1f) << 6) + (ch2 & 0x3f);
    }
    else if ((ch1 & 0xf0) == 0xe0) {
      int ch2 = is.read();
      int ch3 = is.read();
      
      if (ch2 < 0)
        throw new EOFException("unexpected end of file in utf8 character");
      else if ((ch2 & 0xc0) != 0x80)
        throw new CharConversionException("illegal utf8 encoding");
      
      if (ch3 < 0)
        throw new EOFException("unexpected end of file in utf8 character");
      else if ((ch3 & 0xc0) != 0x80)
        throw new CharConversionException("illegal utf8 encoding");

      int ch = ((ch1 & 0x1f) << 12) + ((ch2 & 0x3f) << 6) + (ch3 & 0x3f);

      if (ch == 0xfeff) // handle some writers, e.g. microsoft
        return read();
      else
        return ch;
    }
    else
      throw new CharConversionException("illegal utf8 encoding at (" +
                                        (int) ch1 + ")");
  }

  /**
   * Reads into a character buffer using the correct encoding.
   *
   * @param cbuf character buffer receiving the data.
   * @param off starting offset into the buffer.
   * @param len number of characters to read.
   *
   * @return the number of characters read or -1 on end of file.
   */
  public int read(char []cbuf, int off, int len)
    throws IOException
  {
    int i = 0;

    for (i = 0; i < len; i++) {
      int ch = read();

      if (ch < 0)
	return i == 0 ? -1 : i;

      cbuf[off + i] = (char) ch;
    }

    return i;
  }
}
