/*
 * Copyright (c) 1998-2006 Caucho Technology -- all rights reserved
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

package com.caucho.server.connection;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;

import com.caucho.util.*;
import com.caucho.vfs.*;

public class ServletInputStreamImpl extends ServletInputStream  {
  private InputStream _is;

  public ServletInputStreamImpl()
  {
  }

  public void init(InputStream is)
  {
    _is = is;
  }

  public int available() throws IOException
  {
    if (_is == null)
      return -1;
    else
      return _is.available();
  }

  /**
   * Reads a byte from the input stream.
   *
   * @return the next byte or -1 on end of file.
   */
  public int read() throws IOException
  {
    if (_is == null)
      return -1;
    else
      return _is.read();
  }

  public int read(byte []buf, int offset, int len) throws IOException
  {
    if (_is == null)
      return -1;
    else
      return _is.read(buf, offset, len);
  }

  public long skip(long n) throws IOException
  {
    if (_is == null)
      return -1;
    else
      return _is.skip(n);
  }

  public void close() throws IOException
  {
  }

  public void free()
  {
    _is = null;
  }
}
