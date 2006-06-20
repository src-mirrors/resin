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
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Nam Nguyen
 */

package com.caucho.quercus.lib.zlib;

import java.io.InputStream;
import java.io.IOException;

import java.util.zip.GZIPInputStream;

import com.caucho.quercus.QuercusModuleException;

import com.caucho.quercus.lib.file.BinaryInput;
import com.caucho.quercus.lib.file.ReadStreamInput;

import com.caucho.vfs.ReadStream;
import com.caucho.vfs.Vfs;

/**
 * Input from a compressed stream.
 *
 * 
 */
public class ZlibInputStream extends ReadStreamInput {
  private BinaryInput _in;
  private GZIPInputStream _gzIn;
  private long _position;
  
  public ZlibInputStream(BinaryInput in) throws IOException
  {
    init(in);
  }

  protected void init(BinaryInput in)
    throws IOException
  {
    _in = in;
    _position = 0;

    ReadStream rs;

    // Try opening a GZIP stream.
    // If error, then try opening uncompressed stream.
    try {
      _gzIn = new GZIPInputStream(in.getInputStream());
      rs = Vfs.openRead(_gzIn);
    } catch (IOException e) {
      _in = in.openCopy();
      in.close();
      rs = Vfs.openRead(_in.getInputStream());
    }

    init(rs);
  }

  /**
   * Opens a new copy.
   */
  public BinaryInput openCopy()
    throws IOException
  {
    return new ZlibInputStream(_in.openCopy());
  }

  /**
   * Sets the position.
   */
  public boolean setPosition(long offset)
  {
    try {
      BinaryInput newIn = _in.openCopy();
      
      _gzIn.close();
      getInputStream().close();

      init(newIn);

      if (offset > 0)
	skip(offset);

      return true;
    } catch (IOException e) {
      throw new QuercusModuleException(e);
    }
  }

  public String toString()
  {
    return "ZlibInputStream[]";
  }
}
