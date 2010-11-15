/*
 * Copyright (c) 1998-2010 Caucho Technology -- all rights reserved
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
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.remote.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import com.caucho.util.L10N;

/**
 * WebSocketReader reads a single WebSocket packet.
 *
 * <code><pre>
 * 0x00 utf-8 data 0xff
 * </pre></code>
 */
public class WebSocketReader extends Reader
  implements WebSocketConstants
{
  private static final L10N L = new L10N(WebSocketReader.class);

  private InputStream _is;

  private boolean _isFinal;
  private long _length;

  public WebSocketReader(InputStream is)
    throws IOException
  {
    _is = is;
  }

  public void init(boolean isFinal, long length)
  throws IOException
  {
    _isFinal = isFinal;
    _length = length;
  }

  public long getLength()
  {
    return _length;
  }

  @Override
  public int read()
    throws IOException
  {
    int d1 = readByte();
    
    if (d1 < 0x80)
      return d1;
    
    if ((d1 & 0xe0) == 0xc0) {
      int d2 = readByte();
      
      return ((d1 & 0x1f) << 6) + (d2 & 0x3f);
    }
    else {
      int d2 = readByte();
      int d3 = readByte();
      
      return ((d2 & 0xf) << 12) + ((d2 & 0x3f) << 6) + (d3 & 0x3f);
    }
  }
  
  private int readByte()
    throws IOException
  {
    InputStream is = _is;

    if (_length == 0 && ! _isFinal) {
      readFrameHeader();
    }

    if (_length > 0) {
      int ch = is.read();
      _length--;
      return ch;
    }
    else
      return -1;
  }

  public int read(char []buffer, int offset, int length)
  throws IOException
  {
    /*
  InputStream is = _is;

  if (_length <= 0) {
    if (! readChunkLength())
      return -1;
  }

  int sublen = _length - _offset;

  if (sublen <= 0 || is == null)
    return -1;

  if (length < sublen)
    sublen = length;

  sublen = is.read(buffer, offset, sublen);

  if (sublen > 0) {
    _offset += sublen;
    return sublen;
  }
  else {
    close();
    return -1;
  }
     */

    return -1;
  }

  private void readFrameHeader()
  throws IOException
  {
    InputStream is = _is;

    if (_isFinal || _length > 0)
      throw new IllegalStateException();

    while (! _isFinal && _length == 0) {
      int frame1 = is.read();
      int frame2 = is.read();

      boolean isFinal = (frame1 & FLAG_FIN) == FLAG_FIN;
      int op = frame1 & 0xf;

      if (op != OP_CONT) {
        throw new IOException(L.l("{0}: expected op=CONT '0x{1}' because WebSocket binary protocol expects 0x80 at beginning",
                                  this, Integer.toHexString(frame1 & 0xffff)));
      }

      _isFinal = isFinal;

      long length = frame2 & 0x7f;

      if (length < 0x7e) {
      }
      else if (length == 0x7e) {
        length = ((((long) is.read()) << 8)
            + (((long) is.read())));
      }
      else {
        length = ((((long) is.read()) << 56)
            + (((long) is.read()) << 48)
            + (((long) is.read()) << 40)
            + (((long) is.read()) << 32)
            + (((long) is.read()) << 24)
            + (((long) is.read()) << 16)
            + (((long) is.read()) << 8)
            + (((long) is.read())));
      }

      _length = length;
    }
  }

  @Override
  public void close()
  throws IOException
  {
    while (_length > 0 && !_isFinal) {
      skip(_length);
    }
  }
}
